package com.example.ui.screens

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
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SecurityUpdateGood
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.model.NotificationSeverity
import com.example.data.model.PanelNotification
import com.example.ui.components.EmptyPanelState
import com.example.ui.components.NocCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.formatEpochDateTime
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.RoseCritical
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotificationsScreen(
    notifications: List<PanelNotification>,
    onMarkRead: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRunExpirationAudit: () -> Unit
) {
    var severityFilter by remember { mutableStateOf<String?>(null) }
    val filtered = remember(notifications, severityFilter) {
        if (severityFilter == null) notifications
        else notifications.filter { it.severity == severityFilter }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionHeader(
                title = "Notificaciones y Alertas",
                subtitle = "Alertas de servidores caídos, alto consumo de CPU/RAM y vencimientos de cuentas VPN.",
                action = {
                    Button(onClick = onRunExpirationAudit) {
                        Icon(Icons.Filled.SecurityUpdateGood, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Auditar")
                    }
                }
            )
        }

        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = severityFilter == null,
                    onClick = { severityFilter = null },
                    label = { Text("Todas (${notifications.size})") }
                )
                NotificationSeverity.entries.forEach { sev ->
                    FilterChip(
                        selected = severityFilter == sev.name,
                        onClick = { severityFilter = if (severityFilter == sev.name) null else sev.name },
                        label = { Text(sev.name) }
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                EmptyPanelState(
                    icon = Icons.Filled.Notifications,
                    title = "Sin alertas pendientes",
                    description = "Las alertas de salud de VPS y vencimiento de usuarios aparecerán aquí automáticamente.",
                    actionLabel = "Ejecutar auditoría de vencimientos",
                    onAction = onRunExpirationAudit
                )
            }
        } else {
            items(filtered, key = { it.id }) { item ->
                val sevColor = when (item.parsedSeverity) {
                    NotificationSeverity.CRITICAL -> RoseCritical
                    NotificationSeverity.WARNING -> AmberWarning
                    NotificationSeverity.INFO -> ElectricCyan
                }

                NocCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = if (!item.read) sevColor.copy(alpha = 0.45f) else com.example.ui.theme.SlateBorder
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            StatusPill(label = item.severity, color = sevColor)
                        }
                        Text(item.message, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${item.category} • ${formatEpochDateTime(item.createdAt?.toDate()?.time ?: 0L)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!item.read) {
                                    OutlinedButton(onClick = { onMarkRead(item.id) }) {
                                        Icon(Icons.Filled.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Marcar leída")
                                    }
                                }
                                IconButton(onClick = { onDelete(item.id) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Descartar alerta", tint = RoseCritical)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
