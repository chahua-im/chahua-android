package net.paigu.chahua.data

/** 每人每条消息最多可添加的表态数量。 */
const val MAX_REACTIONS_PER_USER_PER_MESSAGE = 5

/** 表态详情弹窗中“前 N 个表情”的 tab 数量（不含 All / More）。 */
const val MAX_REACTION_HEAD_TABS = 8

/** 每条消息最多可出现的不同表态数量。 */
const val MAX_DISTINCT_REACTIONS_PER_MESSAGE = 50

/** 快捷表情条中最多展示的表态数量（设置里钉住的优先）。 */
const val MAX_PINNED_REACTIONS = 5

/** 最近使用表态的保留数量。 */
const val MAX_RECENT_REACTIONS = 30

/** 默认钉住的快捷表态。 */
val DEFAULT_PINNED_REACTIONS: List<String> = listOf("\uD83D\uDC4D")

/** 默认最近使用的表态（用于首次使用、尚未积累历史时）。 */
val DEFAULT_RECENT_REACTIONS: List<String> =
    listOf("\u2764\uFE0F", "\uD83D\uDE02", "\uD83D\uDE2E", "\uD83D\uDE22", "\uD83C\uDF89")

/** 长按菜单“+”打开的更多 emoji 候选。 */
val REACTION_EMOJI_CHOICES: List<String> = listOf(
    "\uD83D\uDC4D", "\uD83D\uDC4E", "\u2764\uFE0F", "\uD83D\uDE02", "\uD83D\uDE2E",
    "\uD83D\uDE22", "\uD83C\uDF89", "\uD83D\uDD25",
    "\uD83D\uDE0D", "\uD83D\uDE21", "\uD83D\uDC4F", "\uD83D\uDE4F", "\uD83E\uDD23",
    "\uD83D\uDE05", "\uD83D\uDE2D", "\uD83D\uDE31",
    "\uD83E\uDD14", "\uD83D\uDE44", "\uD83D\uDE34", "\uD83E\uDD2F", "\uD83E\uDD73",
    "\uD83D\uDE0E", "\uD83E\uDD17", "\uD83D\uDE07",
    "\uD83D\uDCAF", "\uD83D\uDCAA", "\uD83D\uDC40", "\uD83D\uDE4C", "\uD83E\uDD1D",
    "\uD83D\uDC94", "\u2728", "\u2B50",
    "\uD83C\uDF39", "\uD83C\uDF40", "\uD83C\uDF82", "\uD83C\uDF7A", "\u2615",
    "\uD83D\uDC36", "\uD83D\uDC31", "\uD83D\uDE80",
    "\u26BD", "\uD83C\uDFC0", "\uD83C\uDFB5", "\uD83D\uDCF7", "\uD83D\uDCA1",
    "\uD83C\uDD97", "\u2705", "\u274C",
)
