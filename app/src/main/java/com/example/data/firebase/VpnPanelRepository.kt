package com.example.data.firebase

import com.example.data.model.ActivityLog
import com.example.data.model.LogStatus
import com.example.data.model.NotificationSeverity
import com.example.data.model.PanelMember
import com.example.data.model.PanelNotification
import com.example.data.model.UserProfile
import com.example.data.model.VpnConfigProfile
import com.example.data.model.VpnDevice
import com.example.data.model.VpnProtocolConfig
import com.example.data.model.VpnUser
import com.example.data.model.VpsServer
import com.example.data.remote.VpsProbeResult
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class VpnPanelRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Debe iniciar sesión con Google para operar en Panel VPN.")
    }

    private fun currentActorEmail(): String {
        return auth.currentUser?.email?.takeIf { it.isNotBlank() } ?: "admin@panelvpn"
    }

    private fun userRoot(uid: String) = db.collection("users").document(uid)

    private inline fun <reified T> observeSubcollection(
        userId: String,
        collectionName: String,
        orderField: String = "createdAt",
        descending: Boolean = true
    ): Flow<List<T>> = flow {
        require(userId.isNotBlank()) { "UID inválido" }
        val path = "users/$userId/$collectionName"
        val direction = if (descending) Query.Direction.DESCENDING else Query.Direction.ASCENDING
        emitAll(
            userRoot(userId).collection(collectionName)
                .orderBy(orderField, direction)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(T::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeProfile(userId: String): Flow<UserProfile?> = flow {
        require(userId.isNotBlank()) { "UID inválido" }
        val path = "users/$userId"
        emitAll(
            userRoot(userId)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeVpsServers(userId: String): Flow<List<VpsServer>> =
        observeSubcollection(userId, "vpsServers")

    fun observeProtocols(userId: String): Flow<List<VpnProtocolConfig>> =
        observeSubcollection(userId, "protocols")

    fun observeVpnUsers(userId: String): Flow<List<VpnUser>> =
        observeSubcollection(userId, "vpnUsers")

    fun observeVpnConfigs(userId: String): Flow<List<VpnConfigProfile>> =
        observeSubcollection(userId, "vpnConfigs")

    fun observeDevices(userId: String): Flow<List<VpnDevice>> =
        observeSubcollection(userId, "devices")

    fun observeMembers(userId: String): Flow<List<PanelMember>> =
        observeSubcollection(userId, "members")

    fun observeNotifications(userId: String): Flow<List<PanelNotification>> =
        observeSubcollection(userId, "notifications")

    fun observeActivityLogs(userId: String): Flow<List<ActivityLog>> =
        observeSubcollection(userId, "activityLogs")

    suspend fun ensureWorkspaceInitialized(
        userId: String,
        displayName: String,
        email: String
    ): Result<Unit> {
        val docPath = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Sesión modificada" }
            val profileRef = userRoot(uid)
            val snap = profileRef.get().await()
            val safeName = displayName.trim().ifBlank { "Administrador" }.take(100)
            val safeEmail = email.trim().ifBlank { "admin@panelvpn.local" }.take(160)

            if (!snap.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "email" to safeEmail,
                    "role" to "SUPER_ADMIN",
                    "panelName" to "Panel VPN",
                    "defaultDns" to "1.1.1.1, 8.8.8.8",
                    "defaultMtu" to 1420L,
                    "defaultSubnet" to "10.8.0.0/24",
                    "autoSuspendExpired" to true,
                    "apiTimeoutSeconds" to 10L,
                    "maskSensitiveKeys" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                profileRef.set(payload).await()

                // Register workspace owner as SUPER_ADMIN member
                val ownerMemberRef = profileRef.collection("members").document(uid)
                ownerMemberRef.set(
                    PanelMember(
                        name = safeName,
                        email = safeEmail,
                        role = "SUPER_ADMIN",
                        status = "ACTIVE",
                        twoFactorRequired = true
                    ).toCreateMap()
                ).await()

                recordAuditLogInternal(
                    uid = uid,
                    action = "WORKSPACE_INIT",
                    targetType = "SEGURIDAD",
                    targetName = "Panel VPN",
                    details = "Espacio de trabajo inicializado para $safeEmail (SUPER_ADMIN)"
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateSettings(
        panelName: String,
        defaultDns: String,
        defaultMtu: Long,
        defaultSubnet: String,
        autoSuspendExpired: Boolean,
        apiTimeoutSeconds: Long,
        maskSensitiveKeys: Boolean
    ): Result<Unit> {
        var path = "users/unknown"
        return try {
            val uid = requireUserId()
            path = "users/$uid"
            require(panelName.trim().isNotEmpty()) { "El nombre del panel es obligatorio" }
            require(defaultDns.trim().length >= 3) { "DNS inválido" }
            require(defaultMtu in 1200L..1500L) { "MTU debe estar entre 1200 y 1500" }
            require(defaultSubnet.trim().length >= 5) { "Subred inválida" }

            userRoot(uid).update(
                mapOf(
                    "panelName" to panelName.trim().take(80),
                    "defaultDns" to defaultDns.trim().take(120),
                    "defaultMtu" to defaultMtu,
                    "defaultSubnet" to defaultSubnet.trim().take(40),
                    "autoSuspendExpired" to autoSuspendExpired,
                    "apiTimeoutSeconds" to apiTimeoutSeconds.coerceIn(3L, 60L),
                    "maskSensitiveKeys" to maskSensitiveKeys,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            recordAuditLogInternal(
                uid = uid,
                action = "SETTINGS_UPDATED",
                targetType = "CONFIGURACION",
                targetName = panelName.trim(),
                details = "DNS=$defaultDns, MTU=$defaultMtu, EnmascararClaves=$maskSensitiveKeys"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- VPS Servers ---
    suspend fun addVpsServer(server: VpsServer): Result<String> {
        var path = "users/unknown/vpsServers"
        return try {
            val uid = requireUserId()
            require(server.name.trim().isNotEmpty()) { "Nombre de VPS obligatorio" }
            require(server.ipAddress.trim().length >= 3) { "Dirección IP obligatoria" }
            require(server.location.trim().isNotEmpty()) { "Ubicación obligatoria" }

            val docRef = userRoot(uid).collection("vpsServers").document()
            path = docRef.path
            docRef.set(server.toCreateMap()).await()

            // Create initial WireGuard & OpenVPN protocol definitions for this VPS
            val protoCol = userRoot(uid).collection("protocols")
            protoCol.document().set(
                VpnProtocolConfig(
                    serverId = docRef.id,
                    serverName = server.name.trim().take(80),
                    type = "WIREGUARD",
                    enabled = server.wireguardEnabled,
                    port = server.wireguardPort,
                    transport = "UDP",
                    subnet = "10.8.0.0/24",
                    dns = "1.1.1.1, 8.8.8.8",
                    mtu = 1420L,
                    cipher = "ChaCha20-Poly1305",
                    keepaliveSeconds = 25L
                ).toCreateMap()
            ).await()

            protoCol.document().set(
                VpnProtocolConfig(
                    serverId = docRef.id,
                    serverName = server.name.trim().take(80),
                    type = "OPENVPN",
                    enabled = server.openVpnEnabled,
                    port = server.openVpnPort,
                    transport = "UDP",
                    subnet = "10.9.0.0/24",
                    dns = "1.1.1.1, 8.8.8.8",
                    mtu = 1500L,
                    cipher = "AES-256-GCM",
                    keepaliveSeconds = 10L
                ).toCreateMap()
            ).await()

            recordAuditLogInternal(
                uid = uid,
                action = "VPS_CREATED",
                targetType = "VPS",
                targetName = server.name.trim(),
                details = "IP ${server.ipAddress.trim()} • ${server.location.trim()}"
            )
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateVpsServer(server: VpsServer): Result<Unit> {
        var path = "users/unknown/vpsServers/${server.id}"
        return try {
            val uid = requireUserId()
            require(server.id.isNotBlank()) { "ID de servidor inválido" }
            val ref = userRoot(uid).collection("vpsServers").document(server.id)
            path = ref.path
            ref.update(
                mapOf(
                    "name" to server.name.trim().take(80),
                    "ipAddress" to server.ipAddress.trim().take(100),
                    "location" to server.location.trim().take(100),
                    "apiEndpoint" to server.apiEndpoint.trim().take(300),
                    "apiToken" to server.apiToken.trim().take(300),
                    "sshPort" to server.sshPort.coerceIn(1L, 65535L),
                    "wireguardPort" to server.wireguardPort.coerceIn(1L, 65535L),
                    "openVpnPort" to server.openVpnPort.coerceIn(1L, 65535L),
                    "wireguardEnabled" to server.wireguardEnabled,
                    "openVpnEnabled" to server.openVpnEnabled,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            recordAuditLogInternal(
                uid = uid,
                action = "VPS_UPDATED",
                targetType = "VPS",
                targetName = server.name.trim(),
                details = "Configuración actualizada (${server.ipAddress.trim()})"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun applyVpsProbeResult(server: VpsServer, probe: VpsProbeResult): Result<Unit> {
        var path = "users/unknown/vpsServers/${server.id}"
        return try {
            val uid = requireUserId()
            val ref = userRoot(uid).collection("vpsServers").document(server.id)
            path = ref.path
            val newStatus = if (probe.reachable) "ONLINE" else if (probe.httpCode in 400..599) "ERROR" else "OFFLINE"

            val updates = mutableMapOf<String, Any>(
                "status" to newStatus,
                "latencyMs" to probe.latencyMs.coerceIn(0L, 60000L),
                "lastDiagnostic" to probe.diagnosticMessage.take(500),
                "lastCheckedAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            probe.cpuPercent?.let { updates["cpuPercent"] = it.coerceIn(0.0, 100.0) }
            probe.ramUsedMb?.let { updates["ramUsedMb"] = it.coerceAtLeast(0L) }
            probe.ramTotalMb?.let { updates["ramTotalMb"] = it.coerceAtLeast(0L) }
            probe.storageUsedGb?.let { updates["storageUsedGb"] = it.coerceAtLeast(0L) }
            probe.storageTotalGb?.let { updates["storageTotalGb"] = it.coerceAtLeast(0L) }
            probe.activeConnections?.let { updates["activeConnections"] = it.coerceAtLeast(0L) }
            probe.wireguardEnabled?.let { updates["wireguardEnabled"] = it }
            probe.openVpnEnabled?.let { updates["openVpnEnabled"] = it }

            ref.update(updates).await()

            recordAuditLogInternal(
                uid = uid,
                action = "VPS_HEALTH_CHECK",
                targetType = "VPS",
                targetName = server.name,
                details = probe.diagnosticMessage.take(400),
                status = if (probe.reachable) LogStatus.SUCCESS else LogStatus.FAILED
            )

            if (!probe.reachable) {
                createNotificationInternal(
                    uid = uid,
                    title = "VPS sin conexión: ${server.name}",
                    message = probe.diagnosticMessage.take(480),
                    severity = NotificationSeverity.CRITICAL,
                    category = "VPS"
                )
            } else if ((probe.cpuPercent ?: 0.0) >= 85.0) {
                createNotificationInternal(
                    uid = uid,
                    title = "Uso alto de CPU en ${server.name}",
                    message = "CPU al ${probe.cpuPercent}% (${server.ipAddress})",
                    severity = NotificationSeverity.WARNING,
                    category = "RECURSOS"
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteVpsServer(server: VpsServer): Result<Unit> {
        var path = "users/unknown/vpsServers/${server.id}"
        return try {
            val uid = requireUserId()
            val ref = userRoot(uid).collection("vpsServers").document(server.id)
            path = ref.path
            ref.delete().await()

            recordAuditLogInternal(
                uid = uid,
                action = "VPS_DELETED",
                targetType = "VPS",
                targetName = server.name,
                details = "Servidor ${server.ipAddress} eliminado del panel"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Protocols ---
    suspend fun saveProtocolConfig(protocol: VpnProtocolConfig): Result<Unit> {
        var path = "users/unknown/protocols"
        return try {
            val uid = requireUserId()
            if (protocol.id.isBlank()) {
                val ref = userRoot(uid).collection("protocols").document()
                path = ref.path
                ref.set(protocol.toCreateMap()).await()
            } else {
                val ref = userRoot(uid).collection("protocols").document(protocol.id)
                path = ref.path
                ref.update(
                    mapOf(
                        "enabled" to protocol.enabled,
                        "port" to protocol.port.coerceIn(1L, 65535L),
                        "transport" to protocol.parsedTransport.name,
                        "subnet" to protocol.subnet.trim().take(40),
                        "dns" to protocol.dns.trim().take(120),
                        "mtu" to protocol.mtu.coerceIn(1200L, 1500L),
                        "cipher" to protocol.cipher.trim().take(60),
                        "keepaliveSeconds" to protocol.keepaliveSeconds.coerceIn(0L, 300L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            recordAuditLogInternal(
                uid = uid,
                action = "PROTOCOL_UPDATED",
                targetType = "PROTOCOLO",
                targetName = "${protocol.type} (${protocol.serverName})",
                details = "Estado=${if (protocol.enabled) "ACTIVO" else "INACTIVO"}, Puerto=${protocol.port}/${protocol.transport}"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    // --- VPN Users ---
    suspend fun addVpnUser(user: VpnUser): Result<String> {
        var path = "users/unknown/vpnUsers"
        return try {
            val uid = requireUserId()
            require(user.username.trim().length >= 2) { "El usuario debe tener al menos 2 caracteres" }
            require(user.serverId.isNotBlank()) { "Selecciona un servidor VPS" }

            val ref = userRoot(uid).collection("vpnUsers").document()
            path = ref.path
            ref.set(user.toCreateMap()).await()

            recordAuditLogInternal(
                uid = uid,
                action = "VPN_USER_CREATED",
                targetType = "USUARIO_VPN",
                targetName = user.username.trim(),
                details = "VPS=${user.serverName}, Protocolo=${user.protocol}, MaxDisp=${user.maxDevices}"
            )
            Result.success(ref.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateVpnUser(user: VpnUser): Result<Unit> {
        var path = "users/unknown/vpnUsers/${user.id}"
        return try {
            val uid = requireUserId()
            require(user.id.isNotBlank()) { "ID de usuario inválido" }
            val ref = userRoot(uid).collection("vpnUsers").document(user.id)
            path = ref.path
            ref.update(
                mapOf(
                    "username" to user.username.trim().take(60),
                    "email" to user.email.trim().take(160),
                    "serverId" to user.serverId.trim().take(128),
                    "serverName" to user.serverName.trim().take(80),
                    "protocol" to user.parsedProtocol.name,
                    "status" to user.parsedStatus.name,
                    "maxDevices" to user.maxDevices.coerceIn(1L, 100L),
                    "dataLimitGb" to user.dataLimitGb.coerceIn(0L, 100000L),
                    "expiresAtEpochMs" to user.expiresAtEpochMs.coerceAtLeast(0L),
                    "notes" to user.notes.trim().take(300),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            recordAuditLogInternal(
                uid = uid,
                action = "VPN_USER_UPDATED",
                targetType = "USUARIO_VPN",
                targetName = user.username.trim(),
                details = "Estado=${user.status}, Protocolo=${user.protocol}, MaxDisp=${user.maxDevices}"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleVpnUserStatus(user: VpnUser): Result<Unit> {
        var path = "users/unknown/vpnUsers/${user.id}"
        return try {
            val uid = requireUserId()
            val nextStatus = if (user.status == "SUSPENDED") "ACTIVE" else "SUSPENDED"
            val ref = userRoot(uid).collection("vpnUsers").document(user.id)
            path = ref.path
            ref.update(
                mapOf(
                    "status" to nextStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            recordAuditLogInternal(
                uid = uid,
                action = if (nextStatus == "SUSPENDED") "VPN_USER_SUSPENDED" else "VPN_USER_ACTIVATED",
                targetType = "USUARIO_VPN",
                targetName = user.username,
                details = "Estado cambiado a $nextStatus"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteVpnUser(user: VpnUser): Result<Unit> {
        var path = "users/unknown/vpnUsers/${user.id}"
        return try {
            val uid = requireUserId()
            val ref = userRoot(uid).collection("vpnUsers").document(user.id)
            path = ref.path
            ref.delete().await()

            recordAuditLogInternal(
                uid = uid,
                action = "VPN_USER_DELETED",
                targetType = "USUARIO_VPN",
                targetName = user.username,
                details = "Cuenta VPN eliminada"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Connections, Configs & Devices ---
    suspend fun createConnectionWithDevice(
        config: VpnConfigProfile,
        device: VpnDevice
    ): Result<String> {
        var path = "users/unknown/vpnConfigs"
        return try {
            val uid = requireUserId()
            val deviceRef = userRoot(uid).collection("devices").document()
            val configRef = userRoot(uid).collection("vpnConfigs").document()
            path = configRef.path

            val batch = db.batch()
            batch.set(deviceRef, device.toCreateMap())
            batch.set(configRef, config.copy(deviceId = deviceRef.id).toCreateMap())
            batch.commit().await()

            recordAuditLogInternal(
                uid = uid,
                action = "CONNECTION_CREATED",
                targetType = "CONFIG_VPN",
                targetName = "${config.vpnUsername} • ${config.deviceName}",
                details = "${config.protocol} en ${config.serverName} (${config.assignedIp})"
            )
            Result.success(configRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteVpnConfig(config: VpnConfigProfile): Result<Unit> {
        var path = "users/unknown/vpnConfigs/${config.id}"
        return try {
            val uid = requireUserId()
            val ref = userRoot(uid).collection("vpnConfigs").document(config.id)
            path = ref.path
            ref.delete().await()

            recordAuditLogInternal(
                uid = uid,
                action = "CONFIG_DELETED",
                targetType = "CONFIG_VPN",
                targetName = "${config.vpnUsername} • ${config.deviceName}",
                details = "Perfil ${config.protocol} eliminado"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleDeviceAuthorization(device: VpnDevice): Result<Unit> {
        var path = "users/unknown/devices/${device.id}"
        return try {
            val uid = requireUserId()
            val nextAuth = !device.authorized
            val ref = userRoot(uid).collection("devices").document(device.id)
            path = ref.path
            ref.update(
                mapOf(
                    "authorized" to nextAuth,
                    "connected" to if (!nextAuth) false else device.connected,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            recordAuditLogInternal(
                uid = uid,
                action = if (nextAuth) "DEVICE_AUTHORIZED" else "DEVICE_REVOKED",
                targetType = "DISPOSITIVO",
                targetName = device.deviceName,
                details = "Usuario=${device.vpnUsername}, IP=${device.assignedIp}"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteDevice(device: VpnDevice): Result<Unit> {
        var path = "users/unknown/devices/${device.id}"
        return try {
            val uid = requireUserId()
            val ref = userRoot(uid).collection("devices").document(device.id)
            path = ref.path
            ref.delete().await()

            recordAuditLogInternal(
                uid = uid,
                action = "DEVICE_DELETED",
                targetType = "DISPOSITIVO",
                targetName = device.deviceName,
                details = "Dispositivo de ${device.vpnUsername} eliminado"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Members & Roles ---
    suspend fun addMember(member: PanelMember): Result<Unit> {
        var path = "users/unknown/members"
        return try {
            val uid = requireUserId()
            require(member.name.trim().isNotEmpty()) { "Nombre obligatorio" }
            require(member.email.trim().length >= 3) { "Correo obligatorio" }
            val ref = userRoot(uid).collection("members").document()
            path = ref.path
            ref.set(member.toCreateMap()).await()

            recordAuditLogInternal(
                uid = uid,
                action = "MEMBER_ADDED",
                targetType = "MIEMBRO",
                targetName = member.email.trim(),
                details = "Rol=${member.role}, 2FA=${member.twoFactorRequired}"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateMember(member: PanelMember): Result<Unit> {
        var path = "users/unknown/members/${member.id}"
        return try {
            val uid = requireUserId()
            val ref = userRoot(uid).collection("members").document(member.id)
            path = ref.path
            ref.update(
                mapOf(
                    "name" to member.name.trim().take(80),
                    "email" to member.email.trim().take(160),
                    "role" to member.parsedRole.name,
                    "status" to member.parsedStatus.name,
                    "twoFactorRequired" to member.twoFactorRequired,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            recordAuditLogInternal(
                uid = uid,
                action = "MEMBER_UPDATED",
                targetType = "MIEMBRO",
                targetName = member.email,
                details = "Rol=${member.role}, Estado=${member.status}"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteMember(member: PanelMember): Result<Unit> {
        var path = "users/unknown/members/${member.id}"
        return try {
            val uid = requireUserId()
            val ref = userRoot(uid).collection("members").document(member.id)
            path = ref.path
            ref.delete().await()

            recordAuditLogInternal(
                uid = uid,
                action = "MEMBER_REMOVED",
                targetType = "MIEMBRO",
                targetName = member.email,
                details = "Miembro eliminado del panel"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Notifications ---
    suspend fun markNotificationRead(notificationId: String): Result<Unit> {
        var path = "users/unknown/notifications/$notificationId"
        return try {
            val uid = requireUserId()
            val ref = userRoot(uid).collection("notifications").document(notificationId)
            path = ref.path
            ref.update(
                mapOf(
                    "read" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteNotification(notificationId: String): Result<Unit> {
        var path = "users/unknown/notifications/$notificationId"
        return try {
            val uid = requireUserId()
            val ref = userRoot(uid).collection("notifications").document(notificationId)
            path = ref.path
            ref.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun createNotification(
        title: String,
        message: String,
        severity: NotificationSeverity,
        category: String
    ): Result<Unit> {
        var path = "users/unknown/notifications"
        return try {
            val uid = requireUserId()
            path = "users/$uid/notifications"
            createNotificationInternal(uid, title, message, severity, category)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun clearActivityLog(logId: String): Result<Unit> {
        var path = "users/unknown/activityLogs/$logId"
        return try {
            val uid = requireUserId()
            val ref = userRoot(uid).collection("activityLogs").document(logId)
            path = ref.path
            ref.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun recordAuditLog(
        action: String,
        targetType: String,
        targetName: String,
        details: String,
        status: LogStatus = LogStatus.SUCCESS
    ): Result<Unit> {
        var path = "users/unknown/activityLogs"
        return try {
            val uid = requireUserId()
            path = "users/$uid/activityLogs"
            recordAuditLogInternal(uid, action, targetType, targetName, details, status)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    private suspend fun recordAuditLogInternal(
        uid: String,
        action: String,
        targetType: String,
        targetName: String,
        details: String,
        status: LogStatus = LogStatus.SUCCESS
    ) {
        val log = ActivityLog(
            actorEmail = currentActorEmail(),
            action = action,
            targetType = targetType,
            targetName = targetName,
            details = details,
            status = status.name
        )
        userRoot(uid).collection("activityLogs").document().set(log.toCreateMap()).await()
    }

    private suspend fun createNotificationInternal(
        uid: String,
        title: String,
        message: String,
        severity: NotificationSeverity,
        category: String
    ) {
        val notif = PanelNotification(
            title = title,
            message = message,
            severity = severity.name,
            category = category,
            read = false
        )
        userRoot(uid).collection("notifications").document().set(notif.toCreateMap()).await()
    }
}
