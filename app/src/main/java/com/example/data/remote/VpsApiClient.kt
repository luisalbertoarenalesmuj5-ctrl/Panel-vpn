package com.example.data.remote

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class VpsHealthPayload(
    val status: String? = null,
    val cpuPercent: Double? = null,
    val cpu: Double? = null,
    val ramUsedMb: Long? = null,
    val ramTotalMb: Long? = null,
    val storageUsedGb: Long? = null,
    val storageTotalGb: Long? = null,
    val activeConnections: Long? = null,
    val wireguardEnabled: Boolean? = null,
    val openVpnEnabled: Boolean? = null,
    val wireguardPublicKey: String? = null,
    val message: String? = null
)

@JsonClass(generateAdapter = true)
data class VpsPeerProvisionRequest(
    val username: String,
    val deviceName: String,
    val protocol: String,
    val assignedIp: String,
    val publicKey: String,
    val presharedKey: String
)

@JsonClass(generateAdapter = true)
data class VpsPeerProvisionResponse(
    val ok: Boolean = false,
    val serverPublicKey: String? = null,
    val configContent: String? = null,
    val message: String? = null
)

@JsonClass(generateAdapter = true)
data class VpsProtocolToggleRequest(
    val enabled: Boolean,
    val port: Long,
    val transport: String
)

data class VpsProbeResult(
    val reachable: Boolean,
    val httpCode: Int,
    val latencyMs: Long,
    val cpuPercent: Double?,
    val ramUsedMb: Long?,
    val ramTotalMb: Long?,
    val storageUsedGb: Long?,
    val storageTotalGb: Long?,
    val activeConnections: Long?,
    val wireguardEnabled: Boolean?,
    val openVpnEnabled: Boolean?,
    val serverPublicKey: String?,
    val diagnosticMessage: String
)

class VpsApiClient {

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val healthAdapter = moshi.adapter(VpsHealthPayload::class.java)
    private val peerRequestAdapter = moshi.adapter(VpsPeerProvisionRequest::class.java)
    private val peerResponseAdapter = moshi.adapter(VpsPeerProvisionResponse::class.java)
    private val protocolToggleAdapter = moshi.adapter(VpsProtocolToggleRequest::class.java)
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun buildClient(timeoutSeconds: Long): OkHttpClient {
        val safeTimeout = timeoutSeconds.coerceIn(3L, 60L)
        return OkHttpClient.Builder()
            .connectTimeout(safeTimeout, TimeUnit.SECONDS)
            .readTimeout(safeTimeout, TimeUnit.SECONDS)
            .writeTimeout(safeTimeout, TimeUnit.SECONDS)
            .build()
    }

