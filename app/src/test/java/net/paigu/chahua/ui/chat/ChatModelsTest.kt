package net.paigu.chahua.ui.chat

import net.paigu.chahua.data.models.MessageDto
import net.paigu.chahua.data.models.StickerPackOrderItemDto
import net.paigu.chahua.data.models.StickerPackSummaryDto
import net.paigu.chahua.data.models.StickerMediaDto
import net.paigu.chahua.data.models.StickerPackPreviewStickerDto
import net.paigu.chahua.data.models.UserDto
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatModelsTest {

    private fun serverMsg(
        id: String,
        createdAt: String? = null,
        cgid: String = "",
        deleted: Boolean = false,
    ): MessageDto = MessageDto(
        id = id,
        clientGeneratedId = cgid,
        sender = UserDto(uid = 1),
        chatId = "c1",
        createdAt = createdAt,
        isDeleted = deleted,
    )

    private fun pending(
        cgid: String,
        createdAt: String = "2026-01-03T00:00:00Z",
    ): PendingMessage = PendingMessage(
        clientGeneratedId = cgid,
        text = "pending",
        attachmentLocalUri = null,
        createdAt = createdAt,
        replyToId = null,
    )

    @Test
    fun mergeChatItemsRemovesPendingConfirmedByServer() {
        val merged = mergeChatItems(
            serverList = listOf(serverMsg("m1", cgid = "cg-1")),
            pendingList = listOf(pending("cg-1"), pending("cg-2")),
        )
        assertEquals(2, merged.size)
        val pendingItems = merged.filterIsInstance<ChatItem.Pending>()
        assertEquals(listOf("cg-2"), pendingItems.map { it.pending.clientGeneratedId })
    }

    @Test
    fun mergeChatItemsFiltersDeletedServerMessages() {
        val merged = mergeChatItems(
            serverList = listOf(
                serverMsg("m1"),
                serverMsg("m2", deleted = true),
            ),
            pendingList = emptyList(),
        )
        assertEquals(listOf("m1"), merged.filterIsInstance<ChatItem.Server>().map { it.message.id })
    }

    @Test
    fun mergeChatItemsSortsServerAndPendingByTime() {
        val merged = mergeChatItems(
            serverList = listOf(
                serverMsg("b", createdAt = "2026-01-02T00:00:00Z"),
                serverMsg("a", createdAt = "2026-01-01T00:00:00Z"),
            ),
            pendingList = listOf(pending("cg-3", createdAt = "2026-01-03T00:00:00Z")),
        )
        val keys = merged.map { it.sortKey }
        assertEquals(
            listOf("2026-01-01T00:00:00Z", "2026-01-02T00:00:00Z", "2026-01-03T00:00:00Z"),
            keys,
        )
        assertEquals("cg-3", (merged.last() as ChatItem.Pending).pending.clientGeneratedId)
    }

    @Test
    fun sortPacksByOrderUsesLastUsedThenName() {
        val packs = listOf(
            StickerPackSummaryDto(id = "old", name = "A"),
            StickerPackSummaryDto(id = "new", name = "B"),
            StickerPackSummaryDto(id = "unknown", name = "C"),
        )
        val order = listOf(
            StickerPackOrderItemDto(stickerPackId = "old", lastUsedOn = 100),
            StickerPackOrderItemDto(stickerPackId = "new", lastUsedOn = 200),
        )

        val sorted = sortPacksByOrder(packs, order)

        assertEquals(listOf("new", "old", "unknown"), sorted.map { it.id })
    }
}
