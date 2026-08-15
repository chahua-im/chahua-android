package net.paigu.chahua.ui.chat

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.paigu.chahua.R
import net.paigu.chahua.data.MAX_REACTION_HEAD_TABS
import net.paigu.chahua.data.models.ReactionDetailResponse
import net.paigu.chahua.data.models.ReactionGroupDto
import net.paigu.chahua.data.models.UserDto
import net.paigu.chahua.ui.common.UserAvatar

/**
 * 表态详情弹窗：打开时拉取详情，顶部横滑 tab 按
 * 「All / 前 8 个表情（按人数降序）/ More」分组，点击用户可查看资料。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReactionDetailsSheet(
    messageId: String,
    loadDetails: suspend (String) -> ReactionDetailResponse?,
    onDismiss: () -> Unit,
    onAvatarClick: (UserDto) -> Unit,
) {
    var loading by remember(messageId) { mutableStateOf(true) }
    var groups by remember(messageId) { mutableStateOf<List<ReactionGroupDto>>(emptyList()) }
    var selectedKey by remember(messageId) { mutableStateOf("all") }

    LaunchedEffect(messageId) {
        loading = true
        groups = loadDetails(messageId)?.reactions.orEmpty()
        selectedKey = "all"
        loading = false
    }

    val categories = remember(groups) { groupReactions(groups) }
    val activeCategory = categories.firstOrNull { it.key == selectedKey } ?: categories.firstOrNull()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.chat_reaction_details_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.settings_back),
                    )
                }
            }

            if (loading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else if (categories.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.chat_reaction_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(categories, key = { it.key }) { category ->
                        val selected = category.key == activeCategory?.key
                        val label = when (category.key) {
                            "all" -> stringResource(R.string.chat_reaction_all)
                            "more" -> stringResource(R.string.chat_reaction_more)
                            else -> category.label
                        }
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            modifier = Modifier.clickable { selectedKey = category.key },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = category.count.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                val showEmojis = activeCategory?.key == "all" || activeCategory?.key == "more"
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(
                        activeCategory?.users.orEmpty(),
                        key = { "${it.uid}-${it.firstReactIndex}-${it.emojis.joinToString("")}" },
                    ) { user ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onAvatarClick(
                                        UserDto(
                                            uid = user.uid,
                                            avatarUrl = user.avatarUrl,
                                            name = user.name,
                                        ),
                                    )
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            UserAvatar(
                                url = user.avatarUrl,
                                name = user.name,
                                size = 36.dp,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = user.name ?: stringResource(R.string.message_sender_unknown),
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (showEmojis && user.emojis.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = user.emojis.joinToString(" "),
                                    fontSize = 15.sp,
                                    maxLines = 2,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class GroupedReactionUser(
    val uid: Int,
    val name: String?,
    val avatarUrl: String?,
    val emojis: List<String>,
    val firstReactIndex: Int,
)

private data class ReactionCategory(
    val key: String,
    val label: String,
    val count: Int,
    val users: List<GroupedReactionUser>,
)

/** 按参考实现的分组逻辑：All / 前 [MAX_REACTION_HEAD_TABS] 个表情（人数降序）/ More。 */
private fun groupReactions(groups: List<ReactionGroupDto>): List<ReactionCategory> {
    if (groups.isEmpty()) return emptyList()

    val indexed = groups.mapIndexed { index, group -> group to index }
    val sorted = indexed.sortedWith(
        compareByDescending<Pair<ReactionGroupDto, Int>> { it.first.reactors.size }
            .thenBy { it.second },
    )
    val top = sorted
        .take(MAX_REACTION_HEAD_TABS)
        .map { it.first }
        .sortedWith(
            compareByDescending<ReactionGroupDto> { it.reactors.size }
                .thenBy { it.emoji },
        )
    val more = sorted.drop(MAX_REACTION_HEAD_TABS).map { it.first }

    val categories = mutableListOf<ReactionCategory>()

    val allUsers = LinkedHashMap<Int, GroupedReactionUser>()
    var globalIndex = 0
    groups.forEach { group ->
        group.reactors.forEach { reactor ->
            val index = reactor.sortIndex ?: globalIndex++
            val existing = allUsers[reactor.uid]
            if (existing == null) {
                allUsers[reactor.uid] = GroupedReactionUser(
                    uid = reactor.uid,
                    name = reactor.name,
                    avatarUrl = reactor.avatarUrl,
                    emojis = listOf(group.emoji),
                    firstReactIndex = index,
                )
            } else {
                allUsers[reactor.uid] = existing.copy(
                    emojis = existing.emojis + group.emoji,
                    firstReactIndex = minOf(existing.firstReactIndex, index),
                )
            }
        }
    }
    categories += ReactionCategory(
        key = "all",
        label = "All",
        count = allUsers.size,
        users = allUsers.values.sortedBy { it.firstReactIndex },
    )

    top.forEach { group ->
        val users = group.reactors.mapIndexed { index, reactor ->
            GroupedReactionUser(
                uid = reactor.uid,
                name = reactor.name,
                avatarUrl = reactor.avatarUrl,
                emojis = listOf(group.emoji),
                firstReactIndex = reactor.sortIndex ?: index,
            )
        }
        categories += ReactionCategory(
            key = group.emoji,
            label = group.emoji,
            count = users.size,
            users = users,
        )
    }

    if (more.isNotEmpty()) {
        val moreUsers = mutableListOf<GroupedReactionUser>()
        more.forEach { group ->
            group.reactors.forEachIndexed { index, reactor ->
                moreUsers += GroupedReactionUser(
                    uid = reactor.uid,
                    name = reactor.name,
                    avatarUrl = reactor.avatarUrl,
                    emojis = listOf(group.emoji),
                    firstReactIndex = reactor.sortIndex ?: index,
                )
            }
        }
        categories += ReactionCategory(
            key = "more",
            label = "More",
            count = moreUsers.size,
            users = moreUsers,
        )
    }

    return categories
}
