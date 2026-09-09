package com.sh7411usa.shliachtzibbur.core.net

import com.sh7411usa.shliachtzibbur.core.result.ApiException
import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Low-level HTTP execution over OkHttp. Returns the raw response body on 2xx and
 * throws a typed [ApiException] otherwise (or on transport/timeout failure).
 * Typed encoding/decoding lives in [TzibburApi].
 */
class HttpEngine(
    private val baseUrl: String,
    private val client: OkHttpClient,
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    enum class Method { GET, POST, PATCH, DELETE }

    suspend fun execute(
        method: Method,
        path: String,
        query: Map<String, String?> = emptyMap(),
        jsonBody: String? = null,
    ): String {
        val url = buildUrl(path, query)
        val body: RequestBody? = when {
            jsonBody != null -> jsonBody.toRequestBody(jsonMediaType)
            method == Method.POST || method == Method.PATCH -> ByteArray(0).toRequestBody(jsonMediaType)
            else -> null
        }
        val request = Request.Builder()
            .url(url)
            .method(method.name, body)
            .header("Accept", "application/json")
            .build()

        val response = try {
            client.newCall(request).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            throw ApiException.network(e)
        }

        response.use { resp ->
            val text = resp.body?.string().orEmpty()
            if (resp.isSuccessful) return text

            val retryAfter = resp.header("Retry-After")?.toIntOrNull()
            throw ErrorParser.parse(resp.code, text, retryAfter)
        }
    }

    private fun buildUrl(path: String, query: Map<String, String?>): HttpUrl {
        val builder = (baseUrl.trimEnd('/') + "/" + path.trimStart('/')).toHttpUrl().newBuilder()
        query.forEach { (key, value) -> if (value != null) builder.addQueryParameter(key, value) }
        return builder.build()
    }
}
