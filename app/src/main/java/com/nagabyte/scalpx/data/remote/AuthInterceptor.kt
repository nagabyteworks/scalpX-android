package com.nagabyte.scalpx.data.remote

import com.nagabyte.scalpx.data.local.ConfigStore
import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp interceptor for application transport. No application-device authentication is required.
 */
class AuthInterceptor(private val configStore: ConfigStore) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        return chain.proceed(request)
    }
}
