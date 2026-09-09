package com.sh7411usa.shliachtzibbur.core.net

import com.sh7411usa.shliachtzibbur.core.net.dto.ProblemDto
import com.sh7411usa.shliachtzibbur.core.result.ApiException
import com.sh7411usa.shliachtzibbur.core.result.ErrorType
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Converts an RFC 7807 `problem+json` response body into an [ApiException]. */
object ErrorParser {

    private const val URN_PREFIX = "urn:tzibbur:error:"

    fun parse(status: Int, body: String?, retryAfterHeader: Int?): ApiException {
        val problem = body?.takeIf { it.isNotBlank() }?.let {
            runCatching { NetJson.decodeFromString<ProblemDto>(it) }.getOrNull()
        }

        val slug = problem?.type
            ?.removePrefix(URN_PREFIX)
            ?.substringAfterLast(':')
            ?: problem?.title
            ?: fallbackSlug(status)

        return ApiException(
            type = slug,
            status = if (problem?.status != null && problem.status != 0) problem.status else status,
            detail = problem?.detail,
            fieldErrors = problem?.errors?.let(::parseFieldErrors).orEmpty(),
            retryAfterSeconds = problem?.retryAfterSeconds ?: retryAfterHeader,
        )
    }

    private fun fallbackSlug(status: Int): String = when (status) {
        400 -> ErrorType.VALIDATION_FAILED
        401 -> ErrorType.UNAUTHORIZED
        403 -> ErrorType.FORBIDDEN
        404 -> ErrorType.NOT_FOUND
        429 -> ErrorType.RATE_LIMITED
        501 -> ErrorType.NOT_IMPLEMENTED
        else -> ErrorType.UNKNOWN
    }

    /** Handles both `[{path, message}]` and `{field: reason}` shapes. */
    private fun parseFieldErrors(element: JsonElement): Map<String, String> = when (element) {
        is JsonArray -> element.mapNotNull { item ->
            val obj = (item as? JsonObject) ?: return@mapNotNull null
            val path = obj["path"]?.jsonPrimitive?.contentOrNull ?: obj["field"]?.jsonPrimitive?.contentOrNull
            val message = obj["message"]?.jsonPrimitive?.contentOrNull ?: obj["reason"]?.jsonPrimitive?.contentOrNull
            if (path != null && message != null) path to message else null
        }.toMap()

        is JsonObject -> element.mapNotNull { (key, value) ->
            (value as? JsonPrimitive)?.contentOrNull?.let { key to it }
        }.toMap()

        else -> emptyMap()
    }
}
