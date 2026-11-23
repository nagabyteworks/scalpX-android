package com.nagabyte.scalpx.ui.scalper

import android.net.Uri

/**
 * User intents accepted by ScalperViewModel.
 */
sealed interface ScalperIntent {
    data class SelectIndex(val index: String) : ScalperIntent
    data class SelectExpiry(val expiry: String) : ScalperIntent
    data class SelectStrike(val strike: Double) : ScalperIntent
    data class SelectCallStrike(val strike: Double) : ScalperIntent
    data class SelectPutStrike(val strike: Double) : ScalperIntent
    data class StepStrike(val isCall: Boolean, val deltaSteps: Int) : ScalperIntent

    data class SelectLots(val lots: Int) : ScalperIntent
    data class StepLots(val delta: Int) : ScalperIntent
    data class SelectProduct(val product: String) : ScalperIntent

    data class ToggleZone2Collapse(val collapsed: Boolean) : ScalperIntent
    data class SelectBookTab(val tab: BookTab) : ScalperIntent
    data class ToggleTableView(val isTable: Boolean) : ScalperIntent
    data class ToggleTheme(val isDark: Boolean) : ScalperIntent
    data class OpenRatchetConfig(val open: Boolean) : ScalperIntent

    data class UpdateLadderDraft(val draft: LadderDraftUi) : ScalperIntent

    data class OneClickBuy(
        val token: String,
        val symbol: String,
        val ltpPaise: Long,
        val marketDataTimestamp: Long? = null,
        val lotSize: Int = 25
    ) : ScalperIntent

    data class OneClickSell(
        val token: String,
        val symbol: String,
        val ltpPaise: Long,
        val marketDataTimestamp: Long? = null,
        val lotSize: Int = 25
    ) : ScalperIntent

    data class AdjustMilestoneByDelta(
        val token: String,
        val deltaPaise: Long
    ) : ScalperIntent

    data class UpdateLadderConfig(
        val token: String,
        val nextMilestonePaise: Long? = null,
        val stepSizePaise: Long? = null,
        val breakoutBufferPaise: Long? = null,
        val slippageBufferPaise: Long? = null
    ) : ScalperIntent

    data class ToggleRatchetPause(
        val token: String,
        val isPaused: Boolean
    ) : ScalperIntent

    data class ExitPosition(val token: String) : ScalperIntent
    object PanicExitAll : ScalperIntent
    object CancelAllOrders : ScalperIntent
    object ConnectFlattrade : ScalperIntent
    object OpenDailyOtpSheet : ScalperIntent
    object DismissDailyOtpSheet : ScalperIntent
    data class UpdateOtpDigits(val digits: String) : ScalperIntent
    object SubmitDailyOtp : ScalperIntent
    object LaunchBrowserOAuthFallback : ScalperIntent
    object DismissAlert : ScalperIntent

    object RefreshHealth : ScalperIntent
    object RefreshOptionChain : ScalperIntent
    object RefreshSensexSpot : ScalperIntent
    data class DeepLinkReceived(val uri: Uri) : ScalperIntent

    data class ToggleKeepScreenOn(val enabled: Boolean) : ScalperIntent
    data class OpenSettings(val open: Boolean) : ScalperIntent

    object OpenTodayTrades : ScalperIntent
    object CloseTodayTrades : ScalperIntent
    data class OpenEditLadder(val position: ActivePositionUi?) : ScalperIntent
}
