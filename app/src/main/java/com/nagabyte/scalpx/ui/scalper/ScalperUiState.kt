package com.nagabyte.scalpx.ui.scalper

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

enum class WsState {
    CONNECTED,
    CONNECTING,
    DISCONNECTED,
    AUTH_ERROR
}

@Immutable
data class OptionStrikeUi(
    val strikePrice: Double,
    val ceToken: String? = null,
    val ceSymbol: String? = null,
    val ceLtpPaise: Long? = null,
    val peToken: String? = null,
    val peSymbol: String? = null,
    val peLtpPaise: Long? = null
)

@Immutable
data class ActivePositionUi(
    val token: String,
    val symbol: String,
    val quantity: Int,
    val entryPricePaise: Long,
    val currentLtpPaise: Long,
    val unrealizedPnlPaise: Long,
    val exchangeSlTriggerPaise: Long? = null,
    val exchangeSlLimitPaise: Long? = null,
    val localNextMilestonePaise: Long,
    val stepSizePaise: Long,
    val ratchetPaused: Boolean = false,
    val status: String = "OPEN"
)

@Immutable
data class LadderDraftUi(
    val firstTargetPts: Double = 10.0,
    val stepPts: Double = 5.0,
    val breakoutBufferPts: Double = 1.0,
    val slippageBufferPts: Double = 1.5,
    val initialSlPts: Double = 10.0
)

@Immutable
data class CriticalAlertUi(
    val type: String,
    val message: String,
    val token: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Immutable
data class DailyOtpSheetState(
    val isVisible: Boolean = false,
    val otpDigits: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val suggestBrowserFallback: Boolean = false
)

@Immutable
data class ScalperUiState(
    val brokerMode: String = "PAPER",
    val serverWsState: WsState = WsState.DISCONNECTED,
    val brokerConnected: Boolean = false,
    val wsFeedHealthy: Boolean = false,
    val pingMs: Long = 0L,

    val selectedIndex: String = "NIFTY",
    val selectedExpiry: String = "",
    val strikes: ImmutableList<OptionStrikeUi> = persistentListOf(),
    val availableExpiries: ImmutableList<String> = persistentListOf(),
    val selectedStrike: Double? = null,

    val selectedLots: Int = 2,
    val lotSize: Int = 25,
    val product: String = "MIS",

    val ladderDraft: LadderDraftUi = LadderDraftUi(),
    val positions: ImmutableList<ActivePositionUi> = persistentListOf(),

    val totalUnrealizedPnlPaise: Long = 0L,
    val totalRealizedPnlPaise: Long = 0L,

    val inFlightCommands: Set<String> = emptySet(),
    val criticalAlert: CriticalAlertUi? = null,

    val keepScreenOn: Boolean = true,
    val isSettingsOpen: Boolean = false,
    val isTodayTradesOpen: Boolean = false,
    val editingPosition: ActivePositionUi? = null,
    val todayTrades: ImmutableList<com.nagabyte.scalpx.data.remote.dto.LadderPositionDto> = persistentListOf(),
    val dailyOtpSheet: DailyOtpSheetState = DailyOtpSheetState()
)
