package com.nagabyte.scalpx.ui.auth

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.nagabyte.scalpx.data.repository.ApiResult
import com.nagabyte.scalpx.data.repository.TradingRepository

/**
 * Helper to initiate daily Flattrade OAuth login flow.
 * Invariant: Never uses an embedded WebView. Always opens a secure Chrome Custom Tab.
 */
object BrokerLoginHelper {

    /**
     * Calls GET /api/auth/login-url and opens the OAuth URL in a Chrome Custom Tab.
     */
    suspend fun launchLogin(context: Context, repository: TradingRepository): Result<Unit> {
        return when (val result = repository.getLoginUrl()) {
            is ApiResult.Success -> {
                openCustomTab(context, result.data)
                Result.success(Unit)
            }
            is ApiResult.Error -> {
                Result.failure(Exception(result.message, result.cause))
            }
        }
    }

    /**
     * Launches the given URL in an AndroidX Chrome Custom Tab.
     */
    fun openCustomTab(context: Context, url: String) {
        val customTabsIntent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()

        customTabsIntent.launchUrl(context, Uri.parse(url))
    }
}
