package com.xialiangok.xlreader.presentation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.xialiangok.xlreader.data.ReaderPreferences
import com.xialiangok.xlreader.data.SettingsStore
import com.xialiangok.xlreader.data.epub.EpubBook
import com.xialiangok.xlreader.data.epub.EpubExtractor
import com.xialiangok.xlreader.data.epub.EpubParser
import com.xialiangok.xlreader.data.epub.ExtractStatus
import com.xialiangok.xlreader.data.epub.ReadHistory
import com.xialiangok.xlreader.data.epub.ReadingPosition
import com.xialiangok.xlreader.data.file.defaultRootPath
import com.xialiangok.xlreader.data.file.hasAllFilesAccess
import com.xialiangok.xlreader.data.file.parentWithinRoot
import com.xialiangok.xlreader.presentation.screens.AboutScreen
import com.xialiangok.xlreader.presentation.screens.CachePromptScreen
import com.xialiangok.xlreader.presentation.screens.ChapterListScreen
import com.xialiangok.xlreader.presentation.screens.ChapterScreen
import com.xialiangok.xlreader.presentation.screens.ExtractingScreen
import com.xialiangok.xlreader.presentation.screens.HomeScreen
import com.xialiangok.xlreader.presentation.screens.LicensesScreen
import com.xialiangok.xlreader.presentation.screens.NoticeScreen
import com.xialiangok.xlreader.presentation.screens.PermissionScreen
import com.xialiangok.xlreader.presentation.screens.SettingsScreen
import com.xialiangok.xlreader.presentation.theme.XlReaderTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 页面路由。手表上页面很少，用一个密封接口 + `when` 比引入导航库更轻。 */
sealed interface Route {
    /** 文件浏览器。当前目录另存在 browserDir，这样切到别的页面再回来不会丢。 */
    data object Browser : Route

    data object Permission : Route
    data object Opening : Route

    /** 打开一本书的过程与结果（解压中 / 询问是否更新缓存 / 章节目录）。 */
    data object Book : Route

    data class Chapter(val index: Int) : Route
    data object Settings : Route
    data object About : Route
    data object Licenses : Route
}

/** 一本书的打开状态。 */
private sealed interface BookState {
    /** 正在读元数据（解压已完成或不需要解压）。 */
    data object Loading : BookState

    /** 正在解压，进度 0f..1f。 */
    data class Extracting(val progress: Float) : BookState

    /** 已有完整解压缓存，等用户决定是否重新解压。 */
    data object AskRefresh : BookState

    data class Failed(val message: String) : BookState
    data class Ready(val book: EpubBook) : BookState
}

/** 打开流程里的一个动作。 */
private enum class BookAction {
    /** 自动：没缓存就解压，有缓存就问要不要更新。 */
    Prepare,

    /** 直接用现有缓存，不重新解压。 */
    KeepCache,

    /** 重新解压（更新缓存）。 */
    RefreshCache,
}

/** 用 token 保证同一本书重复发起同一动作也会重新触发。 */
private data class BookRequest(
    val path: String,
    val action: BookAction,
    val token: Int,
)

/**
 * 应用根组件。
 *
 * @param store       阅读偏好存储。
 * @param incomingUri 其他应用通过 VIEW intent 传来的 epub（可为 file:// 或 content://）。
 */
