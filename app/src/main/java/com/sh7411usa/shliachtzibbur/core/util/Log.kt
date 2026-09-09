package com.sh7411usa.shliachtzibbur.core.util

import android.util.Log as AndroidLog
import com.sh7411usa.shliachtzibbur.BuildConfig

/**
 * Thin logging facade so the app does not depend on a logging library. Debug and
 * verbose logs are compiled to no-ops in release builds; warnings and errors
 * always pass through.
 */
object Log {
    private const val TAG = "Tzibbur"

    fun d(message: String) {
        if (BuildConfig.DEBUG) AndroidLog.d(TAG, message)
    }

    fun i(message: String) {
        AndroidLog.i(TAG, message)
    }

    fun w(message: String, throwable: Throwable? = null) {
        AndroidLog.w(TAG, message, throwable)
    }

    fun e(message: String, throwable: Throwable? = null) {
        AndroidLog.e(TAG, message, throwable)
    }
}
