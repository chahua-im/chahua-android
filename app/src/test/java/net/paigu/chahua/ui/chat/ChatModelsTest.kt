package net.paigu.chahua.ui.chat

import net.paigu.chahua.data.models.MessageDto
import net.paigu.chahua.data.models.StickerPackDetailResponse
import net.paigu.chahua.data.models.StickerPackOrderItemDto
import net.paigu.chahua.data.models.StickerPackSummaryDto
import net.paigu.chahua.data.models.StickerMediaDto
import net.paigu.chahua.data.models.StickerPackPreviewStickerDto
import net.paigu.chahua.data.models.StickerSummaryDto
import net.paigu.chahua.data.models.UserDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    private fun sticker(id: String, favorited: Boolean = false): StickerSummaryDto =
        StickerSummaryDto(
            id = id,
            media = StickerMediaDto(id = "media-$id", url = "https://example.com/$id.png"),
            emoji = "😀",
            isFavorited = favorited,
        )

    @Test
    fun applyStickerFavoriteToPanelAddsToFavoritesAndSyncsPackDetails() {
        val panel = StickerPanelUiState(
            details = mapOf(
                "p1" to StickerPackDetailResponse(
                    id = "p1",
                    stickers = listOf(sticker("s1"), sticker("s2")),
                ),
            ),
        )

        val updated = applyStickerFavoriteToPanel(
            panel = panel,
            stickerId = "s1",
            sticker = sticker("s1").copy(isFavorited = true),
            favorite = true,
        )

        assertEquals(listOf("s1"), updated.favorites.map { it.id })
        assertTrue(updated.favorites.single().isFavorited)
        assertTrue(updated.details.getValue("p1").stickers.first().isFavorited)
        assertFalse(updated.details.getValue("p1").stickers.last().isFavorited)
    }

    @Test
    fun applyStickerFavoriteToPanelRemovesFromFavoritesAndSyncsPackDetails() {
        val panel = StickerPanelUiState(
            favorites = listOf(sticker("s1", favorited = true)),
            details = mapOf(
                "p1" to StickerPackDetailResponse(
                    id = "p1",
                    stickers = listOf(sticker("s1", favorited = true)),
                ),
            ),
        )

        val updated = applyStickerFavoriteToPanel(
            panel = panel,
            stickerId = "s1",
            sticker = null,
            favorite = false,
        )

        assertTrue(updated.favorites.isEmpty())
        assertFalse(updated.details.getValue("p1").stickers.single().isFavorited)
    }

    @Test
    fun applyStickerFavoriteToPanelDoesNotDuplicateExistingFavorite() {
        val panel = StickerPanelUiState(favorites = listOf(sticker("s1", favorited = true)))

        val updated = applyStickerFavoriteToPanel(
            panel = panel,
            stickerId = "s1",
            sticker = sticker("s1", favorited = true),
            favorite = true,
        )

        assertEquals(1, updated.favorites.size)
    }

    @Test
    fun applyStickerFavoriteToPanelKeepsFavoritesUnchangedWithoutSummary() {
        val panel = StickerPanelUiState()

        val updated = applyStickerFavoriteToPanel(
            panel = panel,
            stickerId = "s1",
            sticker = null,
            favorite = true,
        )

        assertTrue(updated.favorites.isEmpty())
    }
}
