package com.sh7411usa.shliachtzibbur.core.net

import okhttp3.Interceptor
import okhttp3.Response

/** Supplies the current bearer token synchronously for request signing. */
fun interface TokenProvider {
    fun currentToken(): String?
}

/**
 * Adds `Authorization: Bearer <token>` to every request except the auth
 * endpoints under `/v1/auth/`, which are called before a token exists.
 */
class AuthInterceptor(private val tokens: TokenProvider) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val isAuthCall = request.url.encodedPath.startsWith("/v1/auth/")
        val token = tokens.currentToken()

        val signed = if (!isAuthCall && !token.isNullOrBlank()) {
            request.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            request
        }
        return chain.proceed(signed)
    }
}
