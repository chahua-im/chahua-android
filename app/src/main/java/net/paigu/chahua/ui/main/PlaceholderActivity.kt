package net.paigu.chahua.ui.main

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import net.paigu.chahua.R
import net.paigu.chahua.core.AppGraph
import net.paigu.chahua.data.AppLocale
import net.paigu.chahua.ui.common.EmptyState
import net.paigu.chahua.ui.theme.ChahuaTheme

/**
 * Activity Embedding 的占位页：未选择聊天时占据右栏，选中聊天后由 ChatActivity 替换。
 */
class PlaceholderActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase, AppGraph.settings.snapshot().language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChahuaTheme {
                PlaceholderScreen()
            }
        }
    }
}

@Composable
private fun PlaceholderScreen() {
    EmptyState(
        text = stringResource(R.string.chat_select_from_list),
        modifier = Modifier.fillMaxSize(),
    )
}
