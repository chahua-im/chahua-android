package net.paigu.chahua.ui.splash

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import net.paigu.chahua.R
import net.paigu.chahua.core.AppGraph
import net.paigu.chahua.data.AppLocale
import net.paigu.chahua.ui.auth.AuthActivity
import net.paigu.chahua.ui.main.MainActivity
import net.paigu.chahua.ui.theme.ChahuaTheme
import net.paigu.chahua.ui.theme.GreenDark
import net.paigu.chahua.ui.theme.GreenLight
import net.paigu.chahua.ui.theme.Typography

/** 启动页：播放入场动画后，按会话状态进入主界面或登录页。 */
class SplashActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase, AppGraph.settings.snapshot().language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChahuaTheme {
                SplashScreen(onFinished = ::navigateToNext)
            }
        }
    }

    private suspend fun navigateToNext() {
        val hasSession = AppGraph.session.current().hasSession
        val target = if (hasSession) MainActivity::class.java else AuthActivity::class.java
        startActivity(
            Intent(this, target).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK,
            ),
        )
        finish()
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
