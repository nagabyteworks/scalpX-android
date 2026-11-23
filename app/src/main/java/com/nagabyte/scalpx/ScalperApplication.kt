package com.nagabyte.scalpx

import android.app.Application
import android.util.Log
import com.nagabyte.scalpx.data.local.SecureConfigStore
import com.nagabyte.scalpx.data.remote.ServerConfig
import com.nagabyte.scalpx.data.remote.TradingApi
import com.nagabyte.scalpx.data.remote.TradingWsClient
import com.nagabyte.scalpx.data.repository.TradingRepository

class ScalperApplication : Application() {

    lateinit var configStore: SecureConfigStore
        private set

    lateinit var tradingRepository: TradingRepository
        private set

    override fun onCreate() {
        super.onCreate()

        configStore = SecureConfigStore(this)

        val okHttpClient = TradingApi.createOkHttpClient(configStore)
        val backendBaseUrl = configStore.getServerBaseUrl()
        val webSocketUrl = ServerConfig.toWebSocketUrl("${backendBaseUrl.trimEnd('/')}/ws")
        if (BuildConfig.DEBUG) {
            Log.i(
                TAG,
                "SERVER_ENVIRONMENT: ${ServerConfig.environment}\n" +
                    "BACKEND_BASE_URL: $backendBaseUrl\n" +
                    "WEBSOCKET_URL: $webSocketUrl"
            )
        }

        val tradingApi = TradingApi.create(backendBaseUrl, okHttpClient)
        val wsClient = TradingWsClient(okHttpClient, configStore)

        tradingRepository = TradingRepository(tradingApi, wsClient, configStore)
    }

    companion object {
        private const val TAG = "ScalperApplication"
    }
}
