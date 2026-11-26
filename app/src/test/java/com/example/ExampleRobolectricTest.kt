package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.crypto.WireGuardCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app name and wireguard keygen and qr bitmap work`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Panel VPN", appName)

        val keys = WireGuardCrypto.generateKeyPair()
        assertEquals(44, keys.privateKeyBase64.length)
        assertEquals(44, keys.publicKeyBase64.length)
        assertEquals(44, keys.presharedKeyBase64.length)

        val conf = WireGuardCrypto.buildWireGuardConfig(
            clientPrivateKey = keys.privateKeyBase64,
            assignedIp = "10.8.0.2/32",
            dns = "1.1.1.1",
            mtu = 1420L,
            serverPublicKey = keys.publicKeyBase64,
            presharedKey = keys.presharedKeyBase64,
            endpointHost = "45.80.12.10",
            endpointPort = 51820L
        )
        assertTrue(conf.contains("[Interface]"))
        assertTrue(conf.contains("[Peer]"))

        val qr = WireGuardCrypto.generateQrBitmap(conf, 256)
        assertNotNull(qr)
        assertEquals(256, qr!!.width)
    }
}
