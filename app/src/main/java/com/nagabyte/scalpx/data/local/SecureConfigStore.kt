package com.nagabyte.scalpx.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.nagabyte.scalpx.BuildConfig
import java.util.UUID

/**
 * Android Keystore-backed Secure Encrypted Storage.
 * Invariant: Flattrade api_secret never touches the device.
 * Stores only backend Base URL, personal JWT, permanent X-Device-Id, and execution presets.
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

        // Ensure permanent device ID is generated on first launch
        if (prefs.getString(KEY_DEVICE_ID, null).isNullOrBlank()) {
            val newId = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, newId).apply()
        }
    }

    override fun getServerBaseUrl(): String {
        val stored = prefs.getString(KEY_BASE_URL, null)
        return if (!stored.isNullOrBlank()) stored else BuildConfig.ENV_BASE_URL.ifBlank { DEFAULT_BASE_URL }
    }

    override fun setServerBaseUrl(url: String) {
        prefs.edit().putString(KEY_BASE_URL, url).apply()
    }

    override fun getJwtToken(): String? {
        val stored = prefs.getString(KEY_JWT_TOKEN, null)
        return if (!stored.isNullOrBlank()) stored else BuildConfig.ENV_JWT_TOKEN.ifBlank { DEFAULT_JWT_TOKEN }
    }

    override fun setJwtToken(token: String?) {
        prefs.edit().putString(KEY_JWT_TOKEN, token).apply()
    }

    override fun getFlattradePassword(): String? {
        val stored = prefs.getString(KEY_FLATTRADE_PASSWORD, null)
        return if (!stored.isNullOrBlank()) stored else BuildConfig.ENV_FLATTRADE_PASSWORD.ifBlank { null }
    }

    override fun setFlattradePassword(password: String?) {
        if (password == null) {
            prefs.edit().remove(KEY_FLATTRADE_PASSWORD).apply()
        } else {
            prefs.edit().putString(KEY_FLATTRADE_PASSWORD, password).apply()
        }
    }

    override fun getFlattradeUserId(): String? {
        val stored = prefs.getString(KEY_FLATTRADE_USER_ID, null)
        return if (!stored.isNullOrBlank()) stored else BuildConfig.ENV_FLATTRADE_USER_ID.ifBlank { null }
    }

    override fun setFlattradeUserId(userId: String?) {
        if (userId == null) {
            prefs.edit().remove(KEY_FLATTRADE_USER_ID).apply()
        } else {
            prefs.edit().putString(KEY_FLATTRADE_USER_ID, userId).apply()
        }
    }

    override fun getFlattradeAccessToken(): String? {
        val stored = prefs.getString(KEY_FLATTRADE_ACCESS_TOKEN, null)
        return if (!stored.isNullOrBlank()) stored else BuildConfig.ENV_FLATTRADE_ACCESS_TOKEN.ifBlank { null }
    }

    override fun setFlattradeAccessToken(token: String?) {
        if (token == null) {
            prefs.edit().remove(KEY_FLATTRADE_ACCESS_TOKEN).apply()
        } else {
            prefs.edit().putString(KEY_FLATTRADE_ACCESS_TOKEN, token).apply()
        }
    }

    override fun getDeviceId(): String {
        return prefs.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_DEVICE_ID, it).apply()
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

    companion object {
        private const val PREFS_NAME = "scalpx_secure_config"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_JWT_TOKEN = "jwt_token"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_FLATTRADE_PASSWORD = "flattrade_password"
        private const val KEY_FLATTRADE_USER_ID = "flattrade_user_id"
        private const val KEY_FLATTRADE_ACCESS_TOKEN = "flattrade_access_token"

        private const val KEY_DEFAULT_LOTS = "preset_default_lots"
        private const val KEY_FIRST_TARGET_PTS = "preset_first_target_pts"
        private const val KEY_STEP_PTS = "preset_step_pts"
        private const val KEY_BREAKOUT_BUFFER_PTS = "preset_breakout_buffer_pts"
        private const val KEY_SLIPPAGE_BUFFER_PTS = "preset_slippage_buffer_pts"

        // Default to personal setup so app opens immediately into trading cockpit
        const val DEFAULT_BASE_URL = "http://localhost:3000"
        const val DEFAULT_JWT_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJzY2FscHgtdHJhZGVyIiwicm9sZSI6Im93bmVyIiwiaWF0IjoxNzk1MjYzNTM1LCJleHAiOjE4MjY3OTk1MzV9.3h7GmJIPUIRLQKxw_3IHFO5Dnq7nkMAHrZ_2FSW8yY8"
    }
}
