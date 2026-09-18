package top.tobin.xrecord.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import kotlin.reflect.KClass
import kotlinx.serialization.Serializable
import top.tobin.xrecord.R

@Serializable
data object RecordsRoute

@Serializable
data object ChartsRoute

@Serializable
data object BillsRoute

@Serializable
data object ProfileRoute

/**
 * 底部导航的一级页面。
 *
 * 使用类型安全路由（`@Serializable` 对象）而非字符串，路由参数变更时由编译器发现，
 * 不会出现运行时才暴露的拼写错误。
 */
enum class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    @param:StringRes val labelRes: Int,
    /**
     * 图标使用工程内的矢量资源，而非 material-icons-extended。
     *
     * 该图标库已停止跟随 Compose BOM 发布，且会把上千个图标类打进 APK
     * （实测 debug 包因此多出约 60MB dex）。选中态由 `NavigationBarItem` 的着色区分。
     */
    @param:DrawableRes val iconRes: Int,
) {
    RECORDS(
        route = RecordsRoute,
        routeClass = RecordsRoute::class,
        labelRes = R.string.tab_records,
        iconRes = R.drawable.ic_tab_records,
    ),
    CHARTS(
        route = ChartsRoute,
        routeClass = ChartsRoute::class,
        labelRes = R.string.tab_charts,
        iconRes = R.drawable.ic_tab_charts,
    ),
    BILLS(
        route = BillsRoute,
        routeClass = BillsRoute::class,
        labelRes = R.string.tab_bills,
        iconRes = R.drawable.ic_tab_bills,
    ),
    PROFILE(
        route = ProfileRoute,
        routeClass = ProfileRoute::class,
        labelRes = R.string.tab_profile,
        iconRes = R.drawable.ic_tab_profile,
    ),
}
