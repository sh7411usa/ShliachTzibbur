package com.sh7411usa.shliachtzibbur.model

import com.sh7411usa.shliachtzibbur.data.local.entity.GroupEntity
import com.sh7411usa.shliachtzibbur.data.local.entity.toDomain
import org.junit.Assert.assertEquals
import org.junit.Test

class UnreadTest {

    private fun entity(lastMessageSeq: Long, lastReadSeq: Long) = GroupEntity(
        id = "g", name = "G", category = "family", kind = "standard", createdBy = null,
        createdAt = null, role = "member", memberCount = 3, readSeq = 0, unreadCountHint = 7,
        whoCanPost = "everyone", whoCanAddMembers = "admins",
        memberCap = 100, messageMaxLength = 1000, minMembersToPost = 3,
        lastReadSeq = lastReadSeq, lastMessageSeq = lastMessageSeq,
    )

    @Test
    fun `unread is latest minus read, never negative, ignoring the stale server hint`() {
        assertEquals(0, entity(lastMessageSeq = 0, lastReadSeq = 0).toDomain(muted = false).unreadCount)
        assertEquals(3, entity(lastMessageSeq = 12, lastReadSeq = 9).toDomain(muted = false).unreadCount)
        assertEquals(0, entity(lastMessageSeq = 12, lastReadSeq = 12).toDomain(muted = false).unreadCount)
        assertEquals(0, entity(lastMessageSeq = 5, lastReadSeq = 9).toDomain(muted = false).unreadCount)
    }
}
