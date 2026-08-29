package net.paigu.chahua.ui.chat

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.core.content.FileProvider
import java.io.File

internal fun extractActiveMentionQuery(input: String): String? {
    val atIndex = input.indexOfLast { it == '@' }
    if (atIndex < 0) return null
    if (atIndex > 0 && !input[atIndex - 1].isWhitespace()) return null
    val tail = input.substring(atIndex + 1)
    if (tail.any { it.isWhitespace() }) return null
    return tail
}

internal fun replaceMentionToken(input: TextFieldValue, uid: Int): TextFieldValue {
    val text = input.text
    val atIndex = text.indexOfLast { it == '@' }
    if (atIndex < 0) return input
    val tail = text.substring(atIndex + 1)
    val queryLength = tail.takeWhile { !it.isWhitespace() }.length
    val end = atIndex + 1 + queryLength
    val beforeSpace = if (atIndex > 0 && text[atIndex - 1] != ' ') " " else ""
    val newText = text.substring(0, atIndex) + beforeSpace + "@[uid:$uid] " + text.substring(end)
    val cursor = atIndex + beforeSpace.length + "@[uid:$uid] ".length
    return TextFieldValue(
        text = newText,
        selection = TextRange(cursor),
    )
}

internal fun createTempUri(context: Context, extension: String): Uri {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File.createTempFile("capture_", ".$extension", dir)
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
}

internal fun queryDisplayName(context: Context, uri: Uri): String? = try {
    context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
} catch (e: Exception) {
    null
}
