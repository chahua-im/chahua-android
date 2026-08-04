package net.paigu.chahua.core

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.launch

/** 前后台切换：上报 appState 并触发一次数据同步。 */
class AppForegroundObserver : DefaultLifecycleObserver {
    override fun onStart(owner: LifecycleOwner) {
        AppGraph.engine.appActive = true
        AppGraph.engine.setAppState("active")
        if (AppGraph.session.snapshot().hasSession) {
            AppGraph.scope.launch { AppGraph.syncManager.syncAll() }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        AppGraph.engine.setAppState("inactive")
    }
}
