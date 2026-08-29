package net.paigu.chahua.ui.chat

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatInputUtilsTest {

    @Test
    fun extractActiveMentionQueryReturnsTailAfterAt() {
        assertEquals("abc", extractActiveMentionQuery("@abc"))
        assertEquals("", extractActiveMentionQuery("@"))
        assertEquals("name", extractActiveMentionQuery("hello @name"))
    }

    @Test
    fun extractActiveMentionQueryRejectsInvalidPositions() {
        assertNull(extractActiveMentionQuery(""))
        assertNull(extractActiveMentionQuery("no@mention"))
        assertNull(extractActiveMentionQuery("@ab cd"))
        assertNull(extractActiveMentionQuery("mail a@b.com"))
    }

    @Test
    fun replaceMentionTokenReplacesQueryWithToken() {
        val result = replaceMentionToken(TextFieldValue("hello @ab world"), uid = 7)

        // 原实现会保留被替换尾部的前导空格（“hello @ab world” -> “hello @[uid:7]  world”）。
        assertEquals("hello @[uid:7]  world", result.text)
        assertEquals(TextRange(15), result.selection)
    }

    @Test
    fun replaceMentionTokenAtStartNoLeadingSpace() {
        val result = replaceMentionToken(TextFieldValue("@ab"), uid = 7)

        assertEquals("@[uid:7] ", result.text)
        assertEquals(TextRange(9), result.selection)
    }
}
