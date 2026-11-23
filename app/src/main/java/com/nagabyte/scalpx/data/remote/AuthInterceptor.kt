package com.nagabyte.scalpx.data.remote

import com.nagabyte.scalpx.data.local.ConfigStore
import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp Interceptor that attaches Authorization: Bearer <JWT> to all requests.
 */
class AuthInterceptor(private val configStore: ConfigStore) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val builder = request.newBuilder()

        // Attach Bearer JWT if available
        configStore.getJwtToken()?.let { token ->
            if (token.isNotBlank()) {
                builder.header("Authorization", "Bearer $token")
            }
        }

        return chain.proceed(builder.build())
    }
}
