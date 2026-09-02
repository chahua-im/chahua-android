package net.paigu.chahua.ui.chat

import net.paigu.chahua.data.models.MessageDto
import net.paigu.chahua.data.models.MemberSummaryDto
import net.paigu.chahua.data.models.PinDto
import net.paigu.chahua.data.models.StickerDetailResponse
import net.paigu.chahua.data.models.StickerPackDetailResponse
import net.paigu.chahua.data.models.StickerPackOrderItemDto
import net.paigu.chahua.data.models.StickerPackSummaryDto
import net.paigu.chahua.data.models.StickerSummaryDto

/** 输入框中待发送的附件草稿。 */
data class DraftAttachment(
    val uriString: String,
    val mimeType: String,
    val fileName: String,
    val kind: String, // image | video | file
    val compressVideo: Boolean = false,
)

data class PendingMessage(
    val clientGeneratedId: String,
    val text: String?,
    val attachmentLocalUri: String?,
    val createdAt: String,
    val replyToId: String?,
    val attachmentKind: String = "image",
    val sticker: StickerSummaryDto? = null,
)

sealed interface ChatItem {
    val sortKey: String

    data class Server(val message: MessageDto) : ChatItem {
        override val sortKey: String get() = message.createdAt ?: message.id
    }

    data class Pending(val pending: PendingMessage) : ChatItem {
        override val sortKey: String get() = pending.createdAt
    }
}

data class ChatUiState(
    val loading: Boolean = false,
    val loadingOlder: Boolean = false,
    val error: String? = null,
    val replyTarget: MessageDto? = null,
    val threadMode: Boolean = false,
    val threadReplyCount: Long = 0,
    val title: String = "",
    val scrollToMessageId: String? = null,
    val myRole: String? = null,
    val isDm: Boolean = false,
    val peer: MemberSummaryDto? = null,
    val pins: List<PinDto> = emptyList(),
)

data class StickerPanelUiState(
    val favorites: List<StickerSummaryDto> = emptyList(),
    val packs: List<StickerPackSummaryDto> = emptyList(),
    val details: Map<String, StickerPackDetailResponse> = emptyMap(),
    val selectedPackId: String? = null,
    val loadingPacks: Boolean = false,
    val loadingPackId: String? = null,
    val error: String? = null,
)

/**
 * 收藏 / 取消收藏一张贴纸后，同步更新表情面板状态：
 * - 收藏时向“收藏”列表补充贴纸（已在列表中则不重复）；
 * - 取消收藏时从“收藏”列表移除；
 * - 各已加载表情包详情中的同款贴纸同步刷新收藏标记。
 */
internal fun applyStickerFavoriteToPanel(
    panel: StickerPanelUiState,
    stickerId: String,
    sticker: StickerSummaryDto?,
    favorite: Boolean,
): StickerPanelUiState {
    val favorites = if (favorite) {
        if (panel.favorites.any { it.id == stickerId }) {
            panel.favorites
        } else {
            panel.favorites + listOfNotNull(sticker)
        }
    } else {
        panel.favorites.filterNot { it.id == stickerId }
    }
    return panel.copy(
        favorites = favorites,
        details = panel.details.mapValues { (_, detail) ->
            detail.copy(
                stickers = detail.stickers.map {
                    if (it.id == stickerId) it.copy(isFavorited = favorite) else it
                },
            )
        },
    )
}

/** 聊天内贴纸预览弹窗状态。 */
data class StickerPreviewUiState(
    val stickerId: String? = null,
    val loading: Boolean = false,
    val detail: StickerDetailResponse? = null,
    val error: String? = null,
    val busyFavorite: Boolean = false,
    val busySubscribe: Boolean = false,
    val subscribed: Boolean = false,
)

/**
 * 合并服务端消息与本地待发送消息：
 * - 已被服务端确认（clientGeneratedId 已存在）的待发送项移除；
 * - 已删除消息不展示；
 * - 按时间排序。
 */
internal fun mergeChatItems(
    serverList: List<MessageDto>,
    pendingList: List<PendingMessage>,
): List<ChatItem> {
    val serverCgids = serverList.mapTo(HashSet()) { it.clientGeneratedId }
    val pending = pendingList.filterNot { it.clientGeneratedId in serverCgids }
    return (serverList.filterNot { it.isDeleted }.map { ChatItem.Server(it) as ChatItem } +
        pending.map { ChatItem.Pending(it) as ChatItem })
        .sortedBy { it.sortKey }
}

/** 按服务端记录的最后使用时间排序贴纸包（未记录的最后使用时间视为最旧）。 */
internal fun sortPacksByOrder(
    packs: List<StickerPackSummaryDto>,
    order: List<StickerPackOrderItemDto>,
): List<StickerPackSummaryDto> {
    val usedAt = order.associate { it.stickerPackId to it.lastUsedOn }
    return packs.sortedWith(
        compareByDescending<StickerPackSummaryDto> { usedAt[it.id] ?: Long.MIN_VALUE }
            .thenBy { it.name },
    )
}
