package top.tobin.xrecord

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import top.tobin.xrecord.data.preferences.ThemeMode
import top.tobin.xrecord.ui.XRecordApp
import top.tobin.xrecord.ui.feature.settings.AppearanceViewModel
import top.tobin.xrecord.ui.theme.XRecordTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeViewModel: AppearanceViewModel = hiltViewModel()
            val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
            val dynamicColor by themeViewModel.dynamicColor.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()

            XRecordTheme(
                darkTheme = when (themeMode) {
                    ThemeMode.FOLLOW_SYSTEM -> systemDark
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                },
                dynamicColor = dynamicColor,
            ) {
                XRecordApp()
            }
        }
    }
}
