package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.model.VpsStatus
import com.example.ui.components.EmptyPanelState
import com.example.ui.navigation.PanelSection
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateContainer
import com.example.ui.theme.SlateContainerHigh
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.PanelVpnViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanelShell(
    currentUserId: String,
    viewModel: PanelVpnViewModel,
    onSignOut: () -> Unit
) {
    var selectedSection by rememberSaveable { mutableStateOf(PanelSection.INICIO) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val serversState by viewModel.vpsServersState.collectAsStateWithLifecycle()
    val protocolsState by viewModel.protocolsState.collectAsStateWithLifecycle()
    val vpnUsersState by viewModel.vpnUsersState.collectAsStateWithLifecycle()
    val configsState by viewModel.configsState.collectAsStateWithLifecycle()
    val devicesState by viewModel.devicesState.collectAsStateWithLifecycle()
    val membersState by viewModel.membersState.collectAsStateWithLifecycle()
    val notificationsState by viewModel.notificationsState.collectAsStateWithLifecycle()
    val logsState by viewModel.activityLogsState.collectAsStateWithLifecycle()
    val probingIds by viewModel.probingServerIds.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    if (selectedSection != PanelSection.INICIO) {
        BackHandler {
            selectedSection = PanelSection.INICIO
        }
    }

    val profile: UserProfile? = (profileState as? UiState.Success)?.data
    val servers = (serversState as? UiState.Success)?.data.orEmpty()
    val protocols = (protocolsState as? UiState.Success)?.data.orEmpty()
    val vpnUsers = (vpnUsersState as? UiState.Success)?.data.orEmpty()
    val configs = (configsState as? UiState.Success)?.data.orEmpty()
    val devices = (devicesState as? UiState.Success)?.data.orEmpty()
    val members = (membersState as? UiState.Success)?.data.orEmpty()
    val notifications = (notificationsState as? UiState.Success)?.data.orEmpty()
    val logs = (logsState as? UiState.Success)?.data.orEmpty()

    val unreadNotifs = notifications.count { !it.read }
    val onlineServers = servers.count { it.parsedStatus == VpsStatus.ONLINE }
    val maskKeys = profile?.maskSensitiveKeys ?: true

    val showErrorBanner = (serversState as? UiState.Error)?.message
        ?: (vpnUsersState as? UiState.Error)?.message
        ?: (profileState as? UiState.Error)?.message

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        val isExpandedScreen = maxWidth >= 840.dp
        val drawerState = rememberDrawerState(DrawerValue.Closed)

        val drawerBody: @Composable () -> Unit = {
            DrawerSidebarContent(
                panelName = profile?.panelName?.ifBlank { "Panel VPN" } ?: "Panel VPN",
                adminEmail = profile?.email ?: "",
                onlineServers = onlineServers,
                totalServers = servers.size,
                unreadNotifications = unreadNotifs,
                selectedSection = selectedSection,
                onSelect = { section ->
                    selectedSection = section
                    if (!isExpandedScreen) {
                        scope.launch { drawerState.close() }
                    }
                }
            )
        }

        val mainScaffold: @Composable () -> Unit = {
            Scaffold(
                containerColor = ObsidianBg,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MidnightSurface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        navigationIcon = {
                            if (!isExpandedScreen) {
                                IconButton(
                                    onClick = { scope.launch { drawerState.open() } },
                                    modifier = Modifier.testTag("open_drawer_button")
                                ) {
                                    Icon(Icons.Filled.Menu, contentDescription = "Abrir menú lateral")
                                }
                            }
                        },
                        title = {
                            Column {
                                Text(
                                    text = selectedSection.label,
                                    style = MaterialTheme.typography.titleLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "$onlineServers/${servers.size} VPS online • ${vpnUsers.size} usuarios",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ElectricCyan
                                )
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { selectedSection = PanelSection.NOTIFICATIONS },
                                modifier = Modifier.testTag("topbar_notifications_button")
                            ) {
                                BadgedBox(
                                    badge = {
                                        if (unreadNotifs > 0) {
                                            Badge { Text("$unreadNotifs") }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Notifications,
                                        contentDescription = "Notificaciones"
                                    )
                                }
                            }
                        }
                    )
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (profileState is UiState.Loading && serversState is UiState.Loading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = ElectricCyan)
                        }
                    } else if (showErrorBanner != null && servers.isEmpty() && vpnUsers.isEmpty()) {
                        Box(modifier = Modifier.padding(16.dp)) {
                            EmptyPanelState(
                                icon = Icons.Filled.Notifications,
                                title = "Error sincronizando con Firestore",
                                description = showErrorBanner
                            )
                        }
                    } else {
                        AnimatedContent(
                            targetState = selectedSection,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "panel_section_switch"
                        ) { section ->
                            when (section) {
                                PanelSection.INICIO -> DashboardScreen(
                                    profile = profile,
                                    servers = servers,
                                    vpnUsers = vpnUsers,
                                    devices = devices,
                                    configs = configs,
                                    logs = logs,
                                    onNavigate = { selectedSection = it },
                                    onProbeAll = viewModel::probeAllServers
                                )
                                PanelSection.QUICK_WIZARD -> QuickVpnWizardScreen(
                                    servers = servers,
                                    isBusy = isBusy,
                                    onQuickCreateVpn = viewModel::quickCreateVpnFromVps,
                                    onNavigate = { selectedSection = it },
                                    onNotify = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                                )
                                PanelSection.VPS_SERVERS -> VpsServersScreen(
                                    servers = servers,
                                    probingIds = probingIds,
                                    maskSensitiveKeys = maskKeys,
                                    onAddServer = viewModel::addVpsServer,
                                    onUpdateServer = viewModel::updateVpsServer,
                                    onDeleteServer = viewModel::deleteVpsServer,
                                    onProbeServer = viewModel::probeServer,
                                    onProbeAll = viewModel::probeAllServers,
                                    onNotify = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                                )
                                PanelSection.PROTOCOLS -> ProtocolsScreen(
                                    protocols = protocols,
                                    servers = servers,
                                    onSaveProtocol = viewModel::saveProtocolConfig
                                )
                                PanelSection.VPN_USERS -> VpnUsersScreen(
                                    users = vpnUsers,
                                    servers = servers,
                                    devices = devices,
                                    onAddUser = viewModel::addVpnUser,
                                    onUpdateUser = viewModel::updateVpnUser,
                                    onToggleSuspend = viewModel::toggleVpnUserStatus,
                                    onDeleteUser = viewModel::deleteVpnUser,
                                    onCreateConnectionForUser = { selectedSection = PanelSection.CREATE_CONNECTION }
                                )
                                PanelSection.CREATE_CONNECTION -> CreateConnectionScreen(
                                    profile = profile,
                                    servers = servers,
                                    vpnUsers = vpnUsers,
                                    existingDevices = devices,
                                    isBusy = isBusy,
                                    onCreateConnection = viewModel::createConnectionProfile,
                                    onNavigate = { selectedSection = it }
                                )
                                PanelSection.CONFIGS -> ConfigsGeneratorScreen(
                                    configs = configs,
                                    maskSensitiveKeys = maskKeys,
                                    onDeleteConfig = viewModel::deleteVpnConfig,
                                    onNavigate = { selectedSection = it },
                                    onNotify = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                                )
                                PanelSection.DEVICES -> DevicesScreen(
                                    devices = devices,
                                    onToggleAuthorization = viewModel::toggleDeviceAuthorization,
                                    onDeleteDevice = viewModel::deleteDevice,
                                    onNavigate = { selectedSection = it }
                                )
                                PanelSection.RESOURCES -> ResourcesScreen(
                                    servers = servers,
                                    onProbeAll = viewModel::probeAllServers,
                                    onProbeServer = viewModel::probeServer,
                                    onNavigate = { selectedSection = it }
                                )
                                PanelSection.MEMBERS -> MembersScreen(
                                    currentUserId = currentUserId,
                                    members = members,
                                    onAddMember = viewModel::addMember,
                                    onUpdateMember = viewModel::updateMember,
                                    onDeleteMember = viewModel::deleteMember
                                )
                                PanelSection.NOTIFICATIONS -> NotificationsScreen(
                                    notifications = notifications,
                                    onMarkRead = viewModel::markNotificationRead,
                                    onDelete = viewModel::deleteNotification,
                                    onRunExpirationAudit = viewModel::runExpirationAudit
                                )
                                PanelSection.ACTIVITY_LOGS -> ActivityLogsScreen(
                                    logs = logs,
                                    onDeleteLog = viewModel::clearActivityLog,
                                    onNotify = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                                )
                                PanelSection.SETTINGS -> SettingsScreen(
                                    profile = profile,
                                    isBusy = isBusy,
                                    onSaveSettings = viewModel::updateSettings,
                                    onSignOut = onSignOut
                                )
                            }
                        }
                    }
                }
            }
        }

        if (isExpandedScreen) {
            PermanentNavigationDrawer(
                drawerContent = {
                    PermanentDrawerSheet(
                        modifier = Modifier.width(290.dp),
                        drawerContainerColor = MidnightSurface
                    ) {
                        drawerBody()
                    }
                },
                content = mainScaffold
            )
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        modifier = Modifier.width(295.dp),
                        drawerContainerColor = MidnightSurface
                    ) {
                        drawerBody()
                    }
                },
                content = mainScaffold
            )
        }
    }
}

