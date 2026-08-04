package net.paigu.chahua

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import net.paigu.chahua.core.AppGraph
import net.paigu.chahua.core.AppForegroundObserver

class ChatApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(AppForegroundObserver())
    }
}
