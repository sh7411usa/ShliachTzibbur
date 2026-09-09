package com.sh7411usa.shliachtzibbur.net

import com.sh7411usa.shliachtzibbur.core.net.ErrorParser
import com.sh7411usa.shliachtzibbur.core.result.ErrorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorParserTest {

    @Test
    fun `parses urn slug and field error list`() {
        val body = """
            {
              "type": "urn:tzibbur:error:validation_failed",
              "title": "validation_failed",
              "status": 400,
              "detail": "Request validation failed",
              "errors": [{ "path": "/phones", "message": "Too small" }]
            }
        """.trimIndent()

        val error = ErrorParser.parse(400, body, null)

        assertEquals(ErrorType.VALIDATION_FAILED, error.type)
        assertEquals(400, error.status)
        assertEquals("Too small", error.fieldErrors["/phones"])
    }

    @Test
    fun `parses object-shaped field errors`() {
        val body = """{ "type": "urn:tzibbur:error:validation_failed", "status": 400, "errors": { "phone": "unparseable" } }"""

        val error = ErrorParser.parse(400, body, null)

        assertEquals("unparseable", error.fieldErrors["phone"])
    }

    @Test
    fun `falls back to status when body is not problem json`() {
        val error = ErrorParser.parse(429, "gateway timeout", 12)

        assertEquals(ErrorType.RATE_LIMITED, error.type)
        assertEquals(12, error.retryAfterSeconds)
    }

    @Test
    fun `maps 401 to unauthorized`() {
        val error = ErrorParser.parse(401, null, null)
        assertTrue(error.isAuthError)
    }
}