@Composable
fun XlReaderApp(store: SettingsStore, incomingUri: Uri? = null) {
    // 所有阅读偏好集中在一份数据里：设置页和阅读界面的快捷菜单改的是同一份，
    // 因此在哪儿改都一样，改完两边都生效。
    var preferences by remember { mutableStateOf(store.read()) }
    val updatePreferences: (ReaderPreferences) -> Unit = remember {
        { updated: ReaderPreferences ->
            preferences = updated
            store.save(updated)
        }
    }

    val context = LocalContext.current
    val rootPath = remember { defaultRootPath() }
    var browserDir by remember { mutableStateOf(rootPath) }

    var pendingPath by remember { mutableStateOf<String?>(null) }
    var bookState by remember { mutableStateOf<BookState>(BookState.Loading) }
    var request by remember { mutableStateOf<BookRequest?>(null) }
    var requestToken by remember { mutableIntStateOf(0) }

    // 正文页随时把「现在读到哪一条」报上来。这里刻意不在组合里读它，
    // 所以滚动时的上报不会引起重组，只在离开正文页时用来补一次落盘。
    var lastPosition by remember { mutableStateOf<ReadingPosition?>(null) }
    val reportPosition: (ReadingPosition) -> Unit = remember { { lastPosition = it } }

    // 根组件的作用域在整个应用期间都活着。正文页一离开组合，它自己的协程就被取消，
    // 而「临走前把进度写完」这件事必须活过那次取消，所以落盘用它。
    val appScope = rememberCoroutineScope()

    /** 发起一次打开动作。token 递增，保证 LaunchedEffect 一定会重新跑。 */
    fun requestBook(path: String, action: BookAction) {
        // 换书时把上一次的位置报告清掉，免得刚进新书、正文页还没上报时
        // 拿上一本书的位置写进新书的 history.txt。
        lastPosition = null
        requestToken += 1
        request = BookRequest(path, action, requestToken)
    }

    var route by remember {
        mutableStateOf<Route>(
            when {
                incomingUri != null -> Route.Opening
                hasAllFilesAccess() -> Route.Browser
                else -> Route.Permission
            },
        )
    }

    /**
     * 把「现在读到哪一条」立刻落盘一次。
     *
     * 写 history.txt 的时机一共四处：加载完一章 500ms（仅一次）、单击正文弹出菜单、切换章节、
     * 离开正文页；后三处都调到这里，返回目录 / 返回文件列表 / 系统返回手势也走同一笔，
     * 所以从哪个口子离开都一样。**滚动本身不写盘。**
     *
     * 落盘用根组件的作用域：正文页自己的协程活不过这一次切页（一离开组合就被取消），
     * 最后那一刻的位置可能还没写进 history.txt。
     * 位置必须属于当前这一章：正文页刚进来还没上报时留在 lastPosition 里的
     * 可能是上一章的位置，写回去会把进度倒退回上一章。
     */
    fun saveProgressOnLeave(book: EpubBook, chapterIndex: Int) {
        lastPosition
            ?.takeIf { it.chapter == chapterIndex }
            ?.let { position ->
                appScope.launch(Dispatchers.IO) { ReadHistory.save(book.dir, position) }
            }
    }

    /** 从正文页返回章节目录：先落盘，再切页面。 */
    fun backToCatalog(book: EpubBook, chapterIndex: Int) {
        saveProgressOnLeave(book, chapterIndex)
        route = Route.Book
    }

    /** 从正文页返回文件列表（正文里的按钮与系统返回手势）：同样先落盘。 */
    fun backToFileList(book: EpubBook, chapterIndex: Int) {
        saveProgressOnLeave(book, chapterIndex)
        route = Route.Browser
    }

    /**
     * 解析完直接进正文页。
     *
     * 打开一本书的意图就是接着读，所以不再先停在目录页等用户再点一次；
     * 要看目录、换章，用正文页里的「返回目录」。
     * 解压 / 询问缓存 / 重新解压三条路最后都汇到这里。
     */
    suspend fun openForReading(file: File) {
        val parsed = parseBook(file)
        bookState = parsed
        if (parsed is BookState.Ready) {
            route = Route.Chapter(resumeChapter(parsed.book))
        }
    }

    // 外部传入的 epub：content:// 需要先落到缓存文件才有真实路径可解压。
    LaunchedEffect(incomingUri) {
        val uri = incomingUri ?: return@LaunchedEffect
        val file = withContext(Dispatchers.IO) {
            runCatching { materialize(context, uri) }.getOrNull()
        }
        if (file != null) {
            pendingPath = file.absolutePath
            requestBook(file.absolutePath, BookAction.Prepare)
            route = Route.Book
        } else {
            route = Route.Browser
        }
    }

    // 打开一本书。解压单独成一步，因为要显示百分比、也可能会先问一句是否更新缓存。
    LaunchedEffect(request) {
        val current = request ?: return@LaunchedEffect
        val file = File(current.path)

        when (current.action) {
            // status 与 isUpToDate 都要探磁盘（原 epub 的修改时间/大小、版本记录），
            // 一起放在 IO 线程：以前 isUpToDate 是在主线程上跑的，
            // 光是那几次 file stat 就够让点击到「正在读取」之间卡一下。
            BookAction.Prepare -> {
                val (extractStatus, upToDate) = withContext(Dispatchers.IO) {
                    val status = EpubExtractor.status(file)
                    status to (status == ExtractStatus.Complete && EpubExtractor.isUpToDate(file))
                }

                when (extractStatus) {
                    ExtractStatus.Missing -> {
                        bookState = BookState.Extracting(0f)
                        val error = runExtraction(file, force = false) {
                            bookState = BookState.Extracting(it)
                        }
                        if (error != null) bookState = BookState.Failed(error) else openForReading(file)
                    }

                    // 已经有完整缓存：如果它对应的正是当前这个版本的 epub，
                    // 说明用户之前已经认可过（或刚解压完），直接读；否则才问要不要更新。
                    ExtractStatus.Complete -> if (upToDate) {
                        openForReading(file)
                    } else {
                        BookState.AskRefresh
                    }

                    ExtractStatus.Foreign -> bookState =
                        BookState.Failed(EpubExtractor.foreignDirectoryMessage(file))
                }
            }

            // 用户选「直接阅读」：记下当前版本，表示这个版本的缓存他认了，下次别再问。
            BookAction.KeepCache -> {
                withContext(Dispatchers.IO) { EpubExtractor.writeStamp(file) }
                openForReading(file)
            }

            BookAction.RefreshCache -> {
                bookState = BookState.Extracting(0f)
                val error = runExtraction(file, force = true) {
                    bookState = BookState.Extracting(it)
                }
                if (error != null) bookState = BookState.Failed(error) else openForReading(file)
            }
        }
    }

    XlReaderTheme {
        when (val current = route) {
            Route.Opening -> NoticeScreen(
                title = "正在打开",
                message = "正在读取传入的电子书…",
            )

            Route.Permission -> PermissionScreen(
                onOpenSettings = { openAllFilesAccessSettings(context) },
                onRecheck = {
                    if (hasAllFilesAccess()) route = Route.Browser
                },
            )

            Route.Browser -> HomeScreen(
                dirPath = browserDir,
                onOpenDirectory = { browserDir = it },
                onOpenBook = { path ->
                    pendingPath = path
                    bookState = BookState.Loading
                    requestBook(path, BookAction.Prepare)
                    route = Route.Book
                },
                onNavigateUp = {
                    parentWithinRoot(File(browserDir))?.let { browserDir = it.absolutePath }
                },
                onOpenSettings = { route = Route.Settings },
                onOpenAbout = { route = Route.About },
                // 退出本应用：finishAndRemoveTask 会把整个任务结束并从最近任务列表里移除，
                // 比只 finish 当前 Activity 更接近「退出」的字面意思。
                onExitApp = { context.findActivity()?.finishAndRemoveTask() },
            )

            Route.Book -> {
                val fileName = pendingPath?.let { File(it).name } ?: ""
                when (val state = bookState) {
                    BookState.Loading -> NoticeScreen(
                        title = "正在打开",
                        message = "正在读取…\n$fileName",
                    )

                    is BookState.Extracting -> ExtractingScreen(
                        fileName = fileName,
                        progress = state.progress,
                    )

                    BookState.AskRefresh -> CachePromptScreen(
                        fileName = fileName,
                        onKeep = {
                            pendingPath?.let { requestBook(it, BookAction.KeepCache) }
                        },
                        onRefresh = {
                            pendingPath?.let { requestBook(it, BookAction.RefreshCache) }
                        },
                        onCancel = { route = Route.Browser },
                    )

                    is BookState.Failed -> NoticeScreen(
                        title = "打不开这本书",
                        message = state.message,
                        onAction = { route = Route.Browser },
                    )

                    is BookState.Ready -> ChapterListScreen(
                        book = state.book,
                        onOpenChapter = { route = Route.Chapter(it) },
                        onBack = { route = Route.Browser },
                    )
                }
            }

            is Route.Chapter -> {
                val state = bookState
                if (state is BookState.Ready) {
                    ChapterScreen(
                        book = state.book,
                        chapterIndex = current.index,
                        preferences = preferences,
                        onPreferencesChange = updatePreferences,
                        onOpenChapter = { route = Route.Chapter(it) },
                        onPositionChange = reportPosition,
                        // 正文页点「上一章 / 下一章」或单击弹菜单时，让它立刻记一次进度。
                        onSaveProgress = { saveProgressOnLeave(state.book, current.index) },
                        onBackToList = { backToCatalog(state.book, current.index) },
                        onBackToFileList = { backToFileList(state.book, current.index) },
                    )
                } else {
                    NoticeScreen(
                        title = "正在打开",
                        message = "请稍候…",
                        onAction = { route = Route.Browser },
                    )
                }
            }

            Route.Settings -> SettingsScreen(
                preferences = preferences,
                onPreferencesChange = updatePreferences,
                onBack = { route = Route.Browser },
            )

            Route.About -> AboutScreen(
                onOpenLicenses = { route = Route.Licenses },
                onBack = { route = Route.Browser },
            )

            Route.Licenses -> LicensesScreen(onBack = { route = Route.About })
        }
    }
}

