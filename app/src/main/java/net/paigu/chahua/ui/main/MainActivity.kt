package net.paigu.chahua.ui.main

import android.Manifest
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.FragmentActivity
import androidx.window.embedding.SplitController
import net.paigu.chahua.R
import net.paigu.chahua.core.AppGraph
import net.paigu.chahua.data.AppLocale
import net.paigu.chahua.ui.theme.ChahuaTheme

/**
 * 主框架：底部导航栏（聊天 / 设置），内容区由两个 Fragment 承载。
 * Fragment 容器使用标准 XML 布局，确保初始提交与状态恢复时容器始终可查。
 */
class MainActivity : FragmentActivity() {

    internal var selectedTab by mutableIntStateOf(0)
        private set

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase, AppGraph.settings.snapshot().language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // 三键导航（1920x1080 / 440dpi 等）下，系统导航栏会遮住 XML 底部栏；
        // 显式把底部 inset 作为 padding 交给底栏 ComposeView。
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_bottom_bar)) { view, insets ->
            val bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            view.setPadding(0, 0, 0, bottom)
            insets
        }

        if (savedInstanceState != null) {
            selectedTab = savedInstanceState.getInt(KEY_SELECTED_TAB, 0)
        }

        findViewById<ComposeView>(R.id.main_bottom_bar).setContent {
            ChahuaTheme {
                MainBottomBar(
                    selectedTab = selectedTab,
                    onSelectTab = { selectTab(it) },
                )
            }
        }

        // 容器来自 XML，Activity 恢复时 FragmentManager 会自动还原 Fragment；
        // 首次启动才手动提交初始 Fragment。
        if (savedInstanceState == null) {
            showFragment(selectedTab)
        }
        updateBottomBarVisibility()

        // 返回键规则：设置页先回主页（聊天），主页再次返回才退出 App。
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (selectedTab == 1) {
                        selectTab(0)
                    } else {
                        finish()
                    }
                }
            },
        )

        requestNotificationPermissionIfNeeded()
        AppGraph.startMessaging(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateBottomBarVisibility()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_SELECTED_TAB, selectedTab)
    }

    internal fun selectTab(tab: Int) {
        selectedTab = tab
        showFragment(tab)
        updateBottomBarVisibility()
    }

    private fun showFragment(tab: Int) {
        val fragment = if (tab == 0) ChatFragment() else SettingsFragment()
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    /** 不支持 Activity Embedding 的宽屏设备：聊天页由 Fragment 内双栏接管，隐藏全局底部栏，由左栏自行渲染。 */
    private fun updateBottomBarVisibility() {
        val wideFallback = !isActivityEmbeddingSupported() &&
            resources.configuration.screenWidthDp >= 840
        findViewById<View>(R.id.main_bottom_bar).visibility =
            if (wideFallback) View.GONE else View.VISIBLE
    }

    private fun isActivityEmbeddingSupported(): Boolean =
        SplitController.getInstance(this).splitSupportStatus ==
            SplitController.SplitSupportStatus.SPLIT_AVAILABLE

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        private const val KEY_SELECTED_TAB = "selected_tab"
    }
}
