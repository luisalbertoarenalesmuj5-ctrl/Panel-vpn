package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties

inline fun <reified T : Enum<T>> enumValueOrDefault(raw: String?, default: T): T {
    if (raw.isNullOrBlank()) return default
    return enumValues<T>().firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: default
}

enum class VpsStatus { ONLINE, OFFLINE, ERROR, PENDING }
enum class VpnProtocolType { WIREGUARD, OPENVPN }
enum class ProtocolSelection { WIREGUARD, OPENVPN, BOTH }
enum class TransportType { UDP, TCP }
enum class VpnUserStatus { ACTIVE, SUSPENDED, EXPIRED }
enum class DevicePlatform { ANDROID, IOS, WINDOWS, MACOS, LINUX, ROUTER }
enum class MemberRole { SUPER_ADMIN, ADMIN, OPERATOR, VIEWER }
enum class MemberStatus { ACTIVE, INVITED, SUSPENDED }
enum class NotificationSeverity { INFO, WARNING, CRITICAL }
enum class LogStatus { SUCCESS, FAILED }

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val role: String = "SUPER_ADMIN",
    val panelName: String = "Panel VPN",
    val defaultDns: String = "1.1.1.1, 8.8.8.8",
    val defaultMtu: Long = 1420L,
    val defaultSubnet: String = "10.8.0.0/24",
    val autoSuspendExpired: Boolean = true,
    val apiTimeoutSeconds: Long = 10L,
    val maskSensitiveKeys: Boolean = true,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class VpsServer(
    @DocumentId val id: String = "",
    val name: String = "",
    val ipAddress: String = "",
    val location: String = "",
    val apiEndpoint: String = "",
    val apiToken: String = "",
    val sshPort: Long = 22L,
    val wireguardPort: Long = 51820L,
    val openVpnPort: Long = 1194L,
    val status: String = VpsStatus.PENDING.name,
    val cpuPercent: Double = 0.0,
    val ramUsedMb: Long = 0L,
    val ramTotalMb: Long = 0L,
    val storageUsedGb: Long = 0L,
    val storageTotalGb: Long = 0L,
    val latencyMs: Long = 0L,
    val activeConnections: Long = 0L,
    val wireguardEnabled: Boolean = true,
    val openVpnEnabled: Boolean = false,
    val lastDiagnostic: String = "Pendiente de verificación",
    val lastCheckedAt: Timestamp? = null,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    val parsedStatus: VpsStatus get() = enumValueOrDefault(status, VpsStatus.PENDING)

    fun toCreateMap(): Map<String, Any> = mapOf(
        "name" to name.trim().take(80),
        "ipAddress" to ipAddress.trim().take(100),
        "location" to location.trim().take(100),
        "apiEndpoint" to apiEndpoint.trim().take(300),
        "apiToken" to apiToken.trim().take(300),
        "sshPort" to sshPort.coerceIn(1L, 65535L),
        "wireguardPort" to wireguardPort.coerceIn(1L, 65535L),
        "openVpnPort" to openVpnPort.coerceIn(1L, 65535L),
        "status" to parsedStatus.name,
        "cpuPercent" to cpuPercent.coerceIn(0.0, 100.0),
        "ramUsedMb" to ramUsedMb.coerceAtLeast(0L),
        "ramTotalMb" to ramTotalMb.coerceAtLeast(0L),
        "storageUsedGb" to storageUsedGb.coerceAtLeast(0L),
        "storageTotalGb" to storageTotalGb.coerceAtLeast(0L),
        "latencyMs" to latencyMs.coerceIn(0L, 60000L),
        "activeConnections" to activeConnections.coerceAtLeast(0L),
        "wireguardEnabled" to wireguardEnabled,
        "openVpnEnabled" to openVpnEnabled,
        "lastDiagnostic" to lastDiagnostic.take(500),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class VpnProtocolConfig(
    @DocumentId val id: String = "",
    val serverId: String = "",
    val serverName: String = "",
    val type: String = VpnProtocolType.WIREGUARD.name,
    val enabled: Boolean = true,
    val port: Long = 51820L,
    val transport: String = TransportType.UDP.name,
    val subnet: String = "10.8.0.0/24",
    val dns: String = "1.1.1.1, 8.8.8.8",
    val mtu: Long = 1420L,
    val cipher: String = "ChaCha20-Poly1305",
    val keepaliveSeconds: Long = 25L,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    val parsedType: VpnProtocolType get() = enumValueOrDefault(type, VpnProtocolType.WIREGUARD)
    val parsedTransport: TransportType get() = enumValueOrDefault(transport, TransportType.UDP)

    fun toCreateMap(): Map<String, Any> = mapOf(
        "serverId" to serverId.trim().take(128),
        "serverName" to serverName.trim().take(80),
        "type" to parsedType.name,
        "enabled" to enabled,
        "port" to port.coerceIn(1L, 65535L),
        "transport" to parsedTransport.name,
        "subnet" to subnet.trim().take(40),
        "dns" to dns.trim().take(120),
        "mtu" to mtu.coerceIn(1200L, 1500L),
        "cipher" to cipher.trim().take(60),
        "keepaliveSeconds" to keepaliveSeconds.coerceIn(0L, 300L),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class VpnUser(
    @DocumentId val id: String = "",
    val username: String = "",
    val email: String = "",
    val serverId: String = "",
    val serverName: String = "",
    val protocol: String = ProtocolSelection.WIREGUARD.name,
    val status: String = VpnUserStatus.ACTIVE.name,
    val maxDevices: Long = 2L,
    val activeConnections: Long = 0L,
    val dataLimitGb: Long = 100L,
    val dataUsedMb: Long = 0L,
    val expiresAtEpochMs: Long = 0L,
    val notes: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    val parsedProtocol: ProtocolSelection get() = enumValueOrDefault(protocol, ProtocolSelection.WIREGUARD)
    val parsedStatus: VpnUserStatus get() = enumValueOrDefault(status, VpnUserStatus.ACTIVE)

    fun toCreateMap(): Map<String, Any> = mapOf(
        "username" to username.trim().take(60),
        "email" to email.trim().take(160),
        "serverId" to serverId.trim().take(128),
        "serverName" to serverName.trim().take(80),
        "protocol" to parsedProtocol.name,
        "status" to parsedStatus.name,
        "maxDevices" to maxDevices.coerceIn(1L, 100L),
        "activeConnections" to activeConnections.coerceIn(0L, 1000L),
        "dataLimitGb" to dataLimitGb.coerceIn(0L, 100000L),
        "dataUsedMb" to dataUsedMb.coerceIn(0L, 100000000L),
        "expiresAtEpochMs" to expiresAtEpochMs.coerceAtLeast(0L),
        "notes" to notes.trim().take(300),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class VpnConfigProfile(
    @DocumentId val id: String = "",
    val vpnUserId: String = "",
    val vpnUsername: String = "",
    val serverId: String = "",
    val serverName: String = "",
    val deviceId: String = "",
    val deviceName: String = "",
    val protocol: String = VpnProtocolType.WIREGUARD.name,
    val assignedIp: String = "",
    val publicKey: String = "",
    val privateKey: String = "",
    val presharedKey: String = "",
    val endpoint: String = "",
    val configContent: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    val parsedProtocol: VpnProtocolType get() = enumValueOrDefault(protocol, VpnProtocolType.WIREGUARD)

    fun toCreateMap(): Map<String, Any> = mapOf(
        "vpnUserId" to vpnUserId.trim().take(128),
        "vpnUsername" to vpnUsername.trim().take(60),
        "serverId" to serverId.trim().take(128),
        "serverName" to serverName.trim().take(80),
        "deviceId" to deviceId.trim().take(128),
        "deviceName" to deviceName.trim().take(80),
        "protocol" to parsedProtocol.name,
        "assignedIp" to assignedIp.trim().take(64),
        "publicKey" to publicKey.trim().take(128),
        "privateKey" to privateKey.trim().take(128),
        "presharedKey" to presharedKey.trim().take(128),
        "endpoint" to endpoint.trim().take(120),
        "configContent" to configContent.take(8192),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class VpnDevice(
    @DocumentId val id: String = "",
    val vpnUserId: String = "",
    val vpnUsername: String = "",
    val serverId: String = "",
    val deviceName: String = "",
    val platform: String = DevicePlatform.ANDROID.name,
    val assignedIp: String = "",
    val publicKeyFingerprint: String = "",
    val authorized: Boolean = true,
    val connected: Boolean = false,
    val lastSeenEpochMs: Long = 0L,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    val parsedPlatform: DevicePlatform get() = enumValueOrDefault(platform, DevicePlatform.ANDROID)

    fun toCreateMap(): Map<String, Any> = mapOf(
        "vpnUserId" to vpnUserId.trim().take(128),
        "vpnUsername" to vpnUsername.trim().take(60),
        "serverId" to serverId.trim().take(128),
        "deviceName" to deviceName.trim().take(80),
        "platform" to parsedPlatform.name,
        "assignedIp" to assignedIp.trim().take(64),
        "publicKeyFingerprint" to publicKeyFingerprint.trim().take(128),
        "authorized" to authorized,
        "connected" to connected,
        "lastSeenEpochMs" to lastSeenEpochMs.coerceAtLeast(0L),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class PanelMember(
    @DocumentId val id: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = MemberRole.OPERATOR.name,
    val status: String = MemberStatus.ACTIVE.name,
    val twoFactorRequired: Boolean = true,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    val parsedRole: MemberRole get() = enumValueOrDefault(role, MemberRole.OPERATOR)
    val parsedStatus: MemberStatus get() = enumValueOrDefault(status, MemberStatus.ACTIVE)

    fun toCreateMap(): Map<String, Any> = mapOf(
        "name" to name.trim().take(80),
        "email" to email.trim().take(160),
        "role" to parsedRole.name,
        "status" to parsedStatus.name,
        "twoFactorRequired" to twoFactorRequired,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class PanelNotification(
    @DocumentId val id: String = "",
    val title: String = "",
    val message: String = "",
    val severity: String = NotificationSeverity.INFO.name,
    val category: String = "SYSTEM",
    val read: Boolean = false,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    val parsedSeverity: NotificationSeverity get() = enumValueOrDefault(severity, NotificationSeverity.INFO)

    fun toCreateMap(): Map<String, Any> = mapOf(
        "title" to title.trim().take(120),
        "message" to message.trim().take(500),
        "severity" to parsedSeverity.name,
        "category" to category.trim().take(40),
        "read" to read,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class ActivityLog(
    @DocumentId val id: String = "",
    val actorEmail: String = "",
    val action: String = "",
    val targetType: String = "",
    val targetName: String = "",
    val details: String = "",
    val status: String = LogStatus.SUCCESS.name,
    val createdAt: Timestamp? = null
) {
    val parsedStatus: LogStatus get() = enumValueOrDefault(status, LogStatus.SUCCESS)

    fun toCreateMap(): Map<String, Any> = mapOf(
        "actorEmail" to actorEmail.trim().take(160),
        "action" to action.trim().take(80),
        "targetType" to targetType.trim().take(60),
        "targetName" to targetName.trim().take(120),
        "details" to details.trim().take(500),
        "status" to parsedStatus.name,
        "createdAt" to FieldValue.serverTimestamp()
    )
}
