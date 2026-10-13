package net.paigu.chahua.ui.common.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 与 Web 端 `src/utils/markdown` 下的 `*.test.ts`（chahua#539）对应的行为用例。
 */
class ChatMarkdownTest {

    private fun parse(source: String, names: Map<Int, String> = emptyMap()): MdDocument =
        ChatMarkdown.parse(source) { names[it] }

    private fun covered(doc: MdDocument, style: MdStyle): List<String> =
        doc.spans.filter { it.style == style }.map { doc.text.substring(it.start, it.end) }

    private fun text(source: String, names: Map<Int, String> = emptyMap()): String =
        parse(source, names).text

    /* ---------------- 纯文本与转义 ---------------- */

    @Test
    fun `plain text is untouched`() {
        assertEquals("hello world", text("hello world"))
        assertEquals("你好世界", text("你好世界"))
        assertEquals("5 * 3 = 15", text("5 * 3 = 15"))
        assertEquals("a ~2~ b", text("a ~2~ b"))
        assertTrue(parse("hello world").spans.isEmpty())
        assertTrue(parse("hello world").links.isEmpty())
    }

    @Test
    fun `single underscore is not emphasis`() {
        val doc = parse("_underscore em_")
        assertEquals("_underscore em_", doc.text)
        assertTrue(doc.spans.isEmpty())
    }

    @Test
    fun `headings hr and indented code stay literal`() {
        assertEquals("# heading", text("# heading"))
        assertEquals("---", text("---"))
        assertEquals("indented code", text("    indented code"))
        assertTrue(parse("# heading").spans.isEmpty())
    }

    @Test
    fun `escaped punctuation renders the bare character`() {
        val doc = parse("\\*not emphasis\\*")
        assertEquals("*not emphasis*", doc.text)
        assertTrue(doc.spans.isEmpty())
        assertEquals("literal [bracket]", text("literal \\[bracket\\]"))
    }

    @Test
    fun `backslashes in paths and regexes survive`() {
        assertEquals("C:\\path\\to\\file", text("C:\\path\\to\\file"))
        assertEquals("\\d+ matches digits", text("\\d+ matches digits"))
    }

    /* ---------------- 行内语法 ---------------- */

    @Test
    fun `inline styles`() {
        assertEquals(listOf("bold"), covered(parse("**bold** text"), MdStyle.BOLD))
        assertEquals(listOf("italic"), covered(parse("*italic*"), MdStyle.ITALIC))
        assertEquals(listOf("strike"), covered(parse("a ~~strike~~ b"), MdStyle.STRIKE))
        assertEquals(listOf("underline"), covered(parse("a __underline__ b"), MdStyle.UNDERLINE))
        assertEquals(listOf("inline code"), covered(parse("`inline code`"), MdStyle.CODE))
        assertEquals("inline code", text("`inline code`"))
    }

    @Test
    fun `triple markers give bold and italic`() {
        val doc = parse("***both***")
        assertEquals("both", doc.text)
        assertEquals(listOf("both"), covered(doc, MdStyle.BOLD))
        assertEquals(listOf("both"), covered(doc, MdStyle.ITALIC))
    }

    @Test
    fun `single tilde is literal`() {
        val doc = parse("a ~2~ b")
        assertEquals("a ~2~ b", doc.text)
        assertTrue(covered(doc, MdStyle.STRIKE).isEmpty())
    }

    @Test
    fun `stars inside code spans are never touched`() {
        val doc = parse("`*a *`")
        assertEquals("*a *", doc.text)
        assertEquals(listOf("*a *"), covered(doc, MdStyle.CODE))
        assertTrue(covered(doc, MdStyle.ITALIC).isEmpty())
    }

    /* ---------------- 空白规整 ---------------- */

    @Test
    fun `redundant spaces inside paired markers are forgiven`() {
        assertEquals(listOf("bold"), covered(parse("** bold **"), MdStyle.BOLD))
        assertEquals(listOf("under"), covered(parse("__ under __"), MdStyle.UNDERLINE))
        assertEquals(listOf("struck"), covered(parse("~~ struck ~~"), MdStyle.STRIKE))
    }

    @Test
    fun `single star forgives one side only`() {
        assertEquals(listOf("italic words here"), covered(parse("*italic words here *"), MdStyle.ITALIC))
        assertEquals(listOf("italic words here"), covered(parse("*italic words here*"), MdStyle.ITALIC))
        assertTrue(covered(parse("* x *"), MdStyle.ITALIC).isEmpty())
    }

    @Test
    fun `arithmetic-like stars stay literal`() {
        assertTrue(covered(parse("5 * 3 * 4"), MdStyle.ITALIC).isEmpty())
        assertTrue(covered(parse("a *2 * b"), MdStyle.ITALIC).isEmpty())
        assertEquals("5 * 3 * 4", text("5 * 3 * 4"))
    }

    /* ---------------- 换行与块级 ---------------- */

    @Test
    fun `single newline is a line break`() {
        assertEquals("a\nb", text("a\nb"))
    }

