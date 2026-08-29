package net.paigu.chahua.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.style.TextOverflow
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
 * 长按消息弹出的浮层：更多操作菜单改为横向排列（可横向滚动），
 * 快捷表情栏保持在消息上方，整个浮层位于消息顶部上方。
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
            // 横向操作菜单（位于上方，可滚动）。
            Surface(
                shape = RoundedCornerShape(14.dp),
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .widthIn(max = 360.dp)
                    .onSizeChanged { onMenuMeasured(it) },
            ) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    actions.forEach { action ->
                        ActionCell(action = action)
                    }
                }
            }
            // 快捷表情栏（位于消息正上方）。
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
                            .size(40.dp)
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
        val contentWidth = maxOf(menu.width, bar.width)
        val contentHeight = menu.height + gapPx + bar.height

        layout(contentWidth, contentHeight) {
            menu.place(0, 0)
            bar.place(0, menu.height + gapPx)
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
            .size(40.dp)
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

/** 横向菜单中的单个操作项：图标在上、文字在下，紧凑排列。 */
@Composable
private fun ActionCell(action: MessageActionItem) {
    Column(
        modifier = Modifier
            .clickable { action.onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = action.label,
            modifier = Modifier.size(20.dp),
            tint = if (action.destructive) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Spacer(modifier = Modifier.size(4.dp))
        Text(
            text = action.label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (action.destructive) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

/**
 * 计算弹窗窗口位置：整个浮层位于消息上方，
 * 底部表情栏贴合消息顶部，横向操作菜单在表情栏上方。
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

        // 弹窗水平范围与消息对齐：自己的消息右对齐，收到的消息左对齐。
        val x = if (mine) {
            anchorBounds.right - popupContentSize.width
        } else {
            anchorBounds.left
        }
        // 表情栏底部贴合消息顶部（上方留 4dp）。
        val y = anchorBounds.top - gap - popupContentSize.height

        val minX = sidePadding
        val maxX = (windowSize.width - popupContentSize.width - sidePadding).coerceAtLeast(minX)
        val minY = topPadding
        val maxY = (windowSize.height - popupContentSize.height - bottomPadding).coerceAtLeast(minY)

        return IntOffset(x.coerceIn(minX, maxX), y.coerceIn(minY, maxY))
    }
}
