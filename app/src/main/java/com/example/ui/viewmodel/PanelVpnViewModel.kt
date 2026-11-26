package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.crypto.WireGuardCrypto
import com.example.data.firebase.VpnPanelRepository
import com.example.data.model.ActivityLog
import com.example.data.model.DevicePlatform
import com.example.data.model.LogStatus
import com.example.data.model.NotificationSeverity
import com.example.data.model.PanelMember
import com.example.data.model.PanelNotification
import com.example.data.model.ProtocolSelection
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.model.VpnConfigProfile
import com.example.data.model.VpnDevice
import com.example.data.model.VpnProtocolConfig
import com.example.data.model.VpnProtocolType
import com.example.data.model.VpnUser
import com.example.data.model.VpnUserStatus
import com.example.data.model.VpsServer
import com.example.data.model.VpsStatus
import com.example.data.remote.VpsApiClient
import com.example.data.remote.VpsPeerProvisionRequest
import com.example.data.remote.VpsProbeResult
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardMetrics(
    val registeredUsers: Int = 0,
    val activeUsers: Int = 0,
    val connectedUsers: Int = 0,
    val availableServers: Int = 0,
    val totalServers: Int = 0,
    val activeConnections: Int = 0,
    val avgCpuPercent: Double = 0.0,
    val avgRamPercent: Double = 0.0,
    val avgStoragePercent: Double = 0.0,
    val unreadNotifications: Int = 0
)