@Composable
private fun DrawerSidebarContent(
    panelName: String,
    adminEmail: String,
    onlineServers: Int,
    totalServers: Int,
    unreadNotifications: Int,
    selectedSection: PanelSection,
    onSelect: (PanelSection) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 16.dp)
    ) {
        // Brand Header
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SlateContainer,
            border = BorderStroke(1.dp, SlateBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_auth_shield_1795672549665),
                    contentDescription = "Logo Panel VPN",
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = panelName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = adminEmail.ifBlank { "NOC Admin" },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        val grouped = remember { PanelSection.entries.groupBy { it.group } }
        grouped.forEach { (groupName, items) ->
            Text(
                text = groupName,
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted,
                modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 4.dp)
            )
            items.forEach { section ->
                val isSelected = selectedSection == section
                NavigationDrawerItem(
                    label = {
                        Text(
                            text = section.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    selected = isSelected,
                    onClick = { onSelect(section) },
                    icon = {
                        Icon(
                            imageVector = if (isSelected) section.selectedIcon else section.unselectedIcon,
                            contentDescription = section.label
                        )
                    },
                    badge = {
                        when {
                            section == PanelSection.NOTIFICATIONS && unreadNotifications > 0 -> {
                                Badge { Text("$unreadNotifications") }
                            }
                            section == PanelSection.VPS_SERVERS && totalServers > 0 -> {
                                Text(
                                    text = "$onlineServers/$totalServers",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (onlineServers > 0) EmeraldOnline else TextMuted
                                )
                            }
                        }
                    },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = SlateContainerHigh,
                        selectedIconColor = ElectricCyan,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        unselectedContainerColor = Color.Transparent,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    ),
                    modifier = Modifier
                        .padding(vertical = 2.dp)
                        .testTag("nav_${section.route}")
                )
            }
            HorizontalDivider(
                color = SlateBorder.copy(alpha = 0.5f),
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}
