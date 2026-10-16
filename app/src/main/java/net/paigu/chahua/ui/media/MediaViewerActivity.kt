package net.paigu.chahua.ui.media

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlin.math.abs
import kotlin.math.min
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import net.paigu.chahua.R
import net.paigu.chahua.core.AppGraph
import net.paigu.chahua.data.AppLocale
import net.paigu.chahua.data.ApiJson
import net.paigu.chahua.data.VideoCache
import net.paigu.chahua.ui.common.AuthAsyncImage
import net.paigu.chahua.ui.theme.ChahuaTheme
import java.util.Locale

@Serializable
data class MediaViewerItem(
    val url: String,
    val kind: String,
    val fileName: String? = null,
)

/**
 * 媒体查看：图片支持双指缩放、双击放大，左右滑动切换消息中的上一张/下一张图；
 * 视频用 Media3 ExoPlayer 播放。
 * 沉浸式全屏，视频控件（返回 / 下载 / 进度条 / 播放控制 / 倍速）自动隐藏，点击画面唤出。
 */
class MediaViewerActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase, AppGraph.settings.snapshot().language))
    }

    companion object {
        private const val EXTRA_ITEMS = "media_items"
        private const val EXTRA_INDEX = "media_index"
        private const val EXTRA_URL = "media_url"
        private const val EXTRA_KIND = "media_kind"
        private const val EXTRA_FILE_NAME = "media_file_name"
        private const val EXTRA_HIDE_CONTROLS = "media_hide_controls"

        /** 打开一组媒体（同一消息的多张图），并从 [index] 开始浏览。 */
        fun createIntent(
            context: Context,
            items: List<MediaViewerItem>,
            index: Int,
            hideControls: Boolean = false,
        ): Intent =
            Intent(context, MediaViewerActivity::class.java)
                .putExtra(
                    EXTRA_ITEMS,
                    ApiJson.instance.encodeToString(
                        ListSerializer(MediaViewerItem.serializer()),
                        items,
                    ),
                )
                .putExtra(EXTRA_INDEX, index)
                .putExtra(EXTRA_HIDE_CONTROLS, hideControls)

        /** 打开单个媒体（视频 / 群资料附件等，无滑动切换）。 */
        fun createIntent(context: Context, url: String, kind: String, fileName: String? = null): Intent =
            createIntent(context, listOf(MediaViewerItem(url, kind, fileName)), 0)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())

        val itemsJson = intent.getStringExtra(EXTRA_ITEMS)
        val decodedItems = itemsJson?.let { raw ->
            runCatching {
                ApiJson.instance.decodeFromString(
                    ListSerializer(MediaViewerItem.serializer()),
                    raw,
                )
            }.getOrNull()
        }.orEmpty()
        val items = if (decodedItems.isNotEmpty()) {
            decodedItems
        } else {
            val url = intent.getStringExtra(EXTRA_URL) ?: run {
                finish()
                return
            }
            listOf(
                MediaViewerItem(
                    url = url,
                    kind = intent.getStringExtra(EXTRA_KIND) ?: "image",
                    fileName = intent.getStringExtra(EXTRA_FILE_NAME),
                ),
            )
        }
        val initialIndex = intent.getIntExtra(EXTRA_INDEX, 0).coerceIn(0, items.lastIndex)
        // 从聊天里的视频直接进来时：不显示控件，直接全屏播放。
        val hideControls = intent.getBooleanExtra(EXTRA_HIDE_CONTROLS, false)
        setContent {
            ChahuaTheme {
                MediaViewerScreen(
                    items = items,
                    initialIndex = initialIndex,
                    hideControls = hideControls,
                    onBack = { finish() },
                )
            }
        }
    }
}

