package net.paigu.chahua.ui.chat.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 视频附件气泡：
 * - **未缓存**：先用首帧封面当占位，右上角是"下载 + 体积"角标；点画面或角标开始缓存并显示进度；
 * - **已缓存**：直接在聊天里静音播放，右上角显示已播放时间，点画面进全屏；
 * - 不做"边下边播"：只有整段缓存完成后才会在聊天里播放。
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
    // 只订阅这条视频自己的状态：别的视频在下载/完成时不会让这个气泡重组。
    val cacheState by remember(url) { VideoCache.stateFlow(url, attachment.size) }
        .collectAsState(initial = VideoCache.stateOf(url, attachment.size))
    var thumbnail by remember(url) { mutableStateOf<ImageBitmap?>(null) }

    // 封面：占位用（取帧数据走视频缓存，只读解码需要的片段），结果落盘复用。
    LaunchedEffect(url) {
        val poster = VideoThumbnail.load(context, url, attachment.size) ?: return@LaunchedEffect
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

    // 缓存完成：直接在聊天里播放。
    if (cacheState is VideoCacheState.Cached) {
        InlineVideoPreview(
            url = url,
            sizeHint = attachment.size,
            aspectRatio = ratio,
            onOpenFullscreen = onOpenViewer,
            onLongClick = onLongClick,
            modifier = modifier,
        )
        return
    }

    // 未缓存：封面占位 + 点击开始缓存（超过 100MB 的视频会自动缓存被跳过，只能这样手动触发）。
    Box(
        modifier = modifier
            .width(200.dp)
            .aspectRatio(ratio)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.35f))
            .combinedClickable(
                onClick = { VideoCache.preCache(url, attachment.size) },
                onLongClick = onLongClick,
            ),
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
            onDownload = { VideoCache.preCache(url, attachment.size) },
            onLongClick = onLongClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp),
        )
    }
}

/**
 * 右上角角标：未缓存=下载按钮+体积，缓存中=进度。
 * 长按和气泡其它区域一致，弹出消息菜单（不是再触发一次下载）。
 */
@Composable
private fun VideoCacheBadge(
    state: VideoCacheState,
    sizeBytes: Long,
    onDownload: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.55f))
            .combinedClickable(onClick = onDownload, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        if (state is VideoCacheState.Downloading) {
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
        } else {
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
 * 同一时刻只允许一个聊天内嵌视频在播：可见区域里最后出现的那个接管播放。
 * 否则一屏多个已缓存视频会同时解码、同时出声（虽然静音，但依然白耗电）。
 */
private object InlineVideoPlayback {

    private val _activeUrl = MutableStateFlow<String?>(null)

    val activeUrl: StateFlow<String?> = _activeUrl.asStateFlow()

    fun activate(url: String) {
        _activeUrl.value = url
    }

    /** 只在自己还是当前播放者时清空，避免把后来者的播放权一起清掉。 */
    fun release(url: String) {
        _activeUrl.compareAndSet(url, null)
    }
}

/**
 * 聊天内嵌播放器：**静音**、无播放控件，只播放已完整缓存的视频；
 * 右上角显示已播放时间，点画面进全屏，切到后台自动暂停，离开组合即释放。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InlineVideoPreview(
    url: String,
    sizeHint: Long,
    aspectRatio: Float,
    onOpenFullscreen: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = context as? LifecycleOwner
    val activeUrl by InlineVideoPlayback.activeUrl.collectAsState()
    var positionMs by remember(url) { mutableLongStateOf(0L) }
    val player = remember(url) {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(VideoCache.mediaSourceFactory(url))
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(url))
                repeatMode = Player.REPEAT_MODE_OFF
                volume = 0f // 聊天里静音播放，点开全屏才出声
                playWhenReady = true
                prepare()
            }
    }
    // 进入组合即申请播放权；被别的视频接管时暂停自己（重新可见时会再拿到播放权）。
    LaunchedEffect(url) { InlineVideoPlayback.activate(url) }
    DisposableEffect(url) {
        onDispose { InlineVideoPlayback.release(url) }
    }
    LaunchedEffect(activeUrl, player) {
        if (activeUrl == url) player.play() else player.pause()
    }
    // 右上角的已播放时间；播完一遍后再确认一次"已缓存"状态。
    LaunchedEffect(player, url) {
        var refreshed = false
        while (true) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            if (!refreshed && player.playbackState == Player.STATE_ENDED) {
                refreshed = true
                VideoCache.refreshState(url, sizeHint)
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
                    // 内嵌播放不带控件：点画面直接全屏，控件留给全屏页。
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
        // 右上角是已播放时间，不再是关闭按钮。
        Text(
            text = formatDuration(positionMs),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
