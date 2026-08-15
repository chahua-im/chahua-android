package net.paigu.chahua.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.paigu.chahua.data.models.ReactionDto
import net.paigu.chahua.ui.common.UserAvatar

/**
 * 消息下方的表态药丸：emoji + 最多 5 个头像堆叠（超出显示 +N）。
 * 自己点过的高亮，点击直接切换该表态。
 */
@Composable
fun ReactionPill(
    reaction: ReactionDto,
    mine: Boolean,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = reaction.reactedByMe == true
    val background = when {
        active -> MaterialTheme.colorScheme.primary
        mine -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when {
        active -> MaterialTheme.colorScheme.onPrimary
        mine -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .clickable { onToggle(reaction.emoji) }
            .padding(start = 8.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = reaction.emoji,
            fontSize = 15.sp,
            lineHeight = 18.sp,
        )
        val reactors = reaction.reactors
        if (!reactors.isNullOrEmpty()) {
            Spacer(modifier = Modifier.width(5.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy((-8).dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                reactors.take(5).forEach { reactor ->
                    UserAvatar(
                        url = reactor.avatarUrl,
                        name = reactor.name,
                        size = 18.dp,
                        showBackground = false,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            if (reaction.count > 5) {
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "+${reaction.count - 5}",
                    fontSize = 11.sp,
                    color = contentColor,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        } else if (reaction.count > 1) {
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = reaction.count.toString(),
                fontSize = 12.sp,
                color = contentColor,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
