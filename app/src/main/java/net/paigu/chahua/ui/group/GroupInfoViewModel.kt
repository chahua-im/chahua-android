package net.paigu.chahua.ui.group

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
import net.paigu.chahua.R
import net.paigu.chahua.core.AppGraph
import net.paigu.chahua.data.models.ChatAttachmentDto
import net.paigu.chahua.data.models.GroupInfoDto
import net.paigu.chahua.data.models.InviteResponse
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
    val saving: Boolean = false,
    val avatarUploading: Boolean = false,
    val invites: List<InviteResponse> = emptyList(),
    val loadingInvites: Boolean = false,
    val creatingInvite: Boolean = false,
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

    /** 更新群名称与简介（管理员）。 */
    fun updateInfo(
        name: String,
        description: String,
        onDone: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val chatId = chatId ?: return
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            onError(getString(R.string.group_name_required))
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(saving = true)
            runCatching {
                api.updateGroupInfo(
                    chatId = chatId,
                    body = net.paigu.chahua.data.models.UpdateGroupBody(
                        name = trimmedName,
                        description = description.trim(),
                    ),
                )
            }
                .onSuccess { info ->
                    store.setChatMuted(chatId, info.mutedUntil)
                    _uiState.value = _uiState.value.copy(saving = false, info = info)
                    onDone()
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(saving = false, error = it.message)
                    onError(it.message ?: getString(R.string.chat_action_failed))
                }
        }
    }

    /** 更换群头像：申请上传地址 -> 上传 -> 关联到群。 */
    fun uploadAvatar(
        uri: Uri,
        onDone: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val chatId = chatId ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(avatarUploading = true)
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    val name = queryDisplayName(resolver, uri) ?: "avatar_${System.currentTimeMillis()}"
                    val contentType = resolver.getType(uri) ?: "image/jpeg"
                    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IllegalStateException(getString(R.string.chat_read_file_failed))
                    Triple(name, contentType, bytes)
                }
            }
            result
                .onSuccess { (name, contentType, bytes) ->
                    val uploadResult = runCatching {
                        val upload = api.groupAvatarUploadUrl(
                            chatId = chatId,
                            filename = name,
                            contentType = contentType,
                            size = bytes.size.toLong(),
                        )
                        api.uploadFile(upload.uploadUrl, upload.uploadHeaders, bytes, contentType)
                        upload.imageId
                    }
                    uploadResult
                        .onSuccess { imageId ->
                            val patchResult = runCatching {
                                api.updateGroupInfo(
                                    chatId = chatId,
                                    body = net.paigu.chahua.data.models.UpdateGroupBody(
                                        avatarImageId = imageId,
                                    ),
                                )
                            }
                            _uiState.value = _uiState.value.copy(avatarUploading = false)
                            patchResult
                                .onSuccess { info ->
                                    _uiState.value = _uiState.value.copy(info = info)
                                    onDone()
                                }
                                .onFailure {
                                    _uiState.value = _uiState.value.copy(error = it.message)
                                    onError(it.message ?: getString(R.string.chat_action_failed))
                                }
                        }
                        .onFailure {
                            _uiState.value = _uiState.value.copy(avatarUploading = false)
                            onError(it.message ?: getString(R.string.chat_action_failed))
                        }
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(avatarUploading = false)
                    onError(it.message ?: getString(R.string.chat_action_failed))
                }
        }
    }

    /** 加载当前群的有效/全部邀请。 */
    fun loadInvites() {
        val chatId = chatId ?: return
        if (_uiState.value.loadingInvites) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loadingInvites = true)
            runCatching { api.invites(groupId = chatId) }
                .onSuccess { resp ->
                    _uiState.value = _uiState.value.copy(
                        loadingInvites = false,
                        invites = resp.invites,
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        loadingInvites = false,
                        error = it.message,
                    )
                }
        }
    }

    fun createInvite(onCreated: (InviteResponse) -> Unit) {
        val chatId = chatId ?: return
        if (_uiState.value.creatingInvite) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(creatingInvite = true)
            runCatching { api.createInvite(chatId = chatId, inviteType = "generic") }
                .onSuccess { invite ->
                    _uiState.value = _uiState.value.copy(
                        creatingInvite = false,
                        invites = listOf(invite) + _uiState.value.invites,
                    )
                    onCreated(invite)
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        creatingInvite = false,
                        error = it.message,
                    )
                }
        }
    }

    fun revokeInvite(inviteId: String) {
        viewModelScope.launch {
            runCatching { api.deleteInvite(inviteId) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        invites = _uiState.value.invites.filterNot { it.id == inviteId },
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(error = it.message)
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

    /** 从成员资料发起私聊：查找与 uid 的既有 DM 会话并跳转。 */
    fun openDmWith(
        uid: Int,
        onFound: (chatId: String, title: String) -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            runCatching {
                store.chats.value.firstOrNull { it.kind == "dm" && it.peer?.uid == uid }
                    ?: api.chats(limit = 100).chats
                        .firstOrNull { it.kind == "dm" && it.peer?.uid == uid }
            }
                .onSuccess { dm ->
                    if (dm == null) {
                        onError(getString(R.string.chat_dm_unavailable))
                    } else {
                        onFound(
                            dm.id,
                            dm.peer?.username?.takeIf { it.isNotBlank() } ?: dm.name ?: dm.id,
                        )
                    }
                }
                .onFailure { onError(it.message ?: getString(R.string.chat_dm_unavailable)) }
        }
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

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? = try {
        resolver.query(
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
}
