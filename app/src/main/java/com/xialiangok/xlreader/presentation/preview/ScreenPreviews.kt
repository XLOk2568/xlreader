package com.xialiangok.xlreader.presentation.preview

import androidx.compose.runtime.Composable
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import androidx.wear.compose.ui.tooling.preview.WearPreviewLargeRound
import com.xialiangok.xlreader.data.ReaderPreferences
import com.xialiangok.xlreader.data.epub.EpubBook
import com.xialiangok.xlreader.data.sensor.GestureAction
import com.xialiangok.xlreader.data.sensor.SensorGesture
import com.xialiangok.xlreader.data.sensor.SensorSettings
import com.xialiangok.xlreader.presentation.screens.AboutScreen
import com.xialiangok.xlreader.presentation.screens.ChapterListScreen
import com.xialiangok.xlreader.presentation.screens.ChapterScreen
import com.xialiangok.xlreader.presentation.screens.HomeScreen
import com.xialiangok.xlreader.presentation.screens.LicensesScreen
import com.xialiangok.xlreader.presentation.screens.PermissionScreen
import com.xialiangok.xlreader.presentation.screens.ReaderMenuOverlay
import com.xialiangok.xlreader.presentation.screens.SensorGestureDetailScreen
import com.xialiangok.xlreader.presentation.screens.SensorPermissionScreen
import com.xialiangok.xlreader.presentation.screens.SensorSettingsScreen
import com.xialiangok.xlreader.presentation.screens.SettingsScreen
import com.xialiangok.xlreader.presentation.theme.XlReaderTheme
import java.io.File

/*
 * 预览函数刻意放在 src/main 而不是 src/debug。
 *
 * 放在 src/debug 时，只有在 Build Variants 里选中 debug 变体才看得到预览；
 * 一旦切到 release，整个文件都不在源集里，预览面板就是一片空白。
 * 放在 main 则两个变体都能预览。
 *
 * 正式包并不会因此变大：R8 会把没有被任何代码引用的预览函数与预览工具链一起删掉。
 */

/** 预览用的假书：EpubBook 的构造函数是 internal，同模块里可以直接造。 */
private val previewBook = EpubBook(
    sourceFile = File("/storage/emulated/0/Books/demo.epub"),
    dir = File("/storage/emulated/0/Books/demo"),
    title = "示例书名",
    author = "示例作者",
    chapterTitles = listOf("第一章 起", "第二章 承", "第三章 转"),
    chapterPaths = listOf("OEBPS/c1.xhtml", "OEBPS/c2.xhtml", "OEBPS/c3.xhtml"),
)

@WearPreviewDevices
@Composable
fun HomeScreenPreview() {
    XlReaderTheme {
        HomeScreen(
            dirPath = "/storage/emulated/0",
            onOpenDirectory = {},
            onOpenBook = {},
            onNavigateUp = {},
            onOpenSettings = {},
            onOpenAbout = {},
            onExitApp = {},
        )
    }
}

@WearPreviewDevices
@Composable
fun PermissionScreenPreview() {
    XlReaderTheme {
        PermissionScreen(onOpenSettings = {}, onRecheck = {})
    }
}

@WearPreviewDevices
@Composable
fun ChapterListScreenPreview() {
    XlReaderTheme {
        ChapterListScreen(book = previewBook, onOpenChapter = {}, onBack = {}, onBackToFileList = {})
    }
}

/**
 * 正文页预览。
 *
 * 预览环境里没有真实文件，所以它会停在「正在载入」这一帧；
 * 另外两种状态（载入失败 / 正常正文）复用同一套布局。
 */
@WearPreviewLargeRound
@Composable
fun ChapterScreenPreview() {
    XlReaderTheme {
        ChapterScreen(
            book = previewBook,
            chapterIndex = 0,
            preferences = ReaderPreferences(),
            onPreferencesChange = {},
            onOpenChapter = {},
            onPositionChange = {},
            onSaveProgress = {},
            onOpenCatalog = {},
            onBackToFileList = {},
            onOpenSettings = {},
            menuVisible = false,
            onMenuVisibleChange = {},
        )
    }
}

@WearPreviewDevices
@Composable
fun ReaderMenuPreview() {
    XlReaderTheme {
        ReaderMenuOverlay(
            preferences = ReaderPreferences(),
            onPreferencesChange = {},
            onOpenCatalog = {},
            onBackToFileList = {},
            onOpenSettings = {},
            onDismiss = {},
        )
    }
}

@WearPreviewDevices
@Composable
fun SettingsScreenPreview() {
    XlReaderTheme {
        SettingsScreen(
            preferences = ReaderPreferences(),
            onPreferencesChange = {},
            onOpenSensorSettings = {},
            onOpenDataAdmin = {},
            onBack = {},
        )
    }
}

@WearPreviewDevices
@Composable
fun SensorSettingsScreenPreview() {
    XlReaderTheme {
        SensorSettingsScreen(
            settings = SensorSettings(
                gestures = listOf(
                    SensorGesture(
                        id = "demo-1",
                        name = "翻页",
                        action = GestureAction.ScrollDown,
                        minRoll = 28f,
                        maxRoll = 46f,
                        minPitch = -6f,
                        maxPitch = 4f,
                    ),
                    SensorGesture(
                        id = "demo-2",
                        name = "菜单",
                        action = GestureAction.Tap,
                        minRoll = -12f,
                        maxRoll = -4f,
                        minPitch = 22f,
                        maxPitch = 38f,
                    ),
                ),
            ),
            onChange = {},
            onAddGesture = {},
            onOpenGesture = {},
            onOpenPages = {},
            onBack = {},
        )
    }
}

@WearPreviewDevices
@Composable
fun SensorPermissionScreenPreview() {
    XlReaderTheme {
        SensorPermissionScreen(onOpenSettings = {}, onRecheck = {}, onBack = {})
    }
}

@WearPreviewDevices
@Composable
fun SensorGestureDetailScreenPreview() {
    XlReaderTheme {
        SensorGestureDetailScreen(
            gesture = SensorGesture(
                id = "demo-1",
                name = "翻页",
                action = GestureAction.ScrollDown,
                minRoll = 28f,
                maxRoll = 46f,
                minPitch = -6f,
                maxPitch = 4f,
            ),
            onChange = {},
            onRerecord = {},
            onDelete = {},
            onBack = {},
        )
    }
}

@WearPreviewDevices
@Composable
fun AboutScreenPreview() {
    XlReaderTheme {
        AboutScreen(onOpenLicenses = {}, onBack = {})
    }
}

@WearPreviewDevices
@Composable
fun LicensesScreenPreview() {
    XlReaderTheme {
        LicensesScreen(onBack = {})
    }
}
