package com.sh7411usa.shliachtzibbur.core.util

/**
 * Minimal phone-number handling. Full parsing/validation is delegated to the
 * server (which returns `validation_failed {"phone": "unparseable"}`); this only
 * assembles a plausible E.164 string and does a length sanity check, avoiding a
 * dependency on libphonenumber.
 */
object PhoneNumbers {

    private val DIGITS = Regex("[^0-9]")

    /**
     * Combine a country calling code (with or without a leading "+") and a
     * national number into an E.164 string. If [national] already starts with
     * "+", it is treated as complete and only cleaned.
     */
    fun toE164(callingCode: String, national: String): String {
        val cleanedNational = national.trim()
        if (cleanedNational.startsWith("+")) {
            return "+" + DIGITS.replace(cleanedNational, "")
        }
        val cc = DIGITS.replace(callingCode, "")
        val nn = DIGITS.replace(cleanedNational, "").trimStart('0')
        return "+$cc$nn"
    }

    /** Loose check: a leading "+" and 8–15 digits (E.164 allows up to 15). */
    fun looksValid(e164: String): Boolean {
        if (!e164.startsWith("+")) return false
        val digits = DIGITS.replace(e164, "")
        return digits.length in 8..15
    }
}
