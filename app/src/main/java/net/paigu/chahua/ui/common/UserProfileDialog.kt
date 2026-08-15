package net.paigu.chahua.ui.common

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
) {
    val context = LocalContext.current
    val showUidInChat = LocalAppSettings.current.showUidInChat
    val displayName = user.name?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.message_sender_unknown)

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
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel))
            }
        },
    )
}
