package com.example.data.crypto

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.math.BigInteger
import java.security.SecureRandom

data class WireGuardKeyPair(
    val privateKeyBase64: String,
    val publicKeyBase64: String,
    val presharedKeyBase64: String
)

/**
 * Cryptographic key and configuration generator for WireGuard (RFC 7748 Curve25519)
 * and OpenVPN profiles, plus scannable QR code rendering via ZXing.
 */
object WireGuardCrypto {

    private val secureRandom = SecureRandom()
    private val P = BigInteger.ONE.shiftLeft(255).subtract(BigInteger.valueOf(19))
    private val A24 = BigInteger.valueOf(121665)
    private val BASE_POINT = BigInteger.valueOf(9)

    fun generateKeyPair(): WireGuardKeyPair {
        val priv = ByteArray(32)
        secureRandom.nextBytes(priv)
        // RFC 7748 Curve25519 scalar clamping
        priv[0] = (priv[0].toInt() and 248).toByte()
        priv[31] = (priv[31].toInt() and 127).toByte()
        priv[31] = (priv[31].toInt() or 64).toByte()

        val pub = x25519ScalarMultBase(priv)

        val psk = ByteArray(32)
        secureRandom.nextBytes(psk)

        return WireGuardKeyPair(
            privateKeyBase64 = Base64.encodeToString(priv, Base64.NO_WRAP),
            publicKeyBase64 = Base64.encodeToString(pub, Base64.NO_WRAP),
            presharedKeyBase64 = Base64.encodeToString(psk, Base64.NO_WRAP)
        )
    }

    fun generateServerPublicKeyPreview(): String = generateKeyPair().publicKeyBase64

    fun x25519ScalarMultBase(scalarBytes: ByteArray): ByteArray {
        val k = decodeLittleEndian(scalarBytes)
        val u = BASE_POINT
        var x1 = u
        var x2 = BigInteger.ONE
        var z2 = BigInteger.ZERO
        var x3 = u
        var z3 = BigInteger.ONE
        var swap = 0

        for (t in 254 downTo 0) {
            val kt = if (k.testBit(t)) 1 else 0
            swap = swap xor kt
            if (swap != 0) {
                val tx = x2; x2 = x3; x3 = tx
                val tz = z2; z2 = z3; z3 = tz
            }
            swap = kt

            val a = x2.add(z2).mod(P)
            val aa = a.multiply(a).mod(P)
            val b = x2.subtract(z2).mod(P)
            val bb = b.multiply(b).mod(P)
            val e = aa.subtract(bb).mod(P)
            val c = x3.add(z3).mod(P)
            val d = x3.subtract(z3).mod(P)
            val da = d.multiply(a).mod(P)
            val cb = c.multiply(b).mod(P)

            val sumDaCb = da.add(cb).mod(P)
            x3 = sumDaCb.multiply(sumDaCb).mod(P)

            val diffDaCb = da.subtract(cb).mod(P)
            z3 = x1.multiply(diffDaCb.multiply(diffDaCb).mod(P)).mod(P)

            x2 = aa.multiply(bb).mod(P)
            z2 = e.multiply(aa.add(A24.multiply(e)).mod(P)).mod(P)
        }

        if (swap != 0) {
            x2 = x3
            z2 = z3
        }

        val z2Inv = z2.modInverse(P)
        val result = x2.multiply(z2Inv).mod(P)
        return encodeLittleEndian(result)
    }

    private fun decodeLittleEndian(bytes: ByteArray): BigInteger {
        val reversed = bytes.reversedArray()
        return BigInteger(1, reversed)
    }

    private fun encodeLittleEndian(value: BigInteger): ByteArray {
        val out = ByteArray(32)
        val be = value.toByteArray()
        var idx = 0
        for (i in be.indices.reversed()) {
            if (idx < 32) {
                out[idx++] = be[i]
            }
        }
        return out
    }

    fun buildWireGuardConfig(
        clientPrivateKey: String,
        assignedIp: String,
        dns: String,
        mtu: Long,
        serverPublicKey: String,
        presharedKey: String,
        endpointHost: String,
        endpointPort: Long,
        keepalive: Long = 25L
    ): String {
        val cleanIp = if (assignedIp.contains("/")) assignedIp else "$assignedIp/32"
        return buildString {
            appendLine("[Interface]")
            appendLine("PrivateKey = $clientPrivateKey")
            appendLine("Address = $cleanIp")
            appendLine("DNS = $dns")
            appendLine("MTU = $mtu")
            appendLine()
            appendLine("[Peer]")
            appendLine("PublicKey = $serverPublicKey")
            if (presharedKey.isNotBlank()) {
                appendLine("PresharedKey = $presharedKey")
            }
            appendLine("AllowedIPs = 0.0.0.0/0, ::/0")
            appendLine("Endpoint = $endpointHost:$endpointPort")
            appendLine("PersistentKeepalive = $keepalive")
        }.trim()
    }

    fun buildOpenVpnConfig(
        username: String,
        deviceName: String,
        serverIp: String,
        port: Long,
        transport: String,
        cipher: String,
        dns: String,
        keyMaterialSeed: String
    ): String {
        val proto = transport.lowercase()
        val primaryDns = dns.split(",").firstOrNull()?.trim().orEmpty().ifBlank { "1.1.1.1" }
        val tokenBytes = ByteArray(24)
        secureRandom.nextBytes(tokenBytes)
        val sessionIdHex = tokenBytes.joinToString("") { "%02x".format(it) }

        return buildString {
            appendLine("# Panel VPN - Perfil OpenVPN")
            appendLine("# Usuario: $username | Dispositivo: $deviceName")
            appendLine("client")
            appendLine("dev tun")
            appendLine("proto $proto")
            appendLine("remote $serverIp $port")
            appendLine("resolv-retry infinite")
            appendLine("nobind")
            appendLine("persist-key")
            appendLine("persist-tun")
            appendLine("remote-cert-tls server")
            appendLine("cipher ${if (cipher.contains("AES", true)) cipher else "AES-256-GCM"}")
            appendLine("auth SHA256")
            appendLine("dhcp-option DNS $primaryDns")
            appendLine("verb 3")
            appendLine("# Peer-Fingerprint: $keyMaterialSeed")
            appendLine("# Session-ID: $sessionIdHex")
            appendLine("# NOTA: Inserta o sincroniza los certificados <ca>, <cert> y <key> desde tu agente VPS")
            appendLine("# usando POST /api/v1/openvpn/clients para emitir certificados X.509 firmados por tu PKI.")
        }.trim()
    }

    fun generateQrBitmap(content: String, sizePx: Int = 640): Bitmap? {
        if (content.isBlank()) return null
        return try {
            val hints = mapOf(
                EncodeHintType.MARGIN to 2,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
            val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val width = matrix.width
            val height = matrix.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
                }
            }
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, width, 0, 0, width, height)
            }
        } catch (_: Exception) {
            null
        }
    }
}
