package net.paigu.chahua.ui.chat

import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.emoji2.emojipicker.EmojiPickerView
import net.paigu.chahua.R

/**
 * “+”打开的更多 emoji 选择弹窗，使用官方 AndroidX Emoji Picker。
 * 采用与用户资料弹窗相同的 AlertDialog 尺寸，居中显示、大小紧凑。
 */
@Composable
fun ReactionEmojiPickerSheet(
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_reaction_pick_more)) },
        text = {
            AndroidView(
                factory = { context ->
                    EmojiPickerView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        emojiGridColumns = 8
                        setOnEmojiPickedListener { emoji ->
                            onSelect(emoji.emoji)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.chat_close))
            }
        },
    )
}
