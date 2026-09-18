package top.tobin.xrecord.data.repository

/**
 * 新账号的预置数据。
 *
 * `icon` 存的是语义化的图标键而非资源 id：数据库里不该出现 R.drawable 的数值，
 * 否则资源重新排序后历史数据就会指向错误的图标。UI 层负责把键映射成具体矢量图。
 */
internal object PresetData {

    const val DEFAULT_BOOK_NAME = "日常账本"
    const val DEFAULT_ACCOUNT_NAME = "现金"

    data class PresetCategory(
        val name: String,
        val icon: String,
        val color: String,
    )

    val expenseCategories: List<PresetCategory> = listOf(
        PresetCategory("餐饮", "restaurant", "#FF7043"),
        PresetCategory("交通", "directions_bus", "#42A5F5"),
        PresetCategory("购物", "shopping_bag", "#EC407A"),
        PresetCategory("居住", "home", "#8D6E63"),
        PresetCategory("通讯", "phone_iphone", "#26A69A"),
        PresetCategory("娱乐", "sports_esports", "#AB47BC"),
        PresetCategory("医疗", "local_hospital", "#EF5350"),
        PresetCategory("教育", "school", "#5C6BC0"),
        PresetCategory("人情", "card_giftcard", "#FFA726"),
        PresetCategory("旅行", "flight", "#29B6F6"),
        PresetCategory("运动", "fitness_center", "#66BB6A"),
        PresetCategory("其他", "more_horiz", "#78909C"),
    )

    val incomeCategories: List<PresetCategory> = listOf(
        PresetCategory("工资", "payments", "#43A047"),
        PresetCategory("奖金", "emoji_events", "#FBC02D"),
        PresetCategory("兼职", "work", "#00897B"),
        PresetCategory("投资", "trending_up", "#1E88E5"),
        PresetCategory("红包", "redeem", "#E53935"),
        PresetCategory("报销", "receipt_long", "#6D4C41"),
        PresetCategory("其他", "more_horiz", "#78909C"),
    )
}
