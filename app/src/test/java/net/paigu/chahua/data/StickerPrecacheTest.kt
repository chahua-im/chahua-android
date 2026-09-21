package net.paigu.chahua.data

import net.paigu.chahua.data.models.StickerMediaDto
import net.paigu.chahua.data.models.StickerPackDetailResponse
import net.paigu.chahua.data.models.StickerPackPreviewStickerDto
import net.paigu.chahua.data.models.StickerPackSummaryDto
import net.paigu.chahua.data.models.StickerSummaryDto
import org.junit.Assert.assertEquals
import org.junit.Test

class StickerPrecacheTest {

    private fun media(url: String) = StickerMediaDto(id = "m-$url", url = url)

    private fun summary(id: String, url: String) = StickerSummaryDto(id = id, media = media(url))

    private fun pack(id: String, previewUrl: String? = null) = StickerPackSummaryDto(
        id = id,
        name = id,
        previewSticker = previewUrl?.let {
            StickerPackPreviewStickerDto(id = "p-$id", media = media(it))
        },
    )

    private fun detail(id: String, vararg urls: String) = StickerPackDetailResponse(
        id = id,
        name = id,
        stickers = urls.mapIndexed { index, url -> summary("$id-$index", url) },
    )

    @Test
    fun `collects pack covers, favorites and stickers`() {
        val urls = stickerPrecacheUrls(
            packs = listOf(pack("a", "https://cdn/cover-a.png"), pack("b")),
            details = listOf(
                detail("a", "https://cdn/a1.png", "https://cdn/a2.png"),
                detail("b", "https://cdn/b1.png"),
            ),
            favorites = listOf(summary("f1", "https://cdn/fav.png")),
        )

        assertEquals(
            listOf(
                "https://cdn/cover-a.png",
                "https://cdn/fav.png",
                "https://cdn/a1.png",
                "https://cdn/a2.png",
                "https://cdn/b1.png",
            ),
            urls,
        )
    }

    /** 同一张图出现在多个来源时只应下载一次。 */
    @Test
    fun `deduplicates urls across sources`() {
        val urls = stickerPrecacheUrls(
            packs = listOf(pack("a", "https://cdn/same.png"), pack("b", "https://cdn/same.png")),
            details = listOf(
                detail("a", "https://cdn/same.png"),
                detail("b", "https://cdn/same.png"),
            ),
            favorites = listOf(summary("f1", "https://cdn/same.png")),
        )

        assertEquals(listOf("https://cdn/same.png"), urls)
    }

    @Test
    fun `skips blank urls and empty input`() {
        assertEquals(emptyList<String>(), stickerPrecacheUrls(emptyList(), emptyList(), emptyList()))

        val urls = stickerPrecacheUrls(
            packs = listOf(pack("a", "  ")),
            details = listOf(detail("a", "", "https://cdn/ok.png")),
            favorites = listOf(summary("f1", "")),
        )

        assertEquals(listOf("https://cdn/ok.png"), urls)
    }
}
