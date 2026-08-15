package net.paigu.chahua.ui.group

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.paigu.chahua.R
import net.paigu.chahua.core.AppGraph
import net.paigu.chahua.data.models.ChatAttachmentDto
import net.paigu.chahua.data.models.GroupInfoDto
import net.paigu.chahua.data.models.MemberDto
import net.paigu.chahua.data.models.MessageDto
import net.paigu.chahua.data.models.SavedMessageDto
import java.time.Instant

enum class AttachmentFilter(val apiKind: String) {
    IMAGE("image"),
    VIDEO("video"),
    ALL("all"),
}

data class GroupInfoUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val info: GroupInfoDto? = null,
    val leaving: Boolean = false,
    val attachments: Map<AttachmentFilter, List<ChatAttachmentDto>> = emptyMap(),
    val attachmentCursors: Map<AttachmentFilter, String?> = emptyMap(),
    val loadingAttachments: Boolean = false,
    val loadingMoreAttachments: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<MessageDto> = emptyList(),
    val searching: Boolean = false,
    val searchError: String? = null,
    val savedMessages: List<SavedMessageDto> = emptyList(),
    val loadingSaved: Boolean = false,
    val members: List<MemberDto> = emptyList(),
    val membersLoading: Boolean = false,
    val membersLoadingMore: Boolean = false,
    val membersCursor: Int? = null,
    val membersSearchQuery: String = "",
)

class GroupInfoViewModel(application: Application) : AndroidViewModel(application) {

    private val api = AppGraph.api
    private val store = AppGraph.store

    private val _uiState = MutableStateFlow(GroupInfoUiState())
    val uiState: StateFlow<GroupInfoUiState> = _uiState.asStateFlow()

    private var chatId: String? = null

    fun init(chatId: String) {
        if (this.chatId == chatId && _uiState.value.info != null) return
        this.chatId = chatId
        _uiState.value = GroupInfoUiState(loading = true)
        loadInfo()
        loadAttachments(AttachmentFilter.IMAGE)
        loadAttachments(AttachmentFilter.VIDEO)
    }

