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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import com.example.data.model.UserProfile
import com.example.ui.components.NocCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.RoseCritical
import com.example.ui.theme.TextSecondary

@Composable
fun SettingsScreen(
    profile: UserProfile?,
    isBusy: Boolean,
    onSaveSettings: (
        panelName: String,
        defaultDns: String,
        defaultMtu: Long,
        defaultSubnet: String,
        autoSuspendExpired: Boolean,
        apiTimeoutSeconds: Long,
        maskSensitiveKeys: Boolean
    ) -> Unit,
    onSignOut: () -> Unit
) {
    var panelName by remember(profile) { mutableStateOf(profile?.panelName ?: "Panel VPN") }
    var defaultDns by remember(profile) { mutableStateOf(profile?.defaultDns ?: "1.1.1.1, 8.8.8.8") }
    var defaultMtu by remember(profile) { mutableStateOf((profile?.defaultMtu ?: 1420L).toString()) }
    var defaultSubnet by remember(profile) { mutableStateOf(profile?.defaultSubnet ?: "10.8.0.0/24") }
    var apiTimeout by remember(profile) { mutableStateOf((profile?.apiTimeoutSeconds ?: 10L).toString()) }
    var autoSuspend by remember(profile) { mutableStateOf(profile?.autoSuspendExpired ?: true) }
    var maskKeys by remember(profile) { mutableStateOf(profile?.maskSensitiveKeys ?: true) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionHeader(
                title = "Configuración y Seguridad",
                subtitle = "Parámetros globales de red VPN, protección de credenciales y sesión del administrador."
            )
        }

        // Security Overview Card
        item {
            NocCard(modifier = Modifier.fillMaxWidth(), borderColor = EmeraldOnline.copy(alpha = 0.35f)) {
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
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = EmeraldOnline)
                            Text("Estado de seguridad", style = MaterialTheme.typography.titleMedium)
                        }
                        StatusPill(label = "PROTEGIDO", color = EmeraldOnline)
                    }
                    Text(
                        text = "Sesión Google: ${profile?.email ?: "Verificada"}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = JetBrainsMonoFontFamily,
                        color = ElectricCyan
                    )
                    Text(
                        text = "Rol: ${profile?.role ?: "SUPER_ADMIN"} • Reglas Firestore Zero-Trust activas • Claves Curve25519 generadas localmente en dispositivo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }

        // Global Panel & VPN Network Settings
        item {
            NocCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Preferencias del Panel y Red VPN", style = MaterialTheme.typography.titleMedium, color = ElectricCyan)

                    OutlinedTextField(
                        value = panelName,
                        onValueChange = { panelName = it },
                        label = { Text("Nombre del panel") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = defaultDns,
                        onValueChange = { defaultDns = it },
                        label = { Text("DNS predeterminado") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = defaultMtu,
                            onValueChange = { defaultMtu = it.filter(Char::isDigit) },
                            label = { Text("MTU (1200-1500)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = apiTimeout,
                            onValueChange = { apiTimeout = it.filter(Char::isDigit) },
                            label = { Text("Timeout API (s)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = defaultSubnet,
                        onValueChange = { defaultSubnet = it },
                        label = { Text("Subred predeterminada") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Suspender cuentas vencidas", style = MaterialTheme.typography.titleSmall)
                            Text("Marca usuarios expirados automáticamente durante auditorías.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        Switch(checked = autoSuspend, onCheckedChange = { autoSuspend = it })
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Enmascarar tokens y claves privadas", style = MaterialTheme.typography.titleSmall)
                            Text("Oculta credenciales sensibles por defecto en pantalla.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        Switch(checked = maskKeys, onCheckedChange = { maskKeys = it })
                    }

                    Button(
                        onClick = {
                            onSaveSettings(
                                panelName.trim().ifBlank { "Panel VPN" },
                                defaultDns.trim().ifBlank { "1.1.1.1, 8.8.8.8" },
                                (defaultMtu.toLongOrNull() ?: 1420L).coerceIn(1200L, 1500L),
                                defaultSubnet.trim().ifBlank { "10.8.0.0/24" },
                                autoSuspend,
                                (apiTimeout.toLongOrNull() ?: 10L).coerceIn(3L, 60L),
                                maskKeys
                            )
                        },
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth().testTag("save_settings_button")
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Guardar configuración")
                    }
                }
            }
        }

        // Session Sign Out
        item {
            NocCard(modifier = Modifier.fillMaxWidth(), borderColor = RoseCritical.copy(alpha = 0.35f)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = RoseCritical)
                        Text("Sesión de administrador", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = "Cerrar sesión detendrá los listeners en tiempo real y limpiará las credenciales locales.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Button(
                        onClick = onSignOut,
                        colors = ButtonDefaults.buttonColors(containerColor = RoseCritical),
                        modifier = Modifier.fillMaxWidth().testTag("sign_out_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Cerrar sesión")
                    }
                }
            }
        }
    }
}
