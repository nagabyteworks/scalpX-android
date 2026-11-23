package com.nagabyte.scalpx.data.remote

import com.nagabyte.scalpx.data.local.ConfigStore
import com.nagabyte.scalpx.data.remote.dto.*
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface TradingApi {

    @POST("/api/auth/daily-login")
    suspend fun authenticateTotp(
        @Body request: TotpAuthenticationRequest
    ): Response<TotpAuthenticationResponse>

    @GET("/health")
    suspend fun getHealth(): HealthDto

    @GET("/api/app/config")
    suspend fun getAppConfig(): AppConfigDto

    @GET("/api/auth/login-url")
    suspend fun getLoginUrl(): LoginUrlResponse

    @GET("/api/auth/status")
    suspend fun getAuthStatus(): AuthStatusResponse

    @GET("/api/market-data/sensex")
    suspend fun getSensexSpot(): SensexSpotDto

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
            val loggingInterceptor = object : okhttp3.Interceptor {
                private val bodyLogger = HttpLoggingInterceptor().apply {
                    level = if (com.nagabyte.scalpx.BuildConfig.DEBUG) {
                        HttpLoggingInterceptor.Level.BODY
                    } else {
                        HttpLoggingInterceptor.Level.NONE
                    }
                    redactHeader("Authorization")
                }

                private val headerOnlyLogger = HttpLoggingInterceptor().apply {
                    level = if (com.nagabyte.scalpx.BuildConfig.DEBUG) {
                        HttpLoggingInterceptor.Level.HEADERS
                    } else {
                        HttpLoggingInterceptor.Level.NONE
                    }
                    redactHeader("Authorization")
                }

                override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
                    val request = chain.request()
                    return if (request.url.encodedPath.contains("api/auth/daily-login")) {
                        headerOnlyLogger.intercept(chain)
                    } else {
                        bodyLogger.intercept(chain)
                    }
                }
            }

            return OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .addInterceptor(AuthInterceptor(configStore))
                .addInterceptor(loggingInterceptor)
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
