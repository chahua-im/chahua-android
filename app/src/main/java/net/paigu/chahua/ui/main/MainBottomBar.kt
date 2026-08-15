package net.paigu.chahua.ui.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import net.paigu.chahua.R

/**
 * 底部导航（聊天 / 设置），在 Activity Embedding 中位于左侧聊天列表底部。
 * 使用 NavigationBar 默认的 windowInsets：底部内边距由导航栏内部消费，
 * 导航栏背景延伸到三键导航 / 手势条后面，内容不会被系统导航栏遮挡。
 */
@Composable
internal fun MainBottomBar(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
) {
    NavigationBar {
        NavigationBarItem(
            selected = selectedTab == 0,
            onClick = { onSelectTab(0) },
            icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null) },
            label = { Text(stringResource(R.string.tab_chats)) },
        )
        NavigationBarItem(
            selected = selectedTab == 1,
            onClick = { onSelectTab(1) },
            icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
            label = { Text(stringResource(R.string.tab_settings)) },
        )
    }
}
