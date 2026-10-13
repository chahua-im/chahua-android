package net.paigu.chahua.ui.common.markdown

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
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
 * 链接（裸链接与 `[文字](url)`）会渲染成带下划线的主色文本，点击由 Compose
 * 的 [LinkAnnotation.Url] 交给系统浏览器打开；气泡自身的点击/长按不受影响。
 */
@Composable
internal fun rememberChatMarkdownText(
    message: MessageDto,
    baseStyle: TextStyle,
    mine: Boolean,
): AnnotatedString {
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

/** 把 [ChatMarkdown] 的解析结果转成可直接交给 `Text` 的 [AnnotatedString]。 */
internal fun buildChatMarkdownText(
    source: String,
    mentions: List<MentionDto>,
    baseFontSize: TextUnit,
    linkColor: Color,
    codeBackground: Color,
    quoteColor: Color,
): AnnotatedString {
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
    return buildAnnotatedString {
        append(doc.text)
        doc.spans.forEach { span ->
            val style = when (span.style) {
                MdStyle.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                MdStyle.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                MdStyle.UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
                MdStyle.STRIKE -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                MdStyle.CODE, MdStyle.CODE_BLOCK -> codeStyle
                MdStyle.LINK -> linkStyle
                MdStyle.QUOTE -> SpanStyle(color = quoteColor)
            }
            addStyle(style, span.start, span.end)
        }
        doc.links.forEach { link ->
            addLink(
                LinkAnnotation.Url(
                    url = link.url,
                    styles = TextLinkStyles(style = linkStyle),
                ),
                link.start,
                link.end,
            )
        }
    }
}
