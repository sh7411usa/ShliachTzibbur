package com.sh7411usa.shliachtzibbur.core.net

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Builds the shared OkHttp stack. One [OkHttpClient] is reused app-wide. */
object NetworkFactory {

    fun okHttpClient(tokens: TokenProvider): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokens))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

    fun httpEngine(baseUrl: String, client: OkHttpClient): HttpEngine =
        HttpEngine(baseUrl, client)
}
