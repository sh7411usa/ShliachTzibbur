package com.sh7411usa.shliachtzibbur.core.net

import kotlinx.serialization.json.Json

/** Shared JSON configuration for all API and WebSocket payloads. */
val NetJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
    isLenient = true
}
