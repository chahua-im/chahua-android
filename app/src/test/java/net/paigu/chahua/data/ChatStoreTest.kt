package net.paigu.chahua.data

import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.paigu.chahua.data.models.ChatDto
import net.paigu.chahua.data.models.MessageDto
import net.paigu.chahua.data.models.MessagePreviewDto
import net.paigu.chahua.data.models.ReactionDto
import net.paigu.chahua.data.models.StickerDto
import net.paigu.chahua.data.models.StickerMediaDto
import net.paigu.chahua.data.models.StickerSummaryDto
import net.paigu.chahua.data.models.ThreadDto
import net.paigu.chahua.data.models.UserDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatStoreTest {

    private fun msg(
        id: String,
        createdAt: String? = null,
        chatId: String = "c1",
        senderUid: Int = 1,
        cgid: String = "",
        text: String? = null,
        replyRootId: String? = null,
        deleted: Boolean = false,
    ): MessageDto = MessageDto(
        id = id,
        message = text,
        replyRootId = replyRootId,
        clientGeneratedId = cgid,
        sender = UserDto(uid = senderUid),
        chatId = chatId,
        createdAt = createdAt,
        isDeleted = deleted,
    )

    private fun timelineIds(store: ChatStore, chatId: String = "c1"): List<String> =
        store.messages.value[chatId].orEmpty().map { it.id }

    @Test
    fun setMessagesSortsByCreatedAtThenId() {
        val store = ChatStore()
        store.setMessages(
            "c1",
            null,
            listOf(
                msg("b", createdAt = "2026-01-02T00:00:00Z"),
                msg("a", createdAt = "2026-01-01T00:00:00Z"),
                msg("c", createdAt = "2026-01-01T00:00:00Z"),
            ),
        )
        assertEquals(listOf("a", "c", "b"), timelineIds(store))
    }

    @Test
    fun appendMessagesDedupesById() {
        val store = ChatStore()
        store.appendMessages("c1", null, listOf(msg("a"), msg("b")))
        store.appendMessages("c1", null, listOf(msg("b"), msg("c")))
        assertEquals(listOf("a", "b", "c"), timelineIds(store))
    }

    @Test
    fun onNewMessageAppendsTimelineAndBumpsUnreadOnlyForOthers() {
        val store = ChatStore().apply { currentUid = { 1 } }
        store.setChats(listOf(ChatDto(id = "c1", name = "Chat")))

        store.onNewMessage(msg("m1", chatId = "c1", senderUid = 2, text = "hello"))

        assertEquals(listOf("m1"), timelineIds(store))
        val chat = store.chats.value.single()
        assertEquals("hello", chat.lastMessage?.message)
        assertEquals(1L, chat.unreadCount)

        store.onNewMessage(msg("m2", chatId = "c1", senderUid = 1, text = "mine"))

        assertEquals(1L, store.chats.value.single().unreadCount)
        assertEquals("mine", store.chats.value.single().lastMessage?.message)
    }

    @Test
    fun onNewMessageInThreadBumpsThreadNotChat() {
        val store = ChatStore().apply { currentUid = { 1 } }
        store.setChats(listOf(ChatDto(id = "c1", name = "Chat")))
        store.setThreads(
            listOf(
                ThreadDto(
                    chatId = "c1",
                    chatName = "Chat",
                    threadRootMessage = MessagePreviewDto(id = "root"),
                    replyCount = 1,
                ),
            ),
        )

        store.onNewMessage(
            msg("m1", chatId = "c1", senderUid = 2, replyRootId = "root", text = "reply"),
        )

        assertEquals(0L, store.chats.value.single().unreadCount)
        val thread = store.threads.value.single()
        assertEquals(2L, thread.replyCount)
        assertEquals(1L, thread.unreadCount)
    }

    @Test
    fun onMessageUpdatedReplacesInPlace() {
        val store = ChatStore()
        store.setMessages("c1", null, listOf(msg("a", text = "old")))

        store.onMessageUpdated(msg("a", text = "new"))

        assertEquals("new", store.messages.value["c1"]!!.single().message)
    }

    @Test
    fun onMessageDeletedMarksDeletedAndClearsText() {
        val store = ChatStore()
        store.setMessages("c1", null, listOf(msg("a", text = "secret")))

        store.onMessageDeleted("c1", null, "a")

        val updated = store.messages.value["c1"]!!.single()
        assertTrue(updated.isDeleted)
        assertNull(updated.message)
    }

    @Test
    fun removeMessagesFiltersByIds() {
        val store = ChatStore()
        store.setMessages("c1", null, listOf(msg("a"), msg("b"), msg("c")))

        store.removeMessages("c1", null, listOf("a", "c"))

        assertEquals(listOf("b"), timelineIds(store))
    }

    @Test
    fun onReactionUpdateMergesAndPreservesLocalReactedByMe() {
        val store = ChatStore()
        store.setMessages(
            "c1",
            null,
            listOf(
                msg("a").copy(
                    reactions = listOf(ReactionDto(emoji = "👍", count = 2, reactedByMe = true)),
                ),
            ),
        )

        store.onReactionUpdate(
            "c1",
            "a",
            listOf(ReactionDto(emoji = "👍", count = 3, reactedByMe = null)),
        )

        val reaction = store.messages.value["c1"]!!.single().reactions.single()
        assertEquals(3L, reaction.count)
        assertEquals(true, reaction.reactedByMe)
    }

    @Test
    fun applyStickerFavoriteUpdatesMessagesAcrossTimelines() {
        val store = ChatStore()
        val sticker = StickerDto(
            id = "s1",
            media = StickerMediaDto(id = "m1", url = "https://example.com/s.png"),
        )
        store.setMessages("c1", null, listOf(msg("a").copy(sticker = sticker)))
        store.setMessages("c1", "root", listOf(msg("b", replyRootId = "root").copy(sticker = sticker)))

        store.applyStickerFavorite("s1", favorite = true)

        assertTrue(store.messages.value["c1"]!!.single().sticker!!.isFavorited)
        assertTrue(store.messages.value["c1:root"]!!.single().sticker!!.isFavorited)
    }

    @Test
    fun markChatReadUpdatesUnread() {
        val store = ChatStore()
        store.setChats(listOf(ChatDto(id = "c1", name = "Chat", unreadCount = 5)))

        store.markChatRead("c1", "last", 0)

        val chat = store.chats.value.single()
        assertEquals("last", chat.lastReadMessageId)
        assertEquals(0L, chat.unreadCount)
    }

    @Test
    fun removeChatClearsTimelinesAndThreads() {
        val store = ChatStore()
        store.setChats(listOf(ChatDto(id = "c1", name = "Chat")))
        store.setThreads(
            listOf(
                ThreadDto(
                    chatId = "c1",
                    chatName = "Chat",
                    threadRootMessage = MessagePreviewDto(id = "root"),
                ),
            ),
        )
        store.setMessages("c1", null, listOf(msg("a")))
        store.setMessages("c1", "root", listOf(msg("b", replyRootId = "root")))

        store.removeChat("c1")

        assertTrue(store.chats.value.isEmpty())
        assertTrue(store.threads.value.isEmpty())
        assertFalse(store.messages.value.containsKey("c1"))
        assertFalse(store.messages.value.containsKey("c1:root"))
    }

    @Test
    fun clearResetsEverything() {
        val store = ChatStore()
        store.setChats(listOf(ChatDto(id = "c1", name = "Chat")))
        store.setMessages("c1", null, listOf(msg("a")))
        store.setConnectionState(WsStatus.CONNECTED)

        store.clear()

        assertTrue(store.chats.value.isEmpty())
        assertTrue(store.messages.value.isEmpty())
        assertEquals(WsStatus.DISCONNECTED, store.connectionState.value)
    }

    @Test
    fun incomingEmitsNewMessages() = runBlocking {
        val store = ChatStore()
        val deferred = async(start = CoroutineStart.UNDISPATCHED) { store.incoming.first() }
        store.onNewMessage(msg("m1", chatId = "c1", text = "hi"))
        assertEquals("m1", deferred.await().id)
    }
}
