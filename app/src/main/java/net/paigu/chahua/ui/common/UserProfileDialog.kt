package net.paigu.chahua.ui.common

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Female
import androidx.compose.material.icons.filled.Male
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.paigu.chahua.R
import net.paigu.chahua.data.models.UserDto
import net.paigu.chahua.ui.theme.LocalAppSettings

private const val PERSONAL_SPACE_BASE_URL =
    "https://www.shireyishunjian.com/main/home.php?mod=space&uid="

/** 用户资料弹窗：大头像、用户名、个人空间入口。*/
@Composable
fun UserProfileDialog(
    user: UserDto,
    onDismiss: () -> Unit,
    onMessage: (() -> Unit)? = null,
    onAddFriend: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val showUidInChat = LocalAppSettings.current.showUidInChat
    val displayName = user.name?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.message_sender_unknown)
    val genderText = when (user.gender) {
        1 -> stringResource(R.string.gender_male)
        2 -> stringResource(R.string.gender_female)
        else -> stringResource(R.string.gender_unknown)
    }
    val levelName = user.userGroup?.name?.takeIf { it.isNotBlank() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                UserAvatar(
                    url = user.avatarUrl,
                    name = user.name,
                    size = 96.dp,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                ) {
                    when (user.gender) {
                        1 -> Icon(
                            imageVector = Icons.Filled.Male,
                            contentDescription = stringResource(R.string.gender_male),
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFF42A5F5),
                        )
                        2 -> Icon(
                            imageVector = Icons.Filled.Female,
                            contentDescription = stringResource(R.string.gender_female),
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFFEC407A),
                        )
                        else -> Text(
                            text = stringResource(R.string.user_profile_gender, genderText),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (levelName != null) {
                        val bg = parseGroupColor(user.userGroup?.chatGroupColor)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(bg)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = levelName,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (bg.luminance() > 0.5f) {
                                    Color(0xFF1B1B1F)
                                } else {
                                    Color.White
                                },
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                if (showUidInChat) {
                    Text(
                        text = stringResource(R.string.user_profile_uid, user.uid),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(PERSONAL_SPACE_BASE_URL + user.uid),
                        )
                        runCatching { context.startActivity(intent) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.user_profile_space))
                }
                if (onMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onMessage,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.user_profile_message))
                    }
                } else if (onAddFriend != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onAddFriend,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.user_profile_add_friend))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel))
            }
        },
    )
}

private fun parseGroupColor(hex: String?): Color {
    if (hex.isNullOrBlank()) return Color(0xFFE0E0E0)
    return runCatching {
        Color(android.graphics.Color.parseColor(hex.removePrefix("#").let { "#$it" }))
    }.getOrDefault(Color(0xFFE0E0E0))
}
