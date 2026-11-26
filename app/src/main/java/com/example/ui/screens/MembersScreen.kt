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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.example.data.model.MemberRole
import com.example.data.model.MemberStatus
import com.example.data.model.PanelMember
import com.example.ui.components.EmptyPanelState
import com.example.ui.components.NocCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.statusColorFor
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.IndigoAccent
import com.example.ui.theme.RoseCritical
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateContainerHigh
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MembersScreen(
    currentUserId: String,
    members: List<PanelMember>,
    onAddMember: (PanelMember) -> Unit,
    onUpdateMember: (PanelMember) -> Unit,
    onDeleteMember: (PanelMember) -> Unit
) {
    var showForm by remember { mutableStateOf(false) }
    var editingMember by remember { mutableStateOf<PanelMember?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionHeader(
                title = "Miembros y Roles RBAC",
                subtitle = "Administra operadores, administradores y requisitos de seguridad del equipo.",
                action = {
                    Button(
                        onClick = {
                            editingMember = null
                            showForm = true
                        }
                    ) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Añadir")
                    }
                }
            )
        }

        item {
            NocCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Matriz de roles del panel", style = MaterialTheme.typography.titleSmall, color = ElectricCyan)
                    Text("• SUPER_ADMIN: Control total de infraestructura, VPS, roles y seguridad.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Text("• ADMIN: Gestión de servidores VPS, protocolos, usuarios y configuraciones.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Text("• OPERATOR: Creación de usuarios VPN, perfiles y soporte de dispositivos.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Text("• VIEWER: Solo lectura de estadísticas, telemetría y auditoría.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
        }

        if (members.isEmpty()) {
            item {
                EmptyPanelState(
                    icon = Icons.Filled.AdminPanelSettings,
                    title = "Sin miembros adicionales",
                    description = "Agrega administradores u operadores con roles controlados."
                )
            }
        } else {
            items(members, key = { it.id }) { member ->
                val isSelf = member.id == currentUserId
                val roleColor = when (member.parsedRole) {
                    MemberRole.SUPER_ADMIN -> ElectricCyan
                    MemberRole.ADMIN -> EmeraldOnline
                    MemberRole.OPERATOR -> IndigoAccent
                    MemberRole.VIEWER -> TextSecondary
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isSelf) "${member.name} (Tú)" else member.name,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(member.email, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                            StatusPill(label = member.status, color = statusColorFor(member.status))
                        }

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusPill(label = member.role, color = roleColor)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = SlateContainerHigh,
                                border = BorderStroke(1.dp, SlateBorder)
                            ) {
                                Text(
                                    text = if (member.twoFactorRequired) "2FA Obligatorio" else "2FA Opcional",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        if (!isSelf) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                IconButton(
                                    onClick = {
                                        editingMember = member
                                        showForm = true
                                    }
                                ) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Editar miembro", tint = ElectricCyan)
                                }
                                IconButton(onClick = { onDeleteMember(member) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Eliminar miembro", tint = RoseCritical)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showForm) {
        MemberFormDialog(
            initial = editingMember,
            onDismiss = { showForm = false },
            onSave = { m ->
                if (editingMember == null) onAddMember(m) else onUpdateMember(m)
                showForm = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemberFormDialog(
    initial: PanelMember?,
    onDismiss: () -> Unit,
    onSave: (PanelMember) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var email by remember { mutableStateOf(initial?.email ?: "") }
    var role by remember { mutableStateOf(initial?.parsedRole ?: MemberRole.OPERATOR) }
    var status by remember { mutableStateOf(initial?.parsedStatus ?: MemberStatus.ACTIVE) }
    var twoFactor by remember { mutableStateOf(initial?.twoFactorRequired ?: true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Añadir miembro del panel" else "Editar miembro") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre completo *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Correo electrónico *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Rol asignado:", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MemberRole.entries.filter { it != MemberRole.SUPER_ADMIN }.forEach { r ->
                        FilterChip(
                            selected = role == r,
                            onClick = { role = r },
                            label = { Text(r.name) }
                        )
                    }
                }
                Text("Estado:", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MemberStatus.entries.forEach { s ->
                        FilterChip(
                            selected = status == s,
                            onClick = { status = s },
                            label = { Text(s.name) }
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = twoFactor, onCheckedChange = { twoFactor = it })
                    Text("Exigir verificación 2FA", style = MaterialTheme.typography.bodySmall)
                }
                if (errorMsg != null) {
                    Text(errorMsg!!, color = RoseCritical, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.trim().isEmpty() || email.trim().length < 3) {
                        errorMsg = "Nombre y correo válidos son obligatorios."
                        return@Button
                    }
                    onSave(
                        (initial ?: PanelMember()).copy(
                            name = name.trim(),
                            email = email.trim(),
                            role = role.name,
                            status = status.name,
                            twoFactorRequired = twoFactor
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
