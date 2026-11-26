package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.data.model.ActivityLog
import com.example.ui.components.EmptyPanelState
import com.example.ui.components.NocCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.formatEpochDateTime
import com.example.ui.components.statusColorFor
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.RoseCritical
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActivityLogsScreen(
    logs: List<ActivityLog>,
    onDeleteLog: (String) -> Unit,
    onNotify: (String) -> Unit
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf<String?>(null) }

    val filtered = remember(logs, query, statusFilter) {
        logs.filter { log ->
            val matchQ = query.isBlank() ||
                log.action.contains(query, true) ||
                log.targetName.contains(query, true) ||
                log.actorEmail.contains(query, true) ||
                log.details.contains(query, true)
            val matchS = statusFilter == null || log.status == statusFilter
            matchQ && matchS
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            runCatching {
                val arr = JSONArray()
                filtered.forEach { log ->
                    arr.put(
                        JSONObject().apply {
                            put("action", log.action)
                            put("targetType", log.targetType)
                            put("targetName", log.targetName)
                            put("actorEmail", log.actorEmail)
                            put("details", log.details)
                            put("status", log.status)
                            put("timestamp", formatEpochDateTime(log.createdAt?.toDate()?.time ?: 0L))
                        }
                    )
                }
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(arr.toString(2).toByteArray(Charsets.UTF_8))
                }
            }.onSuccess {
                onNotify("Registro de auditoría exportado")
            }.onFailure {
                onNotify("Error al exportar: ${it.localizedMessage}")
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionHeader(
                title = "Registros de actividad",
                subtitle = "Auditoría inmutable de acciones administrativas, cambios de VPS y seguridad.",
                action = {
                    Button(
                        onClick = { exportLauncher.launch("panel_vpn_audit_logs.json") },
                        enabled = filtered.isNotEmpty()
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Exportar JSON")
                    }
                }
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Filtrar por acción, actor o recurso...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = statusFilter == null,
                        onClick = { statusFilter = null },
                        label = { Text("Todos (${logs.size})") }
                    )
                    FilterChip(
                        selected = statusFilter == "SUCCESS",
                        onClick = { statusFilter = if (statusFilter == "SUCCESS") null else "SUCCESS" },
                        label = { Text("SUCCESS") }
                    )
                    FilterChip(
                        selected = statusFilter == "FAILED",
                        onClick = { statusFilter = if (statusFilter == "FAILED") null else "FAILED" },
                        label = { Text("FAILED") }
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                EmptyPanelState(
                    icon = Icons.Filled.History,
                    title = "Sin registros de auditoría",
                    description = "Cada operación realizada en el panel se registra automáticamente para trazabilidad de seguridad."
                )
            }
        } else {
            items(filtered, key = { it.id }) { log ->
                NocCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${log.action} • ${log.targetType}",
                                style = MaterialTheme.typography.titleSmall,
                                color = ElectricCyan,
                                modifier = Modifier.weight(1f)
                            )
                            StatusPill(label = log.status, color = statusColorFor(log.status))
                        }
                        Text(log.targetName, style = MaterialTheme.typography.titleMedium)
                        if (log.details.isNotBlank()) {
                            Text(log.details, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${log.actorEmail} • ${formatEpochDateTime(log.createdAt?.toDate()?.time ?: 0L)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFontFamily,
                                color = TextMuted,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { onDeleteLog(log.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Eliminar entrada", tint = RoseCritical)
                            }
                        }
                    }
                }
            }
        }
    }
}
