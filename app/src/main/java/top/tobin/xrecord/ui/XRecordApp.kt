package top.tobin.xrecord.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import top.tobin.xrecord.R
import top.tobin.xrecord.data.local.entity.UserEntity
import top.tobin.xrecord.data.repository.SessionState
import top.tobin.xrecord.ui.feature.auth.AuthNavHost
import top.tobin.xrecord.ui.feature.auth.RegisterScreen
import top.tobin.xrecord.ui.feature.auth.SessionViewModel
import top.tobin.xrecord.ui.feature.bills.BillsScreen
import top.tobin.xrecord.ui.feature.accounts.AccountsScreen
import top.tobin.xrecord.ui.feature.categories.CategoriesScreen
import top.tobin.xrecord.ui.feature.charts.ChartsScreen
import top.tobin.xrecord.ui.feature.editor.QuickEntrySheet
import top.tobin.xrecord.ui.feature.editor.TransactionEditorScreen
import top.tobin.xrecord.ui.feature.profile.ProfileScreen
import top.tobin.xrecord.ui.feature.records.RecordsScreen
import top.tobin.xrecord.ui.navigation.BillsRoute
import top.tobin.xrecord.ui.navigation.AccountsRoute
import top.tobin.xrecord.ui.navigation.AddAccountRoute
import top.tobin.xrecord.ui.navigation.AppearanceRoute
import top.tobin.xrecord.ui.navigation.CategoriesRoute
import top.tobin.xrecord.ui.navigation.ChartsRoute
import top.tobin.xrecord.ui.navigation.MainRoute
import top.tobin.xrecord.ui.navigation.ProfileRoute
import top.tobin.xrecord.ui.navigation.RecordsRoute
import top.tobin.xrecord.ui.navigation.TopLevelDestination
import top.tobin.xrecord.ui.navigation.TransactionEditorRoute
import top.tobin.xrecord.ui.feature.settings.AppearanceScreen

@Composable
fun XRecordApp() {
    val sessionViewModel: SessionViewModel = hiltViewModel()
    val sessionState by sessionViewModel.sessionState.collectAsStateWithLifecycle()

    // 登录态是唯一的入口判断依据：登录成功后整个导航树被替换，
    // 未登录界面会连同它的返回栈一起销毁，不存在"按返回键退回登录页"的问题。
    when (val state = sessionState) {
        SessionState.Loading -> LoadingScreen()
        SessionState.LoggedOut -> AuthNavHost()
        // 用账号 id 作为 key：切换账号时整棵主界面树会被重建，
        // 否则记账面板等 ViewModel 会继续持有旧账号的 userId、账户与分类
        is SessionState.LoggedIn -> key(state.user.id) { MainNavHost(user = state.user) }
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

/**
 * 登录后的根导航。
 *
 * 编辑页放在这一层而不是四个 Tab 的导航图里，这样它是真正的全屏页面，
 * 不会出现"编辑流水时底部还挂着导航栏"的别扭效果。
 */
@Composable
private fun MainNavHost(user: UserEntity) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = MainRoute,
        modifier = Modifier.fillMaxSize(),
    ) {
        composable<MainRoute> {
            MainScaffold(
                user = user,
                onOpenTransaction = { transactionId ->
                    navController.navigate(TransactionEditorRoute(transactionId))
                },
                onOpenAccounts = { navController.navigate(AccountsRoute) },
                onOpenCategories = { navController.navigate(CategoriesRoute) },
                onOpenAppearance = { navController.navigate(AppearanceRoute) },
                onAddAccount = { navController.navigate(AddAccountRoute) },
            )
        }
        composable<TransactionEditorRoute> {
            TransactionEditorScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable<AccountsRoute> {
            AccountsScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable<CategoriesRoute> {
            CategoriesScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable<AppearanceRoute> {
            AppearanceScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable<AddAccountRoute> {
            // 复用注册页：注册成功即切到新账号，整棵主界面树会随 user 变化重建
            RegisterScreen(
                onNavigateBack = { navController.popBackStack() },
                showLoginLink = false,
            )
        }
    }
}

@Composable
private fun MainScaffold(
    user: UserEntity,
    onOpenTransaction: (Long) -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenAppearance: () -> Unit,
    onAddAccount: () -> Unit,
) {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = currentBackStackEntry?.destination
    val snackbarHostState = remember { SnackbarHostState() }

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
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = RecordsRoute,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable<RecordsRoute> {
                RecordsScreen(
                    snackbarHostState = snackbarHostState,
                    onOpenTransaction = onOpenTransaction,
                )
            }
            composable<ChartsRoute> { ChartsScreen() }
            composable<BillsRoute> { BillsScreen(snackbarHostState = snackbarHostState) }
            composable<ProfileRoute> {
                ProfileScreen(
                    user = user,
                    snackbarHostState = snackbarHostState,
                    onOpenAccounts = onOpenAccounts,
                    onOpenCategories = onOpenCategories,
                    onOpenAppearance = onOpenAppearance,
                    onAddAccount = onAddAccount,
                )
            }
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
