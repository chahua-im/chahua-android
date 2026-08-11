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
    }

    fun loadThreads() {
        viewModelScope.launch {
            _loading.value = true
            runCatching { store.setThreads(api.threads(limit = 100).threads) }
                .onSuccess { _error.value = null }
                .onFailure { _error.value = it.message }
            _loading.value = false
        }
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
    }
}
