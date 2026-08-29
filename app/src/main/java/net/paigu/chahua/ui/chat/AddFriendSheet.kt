package net.paigu.chahua.ui.chat

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.paigu.chahua.R
import net.paigu.chahua.data.models.FriendAddInfoResponse
import net.paigu.chahua.data.models.UserDto

private sealed interface AddFriendLoadState {
    data object Loading : AddFriendLoadState
    data class Error(val message: String) : AddFriendLoadState
    data class Ready(val info: FriendAddInfoResponse) : AddFriendLoadState
}

/**
 * 添加好友弹窗：按目标用户的好友验证设置展示对应输入
 * （direct 直接发送 / need_message 验证消息 / question 回答问题 / forbid 拒绝请求）。
 */
@Composable
fun AddFriendSheet(
    user: UserDto,
    viewModel: ChatViewModel,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<AddFriendLoadState>(AddFriendLoadState.Loading) }
    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    LaunchedEffect(user.uid) {
        state = AddFriendLoadState.Loading
        text = ""
        sending = false
        state = viewModel.friendAddInfo(user.uid)?.let { AddFriendLoadState.Ready(it) }
            ?: AddFriendLoadState.Error(
                context.getString(R.string.friend_verification_load_failed),
            )
    }

    val displayName = user.name?.takeIf { it.isNotBlank() }
        ?: context.getString(R.string.message_sender_unknown)
    val info = (state as? AddFriendLoadState.Ready)?.info
    val mode = info?.mode
    val trimmed = text.trim()
    val canSend = !sending && mode != null && mode != "forbid" &&
        (mode == "direct" || trimmed.isNotEmpty())

    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text(stringResource(R.string.add_friend_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.add_friend_target, displayName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                when (val current = state) {
                    AddFriendLoadState.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    is AddFriendLoadState.Error -> {
                        Text(
                            text = current.message,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    is AddFriendLoadState.Ready -> {
                        when (mode) {
                            "forbid" -> Text(
                                text = stringResource(R.string.add_friend_forbid),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            "direct" -> Text(
                                text = stringResource(R.string.add_friend_direct_note, displayName),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            "need_message" -> {
                                Text(
                                    text = stringResource(R.string.add_friend_need_message),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = text,
                                    onValueChange = { text = it.take(200) },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = {
                                        Text(stringResource(R.string.add_friend_message_hint))
                                    },
                                    minLines = 2,
                                )
                            }
                            "question" -> {
                                Text(
                                    text = stringResource(R.string.add_friend_question),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                Text(
                                    text = current.info.question.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = text,
                                    onValueChange = { text = it.take(200) },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = {
                                        Text(stringResource(R.string.add_friend_answer_hint))
                                    },
                                    minLines = 2,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (mode == "forbid") {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.settings_cancel))
                }
            } else {
                TextButton(
                    onClick = {
                        scope.launch {
                            sending = true
                            val message = if (mode == "direct") null else trimmed
                            val ok = viewModel.sendFriendRequest(user.uid, message)
                            sending = false
                            if (ok) {
                                Toast.makeText(
                                    context,
                                    R.string.friends_request_sent,
                                    Toast.LENGTH_SHORT,
                                ).show()
                                onDismiss()
                            }
                        }
                    },
                    enabled = canSend,
                ) {
                    Text(stringResource(R.string.add_friend_send))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !sending,
            ) {
                Text(stringResource(R.string.settings_cancel))
            }
        },
    )
}
