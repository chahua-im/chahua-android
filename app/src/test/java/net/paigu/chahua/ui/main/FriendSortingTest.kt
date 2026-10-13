package net.paigu.chahua.ui.main

import net.paigu.chahua.data.models.ChatDto
import net.paigu.chahua.data.models.FriendResponse
import net.paigu.chahua.data.models.MemberSummaryDto
import org.junit.Assert.assertEquals
import org.junit.Test

/** 好友列表按最近聊天排序（issue #30）。 */
class FriendSortingTest {

    private fun friend(uid: Int, name: String) =
        FriendResponse(user = MemberSummaryDto(uid = uid, username = name))

    private fun dm(uid: Int, at: String?) = ChatDto(
        id = "dm-$uid",
        kind = "dm",
        peer = MemberSummaryDto(uid = uid, username = "peer$uid"),
        lastMessageAt = at,
    )

    @Test
    fun `sorts friends by most recent chat`() {
        val friends = listOf(friend(1, "alice"), friend(2, "bob"), friend(3, "carol"))
        val chats = listOf(
            dm(1, "2026-10-13T10:00:00Z"),
            dm(2, "2026-10-13T12:00:00Z"),
            dm(3, "2026-10-13T11:00:00Z"),
        )
        val sorted = sortFriendsByRecentChat(friends, chats).map { it.user.uid }
        assertEquals(listOf(2, 3, 1), sorted)
    }

    @Test
    fun `friends without chats go last ordered by name`() {
        val friends = listOf(
            friend(1, "zoe"),
            friend(2, "bob"),
            friend(3, "alice"),
        )
        val chats = listOf(dm(1, "2026-10-13T10:00:00Z"))
        val sorted = sortFriendsByRecentChat(friends, chats).map { it.user.uid }
        // 有会话的 zoe 在最前，其余按昵称 alice、bob。
        assertEquals(listOf(1, 3, 2), sorted)
    }

    @Test
    fun `invalid or missing timestamp falls back to name order`() {
        val friends = listOf(friend(1, "alice"), friend(2, "bob"))
        val chats = listOf(dm(1, "not-a-time"), dm(2, null))
        val sorted = sortFriendsByRecentChat(friends, chats).map { it.user.uid }
        assertEquals(listOf(1, 2), sorted)
    }

    @Test
    fun `group chats are ignored`() {
        val friends = listOf(friend(1, "alice"), friend(2, "bob"))
        val chats = listOf(
            ChatDto(id = "g1", kind = "group", lastMessageAt = "2026-10-13T23:00:00Z"),
            dm(2, "2026-10-13T09:00:00Z"),
        )
        val sorted = sortFriendsByRecentChat(friends, chats).map { it.user.uid }
        assertEquals(listOf(2, 1), sorted)
    }

    @Test
    fun `lastChatMillis parses iso timestamps`() {
        assertEquals(0L, lastChatMillis(null))
        assertEquals(0L, lastChatMillis(ChatDto(id = "x", lastMessageAt = "oops")))
        assertEquals(
            1_760_000_000_000L,
            lastChatMillis(ChatDto(id = "x", lastMessageAt = "2025-10-09T08:53:20Z")),
        )
    }
}
