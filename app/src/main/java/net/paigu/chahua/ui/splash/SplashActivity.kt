package net.paigu.chahua.ui.splash

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import net.paigu.chahua.R
import net.paigu.chahua.core.AppGraph
import net.paigu.chahua.data.AppLocale
import net.paigu.chahua.ui.auth.AuthActivity
import net.paigu.chahua.ui.chat.ChatActivity
import net.paigu.chahua.ui.main.MainActivity
import net.paigu.chahua.ui.theme.ChahuaTheme
import net.paigu.chahua.ui.theme.GreenDark
import net.paigu.chahua.ui.theme.GreenLight
import net.paigu.chahua.ui.theme.Typography
import java.nio.ByteBuffer
import java.util.Base64
import kotlinx.coroutines.launch

/**
 * 进程级启动状态：开屏动画播放过一次后，只要进程还活着（例如用户按返回键退出、
 * 但应用仍驻留后台），再次启动就直接进目标页，不再重播开屏。
 */
private object StartupState {
    @Volatile
    var splashPlayed: Boolean = false
}

/** 启动页：播放入场动画后，按会话状态进入主界面或登录页。 */
class SplashActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase, AppGraph.settings.snapshot().language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 进程仍存活（例如按返回键退出后又从桌面点进来）时跳过开屏，直接进目标页，
        // 少一层画面切换，启动更连贯。
        if (StartupState.splashPlayed) {
            lifecycleScope.launch { navigateToNext() }
            return
        }
        enableEdgeToEdge()
        setContent {
            ChahuaTheme {
                SplashScreen(onFinished = ::navigateToNext)
            }
        }
    }

    private suspend fun navigateToNext() {
        StartupState.splashPlayed = true
        val hasSession = AppGraph.session.current().hasSession
        val permalink = parsePermalink(intent)
        if (hasSession && permalink != null) {
            val (chatId, messageId) = permalink
            val threadId = runCatching {
                AppGraph.api.message(chatId, messageId)
            }.getOrNull()?.replyRootId
            startWithoutTransition(
                Intent(this, ChatActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .putExtra("chat_id", chatId)
                    .putExtra("title", chatId)
                    .putExtra("message_id", messageId)
                    .putExtra("thread_root_id", threadId)
                    .putExtra("reply_count", 0L),
            )
            finish()
            return
        }
        val target = if (hasSession) MainActivity::class.java else AuthActivity::class.java
        startWithoutTransition(
            Intent(this, target).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK,
            ),
        )
        finish()
    }

    /** 启动目标页并关掉系统默认的页面切换动效：直接切过去，不要滑入/缩放。 */
    private fun startWithoutTransition(intent: Intent) {
        startActivity(intent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    /** 解析 /m/{base64url(chatId|messageId)} 格式的消息链接。 */
    private fun parsePermalink(intent: Intent?): Pair<String, String>? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val path = intent.data?.path ?: return null
        if (!path.startsWith("/m/")) return null
        val encoded = path.removePrefix("/m/")
        return runCatching {
            val bytes = Base64.getUrlDecoder().decode(encoded)
            if (bytes.size != 16) return@runCatching null
            val buffer = ByteBuffer.wrap(bytes)
            buffer.long.toString() to buffer.long.toString()
        }.getOrNull()
    }
}

@Composable
private fun SplashScreen(onFinished: suspend () -> Unit) {
    val iconScale = remember { Animatable(0.4f) }
    val iconAlpha = remember { Animatable(0.35f) }
    val haloScale = remember { Animatable(0.5f) }
    val haloAlpha = remember { Animatable(0.35f) }

    LaunchedEffect(Unit) {
        coroutineScope {
            launch {
                iconAlpha.animateTo(1f, tween(durationMillis = 260))
            }
            launch {
                iconScale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
                )
            }
            launch {
                haloAlpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 850, easing = LinearOutSlowInEasing),
                )
            }
            launch {
                haloScale.animateTo(
                    targetValue = 1.7f,
                    animationSpec = tween(durationMillis = 850, easing = LinearOutSlowInEasing),
                )
            }
        }
        onFinished()
    }

    SplashScreenContent(
        iconScale = iconScale.value,
        iconAlpha = iconAlpha.value,
        haloScale = haloScale.value,
        haloAlpha = haloAlpha.value,
    )
}

@Composable
private fun SplashScreenContent(
    iconScale: Float = 1f,
    iconAlpha: Float = 1f,
    haloScale: Float = 1f,
    haloAlpha: Float = 0f,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .graphicsLayer {
                        scaleX = haloScale
                        scaleY = haloScale
                        alpha = haloAlpha
                    }
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        shape = CircleShape,
                    ),
            )
            Icon(
                painter = painterResource(R.mipmap.ic_launcher_monochrome),
                contentDescription = null,
                modifier = Modifier
                    .size(96.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                        alpha = iconAlpha
                    },
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**@Preview(
    name = "Splash 浅色",
    group = "Splash",
    showBackground = true,
    widthDp = 360,
    heightDp = 720,
)
@Composable
private fun SplashScreenPreviewLight() {
    SplashPreviewTheme(darkTheme = false) {
        SplashScreenContent()
    }
}

@Preview(
    name = "Splash 深色",
    group = "Splash",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    widthDp = 360,
    heightDp = 720,
)
@Composable
private fun SplashScreenPreviewDark() {
    SplashPreviewTheme(darkTheme = true) {
        SplashScreenContent()
    }
}

@Preview(
    name = "Splash 动画（交互预览）",
    group = "Splash",
    showBackground = true,
    widthDp = 360,
    heightDp = 720,
)
@Composable
private fun SplashScreenPreviewAnimated() {
    SplashPreviewTheme(darkTheme = false) {
        SplashScreen(onFinished = {})
    }
}

@Composable
private fun SplashPreviewTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) GreenDark else GreenLight,
        typography = Typography,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            content()
        }
    }
}*/
