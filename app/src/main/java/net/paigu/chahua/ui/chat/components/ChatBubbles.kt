package net.paigu.chahua.ui.chat.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.launch
import net.paigu.chahua.R
import net.paigu.chahua.core.AppGraph
import net.paigu.chahua.data.FontSizeOption
import net.paigu.chahua.data.models.MessageDto
import net.paigu.chahua.data.models.UserDto
import net.paigu.chahua.ui.chat.ChatItem
import net.paigu.chahua.ui.chat.MessageActionItem
import net.paigu.chahua.ui.chat.MessageActionMenu
import net.paigu.chahua.ui.chat.PendingMessage
import net.paigu.chahua.ui.chat.ReactionPill
import net.paigu.chahua.ui.common.AuthAsyncImage
import net.paigu.chahua.ui.common.UserAvatar
import net.paigu.chahua.ui.common.formatTime
import net.paigu.chahua.ui.common.renderMentionsAsText
import net.paigu.chahua.ui.theme.LocalAppSettings
import java.nio.ByteBuffer
import java.util.Base64
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MessageBubble(
    message: MessageDto,
    mine: Boolean,
    myAvatarUrl: String?,
    myName: String?,
    myUid: Int,
    isAdmin: Boolean,
    isPinned: Boolean,
    modifier: Modifier = Modifier,
    showAvatar: Boolean,
    showSenderName: Boolean,
    threadMode: Boolean,
    onOpenMedia: (url: String, kind: String, fileName: String?) -> Unit,
    onDownloadFile: (url: String, fileName: String?, kind: String) -> Unit,
    onOpenSticker: (stickerId: String) -> Unit,
    onReply: () -> Unit,
    onOpenReply: () -> Unit,
    onOpenThread: () -> Unit,
    onAvatarClick: (UserDto) -> Unit,
    onEdit: () -> Unit,
    quickReactionEmojis: List<String>,
    onQuickReact: (String) -> Unit,
    onReactionToggle: (String) -> Unit,
    onOpenReactionDetails: () -> Unit,
    onOpenEmojiPicker: () -> Unit,
    onDelete: () -> Unit,
    onSaveMessage: () -> Unit,
    onPinMessage: () -> Unit,
    onFavoriteSticker: (Boolean) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var anchorBounds by remember { mutableStateOf(IntRect.Zero) }
    val context = LocalContext.current
    val fontScale = FontSizeOption.from(LocalAppSettings.current.fontSizeKey).scale
    val showUidInChat = LocalAppSettings.current.showUidInChat
    val messageStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = (16 * fontScale).sp)
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val maxStickerHeight = (configuration.screenHeightDp * 0.2f).dp
    val replyThreshold = with(density) { 72.dp.toPx() }
    val maxSlide = with(density) { 160.dp.toPx() }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    var replyTriggered by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val slideOffset = remember { Animatable(0f) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        if (!mine) {
            if (showAvatar) {
                UserAvatar(
                    url = message.sender.avatarUrl,
                    name = message.sender.name,
                    modifier = Modifier.padding(top = 20.dp),
                    size = 32.dp,
                    onClick = { onAvatarClick(message.sender) },
                )
                Spacer(modifier = Modifier.width(8.dp))
            } else {
                Spacer(modifier = Modifier.width(40.dp))
            }
        }
        Column(
            horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            if (!mine && showSenderName && message.sender.name != null) {
                Text(
                    text = if (showUidInChat) {
                        stringResource(
                            R.string.chat_sender_name_with_uid,
                            message.sender.name,
                            message.sender.uid,
                        )
                    } else {
                        message.sender.name
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                )
            }
            val stickerOnly = message.sticker != null &&
                message.replyToMessage == null &&
                message.message.isNullOrBlank()
            Box(
                modifier = Modifier.onGloballyPositioned { coordinates ->
                    val rect = coordinates.boundsInWindow()
                    anchorBounds = IntRect(
                        rect.left.roundToInt(),
                        rect.top.roundToInt(),
                        rect.right.roundToInt(),
                        rect.bottom.roundToInt(),
                    )
                },
            ) {
                Column(
                    modifier = Modifier
                        .graphicsLayer { translationX = slideOffset.value }
                        .clip(
                            RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = if (mine) 16.dp else 4.dp,
                                bottomEnd = if (mine) 4.dp else 16.dp,
                            ),
                        )
                        .pointerInput(onReply) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    if (dragDistance <= -replyThreshold && !replyTriggered) {
                                        onReply()
                                    }
                                    dragDistance = 0f
                                    replyTriggered = false
                                    scope.launch { slideOffset.animateTo(0f) }
                                },
                                onDragCancel = {
                                    dragDistance = 0f
                                    replyTriggered = false
                                    scope.launch { slideOffset.animateTo(0f) }
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    dragDistance = (dragDistance + dragAmount)
                                        .coerceIn(-maxSlide, 0f)
                                    scope.launch { slideOffset.snapTo(dragDistance) }
                                    if (dragDistance <= -replyThreshold && !replyTriggered) {
                                        replyTriggered = true
                                        onReply()
                                        scope.launch { slideOffset.animateTo(0f) }
                                    }
                                },
                            )
                        }
                        .background(
                            if (stickerOnly) {
                                Color.Transparent
                            } else if (mine) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        )
                        .combinedClickable(
                            onClick = { },
                            onLongClick = { menuExpanded = true },
                        )
                        .padding(if (stickerOnly) 0.dp else 10.dp),
                ) {
                    message.replyToMessage?.let { reply ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                            modifier = Modifier
                                .padding(bottom = 6.dp)
                                .clickable(onClick = onOpenReply),
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                Text(
                                    text = reply.sender?.name ?: stringResource(R.string.chat_reply_message),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = renderMentionsAsText(reply.message, reply.mentions).ifBlank { "…" },
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    if (!message.message.isNullOrBlank()) {
                        Text(
                            text = renderMentionsAsText(message.message, message.mentions),
                            style = messageStyle,
                            color = if (mine) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                    message.sticker?.media?.url?.let { stickerUrl ->
                        AuthAsyncImage(
                            url = stickerUrl,
                            contentDescription = message.sticker?.emoji,
                            modifier = Modifier
                                .widthIn(max = 200.dp)
                                .heightIn(max = maxStickerHeight)
                                .combinedClickable(
                                    onClick = { onOpenSticker(message.sticker?.id.orEmpty()) },
                                    onLongClick = { menuExpanded = true },
                                ),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    if (message.messageType == "audio") {
                        val audioUrl = message.attachments
                            .firstOrNull { it.kind.startsWith("audio") }
                            ?.url
                        VoiceMessageBubble(url = audioUrl)
                    }
                    message.attachments.forEach { attachment ->
                        when {
                            attachment.kind.startsWith("audio") -> Unit
                            attachment.kind.startsWith("image") -> {
                                AuthAsyncImage(
                                    url = attachment.url,
                                    contentDescription = attachment.fileName,
                                    modifier = Modifier
                                        .widthIn(max = 220.dp)
                                        .height(160.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .combinedClickable(
                                            onClick = {
                                                onOpenMedia(attachment.url, "image", attachment.fileName)
                                            },
                                            onLongClick = { menuExpanded = true },
                                        ),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                            attachment.kind.startsWith("video") -> {
                                Box(
                                    modifier = Modifier
                                        .size(width = 200.dp, height = 120.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .combinedClickable(
                                            onClick = {
                                                onOpenMedia(attachment.url, "video", attachment.fileName)
                                            },
                                            onLongClick = { menuExpanded = true },
                                        ),
                                ) {
                                    if (attachment.fileName.isNotBlank()) {
                                        Text(
                                            text = attachment.fileName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .padding(8.dp),
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.5f))
                                            .align(Alignment.Center),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            Icons.Filled.PlayArrow,
                                            contentDescription = stringResource(R.string.chat_play),
                                            tint = Color.White,
                                        )
                                    }
                                }
                            }
                            else -> {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .combinedClickable(
                                            onClick = {
                                                onDownloadFile(
                                                    attachment.url,
                                                    attachment.fileName,
                                                    attachment.kind,
                                                )
                                            },
                                            onLongClick = { menuExpanded = true },
                                        )
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Filled.AttachFile,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = attachment.fileName,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        Icons.Filled.FileDownload,
                                        contentDescription = stringResource(R.string.chat_file_download),
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
                if (menuExpanded) {
                    Box(modifier = Modifier.matchParentSize()) {
                        MessageActionMenu(
                            expanded = true,
                            mine = mine,
                            anchorBounds = anchorBounds,
                            quickReactionEmojis = quickReactionEmojis,
                            onQuickReact = { emoji ->
                                menuExpanded = false
                                onQuickReact(emoji)
                            },
                            onOpenEmojiPicker = {
                                menuExpanded = false
                                onOpenEmojiPicker()
                            },
                            actions = buildList {
                                add(
                                    MessageActionItem(
                                        label = stringResource(R.string.chat_reply_to),
                                        icon = Icons.AutoMirrored.Filled.Reply,
                                        onClick = {
                                            menuExpanded = false
                                            onReply()
                                        },
                                    ),
                                )
                                if (mine && (!message.message.isNullOrBlank() || message.attachments.isNotEmpty())) {
                                    add(
                                        MessageActionItem(
                                            label = stringResource(R.string.chat_edit),
                                            icon = Icons.Filled.Edit,
                                            onClick = {
                                                menuExpanded = false
                                                onEdit()
                                            },
                                        ),
                                    )
                                }
                                if (!threadMode) {
                                    add(
                                        MessageActionItem(
                                            label = stringResource(R.string.chat_thread),
                                            icon = Icons.Filled.Forum,
                                            onClick = {
                                                menuExpanded = false
                                                onOpenThread()
                                            },
                                        ),
                                    )
                                }
                                if (message.reactions.isNotEmpty()) {
                                    add(
                                        MessageActionItem(
                                            label = stringResource(R.string.chat_reactions),
                                            icon = Icons.Filled.EmojiEmotions,
                                            onClick = {
                                                menuExpanded = false
                                                onOpenReactionDetails()
                                            },
                                        ),
                                    )
                                }
                                if (!message.message.isNullOrBlank()) {
                                    add(
                                        MessageActionItem(
                                            label = stringResource(R.string.chat_copy),
                                            icon = Icons.Filled.ContentCopy,
                                            onClick = {
                                                menuExpanded = false
                                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                                    as ClipboardManager
                                                cm.setPrimaryClip(
                                                    ClipData.newPlainText("message", message.message),
                                                )
                                            },
                                        ),
                                    )
                                }
                                add(
                                    MessageActionItem(
                                        label = stringResource(R.string.chat_copy_link),
                                        icon = Icons.Filled.Link,
                                        onClick = {
                                            menuExpanded = false
                                            val link = buildPermalinkUrl(
                                                chatId = message.chatId,
                                                messageId = message.id,
                                                apiBase = AppGraph.session.snapshot().serverUrl,
                                            )
                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                                as ClipboardManager
                                            cm.setPrimaryClip(ClipData.newPlainText("message link", link))
                                            Toast.makeText(
                                                context,
                                                R.string.chat_link_copied,
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        },
                                    ),
                                )
                                add(
                                    MessageActionItem(
                                        label = stringResource(R.string.chat_save),
                                        icon = Icons.Filled.Bookmark,
                                        onClick = {
                                            menuExpanded = false
                                            onSaveMessage()
                                        },
                                    ),
                                )
                                if (isAdmin) {
                                    add(
                                        MessageActionItem(
                                            label = stringResource(
                                                if (isPinned) {
                                                    R.string.chat_unpin
                                                } else {
                                                    R.string.chat_pin
                                                },
                                            ),
                                            icon = Icons.Filled.PushPin,
                                            onClick = {
                                                menuExpanded = false
                                                onPinMessage()
                                            },
                                        ),
                                    )
                                }
                                message.sticker?.let { sticker ->
                                    add(
                                        MessageActionItem(
                                            label = stringResource(
                                                if (sticker.isFavorited) {
                                                    R.string.chat_sticker_unfavorite
                                                } else {
                                                    R.string.chat_sticker_favorite
                                                },
                                            ),
                                            icon = if (sticker.isFavorited) {
                                                Icons.Filled.FavoriteBorder
                                            } else {
                                                Icons.Filled.Favorite
                                            },
                                            onClick = {
                                                menuExpanded = false
                                                onFavoriteSticker(!sticker.isFavorited)
                                            },
                                        ),
                                    )
                                }
                                if (mine) {
                                    add(
                                        MessageActionItem(
                                            label = stringResource(R.string.chat_recall),
                                            icon = Icons.Filled.Delete,
                                            destructive = true,
                                            onClick = {
                                                menuExpanded = false
                                                onDelete()
                                            },
                                        ),
                                    )
                                }
                            },
                            onDismiss = { menuExpanded = false },
                        )
                    }
                }
            }
            val sortedReactions = message.reactions.sortedWith(
                compareByDescending<net.paigu.chahua.data.models.ReactionDto> { it.count }
                    .thenBy { it.emoji },
            )
            if (sortedReactions.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
                ) {
                    sortedReactions.forEach { reaction ->
                        ReactionPill(
                            reaction = reaction,
                            mine = mine,
                            onToggle = { emoji ->
                                menuExpanded = false
                                onReactionToggle(emoji)
                            },
                        )
                    }
                }
            }
            message.threadInfo?.let { info ->
                if (!threadMode && info.replyCount > 0) {
                    Text(
                        text = stringResource(R.string.thread_reply_count, info.replyCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onOpenThread() }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                text = formatTime(message.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                fontSize = 10.sp,
            )
        }
        if (mine) {
            if (showAvatar) {
                Spacer(modifier = Modifier.width(8.dp))
                UserAvatar(
                    url = myAvatarUrl,
                    name = myName,
                    modifier = Modifier.padding(top = 20.dp),
                    size = 32.dp,
                    onClick = {
                        onAvatarClick(
                            UserDto(
                                uid = myUid,
                                avatarUrl = myAvatarUrl,
                                name = myName,
                            ),
                        )
                    },
                )
            } else {
                Spacer(modifier = Modifier.width(40.dp))
            }
        }
    }
}

internal fun chatItemKey(item: ChatItem): String = when (item) {
    is ChatItem.Server -> "s:${item.message.id}"
    is ChatItem.Pending -> "p:${item.pending.clientGeneratedId}"
}

private fun buildPermalinkUrl(chatId: String, messageId: String, apiBase: String): String {
    val uri = Uri.parse(apiBase.trimEnd('/'))
    val origin = "${uri.scheme}://${uri.authority}"
    val bytes = ByteBuffer.allocate(16)
        .putLong(chatId.toLong())
        .putLong(messageId.toLong())
        .array()
    val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    return "$origin/m/$encoded"
}

@Composable
internal fun PendingBubble(
    pending: PendingMessage,
    myAvatarUrl: String?,
    myName: String?,
    myUid: Int,
    showAvatar: Boolean,
    onAvatarClick: (UserDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(max = 280.dp)) {
            Row(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = 16.dp,
                            bottomEnd = 4.dp,
                        ),
                    )
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                pending.sticker?.let { sticker ->
                    AuthAsyncImage(
                        url = sticker.media.url,
                        contentDescription = sticker.emoji,
                        modifier = Modifier
                            .size(80.dp),
                        contentScale = ContentScale.Fit,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                pending.attachmentLocalUri?.let { uri ->
                    when (pending.attachmentKind) {
                        "image" -> AuthAsyncImage(
                            url = uri,
                            contentDescription = stringResource(R.string.chat_send_image),
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(8.dp)),
                        )
                        "video" -> Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.VideoLibrary,
                                contentDescription = stringResource(R.string.chat_video),
                                modifier = Modifier.size(32.dp),
                            )
                        }
                        else -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = pending.text ?: stringResource(R.string.chat_file),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                pending.text?.let {
                    Text(
                        text = renderMentionsAsText(it, emptyList()),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            }
            Text(
                text = stringResource(R.string.chat_sending),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
            )
        }
        if (showAvatar) {
            Spacer(modifier = Modifier.width(8.dp))
            UserAvatar(
                url = myAvatarUrl,
                name = myName,
                modifier = Modifier.padding(top = 20.dp),
                size = 32.dp,
                onClick = {
                    onAvatarClick(
                        UserDto(
                            uid = myUid,
                            avatarUrl = myAvatarUrl,
                            name = myName,
                        ),
                    )
                },
            )
        } else {
            Spacer(modifier = Modifier.width(40.dp))
        }
    }
}
