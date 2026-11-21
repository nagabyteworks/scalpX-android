package com.nagabyte.scalpx.data.local

interface ConfigStore {
    fun getServerBaseUrl(): String
    fun setServerBaseUrl(url: String)
    fun getJwtToken(): String?
    fun setJwtToken(token: String?)
    fun getDeviceId(): String
    fun clearAuth()

    // Flattrade Broker Credentials (Personal Storage)
    fun getFlattradePassword(): String? = null
    fun setFlattradePassword(password: String?) {}
    fun getFlattradeUserId(): String? = null
    fun setFlattradeUserId(userId: String?) {}
    fun getFlattradeAccessToken(): String? = null
    fun setFlattradeAccessToken(token: String?) {}

    // Execution Presets
    fun getDefaultLots(): Int
    fun setDefaultLots(lots: Int)
    fun getFirstTargetPts(): Double
    fun setFirstTargetPts(pts: Double)
    fun getStepPts(): Double
    fun setStepPts(pts: Double)
    fun getBreakoutBufferPts(): Double
    fun setBreakoutBufferPts(pts: Double)
    fun getSlippageBufferPts(): Double
    fun setSlippageBufferPts(pts: Double)
}