/** 解压；成功返回 null，失败返回给用户看的消息。 */
private suspend fun runExtraction(
    file: File,
    force: Boolean,
    onProgress: (Float) -> Unit,
): String? = try {
    withContext(Dispatchers.IO) {
        EpubExtractor.ensureExtracted(file, force = force, onProgress = onProgress)
    }
    null
} catch (e: Exception) {
    e.message ?: "解压失败"
}

/** 解析元数据与章节结构（不读正文）。 */
private suspend fun parseBook(file: File): BookState = try {
    BookState.Ready(withContext(Dispatchers.IO) { EpubParser.open(file) })
} catch (e: Exception) {
    BookState.Failed(e.message ?: "打不开这本书")
}

/**
 * 直接进正文页时该打开哪一章：有有效进度就接着上次那一章，否则从第一章开始。
 *
 * 章号可能因为换了版本而对不上（`.xlreader_index` 代次号变了），越界就当没有进度 ——
 * 这一步的容错口径和目录页的「继续阅读」卡片保持一致。
 */
private suspend fun resumeChapter(book: EpubBook): Int =
    withContext(Dispatchers.IO) { ReadHistory.load(book.dir) }
        ?.takeIf { it.chapter in 0 until book.chapterCount }
        ?.chapter
        ?: 0

