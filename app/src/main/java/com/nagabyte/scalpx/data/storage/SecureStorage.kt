package com.nagabyte.scalpx.data.storage

import android.content.Context
import com.nagabyte.scalpx.data.local.ConfigStore
import com.nagabyte.scalpx.data.local.SecureConfigStore

/**
 * Adapter ensuring full backward compatibility with ConfigStore.
 */
class SecureStorage(context: Context) : ConfigStore {

    private val delegate = SecureConfigStore(context)

    override fun getServerBaseUrl(): String = delegate.getServerBaseUrl()
    override fun setServerBaseUrl(url: String) = delegate.setServerBaseUrl(url)
    override fun getJwtToken(): String? = delegate.getJwtToken()
    override fun setJwtToken(token: String?) = delegate.setJwtToken(token)
    override fun getDeviceId(): String = delegate.getDeviceId()
    override fun clearAuth() = delegate.clearAuth()

    override fun getDefaultLots(): Int = delegate.getDefaultLots()
    override fun setDefaultLots(lots: Int) = delegate.setDefaultLots(lots)
    override fun getFirstTargetPts(): Double = delegate.getFirstTargetPts()
    override fun setFirstTargetPts(pts: Double) = delegate.setFirstTargetPts(pts)
    override fun getStepPts(): Double = delegate.getStepPts()
    override fun setStepPts(pts: Double) = delegate.setStepPts(pts)
    override fun getBreakoutBufferPts(): Double = delegate.getBreakoutBufferPts()
    override fun setBreakoutBufferPts(pts: Double) = delegate.setBreakoutBufferPts(pts)
    override fun getSlippageBufferPts(): Double = delegate.getSlippageBufferPts()
    override fun setSlippageBufferPts(pts: Double) = delegate.setSlippageBufferPts(pts)

    // Legacy method aliases
    fun getBaseUrl(): String = getServerBaseUrl()
    fun setBaseUrl(url: String) = setServerBaseUrl(url)

    companion object {
        const val DEFAULT_BASE_URL = SecureConfigStore.DEFAULT_BASE_URL
    }
}
