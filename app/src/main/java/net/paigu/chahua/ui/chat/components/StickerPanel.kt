package net.paigu.chahua.ui.chat.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
                        StickerCell(sticker = sticker, onClick = { onSendSticker(sticker) })
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
                            StickerCell(sticker = sticker, onClick = { onSendSticker(sticker) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StickerCell(
    sticker: StickerSummaryDto,
    onClick: () -> Unit,
) {
    AuthAsyncImage(
        url = sticker.media.url,
        contentDescription = sticker.emoji,
        modifier = Modifier
            .size(72.dp)
            .clickable(onClick = onClick),
        contentScale = ContentScale.Fit,
    )
}
