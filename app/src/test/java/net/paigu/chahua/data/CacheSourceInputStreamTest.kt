package net.paigu.chahua.data

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.ByteArrayOutputStream
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** [CacheSourceInputStream]：保存视频时把缓存字节拷进相册的最后一道防线。 */
class CacheSourceInputStreamTest {

    /** 只按顺序吐出 [data] 的 [DataSource] 替身，读到末尾返回 EOS。 */
    private class FakeDataSource(private val data: ByteArray) : DataSource {

        var closed = false
            private set

        private var position = 0

        override fun open(dataSpec: DataSpec): Long = data.size.toLong()

        override fun addTransferListener(transferListener: TransferListener) = Unit

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (position >= data.size) return C.RESULT_END_OF_INPUT
            val count = minOf(length, data.size - position)
            data.copyInto(buffer, offset, position, position + count)
            position += count
            return count
        }

        override fun getUri(): Uri? = null

        override fun close() {
            closed = true
        }
    }

    @Test
    fun `copies every byte when the cache holds the whole video`() {
        val data = ByteArray(20_000) { (it % 251).toByte() }
        val source = FakeDataSource(data)
        val out = ByteArrayOutputStream()

        CacheSourceInputStream(source, data.size.toLong()).use { it.copyTo(out) }

        assertArrayEquals(data, out.toByteArray())
        assertTrue(source.closed)
    }

    @Test
    fun `fails instead of saving a truncated video`() {
        val source = FakeDataSource(ByteArray(1_000))
        val out = ByteArrayOutputStream()

        try {
            CacheSourceInputStream(source, 4_000).use { it.copyTo(out) }
            fail("缓存读不满时必须抛异常，不能把截断的视频交给保存流程")
        } catch (expected: IOException) {
            assertTrue(expected.message.orEmpty().contains("1000 / 4000"))
        }
        assertTrue(source.closed)
    }

    @Test
    fun `reads single bytes and closes only once`() {
        val source = FakeDataSource(byteArrayOf(7, 8))
        val stream = CacheSourceInputStream(source, 2)

        assertEquals(7, stream.read())
        assertEquals(8, stream.read())
        assertEquals(-1, stream.read())

        stream.close()
        stream.close()
        assertTrue(source.closed)
    }

    @Test
    fun `end of input after the expected length is not an error`() {
        val source = FakeDataSource(ByteArray(3))
        val stream = CacheSourceInputStream(source, 3)

        assertEquals(3, stream.read(ByteArray(3), 0, 3))
        assertEquals(-1, stream.read())
        assertFalse(source.closed)

        stream.close()
    }
}
