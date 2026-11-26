package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.VpsServer
import com.example.data.remote.VpsApiClient
import com.example.ui.components.EmptyPanelState
import com.example.ui.components.MaskedSecretRow
import com.example.ui.components.NocCard
import com.example.ui.components.ResourceBarRow
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.formatEpochDateTime
import com.example.ui.components.statusColorFor
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.RoseCritical
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateContainerHigh
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VpsServersScreen(
    servers: List<VpsServer>,
    probingIds: Set<String>,
    maskSensitiveKeys: Boolean,
    onAddServer: (VpsServer) -> Unit,
    onUpdateServer: (VpsServer) -> Unit,
    onDeleteServer: (VpsServer) -> Unit,
    onProbeServer: (VpsServer) -> Unit,
    onProbeAll: () -> Unit,
    onNotify: (String) -> Unit
) {
    var showFormDialog by remember { mutableStateOf(false) }
    var editingServer by remember { mutableStateOf<VpsServer?>(null) }
    var serverToDelete by remember { mutableStateOf<VpsServer?>(null) }
    var showAgentSpecDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionHeader(
                title = "Servidores VPS",
                subtitle = "Registra tus propios nodos VPS y comprueba su API, latencia y telemetría en vivo.",
                action = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showAgentSpecDialog = true }) {
                            Icon(Icons.Filled.Code, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Agente")
                        }
                        Button(
                            onClick = {
                                editingServer = null
                                showFormDialog = true
                            },
                            modifier = Modifier.testTag("add_vps_button")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Nuevo VPS")
                        }
                    }
                }
            )
        }

        if (servers.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${servers.size} servidores registrados",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    FilledTonalButton(onClick = onProbeAll) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Comprobar todos")
                    }
                }
            }
        }

        if (servers.isEmpty()) {
            item {
                EmptyPanelState(
                    icon = Icons.Filled.Dns,
                    title = "No hay servidores VPS registrados",
                    description = "Agrega la IP, ubicación y endpoint API de tu servidor VPS para administrarlo desde el panel.",
                    actionLabel = "Registrar servidor VPS",
                    onAction = {
                        editingServer = null
                        showFormDialog = true
                    }
                )
            }
        } else {
            items(servers, key = { it.id }) { server ->
                val isProbing = probingIds.contains(server.id)
                val ramFraction = if (server.ramTotalMb > 0L) server.ramUsedMb.toFloat() / server.ramTotalMb else 0f
                val diskFraction = if (server.storageTotalGb > 0L) server.storageUsedGb.toFloat() / server.storageTotalGb else 0f

                NocCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = server.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${server.ipAddress} • ${server.location}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    color = ElectricCyan
                                )
                            }
                            StatusPill(
                                label = server.status,
                                color = statusColorFor(server.status)
                            )
                        }

                        // Endpoint & Token
                        Text(
                            text = if (server.apiEndpoint.isNotBlank()) "API: ${server.apiEndpoint}" else "API: No configurada",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = TextSecondary
                        )
                        if (server.apiToken.isNotBlank()) {
                            MaskedSecretRow(
                                label = "API Bearer Token",
                                secretValue = server.apiToken,
                                maskByDefault = maskSensitiveKeys,
                                onCopied = onNotify
                            )
                        }

                        // CPU / RAM / Storage Bars
                        ResourceBarRow(
                            label = "CPU",
                            valueText = "${server.cpuPercent.toInt()}%",
                            fraction = (server.cpuPercent / 100.0).toFloat()
                        )
                        ResourceBarRow(
                            label = "RAM",
                            valueText = if (server.ramTotalMb > 0L) "${server.ramUsedMb} / ${server.ramTotalMb} MB" else "Sin telemetría",
                            fraction = ramFraction
                        )
                        ResourceBarRow(
                            label = "Almacenamiento",
                            valueText = if (server.storageTotalGb > 0L) "${server.storageUsedGb} / ${server.storageTotalGb} GB" else "Sin telemetría",
                            fraction = diskFraction
                        )

                        // Ports & Telemetry meta
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            MetaTag("SSH :${server.sshPort}")
                            MetaTag("WG :${server.wireguardPort} ${if (server.wireguardEnabled) "ON" else "OFF"}")
                            MetaTag("OVPN :${server.openVpnPort} ${if (server.openVpnEnabled) "ON" else "OFF"}")
                            MetaTag("Latencia: ${server.latencyMs} ms")
                            MetaTag("Conexiones: ${server.activeConnections}")
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = SlateContainerHigh,
                            border = BorderStroke(1.dp, SlateBorder)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                                Text(
                                    text = "Diagnóstico: ${server.lastDiagnostic}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                                Text(
                                    text = "Última comprobación: ${formatEpochDateTime(server.lastCheckedAt?.toDate()?.time ?: 0L)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { onProbeServer(server) },
                                enabled = !isProbing,
                                modifier = Modifier.testTag("probe_vps_${server.id}")
                            ) {
                                if (isProbing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Probando...")
                                } else {
                                    Icon(Icons.Filled.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Comprobar conexión")
                                }
                            }
                            Row {
                                IconButton(
                                    onClick = {
                                        editingServer = server
                                        showFormDialog = true
                                    }
                                ) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Editar VPS", tint = ElectricCyan)
                                }
                                IconButton(onClick = { serverToDelete = server }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Eliminar VPS", tint = RoseCritical)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFormDialog) {
        VpsFormDialog(
            initial = editingServer,
            onDismiss = { showFormDialog = false },
            onSave = { server ->
                if (editingServer == null) onAddServer(server) else onUpdateServer(server)
                showFormDialog = false
            }
        )
    }

    if (serverToDelete != null) {
        AlertDialog(
            onDismissRequest = { serverToDelete = null },
            title = { Text("Eliminar servidor VPS") },
            text = { Text("¿Seguro que deseas eliminar ${serverToDelete?.name} (${serverToDelete?.ipAddress})?") },
            confirmButton = {
                Button(
                    onClick = {
                        serverToDelete?.let(onDeleteServer)
                        serverToDelete = null
                    }
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { serverToDelete = null }) { Text("Cancelar") }
            }
        )
    }

    if (showAgentSpecDialog) {
        val clipboard = LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = { showAgentSpecDialog = false },
            title = { Text("API del Agente VPS") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Despliega este micro-agente Node.js (o compatible HTTP JSON) en tu VPS para recibir telemetría real de CPU, RAM, disco y gestión de peers:",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ObsidianBg,
                        border = BorderStroke(1.dp, SlateBorder)
                    ) {
                        Text(
                            text = VpsApiClient.VPS_AGENT_SPEC_SNIPPET,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = EmeraldOnline,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        clipboard.setText(AnnotatedString(VpsApiClient.VPS_AGENT_SPEC_SNIPPET))
                        onNotify("Script de agente VPS copiado")
                        showAgentSpecDialog = false
                    }
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copiar script")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAgentSpecDialog = false }) { Text("Cerrar") }
            }
        )
    }
}

