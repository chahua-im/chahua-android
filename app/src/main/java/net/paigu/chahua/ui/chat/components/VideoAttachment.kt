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
import androidx.compose.material.icons.filled.Fullscreen
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
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

/**
 * 视频附件气泡：
 * - 未缓存时右上角显示下载按钮 + 视频大小，点击后开始缓存并**在聊天里直接播放预览**；
 * - 缓存中显示进度；已缓存则直接点开播放（全屏查看器同样走缓存）；
 * - 预览播放器带全屏 / 关闭按钮。
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

    // 封面：已缓存/正在缓存时用 MediaMetadataRetriever 抽第一帧（数据走缓存，不额外下载整段视频）。
    val shouldLoadThumbnail = cacheState is VideoCacheState.Cached || cacheState is VideoCacheState.Downloading
    LaunchedEffect(url, shouldLoadThumbnail) {
        if (thumbnail == null && shouldLoadThumbnail) {
            thumbnail = VideoThumbnail.load(context, url, attachment.size)?.asImageBitmap()
        }
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
            onClose = { previewing = false },
            onExpand = onOpenViewer,
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
            onDownload = {
                // 点击后：开始缓存 + 直接在聊天里播放预览。
                VideoCache.preCache(url, attachment.size)
                previewing = true
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp),
        )
    }
}

/** 右上角的缓存状态角标：未缓存=下载按钮+大小，缓存中=进度，已缓存=不显示。 */
@Composable
private fun VideoCacheBadge(
    state: VideoCacheState,
    sizeBytes: Long,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state is VideoCacheState.Cached) return
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.55f))
            .combinedClickable(onClick = onDownload, onLongClick = onDownload)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        when (state) {
            is VideoCacheState.Downloading -> {
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

private fun downloadProgressText(state: VideoCacheState.Downloading, sizeBytes: Long): String {
    val total = if (state.total > 0) state.total else sizeBytes
    return if (total > 0) {
        val percent = (state.bytes * 100 / total).coerceIn(0, 100)
        "$percent%"
    } else {
        formatBytes(state.bytes)
    }
}

/** 聊天内嵌预览播放器：走视频缓存，切到后台自动暂停，离开组合即释放。 */
@Composable
private fun InlineVideoPreview(
    url: String,
    aspectRatio: Float,
    onClose: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = context as? LifecycleOwner
    val player = remember(url) {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(VideoCache.mediaSourceFactory(url))
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(url))
                repeatMode = Player.REPEAT_MODE_OFF
                playWhenReady = true
                prepare()
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
                    useController = true
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    this.player = player
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            RoundIconButton(
                icon = Icons.Filled.Fullscreen,
                contentDescription = stringResource(R.string.chat_video_fullscreen),
                onClick = onExpand,
            )
            RoundIconButton(
                icon = Icons.Filled.Close,
                contentDescription = stringResource(R.string.chat_video_close_preview),
                onClick = onClose,
            )
        }
    }
}

@Composable
private fun RoundIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
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
