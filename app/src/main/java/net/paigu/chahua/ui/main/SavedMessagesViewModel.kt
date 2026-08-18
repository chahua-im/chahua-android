package net.paigu.chahua.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.paigu.chahua.core.AppGraph
import net.paigu.chahua.data.models.SavedMessageDto

data class SavedMessagesUiState(
    val loading: Boolean = false,
    val loadingOlder: Boolean = false,
    val items: List<SavedMessageDto> = emptyList(),
    val nextCursor: String? = null,
    val error: String? = null,
    val workingId: String? = null,
)

/** 全局收藏消息列表。 */
class SavedMessagesViewModel(application: Application) : AndroidViewModel(application) {

    private val api = AppGraph.api

    private val _uiState = MutableStateFlow(SavedMessagesUiState())
    val uiState: StateFlow<SavedMessagesUiState> = _uiState.asStateFlow()

    fun load() {
        if (_uiState.value.loading) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            runCatching { api.savedMessages(limit = 50) }
                .onSuccess { resp ->
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        items = resp.savedMessages,
                        nextCursor = resp.nextCursor,
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(loading = false, error = it.message)
                }
        }
    }

    fun loadOlder() {
        val cursor = _uiState.value.nextCursor ?: return
        if (_uiState.value.loadingOlder) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loadingOlder = true)
            runCatching { api.savedMessages(limit = 50, before = cursor) }
                .onSuccess { resp ->
                    _uiState.value = _uiState.value.copy(
                        loadingOlder = false,
                        items = _uiState.value.items + resp.savedMessages,
                        nextCursor = resp.nextCursor,
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(loadingOlder = false, error = it.message)
                }
        }
    }

    fun remove(item: SavedMessageDto) {
        if (_uiState.value.workingId != null) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(workingId = item.id)
            runCatching { api.deleteSavedMessage(item.id) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        workingId = null,
                        items = _uiState.value.items.filterNot { it.id == item.id },
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        workingId = null,
                        error = it.message,
                    )
                }
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