@Composable
private fun MetaTag(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = SlateContainerHigh,
        border = BorderStroke(1.dp, SlateBorder)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = JetBrainsMonoFontFamily,
            color = TextSecondary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun VpsFormDialog(
    initial: VpsServer?,
    onDismiss: () -> Unit,
    onSave: (VpsServer) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var ip by remember { mutableStateOf(initial?.ipAddress ?: "") }
    var location by remember { mutableStateOf(initial?.location ?: "") }
    var apiEndpoint by remember { mutableStateOf(initial?.apiEndpoint ?: "") }
    var apiToken by remember { mutableStateOf(initial?.apiToken ?: "") }
    var sshPort by remember { mutableStateOf((initial?.sshPort ?: 22L).toString()) }
    var wgPort by remember { mutableStateOf((initial?.wireguardPort ?: 51820L).toString()) }
    var ovpnPort by remember { mutableStateOf((initial?.openVpnPort ?: 1194L).toString()) }
    var wgEnabled by remember { mutableStateOf(initial?.wireguardEnabled ?: true) }
    var ovpnEnabled by remember { mutableStateOf(initial?.openVpnEnabled ?: false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Registrar servidor VPS" else "Editar servidor VPS") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre del servidor *") },
                    placeholder = { Text("Ej. VPS Madrid 01") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("vps_name_input")
                )
                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = { Text("Dirección IP o Host *") },
                    placeholder = { Text("Ej. 45.80.12.10") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("vps_ip_input")
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Ubicación / Región *") },
                    placeholder = { Text("Ej. Madrid, España") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("vps_location_input")
                )
                OutlinedTextField(
                    value = apiEndpoint,
                    onValueChange = { apiEndpoint = it },
                    label = { Text("URL API del Agente VPS") },
                    placeholder = { Text("https://tu-vps.com:8443") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("vps_endpoint_input")
                )
                OutlinedTextField(
                    value = apiToken,
                    onValueChange = { apiToken = it },
                    label = { Text("Token de API (Bearer)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = sshPort,
                        onValueChange = { sshPort = it.filter(Char::isDigit) },
                        label = { Text("SSH") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = wgPort,
                        onValueChange = { wgPort = it.filter(Char::isDigit) },
                        label = { Text("WireGuard") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = ovpnPort,
                        onValueChange = { ovpnPort = it.filter(Char::isDigit) },
                        label = { Text("OpenVPN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = wgEnabled, onCheckedChange = { wgEnabled = it })
                    Text("Habilitar WireGuard", style = MaterialTheme.typography.bodyMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = ovpnEnabled, onCheckedChange = { ovpnEnabled = it })
                    Text("Habilitar OpenVPN", style = MaterialTheme.typography.bodyMedium)
                }
                if (validationError != null) {
                    Text(validationError!!, color = RoseCritical, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanName = name.trim()
                    val cleanIp = ip.trim()
                    val cleanLoc = location.trim()
                    if (cleanName.isEmpty() || cleanIp.length < 3 || cleanLoc.isEmpty()) {
                        validationError = "Completa nombre, IP (mín. 3 chars) y ubicación."
                        return@Button
                    }
                    val server = (initial ?: VpsServer()).copy(
                        name = cleanName,
                        ipAddress = cleanIp,
                        location = cleanLoc,
                        apiEndpoint = apiEndpoint.trim(),
                        apiToken = apiToken.trim(),
                        sshPort = (sshPort.toLongOrNull() ?: 22L).coerceIn(1L, 65535L),
                        wireguardPort = (wgPort.toLongOrNull() ?: 51820L).coerceIn(1L, 65535L),
                        openVpnPort = (ovpnPort.toLongOrNull() ?: 1194L).coerceIn(1L, 65535L),
                        wireguardEnabled = wgEnabled,
                        openVpnEnabled = ovpnEnabled
                    )
                    onSave(server)
                },
                modifier = Modifier.testTag("save_vps_button")
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
