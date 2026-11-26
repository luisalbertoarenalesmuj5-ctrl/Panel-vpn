package com.example

import com.example.data.crypto.WireGuardCrypto
import com.example.data.remote.VpsApiClient
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun x25519_matchesRfc7748TestVector() {
        // RFC 7748 Section 6.1 Alice's private key & public key (u = 9)
        val scalar = byteArrayOf(
            0x77, 0x07, 0x6d, 0x0a, 0x73, 0x18, 0xa5.toByte(), 0x7d,
            0x3c, 0x16, 0xc1.toByte(), 0x72, 0x51, 0xb2.toByte(), 0x66, 0x45,
            0xdf.toByte(), 0x4c, 0x2f, 0x87.toByte(), 0xeb.toByte(), 0xc0.toByte(), 0x99.toByte(), 0x2a,
            0xb1.toByte(), 0x77, 0xfb.toByte(), 0xa5.toByte(), 0x1d, 0xb9.toByte(), 0x2c, 0x2a
        )
        // Clamp per RFC 7748
        scalar[0] = (scalar[0].toInt() and 248).toByte()
        scalar[31] = (scalar[31].toInt() and 127).toByte()
        scalar[31] = (scalar[31].toInt() or 64).toByte()

        val expectedPub = byteArrayOf(
            0x85.toByte(), 0x20, 0xf0.toByte(), 0x09, 0x89.toByte(), 0x30, 0xa7.toByte(), 0x54,
            0x74, 0x8b.toByte(), 0x7d, 0xdc.toByte(), 0xb4.toByte(), 0x3e, 0xf7.toByte(), 0x5a,
            0x0d, 0xbf.toByte(), 0x3a, 0x0d, 0x26, 0x38, 0x1a, 0xf4.toByte(),
            0xeb.toByte(), 0xa4.toByte(), 0xa9.toByte(), 0x8e.toByte(), 0xaa.toByte(), 0x9b.toByte(), 0x4e, 0x6a
        )
        val actual = WireGuardCrypto.x25519ScalarMultBase(scalar)
        assertArrayEquals(expectedPub, actual)
    }

    @Test
    fun vpsApiClient_validatesEndpointUrls() {
        val client = VpsApiClient()
        assertTrue(client.normalizeAndValidateBaseUrl("https://vps1.example.com:8443/").isSuccess)
        assertTrue(client.normalizeAndValidateBaseUrl("http://45.80.12.10:8080").isSuccess)
        assertTrue(client.normalizeAndValidateBaseUrl("ftp://bad.host").isFailure)
        assertTrue(client.normalizeAndValidateBaseUrl("").isFailure)
    }
}
