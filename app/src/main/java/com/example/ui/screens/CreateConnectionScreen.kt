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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.DevicePlatform
import com.example.data.model.UserProfile
import com.example.data.model.VpnDevice
import com.example.data.model.VpnProtocolType
import com.example.data.model.VpnUser
import com.example.data.model.VpsServer
import com.example.ui.components.EmptyPanelState
import com.example.ui.components.NocCard
import com.example.ui.components.SectionHeader
import com.example.ui.navigation.PanelSection
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.RoseCritical
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateConnectionScreen(
    profile: UserProfile?,
    servers: List<VpsServer>,
    vpnUsers: List<VpnUser>,
    existingDevices: List<VpnDevice>,
    isBusy: Boolean,
    onCreateConnection: (
        vpnUser: VpnUser,
        server: VpsServer,
        protocol: VpnProtocolType,
        deviceName: String,
        platform: DevicePlatform,
        assignedIp: String,
        dns: String,
        mtu: Long,
        keepalive: Long,
        pushToVpsAgent: Boolean,
        onCreated: (String) -> Unit
    ) -> Unit,
    onNavigate: (PanelSection) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionHeader(
                title = "Crear conexiones",
                subtitle = "Genera claves criptográficas reales Curve25519 / OpenVPN, autoriza el dispositivo y emite el perfil."
            )
        }

        if (servers.isEmpty()) {
            item {
                EmptyPanelState(
                    icon = Icons.Filled.Dns,
                    title = "Registra un servidor VPS primero",
                    description = "Para crear conexiones y generar perfiles necesitas al menos un servidor VPS.",
                    actionLabel = "Ir a Servidores VPS",
                    onAction = { onNavigate(PanelSection.VPS_SERVERS) }
                )
            }
        } else if (vpnUsers.isEmpty()) {
            item {
                EmptyPanelState(
                    icon = Icons.Filled.Group,
                    title = "Crea un usuario VPN primero",
                    description = "Para emitir credenciales y códigos QR crea al menos un usuario VPN.",
                    actionLabel = "Ir a Usuarios VPN",
                    onAction = { onNavigate(PanelSection.VPN_USERS) }
                )
            }
        } else {
            item {
                val suggestedHostOctet = remember(existingDevices.size) {
                    (existingDevices.size + 2).coerceIn(2, 250)
                }
                var selectedUser by remember(vpnUsers) { mutableStateOf(vpnUsers.first()) }
                var selectedServer by remember(servers, selectedUser) {
                    mutableStateOf(servers.firstOrNull { it.id == selectedUser.serverId } ?: servers.first())
                }
                var protocol by remember { mutableStateOf(VpnProtocolType.WIREGUARD) }
                var deviceName by remember { mutableStateOf("") }
                var platform by remember { mutableStateOf(DevicePlatform.ANDROID) }
                var assignedIp by remember(suggestedHostOctet) { mutableStateOf("10.8.0.$suggestedHostOctet/32") }
                var dns by remember(profile) { mutableStateOf(profile?.defaultDns ?: "1.1.1.1, 8.8.8.8") }
                var mtu by remember(profile) { mutableStateOf((profile?.defaultMtu ?: 1420L).toString()) }
                var keepalive by remember { mutableStateOf("25") }
                var pushToAgent by remember { mutableStateOf(true) }
                var errorMsg by remember { mutableStateOf<String?>(null) }

                NocCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("1. Usuario VPN destino", style = MaterialTheme.typography.titleMedium, color = ElectricCyan)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            vpnUsers.forEach { u ->
                                FilterChip(
                                    selected = selectedUser.id == u.id,
                                    onClick = {
                                        selectedUser = u
                                        servers.firstOrNull { it.id == u.serverId }?.let { selectedServer = it }
                                    },
                                    label = { Text(u.username) }
                                )
                            }
                        }

                        Text("2. Servidor VPS y Protocolo", style = MaterialTheme.typography.titleMedium, color = ElectricCyan)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            servers.forEach { srv ->
                                FilterChip(
                                    selected = selectedServer.id == srv.id,
                                    onClick = { selectedServer = srv },
                                    label = { Text("${srv.name} (${srv.ipAddress})") }
                                )
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            VpnProtocolType.entries.forEach { p ->
                                FilterChip(
                                    selected = protocol == p,
                                    onClick = { protocol = p },
                                    label = { Text(p.name) }
                                )
                            }
                        }

                        Text("3. Dispositivo y Parámetros de Túnel", style = MaterialTheme.typography.titleMedium, color = ElectricCyan)
                        OutlinedTextField(
                            value = deviceName,
                            onValueChange = { deviceName = it },
                            label = { Text("Nombre del dispositivo *") },
                            placeholder = { Text("Ej. Pixel 9 Pro / Laptop Oficina") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("conn_device_name_input")
                        )

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            DevicePlatform.entries.forEach { pl ->
                                FilterChip(
                                    selected = platform == pl,
                                    onClick = { platform = pl },
                                    label = { Text(pl.name) }
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = assignedIp,
                                onValueChange = { assignedIp = it },
                                label = { Text("IP de Túnel") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = dns,
                                onValueChange = { dns = it },
                                label = { Text("DNS") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = mtu,
                                onValueChange = { mtu = it.filter(Char::isDigit) },
                                label = { Text("MTU") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = keepalive,
                                onValueChange = { keepalive = it.filter(Char::isDigit) },
                                label = { Text("Keepalive (s)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = pushToAgent, onCheckedChange = { pushToAgent = it })
                            Text(
                                text = "Sincronizar peer con API del VPS si está configurada",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }

                        if (errorMsg != null) {
                            Text(errorMsg!!, color = RoseCritical, style = MaterialTheme.typography.bodySmall)
                        }

                        Button(
                            onClick = {
                                val cleanDev = deviceName.trim()
                                if (cleanDev.isEmpty()) {
                                    errorMsg = "Ingresa un nombre para el dispositivo."
                                    return@Button
                                }
                                errorMsg = null
                                onCreateConnection(
                                    selectedUser,
                                    selectedServer,
                                    protocol,
                                    cleanDev,
                                    platform,
                                    assignedIp.trim().ifBlank { "10.8.0.$suggestedHostOctet/32" },
                                    dns.trim().ifBlank { "1.1.1.1" },
                                    (mtu.toLongOrNull() ?: 1420L).coerceIn(1200L, 1500L),
                                    (keepalive.toLongOrNull() ?: 25L).coerceIn(0L, 300L),
                                    pushToAgent
                                ) {
                                    onNavigate(PanelSection.CONFIGS)
                                }
                            },
                            enabled = !isBusy,
                            modifier = Modifier.fillMaxWidth().testTag("generate_connection_button")
                        ) {
                            if (isBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Generando claves...")
                            } else {
                                Icon(Icons.Filled.VpnKey, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Generar conexión, claves y QR")
                            }
                        }
                    }
                }
            }
        }
    }
}
