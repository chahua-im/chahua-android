package net.paigu.chahua.ui.main

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.paigu.chahua.core.AppGraph
import net.paigu.chahua.data.models.StickerPackDetailResponse
import net.paigu.chahua.data.models.StickerPackSummaryDto

data class StickerPacksUiState(
    val packs: List<StickerPackSummaryDto> = emptyList(),
    val ownedIds: Set<String> = emptySet(),
    val loading: Boolean = false,
    val creating: Boolean = false,
    val error: String? = null,
)

data class StickerPackDetailUiState(
    val detail: StickerPackDetailResponse? = null,
    val loading: Boolean = false,
    val uploading: Boolean = false,
    val working: Boolean = false,
    val error: String? = null,
)

class StickersViewModel(application: Application) : AndroidViewModel(application) {

    private val api = AppGraph.api

    private val _packsState = MutableStateFlow(StickerPacksUiState())
    val packsState: StateFlow<StickerPacksUiState> = _packsState.asStateFlow()

    private val _detailState = MutableStateFlow(StickerPackDetailUiState())
    val detailState: StateFlow<StickerPackDetailUiState> = _detailState.asStateFlow()

    /** 加载收藏（订阅）的表情包，自己创建的排在前面。 */
    fun loadPacks() {
        viewModelScope.launch {
            _packsState.value = _packsState.value.copy(loading = true, error = null)
            runCatching {
                val owned = api.ownedStickerPacks().packs
                val subscribed = api.subscribedStickerPacks().packs
                val ownedIds = owned.mapTo(HashSet()) { it.id }
                owned + subscribed.filterNot { it.id in ownedIds }
            }
                .onSuccess { packs ->
                    _packsState.value = _packsState.value.copy(
                        packs = packs,
                        ownedIds = packs.filter { it.ownerUid == meUid() }.mapTo(HashSet()) { it.id },
                        loading = false,
                    )
                }
                .onFailure {
                    _packsState.value = _packsState.value.copy(loading = false, error = it.message)
                }
        }
    }

    fun createPack(name: String, onCreated: (StickerPackSummaryDto) -> Unit) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            _packsState.value = _packsState.value.copy(creating = true, error = null)
            runCatching { api.createStickerPack(trimmed) }
                .onSuccess { pack ->
                    _packsState.value = _packsState.value.copy(
                        creating = false,
                        packs = listOf(pack) + _packsState.value.packs,
                        ownedIds = _packsState.value.ownedIds + pack.id,
                    )
                    onCreated(pack)
                }
                .onFailure {
                    _packsState.value = _packsState.value.copy(creating = false, error = it.message)
                }
        }
    }

    fun loadDetail(packId: String) {
        viewModelScope.launch {
            _detailState.value = _detailState.value.copy(loading = true, error = null)
            runCatching { api.stickerPackDetail(packId) }
                .onSuccess { detail ->
                    _detailState.value = _detailState.value.copy(detail = detail, loading = false)
                }
                .onFailure {
                    _detailState.value = _detailState.value.copy(loading = false, error = it.message)
                }
        }
    }

    fun unsubscribePack(packId: String, onDone: () -> Unit) {
        viewModelScope.launch {
            _detailState.value = _detailState.value.copy(working = true, error = null)
            runCatching { api.unsubscribeStickerPack(packId) }
                .onSuccess {
                    _detailState.value = _detailState.value.copy(working = false)
                    _packsState.value = _packsState.value.copy(
                        packs = _packsState.value.packs.filterNot { it.id == packId },
                        ownedIds = _packsState.value.ownedIds - packId,
                    )
                    onDone()
                }
                .onFailure {
                    _detailState.value = _detailState.value.copy(working = false, error = it.message)
                }
        }
    }

    /** 从相册 Uri 读取图片并上传到表情包。 */
    fun uploadSticker(
        packId: String,
        uri: Uri,
        emoji: String,
        name: String?,
        onDone: () -> Unit,
    ) {
        val cleanEmoji = emoji.trim()
        if (cleanEmoji.isEmpty()) return
        viewModelScope.launch {
            _detailState.value = _detailState.value.copy(uploading = true, error = null)
            val read = runCatching {
                withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    val fileName = queryDisplayName(resolver, uri) ?: "sticker"
                    val contentType = resolver.getType(uri) ?: inferContentType(fileName)
                    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("cannot read sticker file")
                    Triple(fileName, contentType, bytes)
                }
            }
            read
                .onSuccess { (fileName, contentType, bytes) ->
                    runCatching {
                        api.uploadStickerToPack(
                            packId = packId,
                            fileName = fileName,
                            contentType = contentType,
                            bytes = bytes,
                            emoji = cleanEmoji,
                            name = name,
                        )
                    }
                        .onSuccess { sticker ->
                            val prev = _detailState.value.detail
                            if (prev != null && prev.id == packId) {
                                _detailState.value = _detailState.value.copy(
                                    detail = prev.copy(
                                        stickerCount = prev.stickerCount + 1,
                                        stickers = prev.stickers + sticker,
                                    ),
                                )
                            }
                            _detailState.value = _detailState.value.copy(uploading = false)
                            onDone()
                        }
                        .onFailure { e ->
                            _detailState.value = _detailState.value.copy(uploading = false, error = e.message)
                        }
                }
                .onFailure { e ->
                    _detailState.value = _detailState.value.copy(uploading = false, error = e.message)
                }
        }
    }

    fun dismissPacksError() {
        _packsState.value = _packsState.value.copy(error = null)
    }

    fun dismissDetailError() {
        _detailState.value = _detailState.value.copy(error = null)
    }

    private fun meUid(): Int = AppGraph.session.snapshot().me?.uid ?: -1

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    private fun inferContentType(fileName: String): String = when (
        fileName.substringAfterLast('.', "").lowercase()
    ) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "webm" -> "video/webm"
        else -> "image/png"
    }
}
