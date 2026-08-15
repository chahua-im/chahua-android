package net.paigu.chahua.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.paigu.chahua.core.AppGraph

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
}
