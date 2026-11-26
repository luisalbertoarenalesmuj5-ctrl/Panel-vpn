package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.model.VpsServer
import com.example.ui.components.CircularTelemetryGauge
import com.example.ui.components.EmptyPanelState
import com.example.ui.components.NocCard
import com.example.ui.components.ResourceBarRow
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.statusColorFor
import com.example.ui.navigation.PanelSection
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.IndigoAccent
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.TextSecondary

@Composable
fun ResourcesScreen(
    servers: List<VpsServer>,
    onProbeAll: () -> Unit,
    onProbeServer: (VpsServer) -> Unit,
    onNavigate: (PanelSection) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionHeader(
                title = "Uso de recursos",
                subtitle = "Monitoreo en vivo de CPU, memoria RAM, disco y latencia por nodo VPS.",
                action = {
                    Button(onClick = onProbeAll, enabled = servers.isNotEmpty()) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Sincronizar")
                    }
                }
            )
        }

        if (servers.isEmpty()) {
            item {
                EmptyPanelState(
                    icon = Icons.Filled.Memory,
                    title = "Sin telemetría de recursos",
                    description = "Registra un servidor VPS con su endpoint API para monitorizar CPU, RAM y almacenamiento en tiempo real.",
                    actionLabel = "Registrar VPS",
                    onAction = { onNavigate(PanelSection.VPS_SERVERS) }
                )
            }
        } else {
            items(servers, key = { it.id }) { srv ->
                val ramPct = if (srv.ramTotalMb > 0L) (srv.ramUsedMb.toDouble() / srv.ramTotalMb) * 100.0 else 0.0
                val diskPct = if (srv.storageTotalGb > 0L) (srv.storageUsedGb.toDouble() / srv.storageTotalGb) * 100.0 else 0.0

                NocCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(srv.name, style = MaterialTheme.typography.titleLarge)
                                Text(
                                    text = "${srv.ipAddress} • Latencia ${srv.latencyMs} ms • ${srv.activeConnections} túneles",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    color = ElectricCyan
                                )
                            }
                            StatusPill(label = srv.status, color = statusColorFor(srv.status))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            CircularTelemetryGauge("CPU", srv.cpuPercent, ElectricCyan)
                            CircularTelemetryGauge("RAM", ramPct, EmeraldOnline)
                            CircularTelemetryGauge("Disco", diskPct, IndigoAccent)
                        }

                        ResourceBarRow(
                            label = "Memoria RAM",
                            valueText = "${srv.ramUsedMb} / ${srv.ramTotalMb} MB",
                            fraction = (ramPct / 100.0).toFloat()
                        )
                        ResourceBarRow(
                            label = "Almacenamiento NVMe/SSD",
                            valueText = "${srv.storageUsedGb} / ${srv.storageTotalGb} GB",
                            fraction = (diskPct / 100.0).toFloat()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = srv.lastDiagnostic,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = { onProbeServer(srv) }) {
                                Text("Actualizar")
                            }
                        }
                    }
                }
            }
        }
    }
}
