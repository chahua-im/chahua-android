package net.paigu.chahua.ui.chat

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.paigu.chahua.R
import net.paigu.chahua.ui.common.AuthAsyncImage

/**
 * 聊天内点击表情包弹出的预览：大图 + 所属贴纸包信息，
 * 底部提供“收藏 / 取消收藏”与“订阅 / 取消订阅”两个按钮。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerPreviewSheet(
    viewModel: ChatViewModel,
    stickerId: String,
    myUid: Int,
    onDismiss: () -> Unit,
    onManagePack: (String) -> Unit = {},
) {
    val state by viewModel.stickerPreview.collectAsState()
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(stickerId) {
        viewModel.loadStickerPreview(stickerId)
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            viewModel.dismissStickerPreview()
            onDismiss()
        },
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val detail = state.detail
            when {
                state.loading && detail == null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                detail == null -> {
                    Text(
                        text = stringResource(R.string.sticker_preview_load_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 48.dp),
                    )
                }
                else -> {
                    AuthAsyncImage(
                        url = detail.media.url,
                        contentDescription = detail.emoji,
                        modifier = Modifier
                            .size(180.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Fit,
                    )
                    if (detail.emoji.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = detail.emoji,
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                    val pack = detail.packs.firstOrNull()
                    Spacer(modifier = Modifier.height(12.dp))
                    if (pack != null) {
                        Text(
                            text = pack.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(
                                R.string.sticker_preview_pack_count,
                                pack.stickerCount,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val isOwner = pack?.ownerUid == myUid
                        OutlinedButton(
                            onClick = { viewModel.toggleStickerFavoriteFromPreview() },
                            enabled = !state.busyFavorite,
                            modifier = Modifier.weight(1f),
                        ) {
                            if (state.busyFavorite) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Text(
                                    stringResource(
                                        if (detail.isFavorited) {
                                            R.string.chat_sticker_unfavorite
                                        } else {
                                            R.string.chat_sticker_favorite
                                        },
                                    ),
                                )
                            }
                        }
                        if (isOwner) {
                            Button(
                                onClick = { onManagePack(pack.id) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.sticker_preview_manage))
                            }
                        } else {
                            Button(
                                onClick = { viewModel.toggleStickerPackSubscriptionFromPreview() },
                                enabled = !state.busySubscribe && pack != null,
                                modifier = Modifier.weight(1f),
                            ) {
                                if (state.busySubscribe) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Text(
                                        stringResource(
                                            if (state.subscribed) {
                                                R.string.sticker_preview_unsubscribe
                                            } else {
                                                R.string.sticker_preview_subscribe
                                            },
                                        ),
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
