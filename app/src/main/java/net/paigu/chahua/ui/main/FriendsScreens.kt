package net.paigu.chahua.ui.main

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Badge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.window.embedding.SplitController
import net.paigu.chahua.R
import net.paigu.chahua.core.AppGraph
import net.paigu.chahua.data.models.ChatDto
import net.paigu.chahua.data.models.FriendRequestHistoryEntryDto
import net.paigu.chahua.data.models.FriendResponse
import net.paigu.chahua.data.models.MemberSummaryDto
import net.paigu.chahua.data.models.ThreadDto
import net.paigu.chahua.data.models.UserDto
import net.paigu.chahua.data.models.displayName
import net.paigu.chahua.data.models.isDm
import net.paigu.chahua.ui.chat.ChatActivity
import net.paigu.chahua.ui.chat.ChatScreen
import net.paigu.chahua.ui.chat.ChatViewModel
import net.paigu.chahua.ui.common.EmptyState
import net.paigu.chahua.ui.common.UserAvatar
import net.paigu.chahua.ui.common.UserProfileDialog
import net.paigu.chahua.ui.common.formatListTime
import net.paigu.chahua.ui.common.messagePreviewText
import net.paigu.chahua.ui.common.messagePreviewWithSender
import net.paigu.chahua.ui.group.GroupInfoActivity
import net.paigu.chahua.ui.invite.InviteRedeemActivity
import net.paigu.chahua.ui.media.MediaViewerActivity
import net.paigu.chahua.ui.theme.LocalAppSettings
import net.paigu.chahua.ui.theme.ChahuaTheme
import kotlinx.coroutines.launch

@Composable
internal fun FriendsTabContent(
    viewModel: ChatListViewModel,
    onOpenRequests: () -> Unit,
    onOpenFriend: (MemberSummaryDto) -> Unit,
) {
    val friends by viewModel.friends.collectAsState()
    val pendingCount by viewModel.pendingCount.collectAsState()
    val loading by viewModel.friendsLoading.collectAsState()

    LazyColumn {
        item(key = "friend_requests_entry") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenRequests)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.PersonAdd,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.friends_requests),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
                if (pendingCount > 0) {
                    Badge {
                        Text(
                            if (pendingCount > 99) "99+" else pendingCount.toString(),
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
        }
        when {
            loading && friends.isEmpty() -> {
                item(key = "friends_loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
            friends.isEmpty() -> {
                item(key = "friends_empty") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.friends_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            else -> {
                item(key = "friends_header") {
                    SectionHeader(text = stringResource(R.string.friends_section))
                }
                items(friends, key = { it.user.uid }) { friend ->
                    FriendRow(friend = friend, onClick = { onOpenFriend(friend.user) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun FriendRow(
    friend: FriendResponse,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatar(
            url = friend.user.avatarUrl,
            name = friend.user.username,
            size = 48.dp,
            showBackground = false,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = friend.user.username?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.message_sender_unknown),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            friend.since?.let {
                Text(
                    text = formatListTime(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 好友请求子页面：双向请求历史，收到的待处理请求可接受/拒绝。 */
@Composable
internal fun FriendRequestsContent(
    viewModel: ChatListViewModel,
    onBack: () -> Unit,
    onOpenProfile: (MemberSummaryDto) -> Unit,
) {
    val requests by viewModel.requests.collectAsState()
    val loading by viewModel.requestsLoading.collectAsState()
    val workingId by viewModel.workingRequestId.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadFriendRequests()
    }

    LazyColumn {
        item(key = "requests_back") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onBack)
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.settings_back),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.friends_requests),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            HorizontalDivider()
        }
        when {
            loading && requests.isEmpty() -> {
                item(key = "requests_loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
            requests.isEmpty() -> {
                item(key = "requests_empty") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.friends_requests_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            else -> {
                items(requests, key = { it.request.id }) { entry ->
                    RequestRow(
                        entry = entry,
                        workingId = workingId,
                        onAccept = { viewModel.acceptRequest(it) },
                        onReject = { viewModel.rejectRequest(it) },
                        onClick = {
                            val peer = if (entry.direction == "incoming") {
                                entry.request.from
                            } else {
                                entry.request.to
                            }
                            onOpenProfile(peer)
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun RequestRow(
    entry: FriendRequestHistoryEntryDto,
    workingId: String?,
    onAccept: (String) -> Unit,
    onReject: (String) -> Unit,
    onClick: () -> Unit,
) {
    val request = entry.request
    val incoming = entry.direction == "incoming"
    val peer = if (incoming) request.from else request.to
    val displayName = peer.username?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.message_sender_unknown)
    val pending = request.status == "pending"
    val preview = when {
        request.question != null -> stringResource(
            R.string.friends_request_qa,
            request.question,
            request.message.orEmpty(),
        )
        request.message != null -> request.message
        incoming -> stringResource(R.string.friends_request_default)
        else -> stringResource(R.string.friends_request_sent)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatar(
            url = peer.avatarUrl,
            name = displayName,
            size = 48.dp,
            showBackground = false,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = preview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (pending && incoming) {
            Row {
                TextButton(
                    onClick = { onAccept(request.id) },
                    enabled = workingId == null,
                ) {
                    Text(stringResource(R.string.friends_accept))
                }
                TextButton(
                    onClick = { onReject(request.id) },
                    enabled = workingId == null,
                ) {
                    Text(
                        text = stringResource(R.string.friends_reject),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        } else {
            Text(
                text = stringResource(
                    when (request.status) {
                        "accepted" -> R.string.friends_accepted
                        "rejected" -> R.string.friends_rejected
                        else -> R.string.friends_pending
                    },
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
