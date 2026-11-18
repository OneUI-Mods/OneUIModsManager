package dev.ryuya.oneuimods

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.*
import org.junit.Test

class NetworkCleanupTest {
    private class Connection(url: URL, val status: Int, val failure: Boolean = false) :
        HttpURLConnection(url) {
        var closed = false

        override fun disconnect() {
            closed = true
        }

        override fun usingProxy() = false

        override fun connect() {}

        override fun getResponseCode(): Int {
            if (failure) throw IOException("network lost while connecting")
            return status
        }

        override fun getHeaderField(name: String?): String? = null
    }

    @Test
    fun connectingFailureClosesTheCreatedConnection() {
        val c = Connection(URL("https://api.github.com/test"), 200, true)
        try {
            Network.openWithFactory(c.url.toString(), "application/json", null) { c }
            fail("I/O error required")
        } catch (_: IOException) {}
        assertTrue(c.closed)
    }

    @Test
    fun aRateLimitWithoutResetMetadataKeepsTheHttpFailureAndCloses() {
        val c = Connection(URL("https://api.github.com/test"), 429)
        try {
            Network.openWithFactory(c.url.toString(), "application/json", null) { c }
            fail("Rate limit required")
        } catch (error: Network.Failure) {
            assertEquals(429, error.status)
        }
        assertTrue(c.closed)
    }

    @Test
    fun redirectWithoutLocationClosesTheConnection() {
        val c = Connection(URL("https://api.github.com/test"), 302)
        try {
            Network.openWithFactory(c.url.toString(), "application/json", null) { c }
            fail("Missing redirect required")
        } catch (_: IOException) {}
        assertTrue(c.closed)
    }

    @Test
    fun successfulConnectionRemainsOwnedByTheCaller() {
        val c = Connection(URL("https://api.github.com/test"), 200)
        assertSame(c, Network.openWithFactory(c.url.toString(), "application/json", null) { c })
        assertFalse(c.closed)
        assertEquals(15000, c.connectTimeout)
        assertEquals(20000, c.readTimeout)
        c.disconnect()
    }
}
