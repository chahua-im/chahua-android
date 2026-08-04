package net.paigu.chahua.data

/** 重连/回前台后的全量同步：刷新聊天列表与话题列表。 */
class SyncManager(
    private val api: ChatApi,
    private val store: ChatStore,
) {
    suspend fun syncAll() {
        runCatching { store.setChats(api.chats(limit = 100).chats) }
        runCatching { store.setThreads(api.threads(limit = 100).threads) }
    }
}
