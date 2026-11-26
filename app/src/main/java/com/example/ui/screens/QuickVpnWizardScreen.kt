package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.crypto.WireGuardCrypto
import com.example.data.model.DevicePlatform
import com.example.data.model.VpnConfigProfile
import com.example.data.model.VpnProtocolType
import com.example.data.model.VpsServer
import com.example.data.remote.VpsApiClient
import com.example.data.remote.VpsProbeResult
import com.example.ui.components.NocCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.navigation.PanelSection
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.IndigoAccent
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.RoseCritical
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateContainerHigh
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickVpnWizardScreen(
    servers: List<VpsServer>,
    isBusy: Boolean,
    onQuickCreateVpn: (
        vpsIpOrHost: String,
        vpsName: String,
        vpsLocation: String,
        apiEndpoint: String,
        apiToken: String,
        wireguardPort: Long,
        openVpnPort: Long,
        protocol: VpnProtocolType,
        deviceName: String,
        platform: DevicePlatform,
        onReady: (VpnConfigProfile, VpsProbeResult?) -> Unit
    ) -> Unit,
    onNavigate: (PanelSection) -> Unit,
    onNotify: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var generatedToken by remember { mutableStateOf(VpsApiClient.generateAgentToken()) }
    var showScriptPreview by remember { mutableStateOf(false) }

    var vpsIp by remember { mutableStateOf(servers.firstOrNull()?.ipAddress ?: "") }
    var vpsName by remember { mutableStateOf(servers.firstOrNull()?.name ?: "") }
    var vpsLocation by remember { mutableStateOf(servers.firstOrNull()?.location ?: "Mi VPS") }
    var apiEndpoint by remember { mutableStateOf(servers.firstOrNull()?.apiEndpoint ?: "") }
    var apiToken by remember { mutableStateOf(servers.firstOrNull()?.apiToken?.ifBlank { generatedToken } ?: generatedToken) }
    var wgPort by remember { mutableStateOf((servers.firstOrNull()?.wireguardPort ?: 51820L).toString()) }
    var ovpnPort by remember { mutableStateOf((servers.firstOrNull()?.openVpnPort ?: 1194L).toString()) }
    var showAdvanced by remember { mutableStateOf(false) }

    var protocol by remember { mutableStateOf(VpnProtocolType.WIREGUARD) }
    var deviceName by remember { mutableStateOf("Mi Celular") }
    var platform by remember { mutableStateOf(DevicePlatform.ANDROID) }
    var validationError by remember { mutableStateOf<String?>(null) }

    var createdConfig by remember { mutableStateOf<VpnConfigProfile?>(null) }
    var lastProbe by remember { mutableStateOf<VpsProbeResult?>(null) }
    var showQrDialog by remember { mutableStateOf(false) }

    val installerScript = remember(generatedToken, wgPort) {
        VpsApiClient.buildOneClickVpsInstallerScript(
            apiToken = generatedToken,
            wireguardPort = wgPort.toLongOrNull() ?: 51820L,
            agentPort = 8443L
        )
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val cfg = createdConfig
        if (uri != null && cfg != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(cfg.configContent.toByteArray(Charsets.UTF_8))
                }
            }.onSuccess {
                onNotify("¡Archivo VPN guardado! Ya puedes importarlo en tu app.")
            }.onFailure { err ->
                onNotify("Error al guardar: ${err.localizedMessage}")
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionHeader(
                title = "Crear VPN Fácil",
                subtitle = "Sin experiencia previa: prepara tu VPS con 1 comando, escribe su IP y descarga tu VPN lista."
            )
        }

        // PASO 1: Preparar VPS
        item {
            NocCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = ElectricCyan.copy(alpha = 0.35f)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusPill(label = "PASO 1 • TU VPS", color = ElectricCyan)
                        Text(
                            text = "Ubuntu / Debian",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                    Text(
                        text = "Instala WireGuard y el conector en tu VPS con 1 clic",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Si tu VPS es nuevo, copia este script y pégalo una sola vez en la terminal SSH de tu VPS (como root). Si ya instalaste tu VPN, pasa directo al Paso 2.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                clipboard.setText(AnnotatedString(installerScript))
                                onNotify("Script de instalación copiado. Pégalo en la terminal de tu VPS.")
                            },
                            modifier = Modifier.testTag("wizard_copy_installer_button")
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Copiar instalador 1-clic")
                        }
                        OutlinedButton(
                            onClick = {
                                val next = VpsApiClient.generateAgentToken()
                                generatedToken = next
                                apiToken = next
                                onNotify("Nuevo token seguro generado")
                            }
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Nuevo token")
                        }
                        TextButton(onClick = { showScriptPreview = !showScriptPreview }) {
                            Icon(
                                imageVector = if (showScriptPreview) Icons.Filled.ExpandLess else Icons.Filled.Terminal,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(if (showScriptPreview) "Ocultar script" else "Ver script")
                        }
                    }

                    AnimatedVisibility(visible = showScriptPreview) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = ObsidianBg,
                            border = BorderStroke(1.dp, SlateBorder)
                        ) {
                            Text(
                                text = installerScript,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFontFamily,
                                color = EmeraldOnline,
                                modifier = Modifier.fillMaxWidth().padding(12.dp)
                            )
                        }
                    }
                }
            }
        }

        // PASO 2: Conectar VPS y Crear VPN
        item {
            NocCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = IndigoAccent.copy(alpha = 0.4f)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatusPill(label = "PASO 2 • CREAR TU VPN", color = IndigoAccent)
                    Text(
                        text = "Solo necesitamos la IP de tu VPS",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (servers.isNotEmpty()) {
                        Text(
                            text = "O elige un VPS ya guardado:",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            servers.forEach { srv ->
                                FilterChip(
                                    selected = vpsIp.equals(srv.ipAddress, ignoreCase = true),
                                    onClick = {
                                        vpsIp = srv.ipAddress
                                        vpsName = srv.name
                                        vpsLocation = srv.location
                                        apiEndpoint = srv.apiEndpoint
                                        if (srv.apiToken.isNotBlank()) apiToken = srv.apiToken
                                        wgPort = srv.wireguardPort.toString()
                                        ovpnPort = srv.openVpnPort.toString()
                                    },
                                    label = { Text("${srv.name} (${srv.ipAddress})") }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = vpsIp,
                        onValueChange = { vpsIp = it },
                        label = { Text("IP o dominio de tu VPS *") },
                        placeholder = { Text("Ej. 45.80.12.10") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("wizard_vps_ip_input")
                    )

                    OutlinedTextField(
                        value = deviceName,
                        onValueChange = { deviceName = it },
                        label = { Text("Nombre de tu conexión / dispositivo") },
                        placeholder = { Text("Ej. Mi Celular") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("wizard_device_name_input")
                    )

                    Text(
                        text = "Protocolo VPN",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = protocol == VpnProtocolType.WIREGUARD,
                            onClick = { protocol = VpnProtocolType.WIREGUARD },
                            label = { Text("WireGuard (Recomendado)") }
                        )
                        FilterChip(
                            selected = protocol == VpnProtocolType.OPENVPN,
                            onClick = { protocol = VpnProtocolType.OPENVPN },
                            label = { Text("OpenVPN") }
                        )
                    }

                    Text(
                        text = "¿Dónde la vas a usar?",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            DevicePlatform.ANDROID,
                            DevicePlatform.IOS,
                            DevicePlatform.WINDOWS,
                            DevicePlatform.MACOS,
                            DevicePlatform.LINUX
                        ).forEach { pl ->
                            FilterChip(
                                selected = platform == pl,
                                onClick = { platform = pl },
                                label = { Text(pl.name) }
                            )
                        }
                    }

                    TextButton(onClick = { showAdvanced = !showAdvanced }) {
                        Icon(
                            imageVector = if (showAdvanced) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (showAdvanced) "Ocultar opciones avanzadas" else "Opciones avanzadas (puerto / token / URL)")
                    }

                    AnimatedVisibility(visible = showAdvanced) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = vpsName,
                                onValueChange = { vpsName = it },
                                label = { Text("Nombre del servidor") },
                                placeholder = { Text("VPS Principal") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = vpsLocation,
                                onValueChange = { vpsLocation = it },
                                label = { Text("Ubicación") },
                                placeholder = { Text("Madrid / Miami / Frankfurt") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = apiEndpoint,
                                onValueChange = { apiEndpoint = it },
                                label = { Text("URL Agente (opcional)") },
                                placeholder = { Text("http://${vpsIp.ifBlank { "IP" }}:8443") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = apiToken,
                                onValueChange = { apiToken = it },
                                label = { Text("Token del Agente") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = wgPort,
                                    onValueChange = { wgPort = it.filter(Char::isDigit) },
                                    label = { Text("Puerto WireGuard") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = ovpnPort,
                                    onValueChange = { ovpnPort = it.filter(Char::isDigit) },
                                    label = { Text("Puerto OpenVPN") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    if (validationError != null) {
                        Text(validationError!!, color = RoseCritical, style = MaterialTheme.typography.bodySmall)
                    }

                    Button(
                        onClick = {
                            val cleanIp = vpsIp.trim()
                            if (cleanIp.length < 3) {
                                validationError = "Escribe la IP o dominio de tu VPS para crear tu VPN."
                                return@Button
                            }
                            validationError = null
                            onQuickCreateVpn(
                                cleanIp,
                                vpsName,
                                vpsLocation,
                                apiEndpoint,
                                apiToken,
                                (wgPort.toLongOrNull() ?: 51820L).coerceIn(1L, 65535L),
                                (ovpnPort.toLongOrNull() ?: 1194L).coerceIn(1L, 65535L),
                                protocol,
                                deviceName.trim().ifBlank { "Mi Celular" },
                                platform
                            ) { profile, probe ->
                                createdConfig = profile
                                lastProbe = probe
                            }
                        },
                        enabled = !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("wizard_create_vpn_button")
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Conectando VPS y creando VPN...")
                        } else {
                            Icon(Icons.Filled.RocketLaunch, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Crear VPN y preparar descarga")
                        }
                    }
                }
            }
        }

        // PASO 3: Descargar / Conectar
        if (createdConfig != null) {
            val cfg = createdConfig!!
            val isWg = cfg.parsedProtocol == VpnProtocolType.WIREGUARD
            val ext = if (isWg) "conf" else "ovpn"
            val probe = lastProbe

            item {
                NocCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = EmeraldOnline.copy(alpha = 0.55f)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusPill(label = "PASO 3 • ¡VPN LISTA!", color = EmeraldOnline)
                            StatusPill(
                                label = if (probe?.reachable == true) "VPS ONLINE" else "PERFIL LISTO",
                                color = if (probe?.reachable == true) EmeraldOnline else AmberWarning
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = EmeraldOnline,
                                modifier = Modifier.size(26.dp)
                            )
                            Column {
                                Text(
                                    text = "${cfg.deviceName} • ${cfg.protocol}",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${cfg.endpoint} • IP túnel ${cfg.assignedIp}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    color = ElectricCyan
                                )
                            }
                        }

                        if (probe != null) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = SlateContainerHigh,
                                border = BorderStroke(1.dp, SlateBorder)
                            ) {
                                Text(
                                    text = if (probe.reachable) {
                                        "VPS conectado (${probe.latencyMs} ms): ${probe.diagnosticMessage}"
                                    } else {
                                        "Perfil generado y guardado. Si aún no ejecutaste el Paso 1 en tu VPS, hazlo ahora para activar el túnel (${probe.diagnosticMessage})."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    modifier = Modifier.fillMaxWidth().padding(10.dp)
                                )
                            }
                        }

                        Button(
                            onClick = {
                                val safeName = "${cfg.vpnUsername}_${cfg.deviceName}"
                                    .replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                                createDocumentLauncher.launch("$safeName.$ext")
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldOnline,
                                contentColor = ObsidianBg
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("wizard_download_vpn_button")
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Descargar VPN (.$ext)")
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { showQrDialog = true },
                                modifier = Modifier.testTag("wizard_qr_button")
                            ) {
                                Icon(Icons.Filled.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Código QR")
                            }
                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "Mi VPN (${cfg.deviceName})")
                                        putExtra(Intent.EXTRA_TEXT, cfg.configContent)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Abrir o compartir configuración VPN"))
                                }
                            ) {
                                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Compartir")
                            }
                            OutlinedButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(cfg.configContent))
                                    onNotify("Configuración .$ext copiada")
                                }
                            ) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Copiar")
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SlateContainerHigh,
                            border = BorderStroke(1.dp, SlateBorder)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "¿Cómo conectarte en 10 segundos?",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isWg) {
                                        "1. Descarga el archivo .$ext arriba (o abre el Código QR).\n2. Instala la app gratuita WireGuard, toca '+' e importa el archivo o escanea el QR.\n3. Activa el interruptor y ¡listo, ya estás navegando por tu propia VPN!"
                                    } else {
                                        "1. Descarga el archivo .ovpn arriba.\n2. Instala OpenVPN Connect, importa el archivo y presiona Conectar."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            val pkg = if (isWg) "com.wireguard.android" else "net.openvpn.openvpn"
                                            val playIntent = Intent(
                                                Intent.ACTION_VIEW,
                                                Uri.parse("https://play.google.com/store/apps/details?id=$pkg")
                                            )
                                            runCatching { context.startActivity(playIntent) }
                                                .onFailure { onNotify("Busca ${if (isWg) "WireGuard" else "OpenVPN Connect"} en Google Play") }
                                        }
                                    ) {
                                        Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(if (isWg) "App WireGuard" else "App OpenVPN")
                                    }
                                    TextButton(onClick = { onNavigate(PanelSection.CONFIGS) }) {
                                        Text("Ver todas mis VPN")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showQrDialog && createdConfig != null) {
        val target = createdConfig!!
        val qrBitmap = remember(target.configContent) {
            WireGuardCrypto.generateQrBitmap(target.configContent, 640)
        }
        AlertDialog(
            onDismissRequest = { showQrDialog = false },
            title = { Text("Escanea para conectar (${target.protocol})") },
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
                        text = "Abre la app oficial de ${if (target.parsedProtocol == VpnProtocolType.WIREGUARD) "WireGuard" else "OpenVPN Connect"} y escanea este código.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showQrDialog = false }) { Text("Cerrar") }
            }
        )
    }
}
