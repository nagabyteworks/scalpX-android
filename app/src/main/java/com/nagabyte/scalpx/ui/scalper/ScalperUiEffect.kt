package com.nagabyte.scalpx.ui.scalper

/**
 * One-shot UI effects emitted by ScalperViewModel.
 */
sealed interface ScalperUiEffect {
    data class TriggerHaptic(val isImportant: Boolean = false) : ScalperUiEffect
    data class PlaySound(val soundType: String) : ScalperUiEffect
    data class ShowToast(val message: String) : ScalperUiEffect
    data class OpenCustomTab(val url: String) : ScalperUiEffect
}
