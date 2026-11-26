package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.ActivityLog
import com.example.data.model.UserProfile
import com.example.data.model.VpnConfigProfile
import com.example.data.model.VpnDevice
import com.example.data.model.VpnUser
import com.example.data.model.VpnUserStatus
import com.example.data.model.VpsServer
import com.example.data.model.VpsStatus
import com.example.ui.components.CircularTelemetryGauge
import com.example.ui.components.EmptyPanelState
import com.example.ui.components.KpiMetricCard
import com.example.ui.components.NocCard
import com.example.ui.components.StatusPill
import com.example.ui.components.formatEpochDateTime
import com.example.ui.components.statusColorFor
import com.example.ui.navigation.PanelSection
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.IndigoAccent
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.RoseCritical
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateContainer
import com.example.ui.theme.SlateContainerHigh
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    profile: UserProfile?,
    servers: List<VpsServer>,
    vpnUsers: List<VpnUser>,
    devices: List<VpnDevice>,
    configs: List<VpnConfigProfile>,
    logs: List<ActivityLog>,
    onNavigate: (PanelSection) -> Unit,
    onProbeAll: () -> Unit
) {
    val onlineServers = servers.count { it.parsedStatus == VpsStatus.ONLINE }
    val activeUsers = vpnUsers.count { it.parsedStatus == VpnUserStatus.ACTIVE }
    val connectedDevices = devices.count { it.connected }
    val activeConnections = maxOf(
        servers.sumOf { it.activeConnections }.toInt(),
        connectedDevices
    )
    val connectedUsersCount = maxOf(
        vpnUsers.count { it.activeConnections > 0L },
        devices.filter { it.connected }.map { it.vpnUserId }.distinct().size
    )

    val avgCpu = if (servers.isNotEmpty()) servers.map { it.cpuPercent }.average() else 0.0
    val avgRam = if (servers.any { it.ramTotalMb > 0 }) {
        servers.filter { it.ramTotalMb > 0 }.map { (it.ramUsedMb.toDouble() / it.ramTotalMb) * 100.0 }.average()
    } else 0.0
    val avgStorage = if (servers.any { it.storageTotalGb > 0 }) {
        servers.filter { it.storageTotalGb > 0 }.map { (it.storageUsedGb.toDouble() / it.storageTotalGb) * 100.0 }.average()
    } else 0.0

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Banner
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(SlateContainer)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_vpn_hero_1795672549735),
                    contentDescription = "Infraestructura de red Panel VPN",
                    modifier = Modifier
                        .matchParentSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.38f
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    ObsidianBg.copy(alpha = 0.94f),
                                    SlateContainer.copy(alpha = 0.78f)
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusPill(
                            label = if (onlineServers > 0) "$onlineServers NODOS ONLINE" else "LISTO PARA DESPLIEGUE",
                            color = if (onlineServers > 0) EmeraldOnline else ElectricCyan
                        )
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = SlateContainerHigh.copy(alpha = 0.85f),
                            border = BorderStroke(1.dp, SlateBorder)
                        ) {
                            Text(
                                text = profile?.role ?: "SUPER_ADMIN",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    Text(
                        text = profile?.panelName?.ifBlank { "Panel VPN" } ?: "Panel VPN",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Operador activo: ${profile?.email ?: "Sesión verificada"} • ${configs.size} perfiles emitidos",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onNavigate(PanelSection.VPS_SERVERS) },
                            modifier = Modifier.testTag("dash_add_vps_btn")
                        ) {
                            Icon(Icons.Filled.Dns, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Servidores VPS")
                        }
                        FilledTonalButton(
                            onClick = { onNavigate(PanelSection.CREATE_CONNECTION) },
                            modifier = Modifier.testTag("dash_create_conn_btn")
                        ) {
                            Icon(Icons.Filled.AddLink, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Crear conexión")
                        }
                        OutlinedButton(
                            onClick = onProbeAll,
                            modifier = Modifier.testTag("dash_probe_all_btn")
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Comprobar VPS")
                        }
                    }
                }
            }
        }

        // KPI Grid (Responsive 2 cols phone / 4 cols tablet)
        item {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val columns = if (maxWidth >= 700.dp) 4 else 2
                val items = listOf(
                    Triple("Usuarios registrados", "${vpnUsers.size}", "$activeUsers activos") to (Icons.Filled.Group to ElectricCyan),
                    Triple("Usuarios conectados", "$connectedUsersCount", "${devices.size} dispositivos") to (Icons.Filled.PersonAdd to EmeraldOnline),
                    Triple("Servidores disponibles", "$onlineServers/${servers.size}", if (servers.isEmpty()) "Sin nodos" else "API monitoreada") to (Icons.Filled.Dns to IndigoAccent),
                    Triple("Conexiones activas", "$activeConnections", "${configs.size} perfiles generados") to (Icons.Filled.Link to AmberWarning)
                )
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items.chunked(columns).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowItems.forEach { (meta, visual) ->
                                KpiMetricCard(
                                    title = meta.first,
                                    value = meta.second,
                                    subtitle = meta.third,
                                    icon = visual.first,
                                    accentColor = visual.second,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(columns - rowItems.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        // Resource consumption & Server distribution chart
        item {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val wide = maxWidth >= 760.dp
                if (wide) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        ResourceTelemetrySummaryCard(
                            avgCpu = avgCpu,
                            avgRam = avgRam,
                            avgStorage = avgStorage,
                            onOpenResources = { onNavigate(PanelSection.RESOURCES) },
                            modifier = Modifier.weight(1f)
                        )
                        VpsHealthBarChartCard(
                            servers = servers,
                            onOpenVps = { onNavigate(PanelSection.VPS_SERVERS) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        ResourceTelemetrySummaryCard(
                            avgCpu = avgCpu,
                            avgRam = avgRam,
                            avgStorage = avgStorage,
                            onOpenResources = { onNavigate(PanelSection.RESOURCES) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        VpsHealthBarChartCard(
                            servers = servers,
                            onOpenVps = { onNavigate(PanelSection.VPS_SERVERS) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // Quick Actions Strip
        item {
            NocCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Accesos rápidos de operación",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuickNavChip("Usuarios VPN", Icons.Filled.Group) { onNavigate(PanelSection.VPN_USERS) }
                        QuickNavChip("Protocolos", Icons.Filled.Hub) { onNavigate(PanelSection.PROTOCOLS) }
                        QuickNavChip("Generar QR / .conf", Icons.Filled.QrCode2) { onNavigate(PanelSection.CONFIGS) }
                        QuickNavChip("Seguridad y Roles", Icons.Filled.Security) { onNavigate(PanelSection.MEMBERS) }
                    }
                }
            }
        }

        // Activity History
        item {
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.History, contentDescription = null, tint = ElectricCyan)
                            Text(
                                text = "Historial reciente de actividad",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        OutlinedButton(onClick = { onNavigate(PanelSection.ACTIVITY_LOGS) }) {
                            Text("Ver todo")
                        }
                    }

                    if (logs.isEmpty()) {
                        Text(
                            text = "Sin eventos registrados todavía. Toda acción administrativa aparecerá aquí en tiempo real.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    } else {
                        logs.take(5).forEach { log ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = SlateContainerHigh,
                                border = BorderStroke(1.dp, SlateBorder)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${log.action} • ${log.targetName}",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = log.details.ifBlank { log.actorEmail },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Column(horizontalAlignment = Alignment.End) {
                                        StatusPill(
                                            label = log.status,
                                            color = statusColorFor(log.status)
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = formatEpochDateTime(log.createdAt?.toDate()?.time ?: 0L),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextMuted
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResourceTelemetrySummaryCard(
    avgCpu: Double,
    avgRam: Double,
    avgStorage: Double,
    onOpenResources: () -> Unit,
    modifier: Modifier = Modifier
) {
    NocCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Consumo promedio de recursos",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                OutlinedButton(onClick = onOpenResources) {
                    Text("Detalle")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CircularTelemetryGauge("CPU", avgCpu, ElectricCyan)
                CircularTelemetryGauge("RAM", avgRam, EmeraldOnline)
                CircularTelemetryGauge("Disco", avgStorage, IndigoAccent)
            }
        }
    }
}

@Composable
private fun VpsHealthBarChartCard(
    servers: List<VpsServer>,
    onOpenVps: () -> Unit,
    modifier: Modifier = Modifier
) {
    NocCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Carga CPU por nodo VPS",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                OutlinedButton(onClick = onOpenVps) {
                    Text("Nodos")
                }
            }

            if (servers.isEmpty()) {
                EmptyPanelState(
                    icon = Icons.Filled.Dns,
                    title = "Sin servidores VPS",
                    description = "Registra tu primer servidor VPS para visualizar telemetría real."
                )
            } else {
                val previewServers = servers.take(5)
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                ) {
                    val count = previewServers.size.coerceAtLeast(1)
                    val slotWidth = size.width / count
                    val barWidth = (slotWidth * 0.48f).coerceAtMost(44.dp.toPx())
                    previewServers.forEachIndexed { index, srv ->
                        val ratio = (srv.cpuPercent / 100.0).toFloat().coerceIn(0.06f, 1f)
                        val barHeight = size.height * ratio
                        val x = index * slotWidth + (slotWidth - barWidth) / 2f
                        val y = size.height - barHeight
                        val barColor = when {
                            srv.parsedStatus != VpsStatus.ONLINE -> RoseCritical.copy(alpha = 0.65f)
                            srv.cpuPercent >= 85.0 -> RoseCritical
                            srv.cpuPercent >= 65.0 -> AmberWarning
                            else -> ElectricCyan
                        }
                        drawRoundRect(
                            color = SlateContainerHigh,
                            topLeft = Offset(x, 0f),
                            size = Size(barWidth, size.height),
                            cornerRadius = CornerRadius(10f, 10f)
                        )
                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(x, y),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(10f, 10f)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    previewServers.forEach { srv ->
                        Text(
                            text = srv.name.take(9),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = TextSecondary,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickNavChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = SlateContainerHigh,
            contentColor = Color.White
        )
    ) {
        Icon(icon, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label)
    }
}
