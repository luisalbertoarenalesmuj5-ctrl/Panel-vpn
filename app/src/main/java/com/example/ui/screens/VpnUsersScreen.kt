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
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.ProtocolSelection
import com.example.data.model.VpnDevice
import com.example.data.model.VpnUser
import com.example.data.model.VpnUserStatus
import com.example.data.model.VpsServer
import com.example.ui.components.EmptyPanelState
import com.example.ui.components.NocCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.formatEpochDate
import com.example.ui.components.statusColorFor
import com.example.ui.theme.AmberWarning
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
fun VpnUsersScreen(
    users: List<VpnUser>,
    servers: List<VpsServer>,
    devices: List<VpnDevice>,
    onAddUser: (VpnUser) -> Unit,
    onUpdateUser: (VpnUser) -> Unit,
    onToggleSuspend: (VpnUser) -> Unit,
    onDeleteUser: (VpnUser) -> Unit,
    onCreateConnectionForUser: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf<String?>(null) }
    var showFormDialog by remember { mutableStateOf(false) }
    var editingUser by remember { mutableStateOf<VpnUser?>(null) }
    var inspectingUser by remember { mutableStateOf<VpnUser?>(null) }
    var userToDelete by remember { mutableStateOf<VpnUser?>(null) }

    val filteredUsers = remember(users, searchQuery, statusFilter) {
        users.filter { u ->
            val matchesQuery = searchQuery.isBlank() ||
                u.username.contains(searchQuery, true) ||
                u.email.contains(searchQuery, true) ||
                u.serverName.contains(searchQuery, true)
            val matchesStatus = statusFilter == null || u.status == statusFilter
            matchesQuery && matchesStatus
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionHeader(
                title = "Usuarios VPN",
                subtitle = "Crea, edita, suspende y controla vencimientos y dispositivos autorizados.",
                action = {
                    Button(
                        onClick = {
                            editingUser = null
                            showFormDialog = true
                        },
                        enabled = servers.isNotEmpty(),
                        modifier = Modifier.testTag("add_vpn_user_button")
                    ) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Crear usuario")
                    }
                }
            )
        }

        if (servers.isEmpty()) {
            item {
                EmptyPanelState(
                    icon = Icons.Filled.Group,
                    title = "Primero registra un servidor VPS",
                    description = "Necesitas al menos un servidor VPS registrado para asignar cuentas VPN."
                )
            }
        } else {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Buscar por usuario, correo o VPS...") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = statusFilter == null,
                            onClick = { statusFilter = null },
                            label = { Text("Todos (${users.size})") }
                        )
                        VpnUserStatus.entries.forEach { st ->
                            val count = users.count { it.status == st.name }
                            FilterChip(
                                selected = statusFilter == st.name,
                                onClick = { statusFilter = if (statusFilter == st.name) null else st.name },
                                label = { Text("${st.name} ($count)") }
                            )
                        }
                    }
                }
            }

            if (filteredUsers.isEmpty()) {
                item {
                    EmptyPanelState(
                        icon = Icons.Filled.Group,
                        title = "Sin usuarios VPN",
                        description = "Crea tu primer usuario VPN y asigna su protocolo, dispositivos máximos y fecha de vencimiento.",
                        actionLabel = "Crear usuario VPN",
                        onAction = {
                            editingUser = null
                            showFormDialog = true
                        }
                    )
                }
            } else {
                items(filteredUsers, key = { it.id }) { user ->
                    val userDevices = devices.filter { it.vpnUserId == user.id }
                    val now = System.currentTimeMillis()
                    val isExpired = user.expiresAtEpochMs > 0L && user.expiresAtEpochMs <= now

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
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = user.username,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${user.serverName} • ${user.protocol}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        color = ElectricCyan
                                    )
                                }
                                StatusPill(
                                    label = if (isExpired && user.status == "ACTIVE") "EXPIRADO" else user.status,
                                    color = if (isExpired) RoseCritical else statusColorFor(user.status)
                                )
                            }

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                UserChip("Disp: ${userDevices.size}/${user.maxDevices}")
                                UserChip("Conexiones: ${user.activeConnections}")
                                UserChip("Cuota: ${user.dataLimitGb} GB")
                                UserChip("Vence: ${formatEpochDate(user.expiresAtEpochMs)}")
                            }

                            if (user.notes.isNotBlank()) {
                                Text(
                                    text = user.notes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(onClick = { inspectingUser = user }) {
                                    Text("Dispositivos (${userDevices.size})")
                                }
                                OutlinedButton(onClick = { onCreateConnectionForUser() }) {
                                    Icon(Icons.Filled.AddLink, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Perfil")
                                }
                                IconButton(onClick = { onToggleSuspend(user) }) {
                                    Icon(
                                        imageVector = if (user.status == "SUSPENDED") Icons.Filled.CheckCircle else Icons.Filled.Block,
                                        contentDescription = if (user.status == "SUSPENDED") "Activar usuario" else "Suspender usuario",
                                        tint = if (user.status == "SUSPENDED") EmeraldOnline else AmberWarning
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        editingUser = user
                                        showFormDialog = true
                                    }
                                ) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Editar usuario", tint = ElectricCyan)
                                }
                                IconButton(onClick = { userToDelete = user }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Eliminar usuario", tint = RoseCritical)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFormDialog) {
        VpnUserFormDialog(
            initial = editingUser,
            servers = servers,
            onDismiss = { showFormDialog = false },
            onSave = { u ->
                if (editingUser == null) onAddUser(u) else onUpdateUser(u)
                showFormDialog = false
            }
        )
    }

    if (inspectingUser != null) {
        val target = inspectingUser!!
        val userDevices = devices.filter { it.vpnUserId == target.id }
        AlertDialog(
            onDismissRequest = { inspectingUser = null },
            title = { Text("Conexiones y dispositivos: ${target.username}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Límite autorizado: ${target.maxDevices} dispositivos • Conexiones activas: ${target.activeConnections}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    if (userDevices.isEmpty()) {
                        Text(
                            text = "Este usuario aún no tiene dispositivos asociados. Crea una conexión en 'Crear conexiones'.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    } else {
                        userDevices.forEach { dev ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = SlateContainerHigh,
                                border = BorderStroke(1.dp, SlateBorder)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(dev.deviceName, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            "${dev.platform} • ${dev.assignedIp}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = JetBrainsMonoFontFamily,
                                            color = ElectricCyan
                                        )
                                    }
                                    StatusPill(
                                        label = if (dev.authorized) "AUTORIZADO" else "REVOCADO",
                                        color = if (dev.authorized) EmeraldOnline else RoseCritical
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { inspectingUser = null }) { Text("Cerrar") }
            }
        )
    }

    if (userToDelete != null) {
        AlertDialog(
            onDismissRequest = { userToDelete = null },
            title = { Text("Eliminar usuario VPN") },
            text = { Text("¿Eliminar permanentemente a ${userToDelete?.username}?") },
            confirmButton = {
                Button(
                    onClick = {
                        userToDelete?.let(onDeleteUser)
                        userToDelete = null
                    }
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { userToDelete = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun UserChip(text: String) {
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VpnUserFormDialog(
    initial: VpnUser?,
    servers: List<VpsServer>,
    onDismiss: () -> Unit,
    onSave: (VpnUser) -> Unit
) {
    var username by remember { mutableStateOf(initial?.username ?: "") }
    var email by remember { mutableStateOf(initial?.email ?: "") }
    var selectedServer by remember {
        mutableStateOf(servers.firstOrNull { it.id == initial?.serverId } ?: servers.first())
    }
    var protocol by remember { mutableStateOf(initial?.parsedProtocol ?: ProtocolSelection.WIREGUARD) }
    var maxDevices by remember { mutableStateOf((initial?.maxDevices ?: 2L).toString()) }
    var dataLimitGb by remember { mutableStateOf((initial?.dataLimitGb ?: 100L).toString()) }
    var daysValid by remember { mutableStateOf("30") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Crear usuario VPN" else "Editar usuario VPN") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Nombre de usuario *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("vpn_user_name_input")
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Correo (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Servidor VPS asignado:", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    servers.forEach { srv ->
                        FilterChip(
                            selected = selectedServer.id == srv.id,
                            onClick = { selectedServer = srv },
                            label = { Text(srv.name) }
                        )
                    }
                }
                Text("Protocolo permitido:", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProtocolSelection.entries.forEach { p ->
                        FilterChip(
                            selected = protocol == p,
                            onClick = { protocol = p },
                            label = { Text(p.name) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = maxDevices,
                        onValueChange = { maxDevices = it.filter(Char::isDigit) },
                        label = { Text("Máx. disp.") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = dataLimitGb,
                        onValueChange = { dataLimitGb = it.filter(Char::isDigit) },
                        label = { Text("Cuota GB") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text("Vigencia (días desde hoy):", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("7", "30", "90", "365").forEach { d ->
                        FilterChip(
                            selected = daysValid == d,
                            onClick = { daysValid = d },
                            label = { Text("$d días") }
                        )
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notas") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMsg != null) {
                    Text(errorMsg!!, color = RoseCritical, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val clean = username.trim()
                    if (clean.length < 2) {
                        errorMsg = "El usuario debe tener al menos 2 caracteres."
                        return@Button
                    }
                    val days = daysValid.toLongOrNull() ?: 30L
                    val expiresAt = if (initial != null && initial.expiresAtEpochMs > System.currentTimeMillis() && daysValid == "30") {
                        initial.expiresAtEpochMs
                    } else {
                        System.currentTimeMillis() + days * 24L * 60L * 60L * 1000L
                    }
                    onSave(
                        (initial ?: VpnUser()).copy(
                            username = clean,
                            email = email.trim(),
                            serverId = selectedServer.id,
                            serverName = selectedServer.name,
                            protocol = protocol.name,
                            status = initial?.status ?: VpnUserStatus.ACTIVE.name,
                            maxDevices = (maxDevices.toLongOrNull() ?: 2L).coerceIn(1L, 100L),
                            dataLimitGb = (dataLimitGb.toLongOrNull() ?: 100L).coerceIn(0L, 100000L),
                            expiresAtEpochMs = expiresAt,
                            notes = notes.trim()
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
