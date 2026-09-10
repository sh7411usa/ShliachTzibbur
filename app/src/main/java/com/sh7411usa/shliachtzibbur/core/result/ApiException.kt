package com.sh7411usa.shliachtzibbur.core.result

/**
 * A structured API failure parsed from an RFC 7807 `application/problem+json`
 * body, or synthesised for transport/parsing failures.
 *
 * [type] is the bare error slug (e.g. `validation_failed`, `group_too_small`)
 * with the `urn:tzibbur:error:` prefix stripped, so callers can `when` on it via
 * the [ErrorType] constants.
 */
class ApiException(
    val type: String,
    val status: Int,
    val detail: String?,
    val fieldErrors: Map<String, String> = emptyMap(),
    val retryAfterSeconds: Int? = null,
    cause: Throwable? = null,
) : Exception(detail ?: type, cause) {

    val isAuthError: Boolean
        get() = type == ErrorType.UNAUTHORIZED || status == 401

    companion object {
        fun network(cause: Throwable): ApiException =
            ApiException(ErrorType.NETWORK, status = 0, detail = cause.message, cause = cause)

        fun parsing(cause: Throwable): ApiException =
            ApiException(ErrorType.MALFORMED_RESPONSE, status = 0, detail = cause.message, cause = cause)

        fun unknown(status: Int, detail: String?): ApiException =
            ApiException(ErrorType.UNKNOWN, status = status, detail = detail)
    }
}

/** Known error slugs. Not exhaustive — the server may return others. */
object ErrorType {
    const val VALIDATION_FAILED = "validation_failed"
    const val INVALID_DISPLAY_NAME = "invalid_display_name"
    const val RESERVED_DISPLAY_NAME = "reserved_display_name"
    const val INVALID_GROUP_NAME = "invalid_group_name"
    const val INVALID_CATEGORY = "invalid_category"
    const val INVALID_MESSAGE = "invalid_message"
    const val CONTACTS_BATCH_TOO_LARGE = "contacts_batch_too_large"
    const val UNAUTHORIZED = "unauthorized"
    const val INVALID_CODE = "invalid_code"
    const val FORBIDDEN = "forbidden"
    const val NOT_FOUND = "not_found"
    const val CLIENT_MESSAGE_ID_REUSED = "client_message_id_reused"
    const val GROUP_TOO_SMALL = "group_too_small"
    const val GROUP_FULL = "group_full"
    const val LAST_ADMIN = "last_admin"
    const val SMS_DELIVERY_FAILED = "sms_delivery_failed"
    const val RATE_LIMITED = "rate_limited"
    const val NOT_IMPLEMENTED = "not_implemented"

    // Client-side synthetic slugs.
    const val NETWORK = "client_network_error"
    const val MALFORMED_RESPONSE = "client_malformed_response"
    const val UNKNOWN = "client_unknown_error"
    const val ENCRYPTION_LOCKED = "client_encryption_locked"
}
