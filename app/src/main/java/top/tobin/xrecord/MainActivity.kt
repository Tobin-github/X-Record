package top.tobin.xrecord

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import top.tobin.xrecord.ui.XRecordApp
import top.tobin.xrecord.ui.theme.XRecordTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            XRecordTheme {
                XRecordApp()
            }
        }
    }
}
