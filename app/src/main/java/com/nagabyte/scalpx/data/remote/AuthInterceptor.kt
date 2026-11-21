package com.nagabyte.scalpx.data.remote

import com.nagabyte.scalpx.data.local.ConfigStore
import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp Interceptor that attaches Authorization: Bearer <JWT> and X-Device-Id: <UUID>
 * to all requests, matching the backend scalpX contract.
 */
class AuthInterceptor(private val configStore: ConfigStore) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val builder = request.newBuilder()

        // Always attach permanent X-Device-Id header
        val deviceId = configStore.getDeviceId()
        if (deviceId.isNotBlank()) {
            builder.header("X-Device-Id", deviceId)
        }

        // Attach Bearer JWT if available
        configStore.getJwtToken()?.let { token ->
            if (token.isNotBlank()) {
                builder.header("Authorization", "Bearer $token")
            }
        }

        return chain.proceed(builder.build())
    }
}
