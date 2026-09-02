package net.paigu.chahua.ui.chat.components

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt
import net.paigu.chahua.R
import net.paigu.chahua.data.models.StickerSummaryDto
import net.paigu.chahua.ui.chat.ChatViewModel
import net.paigu.chahua.ui.common.AuthAsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StickerPanel(
    viewModel: ChatViewModel,
    onSendSticker: (StickerSummaryDto) -> Unit,
) {
    val state by viewModel.stickerPanel.collectAsState()
    val context = LocalContext.current
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val toggleFavorite: (StickerSummaryDto) -> Unit = { sticker ->
        viewModel.setStickerFavorite(
            stickerId = sticker.id,
            favorite = !sticker.isFavorited,
            sticker = sticker,
            onDone = { toast ->
                Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
            },
            onError = { err ->
                Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
            },
        )
    }

    LaunchedEffect(Unit) {
        viewModel.loadStickerPanel()
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.dismissStickerError()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp)
            .background(MaterialTheme.colorScheme.surface),
    ) {
        val safeIndex = selectedTabIndex.coerceIn(0, state.packs.size)
        SecondaryScrollableTabRow(selectedTabIndex = safeIndex, edgePadding = 8.dp) {
            Tab(
                selected = safeIndex == 0,
                onClick = { selectedTabIndex = 0 },
                text = {
                    Text(
                        stringResource(R.string.chat_emoji_favorites),
                        maxLines = 1,
                    )
                },
            )
            state.packs.forEachIndexed { index, pack ->
                Tab(
                    selected = safeIndex == index + 1,
                    onClick = {
                        selectedTabIndex = index + 1
                        viewModel.selectStickerPack(pack.id)
                    },
                    text = {
                        Text(
                            text = pack.name,
                            maxLines = 1,
                        )
                    },
                )
            }
        }

        if (state.loadingPacks && state.packs.isEmpty() && state.favorites.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else if (safeIndex == 0) {
            if (state.favorites.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.chat_emoji_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    gridItems(state.favorites, key = { it.id }) { sticker ->
                        StickerCell(
                            sticker = sticker,
                            onClick = { onSendSticker(sticker) },
                            onToggleFavorite = { toggleFavorite(sticker) },
                        )
                    }
                }
            }
        } else {
            val pack = state.packs.getOrNull(safeIndex - 1)
            if (pack == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.chat_emoji_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val detail = state.details[pack.id]
                if (detail == null) {
                    LaunchedEffect(pack.id) {
                        viewModel.selectStickerPack(pack.id)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (detail.stickers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.chat_emoji_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        gridItems(detail.stickers, key = { it.id }) { sticker ->
                            StickerCell(
                                sticker = sticker,
                                onClick = { onSendSticker(sticker) },
                                onToggleFavorite = { toggleFavorite(sticker) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 表情面板中的单个表情：
 * - 单击发送；
 * - 长按弹出“添加到收藏 / 从收藏中移除”菜单（根据当前收藏状态展示对应操作）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StickerCell(
    sticker: StickerSummaryDto,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var anchorBounds by remember { mutableStateOf(IntRect.Zero) }
    Box(
        modifier = Modifier.onGloballyPositioned { coordinates ->
            val rect = coordinates.boundsInWindow()
            anchorBounds = IntRect(
                left = rect.left.roundToInt(),
                top = rect.top.roundToInt(),
                right = rect.right.roundToInt(),
                bottom = rect.bottom.roundToInt(),
            )
        },
    ) {
        AuthAsyncImage(
            url = sticker.media.url,
            contentDescription = sticker.emoji,
            modifier = Modifier
                .size(72.dp)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { menuExpanded = true },
                ),
            contentScale = ContentScale.Fit,
        )
    }
    if (menuExpanded && anchorBounds != IntRect.Zero) {
        StickerFavoriteMenu(
            anchorBounds = anchorBounds,
            label = stringResource(
                if (sticker.isFavorited) {
                    R.string.chat_sticker_remove_from_favorites
                } else {
                    R.string.chat_sticker_add_to_favorites
                },
            ),
            icon = if (sticker.isFavorited) {
                Icons.Filled.FavoriteBorder
            } else {
                Icons.Filled.Favorite
            },
            onClick = onToggleFavorite,
            onDismiss = { menuExpanded = false },
        )
    }
}

/** 长按表情后弹出的单项菜单浮层（贴合被长按的表情，靠下优先展示）。 */
@Composable
private fun StickerFavoriteMenu(
    anchorBounds: IntRect,
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    Popup(
        onDismissRequest = onDismiss,
        popupPositionProvider = remember(anchorBounds) {
            StickerFavoriteMenuPositionProvider(density = density, anchorRect = anchorBounds)
        },
        properties = remember { PopupProperties(focusable = true) },
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 4.dp,
            shadowElevation = 8.dp,
        ) {
            Row(
                modifier = Modifier
                    .clickable {
                        onClick()
                        onDismiss()
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** 将收藏菜单浮层定位到被长按表情的下方（空间不足时改为上方）。 */
private class StickerFavoriteMenuPositionProvider(
    private val density: Density,
    private val anchorRect: IntRect,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val gap = with(density) { 4.dp.roundToPx() }
        val margin = with(density) { 8.dp.roundToPx() }
        val x = anchorRect.center.x - popupContentSize.width / 2
        val belowY = anchorRect.bottom + gap
        val fitsBelow = belowY + popupContentSize.height <= windowSize.height - margin
        val y = if (fitsBelow) belowY else anchorRect.top - gap - popupContentSize.height
        val minX = margin
        val maxX = (windowSize.width - popupContentSize.width - margin).coerceAtLeast(minX)
        val minY = margin
        val maxY = (windowSize.height - popupContentSize.height - margin).coerceAtLeast(minY)
        return IntOffset(x.coerceIn(minX, maxX), y.coerceIn(minY, maxY))
    }
}
