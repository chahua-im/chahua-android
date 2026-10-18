package net.paigu.chahua.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 回退双栏的固定左栏比例，与 Activity Embedding 的默认比例保持一致。 */
private const val DEFAULT_LEFT_RATIO = 0.3f

/**
 * 不支持 Activity Embedding 时使用的宽屏双栏骨架：
 * 左栏内容 + 底部导航，右栏内容独占剩余空间，分割线固定不可拖动。
 */
@Composable
internal fun WideFallbackFrame(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    leftContent: @Composable () -> Unit,
    rightContent: @Composable () -> Unit,
) {
    // 双栏骨架自己铺一层主题背景：否则右栏没有内容时露出的会是窗口背景（按系统夜间模式取色，
    // 和应用内深浅色设置不一致时会突然出现一整块反色）。
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(DEFAULT_LEFT_RATIO)
                .fillMaxHeight(),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                leftContent()
            }
            MainBottomBar(
                selectedTab = selectedTab,
                onSelectTab = onSelectTab,
            )
        }
        VerticalDivider(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            rightContent()
        }
    }
}
