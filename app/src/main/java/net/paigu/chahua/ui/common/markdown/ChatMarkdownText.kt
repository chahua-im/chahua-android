package net.paigu.chahua.ui.common.markdown

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import net.paigu.chahua.data.models.MentionDto
import net.paigu.chahua.data.models.MessageDto

/**
 * 消息气泡正文的 Markdown 渲染入口。
 *
 * 链接（裸链接与 `[文字](url)`）渲染成带下划线的主色文本，但**刻意不使用**
 * [androidx.compose.ui.text.LinkAnnotation]：Compose 会为每个链接区间生成一个
 * `combinedClickable` 覆盖层，它在手势的 Main pass 里比气泡更早拿到事件并消费掉 down，
 * 于是长按链接文字时气泡自己的 `combinedClickable` 收不到手势，消息菜单弹不出来。
 * 这里只保留链接样式，点击命中交给 [ChatMarkdownText.linkAt] 由气泡自己判断。
 */
@Composable
internal fun rememberChatMarkdownText(
    message: MessageDto,
    baseStyle: TextStyle,
    mine: Boolean,
): ChatMarkdownText {
    val source = message.message.orEmpty()
    val mentions = message.mentions
    val linkColor = if (mine) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.primary
    }
    val codeBackground = if (mine) {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.14f)
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    }
    val quoteColor = if (mine) {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val baseFontSize = baseStyle.fontSize
    return remember(source, mentions, baseFontSize, linkColor, codeBackground, quoteColor) {
        buildChatMarkdownText(
            source = source,
            mentions = mentions,
            baseFontSize = baseFontSize,
            linkColor = linkColor,
            codeBackground = codeBackground,
            quoteColor = quoteColor,
        )
    }
}

/** 正文里的一条可点击链接及其字符区间。 */
internal data class ChatMarkdownLink(val start: Int, val end: Int, val url: String)

/**
 * 一条消息正文的渲染结果：交给 `Text` 的 [text]，以及用于命中测试的 [links]。
 */
internal data class ChatMarkdownText(
    val text: AnnotatedString,
    val links: List<ChatMarkdownLink>,
) {
    /** 把 `Text` 布局内的坐标映射成链接；不在任何链接上时返回 null。 */
    fun linkAt(layout: TextLayoutResult, position: Offset): ChatMarkdownLink? {
        if (position.x < 0f || position.y < 0f ||
            position.x > layout.size.width || position.y > layout.size.height
        ) {
            return null
        }
        val offset = layout.getOffsetForPosition(position)
        return links.firstOrNull { offset >= it.start && offset < it.end }
    }
}

/** 把 [ChatMarkdown] 的解析结果转成可直接交给 `Text` 的 [ChatMarkdownText]。 */
internal fun buildChatMarkdownText(
    source: String,
    mentions: List<MentionDto>,
    baseFontSize: TextUnit,
    linkColor: Color,
    codeBackground: Color,
    quoteColor: Color,
): ChatMarkdownText {
    val nameByUid = mentions.associate { it.uid to it.username }
    val doc = ChatMarkdown.parse(source) { uid ->
        nameByUid[uid]?.takeIf { it.isNotBlank() }
    }
    val codeStyle = SpanStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = if (baseFontSize.isSp) baseFontSize * 0.92f else baseFontSize,
        background = codeBackground,
    )
    val linkStyle = SpanStyle(
        color = linkColor,
        textDecoration = TextDecoration.Underline,
    )
    val text = buildAnnotatedString {
        append(doc.text)
        doc.spans.forEach { span ->
            val style = when (span.style) {
                MdStyle.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                MdStyle.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                MdStyle.UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
                MdStyle.STRIKE -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                MdStyle.CODE, MdStyle.CODE_BLOCK -> codeStyle
                // 链接的样式由这里提供，不再额外挂 LinkAnnotation（见文件顶部注释）。
                MdStyle.LINK -> linkStyle
                MdStyle.QUOTE -> SpanStyle(color = quoteColor)
            }
            addStyle(style, span.start, span.end)
        }
    }
    return ChatMarkdownText(
        text = text,
        links = doc.links.map { ChatMarkdownLink(it.start, it.end, it.url) },
    )
}