    @Test
    fun `star bullet lines are literal but dash and ordered lists work`() {
        assertEquals("* item", text("* item"))
        assertEquals("* item\n* two", text("* item\n* two"))

        val dash = text("- a\n- b")
        assertTrue(dash.contains("• a"))
        assertTrue(dash.contains("• b"))

        val ordered = text("1. a\n2. b")
        assertTrue(ordered.contains("1. a"))
        assertTrue(ordered.contains("2. b"))
    }

    @Test
    fun `nested list items are indented`() {
        val doc = text("- a\n  - b")
        assertTrue(doc.contains("• a"))
        assertTrue(doc.contains("  • b"))
    }

    @Test
    fun `blockquote gets a bar prefix`() {
        assertTrue(text("> quote").contains("▎ quote"))
        assertTrue(text("> a\n> b").contains("▎ a\n▎ b"))
    }

    @Test
    fun `fenced code block is preserved verbatim`() {
        val doc = parse("```\nval a = *b*\n```")
        assertEquals("val a = *b*", doc.text)
        assertEquals(listOf("val a = *b*"), covered(doc, MdStyle.CODE_BLOCK))
        assertTrue(covered(doc, MdStyle.ITALIC).isEmpty())
    }

    @Test
    fun `image macros stay literal`() {
        val doc = parse("![alt](diagram.png)")
        assertEquals("![alt](diagram.png)", doc.text)
        assertTrue(doc.links.isEmpty())
    }

    /* ---------------- 链接 ---------------- */

    @Test
    fun `bare url becomes a link`() {
        val doc = parse("see http://example.com/x?y=1 now")
        assertEquals("see http://example.com/x?y=1 now", doc.text)
        assertEquals(1, doc.links.size)
        assertEquals("http://example.com/x?y=1", doc.links[0].url)
        assertEquals("http://example.com/x?y=1", doc.text.substring(doc.links[0].start, doc.links[0].end))
        assertEquals(listOf("http://example.com/x?y=1"), covered(doc, MdStyle.LINK))
    }

    @Test
    fun `explicit markdown link keeps its label`() {
        val doc = parse("[label](https://a.b)")
        assertEquals("label", doc.text)
        assertEquals(1, doc.links.size)
        assertEquals("https://a.b", doc.links[0].url)
    }

    @Test
    fun `unsafe and relative hrefs are not links`() {
        val js = parse("[x](javascript:alert(1))")
        assertEquals("[x](javascript:alert(1))", js.text)
        assertTrue(js.links.isEmpty())

        val relative = parse("[alt](diagram.png)")
        assertEquals("alt", relative.text)
        assertTrue(relative.links.isEmpty())
    }

    @Test
    fun `trailing punctuation is excluded from bare urls`() {
        val doc = parse("看这个 https://a.b/c，很好")
        assertEquals(1, doc.links.size)
        assertEquals("https://a.b/c", doc.links[0].url)
        assertEquals("看这个 https://a.b/c，很好", doc.text)
    }

    @Test
    fun `www urls are linkified with http scheme`() {
        val doc = parse("go www.example.com now")
        assertEquals(1, doc.links.size)
        assertEquals("http://www.example.com", doc.links[0].url)
    }

    @Test
    fun `email addresses are not linkified`() {
        val doc = parse("mail me@example.com please")
        assertTrue(doc.links.isEmpty())
    }

    /* ---------------- 提及 ---------------- */

    @Test
    fun `mentions are restored as at-name`() {
        val doc = parse("@[uid:1] hello", mapOf(1 to "alice"))
        assertEquals("@alice hello", doc.text)

        val unknown = parse("@[uid:9] hello")
        assertEquals("@User 9 hello", unknown.text)
    }

    @Test
    fun `mentions inside code stay verbatim`() {
        val doc = parse("`@[uid:1]`", mapOf(1 to "alice"))
        assertEquals("@[uid:1]", doc.text)
        assertEquals(listOf("@[uid:1]"), covered(doc, MdStyle.CODE))
    }

    @Test
    fun `mention inside a star pair can be italic`() {
        val doc = parse("*@[uid:2] *", mapOf(2 to "devuser2"))
        assertEquals("@devuser2 ", doc.text)
        assertEquals(listOf("@devuser2"), covered(doc, MdStyle.ITALIC))
    }

    @Test
    fun `mention adjacent to markdown is not mangled`() {
        val doc = parse("**@[uid:3]** hi", mapOf(3 to "bob"))
        assertEquals("@bob hi", doc.text)
        assertEquals(listOf("@bob"), covered(doc, MdStyle.BOLD))
    }

    /* ---------------- 综合 ---------------- */

    @Test
    fun `mixed block and inline content`() {
        val doc = parse(
            """
            # 标题是字面量
            **加粗** 和 [链接](https://a.b)
            - 第一项
            - 第二项
            """.trimIndent(),
            emptyMap(),
        )
        assertTrue(doc.text.contains("# 标题是字面量"))
        assertTrue(doc.text.contains("• 第一项"))
        assertEquals(listOf("加粗"), covered(doc, MdStyle.BOLD))
        assertEquals(1, doc.links.size)
        assertFalse(doc.text.contains("**"))
    }
}
