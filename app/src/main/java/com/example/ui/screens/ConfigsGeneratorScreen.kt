package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.data.crypto.WireGuardCrypto
import com.example.data.model.VpnConfigProfile
import com.example.data.model.VpnProtocolType
import com.example.ui.components.EmptyPanelState
import com.example.ui.components.MaskedSecretRow
import com.example.ui.components.NocCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.navigation.PanelSection
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.IndigoAccent
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.RoseCritical
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConfigsGeneratorScreen(
    configs: List<VpnConfigProfile>,
    maskSensitiveKeys: Boolean,
    onDeleteConfig: (VpnConfigProfile) -> Unit,
    onNavigate: (PanelSection) -> Unit,
    onNotify: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var qrConfig by remember { mutableStateOf<VpnConfigProfile?>(null) }
    var guideConfig by remember { mutableStateOf<VpnConfigProfile?>(null) }
    var pendingDownloadConfig by remember { mutableStateOf<VpnConfigProfile?>(null) }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val cfg = pendingDownloadConfig
        if (uri != null && cfg != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(cfg.configContent.toByteArray(Charsets.UTF_8))
                }
            }.onSuccess {
                onNotify("Archivo guardado correctamente")
            }.onFailure { err ->
                onNotify("Error al guardar archivo: ${err.localizedMessage}")
            }
        }
        pendingDownloadConfig = null
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionHeader(
                title = "Configuraciones VPN",
                subtitle = "Descarga archivos .conf/.ovpn, genera códigos QR y consulta guías de conexión.",
                action = {
                    Button(onClick = { onNavigate(PanelSection.CREATE_CONNECTION) }) {
                        Icon(Icons.Filled.AddLink, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Nuevo perfil")
                    }
                }
            )
        }

        if (configs.isEmpty()) {
            item {
                EmptyPanelState(
                    icon = Icons.Filled.QrCode2,
                    title = "Sin configuraciones generadas",
                    description = "Crea una conexión para generar archivos WireGuard (.conf) u OpenVPN (.ovpn) y códigos QR escaneables.",
                    actionLabel = "Crear conexión",
                    onAction = { onNavigate(PanelSection.CREATE_CONNECTION) }
                )
            }
        } else {
            items(configs, key = { it.id }) { cfg ->
                val isWg = cfg.parsedProtocol == VpnProtocolType.WIREGUARD
                val ext = if (isWg) "conf" else "ovpn"

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
                                    text = "${cfg.vpnUsername} • ${cfg.deviceName}",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${cfg.serverName} • ${cfg.endpoint} • IP ${cfg.assignedIp}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    color = ElectricCyan
                                )
                            }
                            StatusPill(
                                label = cfg.protocol,
                                color = if (isWg) EmeraldOnline else IndigoAccent
                            )
                        }

                        if (cfg.privateKey.isNotBlank()) {
                            MaskedSecretRow(
                                label = "Clave Privada / Seed",
                                secretValue = cfg.privateKey,
                                maskByDefault = maskSensitiveKeys,
                                onCopied = onNotify
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = ObsidianBg,
                            border = BorderStroke(1.dp, SlateBorder)
                        ) {
                            Text(
                                text = if (maskSensitiveKeys) {
                                    cfg.configContent.lines().joinToString("\n") { line ->
                                        if (line.startsWith("PrivateKey") || line.startsWith("PresharedKey")) {
                                            "${line.substringBefore("=")} = ••••••••••••••••"
                                        } else line
                                    }
                                } else cfg.configContent,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFontFamily,
                                color = TextSecondary,
                                modifier = Modifier.fillMaxWidth().padding(12.dp)
                            )
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { qrConfig = cfg },
                                modifier = Modifier.testTag("show_qr_${cfg.id}")
                            ) {
                                Icon(Icons.Filled.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Código QR")
                            }
                            FilledTonalButton(
                                onClick = {
                                    pendingDownloadConfig = cfg
                                    val safeFile = "${cfg.vpnUsername}_${cfg.deviceName}".replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                                    createDocumentLauncher.launch("$safeFile.$ext")
                                }
                            ) {
                                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Descargar .$ext")
                            }
                            OutlinedButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(cfg.configContent))
                                    onNotify("Perfil .$ext copiado")
                                }
                            ) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Copiar")
                            }
                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "Perfil VPN ${cfg.vpnUsername}")
                                        putExtra(Intent.EXTRA_TEXT, cfg.configContent)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Compartir configuración VPN"))
                                }
                            ) {
                                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Compartir")
                            }
                            OutlinedButton(onClick = { guideConfig = cfg }) {
                                Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Instrucciones")
                            }
                            IconButton(onClick = { onDeleteConfig(cfg) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Eliminar perfil", tint = RoseCritical)
                            }
                        }
                    }
                }
            }
        }
    }

    if (qrConfig != null) {
        val target = qrConfig!!
        val qrBitmap = remember(target.configContent) {
            WireGuardCrypto.generateQrBitmap(target.configContent, 640)
        }
        AlertDialog(
            onDismissRequest = { qrConfig = null },
            title = { Text("QR ${target.protocol}: ${target.deviceName}") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (qrBitmap != null) {
                        Box(
                            modifier = Modifier
                                .background(Color.White, RoundedCornerShape(16.dp))
                                .padding(14.dp)
                        ) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "Código QR de configuración VPN",
                                modifier = Modifier.size(220.dp)
                            )
                        }
                    }
                    Text(
                        text = "Escanea este código desde la app oficial de ${if (target.parsedProtocol == VpnProtocolType.WIREGUARD) "WireGuard" else "OpenVPN Connect"} para importar el túnel al instante.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { qrConfig = null }) { Text("Cerrar") }
            }
        )
    }

    if (guideConfig != null) {
        val target = guideConfig!!
        ConnectionInstructionsDialog(
            config = target,
            onDismiss = { guideConfig = null }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConnectionInstructionsDialog(
    config: VpnConfigProfile,
    onDismiss: () -> Unit
) {
    var selectedOs by remember { mutableStateOf("Android / iOS") }
    val osTabs = listOf("Android / iOS", "Windows / macOS", "Linux CLI")
    val isWg = config.parsedProtocol == VpnProtocolType.WIREGUARD

    val steps = when (selectedOs) {
        "Android / iOS" -> if (isWg) listOf(
            "1. Instala la app oficial WireGuard desde Google Play o App Store.",
            "2. Toca el botón '+' y elige 'Escanear desde código QR' (o 'Importar desde archivo').",
            "3. Escanea el QR de este perfil y asigna el nombre '${config.deviceName}'.",
            "4. Activa el interruptor para establecer el túnel hacia ${config.endpoint}."
        ) else listOf(
            "1. Instala OpenVPN Connect en tu dispositivo móvil.",
            "2. Descarga el archivo .ovpn desde este panel.",
            "3. Importa el archivo en OpenVPN Connect > File > Import.",
            "4. Conecta y verifica tu IP asignada (${config.assignedIp})."
        )
        "Windows / macOS" -> if (isWg) listOf(
            "1. Descarga el cliente WireGuard para Windows/macOS desde wireguard.com/install.",
            "2. Descarga el archivo .conf desde este panel.",
            "3. Haz clic en 'Importar túnel(es) desde archivo' y selecciona el .conf.",
            "4. Presiona 'Activar' para iniciar el túnel cifrado."
        ) else listOf(
            "1. Instala OpenVPN GUI (Windows) o Tunnelblick (macOS).",
            "2. Descarga el perfil .ovpn.",
            "3. Arrastra el archivo .ovpn al cliente y conecta."
        )
        else -> if (isWg) listOf(
            "1. Instala WireGuard: sudo apt install wireguard resolvconf",
            "2. Guarda el archivo en /etc/wireguard/wg0.conf (permisos chmod 600).",
            "3. Activa el túnel: sudo wg-quick up wg0",
            "4. Verifica el handshake: sudo wg show"
        ) else listOf(
            "1. Instala OpenVPN: sudo apt install openvpn",
            "2. Ejecuta: sudo openvpn --config perfil.ovpn"
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Instrucciones de conexión (${config.protocol})") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    osTabs.forEach { os ->
                        FilterChip(
                            selected = selectedOs == os,
                            onClick = { selectedOs = os },
                            label = { Text(os) }
                        )
                    }
                }
                steps.forEach { step ->
                    Text(step, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Entendido") }
        }
    )
}
