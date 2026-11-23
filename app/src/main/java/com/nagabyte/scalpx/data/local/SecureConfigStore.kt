package com.nagabyte.scalpx.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.nagabyte.scalpx.BuildConfig
/**
 * Android Keystore-backed Secure Encrypted Storage.
 * Invariant: Flattrade api_secret never touches the device.
 * Stores only the personal JWT and execution presets.
 */
class SecureConfigStore(context: Context) : ConfigStore {

    private val prefs: SharedPreferences

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        prefs = EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override fun getServerBaseUrl(): String {
        return normalizeUrl(BuildConfig.ENV_BASE_URL.ifBlank { DEFAULT_BASE_URL })
    }

    override fun setServerBaseUrl(url: String) {
        throw UnsupportedOperationException("Backend URL is controlled by the Android build configuration.")
    }

    override fun getJwtToken(): String? {
        val stored = prefs.getString(KEY_JWT_TOKEN, null)
        return if (!stored.isNullOrBlank()) stored else null
    }

    override fun setJwtToken(token: String?) {
        if (token.isNullOrBlank()) {
            prefs.edit().remove(KEY_JWT_TOKEN).apply()
        } else {
            prefs.edit().putString(KEY_JWT_TOKEN, token.trim()).apply()
        }
    }

    override fun clearAuth() {
        prefs.edit().remove(KEY_JWT_TOKEN).apply()
    }

    override fun getDefaultLots(): Int {
        return prefs.getInt(KEY_DEFAULT_LOTS, 2)
    }

    override fun setDefaultLots(lots: Int) {
        prefs.edit().putInt(KEY_DEFAULT_LOTS, lots).apply()
    }

    override fun getFirstTargetPts(): Double {
        return prefs.getFloat(KEY_FIRST_TARGET_PTS, 10.0f).toDouble()
    }

    override fun setFirstTargetPts(pts: Double) {
        prefs.edit().putFloat(KEY_FIRST_TARGET_PTS, pts.toFloat()).apply()
    }

    override fun getStepPts(): Double {
        return prefs.getFloat(KEY_STEP_PTS, 5.0f).toDouble()
    }

    override fun setStepPts(pts: Double) {
        prefs.edit().putFloat(KEY_STEP_PTS, pts.toFloat()).apply()
    }

    override fun getBreakoutBufferPts(): Double {
        return prefs.getFloat(KEY_BREAKOUT_BUFFER_PTS, 1.0f).toDouble()
    }

    override fun setBreakoutBufferPts(pts: Double) {
        prefs.edit().putFloat(KEY_BREAKOUT_BUFFER_PTS, pts.toFloat()).apply()
    }

    override fun getSlippageBufferPts(): Double {
        return prefs.getFloat(KEY_SLIPPAGE_BUFFER_PTS, 1.5f).toDouble()
    }

    override fun setSlippageBufferPts(pts: Double) {
        prefs.edit().putFloat(KEY_SLIPPAGE_BUFFER_PTS, pts.toFloat()).apply()
    }

    private fun normalizeUrl(url: String): String {
        var u = url.trim()
        require(u.startsWith("https://", ignoreCase = true)) { "The scalpX backend URL must use HTTPS." }
        while (u.endsWith("/")) {
            u = u.substring(0, u.length - 1)
        }
        return u
    }

    companion object {
        private const val PREFS_NAME = "scalpx_secure_config"
        private const val KEY_JWT_TOKEN = "jwt_token"

        private const val KEY_DEFAULT_LOTS = "preset_default_lots"
        private const val KEY_FIRST_TARGET_PTS = "preset_first_target_pts"
        private const val KEY_STEP_PTS = "preset_step_pts"
        private const val KEY_BREAKOUT_BUFFER_PTS = "preset_breakout_buffer_pts"
        private const val KEY_SLIPPAGE_BUFFER_PTS = "preset_slippage_buffer_pts"

        // Production default backend URL
        const val DEFAULT_BASE_URL = "https://trade.nagabyte.com"
    }
}
