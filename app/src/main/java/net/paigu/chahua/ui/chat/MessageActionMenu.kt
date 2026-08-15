package net.paigu.chahua.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import net.paigu.chahua.R

/** 长按消息后展示的操作项。 */
data class MessageActionItem(
    val label: String,
    val icon: ImageVector,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * 长按消息弹出的浮层：操作菜单固定在消息靠中心一侧、菜单底部与消息底部对齐；
 * 快捷表情栏与菜单分离，独立显示在消息正下方，最右侧“+”可打开更多 emoji 选择。
 * 菜单与表情栏位于同一个弹窗窗口内，通过绝对定位放置，避免多个弹窗互相抢点击。
 */
@Composable
fun MessageActionMenu(
    expanded: Boolean,
    mine: Boolean,
    anchorBounds: IntRect,
    quickReactionEmojis: List<String>,
    onQuickReact: (String) -> Unit,
    onOpenEmojiPicker: () -> Unit,
    actions: List<MessageActionItem>,
    onDismiss: () -> Unit,
) {
    if (!expanded) return
    val density = LocalDensity.current
    var menuSize by remember { mutableStateOf(IntSize.Zero) }
    var barSize by remember { mutableStateOf(IntSize.Zero) }

    Popup(
        onDismissRequest = onDismiss,
        popupPositionProvider = remember(mine, density, anchorBounds, menuSize, barSize) {
            ReactionOverlayPositionProvider(
                mine = mine,
                density = density,
                menuSize = menuSize,
                barSize = barSize,
            )
        },
        properties = remember { PopupProperties(focusable = true) },
    ) {
        ReactionOverlayContent(
            mine = mine,
            anchorBounds = anchorBounds,
            quickReactionEmojis = quickReactionEmojis,
            onQuickReact = onQuickReact,
            onOpenEmojiPicker = onOpenEmojiPicker,
            actions = actions,
            onMenuMeasured = { menuSize = it },
            onBarMeasured = { barSize = it },
        )
    }
}

@Composable
private fun ReactionOverlayContent(
    mine: Boolean,
    anchorBounds: IntRect,
    quickReactionEmojis: List<String>,
    onQuickReact: (String) -> Unit,
    onOpenEmojiPicker: () -> Unit,
    actions: List<MessageActionItem>,
    onMenuMeasured: (IntSize) -> Unit,
    onBarMeasured: (IntSize) -> Unit,
) {
    val density = LocalDensity.current
    val gapPx = with(density) { 4.dp.roundToPx() }

    Layout(
        content = {
            Surface(
                shape = RoundedCornerShape(14.dp),
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .onSizeChanged { onMenuMeasured(it) },
            ) {
                Column {
                    actions.forEach { action ->
                        ActionRow(action = action)
                    }
                }
            }
            Surface(
                shape = RoundedCornerShape(18.dp),
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.onSizeChanged { onBarMeasured(it) },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    quickReactionEmojis.forEach { emoji ->
                        QuickReactionButton(
                            emoji = emoji,
                            onClick = { onQuickReact(emoji) },
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable(onClick = onOpenEmojiPicker),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.chat_reaction_more),
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val menu = measurables[0].measure(constraints)
        val bar = measurables[1].measure(constraints)

        // 菜单靠中心一侧：自己的消息在气泡左侧，收到的消息在气泡右侧。
        val menuX = if (mine) {
            anchorBounds.left - gapPx - menu.width
        } else {
            anchorBounds.right + gapPx
        }
        // 表情栏贴合消息正下方：与气泡左/右边缘对齐。
        val barX = if (mine) {
            anchorBounds.right - bar.width
        } else {
            anchorBounds.left
        }
        val contentLeft = minOf(menuX, barX)
        val contentRight = maxOf(menuX + menu.width, barX + bar.width)
        val contentWidth = (contentRight - contentLeft).coerceAtLeast(0)
        val contentHeight = menu.height + gapPx + bar.height

        layout(contentWidth, contentHeight) {
            menu.place(menuX - contentLeft, 0)
            bar.place(barX - contentLeft, menu.height + gapPx)
        }
    }
}

@Composable
private fun QuickReactionButton(
    emoji: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = emoji,
            fontSize = 20.sp,
        )
    }
}

@Composable
private fun ActionRow(action: MessageActionItem) {
    Row(
        modifier = Modifier
            .clickable { action.onClick() }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (action.destructive) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = action.label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (action.destructive) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

/**
 * 计算弹窗窗口位置：菜单底部与消息底部对齐、靠中心一侧；
 * 弹窗内容中菜单在上、表情栏在菜单下方，因此表情栏恰好位于消息正下方。
 */
private class ReactionOverlayPositionProvider(
    private val mine: Boolean,
    private val density: Density,
    private val menuSize: IntSize,
    private val barSize: IntSize,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val gap = with(density) { 4.dp.roundToPx() }
        val sidePadding = with(density) { 12.dp.roundToPx() }
        val topPadding = with(density) { 12.dp.roundToPx() }
        val bottomPadding = with(density) { 24.dp.roundToPx() }

        val menuX = if (mine) {
            anchorBounds.left - gap - menuSize.width
        } else {
            anchorBounds.right + gap
        }
        val barX = if (mine) {
            anchorBounds.right - barSize.width
        } else {
            anchorBounds.left
        }
        val x = minOf(menuX, barX)
        // 菜单底部与消息底部对齐
        val y = anchorBounds.bottom - menuSize.height

        val minX = sidePadding
        val maxX = (windowSize.width - popupContentSize.width - sidePadding).coerceAtLeast(minX)
        val minY = topPadding
        val maxY = (windowSize.height - popupContentSize.height - bottomPadding).coerceAtLeast(minY)

        return IntOffset(x.coerceIn(minX, maxX), y.coerceIn(minY, maxY))
    }
}
