package com.sh7411usa.shliachtzibbur.core.util

import java.util.UUID

object Ids {
    /** A fresh random UUID string, used for `clientMessageId`. */
    fun newUuid(): String = UUID.randomUUID().toString()
}
