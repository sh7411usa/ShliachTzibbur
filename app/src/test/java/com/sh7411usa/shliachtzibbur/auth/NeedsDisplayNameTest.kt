package com.sh7411usa.shliachtzibbur.auth

import com.sh7411usa.shliachtzibbur.core.result.ApiException
import com.sh7411usa.shliachtzibbur.core.result.ErrorType
import com.sh7411usa.shliachtzibbur.ui.auth.needsDisplayName
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NeedsDisplayNameTest {

    @Test
    fun `matches the plain-English server message`() {
        val error = ApiException(
            type = ErrorType.VALIDATION_FAILED,
            status = 400,
            detail = "A display name is required to create an account",
        )
        assertTrue(needsDisplayName(error))
    }

    @Test
    fun `matches known slugs`() {
        assertTrue(needsDisplayName(ApiException(ErrorType.INVALID_DISPLAY_NAME, 400, null)))
        assertTrue(needsDisplayName(ApiException(ErrorType.RESERVED_DISPLAY_NAME, 400, null)))
    }

    @Test
    fun `matches a structured field error`() {
        val error = ApiException(
            type = ErrorType.VALIDATION_FAILED,
            status = 400,
            detail = null,
            fieldErrors = mapOf("/displayName" to "Required"),
        )
        assertTrue(needsDisplayName(error))
    }

    @Test
    fun `does not match unrelated validation errors`() {
        assertFalse(
            needsDisplayName(
                ApiException(ErrorType.VALIDATION_FAILED, 400, "phone number is unparseable"),
            ),
        )
        assertFalse(needsDisplayName(ApiException(ErrorType.RATE_LIMITED, 429, "slow down")))
    }
}
