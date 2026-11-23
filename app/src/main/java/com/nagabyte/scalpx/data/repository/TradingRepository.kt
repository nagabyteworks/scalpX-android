package com.nagabyte.scalpx.data.repository

import android.os.SystemClock
import com.nagabyte.scalpx.core.MoneyFormatter
import com.nagabyte.scalpx.data.local.ConfigStore
import com.nagabyte.scalpx.data.remote.TradingApi
import com.nagabyte.scalpx.data.remote.TradingWsClient
import com.nagabyte.scalpx.data.remote.WsConnectionState
import com.nagabyte.scalpx.data.remote.dto.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import java.io.IOException
import java.util.UUID

sealed class ApiResult<out T> {
    data class Success<out T>(val data: T) : ApiResult<T>()
    data class Error(
        val message: String,
        val statusCode: Int? = null,
        val cause: Throwable? = null,
        val errorCode: String? = null
    ) : ApiResult<Nothing>()
}

/**
 * Server-authoritative TradingRepository.
 * Manages REST API calls with fresh UUID commandIds, 500 ms anti-double-tap cooldown,
 * and merges WebSocket snapshot/event streams into observable flows.
 */
open class TradingRepository(
    open val api: TradingApi,
    open val wsClient: TradingWsClient,
    open val configStore: ConfigStore
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var lastTapTimestamp = 0L

    protected val _snapshotState = MutableStateFlow<TradingSnapshotMessage?>(null)
    open val snapshotStream: StateFlow<TradingSnapshotMessage?> = _snapshotState.asStateFlow()

    protected val _alertsStream = MutableSharedFlow<PriorityAlert>(extraBufferCapacity = 32)
    open val alertsStream: SharedFlow<PriorityAlert> = _alertsStream.asSharedFlow()
    open val priorityAlerts: SharedFlow<PriorityAlert> = _alertsStream.asSharedFlow()

    open val connectionState: StateFlow<WsConnectionState> = wsClient.connectionState
    open val pingMs: StateFlow<Long> = wsClient.pingMs
    open val snapshotAgeMs: StateFlow<Long> = wsClient.snapshotAgeMs

    init {
        scope.launch {
            wsClient.snapshotFlow.collect { snapshot ->
                _snapshotState.value = snapshot
            }
        }

        scope.launch {
            wsClient.priorityEventFlow.collect { event ->
                val alert = parsePriorityEvent(event)
                _alertsStream.emit(alert)
            }
        }
    }

    open fun connectWebSocket() = wsClient.connect()
    open fun disconnectWebSocket() = wsClient.disconnect()
    open fun onNetworkAvailable() = wsClient.onNetworkAvailable()

    fun isUserAuthenticated(): Boolean = true
    fun saveJwtToken(token: String?) = configStore.setJwtToken(token)

    open fun clearAuth() {
        configStore.clearAuth()
        disconnectWebSocket()
    }

    private val json = Json { ignoreUnknownKeys = true }

    open suspend fun checkHealth(): ApiResult<HealthDto> = safeApiCall {
        api.getHealth()
    }

    open suspend fun getAppConfig(): ApiResult<AppConfigDto> = safeApiCall {
        api.getAppConfig()
    }

    open suspend fun getSensexSpot(): ApiResult<SensexSpotDto> = safeApiCall {
        api.getSensexSpot()
    }

    open suspend fun getIndexSpot(symbol: String): ApiResult<SensexSpotDto> = safeApiCall {
        api.getIndexSpot(symbol)
    }

    open suspend fun getLoginUrl(): ApiResult<String> = safeApiCall {
        api.getLoginUrl().loginUrl
    }

    open suspend fun getAuthStatus(): ApiResult<AuthStatusResponse> = safeApiCall {
        api.getAuthStatus()
    }

    open suspend fun getOptionChain(symbol: String, expiry: String, strikes: Int = 10): ApiResult<OptionChainResponse> = safeApiCall {
        api.getOptionChain(symbol, expiry, strikes)
    }

    /**
     * Executes 1-click order with anti-double-tap 500 ms cooldown.
     * Generates fresh UUID commandId.
     */
    open suspend fun executeOneClickOrder(
        token: String,
        symbol: String,
        exchange: String = "NFO",
        lots: Int,
        lotSize: Int,
        stepSizePaise: Long,
        breakoutBufferPaise: Long,
        slippageBufferPaise: Long,
        firstMilestonePaise: Long? = null,
        initialSlPoints: Double? = null,
        side: String = "BUY",
        product: String = "I"
    ): ApiResult<LadderPositionDto> {
        val cooldownCheck = checkCooldown()
        if (cooldownCheck != null) return cooldownCheck

        return safeApiCall {
            val commandId = UUID.randomUUID().toString()
            val request = OneClickOrderRequest(
                commandId = commandId,
                token = token,
                tradingSymbol = symbol,
                exchange = exchange,
                side = side,
                lots = lots,
                lotSize = lotSize,
                product = product,
                initialSlPoints = initialSlPoints,
                ladderConfig = LadderConfigDto(
                    firstMilestonePaise = firstMilestonePaise,
                    stepSizePaise = stepSizePaise,
                    breakoutBufferPaise = breakoutBufferPaise,
                    slippageBufferPaise = slippageBufferPaise
                )
            )
            api.placeOneClickOrder(request)
        }
    }

    open suspend fun patchLadder(
        token: String,
        nextMilestonePaise: Long? = null,
        stepSizePaise: Long? = null,
        breakoutBufferPaise: Long? = null,
        slippageBufferPaise: Long? = null,
        paused: Boolean? = null
    ): ApiResult<LadderPositionDto> = safeApiCall {
        api.patchLadder(
            token = token,
            request = PatchLadderRequest(
                nextMilestonePaise = nextMilestonePaise,
                stepSizePaise = stepSizePaise,
                breakoutBufferPaise = breakoutBufferPaise,
                slippageBufferPaise = slippageBufferPaise,
                paused = paused
            )
        )
    }

    open suspend fun exitPosition(token: String): ApiResult<ExitPositionResponse> {
        val cooldownCheck = checkCooldown()
        if (cooldownCheck != null) return cooldownCheck

        return safeApiCall {
            val commandId = UUID.randomUUID().toString()
            api.exitPosition(ExitPositionRequest(commandId = commandId, token = token))
        }
    }

    open suspend fun panicExitAll(): ApiResult<ExitAllResponse> {
        val cooldownCheck = checkCooldown()
        if (cooldownCheck != null) return cooldownCheck

        return safeApiCall {
            val commandId = UUID.randomUUID().toString()
            api.exitAll(ExitAllRequest(commandId = commandId))
        }
    }

    open suspend fun getTodayTrades(): ApiResult<TodayTradesResponse> = safeApiCall {
        api.getTodayTrades()
    }

    private fun checkCooldown(): ApiResult.Error? {
        val now = SystemClock.elapsedRealtime()
        if (now - lastTapTimestamp < TAP_COOLDOWN_MS) {
            return ApiResult.Error("Cooldown active: 500 ms anti-double-tap")
        }
        lastTapTimestamp = now
        return null
    }

    private inline fun <T> safeApiCall(call: () -> T): ApiResult<T> {
        return try {
            ApiResult.Success(call())
        } catch (e: HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            ApiResult.Error(
                message = errorBody?.let(::parseApiError)
                    ?: e.message()
                    ?: "HTTP ${e.code()}",
                statusCode = e.code(),
                cause = e
            )
        } catch (e: IOException) {
            ApiResult.Error(
                message = "Network error: ${e.message ?: "Server unreachable"}",
                cause = e
            )
        } catch (e: Exception) {
            ApiResult.Error(
                message = e.message ?: "Unexpected error occurred",
                cause = e
            )
        }

    }

    private fun parseApiError(body: String): String {
        return try {
            val decoded = json.decodeFromString<ApiErrorResponse>(body)
            decoded.message.takeIf { it.isNotBlank() }
                ?: decoded.error.takeIf { it.isNotBlank() }
                ?: "Market-data request failed"
        } catch (_: Exception) {
            "Market-data request failed"
        }
    }

    private fun parsePriorityEvent(event: PriorityEventMessage): PriorityAlert {
        val dataObj = event.data?.jsonObject
        val token = dataObj?.get("token")?.jsonPrimitive?.content

        val message = when (event.type) {
            "ORDER_FILLED" -> {
                val qty = dataObj?.get("quantity")?.jsonPrimitive?.content ?: ""
                val pricePaise = dataObj?.get("entryPricePaise")?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                "Order filled for $token: $qty qty @ ${MoneyFormatter.formatPaise(pricePaise)}"
            }
            "SL_RATCHETED" -> {
                val trgPaise = dataObj?.get("newTriggerPaise")?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                val lmtPaise = dataObj?.get("newLimitPaise")?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                "SL ratcheted for $token: Trg ${MoneyFormatter.formatPaise(trgPaise)}, Lmt ${MoneyFormatter.formatPaise(lmtPaise)}"
            }
            "ORDER_REJECTED" -> {
                val reason = dataObj?.get("reason")?.jsonPrimitive?.content ?: "Rejected by exchange"
                "Order rejected for $token: $reason"
            }
            "FEED_STALE" -> {
                val msg = dataObj?.get("message")?.jsonPrimitive?.content ?: "Market feed stale"
                "Warning: $msg"
            }
            "MANUAL_INTERVENTION_REQUIRED" -> {
                val error = dataObj?.get("error")?.jsonPrimitive?.content ?: "Broker timeout / unknown state"
                "CRITICAL: Manual intervention required for $token! $error"
            }
            else -> "Event: ${event.type}"
        }

        return PriorityAlert(
            type = event.type,
            message = message,
            token = token,
            timestamp = event.timestamp ?: System.currentTimeMillis()
        )
    }

    companion object {
        const val TAP_COOLDOWN_MS = 500L
    }
}
