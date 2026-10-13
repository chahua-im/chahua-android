package net.paigu.chahua.ui.common.markdown

/** 消息正文里可用的行内/块级样式（具体外观由渲染层决定）。 */
internal enum class MdStyle {
    BOLD,
    ITALIC,
    UNDERLINE,
    STRIKE,
    CODE,
    CODE_BLOCK,
    LINK,
    QUOTE,
}

/** `[start, end)` 区间上的样式。 */
internal data class MdSpan(val start: Int, val end: Int, val style: MdStyle)

/** `[start, end)` 区间上可点击的链接。 */
internal data class MdLink(val start: Int, val end: Int, val url: String)

/** 解析结果：纯文本 + 样式区间 + 链接区间，偏移与 [text] 一一对应。 */
internal data class MdDocument(
    val text: String,
    val spans: List<MdSpan>,
    val links: List<MdLink>,
)

/**
 * 聊天消息的 Markdown 子集解析（与 Web 端 wetty-chat-mobile 的
 * `src/utils/markdown` 保持一致，见 chahua#539）。
 *
 * 支持的语法：
 *  - 行内：`*斜体*`、`**粗体**`、`__下划线__`、`~~删除线~~`、`` `行内代码` ``、
 *    `[文字](https://…)`、裸链接自动识别（http/https/www）；
 *  - 块级：段落（单换行即换行）、`-`/`+`/`1.` 列表、`>` 引用、``` 围栏代码块。
 *
 * 刻意不支持（按 Web 端子集，保持字面量）：标题、分割线、表格、图片、HTML、
 * 引用式链接、四空格缩进代码块，以及 `_单个下划线_`、`*` 作为列表符号。
 *
 * 解析结果是一段纯文本 + 样式区间 [+ 链接区间]，与 Compose 无关，便于单元测试。
 */
internal object ChatMarkdown {

    private const val PLACEHOLDER_OPEN = '\uFFF0'
    private const val PLACEHOLDER_CLOSE = '\uFFF1'

    private val MENTION_REGEX = Regex("@\\[uid:(\\d+)\\]")

    /** ASCII 标点：反斜杠只能转义这些字符。 */
    private const val ESCAPABLE_PUNCT = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~"

    private const val QUOTE_PREFIX = "▎ "
    private const val BULLET_PREFIX = "• "
    private const val LIST_INDENT = "  "

    /**
     * 解析消息正文。
     *
     * @param mentionName 由 uid 取用户名，用于把 `@[uid:N]` 还原成 `@用户名`。
     */
    fun parse(source: String, mentionName: (Int) -> String? = { null }): MdDocument {
        if (source.isEmpty()) return MdDocument("", emptyList(), emptyList())
        val text = source.replace("\r\n", "\n").replace('\r', '\n')
        val regions = findCodeRegions(text)
        val carved = carveMentions(text, regions)
        val body = normalizeMarkdown(carved.body)
        val emitter = Emitter { raw -> resolveMentions(raw, carved.slots, mentionName) }
        renderBlocks(parseBlocks(body.split('\n')), emitter)
        return MdDocument(emitter.text, emitter.spans, emitter.links)
    }

    /* ===================== 提及占位符（@[uid:N]） ===================== */

    private class MentionSlot(val uid: Int)

    private class Carved(val body: String, val slots: List<MentionSlot>)

    /**
     * 把代码区域之外的 `@[uid:N]` 换成不可见占位符，避免 Markdown 解析
     * （`[`、`]`、`_` 等）破坏提及；代码区域内的提及保持原样。
     */
    private fun carveMentions(source: String, regions: List<TextRegion>): Carved {
        val slots = mutableListOf<MentionSlot>()
        val out = StringBuilder(source.length)
        var cursor = 0
        var from = 0
        while (true) {
            val match = MENTION_REGEX.find(source, from) ?: break
            val start = match.range.first
            val end = match.range.last + 1
            if (regions.any { start >= it.start && start < it.end }) {
                from = end
                continue
            }
            val uid = match.groupValues[1].toIntOrNull()
            if (uid == null) {
                from = end
                continue
            }
            out.append(source, cursor, start)
            slots += MentionSlot(uid)
            out.append(PLACEHOLDER_OPEN).append(slots.size - 1).append(PLACEHOLDER_CLOSE)
            cursor = end
            from = end
        }
        out.append(source, cursor, source.length)
        return Carved(out.toString(), slots)
    }