    fun normalizeAndValidateBaseUrl(rawUrl: String): Result<String> {
        val trimmed = rawUrl.trim().trimEnd('/')
        if (trimmed.isBlank()) {
            return Result.failure(IllegalArgumentException("La URL del endpoint API está vacía"))
        }
        return try {
            val uri = URI(trimmed)
            val scheme = uri.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") {
                Result.failure(IllegalArgumentException("El endpoint debe usar http:// o https://"))
            } else if (uri.host.isNullOrBlank()) {
                Result.failure(IllegalArgumentException("Host inválido en la URL del endpoint"))
            } else {
                Result.success(trimmed)
            }
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("URL inválida: ${e.message}"))
        }
    }

    suspend fun probeServerHealth(
        endpointUrl: String,
        apiToken: String,
        timeoutSeconds: Long = 10L
    ): VpsProbeResult = withContext(Dispatchers.IO) {
        val validated = normalizeAndValidateBaseUrl(endpointUrl)
        val baseUrl = validated.getOrElse { err ->
            return@withContext VpsProbeResult(
                reachable = false,
                httpCode = 0,
                latencyMs = 0L,
                cpuPercent = null,
                ramUsedMb = null,
                ramTotalMb = null,
                storageUsedGb = null,
                storageTotalGb = null,
                activeConnections = null,
                wireguardEnabled = null,
                openVpnEnabled = null,
                serverPublicKey = null,
                diagnosticMessage = err.message ?: "URL inválida"
            )
        }

        val client = buildClient(timeoutSeconds)
        val candidatePaths = listOf("/api/v1/health", "/api/v1/status", "/health", "")
        var lastError = "Sin respuesta del servidor"

        for (path in candidatePaths) {
            val targetUrl = "$baseUrl$path"
            val startNanos = System.nanoTime()
            try {
                val requestBuilder = Request.Builder()
                    .url(targetUrl)
                    .get()
                    .header("Accept", "application/json")

                if (apiToken.isNotBlank()) {
                    requestBuilder.header("Authorization", "Bearer $apiToken")
                    requestBuilder.header("X-API-Key", apiToken)
                }

                client.newCall(requestBuilder.build()).execute().use { response ->
                    val latencyMs = ((System.nanoTime() - startNanos) / 1_000_000L).coerceAtLeast(1L)
                    val bodyStr = response.body?.string().orEmpty()

                    if (response.isSuccessful) {
                        val parsed = runCatching {
                            if (bodyStr.trim().startsWith("{")) healthAdapter.fromJson(bodyStr) else null
                        }.getOrNull()

                        return@withContext VpsProbeResult(
                            reachable = true,
                            httpCode = response.code,
                            latencyMs = latencyMs,
                            cpuPercent = parsed?.cpuPercent ?: parsed?.cpu,
                            ramUsedMb = parsed?.ramUsedMb,
                            ramTotalMb = parsed?.ramTotalMb,
                            storageUsedGb = parsed?.storageUsedGb,
                            storageTotalGb = parsed?.storageTotalGb,
                            activeConnections = parsed?.activeConnections,
                            wireguardEnabled = parsed?.wireguardEnabled,
                            openVpnEnabled = parsed?.openVpnEnabled,
                            serverPublicKey = parsed?.wireguardPublicKey,
                            diagnosticMessage = parsed?.message ?: "HTTP ${response.code} OK ($path • ${latencyMs}ms)"
                        )
                    } else if (response.code == 401 || response.code == 403) {
                        return@withContext VpsProbeResult(
                            reachable = false,
                            httpCode = response.code,
                            latencyMs = latencyMs,
                            cpuPercent = null,
                            ramUsedMb = null,
                            ramTotalMb = null,
                            storageUsedGb = null,
                            storageTotalGb = null,
                            activeConnections = null,
                            wireguardEnabled = null,
                            openVpnEnabled = null,
                            serverPublicKey = null,
                            diagnosticMessage = "HTTP ${response.code}: Token de API rechazado por el agente VPS"
                        )
                    } else {
                        lastError = "HTTP ${response.code} en $path (${latencyMs}ms)"
                    }
                }
            } catch (e: Exception) {
                lastError = "${e.javaClass.simpleName}: ${e.localizedMessage ?: "Error de red"}"
            }
        }

        VpsProbeResult(
            reachable = false,
            httpCode = 0,
            latencyMs = 0L,
            cpuPercent = null,
            ramUsedMb = null,
            ramTotalMb = null,
            storageUsedGb = null,
            storageTotalGb = null,
            activeConnections = null,
            wireguardEnabled = null,
            openVpnEnabled = null,
            serverPublicKey = null,
            diagnosticMessage = lastError
        )
    }

    suspend fun provisionPeerOnVps(
        endpointUrl: String,
        apiToken: String,
        requestPayload: VpsPeerProvisionRequest,
        timeoutSeconds: Long = 10L
    ): Result<VpsPeerProvisionResponse> = withContext(Dispatchers.IO) {
        val baseUrl = normalizeAndValidateBaseUrl(endpointUrl).getOrElse {
            return@withContext Result.failure(it)
        }
        val path = if (requestPayload.protocol == "WIREGUARD") "/api/v1/wireguard/peers" else "/api/v1/openvpn/clients"
        return@withContext try {
            val json = peerRequestAdapter.toJson(requestPayload)
            val req = Request.Builder()
                .url("$baseUrl$path")
                .post(json.toRequestBody(jsonMediaType))
                .apply {
                    if (apiToken.isNotBlank()) {
                        header("Authorization", "Bearer $apiToken")
                        header("X-API-Key", apiToken)
                    }
                }
                .build()
            buildClient(timeoutSeconds).newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (resp.isSuccessful) {
                    val parsed = runCatching { peerResponseAdapter.fromJson(body) }.getOrNull()
                        ?: VpsPeerProvisionResponse(ok = true, message = "HTTP ${resp.code}")
                    Result.success(parsed)
                } else {
                    Result.failure(IllegalStateException("Agente VPS respondió HTTP ${resp.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleProtocolOnVps(
        endpointUrl: String,
        apiToken: String,
        protocolType: String,
        enabled: Boolean,
        port: Long,
        transport: String,
        timeoutSeconds: Long = 10L
    ): Result<String> = withContext(Dispatchers.IO) {
        val baseUrl = normalizeAndValidateBaseUrl(endpointUrl).getOrElse {
            return@withContext Result.failure(it)
        }
        return@withContext try {
            val json = protocolToggleAdapter.toJson(VpsProtocolToggleRequest(enabled, port, transport))
            val req = Request.Builder()
                .url("$baseUrl/api/v1/protocols/${protocolType.lowercase()}/toggle")
                .post(json.toRequestBody(jsonMediaType))
                .apply {
                    if (apiToken.isNotBlank()) {
                        header("Authorization", "Bearer $apiToken")
                        header("X-API-Key", apiToken)
                    }
                }
                .build()
            buildClient(timeoutSeconds).newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    Result.success("Servicio $protocolType actualizado en VPS (HTTP ${resp.code})")
                } else {
                    Result.failure(IllegalStateException("VPS respondió HTTP ${resp.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        const val VPS_AGENT_SPEC_SNIPPET = """# Agente API Ligero para tu VPS (Node.js / Express)
# Instalar: npm i express systeminformation
const express = require('express');
const si = require('systeminformation');
const { execFile } = require('child_process');
const app = express();
app.use(express.json());

const API_TOKEN = process.env.VPN_PANEL_TOKEN;
app.use((req, res, next) => {
  const auth = req.headers.authorization?.replace('Bearer ', '');
  if (!API_TOKEN || auth !== API_TOKEN) return res.status(401).json({ error: 'Unauthorized' });
  next();
});

app.get('/api/v1/health', async (req, res) => {
  const [cpu, mem, fs] = await Promise.all([si.currentLoad(), si.mem(), si.fsSize()]);
  res.json({
    status: 'ONLINE',
    cpuPercent: Number(cpu.currentLoad.toFixed(1)),
    ramUsedMb: Math.round(mem.active / 1048576),
    ramTotalMb: Math.round(mem.total / 1048576),
    storageUsedGb: Math.round((fs[0]?.used || 0) / 1073741824),
    storageTotalGb: Math.round((fs[0]?.size || 0) / 1073741824),
    activeConnections: 0,
    wireguardEnabled: true,
    openVpnEnabled: false,
    message: 'Agente VPS operativo'
  });
});

app.listen(8443, '0.0.0.0');"""
    }
}
