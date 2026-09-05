package net.paigu.chahua.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.paigu.chahua.core.AppGraph
import net.paigu.chahua.R
import net.paigu.chahua.data.models.FriendRequestHistoryEntryDto
import net.paigu.chahua.data.models.FriendResponse

class ChatListViewModel(application: Application) : AndroidViewModel(application) {

    private val api = AppGraph.api
    private val store = AppGraph.store

    val chats = store.chats
    val threads = store.threads
    val latencyMs = AppGraph.engine.latencyMs

    private val _archivedChats = MutableStateFlow<List<net.paigu.chahua.data.models.ChatDto>>(emptyList())
    val archivedChats: StateFlow<List<net.paigu.chahua.data.models.ChatDto>> = _archivedChats.asStateFlow()

    private val _archivedThreads = MutableStateFlow<List<net.paigu.chahua.data.models.ThreadDto>>(emptyList())
    val archivedThreads: StateFlow<List<net.paigu.chahua.data.models.ThreadDto>> = _archivedThreads.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _friends = MutableStateFlow<List<FriendResponse>>(emptyList())
    val friends: StateFlow<List<FriendResponse>> = _friends.asStateFlow()

    private val _requests = MutableStateFlow<List<FriendRequestHistoryEntryDto>>(emptyList())
    val requests: StateFlow<List<FriendRequestHistoryEntryDto>> = _requests.asStateFlow()

    private val _pendingCount = MutableStateFlow(0L)
    val pendingCount: StateFlow<Long> = _pendingCount.asStateFlow()

    private val _friendsLoading = MutableStateFlow(false)
    val friendsLoading: StateFlow<Boolean> = _friendsLoading.asStateFlow()

    private val _friendsError = MutableStateFlow<String?>(null)
    val friendsError: StateFlow<String?> = _friendsError.asStateFlow()

    private val _workingRequestId = MutableStateFlow<String?>(null)
    val workingRequestId: StateFlow<String?> = _workingRequestId.asStateFlow()

    private val _requestsLoading = MutableStateFlow(false)
    val requestsLoading: StateFlow<Boolean> = _requestsLoading.asStateFlow()

    fun loadChats() {
        viewModelScope.launch {
            _loading.value = true
            runCatching { store.setChats(api.chats(limit = 100).chats) }
                .onSuccess { _error.value = null }
                .onFailure { _error.value = it.message }
            _loading.value = false
        }
        loadArchived()
    }

    fun loadThreads() {
        viewModelScope.launch {
            _loading.value = true
            runCatching { store.setThreads(api.threads(limit = 100).threads) }
                .onSuccess { _error.value = null }
                .onFailure { _error.value = it.message }
            _loading.value = false
        }
        loadArchived()
    }

    /** “全部”标签：同时加载群组与话题，群组在前、话题在后展示。 */
    fun loadAll() {
        viewModelScope.launch {
            _loading.value = true
            runCatching {
                val chatsResp = api.chats(limit = 100)
                val threadsResp = api.threads(limit = 100)
                store.setChats(chatsResp.chats)
                store.setThreads(threadsResp.threads)
            }
                .onSuccess { _error.value = null }
                .onFailure { _error.value = it.message }
            _loading.value = false
        }
        loadArchived()
    }

    /** 拉取已归档的群聊与话题，用于话题列表顶部的“已归档”入口。*/
    fun loadArchived() {
        viewModelScope.launch {
            runCatching {
                val chatsResp = api.chats(limit = 100, archived = true)
                val threadsResp = api.threads(limit = 100, archived = true)
                _archivedChats.value = chatsResp.chats
                _archivedThreads.value = threadsResp.threads
            }
        }
    }

    // ---- 好友 ----

    /** 加载好友列表与待处理请求数。 */
    fun loadFriends() {
        if (_friendsLoading.value) return
        viewModelScope.launch {
            _friendsLoading.value = true
            _friendsError.value = null
            // 好友页按“全部”页的会话样式展示（最后一条消息、时间、未读），
            // 先同步一次会话列表保证 DM 数据可用；失败不影响好友列表展示。
            runCatching { store.setChats(api.chats(limit = 100).chats) }
            runCatching {
                val friendsResp = api.friends()
                val countResp = api.pendingFriendRequestCount()
                friendsResp.friends to countResp.pendingIncomingCount
            }
                .onSuccess { (list, count) ->
                    _friends.value = list
                    _pendingCount.value = count
                }
                .onFailure {
                    _friendsError.value = it.message
                }
            _friendsLoading.value = false
        }
    }

    /** 加载好友请求历史（双向）。 */
    fun loadFriendRequests() {
        if (_requestsLoading.value) return
        viewModelScope.launch {
            _requestsLoading.value = true
            _friendsError.value = null
            runCatching {
                val requestsResp = api.friendRequests()
                val countResp = api.pendingFriendRequestCount()
                requestsResp.requests to countResp.pendingIncomingCount
            }
                .onSuccess { (list, count) ->
                    _requests.value = list
                    _pendingCount.value = count
                }
                .onFailure {
                    _friendsError.value = it.message
                }
            _requestsLoading.value = false
        }
    }

    /** 接受好友请求，成功后本地刷新状态。 */
    fun acceptRequest(requestId: String) {
        if (_workingRequestId.value != null) return
        viewModelScope.launch {
            _workingRequestId.value = requestId
            runCatching { api.acceptFriendRequest(requestId) }
                .onSuccess {
                    updateRequestAfterDecision(requestId, "accepted")
                    refreshPendingCount()
                }
                .onFailure {
                    _friendsError.value = it.message
                }
            _workingRequestId.value = null
        }
    }

    /** 拒绝好友请求。 */
    fun rejectRequest(requestId: String) {
        if (_workingRequestId.value != null) return
        viewModelScope.launch {
            _workingRequestId.value = requestId
            runCatching { api.rejectFriendRequest(requestId) }
                .onSuccess {
                    updateRequestAfterDecision(requestId, "rejected")
                    refreshPendingCount()
                }
                .onFailure {
                    _friendsError.value = it.message
                }
            _workingRequestId.value = null
        }
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
                        onError(getApplication<Application>().getString(R.string.chat_dm_unavailable))
                    } else {
                        onFound(
                            dm.id,
                            dm.peer?.username?.takeIf { it.isNotBlank() } ?: dm.name ?: dm.id,
                        )
                    }
                }
                .onFailure {
                    onError(it.message ?: getApplication<Application>().getString(R.string.chat_dm_unavailable))
                }
        }
    }

    private fun updateRequestAfterDecision(requestId: String, status: String) {
        _requests.value = _requests.value.map { entry ->
            if (entry.id == requestId) {
                entry.copy(status = status)
            } else {
                entry
            }
        }
    }

    private fun refreshPendingCount() {
        viewModelScope.launch {
            runCatching { api.pendingFriendRequestCount() }
                .onSuccess { _pendingCount.value = it.pendingIncomingCount }
        }
    }

    fun dismissFriendsError() {
        _friendsError.value = null
    }
}
