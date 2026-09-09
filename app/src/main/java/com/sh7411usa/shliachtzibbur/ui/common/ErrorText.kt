package com.sh7411usa.shliachtzibbur.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.sh7411usa.shliachtzibbur.R
import com.sh7411usa.shliachtzibbur.core.result.ApiException
import com.sh7411usa.shliachtzibbur.core.result.ErrorType

/** Maps an [ApiException] to a user-facing message, with sensible fallbacks. */
@Composable
fun ApiException.toUserMessage(): String = when (type) {
    ErrorType.NETWORK -> stringResource(R.string.error_network)
    ErrorType.UNAUTHORIZED, ErrorType.INVALID_CODE -> when (type) {
        ErrorType.INVALID_CODE -> stringResource(R.string.auth_error_invalid_code)
        else -> stringResource(R.string.error_unauthorized)
    }
    ErrorType.RATE_LIMITED -> stringResource(R.string.error_rate_limited, retryAfterSeconds ?: 30)
    ErrorType.INVALID_DISPLAY_NAME, ErrorType.RESERVED_DISPLAY_NAME ->
        stringResource(R.string.auth_error_invalid_name)
    ErrorType.SMS_DELIVERY_FAILED -> stringResource(R.string.auth_error_sms_failed)
    "auth_error_invalid_phone" -> stringResource(R.string.auth_error_invalid_phone)
    ErrorType.GROUP_TOO_SMALL -> stringResource(R.string.messages_too_small_generic)
    "send_timed_out" -> stringResource(R.string.messages_send_timed_out)
    ErrorType.GROUP_FULL -> stringResource(R.string.members_error_group_full)
    ErrorType.LAST_ADMIN -> stringResource(R.string.members_error_last_admin)
    ErrorType.VALIDATION_FAILED -> fieldErrors.values.firstOrNull()
        ?: detail
        ?: stringResource(R.string.error_generic)
    else -> detail ?: stringResource(R.string.error_generic)
}
