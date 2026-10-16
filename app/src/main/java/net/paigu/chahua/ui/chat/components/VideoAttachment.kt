package net.paigu.chahua.ui.chat.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import net.paigu.chahua.R
import net.paigu.chahua.data.VideoCache
import net.paigu.chahua.data.VideoCacheState
import net.paigu.chahua.data.VideoThumbnail
import net.paigu.chahua.data.models.AttachmentDto
import net.paigu.chahua.ui.main.formatBytes
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * 视频附件气泡：
 * - 进入聊天即显示封面（MediaMetadataRetriever 抽帧，已缓存时本地读取）；
 * - 未缓存时右上角是下载按钮 + 视频大小，点击后开始缓存并在聊天里**静音预览**；
 * - 缓存中显示进度，缓存完成后右上角换成视频时长；
 * - 点击画面（含预览）直接全屏播放，全屏页默认不显示控件。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun VideoAttachmentBubble(
    attachment: AttachmentDto,
    onOpenViewer: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val url = attachment.url
    val context = LocalContext.current
    val states by VideoCache.states.collectAsState()
    val initiallyCached = remember(url) { VideoCache.isCached(url) }
    val cacheState = states[url] ?: if (initiallyCached) VideoCacheState.Cached else VideoCacheState.Idle
    var previewing by remember(url) { mutableStateOf(false) }
    var thumbnail by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    var durationMs by remember(url) { mutableLongStateOf(0L) }

    // 封面：无论是否缓存都尝试生成（取帧数据走缓存数据源，只读解码需要的片段）。
    LaunchedEffect(url) {
        val poster = VideoThumbnail.load(context, url, attachment.size) ?: return@LaunchedEffect
        durationMs = poster.durationMs
        poster.bitmap?.let { thumbnail = it.asImageBitmap() }
    }
    val ratio = remember(attachment.width, attachment.height, thumbnail) {
        val w = attachment.width ?: 0
        val h = attachment.height ?: 0
        when {
            w > 0 && h > 0 -> (w.toFloat() / h).coerceIn(0.6f, 1.9f)
            thumbnail != null -> {
                val bitmap = thumbnail!!
                (bitmap.width.toFloat() / bitmap.height).coerceIn(0.6f, 1.9f)
            }
            else -> 16f / 9f
        }
    }

    if (previewing) {
        InlineVideoPreview(
            url = url,
            aspectRatio = ratio,
            onOpenFullscreen = onOpenViewer,
            onClose = { previewing = false },
            onLongClick = onLongClick,
            onDurationKnown = { if (it > 0L) durationMs = it },
            modifier = modifier,
        )
        return
    }

    Box(
        modifier = modifier
            .width(200.dp)
            .aspectRatio(ratio)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.35f))
            .combinedClickable(onClick = onOpenViewer, onLongClick = onLongClick),
    ) {
        thumbnail?.let { bitmap ->
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // 压一层暗色，保证播放按钮与文件名在亮色画面上也清晰。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f)),
            )
        }
        if (attachment.fileName.isNotBlank()) {
            Text(
                text = attachment.fileName,
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
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
        VideoCacheBadge(
            state = cacheState,
            sizeBytes = attachment.size,
            durationMs = durationMs,
            onDownload = {
                // 点击后：开始缓存 + 在聊天里静音预览（再点画面即全屏）。
                VideoCache.preCache(url, attachment.size)
                previewing = true
            },
            onLongClick = onLongClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp),
        )
    }
}

/**
 * 右上角角标：未缓存=下载按钮+大小，缓存中=进度，已缓存=视频时长。
 * 长按和气泡其它区域一致，弹出消息菜单（不是再触发一次下载）。
 */
@Composable
private fun VideoCacheBadge(
    state: VideoCacheState,
    sizeBytes: Long,
    durationMs: Long,
    onDownload: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state is VideoCacheState.Cached && durationMs <= 0L) return
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.55f))
            .combinedClickable(onClick = onDownload, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        when {
            state is VideoCacheState.Cached -> {
                Text(
                    text = formatDuration(durationMs),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            state is VideoCacheState.Downloading -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = Color.White,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = downloadProgressText(state, sizeBytes),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.FileDownload,
                        contentDescription = stringResource(R.string.chat_video_download),
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                    if (sizeBytes > 0) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = formatBytes(sizeBytes),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

/** 视频时长：mm:ss（超过一小时显示 h:mm:ss）。 */
internal fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

private fun downloadProgressText(state: VideoCacheState.Downloading, sizeBytes: Long): String {
    val total = if (state.total > 0) state.total else sizeBytes
    return if (total > 0) {
        val percent = (state.bytes * 100 / total).coerceIn(0, 100)
        "$percent%"
    } else {
        formatBytes(state.bytes)
    }
}

/**
 * 聊天内嵌预览播放器：**静音**、无播放器控件，点画面直接进全屏；
 * 走视频缓存，切到后台自动暂停，离开组合即释放。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InlineVideoPreview(
    url: String,
    aspectRatio: Float,
    onOpenFullscreen: () -> Unit,
    onClose: () -> Unit,
    onLongClick: () -> Unit,
    onDurationKnown: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = context as? LifecycleOwner
    val currentOnDurationKnown by rememberUpdatedState(onDurationKnown)
    val player = remember(url) {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(VideoCache.mediaSourceFactory(url))
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(url))
                repeatMode = Player.REPEAT_MODE_OFF
                volume = 0f // 聊天里预览静音，点开全屏才出声
                playWhenReady = true
                prepare()
            }
    }
    // 播放器解析出时长后回传给气泡（右上角角标改用时长显示）。
    LaunchedEffect(player) {
        while (true) {
            val duration = player.duration
            if (duration > 0) {
                currentOnDurationKnown(duration)
                break
            }
            delay(500)
        }
    }
    DisposableEffect(player, lifecycleOwner) {
        val observer = object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                player.pause()
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose {
            lifecycleOwner?.lifecycle?.removeObserver(observer)
            player.release()
        }
    }
    // 预览播放的同时继续把整个视频写进缓存，播完即缓存完成。
    LaunchedEffect(url) {
        VideoCache.preCache(url)
    }

    Box(
        modifier = modifier
            .width(240.dp)
            .aspectRatio(aspectRatio)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black),
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    // 预览不带控件：点画面直接全屏，控件留给全屏页。
                    useController = false
                    this.player = player
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        // PlayerView 是真实 View，触摸事件可能被它吃掉。盖一层透明手势层，
        // 让点击进全屏、长按弹消息菜单都由 Compose 处理。
        Box(
            modifier = Modifier
                .matchParentSize()
                .combinedClickable(
                    onClick = onOpenFullscreen,
                    onLongClick = onLongClick,
                ),
        )
        RoundIconButton(
            icon = Icons.Filled.Close,
            contentDescription = stringResource(R.string.chat_video_close_preview),
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp),
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .combinedClickable(onClick = onClick, onLongClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(16.dp),
        )
    }
}
