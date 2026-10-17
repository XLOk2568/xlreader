package com.xialiangok.xlreader.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.xialiangok.xlreader.data.SettingsStore
import com.xialiangok.xlreader.data.appendAppLog

/** 应用唯一 Activity，承载全部 Compose 界面。 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // 使用纯黑的启动画面：与主界面背景一致，启动过程不会有亮色闪一下。
        installSplashScreen()
        super.onCreate(savedInstanceState)
        installCrashLogger()

        val store = SettingsStore(this)
        // 从文件管理器或别的应用点开 epub 时，系统会带 ACTION_VIEW 进来。
        val incomingUri = intent
            ?.takeIf { it.action == Intent.ACTION_VIEW }
            ?.data

        setContent {
            XlReaderApp(store = store, incomingUri = incomingUri)
        }
    }

    /**
     * 把未捕获异常的堆栈写进 data 目录的 log.txt, 再交给系统原来的处理.
     *
     * 手表上没法连 adb 看 logcat, 闪退时只能靠这个文件知道崩在哪; 只记录不接管,
     * 进程照样会被系统杀掉, 不会把崩溃变成「卡住不动」.
     */
    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { appendAppLog(applicationContext, "未捕获异常 thread=${thread.name}", throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }
}
