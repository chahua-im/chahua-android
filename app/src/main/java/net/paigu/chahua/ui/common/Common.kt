package net.paigu.chahua.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import net.paigu.chahua.core.AppGraph
import net.paigu.chahua.data.models.MessagePreviewDto
import net.paigu.chahua.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 带鉴权头的网络图片（私有 S3 资源需要附加认证头）。 */
@Composable
fun AuthAsyncImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    if (url.isNullOrBlank()) return
    AsyncImage(
        model = url,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        imageLoader = AppGraph.imageLoader,
    )
}

/** 圆形头像：有 URL 加载图片，否则显示姓名首字符。 */
@Composable
fun UserAvatar(
    url: String?,
    name: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (!url.isNullOrBlank()) {
            AuthAsyncImage(
                url = url,
                contentDescription = name ?: stringResource(R.string.common_avatar_desc),
                modifier = Modifier.size(size),
            )
        } else {
            val initial = (name ?: "?").trim().take(1).uppercase(Locale.getDefault())
            Text(
                text = initial.ifBlank { "?" },
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

@Composable
fun formatTime(iso: String?): String = formatTime(iso, stringResource(R.string.time_format_date))

@Composable
fun formatListTime(iso: String?): String = formatTime(iso, stringResource(R.string.time_format_date))

private fun formatTime(iso: String?, datePattern: String): String {
    if (iso.isNullOrBlank()) return ""
    return try {
        val instant = Instant.parse(iso)
        val zone = ZoneId.systemDefault()
        val ldt = instant.atZone(zone).toLocalDateTime()
        val now = java.time.LocalDateTime.now()
        if (ldt.toLocalDate() == now.toLocalDate()) {
            ldt.format(timeFormatter)
        } else {
            ldt.format(DateTimeFormatter.ofPattern(datePattern, Locale.getDefault()))
        }
    } catch (e: Exception) {
        ""
    }
}

@Composable
fun EmptyState(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
fun messagePreviewText(message: String?, messageType: String?): String {
    if (!message.isNullOrBlank()) return message
    return when (messageType) {
        "image" -> stringResource(R.string.chat_image)
        "video" -> stringResource(R.string.chat_video)
        "audio" -> stringResource(R.string.chat_audio)
        "file" -> stringResource(R.string.chat_file)
        "sticker" -> stringResource(R.string.chat_sticker)
        else -> stringResource(R.string.chat_message)
    }
}

/** 列表预览：带发言人前缀，如 "user：message"。 */
@Composable
fun messagePreviewWithSender(
    preview: MessagePreviewDto?,
    unknownSender: String,
): String {
    if (preview == null) return stringResource(R.string.chat_message)
    val sender = preview.sender?.name?.takeIf { it.isNotBlank() } ?: unknownSender
    val text = messagePreviewText(preview.message, preview.messageType)
    return stringResource(R.string.message_sender_format, sender, text)
}
