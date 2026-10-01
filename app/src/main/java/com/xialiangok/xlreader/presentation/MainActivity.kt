package com.xialiangok.xlreader.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.xialiangok.xlreader.data.SettingsStore

/** 应用唯一 Activity，承载全部 Compose 界面。 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // 使用纯黑的启动画面：与主界面背景一致，启动过程不会有亮色闪一下。
        installSplashScreen()
        super.onCreate(savedInstanceState)

        val store = SettingsStore(this)
        // 从文件管理器或别的应用点开 epub 时，系统会带 ACTION_VIEW 进来。
        val incomingUri = intent
            ?.takeIf { it.action == Intent.ACTION_VIEW }
            ?.data

        setContent {
            XlReaderApp(store = store, incomingUri = incomingUri)
        }
    }
}
