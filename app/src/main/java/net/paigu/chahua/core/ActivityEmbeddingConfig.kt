package net.paigu.chahua.core

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.window.RequiresWindowSdkExtension
import androidx.window.WindowSdkExtensions
import androidx.window.embedding.ActivityFilter
import androidx.window.embedding.ActivityRule
import androidx.window.embedding.DividerAttributes
import androidx.window.embedding.RuleController
import androidx.window.embedding.SplitAttributes
import androidx.window.embedding.SplitPairFilter
import androidx.window.embedding.SplitPairRule
import androidx.window.embedding.SplitPlaceholderRule
import net.paigu.chahua.ui.chat.ChatActivity
import net.paigu.chahua.ui.main.MainActivity
import net.paigu.chahua.ui.main.PlaceholderActivity
import net.paigu.chahua.ui.media.MediaViewerActivity

/**
 * 平板双栏（Activity Embedding）配置：
 * - MainActivity（聊天列表 + 底部导航）作为左栏
 * - ChatActivity 作为右栏，右侧独占
 * - 分割线可拖动，限制左栏比例在 [MIN_LEFT_RATIO, MAX_LEFT_RATIO] 之间
 */
object ActivityEmbeddingConfig {

    private const val TAG_CHAT_SPLIT = "chat_split"
    private const val TAG_CHAT_PLACEHOLDER = "chat_placeholder"

    /** 与手机/PC 布局保持一致：宽屏（>=840dp）才启用左右分栏。 */
    private const val MIN_WIDTH_DP = 840
    private const val MIN_SMALLEST_WIDTH_DP = 600

    /** 默认左栏宽度约 30%（接近 PC 端固定 360px 的比例）。 */
    private const val DEFAULT_LEFT_RATIO = 0.3f

    /** 拖拽分割线时允许的左栏比例范围。 */
    private const val MIN_LEFT_RATIO = 0.2f
    private const val MAX_LEFT_RATIO = 0.5f

    private const val DIVIDER_WIDTH_DP = 4
    private const val DIVIDER_COLOR = 0xFF9E9E9E.toInt()

    fun configure(context: Context) {
        val main = ComponentName(context, MainActivity::class.java)
        val chat = ComponentName(context, ChatActivity::class.java)

        val chatSplitRule = SplitPairRule.Builder(
            filters = setOf(
                SplitPairFilter(
                    primaryActivityName = main,
                    secondaryActivityName = chat,
                    secondaryActivityIntentAction = null,
                ),
            ),
        )
            .setTag(TAG_CHAT_SPLIT)
            .setMinWidthDp(MIN_WIDTH_DP)
            .setMinSmallestWidthDp(MIN_SMALLEST_WIDTH_DP)
            .setClearTop(true)
            .setDefaultSplitAttributes(createSplitAttributes())
            .build()

        val placeholderRule = SplitPlaceholderRule.Builder(
            filters = setOf(ActivityFilter(main, null)),
            placeholderIntent = Intent(context, PlaceholderActivity::class.java),
        )
            .setTag(TAG_CHAT_PLACEHOLDER)
            .setMinWidthDp(MIN_WIDTH_DP)
            .setMinSmallestWidthDp(MIN_SMALLEST_WIDTH_DP)
            .setDefaultSplitAttributes(createSplitAttributes())
            .build()

        RuleController.getInstance(context).addRule(chatSplitRule)
        RuleController.getInstance(context).addRule(placeholderRule)

        // 媒体查看器从右栏打开时仍应独占整个任务窗口，而不是被限制在聊天窗格内。
        val mediaExpandRule = ActivityRule.Builder(
            filters = setOf(ActivityFilter(ComponentName(context, MediaViewerActivity::class.java), null)),
        )
            .setAlwaysExpand(true)
            .build()
        RuleController.getInstance(context).addRule(mediaExpandRule)
    }

    private fun createSplitAttributes(): SplitAttributes {
        val builder = SplitAttributes.Builder()
            .setSplitType(SplitAttributes.SplitType.ratio(DEFAULT_LEFT_RATIO))
            .setLayoutDirection(SplitAttributes.LayoutDirection.LOCALE)

        // 可拖拽分割线需要 Window SDK extensions >= 6，低版本自动退化为固定比例。
        if (WindowSdkExtensions.getInstance().extensionVersion >= 6) {
            builder.setDividerAttributes(createDraggableDivider())
        }
        return builder.build()
    }

    @RequiresWindowSdkExtension(version = 6)
    private fun createDraggableDivider(): DividerAttributes =
        DividerAttributes.DraggableDividerAttributes.Builder()
            .setWidthDp(DIVIDER_WIDTH_DP)
            .setColor(DIVIDER_COLOR)
            .setDragRange(
                DividerAttributes.DragRange.SplitRatioDragRange(
                    minRatio = MIN_LEFT_RATIO,
                    maxRatio = MAX_LEFT_RATIO,
                ),
            )
            .build()
}