    private fun resolveMentions(
        raw: String,
        slots: List<MentionSlot>,
        mentionName: (Int) -> String?,
    ): String {
        if (raw.indexOf(PLACEHOLDER_OPEN) < 0) return raw
        val out = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val ch = raw[i]
            if (ch != PLACEHOLDER_OPEN) {
                out.append(ch)
                i++
                continue
            }
            var j = i + 1
            var index = 0
            var digits = 0
            while (j < raw.length && raw[j].isDigit()) {
                index = index * 10 + (raw[j] - '0')
                digits++
                j++
            }
            if (digits > 0 && j < raw.length && raw[j] == PLACEHOLDER_CLOSE) {
                slots.getOrNull(index)?.let { slot ->
                    val name = mentionName(slot.uid)?.takeIf { it.isNotBlank() }
                    out.append('@').append(name ?: "User ${slot.uid}")
                }
                i = j + 1
            } else {
                out.append(ch)
                i++
            }
        }
        return out.toString()
    }

    /* ===================== 代码区域 ===================== */

    private class TextRegion(val start: Int, val end: Int)

    /** 围栏代码块 + 行内代码，供提及占位与空白规整跳过。 */
    private fun findCodeRegions(source: String): List<TextRegion> =
        (findFencedRegions(source) + findInlineCodeRegions(source)).sortedBy { it.start }

    private fun findFencedRegions(source: String): List<TextRegion> {
        val regions = mutableListOf<TextRegion>()
        val n = source.length
        var i = 0
        while (i < n) {
            val ch = source[i]
            if (ch != '`' && ch != '~') {
                i++
                continue
            }
            val lineStart = if (i == 0) 0 else source.lastIndexOf('\n', i - 1) + 1
            val lead = source.substring(lineStart, i)
            if (lead.length > 3 || lead.any { it != ' ' && it != '\t' }) {
                i++
                continue
            }
            var run = i
            while (run < n && source[run] == ch) run++
            val openLen = run - i
            if (openLen < 3) {
                i = run
                continue
            }
            var cursor = source.indexOf('\n', run).let { if (it < 0) n else it + 1 }
            var end = n
            while (cursor < n) {
                val lineEnd = source.indexOf('\n', cursor).let { if (it < 0) n else it }
                var p = cursor
                while (p < lineEnd && (source[p] == ' ' || source[p] == '\t') && p - cursor < 3) p++
                var isClosing = false
                if (p < lineEnd && source[p] == ch) {
                    var r = p
                    while (r < lineEnd && source[r] == ch) r++
                    if (r - p >= openLen) {
                        var t = r
                        while (t < lineEnd && (source[t] == ' ' || source[t] == '\t')) t++
                        isClosing = t == lineEnd
                    }
                }
                if (isClosing) {
                    end = lineEnd
                    break
                }
                cursor = lineEnd + 1
            }
            regions += TextRegion(i, end)
            i = end
        }
        return regions
    }

    private fun findInlineCodeRegions(source: String): List<TextRegion> {
        val regions = mutableListOf<TextRegion>()
        val n = source.length
        var i = 0
        while (i < n) {
            if (source[i] != '`') {
                i++
                continue
            }
            var run = i
            while (run < n && source[run] == '`') run++
            val len = run - i
            val lineEnd = source.indexOf('\n', run).let { if (it < 0) n else it }
            var close = -1
            var j = run
            while (j < lineEnd) {
                if (source[j] == '`') {
                    var r = j
                    while (r < n && source[r] == '`') r++
                    if (r - j == len) {
                        close = j
                        break
                    }
                    j = r
                } else {
                    j++
                }
            }
            if (close < 0) {
                i = run
                continue
            }
            regions += TextRegion(i, close + len)
            i = close + len
        }
        return regions
    }

    /* ===================== 解析前规整 ===================== */

    private fun normalizeMarkdown(source: String): String {
        if (source.isEmpty()) return source
        val masked = maskCodeRegions(source)
        return reflowPairedSpaces(reflowPairedSpaces(escapeImageMacros(source, masked), MODE_TWO), MODE_ONE)
    }

    /** 转义字符与代码区域：规整时跳过。 */
    private fun maskCodeRegions(source: String): BooleanArray {
        val n = source.length
        val masked = BooleanArray(n)
        var i = 0
        while (i < n) {
            if (source[i] == '\\' && i + 1 < n) {
                masked[i] = true
                masked[i + 1] = true
                i += 2
                continue
            }
            i++
        }
        for (region in findCodeRegions(source)) {
            for (x in region.start until minOf(region.end, n)) masked[x] = true
        }
        return masked
    }

    /**
     * `![alt](url)` 里的 `[` 加反斜杠，使图片宏整体按字面量渲染
     * （子集不支持图片，但 `![a](b)` 会被解析成 `!` + 普通链接）。
     */
    private fun escapeImageMacros(source: String, masked: BooleanArray): String {
        val out = StringBuilder(source.length + 8)
        var i = 0
        while (i < source.length) {
            if (!masked[i] && i + 1 < source.length &&
                source[i] == '!' && source[i + 1] == '[' && !masked[i + 1]
            ) {
                out.append('!').append('\\')
            } else {
                out.append(source[i])
            }
            i++
        }
        return out.toString()
    }

    private const val MODE_TWO = "two"
    private const val MODE_ONE = "one"

    private class MarkerRun(val start: Int)

    private fun collectMarkerPairs(
        source: String,
        masked: BooleanArray,
        mode: String,
    ): List<Pair<Int, Int>> {
        val runs = mutableListOf<MarkerRun>()
        var i = 0
        while (i < source.length) {
            val ch = source[i]
            if (masked[i] || (ch != '*' && ch != '~' && ch != '_')) {
                i++
                continue
            }
            var j = i
            while (j < source.length && source[j] == ch) j++
            val len = j - i
            val wanted = if (mode == MODE_TWO) 2 else 1
            if (len == wanted && (mode != MODE_ONE || ch == '*')) {
                runs += MarkerRun(i)
            }
            i = j
        }
        val pairs = mutableListOf<Pair<Int, Int>>()
        var t = 0
        while (t + 1 < runs.size) {
            pairs += runs[t].start to runs[t + 1].start
            t += 2
        }
        return pairs
    }

    /**
     * 把成对标记内侧多余的空格移到标记外：`** 粗体 **` → ` **粗体** `。
     * 双字符标记（`**`/`~~`/`__`）两侧都容忍；单 `*` 只容忍一侧，
     * 以免 `5 * 3 * 4` 这类算式被改写。
     */
    private fun reflowPairedSpaces(source: String, mode: String): String {
        if (source.isEmpty()) return source
        val masked = maskCodeRegions(source)
        val pairs = collectMarkerPairs(source, masked, mode)
        val markerLen = if (mode == MODE_TWO) 2 else 1
        var out = source
        for (p in pairs.indices.reversed()) {
            val openStart = pairs[p].first
            val closeStart = pairs[p].second
            if (closeStart <= openStart + markerLen) continue
            val mid = out.substring(openStart + markerLen, closeStart)
            if (mid.contains('\n')) continue
            var lead = 0
            while (lead < mid.length && (mid[lead] == ' ' || mid[lead] == '\t')) lead++
            var trail = 0
            while (trail < mid.length - lead &&
                (mid[mid.length - 1 - trail] == ' ' || mid[mid.length - 1 - trail] == '\t')
            ) {
                trail++
            }
            val inner = mid.substring(lead, mid.length - trail)
            if (inner.isEmpty()) continue
            if (lead == 0 && trail == 0) continue
            if (mode == MODE_ONE && lead > 0 && trail > 0) continue
            val prevChar = if (openStart > 0) out[openStart - 1] else ' '
            val nextChar = if (closeStart + markerLen < out.length) out[closeStart + markerLen] else ' '
            if (prevChar.isDigit() || nextChar.isDigit()) continue
            if (mode == MODE_ONE && inner.matches(Regex("^[0-9][0-9.,%]*$"))) continue
            if (mode == MODE_TWO && out[openStart] == '_' &&
                (prevChar.isLetterOrDigit() || nextChar.isLetterOrDigit())
            ) {
                continue
            }
            val marker = if (mode == MODE_TWO) out.substring(openStart, openStart + 2) else "*"
            val leadWs = mid.substring(0, lead)
            val trailWs = mid.substring(mid.length - trail)
            out = out.substring(0, openStart) + leadWs + marker + inner + marker + trailWs +
                out.substring(closeStart + markerLen)
        }
        return out
    }

    /* ===================== 块级解析 ===================== */

    private sealed interface Block {
        class Paragraph(val lines: List<String>) : Block
        class CodeBlock(val lines: List<String>) : Block
        class Quote(val blocks: List<Block>) : Block
        class BulletList(val items: List<List<Block>>) : Block
        class OrderedList(val start: Int, val items: List<List<Block>>) : Block
    }

    private class Fence(val char: Char, val length: Int)

    private fun fenceStart(line: String): Fence? {
        if (line.isEmpty()) return null
        val ch = line[0]
        if (ch != '`' && ch != '~') return null
        var k = 0
        while (k < line.length && line[k] == ch) k++
        if (k < 3) return null
        return Fence(ch, k)
    }

    private fun fenceEnd(line: String, fence: Fence): Boolean {
        val t = line.trimStart()
        if (t.isEmpty() || t[0] != fence.char) return false
        var k = 0
        while (k < t.length && t[k] == fence.char) k++
        if (k < fence.length) return false
        return t.substring(k).isBlank()
    }

    private fun bulletMarker(line: String): Int? {
        if (line.isEmpty()) return null
        val ch = line[0]
        if (ch != '-' && ch != '+') return null
        if (line.length == 1) return 1
        if (line[1] != ' ' && line[1] != '\t') return null
        var k = 1
        while (k < line.length && (line[k] == ' ' || line[k] == '\t') && k < 5) k++
        return k
    }

    private class OrderedMarker(val length: Int, val number: Int)

    private fun orderedMarker(line: String): OrderedMarker? {
        var k = 0
        while (k < line.length && line[k].isDigit() && k < 9) k++
        if (k == 0 || k >= line.length) return null
        val delim = line[k]
        if (delim != '.' && delim != ')') return null
        if (k + 1 < line.length && line[k + 1] != ' ' && line[k + 1] != '\t') return null
        val number = line.substring(0, k).toIntOrNull() ?: return null
        var m = k + 1
        while (m < line.length && (line[m] == ' ' || line[m] == '\t') && m < k + 5) m++
        return OrderedMarker(m, number)
    }

    private fun startsBlock(line: String): Boolean =
        line.startsWith(">") ||
            bulletMarker(line) != null ||
            orderedMarker(line) != null ||
            fenceStart(line) != null

    private fun parseBlocks(lines: List<String>): List<Block> {
        val blocks = mutableListOf<Block>()
        var i = 0
        while (i < lines.size) {
            val raw = lines[i]
            val trimmed = raw.trimStart()
            if (trimmed.isEmpty()) {
                i++
                continue
            }
            val indent = raw.length - trimmed.length

            val fence = if (indent <= 3) fenceStart(trimmed) else null
            if (fence != null) {
                val code = mutableListOf<String>()
                i++
                while (i < lines.size && !fenceEnd(lines[i], fence)) {
                    code += lines[i]
                    i++
                }
                if (i < lines.size) i++
                blocks += Block.CodeBlock(code)
                continue
            }

            if (indent <= 3 && trimmed.startsWith(">")) {
                val inner = mutableListOf<String>()
                while (i < lines.size) {
                    val line = lines[i]
                    val t = line.trimStart()
                    val li = line.length - t.length
                    if (li <= 3 && t.startsWith(">")) {
                        val body = t.substring(1).removePrefix(" ")
                        inner += body
                        i++
                    } else if (line.isNotBlank() && li <= 3 && !startsBlock(t)) {
                        inner += t
                        i++
                    } else {
                        break
                    }
                }
                blocks += Block.Quote(parseBlocks(inner))
                continue
            }

            val bullet = if (indent <= 3) bulletMarker(trimmed) else null
            val ordered = if (indent <= 3) orderedMarker(trimmed) else null
            if (bullet != null || ordered != null) {
                val isOrdered = ordered != null
                val startNumber = ordered?.number ?: 1
                val items = mutableListOf<List<Block>>()
                while (i < lines.size) {
                    val line = lines[i]
                    val t = line.trimStart()
                    val li = line.length - t.length
                    if (li > 3) break
                    val markerLength: Int
                    if (isOrdered) {
                        val m = orderedMarker(t) ?: break
                        markerLength = m.length
                    } else {
                        markerLength = bulletMarker(t) ?: break
                    }
                    val contentIndent = li + markerLength
                    val itemLines = mutableListOf(t.substring(markerLength))
                    i++
                    while (i < lines.size) {
                        val inner = lines[i]
                        if (inner.isBlank()) {
                            var k = i + 1
                            while (k < lines.size && lines[k].isBlank()) k++
                            val next = lines.getOrNull(k)
                            val nextIndent = next?.let { it.length - it.trimStart().length } ?: -1
                            if (next != null && nextIndent >= contentIndent) {
                                itemLines += ""
                                i = k
                                continue
                            }
                            break
                        }
                        val it2 = inner.trimStart()
                        val ind2 = inner.length - it2.length
                        if (ind2 >= contentIndent) {
                            itemLines += " ".repeat(ind2 - contentIndent) + it2
                            i++
                            continue
                        }
                        if (!startsBlock(it2)) {
                            itemLines += it2
                            i++
                            continue
                        }
                        break
                    }
                    items += parseBlocks(itemLines)
                }
                if (items.isNotEmpty()) {
                    blocks += if (isOrdered) {
                        Block.OrderedList(startNumber, items)
                    } else {
                        Block.BulletList(items)
                    }
                }
                continue
            }

            val paragraph = mutableListOf<String>()
            while (i < lines.size) {
                val line = lines[i]
                val t = line.trimStart()
                if (t.isEmpty()) break
                val li = line.length - t.length
                if (li <= 3 && startsBlock(t)) break
                paragraph += t
                i++
            }
            if (paragraph.isEmpty()) {
                i++
                continue
            }
            blocks += Block.Paragraph(paragraph)
        }
        return blocks
    }

    /* ===================== 输出 ===================== */

    private data class Prefix(val text: String, val style: MdStyle?)

    private class Emitter(private val resolve: (String) -> String) {
        private val builder = StringBuilder()
        private val spanList = mutableListOf<MdSpan>()
        private val linkList = mutableListOf<MdLink>()
        private val prefixes = mutableListOf<Prefix>()
        private var atLineStart = true

        val text: String get() = builder.toString()
        val spans: List<MdSpan> get() = spanList
        val links: List<MdLink> get() = linkList
        val length: Int get() = builder.length

        fun pushPrefix(prefix: Prefix) {
            prefixes += prefix
        }

        fun popPrefix() {
            if (prefixes.isNotEmpty()) prefixes.removeAt(prefixes.lastIndex)
        }

        /** 结束当前行（尚未写出下一行的前缀）。 */
        fun newLine() {
            if (!atLineStart) {
                builder.append('\n')
                atLineStart = true
            }
        }

        fun append(raw: String): Int {
            if (raw.isEmpty()) return builder.length
            if (atLineStart) {
                for (prefix in prefixes) {
                    val start = builder.length
                    builder.append(prefix.text)
                    prefix.style?.let { spanList += MdSpan(start, builder.length, it) }
                }
                atLineStart = false
            }
            val start = builder.length
            builder.append(resolve(raw))
            return start
        }

        fun span(start: Int, end: Int, style: MdStyle) {
            if (end > start) spanList += MdSpan(start, end, style)
        }

        fun link(start: Int, end: Int, url: String) {
            if (end > start) linkList += MdLink(start, end, url)
        }
    }

    private fun renderBlocks(blocks: List<Block>, emitter: Emitter) {
        for ((index, block) in blocks.withIndex()) {
            if (index > 0) emitter.newLine()
            when (block) {
                is Block.Paragraph -> {
                    for ((lineIndex, line) in block.lines.withIndex()) {
                        if (lineIndex > 0) emitter.newLine()
                        renderInline(tokenizeInline(line), emitter)
                    }
                }
                is Block.CodeBlock -> {
                    for ((lineIndex, line) in block.lines.withIndex()) {
                        if (lineIndex > 0) emitter.newLine()
                        val start = emitter.append(line)
                        emitter.span(start, emitter.length, MdStyle.CODE_BLOCK)
                    }
                }
                is Block.Quote -> {
                    // 引用总是独占新行，避免和列表符号挤在一行时丢失引用条。
                    emitter.newLine()
                    emitter.pushPrefix(Prefix(QUOTE_PREFIX, MdStyle.QUOTE))
                    renderBlocks(block.blocks, emitter)
                    emitter.popPrefix()
                }
                is Block.BulletList -> {
                    for (item in block.items) {
                        emitter.newLine()
                        emitter.append(BULLET_PREFIX)
                        emitter.pushPrefix(Prefix(LIST_INDENT, null))
                        renderBlocks(item, emitter)
                        emitter.popPrefix()
                    }
                }
                is Block.OrderedList -> {
                    var number = block.start
                    for (item in block.items) {
                        emitter.newLine()
                        val marker = "$number. "
                        emitter.append(marker)
                        emitter.pushPrefix(Prefix(" ".repeat(marker.length), null))
                        renderBlocks(item, emitter)
                        emitter.popPrefix()
                        number++
                    }
                }
            }
        }
    }

    /* ===================== 行内解析 ===================== */

    private sealed interface Inline

    private class IText(val value: String) : Inline

    private class IEscape(val value: String) : Inline

    private class ICode(val value: String) : Inline

    private object IBreak : Inline

    private class IDelim(
        val marker: Char,
        val count: Int,
        val pairUnit: Int,
        val canOpen: Boolean,
        val canClose: Boolean,
    ) : Inline

    private class ILink(val label: List<Inline>, val url: String, val clickable: Boolean) : Inline

    private class IAutoLink(val text: String, val url: String) : Inline

    private class Pairing(
        val opener: Int,
        val closer: Int,
        val use: Int,
        val style: MdStyle,
    ) {
        var start: Int = -1
    }

    private fun tokenizeInline(source: String): List<Inline> {
        val out = mutableListOf<Inline>()
        val text = StringBuilder()
        fun flush() {
            if (text.isNotEmpty()) {
                out += IText(text.toString())
                text.clear()
            }
        }
        var i = 0
        while (i < source.length) {
            val ch = source[i]
            when {
                ch == '\\' && i + 1 < source.length && ESCAPABLE_PUNCT.indexOf(source[i + 1]) >= 0 -> {
                    flush()
                    out += IEscape(source[i + 1].toString())
                    i += 2
                }
                ch == '`' -> {
                    var run = i
                    while (run < source.length && source[run] == '`') run++
                    val len = run - i
                    val lineEnd = source.indexOf('\n', run).let { if (it < 0) source.length else it }
                    var close = -1
                    var j = run
                    while (j < lineEnd) {
                        if (source[j] == '`') {
                            var r = j
                            while (r < source.length && source[r] == '`') r++
                            if (r - j == len) {
                                close = j
                                break
                            }
                            j = r
                        } else {
                            j++
                        }
                    }
                    if (close < 0) {
                        text.append(source, i, run)
                        i = run
                    } else {
                        flush()
                        out += ICode(source.substring(run, close).trim())
                        i = close + len
                    }
                }
                ch == '\n' -> {
                    flush()
                    out += IBreak
                    i++
                }
                ch == '[' -> {
                    val link = parseLink(source, i)
                    if (link == null) {
                        text.append(ch)
                        i++
                    } else {
                        flush()
                        out += link.node
                        i = link.next
                    }
                }
                ch == '*' || ch == '_' || ch == '~' -> {
                    var run = i
                    while (run < source.length && source[run] == ch) run++
                    val count = run - i
                    if ((ch == '~' || ch == '_') && count < 2) {
                        text.append(source, i, run)
                        i = run
                    } else {
                        val scan = scanDelimiters(source, i, count, canSplitWord = true)
                        flush()
                        out += IDelim(
                            marker = ch,
                            count = count,
                            pairUnit = if (ch == '*') 0 else 2,
                            canOpen = scan.canOpen,
                            canClose = scan.canClose,
                        )
                        i = run
                    }
                }
                ch == 'h' || ch == 'H' || ch == 'w' || ch == 'W' -> {
                    val url = matchBareUrl(source, i)
                    if (url == null) {
                        text.append(ch)
                        i++
                    } else {
                        flush()
                        out += IAutoLink(url.text, url.href)
                        i += url.text.length
                    }
                }
                else -> {
                    text.append(ch)
                    i++
                }
            }
        }
        flush()
        return out
    }

    private class ParsedLink(val node: ILink, val next: Int)

    private fun parseLink(source: String, start: Int): ParsedLink? {
        var depth = 0
        var j = start
        var labelEnd = -1
        while (j < source.length) {
            when (source[j]) {
                '\\' -> j++
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) {
                        labelEnd = j
                    }
                }
                '\n' -> return null
            }
            if (labelEnd >= 0) break
            j++
        }
        if (labelEnd < 0 || labelEnd + 1 >= source.length || source[labelEnd + 1] != '(') return null
        var k = labelEnd + 2
        var parens = 0
        var urlEnd = -1
        while (k < source.length) {
            val c = source[k]
            if (c == '\\') {
                k += 2
                continue
            }
            if (c == '(') {
                parens++
            } else if (c == ')') {
                if (parens == 0) {
                    urlEnd = k
                    break
                }
                parens--
            } else if (c == '\n') {
                return null
            }
            k++
        }
        if (urlEnd < 0) return null
        val rawTarget = source.substring(labelEnd + 2, urlEnd).trim()
        // 支持 `[文字](url "标题")` 形式。
        val url = if (rawTarget.any { it.isWhitespace() }) {
            val head = rawTarget.substringBeforeLast(' ').trim()
            val tail = rawTarget.substring(rawTarget.indexOf(' ') + 1).trim()
            val looksLikeTitle = tail.length >= 2 &&
                ((tail.first() == '"' && tail.last() == '"') ||
                    (tail.first() == '\'' && tail.last() == '\'') ||
                    (tail.first() == '(' && tail.last() == ')'))
            if (!looksLikeTitle) return null else head
        } else {
            rawTarget
        }
        val safeUrl = safeHref(url) ?: return null
        val label = source.substring(start + 1, labelEnd)
        return ParsedLink(ILink(tokenizeInline(label), safeUrl, isClickableHref(safeUrl)), urlEnd + 1)
    }

    /**
     * markdown-it 的 `validateLink`：只有危险协议被拒绝（整段按字面量渲染），
     * 其余（相对路径等）仍按链接语法解析，只是不可点击。
     */
    private fun safeHref(url: String): String? {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return null
        if (Regex("^(?i)(javascript|vbscript|file|data):").containsMatchIn(trimmed)) return null
        return trimmed
    }

    /** 只有绝对 http/https 链接可点击，与 Web 端 `isSafeLinkHref` 一致。 */
    private fun isClickableHref(url: String): Boolean =
        url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)

    private val URL_TRAILING = ".,!?;:'\"、。，！？；：”’"

    private class BareUrl(val text: String, val href: String)

    /** 裸链接自动识别：http(s):// 与 www. 开头。 */
    private fun matchBareUrl(source: String, start: Int): BareUrl? {
        val rest = source.substring(start)
        val schemeMatch = Regex("^(?i)https?://").find(rest)
        val hasScheme = schemeMatch != null
        if (!hasScheme && !rest.startsWith("www.", ignoreCase = true)) return null
        var end = if (hasScheme) schemeMatch!!.value.length else 0
        if (hasScheme && end >= rest.length) return null
        while (end < rest.length) {
            val c = rest[end]
            // 只吃 ASCII 可见字符，中文/空白/引号即结束，避免把整句中文吞进链接。
            if (c.code <= 0x20 || c.code >= 0x7F || c == '<' || c == '>' || c == '"') break
            end++
        }
        if (!hasScheme) {
            val dot = rest.indexOf('.')
            if (dot < 0 || end <= dot + 1) return null
        }
        // 去掉尾部的标点与不配对的后括号。
        while (end > 0 && URL_TRAILING.indexOf(rest[end - 1]) >= 0) end--
        while (end > 0) {
            val last = rest[end - 1]
            val open = when (last) {
                ')' -> '('
                ']' -> '['
                '}' -> '{'
                else -> break
            }
            val body = rest.substring(0, end)
            if (body.count { it == open } >= body.count { it == last }) break
            end--
        }
        if (end <= 0) return null
        val url = rest.substring(0, end)
        return if (hasScheme) BareUrl(url, url) else BareUrl(url, "http://$url")
    }

    private class DelimScan(val canOpen: Boolean, val canClose: Boolean)

    private fun scanDelimiters(
        source: String,
        start: Int,
        length: Int,
        canSplitWord: Boolean,
    ): DelimScan {
        val lastChar = if (start > 0) source[start - 1] else '\n'
        val nextChar = if (start + length < source.length) source[start + length] else '\n'
        val lastWhite = isWhiteSpaceChar(lastChar)
        val lastPunct = isPunctChar(lastChar)
        val nextWhite = isWhiteSpaceChar(nextChar)
        val nextPunct = isPunctChar(nextChar)
        val leftFlanking = !nextWhite && (!nextPunct || lastWhite || lastPunct)
        val rightFlanking = !lastWhite && (!lastPunct || nextWhite || nextPunct)
        return if (canSplitWord) {
            DelimScan(leftFlanking, rightFlanking)
        } else {
            DelimScan(
                leftFlanking && (!rightFlanking || lastPunct),
                rightFlanking && (!leftFlanking || nextPunct),
            )
        }
    }

    private fun isWhiteSpaceChar(ch: Char): Boolean {
        val code = ch.code
        if (code in 0x2000..0x200A) return true
        return when (code) {
            0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x20, 0xA0, 0x1680, 0x202F, 0x205F, 0x3000 -> true
            else -> false
        }
    }

    private fun isPunctChar(ch: Char): Boolean {
        val code = ch.code
        if (code < 0x80) {
            return code in 0x21..0x2F || code in 0x3A..0x40 || code in 0x5B..0x60 || code in 0x7B..0x7E
        }
        return when (Character.getType(ch)) {
            Character.CONNECTOR_PUNCTUATION.toInt(),
            Character.DASH_PUNCTUATION.toInt(),
            Character.START_PUNCTUATION.toInt(),
            Character.END_PUNCTUATION.toInt(),
            Character.INITIAL_QUOTE_PUNCTUATION.toInt(),
            Character.FINAL_QUOTE_PUNCTUATION.toInt(),
            Character.OTHER_PUNCTUATION.toInt(),
            Character.MATH_SYMBOL.toInt(),
            Character.CURRENCY_SYMBOL.toInt(),
            Character.MODIFIER_SYMBOL.toInt(),
            Character.OTHER_SYMBOL.toInt(),
            -> true
            else -> false
        }
    }

    private fun renderInline(tokens: List<Inline>, emitter: Emitter) {
        val pairings = pairDelimiters(tokens)
        val opening = pairings.groupBy { it.opener }
        val closing = pairings.groupBy { it.closer }
        for ((index, token) in tokens.withIndex()) {
            when (token) {
                is IText -> emitter.append(token.value)
                is IEscape -> emitter.append(token.value)
                is ICode -> {
                    val start = emitter.append(token.value)
                    emitter.span(start, emitter.length, MdStyle.CODE)
                }
                is IBreak -> emitter.newLine()
                is IAutoLink -> {
                    val start = emitter.append(token.text)
                    val end = emitter.length
                    emitter.span(start, end, MdStyle.LINK)
                    emitter.link(start, end, token.url)
                }
                is ILink -> {
                    val start = emitter.length
                    renderInline(token.label, emitter)
                    val end = emitter.length
                    if (token.clickable) {
                        emitter.span(start, end, MdStyle.LINK)
                        emitter.link(start, end, token.url)
                    }
                }
                is IDelim -> {
                    closing[index]?.forEach { pairing ->
                        emitter.span(pairing.start, emitter.length, pairing.style)
                    }
                    val consumed = (opening[index]?.sumOf { it.use } ?: 0) +
                        (closing[index]?.sumOf { it.use } ?: 0)
                    val leftover = token.count - consumed
                    if (leftover > 0) emitter.append(token.marker.toString().repeat(leftover))
                    opening[index]?.forEach { pairing -> pairing.start = emitter.length }
                }
            }
        }
    }

    private fun pairDelimiters(tokens: List<Inline>): List<Pairing> {
        val pairings = mutableListOf<Pairing>()
        val remaining = HashMap<Int, Int>()
        for ((index, token) in tokens.withIndex()) {
            if (token is IDelim) remaining[index] = token.count
        }
        val openStack = ArrayDeque<Int>()
        for ((index, token) in tokens.withIndex()) {
            if (token !is IDelim) continue
            if (token.canClose) {
                while ((remaining[index] ?: 0) > 0) {
                    var found = -1
                    for (k in openStack.indices.reversed()) {
                        val openerIndex = openStack[k]
                        val opener = tokens[openerIndex] as IDelim
                        if (opener.marker != token.marker) continue
                        val left = remaining[openerIndex] ?: 0
                        if (left <= 0) continue
                        if (token.pairUnit == 2 && left < 2) continue
                        found = k
                        break
                    }
                    if (found < 0) break
                    val openerIndex = openStack[found]
                    val left = remaining[openerIndex] ?: 0
                    val right = remaining[index] ?: 0
                    val use = if (token.pairUnit == 2) 2 else if (left >= 2 && right >= 2) 2 else 1
                    val style = when (token.marker) {
                        '~' -> MdStyle.STRIKE
                        '_' -> MdStyle.UNDERLINE
                        else -> if (use == 2) MdStyle.BOLD else MdStyle.ITALIC
                    }
                    pairings += Pairing(openerIndex, index, use, style)
                    remaining[openerIndex] = left - use
                    remaining[index] = right - use
                    if ((remaining[openerIndex] ?: 0) <= 0) openStack.removeAt(found)
                }
            }
            if (token.canOpen && (remaining[index] ?: 0) > 0) openStack.addLast(index)
        }
        return pairings
    }
}
