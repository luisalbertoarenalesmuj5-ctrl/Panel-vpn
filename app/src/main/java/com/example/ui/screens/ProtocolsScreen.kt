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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.TransportType
import com.example.data.model.VpnProtocolConfig
import com.example.data.model.VpnProtocolType
import com.example.data.model.VpsServer
import com.example.ui.components.EmptyPanelState
import com.example.ui.components.NocCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.IndigoAccent
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.RoseCritical
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateContainerHigh
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProtocolsScreen(
    protocols: List<VpnProtocolConfig>,
    servers: List<VpsServer>,
    onSaveProtocol: (VpnProtocolConfig, VpsServer?) -> Unit
) {
    var editingProtocol by remember { mutableStateOf<VpnProtocolConfig?>(null) }

    val wgCount = protocols.count { it.parsedType == VpnProtocolType.WIREGUARD && it.enabled }
    val ovpnCount = protocols.count { it.parsedType == VpnProtocolType.OPENVPN && it.enabled }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionHeader(
                title = "Protocolos VPN",
                subtitle = "Administra los servicios WireGuard y OpenVPN, puertos, cifrado y subredes en tus servidores VPS."
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                NocCard(modifier = Modifier.weight(1f), borderColor = ElectricCyan.copy(alpha = 0.35f)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("WireGuard", style = MaterialTheme.typography.titleMedium, color = ElectricCyan)
                        Text("$wgCount servicios activos", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Text("ChaCha20-Poly1305 • Kernel UDP", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                }
                NocCard(modifier = Modifier.weight(1f), borderColor = IndigoAccent.copy(alpha = 0.35f)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("OpenVPN", style = MaterialTheme.typography.titleMedium, color = IndigoAccent)
                        Text("$ovpnCount servicios activos", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Text("AES-256-GCM • TCP/UDP TLS", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                }
            }
        }

        if (protocols.isEmpty()) {
            item {
                EmptyPanelState(
                    icon = Icons.Filled.Hub,
                    title = "Sin protocolos configurados",
                    description = "Al registrar un servidor VPS se crean automáticamente las configuraciones de WireGuard y OpenVPN listas para administrar."
                )
            }
        } else {
            items(protocols, key = { it.id }) { proto ->
                val boundServer = servers.firstOrNull { it.id == proto.serverId }
                val isWg = proto.parsedType == VpnProtocolType.WIREGUARD

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
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = (if (isWg) ElectricCyan else IndigoAccent).copy(alpha = 0.14f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Shield,
                                        contentDescription = proto.type,
                                        tint = if (isWg) ElectricCyan else IndigoAccent,
                                        modifier = Modifier.padding(10.dp).size(20.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "${proto.type} • ${proto.serverName}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Puerto ${proto.port}/${proto.transport} • ${proto.cipher}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        color = TextSecondary
                                    )
                                }
                            }
                            Switch(
                                checked = proto.enabled,
                                onCheckedChange = { next ->
                                    onSaveProtocol(proto.copy(enabled = next), boundServer)
                                }
                            )
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            StatusPill(
                                label = if (proto.enabled) "ACTIVO" else "DETENIDO",
                                color = if (proto.enabled) EmeraldOnline else RoseCritical
                            )
                            ProtoTag("Subred: ${proto.subnet}")
                            ProtoTag("DNS: ${proto.dns}")
                            ProtoTag("MTU: ${proto.mtu}")
                            ProtoTag("Keepalive: ${proto.keepaliveSeconds}s")
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(onClick = { editingProtocol = proto }) {
                                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Configurar parámetros")
                            }
                        }
                    }
                }
            }
        }
    }

    if (editingProtocol != null) {
        val current = editingProtocol!!
        val boundServer = servers.firstOrNull { it.id == current.serverId }
        ProtocolEditDialog(
            initial = current,
            onDismiss = { editingProtocol = null },
            onSave = { updated ->
                onSaveProtocol(updated, boundServer)
                editingProtocol = null
            }
        )
    }
}

@Composable
private fun ProtoTag(text: String) {
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
private fun ProtocolEditDialog(
    initial: VpnProtocolConfig,
    onDismiss: () -> Unit,
    onSave: (VpnProtocolConfig) -> Unit
) {
    var port by remember { mutableStateOf(initial.port.toString()) }
    var transport by remember { mutableStateOf(initial.parsedTransport) }
    var subnet by remember { mutableStateOf(initial.subnet) }
    var dns by remember { mutableStateOf(initial.dns) }
    var mtu by remember { mutableStateOf(initial.mtu.toString()) }
    var cipher by remember { mutableStateOf(initial.cipher) }
    var keepalive by remember { mutableStateOf(initial.keepaliveSeconds.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configurar ${initial.type}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter(Char::isDigit) },
                    label = { Text("Puerto") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TransportType.entries.forEach { t ->
                        FilterChip(
                            selected = transport == t,
                            onClick = { transport = t },
                            label = { Text(t.name) }
                        )
                    }
                }
                OutlinedTextField(
                    value = subnet,
                    onValueChange = { subnet = it },
                    label = { Text("Subred CIDR") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dns,
                    onValueChange = { dns = it },
                    label = { Text("Servidores DNS") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
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
                OutlinedTextField(
                    value = cipher,
                    onValueChange = { cipher = it },
                    label = { Text("Cifrado") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        initial.copy(
                            port = (port.toLongOrNull() ?: initial.port).coerceIn(1L, 65535L),
                            transport = transport.name,
                            subnet = subnet.trim().ifBlank { initial.subnet },
                            dns = dns.trim().ifBlank { initial.dns },
                            mtu = (mtu.toLongOrNull() ?: initial.mtu).coerceIn(1200L, 1500L),
                            cipher = cipher.trim().ifBlank { initial.cipher },
                            keepaliveSeconds = (keepalive.toLongOrNull() ?: initial.keepaliveSeconds).coerceIn(0L, 300L)
                        )
                    )
                }
            ) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