@Composable
private fun MediaViewerScreen(
    items: List<MediaViewerItem>,
    initialIndex: Int,
    onBack: () -> Unit,
    hideControls: Boolean = false,
) {
    val context = LocalContext.current
    val savingUrls by MediaSaver.saving.collectAsState()
    var showSaveScopeDialog by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(initialPage = initialIndex) { items.size }

    fun currentItem(): MediaViewerItem? = items.getOrNull(pagerState.currentPage)

    /** 交给应用级作用域执行：保存过程中退出界面 / Activity 也不会中断。 */
    fun saveItems(requests: List<MediaViewerItem>) {
        if (requests.isEmpty()) return
        MediaSaver.saveToGalleryInBackground(
            context = context,
            requests = requests.map {
                MediaSaveRequest(url = it.url, kind = it.kind, fileName = it.fileName)
            },
            onResult = { saved, failed, firstError ->
                val message = when {
                    failed == 0 && saved == 1 -> context.getString(R.string.media_saved)
                    failed == 0 -> context.getString(R.string.media_saved_count, saved)
                    saved == 0 -> context.getString(R.string.media_save_failed, firstError.orEmpty())
                    else -> context.getString(R.string.media_saved_partial, saved, failed)
                }
                Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
            },
        )
    }

    fun performSave() {
        val item = currentItem() ?: return
        // 这条消息里有多个图片/视频时，先问是只存当前还是全部存。
        if (items.size > 1) {
            showSaveScopeDialog = true
        } else {
            saveItems(listOf(item))
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            performSave()
        } else {
            Toast.makeText(context, R.string.media_save_permission_denied, Toast.LENGTH_SHORT).show()
        }
    }

    fun requestSave() {
        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            performSave()
        }
    }

    // 图片控件栏：单击图片切换显隐（单击不再直接退出，避免和双击抢事件造成点击延迟），
    // 显示 3 秒后自动淡出，与视频控件保持一致。
    val currentPageItem = items.getOrNull(pagerState.currentPage)
    val currentIsImage = currentPageItem != null && !currentPageItem.kind.startsWith("video")
    val currentSaving = currentPageItem?.url?.let { savingUrls.contains(it) } == true
    var imageControlsVisible by remember { mutableStateOf(true) }
    var currentPageZoomed by remember { mutableStateOf(false) }

    // 翻页后清掉放大标记，否则会把翻页手势一直锁住。
    LaunchedEffect(pagerState.currentPage) {
        currentPageZoomed = false
    }
    LaunchedEffect(imageControlsVisible, currentIsImage, pagerState.currentPage) {
        if (imageControlsVisible && currentIsImage) {
            delay(IMAGE_CONTROLS_AUTO_HIDE_MS)
            imageControlsVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            // 放大状态下先专心拖图片，避免和左右翻页互相抢手势。
            userScrollEnabled = !currentPageZoomed,
        ) { page ->
            val item = items[page]
            val saving = item.url in savingUrls
            if (item.kind.startsWith("video")) {
                ExoVideoPlayer(
                    url = item.url,
                    onBack = onBack,
                    onSave = ::requestSave,
                    saving = saving,
                    hideControlsInitially = hideControls,
                )
            } else {
                ZoomableImage(
                    url = item.url,
                    onToggleControls = { imageControlsVisible = !imageControlsVisible },
                    onClose = onBack,
                    onZoomedChange = { currentPageZoomed = it },
                )
            }
        }
        // 视频页沿用原来的常驻页码；图片页的页码跟控件栏一起显示 / 隐藏。
        if (!currentIsImage && items.size > 1) {
            PageIndicator(
                text = "${pagerState.currentPage + 1} / ${items.size}",
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 8.dp),
            )
        }
        AnimatedVisibility(
            visible = currentIsImage && imageControlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OverlayIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.chat_back),
                    onClick = onBack,
                )
                Spacer(modifier = Modifier.weight(1f))
                if (items.size > 1) {
                    PageIndicator(text = "${pagerState.currentPage + 1} / ${items.size}")
                }
                Spacer(modifier = Modifier.weight(1f))
                OverlayIconButton(
                    icon = Icons.Filled.Download,
                    contentDescription = stringResource(R.string.media_save),
                    onClick = ::requestSave,
                    showProgress = currentSaving,
                )
            }
        }
    }

    // 一条消息里有多张图片/视频时，保存前先问范围。
    if (showSaveScopeDialog) {
        AlertDialog(
            onDismissRequest = { showSaveScopeDialog = false },
            title = { Text(stringResource(R.string.media_save_scope_title)) },
            text = { Text(stringResource(R.string.media_save_scope_message, items.size)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSaveScopeDialog = false
                        currentItem()?.let { saveItems(listOf(it)) }
                    },
                ) {
                    Text(stringResource(R.string.media_save_current))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSaveScopeDialog = false
                        saveItems(items)
                    },
                ) {
                    Text(stringResource(R.string.media_save_all, items.size))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExoVideoPlayer(
    url: String,
    onBack: () -> Unit,
    onSave: () -> Unit,
    saving: Boolean,
    hideControlsInitially: Boolean = false,
) {
    val context = LocalContext.current
    val lifecycleOwner = context as? LifecycleOwner
    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            // 走视频缓存：已缓存的直接本地播放，未缓存的边播边写。
            .setMediaSourceFactory(VideoCache.mediaSourceFactory(url))
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(url))
                repeatMode = Player.REPEAT_MODE_ONE
                prepare()
                playWhenReady = true
            }
    }
    DisposableEffect(exoPlayer, lifecycleOwner) {
        val observer = object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                exoPlayer.pause()
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose {
            lifecycleOwner?.lifecycle?.removeObserver(observer)
            exoPlayer.release()
        }
    }

    var controlsVisible by remember { mutableStateOf(!hideControlsInitially) }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var speed by remember { mutableFloatStateOf(1f) }
    var speedMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(exoPlayer) {
        while (true) {
            isPlaying = exoPlayer.isPlaying
            durationMs = exoPlayer.duration.coerceAtLeast(0L)
            positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            delay(500)
        }
    }

    LaunchedEffect(isPlaying, controlsVisible, speedMenuExpanded) {
        if (isPlaying && controlsVisible && !speedMenuExpanded) {
            delay(3000)
            controlsVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { controlsVisible = !controlsVisible },
    ) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    player = exoPlayer
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
            ) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OverlayIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.chat_back),
                        onClick = onBack,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    OverlayIconButton(
                        icon = Icons.Filled.Download,
                        contentDescription = stringResource(R.string.media_save),
                        onClick = onSave,
                        showProgress = saving,
                    )
                }

                Column(
                    modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = formatDuration(positionMs),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                        )
                        val sliderColors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                        )
                        Slider(
                            value = positionMs.coerceIn(0L, durationMs.coerceAtLeast(0L)).toFloat(),
                            onValueChange = { value ->
                                positionMs = value.toLong()
                                exoPlayer.seekTo(value.toLong())
                            },
                            valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp),
                            colors = sliderColors,
                            track = { sliderState ->
                                ThinVideoTrack(sliderState = sliderState)
                            },
                        )
                        Text(
                            text = formatDuration(durationMs),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    ) {
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TransportButton(
                                icon = Icons.Filled.FastRewind,
                                label = "5s",
                                contentDescription = stringResource(R.string.media_rewind_5),
                                onClick = {
                                    exoPlayer.seekTo(
                                        (exoPlayer.currentPosition - 5000).coerceAtLeast(0L),
                                    )
                                },
                            )

                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .clickable {
                                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = if (exoPlayer.isPlaying) {
                                        Icons.Filled.Pause
                                    } else {
                                        Icons.Filled.PlayArrow
                                    },
                                    contentDescription = stringResource(
                                        if (exoPlayer.isPlaying) R.string.media_pause else R.string.media_play,
                                    ),
                                    tint = Color.Black,
                                    modifier = Modifier.size(32.dp),
                                )
                            }

                            TransportButton(
                                icon = Icons.Filled.FastForward,
                                label = "10s",
                                contentDescription = stringResource(R.string.media_forward_10),
                                onClick = {
                                    val duration = exoPlayer.duration.coerceAtLeast(exoPlayer.currentPosition)
                                    exoPlayer.seekTo(
                                        (exoPlayer.currentPosition + 10000).coerceAtMost(duration),
                                    )
                                },
                            )
                        }

                        Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                            val density = LocalDensity.current
                            SpeedButton(
                                speed = speed,
                                onClick = { speedMenuExpanded = true },
                            )
                            if (speedMenuExpanded) {
                                Popup(
                                    alignment = Alignment.BottomCenter,
                                    offset = with(density) {
                                        IntOffset(0, (-8).dp.roundToPx())
                                    },
                                    onDismissRequest = { speedMenuExpanded = false },
                                ) {
                                    SpeedMenu(
                                        currentSpeed = speed,
                                        onSelect = { selected ->
                                            speed = selected
                                            exoPlayer.setPlaybackSpeed(selected)
                                            speedMenuExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlayIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showProgress: Boolean = false,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.5f)),
    ) {
        IconButton(onClick = onClick) {
            if (showProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = Color.White,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThinVideoTrack(sliderState: SliderState) {
    val fraction = if (sliderState.valueRange.endInclusive > sliderState.valueRange.start) {
        ((sliderState.value - sliderState.valueRange.start) /
            (sliderState.valueRange.endInclusive - sliderState.valueRange.start)).coerceIn(0f, 1f)
    } else {
        0f
    }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp),
    ) {
        val strokeWidth = 2.dp.toPx()
        val y = size.height / 2f
        drawLine(
            color = Color.White.copy(alpha = 0.3f),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color.White,
            start = Offset(0f, y),
            end = Offset(size.width * fraction, y),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun TransportButton(
    icon: ImageVector,
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(28.dp),
        )
        Text(
            text = label,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun SpeedButton(
    speed: Float,
    onClick: () -> Unit,
) {
    val label = if (speed == speed.toInt().toFloat()) {
        "${speed.toInt()}x"
    } else {
        "${speed}x"
    }
    Text(
        text = label,
        color = Color.White,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

private val speedOptions = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

@Composable
private fun SpeedMenu(
    currentSpeed: Float,
    onSelect: (Float) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xF21C1C1C),
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            speedOptions.forEach { option ->
                val selected = option == currentSpeed
                val label = if (option == option.toInt().toFloat()) {
                    "${option.toInt()}x"
                } else {
                    "${option}x"
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) Color(0xFF64B5F6) else Color.White,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(option) }
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/** 页码胶囊：图片页跟控件栏一起显隐，视频页常驻。 */
@Composable
private fun PageIndicator(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Color.White,
        style = MaterialTheme.typography.labelLarge,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.4f))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

// ---------------------------------------------------------------------------
// 图片查看的手感参数
// ---------------------------------------------------------------------------

/** 捏合能达到的最大缩放倍数。 */
private const val MAX_IMAGE_SCALE = 5f

/** 双击一次放大到的倍数。 */
private const val DOUBLE_TAP_SCALE = 2.5f

/** 低于这个倍数就算"没放大"：双击还原、下拉退出、边界夹取都按它判断。 */
private const val ZOOM_EPSILON = 1.05f

/** 图片控件栏自动隐藏延时，与视频控件保持一致。 */
private const val IMAGE_CONTROLS_AUTO_HIDE_MS = 3000L

/** 下拉超过视口高度的这个比例就关闭查看器。 */
private const val DISMISS_THRESHOLD_FRACTION = 0.22f

/** 触发下拉所需的最小位移，避免轻点被误判成下拉。 */
private const val DISMISS_MIN_DRAG_DP = 24f

/** 下拉甩动速度阈值（px/s）：够快时不必拉满距离也能关闭。 */
private const val DISMISS_FLING_PX_PER_SEC = 1800f

/** 松手后按速度滑行的时长系数，做出"甩一下"的惯性。 */
private const val PAN_GLIDE_FACTOR = 0.12f

/** 低于这个速度就不滑行，否则松手会显得发飘。 */
private const val PAN_FLING_MIN_PX_PER_SEC = 500f

/** 图片手势当前处于哪种模式。 */
private enum class ImageGesture { UNDECIDED, PAN, TRANSFORM, DISMISS, PAGER }

/**
 * 可缩放的图片查看：
 * - 双指捏合以双指中心为焦点缩放；双击以双击点为焦点放大 / 还原，均带弹簧动画；
 * - 平移始终夹在图片边界内，缩放变化后不会把图片甩出屏幕；
 * - 未放大时下拉可关闭（图片跟手位移 + 渐隐 + 缩小），松手不过阈值则弹回；
 * - 未放大时单击滑动一律不消费，交给外层 HorizontalPager 翻页；
 * - 单击只切换控件栏（由调用方渲染），退出走返回按钮 / 系统返回 / 下拉。
 *
 * 点击检测与缩放平移检测是两个 pointerInput，但分工明确：前者只在原地点击时消费事件，
 * 后者只在进入拖动 / 缩放后消费，因此不会互相抢手势。
 */
@Composable
private fun ZoomableImage(
    url: String,
    onToggleControls: () -> Unit,
    onClose: () -> Unit,
    onZoomedChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var dismissY by remember { mutableFloatStateOf(0f) }
    // 正在播放的缩放 / 回弹动画。手势接管前先取消，避免动画和手指抢同一个值。
    val runningAnimation = remember { mutableStateOf<Job?>(null) }
    var intrinsic by remember { mutableStateOf(Size.Zero) }
    var container by remember { mutableStateOf(IntSize.Zero) }
    var loading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var retryKey by remember { mutableIntStateOf(0) }

    fun cancelAnimation() {
        runningAnimation.value?.cancel()
        runningAnimation.value = null
    }

    fun notifyZoomed() {
        onZoomedChange(scale > ZOOM_EPSILON)
    }

    /**
     * 用 Animatable 驱动一次数值动画。
     * 手势的 [awaitEachGesture] 是 `@RestrictsSuspension` 作用域，里面不能调用挂起函数，
     * 所以所有动画都放在这里、由 [runningAnimation] 统一取消。
     */
    suspend fun animateFloat(
        start: Float,
        end: Float,
        spec: AnimationSpec<Float>,
        apply: (Float) -> Unit,
    ) {
        if (start == end) return
        Animatable(start).animateTo(end, spec) { apply(value) }
    }

    suspend fun animateOffset(
        start: Offset,
        end: Offset,
        spec: AnimationSpec<Offset>,
        apply: (Offset) -> Unit,
    ) {
        if (start == end) return
        Animatable(start, Offset.VectorConverter).animateTo(end, spec) { apply(value) }
    }

    /** 同时把缩放与偏移动画到目标值，让动画过程中焦点不漂。 */
    fun animateTo(targetScale: Float, targetOffset: Offset) {
        cancelAnimation()
        runningAnimation.value = scope.launch {
            coroutineScope {
                launch {
                    animateFloat(
                        scale,
                        targetScale,
                        spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow),
                    ) { scale = it }
                }
                launch {
                    animateOffset(
                        offset,
                        targetOffset,
                        spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow),
                    ) { offset = it }
                }
            }
            notifyZoomed()
        }
    }

    /** ContentScale.Fit 之后图片在屏幕上的显示尺寸（未缩放时用于算边界）。 */
    fun fittedSize(): Size {
        val image = intrinsic
        val box = container
        if (!image.width.isFinite() || !image.height.isFinite() ||
            image.width <= 0f || image.height <= 0f ||
            box.width <= 0 || box.height <= 0
        ) {
            return Size.Zero
        }
        val factor = min(box.width / image.width, box.height / image.height)
        return Size(image.width * factor, image.height * factor)
    }

    /** 当前倍数下允许的最大平移量：图片边缘不允许滑进视口内。 */
    fun panLimit(currentScale: Float): Offset {
        val fitted = fittedSize()
        if (fitted == Size.Zero) return Offset.Zero
        return Offset(
            ((fitted.width * currentScale - container.width) / 2f).coerceAtLeast(0f),
            ((fitted.height * currentScale - container.height) / 2f).coerceAtLeast(0f),
        )
    }

    fun clampOffset(value: Offset, currentScale: Float): Offset {
        val limit = panLimit(currentScale)
        return Offset(
            value.x.coerceIn(-limit.x, limit.x),
            value.y.coerceIn(-limit.y, limit.y),
        )
    }

    /**
     * 以 [focal]（相对视口中心）为焦点缩放 [factor] 倍，并叠加手指平移 [pan]：
     * 焦点下的内容保持不动，再夹回边界，避免图片被拖出屏幕。
     */
    fun zoomAround(focal: Offset, pan: Offset, factor: Float): Pair<Float, Offset> {
        val oldScale = scale
        val newScale = (oldScale * factor).coerceIn(1f, MAX_IMAGE_SCALE)
        if (newScale == oldScale && pan == Offset.Zero) return oldScale to offset
        val contentPoint = (focal - offset) / oldScale
        return newScale to clampOffset(focal + pan - contentPoint * newScale, newScale)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { container = it }
            .pointerInput(url) {
                detectTapGestures(
                    onTap = { if (!loadFailed) onToggleControls() },
                    onDoubleTap = { tap ->
                        val target = if (scale > ZOOM_EPSILON) 1f else DOUBLE_TAP_SCALE
                        val focal = tap - Offset(size.width / 2f, size.height / 2f)
                        val contentPoint = (focal - offset) / scale
                        val targetOffset = if (target == 1f) {
                            Offset.Zero
                        } else {
                            clampOffset(focal - contentPoint * target, target)
                        }
                        animateTo(target, targetOffset)
                    },
                )
            }
            .pointerInput(url) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val touchSlop = viewConfiguration.touchSlop
                    var mode = ImageGesture.UNDECIDED
                    var traveled = Offset.Zero
                    var lastPosition = down.position
                    var velocity = Offset.Zero

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break

                        if (pressed.size > 1) {
                            // 双指：缩放 + 平移，焦点跟随双指中心。
                            if (mode != ImageGesture.TRANSFORM) {
                                mode = ImageGesture.TRANSFORM
                                cancelAnimation()
                            }
                            val factor = event.calculateZoom()
                            val pan = event.calculatePan()
                            val centroid = event.calculateCentroid(useCurrent = true)
                            val focal = centroid - Offset(size.width / 2f, size.height / 2f)
                            val (newScale, newOffset) = zoomAround(focal, pan, factor)
                            scale = newScale
                            offset = newOffset
                            event.changes.forEach { it.consume() }
                            lastPosition = centroid
                            continue
                        }

                        val change = pressed.first()
                        val delta = change.position - change.previousPosition
                        val elapsed = (change.uptimeMillis - down.uptimeMillis).coerceAtLeast(1L)
                        velocity = (change.position - lastPosition) / elapsed.toFloat() * 1000f
                        lastPosition = change.position
                        traveled += delta

                        if (mode == ImageGesture.UNDECIDED) {
                            // 方向锁：放大后一律平移；未放大时向下拉才进入下拉关闭，
                            // 横向滑动直接放行给 HorizontalPager。
                            mode = when {
                                scale > ZOOM_EPSILON -> ImageGesture.PAN
                                traveled.y > touchSlop && abs(traveled.y) > abs(traveled.x) ->
                                    ImageGesture.DISMISS
                                abs(traveled.x) > touchSlop || abs(traveled.y) > touchSlop ->
                                    ImageGesture.PAGER
                                else -> ImageGesture.UNDECIDED
                            }
                            if (mode != ImageGesture.UNDECIDED && mode != ImageGesture.PAGER) {
                                cancelAnimation()
                            }
                        }

                        when (mode) {
                            ImageGesture.PAN, ImageGesture.TRANSFORM -> {
                                offset = clampOffset(offset + delta, scale)
                                change.consume()
                            }
                            ImageGesture.DISMISS -> {
                                dismissY = (dismissY + delta.y).coerceAtLeast(0f)
                                change.consume()
                            }
                            else -> Unit
                        }
                    }

                    when (mode) {
                        ImageGesture.DISMISS -> {
                            val height = size.height.coerceAtLeast(1)
                            val pulled = dismissY
                            val flung = velocity.y > DISMISS_FLING_PX_PER_SEC &&
                                pulled > DISMISS_MIN_DRAG_DP.dp.toPx()
                            cancelAnimation()
                            runningAnimation.value = scope.launch {
                                if (pulled > height * DISMISS_THRESHOLD_FRACTION || flung) {
                                    animateFloat(
                                        dismissY,
                                        height * 1.2f,
                                        tween(durationMillis = 160),
                                    ) { dismissY = it }
                                    onClose()
                                } else {
                                    animateFloat(
                                        dismissY,
                                        0f,
                                        spring(
                                            dampingRatio = 1f,
                                            stiffness = Spring.StiffnessMediumLow,
                                        ),
                                    ) { dismissY = it }
                                }
                            }
                        }
                        ImageGesture.PAN, ImageGesture.TRANSFORM -> {
                            // 捏合到 1 倍以下时收回 1 倍；松手够快则按速度滑行一小段，做出惯性。
                            val targetScale = if (scale < ZOOM_EPSILON) 1f else scale
                            val glide = if (
                                targetScale > 1f &&
                                velocity.getDistance() > PAN_FLING_MIN_PX_PER_SEC
                            ) {
                                clampOffset(offset + velocity * PAN_GLIDE_FACTOR, targetScale)
                            } else {
                                clampOffset(offset, targetScale)
                            }
                            animateTo(targetScale, glide)
                        }
                        else -> Unit
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = if (retryKey == 0) {
                url
            } else {
                // 重试：绕开内存缓存里缓存的失败结果，重新拉一次。
                ImageRequest.Builder(context)
                    .data(url)
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .build()
            },
            contentDescription = stringResource(R.string.media_title),
            imageLoader = AppGraph.imageLoader,
            contentScale = ContentScale.Fit,
            onLoading = {
                loading = true
                loadFailed = false
            },
            onSuccess = { state ->
                loading = false
                loadFailed = false
                val imageSize = state.painter.intrinsicSize
                if (imageSize.width.isFinite() && imageSize.height.isFinite()) {
                    intrinsic = imageSize
                }
            },
            onError = {
                loading = false
                loadFailed = true
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // 下拉进度：跟手位移 + 轻微缩小 + 渐隐，松手越界则滑出屏幕。
                    val pull = (dismissY /
                        (container.height * DISMISS_THRESHOLD_FRACTION).coerceAtLeast(1f))
                        .coerceIn(0f, 1f)
                    val zoom = scale * (1f - 0.16f * pull)
                    scaleX = zoom
                    scaleY = zoom
                    translationX = offset.x
                    translationY = offset.y + dismissY
                    alpha = 1f - 0.55f * pull
                },
        )
        if (loading) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier.size(36.dp),
            )
        }
        if (loadFailed) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.media_load_failed),
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(
                    onClick = {
                        loadFailed = false
                        loading = true
                        retryKey++
                    },
                ) {
                    Text(text = stringResource(R.string.retry), color = Color.White)
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}