class PanelVpnViewModel(
    private val repository: VpnPanelRepository,
    private val vpsApiClient: VpsApiClient,
    private val currentUserId: String,
    private val currentUserName: String,
    private val currentUserEmail: String
) : ViewModel() {

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    private val _probingServerIds = MutableStateFlow<Set<String>>(emptySet())
    val probingServerIds: StateFlow<Set<String>> = _probingServerIds.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private fun <T> Flow<T>.toUiState(label: String): StateFlow<UiState<T>> =
        this.map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing $label", rootError)
                emit(UiState.Error(rootError.localizedMessage ?: "Error al cargar $label"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val profileState: StateFlow<UiState<UserProfile?>> =
        repository.observeProfile(currentUserId).toUiState("perfil")

    val vpsServersState: StateFlow<UiState<List<VpsServer>>> =
        repository.observeVpsServers(currentUserId).toUiState("servidores VPS")

    val protocolsState: StateFlow<UiState<List<VpnProtocolConfig>>> =
        repository.observeProtocols(currentUserId).toUiState("protocolos VPN")

    val vpnUsersState: StateFlow<UiState<List<VpnUser>>> =
        repository.observeVpnUsers(currentUserId).toUiState("usuarios VPN")

    val configsState: StateFlow<UiState<List<VpnConfigProfile>>> =
        repository.observeVpnConfigs(currentUserId).toUiState("configuraciones VPN")

    val devicesState: StateFlow<UiState<List<VpnDevice>>> =
        repository.observeDevices(currentUserId).toUiState("dispositivos")

    val membersState: StateFlow<UiState<List<PanelMember>>> =
        repository.observeMembers(currentUserId).toUiState("miembros")

    val notificationsState: StateFlow<UiState<List<PanelNotification>>> =
        repository.observeNotifications(currentUserId).toUiState("notificaciones")

    val activityLogsState: StateFlow<UiState<List<ActivityLog>>> =
        repository.observeActivityLogs(currentUserId).toUiState("registros")

    init {
        viewModelScope.launch {
            val res = repository.ensureWorkspaceInitialized(
                userId = currentUserId,
                displayName = currentUserName,
                email = currentUserEmail
            )
            res.onFailure { err ->
                _events.tryEmit("Error inicializando espacio de trabajo: ${err.localizedMessage}")
            }
        }
    }

    private fun currentTimeoutSeconds(): Long {
        val profile = (profileState.value as? UiState.Success)?.data
        return profile?.apiTimeoutSeconds ?: 10L
    }

    fun addVpsServer(server: VpsServer) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.addVpsServer(server)
            _isBusy.value = false
            res.onSuccess { newId ->
                _events.tryEmit("Servidor ${server.name} registrado")
                if (server.apiEndpoint.isNotBlank()) {
                    probeServer(server.copy(id = newId))
                }
            }.onFailure { err ->
                _events.tryEmit(err.localizedMessage ?: "No se pudo registrar el servidor")
            }
        }
    }

    fun updateVpsServer(server: VpsServer) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.updateVpsServer(server)
                .onSuccess { _events.tryEmit("Servidor ${server.name} actualizado") }
                .onFailure { _events.tryEmit(it.localizedMessage ?: "Error al actualizar VPS") }
            _isBusy.value = false
        }
    }

    fun deleteVpsServer(server: VpsServer) {
        viewModelScope.launch {
            repository.deleteVpsServer(server)
                .onSuccess { _events.tryEmit("Servidor ${server.name} eliminado") }
                .onFailure { _events.tryEmit(it.localizedMessage ?: "Error al eliminar VPS") }
        }
    }

    fun probeServer(server: VpsServer) {
        viewModelScope.launch {
            _probingServerIds.value = _probingServerIds.value + server.id
            val result = vpsApiClient.probeServerHealth(
                endpointUrl = server.apiEndpoint,
                apiToken = server.apiToken,
                timeoutSeconds = currentTimeoutSeconds()
            )
            repository.applyVpsProbeResult(server, result)
                .onFailure { _events.tryEmit(it.localizedMessage ?: "Error guardando diagnóstico") }
            _events.tryEmit("${server.name}: ${result.diagnosticMessage}")
            _probingServerIds.value = _probingServerIds.value - server.id
        }
    }

    fun probeAllServers() {
        val servers = (vpsServersState.value as? UiState.Success)?.data.orEmpty()
        if (servers.isEmpty()) {
            _events.tryEmit("No hay servidores VPS registrados para comprobar")
            return
        }
        servers.forEach { probeServer(it) }
    }

    fun saveProtocolConfig(protocol: VpnProtocolConfig, server: VpsServer?) {
        viewModelScope.launch {
            _isBusy.value = true
            val repoResult = repository.saveProtocolConfig(protocol)
            if (repoResult.isSuccess && server != null && server.apiEndpoint.isNotBlank()) {
                val remoteRes = vpsApiClient.toggleProtocolOnVps(
                    endpointUrl = server.apiEndpoint,
                    apiToken = server.apiToken,
                    protocolType = protocol.type,
                    enabled = protocol.enabled,
                    port = protocol.port,
                    transport = protocol.transport,
                    timeoutSeconds = currentTimeoutSeconds()
                )
                remoteRes.onSuccess { msg -> _events.tryEmit(msg) }
                    .onFailure { err ->
                        _events.tryEmit("Guardado en panel; aviso VPS: ${err.localizedMessage}")
                    }
            } else {
                repoResult.onSuccess { _events.tryEmit("Protocolo ${protocol.type} actualizado") }
                    .onFailure { _events.tryEmit(it.localizedMessage ?: "Error al guardar protocolo") }
            }
            _isBusy.value = false
        }
    }

    fun addVpnUser(user: VpnUser) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.addVpnUser(user)
                .onSuccess { _events.tryEmit("Usuario VPN ${user.username} creado") }
                .onFailure { _events.tryEmit(it.localizedMessage ?: "Error al crear usuario") }
            _isBusy.value = false
        }
    }

    fun updateVpnUser(user: VpnUser) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.updateVpnUser(user)
                .onSuccess { _events.tryEmit("Usuario ${user.username} actualizado") }
                .onFailure { _events.tryEmit(it.localizedMessage ?: "Error al actualizar usuario") }
            _isBusy.value = false
        }
    }

    fun toggleVpnUserStatus(user: VpnUser) {
        viewModelScope.launch {
            repository.toggleVpnUserStatus(user)
                .onSuccess {
                    val label = if (user.status == "SUSPENDED") "reactivado" else "suspendido"
                    _events.tryEmit("Usuario ${user.username} $label")
                }
                .onFailure { _events.tryEmit(it.localizedMessage ?: "Error cambiando estado") }
        }
    }

    fun deleteVpnUser(user: VpnUser) {
        viewModelScope.launch {
            repository.deleteVpnUser(user)
                .onSuccess { _events.tryEmit("Usuario ${user.username} eliminado") }
                .onFailure { _events.tryEmit(it.localizedMessage ?: "Error al eliminar usuario") }
        }
    }

    fun createConnectionProfile(
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
        onCreated: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val keys = WireGuardCrypto.generateKeyPair()
            val port = if (protocol == VpnProtocolType.WIREGUARD) server.wireguardPort else server.openVpnPort
            var serverPublicKey = WireGuardCrypto.generateServerPublicKeyPreview()
            var remoteNote = ""
            var serverConfigContent: String? = null

            if (pushToVpsAgent && server.apiEndpoint.isNotBlank()) {
                val remoteRes = vpsApiClient.provisionPeerOnVps(
                    endpointUrl = server.apiEndpoint,
                    apiToken = server.apiToken,
                    requestPayload = VpsPeerProvisionRequest(
                        username = vpnUser.username,
                        deviceName = deviceName,
                        protocol = protocol.name,
                        assignedIp = assignedIp,
                        publicKey = keys.publicKeyBase64,
                        presharedKey = keys.presharedKeyBase64
                    ),
                    timeoutSeconds = currentTimeoutSeconds()
                )
                remoteRes.onSuccess { resp ->
                    if (!resp.serverPublicKey.isNullOrBlank()) {
                        serverPublicKey = resp.serverPublicKey
                    }
                    if (!resp.configContent.isNullOrBlank()) {
                        serverConfigContent = resp.configContent
                    }
                    remoteNote = " • Sincronizado con agente VPS"
                }.onFailure { err ->
                    remoteNote = " • Guardado local (VPS: ${err.localizedMessage})"
                }
            }

            val configText = serverConfigContent ?: if (protocol == VpnProtocolType.WIREGUARD) {
                WireGuardCrypto.buildWireGuardConfig(
                    clientPrivateKey = keys.privateKeyBase64,
                    assignedIp = assignedIp,
                    dns = dns,
                    mtu = mtu,
                    serverPublicKey = serverPublicKey,
                    presharedKey = keys.presharedKeyBase64,
                    endpointHost = server.ipAddress,
                    endpointPort = port,
                    keepalive = keepalive
                )
            } else {
                WireGuardCrypto.buildOpenVpnConfig(
                    username = vpnUser.username,
                    deviceName = deviceName,
                    serverIp = server.ipAddress,
                    port = port,
                    transport = "UDP",
                    cipher = "AES-256-GCM",
                    dns = dns,
                    keyMaterialSeed = keys.publicKeyBase64.take(24)
                )
            }

            val device = VpnDevice(
                vpnUserId = vpnUser.id,
                vpnUsername = vpnUser.username,
                serverId = server.id,
                deviceName = deviceName.trim(),
                platform = platform.name,
                assignedIp = assignedIp.trim(),
                publicKeyFingerprint = keys.publicKeyBase64,
                authorized = true,
                connected = false,
                lastSeenEpochMs = System.currentTimeMillis()
            )

            val config = VpnConfigProfile(
                vpnUserId = vpnUser.id,
                vpnUsername = vpnUser.username,
                serverId = server.id,
                serverName = server.name,
                deviceName = deviceName.trim(),
                protocol = protocol.name,
                assignedIp = assignedIp.trim(),
                publicKey = keys.publicKeyBase64,
                privateKey = keys.privateKeyBase64,
                presharedKey = keys.presharedKeyBase64,
                endpoint = "${server.ipAddress}:$port",
                configContent = configText
            )

            repository.createConnectionWithDevice(config, device)
                .onSuccess { configId ->
                    _events.tryEmit("Perfil ${protocol.name} generado para ${vpnUser.username}$remoteNote")
                    onCreated(configId)
                }
                .onFailure { _events.tryEmit(it.localizedMessage ?: "Error al crear conexión") }
            _isBusy.value = false
        }
    }

    fun quickCreateVpnFromVps(
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
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val cleanHost = vpsIpOrHost.trim()
                val existingServers = (vpsServersState.value as? UiState.Success)?.data.orEmpty()
                val existingUsers = (vpnUsersState.value as? UiState.Success)?.data.orEmpty()
                val existingDevices = (devicesState.value as? UiState.Success)?.data.orEmpty()
                val profile = (profileState.value as? UiState.Success)?.data

                val normalizedEndpoint = apiEndpoint.trim().ifBlank { "http://$cleanHost:8443" }
                val cleanName = vpsName.trim().ifBlank { "VPS $cleanHost" }
                val cleanLocation = vpsLocation.trim().ifBlank { "Servidor Propio" }

                val matchedServer = existingServers.firstOrNull {
                    it.ipAddress.equals(cleanHost, ignoreCase = true)
                }

                val server: VpsServer = if (matchedServer != null) {
                    val updated = matchedServer.copy(
                        name = if (vpsName.isNotBlank()) cleanName else matchedServer.name,
                        apiEndpoint = if (apiEndpoint.isNotBlank()) normalizedEndpoint else matchedServer.apiEndpoint.ifBlank { normalizedEndpoint },
                        apiToken = if (apiToken.isNotBlank()) apiToken.trim() else matchedServer.apiToken,
                        wireguardPort = wireguardPort,
                        openVpnPort = openVpnPort,
                        wireguardEnabled = protocol == VpnProtocolType.WIREGUARD || matchedServer.wireguardEnabled,
                        openVpnEnabled = protocol == VpnProtocolType.OPENVPN || matchedServer.openVpnEnabled
                    )
                    repository.updateVpsServer(updated)
                    updated
                } else {
                    val newServer = VpsServer(
                        name = cleanName,
                        ipAddress = cleanHost,
                        location = cleanLocation,
                        apiEndpoint = normalizedEndpoint,
                        apiToken = apiToken.trim(),
                        wireguardPort = wireguardPort,
                        openVpnPort = openVpnPort,
                        wireguardEnabled = protocol == VpnProtocolType.WIREGUARD,
                        openVpnEnabled = protocol == VpnProtocolType.OPENVPN
                    )
                    val newServerId = repository.addVpsServer(newServer).getOrThrow()
                    newServer.copy(id = newServerId)
                }

                var probeResult: VpsProbeResult? = null
                var serverPublicKey = WireGuardCrypto.generateServerPublicKeyPreview()
                if (server.apiEndpoint.isNotBlank()) {
                    val probe = vpsApiClient.probeServerHealth(
                        endpointUrl = server.apiEndpoint,
                        apiToken = server.apiToken,
                        timeoutSeconds = currentTimeoutSeconds()
                    )
                    probeResult = probe
                    repository.applyVpsProbeResult(server, probe)
                    if (!probe.serverPublicKey.isNullOrBlank()) {
                        serverPublicKey = probe.serverPublicKey
                    }
                }

                val cleanDev = deviceName.trim().ifBlank { "Mi Dispositivo" }
                val autoUsername = cleanDev.lowercase()
                    .replace(Regex("[^a-z0-9_]"), "_")
                    .trim('_')
                    .ifBlank { "vpn_user" }
                    .take(24)

                val matchedUser = existingUsers.firstOrNull {
                    it.serverId == server.id && it.username.equals(autoUsername, ignoreCase = true)
                } ?: existingUsers.firstOrNull { it.serverId == server.id }

                val vpnUser: VpnUser = if (matchedUser != null) {
                    matchedUser
                } else {
                    val newUser = VpnUser(
                        username = autoUsername,
                        email = currentUserEmail,
                        serverId = server.id,
                        serverName = server.name,
                        protocol = if (protocol == VpnProtocolType.WIREGUARD) ProtocolSelection.WIREGUARD.name else ProtocolSelection.OPENVPN.name,
                        status = VpnUserStatus.ACTIVE.name,
                        maxDevices = 5L,
                        dataLimitGb = 0L,
                        expiresAtEpochMs = 0L,
                        notes = "Creado automáticamente desde Asistente Rápido"
                    )
                    val newUserId = repository.addVpnUser(newUser).getOrThrow()
                    newUser.copy(id = newUserId)
                }

                val nextOctet = (existingDevices.size + 2).coerceIn(2, 250)
                val assignedIp = "10.8.0.$nextOctet/32"
                val dns = profile?.defaultDns ?: "1.1.1.1, 8.8.8.8"
                val mtu = profile?.defaultMtu ?: 1420L
                val port = if (protocol == VpnProtocolType.WIREGUARD) server.wireguardPort else server.openVpnPort
                val keys = WireGuardCrypto.generateKeyPair()
                var serverConfigContent: String? = null
                var remoteStatusNote = ""

                if (server.apiEndpoint.isNotBlank()) {
                    vpsApiClient.provisionPeerOnVps(
                        endpointUrl = server.apiEndpoint,
                        apiToken = server.apiToken,
                        requestPayload = VpsPeerProvisionRequest(
                            username = vpnUser.username,
                            deviceName = cleanDev,
                            protocol = protocol.name,
                            assignedIp = assignedIp,
                            publicKey = keys.publicKeyBase64,
                            presharedKey = keys.presharedKeyBase64
                        ),
                        timeoutSeconds = currentTimeoutSeconds()
                    ).onSuccess { resp ->
                        if (!resp.serverPublicKey.isNullOrBlank()) {
                            serverPublicKey = resp.serverPublicKey
                        }
                        if (!resp.configContent.isNullOrBlank()) {
                            serverConfigContent = resp.configContent
                        }
                        remoteStatusNote = " • Sincronizado con tu VPS"
                    }.onFailure {
                        remoteStatusNote = " • Perfil listo (ejecuta el comando en tu VPS si aún no lo instalas)"
                    }
                }

                val configText = serverConfigContent ?: if (protocol == VpnProtocolType.WIREGUARD) {
                    WireGuardCrypto.buildWireGuardConfig(
                        clientPrivateKey = keys.privateKeyBase64,
                        assignedIp = assignedIp,
                        dns = dns,
                        mtu = mtu,
                        serverPublicKey = serverPublicKey,
                        presharedKey = keys.presharedKeyBase64,
                        endpointHost = server.ipAddress,
                        endpointPort = port,
                        keepalive = 25L
                    )
                } else {
                    WireGuardCrypto.buildOpenVpnConfig(
                        username = vpnUser.username,
                        deviceName = cleanDev,
                        serverIp = server.ipAddress,
                        port = port,
                        transport = "UDP",
                        cipher = "AES-256-GCM",
                        dns = dns,
                        keyMaterialSeed = keys.publicKeyBase64.take(24)
                    )
                }

                val device = VpnDevice(
                    vpnUserId = vpnUser.id,
                    vpnUsername = vpnUser.username,
                    serverId = server.id,
                    deviceName = cleanDev,
                    platform = platform.name,
                    assignedIp = assignedIp,
                    publicKeyFingerprint = keys.publicKeyBase64,
                    authorized = true,
                    connected = false,
                    lastSeenEpochMs = System.currentTimeMillis()
                )

                val config = VpnConfigProfile(
                    vpnUserId = vpnUser.id,
                    vpnUsername = vpnUser.username,
                    serverId = server.id,
                    serverName = server.name,
                    deviceName = cleanDev,
                    protocol = protocol.name,
                    assignedIp = assignedIp,
                    publicKey = keys.publicKeyBase64,
                    privateKey = keys.privateKeyBase64,
                    presharedKey = keys.presharedKeyBase64,
                    endpoint = "${server.ipAddress}:$port",
                    configContent = configText
                )

                val configId = repository.createConnectionWithDevice(config, device).getOrThrow()
                _events.tryEmit("¡Tu VPN está lista para descargar!$remoteStatusNote")
                onReady(config.copy(id = configId), probeResult)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.tryEmit(e.localizedMessage ?: "No se pudo crear la VPN")
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun deleteVpnConfig(config: VpnConfigProfile) {
        viewModelScope.launch {
            repository.deleteVpnConfig(config)
                .onSuccess { _events.tryEmit("Configuración eliminada") }
                .onFailure { _events.tryEmit(it.localizedMessage ?: "Error al eliminar configuración") }
        }
    }

    fun toggleDeviceAuthorization(device: VpnDevice) {
        viewModelScope.launch {
            repository.toggleDeviceAuthorization(device)
                .onSuccess {
                    val state = if (device.authorized) "revocado" else "autorizado"
                    _events.tryEmit("Dispositivo ${device.deviceName} $state")
                }
                .onFailure { _events.tryEmit(it.localizedMessage ?: "Error actualizando dispositivo") }
        }
    }

    fun deleteDevice(device: VpnDevice) {
        viewModelScope.launch {
            repository.deleteDevice(device)
                .onSuccess { _events.tryEmit("Dispositivo ${device.deviceName} eliminado") }
                .onFailure { _events.tryEmit(it.localizedMessage ?: "Error eliminando dispositivo") }
        }
    }

    fun addMember(member: PanelMember) {
        viewModelScope.launch {
            repository.addMember(member)
                .onSuccess { _events.tryEmit("Miembro ${member.name} añadido (${member.role})") }
                .onFailure { _events.tryEmit(it.localizedMessage ?: "Error al añadir miembro") }
        }
    }

    fun updateMember(member: PanelMember) {
        viewModelScope.launch {
            repository.updateMember(member)
                .onSuccess { _events.tryEmit("Rol de ${member.name} actualizado") }
                .onFailure { _events.tryEmit(it.localizedMessage ?: "Error al actualizar miembro") }
        }
    }

    fun deleteMember(member: PanelMember) {
        viewModelScope.launch {
            if (member.id == currentUserId) {
                _events.tryEmit("No puedes eliminar tu propia cuenta de Super Admin")
                return@launch
            }
            repository.deleteMember(member)
                .onSuccess { _events.tryEmit("Miembro eliminado") }
                .onFailure { _events.tryEmit(it.localizedMessage ?: "Error al eliminar miembro") }
        }
    }

    fun markNotificationRead(id: String) {
        viewModelScope.launch { repository.markNotificationRead(id) }
    }

    fun deleteNotification(id: String) {
        viewModelScope.launch { repository.deleteNotification(id) }
    }

    fun runExpirationAudit() {
        viewModelScope.launch {
            val users = (vpnUsersState.value as? UiState.Success)?.data.orEmpty()
            val profile = (profileState.value as? UiState.Success)?.data
            val now = System.currentTimeMillis()
            val sevenDaysMs = 7L * 24L * 60L * 60L * 1000L
            var alertsCreated = 0

            users.forEach { user ->
                if (user.expiresAtEpochMs > 0L) {
                    if (user.expiresAtEpochMs <= now && user.status == VpnUserStatus.ACTIVE.name) {
                        if (profile?.autoSuspendExpired == true) {
                            repository.updateVpnUser(user.copy(status = VpnUserStatus.EXPIRED.name))
                        }
                        repository.createNotification(
                            title = "Usuario VPN expirado: ${user.username}",
                            message = "La suscripción de ${user.username} en ${user.serverName} ha vencido.",
                            severity = NotificationSeverity.CRITICAL,
                            category = "VENCIMIENTO"
                        )
                        alertsCreated++
                    } else if (user.expiresAtEpochMs - now in 1L..sevenDaysMs && user.status == VpnUserStatus.ACTIVE.name) {
                        repository.createNotification(
                            title = "Próximo vencimiento: ${user.username}",
                            message = "La cuenta ${user.username} vence en menos de 7 días.",
                            severity = NotificationSeverity.WARNING,
                            category = "VENCIMIENTO"
                        )
                        alertsCreated++
                    }
                }
            }

            repository.recordAuditLog(
                action = "EXPIRATION_AUDIT",
                targetType = "SEGURIDAD",
                targetName = "Cuentas VPN",
                details = "Revisión ejecutada: $alertsCreated alertas generadas",
                status = LogStatus.SUCCESS
            )
            _events.tryEmit(
                if (alertsCreated > 0) "$alertsCreated alertas de vencimiento generadas"
                else "Auditoría completada: sin cuentas por vencer"
            )
        }
    }

    fun clearActivityLog(logId: String) {
        viewModelScope.launch { repository.clearActivityLog(logId) }
    }

    fun updateSettings(
        panelName: String,
        defaultDns: String,
        defaultMtu: Long,
        defaultSubnet: String,
        autoSuspendExpired: Boolean,
        apiTimeoutSeconds: Long,
        maskSensitiveKeys: Boolean
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.updateSettings(
                panelName = panelName,
                defaultDns = defaultDns,
                defaultMtu = defaultMtu,
                defaultSubnet = defaultSubnet,
                autoSuspendExpired = autoSuspendExpired,
                apiTimeoutSeconds = apiTimeoutSeconds,
                maskSensitiveKeys = maskSensitiveKeys
            ).onSuccess {
                _events.tryEmit("Configuración del panel guardada")
            }.onFailure {
                _events.tryEmit(it.localizedMessage ?: "Error al guardar configuración")
            }
            _isBusy.value = false
        }
    }

    companion object {
        private const val TAG = "PanelVpnVM"
        private const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
