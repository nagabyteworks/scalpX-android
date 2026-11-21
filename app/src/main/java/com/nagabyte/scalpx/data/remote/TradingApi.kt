package com.nagabyte.scalpx.data.remote

import com.nagabyte.scalpx.data.local.ConfigStore
import com.nagabyte.scalpx.data.remote.dto.*
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface TradingApi {

    @GET("/health")
    suspend fun getHealth(): HealthDto

    @GET("/api/auth/login-url")
    suspend fun getLoginUrl(): LoginUrlResponse

    @GET("/api/option-chain")
    suspend fun getOptionChain(
        @Query("symbol") symbol: String,
        @Query("expiry") expiry: String,
        @Query("strikes") strikes: Int = 10
    ): OptionChainResponse

    @POST("/api/orders/one-click")
    suspend fun placeOneClickOrder(
        @Body request: OneClickOrderRequest
    ): LadderPositionDto

    @PATCH("/api/ladders/{token}")
    suspend fun patchLadder(
        @Path("token") token: String,
        @Body request: PatchLadderRequest
    ): LadderPositionDto

    @POST("/api/orders/exit-position")
    suspend fun exitPosition(
        @Body request: ExitPositionRequest
    ): ExitPositionResponse

    @POST("/api/orders/exit-all")
    suspend fun exitAll(
        @Body request: ExitAllRequest
    ): ExitAllResponse

    @GET("/api/trades/today")
    suspend fun getTodayTrades(): TodayTradesResponse

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            encodeDefaults = true
        }

        fun createOkHttpClient(configStore: ConfigStore): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .addInterceptor(AuthInterceptor(configStore))
                .build()
        }

        fun create(baseUrl: String, okHttpClient: OkHttpClient): TradingApi {
            val contentType = "application/json".toMediaType()
            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(json.asConverterFactory(contentType))
                .build()
                .create(TradingApi::class.java)
        }
    }
}
