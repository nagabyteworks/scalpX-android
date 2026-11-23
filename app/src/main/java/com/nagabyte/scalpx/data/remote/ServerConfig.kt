package com.nagabyte.scalpx.data.remote

import com.nagabyte.scalpx.BuildConfig
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

enum class ServerEnvironment {
    DEV,
    PROD
}

object ServerConfig {
    const val PRODUCTION_BASE_URL = "https://trade.nagabyte.com"

    val environment: ServerEnvironment
        get() = parseEnvironment(BuildConfig.SERVER)

    val baseUrl: String
        get() = resolveBaseUrl(BuildConfig.SERVER, BuildConfig.DEV_SERVER_URL)

    val webSocketUrl: String
        get() = toWebSocketUrl("${baseUrl.trimEnd('/')}/ws")

    fun parseEnvironment(server: String): ServerEnvironment =
        when (server.trim().lowercase(Locale.ROOT)) {
            "dev" -> ServerEnvironment.DEV
            "prod" -> ServerEnvironment.PROD
            else -> error("Invalid SERVER configuration '$server'; expected 'dev' or 'prod'.")
        }

    fun resolveBaseUrl(server: String, devServerUrl: String): String =
        when (parseEnvironment(server)) {
            ServerEnvironment.DEV -> validateDevServerUrl(devServerUrl)
            ServerEnvironment.PROD -> PRODUCTION_BASE_URL
        }

    fun toWebSocketUrl(url: String): String {
        val uri = URI(url)
        require(uri.host != null && uri.userInfo == null) { "Backend URL must have a valid host and no credentials." }
        return when (uri.scheme?.lowercase(Locale.ROOT)) {
            "http" -> url.replaceFirst(Regex("^http://", RegexOption.IGNORE_CASE), "ws://")
            "https" -> url.replaceFirst(Regex("^https://", RegexOption.IGNORE_CASE), "wss://")
            else -> error("Backend URL must use HTTP or HTTPS.")
        }
    }

    private fun validateDevServerUrl(url: String): String {
        val normalized = url.trim().trimEnd('/')
        val uri = try {
            URI(normalized)
        } catch (error: URISyntaxException) {
            throw IllegalArgumentException("DEV_SERVER_URL must be an absolute HTTP(S) URL.", error)
        }
        require(
            uri.host != null &&
                uri.userInfo == null &&
                uri.query == null &&
                uri.fragment == null &&
                uri.rawPath.isNullOrEmpty() &&
                uri.scheme?.lowercase(Locale.ROOT) in setOf("http", "https")
        ) {
            "DEV_SERVER_URL must be an absolute HTTP(S) origin without credentials, path, query, or fragment."
        }
        return normalized
    }
}
