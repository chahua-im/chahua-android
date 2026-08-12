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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
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
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.paigu.chahua.R
import net.paigu.chahua.core.AppGraph
import net.paigu.chahua.data.AppLocale
import net.paigu.chahua.ui.common.AuthAsyncImage
import net.paigu.chahua.ui.theme.ChahuaTheme
import java.util.Locale

/**
 * 媒体查看：图片支持双指缩放、双击放大；视频用 Media3 ExoPlayer 播放。
 * 沉浸式全屏，视频控件（返回 / 下载 / 进度条 / 播放控制 / 倍速）自动隐藏，点击画面唤出。
 */
class MediaViewerActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase, AppGraph.settings.snapshot().language))
    }

    companion object {
        private const val EXTRA_URL = "media_url"
        private const val EXTRA_KIND = "media_kind"
        private const val EXTRA_FILE_NAME = "media_file_name"

        fun createIntent(context: Context, url: String, kind: String, fileName: String? = null): Intent =
            Intent(context, MediaViewerActivity::class.java)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_KIND, kind)
                .putExtra(EXTRA_FILE_NAME, fileName)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())

        val url = intent.getStringExtra(EXTRA_URL) ?: run {
            finish()
            return
        }
        val kind = intent.getStringExtra(EXTRA_KIND) ?: "image"
        val fileName = intent.getStringExtra(EXTRA_FILE_NAME)
        setContent {
            ChahuaTheme {
                MediaViewerScreen(
                    url = url,
                    kind = kind,
                    fileName = fileName,
                    onBack = { finish() },
                )
            }
        }
    }
}

@Composable
private fun MediaViewerScreen(
    url: String,
    kind: String,
    fileName: String?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }

    fun performSave() {
        if (saving) return
        scope.launch {
            saving = true
            try {
                MediaSaver.downloadToGallery(context, url, kind, fileName)
                Toast.makeText(context, R.string.media_saved, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    context.getString(R.string.media_save_failed, e.message),
                    Toast.LENGTH_SHORT,
                ).show()
            } finally {
                saving = false
            }
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        if (kind.startsWith("video")) {
            ExoVideoPlayer(
                url = url,
                onBack = onBack,
                onSave = ::requestSave,
                saving = saving,
            )
        } else {
            ZoomableImage(url = url, onTap = onBack)
            OverlayIconButton(
                icon = Icons.Filled.Download,
                contentDescription = stringResource(R.string.media_save),
                onClick = ::requestSave,
                showProgress = saving,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(8.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExoVideoPlayer(
    url: String,
    onBack: () -> Unit,
    onSave: () -> Unit,
    saving: Boolean,
) {
    val context = LocalContext.current
    val lifecycleOwner = context as? LifecycleOwner
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
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

    var controlsVisible by remember { mutableStateOf(true) }
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

@Composable
private fun ZoomableImage(url: String, onTap: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 4f)
                    offset = if (scale > 1f) offset + pan else Offset.Zero
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AuthAsyncImage(
            url = url,
            contentDescription = stringResource(R.string.media_title),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
            contentScale = ContentScale.Fit,
        )
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
