package com.sh7411usa.shliachtzibbur.sync

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Process-wide hints so the sync layer can suppress notifications for content the
 * user is already looking at. Updated by the Activity and the messages screen.
 */
object AppForegroundState {
    private val foreground = AtomicBoolean(false)
    private val visibleGroup = AtomicReference<String?>(null)

    var isInForeground: Boolean
        get() = foreground.get()
        set(value) = foreground.set(value)

    var visibleGroupId: String?
        get() = visibleGroup.get()
        set(value) = visibleGroup.set(value)

    /** True when a new message for [groupId] should NOT raise a notification. */
    fun suppressesNotificationFor(groupId: String): Boolean =
        isInForeground && visibleGroupId == groupId
}
