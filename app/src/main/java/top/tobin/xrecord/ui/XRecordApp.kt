package top.tobin.xrecord.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import top.tobin.xrecord.R
import top.tobin.xrecord.ui.feature.bills.BillsScreen
import top.tobin.xrecord.ui.feature.charts.ChartsScreen
import top.tobin.xrecord.ui.feature.editor.QuickEntrySheet
import top.tobin.xrecord.ui.feature.profile.ProfileScreen
import top.tobin.xrecord.ui.feature.records.RecordsScreen
import top.tobin.xrecord.ui.navigation.BillsRoute
import top.tobin.xrecord.ui.navigation.ChartsRoute
import top.tobin.xrecord.ui.navigation.ProfileRoute
import top.tobin.xrecord.ui.navigation.RecordsRoute
import top.tobin.xrecord.ui.navigation.TopLevelDestination

@Composable
fun XRecordApp() {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = currentBackStackEntry?.destination

    var showQuickEntry by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                TopLevelDestination.entries.forEach { destination ->
                    val selected = currentDestination
                        ?.hierarchy
                        ?.any { it.hasRoute(destination.routeClass) } == true

                    NavigationBarItem(
                        selected = selected,
                        onClick = { navController.navigateToTopLevel(destination) },
                        icon = {
                            Icon(
                                painter = painterResource(destination.iconRes),
                                contentDescription = null,
                            )
                        },
                        label = { Text(text = stringResource(destination.labelRes)) },
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showQuickEntry = true }) {
                Icon(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = stringResource(R.string.action_add_transaction),
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = RecordsRoute,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable<RecordsRoute> { RecordsScreen() }
            composable<ChartsRoute> { ChartsScreen() }
            composable<BillsRoute> { BillsScreen() }
            composable<ProfileRoute> { ProfileScreen() }
        }
    }

    if (showQuickEntry) {
        QuickEntrySheet(onDismiss = { showQuickEntry = false })
    }
}

/**
 * 切换一级页面。
 *
 * `saveState` / `restoreState` 保证四个 Tab 各自保留滚动位置与筛选状态；
 * `launchSingleTop` 避免同一 Tab 被反复压栈。
 */
private fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
