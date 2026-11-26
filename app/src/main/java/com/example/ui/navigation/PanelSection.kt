package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AddLink
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class PanelSection(
    val route: String,
    val label: String,
    val group: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    INICIO("inicio", "Inicio", "PRINCIPAL", Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
    QUICK_WIZARD("crear_vpn_facil", "Crear VPN Fácil", "PRINCIPAL", Icons.Filled.RocketLaunch, Icons.Outlined.RocketLaunch),
    VPS_SERVERS("servidores_vps", "Servidores VPS", "INFRAESTRUCTURA", Icons.Filled.Dns, Icons.Outlined.Dns),
    PROTOCOLS("protocolos_vpn", "Protocolos VPN", "INFRAESTRUCTURA", Icons.Filled.Hub, Icons.Outlined.Hub),
    RESOURCES("uso_recursos", "Uso de recursos", "INFRAESTRUCTURA", Icons.Filled.Memory, Icons.Outlined.Memory),
    VPN_USERS("usuarios_vpn", "Usuarios VPN", "GESTIÓN VPN", Icons.Filled.Group, Icons.Outlined.Group),
    CREATE_CONNECTION("crear_conexiones", "Crear conexiones", "GESTIÓN VPN", Icons.Filled.AddLink, Icons.Outlined.AddLink),
    CONFIGS("configuraciones_vpn", "Configuraciones VPN", "GESTIÓN VPN", Icons.Filled.QrCode2, Icons.Outlined.QrCode2),
    DEVICES("dispositivos", "Dispositivos", "GESTIÓN VPN", Icons.Filled.Devices, Icons.Outlined.Devices),
    MEMBERS("miembros", "Miembros", "ADMINISTRACIÓN", Icons.Filled.AdminPanelSettings, Icons.Outlined.AdminPanelSettings),
    NOTIFICATIONS("notificaciones", "Notificaciones", "ADMINISTRACIÓN", Icons.Filled.Notifications, Icons.Outlined.Notifications),
    ACTIVITY_LOGS("registros_actividad", "Registros de actividad", "ADMINISTRACIÓN", Icons.Filled.History, Icons.Outlined.History),
    SETTINGS("configuracion", "Configuración", "SISTEMA", Icons.Filled.Settings, Icons.Outlined.Settings)
}
