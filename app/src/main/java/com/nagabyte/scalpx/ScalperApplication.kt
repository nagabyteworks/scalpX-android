package com.nagabyte.scalpx

import android.app.Application
import com.nagabyte.scalpx.data.local.SecureConfigStore
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
        val tradingApi = TradingApi.create(configStore.getServerBaseUrl(), okHttpClient)
        val wsClient = TradingWsClient(okHttpClient, configStore)

        tradingRepository = TradingRepository(tradingApi, wsClient, configStore)
    }
}
