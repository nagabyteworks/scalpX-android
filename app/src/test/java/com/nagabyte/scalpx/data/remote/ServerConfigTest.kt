package com.nagabyte.scalpx.data.remote

import com.nagabyte.scalpx.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerConfigTest {

    @Test
    fun `dev selects the configured development origin`() {
        assertEquals(
            "http://10.0.2.2:3000",
            ServerConfig.resolveBaseUrl("dev", "http://10.0.2.2:3000")
        )
        assertEquals(
            "http://192.168.1.25:3000",
            ServerConfig.resolveBaseUrl("DEV", "http://192.168.1.25:3000/")
        )
    }

    @Test
    fun `prod always selects the production origin`() {
        assertEquals(
            ServerConfig.PRODUCTION_BASE_URL,
            ServerConfig.resolveBaseUrl("prod", "http://192.168.1.25:3000")
        )
    }

    @Test
    fun `invalid server setting fails with an actionable message`() {
        val error = try {
            ServerConfig.parseEnvironment("staging")
            error("Expected SERVER parsing to fail.")
        } catch (failure: IllegalStateException) {
            failure
        }

        assertTrue(error.message.orEmpty().contains("SERVER"))
        assertTrue(error.message.orEmpty().contains("dev"))
        assertTrue(error.message.orEmpty().contains("prod"))
    }

    @Test
    fun `HTTP and HTTPS websocket conversions preserve paths and queries`() {
        assertEquals(
            "ws://10.0.2.2:3000/api/ws?symbol=SENSEX",
            ServerConfig.toWebSocketUrl("http://10.0.2.2:3000/api/ws?symbol=SENSEX")
        )
        assertEquals(
            "wss://trade.nagabyte.com/ws?symbol=NIFTY",
            ServerConfig.toWebSocketUrl("https://trade.nagabyte.com/ws?symbol=NIFTY")
        )
    }

    @Test
    fun `REST and websocket resolve from the same selected environment`() {
        val baseUrl = ServerConfig.resolveBaseUrl("dev", "http://192.168.1.25:3000")
        val wsUrl = ServerConfig.toWebSocketUrl("$baseUrl/ws")

        assertEquals("http://192.168.1.25:3000", baseUrl)
        assertEquals("ws://192.168.1.25:3000/ws", wsUrl)
    }

    @Test
    fun `active build configuration resolves one REST and websocket origin`() {
        assertEquals(
            ServerConfig.resolveBaseUrl(BuildConfig.SERVER, BuildConfig.DEV_SERVER_URL),
            ServerConfig.baseUrl
        )
        assertEquals(
            ServerConfig.toWebSocketUrl("${ServerConfig.baseUrl}/ws"),
            ServerConfig.webSocketUrl
        )
    }

    @Test
    fun `release build configuration is HTTPS production only`() {
        if (!BuildConfig.DEBUG) {
            assertEquals("prod", BuildConfig.SERVER)
            assertEquals(ServerConfig.PRODUCTION_BASE_URL, ServerConfig.baseUrl)
            assertEquals("", BuildConfig.DEV_SERVER_URL)
            assertTrue(ServerConfig.baseUrl.startsWith("https://"))
            assertTrue(ServerConfig.webSocketUrl.startsWith("wss://"))
        }
    }
}
