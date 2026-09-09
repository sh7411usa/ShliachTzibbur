package com.sh7411usa.shliachtzibbur.core.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** RFC 3339 timestamp helpers. `java.time` is available on all API levels via desugaring. */
object Timestamps {

    fun epochMillisOrNull(rfc3339: String?): Long? =
        rfc3339?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

    fun epochMillis(rfc3339: String?): Long =
        epochMillisOrNull(rfc3339) ?: System.currentTimeMillis()

    /** Localised short time, e.g. "14:03". */
    fun formatTime(rfc3339: String?, locale: Locale = Locale.getDefault()): String {
        val instant = rfc3339?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return ""
        return DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
            .withLocale(locale)
            .withZone(ZoneId.systemDefault())
            .format(instant)
    }

    /** Localised medium date, e.g. "3 Sep 2026". */
    fun formatDate(rfc3339: String?, locale: Locale = Locale.getDefault()): String {
        val instant = rfc3339?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return ""
        return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(locale)
            .withZone(ZoneId.systemDefault())
            .format(instant)
    }
}
