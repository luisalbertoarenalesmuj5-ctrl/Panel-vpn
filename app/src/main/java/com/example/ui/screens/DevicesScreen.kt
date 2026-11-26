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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Router
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.model.DevicePlatform
import com.example.data.model.VpnDevice
import com.example.ui.components.EmptyPanelState
import com.example.ui.components.NocCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.formatEpochDateTime
import com.example.ui.navigation.PanelSection
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.RoseCritical
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateContainerHigh
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DevicesScreen(
    devices: List<VpnDevice>,
    onToggleAuthorization: (VpnDevice) -> Unit,
    onDeleteDevice: (VpnDevice) -> Unit,
    onNavigate: (PanelSection) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionHeader(
                title = "Dispositivos autorizados",
                subtitle = "Controla los equipos vinculados a cada cuenta VPN, su IP asignada y autorización de acceso.",
                action = {
                    Button(onClick = { onNavigate(PanelSection.CREATE_CONNECTION) }) {
                        Icon(Icons.Filled.AddLink, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Vincular")
                    }
                }
            )
        }

        if (devices.isEmpty()) {
            item {
                EmptyPanelState(
                    icon = Icons.Filled.Devices,
                    title = "Sin dispositivos registrados",
                    description = "Cuando crees una conexión VPN se registrará aquí el dispositivo autorizado junto con su huella de clave pública.",
                    actionLabel = "Crear conexión",
                    onAction = { onNavigate(PanelSection.CREATE_CONNECTION) }
                )
            }
        } else {
            items(devices, key = { it.id }) { dev ->
                val icon = when (dev.parsedPlatform) {
                    DevicePlatform.ANDROID, DevicePlatform.IOS -> Icons.Filled.PhoneAndroid
                    DevicePlatform.ROUTER -> Icons.Filled.Router
                    else -> Icons.Filled.Laptop
                }

                NocCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = SlateContainerHigh
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = dev.platform,
                                        tint = ElectricCyan,
                                        modifier = Modifier.padding(10.dp).size(20.dp)
                                    )
                                }
                                Column {
                                    Text(dev.deviceName, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = "${dev.vpnUsername} • ${dev.platform} • ${dev.assignedIp}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        color = ElectricCyan
                                    )
                                }
                            }
                            Switch(
                                checked = dev.authorized,
                                onCheckedChange = { onToggleAuthorization(dev) }
                            )
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            StatusPill(
                                label = if (dev.authorized) "AUTORIZADO" else "REVOCADO",
                                color = if (dev.authorized) EmeraldOnline else RoseCritical
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = SlateContainerHigh,
                                border = BorderStroke(1.dp, SlateBorder)
                            ) {
                                Text(
                                    text = "Última actividad: ${formatEpochDateTime(dev.lastSeenEpochMs)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Key: ${dev.publicKeyFingerprint.take(24)}...",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFontFamily,
                                color = TextMuted
                            )
                            IconButton(onClick = { onDeleteDevice(dev) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Eliminar dispositivo", tint = RoseCritical)
                            }
                        }
                    }
                }
            }
        }
    }
}
