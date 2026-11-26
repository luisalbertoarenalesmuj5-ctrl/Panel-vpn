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
        private val secureRandom = java.security.SecureRandom()

        fun generateAgentToken(): String {
            val bytes = ByteArray(24)
            secureRandom.nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }

        fun buildOneClickVpsInstallerScript(
            apiToken: String,
            wireguardPort: Long = 51820L,
            agentPort: Long = 8443L
        ): String {
            val safeToken = apiToken.replace(Regex("[^a-zA-Z0-9_\\-]"), "").ifBlank { "panelvpn_token" }
            val safeWgPort = wireguardPort.coerceIn(1L, 65535L)
            val safeAgentPort = agentPort.coerceIn(1L, 65535L)
            return """#!/usr/bin/env bash
set -euo pipefail

# Panel VPN - Instalador automatico 1-clic (Ubuntu / Debian)
export DEBIAN_FRONTEND=noninteractive
VPN_TOKEN="$safeToken"
WG_PORT="$safeWgPort"
AGENT_PORT="$safeAgentPort"

apt-get update -y
apt-get install -y wireguard wireguard-tools iptables curl ufw nodejs

sysctl -w net.ipv4.ip_forward=1
grep -q '^net.ipv4.ip_forward=1' /etc/sysctl.conf || echo 'net.ipv4.ip_forward=1' >> /etc/sysctl.conf

mkdir -p /etc/wireguard /opt/panel-vpn-agent
chmod 700 /etc/wireguard

if [ ! -f /etc/wireguard/server_private.key ]; then
  wg genkey | tee /etc/wireguard/server_private.key | wg pubkey > /etc/wireguard/server_public.key
  chmod 600 /etc/wireguard/server_private.key
fi

PRIV_KEY=${'$'}(cat /etc/wireguard/server_private.key)
PUB_IF=${'$'}(ip -4 route show default | awk '{print ${'$'}5; exit}')
PUB_IF=${'$'}{PUB_IF:-eth0}

if [ ! -f /etc/wireguard/wg0.conf ]; then
  cat > /etc/wireguard/wg0.conf <<EOF
[Interface]
Address = 10.8.0.1/24
ListenPort = ${'$'}WG_PORT
PrivateKey = ${'$'}PRIV_KEY
SaveConfig = true
PostUp = iptables -t nat -A POSTROUTING -s 10.8.0.0/24 -o ${'$'}PUB_IF -j MASQUERADE
PostDown = iptables -t nat -D POSTROUTING -s 10.8.0.0/24 -o ${'$'}PUB_IF -j MASQUERADE
EOF
  chmod 600 /etc/wireguard/wg0.conf
fi

systemctl enable --now wg-quick@wg0

cat > /opt/panel-vpn-agent/server.js <<'NODE_EOF'
const http = require('http');
const fs = require('fs');
const os = require('os');
const { execFileSync, execSync } = require('child_process');

const TOKEN = process.env.VPN_PANEL_TOKEN || '';
const PORT = Number(process.env.VPN_AGENT_PORT || 8443);

function readServerPubKey() {
  try { return fs.readFileSync('/etc/wireguard/server_public.key', 'utf8').trim(); }
  catch { return ''; }
}

function getTelemetry() {
  const cpus = os.cpus().length || 1;
  const load = os.loadavg()[0] || 0;
  const cpuPercent = Math.min(100, Number(((load / cpus) * 100).toFixed(1)));
  const ramTotalMb = Math.round(os.totalmem() / 1048576);
  const ramUsedMb = Math.round((os.totalmem() - os.freemem()) / 1048576);
  let storageUsedGb = 0, storageTotalGb = 0;
  try {
    const df = execSync("df -BG / | tail -1 | awk '{print $2,$3}'").toString().trim().split(/\s+/);
    storageTotalGb = parseInt(df[0], 10) || 0;
    storageUsedGb = parseInt(df[1], 10) || 0;
  } catch {}
  let activeConnections = 0, wgEnabled = false;
  try {
    const out = execSync('wg show wg0 peers').toString().trim();
    wgEnabled = true;
    activeConnections = out ? out.split('\n').filter(Boolean).length : 0;
  } catch {}
  return {
    status: 'ONLINE',
    cpuPercent,
    ramUsedMb,
    ramTotalMb,
    storageUsedGb,
    storageTotalGb,
    activeConnections,
    wireguardEnabled: wgEnabled,
    openVpnEnabled: false,
    wireguardPublicKey: readServerPubKey(),
    message: 'Agente Panel VPN operativo'
  };
}

const server = http.createServer((req, res) => {
  res.setHeader('Content-Type', 'application/json');
  const auth = (req.headers.authorization || '').replace(/^Bearer\s+/i, '');
  if (!TOKEN || auth !== TOKEN) {
    res.writeHead(401);
    return res.end(JSON.stringify({ error: 'Unauthorized' }));
  }

  if (req.method === 'GET' && (req.url === '/api/v1/health' || req.url === '/health')) {
    res.writeHead(200);
    return res.end(JSON.stringify(getTelemetry()));
  }

  let body = '';
  req.on('data', chunk => { body += chunk; if (body.length > 65536) req.destroy(); });
  req.on('end', () => {
    try {
      const payload = body ? JSON.parse(body) : {};
      if (req.method === 'POST' && req.url === '/api/v1/wireguard/peers') {
        const pub = String(payload.publicKey || '').trim();
        const ip = String(payload.assignedIp || '').trim();
        if (!/^[A-Za-z0-9+/=]{42,46}$/.test(pub) || !/^[0-9./]+$/.test(ip)) {
          res.writeHead(400);
          return res.end(JSON.stringify({ ok: false, message: 'Clave o IP invalida' }));
        }
        const args = ['set', 'wg0', 'peer', pub, 'allowed-ips', ip];
        execFileSync('wg', args);
        execFileSync('wg-quick', ['save', 'wg0']);
        res.writeHead(200);
        return res.end(JSON.stringify({
          ok: true,
          serverPublicKey: readServerPubKey(),
          message: 'Peer WireGuard activado'
        }));
      }
      if (req.method === 'POST' && req.url.startsWith('/api/v1/protocols/')) {
        const enable = Boolean(payload.enabled);
        execFileSync('systemctl', [enable ? 'start' : 'stop', 'wg-quick@wg0']);
        res.writeHead(200);
        return res.end(JSON.stringify({ ok: true }));
      }
      res.writeHead(404);
      res.end(JSON.stringify({ error: 'Not found' }));
    } catch (err) {
      res.writeHead(500);
      res.end(JSON.stringify({ ok: false, message: err.message }));
    }
  });
});

server.listen(PORT, '0.0.0.0', () => console.log('Panel VPN Agent listening on', PORT));
NODE_EOF

cat > /etc/systemd/system/panel-vpn-agent.service <<EOF
[Unit]
Description=Panel VPN HTTP Agent
After=network.target wg-quick@wg0.service

[Service]
Environment=VPN_PANEL_TOKEN=${'$'}VPN_TOKEN
Environment=VPN_AGENT_PORT=${'$'}AGENT_PORT
ExecStart=/usr/bin/node /opt/panel-vpn-agent/server.js
Restart=always
RestartSec=3

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable --now panel-vpn-agent

if command -v ufw >/dev/null 2>&1; then
  ufw allow ${'$'}WG_PORT/udp || true
  ufw allow ${'$'}AGENT_PORT/tcp || true
fi

echo "============================================"
echo "Panel VPN listo en tu VPS!"
echo "Endpoint: http://${'$'}(curl -s4 ifconfig.me || hostname -I | awk '{print ${'$'}1}'):${'$'}AGENT_PORT"
echo "Token: ${'$'}VPN_TOKEN"
echo "Clave Publica WireGuard: ${'$'}(cat /etc/wireguard/server_public.key)"
echo "============================================"""
        }

        const val VPS_AGENT_SPEC_SNIPPET = """# Instalacion rapida en VPS Ubuntu/Debian (copia y pega como root):
# 1) Genera tu token en el Asistente Rapido o usa uno propio:
#    export VPN_PANEL_TOKEN="tu_token_seguro"
# 2) El agente expone:
#    GET  /api/v1/health             -> CPU, RAM, disco y serverPublicKey
#    POST /api/v1/wireguard/peers    -> Autoriza clave Curve25519 + IP en wg0
#    POST /api/v1/protocols/:p/toggle -> Inicia o detiene wg-quick@wg0"""
    }
}
