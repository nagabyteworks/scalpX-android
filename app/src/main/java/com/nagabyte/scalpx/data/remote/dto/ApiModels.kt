package com.nagabyte.scalpx.data.remote.dto

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class HealthDto(
    val status: String,
    val environment: String = "LIVE",
    val brokerConnected: Boolean,
    val wsFeedHealthy: Boolean,
    val openPositionsCount: Int,
    val memoryRssMb: Long,
    val uptimeSec: Long,
    val tradingEnabled: Boolean = false,
    val wsFeedStatus: String = "UNKNOWN",
    val accountStateUncertain: Boolean = false,
    val sessionTokenPresent: Boolean = false,
    val sessionValid: Boolean = false,
    val sessionValidity: String = "UNKNOWN",
    val timestamp: String? = null
)

@Serializable
data class AppConfigDto(
    val environment: String = "LIVE",
    val trading: TradingConfigDto = TradingConfigDto(),
    val broker: BrokerConfigDto = BrokerConfigDto(),
    val marketFeed: MarketFeedConfigDto = MarketFeedConfigDto(),
    val features: FeatureConfigDto = FeatureConfigDto()
)

@Serializable
data class TradingConfigDto(val mode: String = "LIVE", val enabled: Boolean = false, val authorized: Boolean = false)
@Serializable
data class BrokerConfigDto(val connected: Boolean = false, val sessionValidity: String = "UNKNOWN")
@Serializable
data class MarketFeedConfigDto(val connected: Boolean = false, val status: String = "DISCONNECTED")
@Serializable
data class FeatureConfigDto(val optionsTrading: Boolean = false, val positions: Boolean = false, val orderManagement: Boolean = false)

@Serializable
data class LoginUrlResponse(
    val loginUrl: String
)

@Serializable
data class AuthStatusResponse(
    val status: String = "AUTHENTICATION_REQUIRED",
    val brokerConnected: Boolean = false,
    val wsFeedHealthy: Boolean = false,
    val wsFeedStatus: String = "FEED_DISCONNECTED",
    val tradeDate: String? = null,
    val environment: String? = null,
    val tradingEnabled: Boolean = false,
    val sessionTokenPresent: Boolean = false,
    val sessionValid: Boolean = false,
    val sessionValidity: String = "UNKNOWN",
    val wsTransportConnected: Boolean = false,
    val wsAuthenticated: Boolean = false,
    val marketDataReady: Boolean = false,
    val accountStateUncertain: Boolean = false
)

@Serializable
data class SensexSpotDto(
    val index: String = "SENSEX",
    val exchange: String = "BSE",
    val token: String? = null,
    val status: String = "WAITING_FOR_TICK",
    val spotLtpPaise: Long? = null,
    val timestamp: Long? = null,
    val ageMs: Long? = null,
    val marketDataReady: Boolean = false,
    val validation: String? = null
)

@Serializable
data class ApiErrorResponse(
    val error: String = "",
    val message: String = "",
    val remainingSec: Int? = null,
    val fallbackRequired: Boolean = false
)

@Serializable
data class OptionStrikeDto(
    val strikePrice: Double,
    val ceToken: String? = null,
    val ceSymbol: String? = null,
    val ceLtpPaise: Long? = null,
    val peToken: String? = null,
    val peSymbol: String? = null,
    val peLtpPaise: Long? = null
)

@Serializable
data class OptionChainResponse(
    val symbol: String,
    val expiry: String,
    val strikes: List<OptionStrikeDto> = emptyList(),
    val atmStrike: Double? = null,
    val dataSource: String? = null,
    val marketDataReady: Boolean = false,
    val validation: String? = null
)

@Serializable
data class LadderConfigDto(
    val firstMilestonePaise: Long? = null,
    val stepSizePaise: Long,
    val breakoutBufferPaise: Long,
    val slippageBufferPaise: Long
)

@Serializable
data class OneClickOrderRequest(
    val commandId: String,
    val token: String,
    val tradingSymbol: String,
    val exchange: String = "NFO",
    val side: String = "BUY",
    val lots: Int,
    val lotSize: Int,
    val product: String = "I",
    val initialSlPoints: Double? = null,
    val ladderConfig: LadderConfigDto? = null
)

@Immutable
@Serializable
data class LadderPositionDto(
    val token: String,
    val symbol: String,
    val side: String = "BUY",
    val quantity: Int,
    val entryPricePaise: Long,
    val nextMilestonePaise: Long,
    val stepSizePaise: Long,
    val breakoutBufferPaise: Long,
    val slippageBufferPaise: Long,
    val activeSlOrderId: String? = null,
    val activeSlTriggerPaise: Long? = null,
    val activeSlLimitPaise: Long? = null,
    val status: String,
    val isPaused: Boolean = false
)

@Serializable
data class PatchLadderRequest(
    val nextMilestonePaise: Long? = null,
    val stepSizePaise: Long? = null,
    val breakoutBufferPaise: Long? = null,
    val slippageBufferPaise: Long? = null,
    val paused: Boolean? = null
)

@Serializable
data class ExitPositionRequest(
    val commandId: String,
    val token: String
)

@Serializable
data class ExitPositionResponse(
    val success: Boolean,
    val token: String,
    val commandId: String,
    val orderId: String,
    val status: String
)

@Serializable
data class ExitAllRequest(
    val commandId: String
)

@Serializable
data class ExitAllResponse(
    val success: Boolean,
    val message: String
)

@Serializable
data class TodayTradesResponse(
    val activePositions: List<LadderPositionDto> = emptyList(),
    val openPositionsCount: Int = 0,
    val timestamp: Long = 0L
)

// --- WebSocket Models ---

@Immutable
@Serializable
data class PositionSnapshotItemDto(
    val token: String,
    val symbol: String,
    val quantity: Int,
    val entryPricePaise: Long,
    val currentLtpPaise: Long,
    val unrealizedPnlPaise: Long,
    val nextMilestonePaise: Long,
    val activeSlTriggerPaise: Long? = null,
    val activeSlLimitPaise: Long? = null,
    val isPaused: Boolean = false,
    val status: String
)

@Immutable
@Serializable
data class TradingSnapshotMessage(
    val type: String = "SNAPSHOT",
    val timestamp: Long,
    val openPositionsCount: Int,
    val positions: List<PositionSnapshotItemDto> = emptyList()
)

@Serializable
data class PriorityEventMessage(
    val type: String,
    val timestamp: Long? = null,
    val data: JsonElement? = null
)

data class PriorityAlert(
    val type: String,
    val message: String,
    val token: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
