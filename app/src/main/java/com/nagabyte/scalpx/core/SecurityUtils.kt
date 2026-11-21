package com.nagabyte.scalpx.core

import android.app.Activity
import android.view.WindowManager

/**
 * Security utilities to enforce application-level isolation and prevent credential/P&L leaks.
 */
object SecurityUtils {

    /**
     * Enables FLAG_SECURE on the current window.
     * Prevents screen captures, screen recording, and thumbnail previews in Android Recent Apps.
     */
    fun enableSecureWindow(activity: Activity) {
        activity.window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
    }

    fun disableSecureWindow(activity: Activity) {
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}

fun Activity.enableSecureScreen() {
    SecurityUtils.enableSecureWindow(this)
}
