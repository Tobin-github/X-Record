package top.tobin.xrecord.ui.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import top.tobin.xrecord.ui.navigation.LoginRoute
import top.tobin.xrecord.ui.navigation.RegisterRoute

/** 未登录时的导航图。登录成功后由根导航整体切换到主界面，不在此处做跳转。 */
@Composable
fun AuthNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = LoginRoute,
        modifier = modifier,
    ) {
        composable<LoginRoute> {
            LoginScreen(
                onNavigateToRegister = {
                    navController.navigate(RegisterRoute) { launchSingleTop = true }
                },
            )
        }
        composable<RegisterRoute> {
            RegisterScreen(onNavigateToLogin = { navController.popBackStack() })
        }
    }
}