    fun loadInfo() {
        val chatId = chatId ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            runCatching { api.groupInfo(chatId) }
                .onSuccess { info ->
                    store.setChatMuted(chatId, info.mutedUntil)
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        info = info,
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        error = it.message,
                    )
                }
        }
    }

    fun loadAttachments(filter: AttachmentFilter) {
        val chatId = chatId ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loadingAttachments = true)
            runCatching { api.chatAttachments(chatId = chatId, kind = filter.apiKind, limit = 30) }
                .onSuccess { resp ->
                    val current = _uiState.value
                    _uiState.value = current.copy(
                        loadingAttachments = false,
                        attachments = current.attachments + (filter to resp.attachments),
                        attachmentCursors = current.attachmentCursors + (filter to resp.olderCursor),
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        loadingAttachments = false,
                        error = it.message,
                    )
                }
        }
    }

    fun loadMoreAttachments(filter: AttachmentFilter) {
        val chatId = chatId ?: return
        val cursor = _uiState.value.attachmentCursors[filter] ?: return
        if (_uiState.value.loadingMoreAttachments) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loadingMoreAttachments = true)
            runCatching {
                api.chatAttachments(
                    chatId = chatId,
                    kind = filter.apiKind,
                    limit = 30,
                    before = cursor,
                )
            }
                .onSuccess { resp ->
                    val current = _uiState.value
                    val merged = (current.attachments[filter].orEmpty() + resp.attachments)
                        .distinctBy { it.id }
                    _uiState.value = current.copy(
                        loadingMoreAttachments = false,
                        attachments = current.attachments + (filter to merged),
                        attachmentCursors = current.attachmentCursors + (filter to resp.olderCursor),
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        loadingMoreAttachments = false,
                        error = it.message,
                    )
                }
        }
    }

    fun searchMessages(query: String) {
        val chatId = chatId ?: return
        val q = query.trim()
        if (q.length < 2) {
            _uiState.value = _uiState.value.copy(
                searchResults = emptyList(),
                searchError = getString(R.string.group_search_min_chars),
            )
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                searchQuery = q,
                searching = true,
                searchError = null,
            )
            runCatching {
                api.searchMessages(chatId = chatId, q = q, limit = 20, sort = "newest")
            }
                .onSuccess { resp ->
                    _uiState.value = _uiState.value.copy(
                        searching = false,
                        searchResults = resp.messages,
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        searching = false,
                        searchError = it.message,
                    )
                }
        }
    }

    fun clearSearch() {
        _uiState.value = _uiState.value.copy(
            searchQuery = "",
            searchResults = emptyList(),
            searchError = null,
        )
    }

    fun loadSavedMessages() {
        val chatId = chatId ?: return
        if (_uiState.value.loadingSaved) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loadingSaved = true)
            runCatching { api.chatSavedMessages(chatId = chatId, limit = 50) }
                .onSuccess { resp ->
                    _uiState.value = _uiState.value.copy(
                        loadingSaved = false,
                        savedMessages = resp.savedMessages,
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        loadingSaved = false,
                        error = it.message,
                    )
                }
        }
    }

    fun loadMembers(query: String? = null) {
        val chatId = chatId ?: return
        viewModelScope.launch {
            val normalizedQuery = query?.trim().orEmpty()
            _uiState.value = _uiState.value.copy(
                membersLoading = true,
                membersSearchQuery = normalizedQuery,
            )
            runCatching {
                api.members(
                    chatId = chatId,
                    limit = 50,
                    q = normalizedQuery.takeIf { it.isNotBlank() },
                )
            }
                .onSuccess { resp ->
                    _uiState.value = _uiState.value.copy(
                        membersLoading = false,
                        members = resp.members,
                        membersCursor = resp.nextCursor,
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        membersLoading = false,
                        error = it.message,
                    )
                }
        }
    }

    fun loadMoreMembers() {
        val chatId = chatId ?: return
        val cursor = _uiState.value.membersCursor ?: return
        if (_uiState.value.membersLoading || _uiState.value.membersLoadingMore) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(membersLoadingMore = true)
            runCatching {
                api.members(
                    chatId = chatId,
                    limit = 50,
                    after = cursor,
                    q = _uiState.value.membersSearchQuery.takeIf { it.isNotBlank() },
                )
            }
                .onSuccess { resp ->
                    val current = _uiState.value
                    _uiState.value = current.copy(
                        membersLoadingMore = false,
                        members = (current.members + resp.members).distinctBy { it.uid },
                        membersCursor = resp.nextCursor,
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        membersLoadingMore = false,
                        error = it.message,
                    )
                }
        }
    }

    fun toggleMute(onResult: (Boolean) -> Unit) {
        val chatId = chatId ?: return
        val muted = isMutedUntil(_uiState.value.info?.mutedUntil)
        viewModelScope.launch {
            runCatching {
                val next = if (muted) {
                    api.unmuteChat(chatId)
                    null
                } else {
                    api.muteChat(chatId, durationSeconds = null).mutedUntil
                }
                store.setChatMuted(chatId, next)
                _uiState.value = _uiState.value.copy(
                    info = _uiState.value.info?.copy(mutedUntil = next),
                )
            }.onSuccess {
                onResult(true)
            }.onFailure {
                _uiState.value = _uiState.value.copy(error = it.message)
                onResult(false)
            }
        }
    }

    fun leaveGroup(onResult: (Boolean) -> Unit) {
        val chatId = chatId ?: return
        val uid = AppGraph.session.snapshot().me?.uid ?: return
        if (_uiState.value.leaving) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(leaving = true)
            runCatching { api.leaveGroup(chatId, uid) }
                .onSuccess {
                    store.removeChat(chatId)
                    onResult(true)
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        leaving = false,
                        error = it.message,
                    )
                    onResult(false)
                }
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null, searchError = null)
    }

    fun isMuted(): Boolean = isMutedUntil(_uiState.value.info?.mutedUntil)

    private fun isMutedUntil(mutedUntil: String?): Boolean {
        if (mutedUntil.isNullOrBlank()) return false
        return try {
            Instant.parse(mutedUntil).isAfter(Instant.now())
        } catch (e: Exception) {
            false
        }
    }

    private fun getString(id: Int, vararg args: Any?): String =
        getApplication<Application>().getString(id, *args)
}