/**
 * 把外部传入的 Uri 变成一个可随机读取的文件。
 *
 * `file://` 直接用；`content://` 拷进 cacheDir —— 后面还要按同一套逻辑解压，
 * 而 ContentResolver 给的是单向流、没有真实路径。
 */
private fun materialize(context: Context, uri: Uri): File? {
    if (uri.scheme == "file") return uri.path?.let(::File)
    val target = File(context.cacheDir, "opened.epub")
    val input = context.contentResolver.openInputStream(uri) ?: return null
    input.use { source ->
        target.outputStream().use { sink -> source.copyTo(sink) }
    }
    return target
}

/**
 * 跳到系统的「所有文件访问」页面，逐级回退以防某些设备没有对应界面。
 *
 * 注意 `LocalContext.current` 拿到的通常是包了一层的 ContextThemeWrapper，
 * 直接写 `context !is Activity` 会误判成「不是 Activity」，从而多加
 * FLAG_ACTIVITY_NEW_TASK —— Wear 的最近任务栈对这个标志很敏感，
 * 所以这里先把包装层剥开再判断。
 */
private fun openAllFilesAccessSettings(context: Context) {
    val host = context.findActivity()
    val packageUri = Uri.fromParts("package", context.packageName, null)
    val candidates = listOf(
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, packageUri),
        Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri),
    )
    for (intent in candidates) {
        val started = if (host != null) {
            runCatching { host.startActivity(intent) }.isSuccess
        } else {
            // 兜底：调用点一定在 Activity 的 composition 内，正常走不到这里。
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }.isSuccess
        }
        if (started) return
    }
}
