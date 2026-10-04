package com.xialiangok.xlreader.presentation

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.xialiangok.xlreader.data.ReaderPreferences
import com.xialiangok.xlreader.data.SettingsStore
import com.xialiangok.xlreader.data.epub.EpubBook
import com.xialiangok.xlreader.data.epub.EpubExtractor
import com.xialiangok.xlreader.data.epub.EpubParser
import com.xialiangok.xlreader.data.epub.ExtractStatus
import com.xialiangok.xlreader.data.epub.ReadHistory
import com.xialiangok.xlreader.data.epub.ReadingPosition
import com.xialiangok.xlreader.data.file.defaultRootPath
import com.xialiangok.xlreader.data.file.appDataDir
import com.xialiangok.xlreader.data.file.hasAllFilesAccess
import com.xialiangok.xlreader.data.file.parentWithinRoot
import com.xialiangok.xlreader.data.file.writeLatestMarker
import com.xialiangok.xlreader.data.sensor.GestureAction
import com.xialiangok.xlreader.data.sensor.GesturePage
import com.xialiangok.xlreader.data.sensor.SensorGesture
import com.xialiangok.xlreader.data.sensor.SensorSettings
import com.xialiangok.xlreader.data.sensor.SensorStore
import com.xialiangok.xlreader.data.sensor.TiltGestureDetector
import com.xialiangok.xlreader.data.sensor.hasAccelerometer
import com.xialiangok.xlreader.presentation.screens.AboutScreen
import com.xialiangok.xlreader.presentation.screens.CachePromptScreen
import com.xialiangok.xlreader.presentation.screens.ChapterListScreen
import com.xialiangok.xlreader.presentation.screens.ChapterListScreenNumber
import com.xialiangok.xlreader.presentation.screens.ChapterScreen
import com.xialiangok.xlreader.presentation.screens.ExtractingScreen
import com.xialiangok.xlreader.presentation.screens.HomeScreen
import com.xialiangok.xlreader.presentation.screens.LicensesScreen
import com.xialiangok.xlreader.presentation.screens.NoticeScreen
import com.xialiangok.xlreader.presentation.screens.PermissionScreen
import com.xialiangok.xlreader.presentation.screens.SensorGestureDetailScreen
import com.xialiangok.xlreader.presentation.screens.SensorGestureRecordScreen
import com.xialiangok.xlreader.presentation.screens.SensorPagesScreen
import com.xialiangok.xlreader.presentation.screens.SensorPermissionScreen
import com.xialiangok.xlreader.presentation.screens.SensorSettingsScreen
import com.xialiangok.xlreader.presentation.screens.SettingsDataAdminScreen
import com.xialiangok.xlreader.presentation.screens.SettingsImportScreen
import com.xialiangok.xlreader.presentation.screens.SettingsScreen
import com.xialiangok.xlreader.presentation.theme.XlReaderTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** 页面路由。手表上页面很少，用一个密封接口 + `when` 比引入导航库更轻。 */
sealed interface Route {
    /** 文件浏览器。当前目录另存在 browserDir，这样切到别的页面再回来不会丢。 */
    data object Browser : Route

    data object Permission : Route
    data object Opening : Route

    /** 打开一本书的过程与结果（解压中 / 询问是否更新缓存 / 章节目录）。 */
    data object Book : Route

    /** 目录页顶部「数字跳转章节」按钮打开的数字跳章页；返回只是把这个附页关掉、回到目录。 */
    data object CatalogNumber : Route

    data class Chapter(val index: Int) : Route
    data object Settings : Route
    data object About : Route
    data object Licenses : Route

    /** 体感手势设置主页。 */
    data object SensorSettings : Route

    /** 选择在哪些页面启用手势。 */
    data object SensorPages : Route

    /** 单条手势的详情（改名 / 动作 / 速度 / 重录 / 删除）。 */
    data class SensorGestureEdit(val id: String) : Route

    /** 录制一条手势；[id] 为空表示新增，否则是重录已有的那一条。 */
    data class SensorRecord(val id: String?) : Route

    /** data 目录管理（浏览 / 删除 / 复制 / 粘贴 / 导入导出设置）。 */
    data object DataAdmin : Route

    /** 从用户主文件目录里挑一个 zip 导入设置。 */
    data object SettingsImport : Route
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

    /**
     * 更新缓存：只把变了 / 新增的条目补解压进现有目录，并清掉新版里已经没有的文件。
     *
     * 刻意不整本重新解压：原书更新一般只动了目录和少数章节，整本重来在手表上要等很久。
     */
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
    // 上次退出时停在哪个目录就从哪儿继续（目录被删了、或路径不在存储内就退回根目录）。
    var browserDir by remember {
        mutableStateOf(
            store.readBrowserDir()
                ?.takeIf { it.startsWith(rootPath) && File(it).isDirectory }
                ?: rootPath,
        )
    }

    /** 换目录：内存里换掉的同时立刻记盘，切到别的页面或直接退出应用都不会丢这个位置。 */
    fun setBrowserDir(path: String) {
        browserDir = path
        store.saveBrowserDir(path)
    }

    /**
     * 上次打开的那本 epub 的路径，启动时读一次就够。
     *
     * 它和 [browserDir] 是两码事：目录照旧由上次浏览的位置决定，这份路径只用来给文件列表
     * 做启动定位（和书并排的 `xlrLatest.txt` 万一被删了，退回定位到这本书上）。
     */
    val lastBookPath = remember { store.readLastBookPath() }

    /** 本次启动后是否还没做过「最近打开」的定位；文件列表第一次进来时用一次，之后置 false。 */
    var locateOnLaunch by remember { mutableStateOf(true) }

    var pendingPath by remember { mutableStateOf<String?>(null) }
    var bookState by remember { mutableStateOf<BookState>(BookState.Loading) }

    /**
     * 从正文页「打开目录」进来时所在的那一章；目录页里的返回就是回到它。
     *
     * 目录是「打开」而不是「返回」：在目录里退一步只是关掉目录，不该一路退出这本书
     * （那等于把书关了、落到文件列表上）。要回文件列表得用目录页底部的那个按钮。
     */
    var catalogReturnChapter by remember { mutableStateOf<Int?>(null) }

    /**
     * 从正文页的阅读菜单打开设置页时所在的那一章；设置页里的返回就是回到它。
     *
     * 和目录同理：设置是「打开」而不是「返回」，在设置里退一步不该一路退出这本书
     * （那等于把书关了、落到文件列表上）。从文件列表进的设置页这里为 null，返回就回文件列表。
     */
    var settingsReturnChapter by remember { mutableStateOf<Int?>(null) }

    /**
     * 阅读界面的快捷菜单是不是开着。
     *
     * 状态放在根组件而不是正文页里：从菜单「打开目录」时**不清掉它**，
     * 于是在目录里返回时回到的还是那个菜单，而不是直接落回正文；
     * 从正文底部「打开目录」进去时它本来是关着的，返回也就直接回正文。
     */
    var readerMenuVisible by remember { mutableStateOf(false) }
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
        // 上一本书的阅读菜单状态（从菜单进目录、留在目录里返回）同样不能带过来。
        readerMenuVisible = false
        requestToken += 1
        request = BookRequest(path, action, requestToken)
    }

    var route by remember {
        mutableStateOf(
            when {
                incomingUri != null -> Route.Opening
                hasAllFilesAccess() -> Route.Browser
                else -> Route.Permission
            },
        )
    }

    // 体感手势是另一份设置（手势列表 / 检测间隔 / 启用页面），存在自己的 SharedPreferences 里，
    // 与阅读排版那份互不影响。
    val sensorStore = remember(context) { SensorStore(context) }
    var sensorSettings by remember { mutableStateOf(sensorStore.read()) }
    val updateSensorSettings: (SensorSettings) -> Unit = remember {
        { updated: SensorSettings ->
            sensorSettings = updated
            sensorStore.save(updated)
        }
    }

    // 加速度计读不到（设备没有 / 系统或 ROM 关掉了）时，先给一页同款授权引导，
    // 和「所有文件访问」那一页的做法一致；用户从系统设置回来后点「重新检查」再放行。
    var sensorReady by remember(context) { mutableStateOf(hasAccelerometer(context)) }

    // 页面用 BindGestureActions 把自己能响应的动作登记到这里，根组件在手势命中时照着做。
    val gestureHolder = remember { GestureActionHolder() }

    /**
     * 把一个命中的手势翻译成当前页面上的动作。
     *
     * 「退出」是根组件自己就能做的；其余三种都落在当前页面上 —— 页面没登记这个动作
     * （例如列表页没有「返回」的对象）就什么都不做，不会串到别的页面上去。
     */
    fun performGesture(gesture: SensorGesture) {
        when (gesture.action) {
            GestureAction.Exit -> context.findActivity()?.finishAndRemoveTask()
            GestureAction.Tap -> gestureHolder.onTap?.invoke()
            GestureAction.Back -> gestureHolder.onBack?.invoke()
            GestureAction.ScrollDown -> gestureHolder.onScrollBy?.invoke(gesture.speed)
            GestureAction.ScrollUp -> gestureHolder.onScrollBy?.invoke(-gesture.speed)
        }
    }

    // 手势只在三个内容页里有意义，别的页面（设置、关于、录制中…）一律不检测。
    // 数字跳章页算在目录里：它是目录页自动弹出来的附页，在那儿手势不该突然失灵。
    val gesturePage = when (route) {
        Route.Browser -> GesturePage.FileList
        Route.Book, Route.CatalogNumber -> GesturePage.Catalog
        is Route.Chapter -> GesturePage.Reader
        else -> null
    }
    // 当前页面此刻要检测的手势：总开关、启用页面、单个手势开关三个条件都满足才留下来。
    val activeGestures = remember(sensorSettings, gesturePage) {
        gesturePage?.let { sensorSettings.activeOn(it) } ?: emptyList()
    }

    val detector = remember(context) { TiltGestureDetector(context) }
    val gestureHandler = rememberUpdatedState<(SensorGesture) -> Unit> { performGesture(it) }
    // 传感器监听只注册一次，回调里读的永远是「最新那份」动作分发。
    SideEffect { detector.onTrigger = { gesture -> gestureHandler.value(gesture) } }

    // 应用有没有在前台。退到后台/表盘息屏（ON_PAUSE）时**立刻停检测**：
    // 用户看不见的时候不该还在采样耗电，更不该在后台把手势动作执行出来
    // （尤其是「退出」那种会 finishAndRemoveTask 的动作）。
    val lifecycleOwner = remember(context) { context.findActivity() as? LifecycleOwner }
    var resumed by remember(lifecycleOwner) {
        mutableStateOf(
            lifecycleOwner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) ?: true,
        )
    }
    DisposableEffect(lifecycleOwner) {
        val owner = lifecycleOwner ?: return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> resumed = true
                Lifecycle.Event.ON_PAUSE -> resumed = false
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    // 需要检测就注册传感器，不需要就立刻注销：这就是「非启用任何手势的页面立刻停止检测任务」，
    // 也是总开关关掉、应用退到后台时的行为。
    // 间隔一变也要重新注册（采样率是注册时定下的）。
    DisposableEffect(activeGestures, sensorSettings.intervalMs, resumed) {
        if (resumed && activeGestures.isNotEmpty()) {
            // gestureMode 为 null 表示「只跑第一个命中的」；多手势那两种方式由设置页决定。
            detector.start(activeGestures, sensorSettings.intervalMs, sensorSettings.gestureMode)
        } else {
            detector.stop()
        }
        onDispose { detector.stop() }
    }

    // 进入启用了手势的页面时提示一句：检测任务真的跑起来了，也就意味着在耗电。
    LaunchedEffect(gesturePage, activeGestures.isNotEmpty()) {
        if (gesturePage == null || activeGestures.isEmpty()) return@LaunchedEffect
        Toast.makeText(context, "手势检测任务已启用（${gesturePage.label}）", Toast.LENGTH_SHORT).show()
    }

    /**
     * 把「现在读到哪一条」立刻落盘一次。
     *
     * 写 history.txt 的时机一共四处：加载完一章 500ms（仅一次）、单击正文弹出菜单、切换章节、
     * 离开正文页；后三处都调到这里，打开目录 / 返回文件列表 / 系统返回手势也走同一笔，
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

    /**
     * 记下「最近打开的这本 epub」。
     *
     * 两件事一起做：在 epub 所在目录里写一份 `xlrLatest.txt`（内容就是它的**文件名**，
     * 每次整文件覆写 —— 启动时靠它按文件名认出并定位这本 epub），
     * 再把完整路径记进本地设置：标记被删掉或挪走时，启动定位还能退回这本书上。
     *
     * 和阅读进度一样用根组件的作用域落盘：离开正文页时页面自己的协程已经被取消，
     * 但这一笔写完才算真的「离开这本书」。
     */
    fun rememberLatestBook(source: File) {
        store.saveLastBookPath(source.absolutePath)
        appScope.launch(Dispatchers.IO) { writeLatestMarker(source) }
    }

    /** 从正文页打开章节目录：先落盘，再切页面，并记下是从哪一章进来的。 */
    fun openCatalog(book: EpubBook, chapterIndex: Int) {
        saveProgressOnLeave(book, chapterIndex)
        catalogReturnChapter = chapterIndex
        route = Route.Book
    }

    /**
     * 从目录页返回：回到打开目录时所在的那一章（正常走不到「没有来路」那一步）。
     *
     * 这里**不动** [readerMenuVisible]：若是从阅读菜单里进的目录，它还是 true，
     * 于是返回时正文页上的那个菜单重新出现，而不是直接落回正文。
     */
    fun closeCatalog() {
        route = catalogReturnChapter?.let { Route.Chapter(it) } ?: Route.Browser
    }

    /** 从正文页的阅读菜单打开设置页：先落盘，返回时回到那一章（不是文件列表）。 */
    fun openSettingsFromReader(book: EpubBook, chapterIndex: Int) {
        saveProgressOnLeave(book, chapterIndex)
        settingsReturnChapter = chapterIndex
        route = Route.Settings
    }

    /** 从正文页返回文件列表（正文里的按钮与系统返回手势）：同样先落盘。 */
    fun backToFileList(book: EpubBook, chapterIndex: Int) {
        saveProgressOnLeave(book, chapterIndex)
        // 离开正文页 = 离开这本书：把「最近打开」的标记写到它旁边。
        rememberLatestBook(book.sourceFile)
        // 已经离开正文页了，菜单状态留着会让下一本书一进来就弹菜单。
        readerMenuVisible = false
        route = Route.Browser
    }

    /**
     * 解析完直接进正文页。
     *
     * 打开一本书的意图就是接着读，所以不再先停在目录页等用户再点一次；
     * 要看目录、换章，用正文页里的「打开目录」（打开后返回只是关掉目录，仍是这本书）。
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
                        // 缓存还在，但原 epub 已经换过版本了：问一句要不要把缓存更新到新版。
                        // （更新走增量解压，只补变了的条目，见 BookAction.RefreshCache。）
                        bookState = BookState.AskRefresh
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
                val error = runIncrementalUpdate(file) {
                    bookState = BookState.Extracting(it)
                }
                if (error != null) bookState = BookState.Failed(error) else openForReading(file)
            }
        }
    }

    XlReaderTheme {
        // 页面在这里登记自己支持的手势动作（BindGestureActions），根组件负责分发。
        CompositionLocalProvider(LocalGestureActions provides gestureHolder) {
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
                    lastBookPath = lastBookPath,
                    autoLocate = locateOnLaunch,
                    onAutoLocated = { locateOnLaunch = false },
                    onOpenDirectory = { setBrowserDir(it) },
                    onOpenBook = { path ->
                        pendingPath = path
                        // 换书：上一本书「打开目录时在第几章」不能带到这一本上来。
                        catalogReturnChapter = null
                        bookState = BookState.Loading
                        requestBook(path, BookAction.Prepare)
                        route = Route.Book
                    },
                    onNavigateUp = {
                        parentWithinRoot(File(browserDir))?.let { setBrowserDir(it.absolutePath) }
                    },
                    // 从文件列表进设置：返回就回文件列表（把阅览器那条来路清掉）。
                    onOpenSettings = {
                        settingsReturnChapter = null
                        route = Route.Settings
                    },
                    onOpenAbout = { route = Route.About },
                    // 退出本应用：finishAndRemoveTask 会把整个任务结束并从最近任务列表里移除，
                    // 比只 finish 当前 Activity 更接近「退出」的字面意思。
                    onExitApp = {
                        store.saveBrowserDir(browserDir)
                        context.findActivity()?.finishAndRemoveTask()
                    },
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
                            // 在目录里直接挑了别的章节：这是「打开某一章」，不是「关掉目录」，
                            // 所以菜单状态一并清掉，别让菜单莫名其妙地弹出来。
                            onOpenChapter = {
                                readerMenuVisible = false
                                route = Route.Chapter(it)
                            },
                            // 目录是「打开」的：返回 = 关掉目录、回到进来时那一章，不退到文件列表。
                            // 从阅读菜单进来的话，readerMenuVisible 还是 true，于是回到那个菜单。
                            onBack = { closeCatalog() },
                            onBackToFileList = {
                                // 从目录页直接离开这本书也算「最近打开」，和正文页那条路一致。
                                rememberLatestBook(state.book.sourceFile)
                                readerMenuVisible = false
                                route = Route.Browser
                            },
                            // 顶部固定的「数字跳转章节」按钮：打开数字跳章页（那一页的返回只是回到这一页）。
                            onOpenNumber = { route = Route.CatalogNumber },
                        )
                    }
                }

                Route.CatalogNumber -> {
                    val state = bookState
                    if (state is BookState.Ready) {
                        ChapterListScreenNumber(
                            book = state.book,
                            // 和目录页里点某一章是同一条路：清掉菜单状态、直接进正文。
                            onOpenChapter = {
                                readerMenuVisible = false
                                route = Route.Chapter(it)
                            },
                            // 返回 = 关掉这个附页、回到目录。
                            onBack = { route = Route.Book },
                        )
                    } else {
                        NoticeScreen(
                            title = "正在打开",
                            message = "请稍候…",
                            onAction = { route = Route.Browser },
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
                            onOpenCatalog = { openCatalog(state.book, current.index) },
                            onBackToFileList = { backToFileList(state.book, current.index) },
                            onOpenSettings = { openSettingsFromReader(state.book, current.index) },
                            menuVisible = readerMenuVisible,
                            onMenuVisibleChange = { readerMenuVisible = it },
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
                    onOpenSensorSettings = { route = Route.SensorSettings },
                    onOpenDataAdmin = { route = Route.DataAdmin },
                    // 从阅读菜单进来的（settingsReturnChapter 有值）回到那一章，
                    // 从文件列表进来的回文件列表。
                    onBack = {
                        route = settingsReturnChapter?.let { Route.Chapter(it) } ?: Route.Browser
                        settingsReturnChapter = null
                    },
                )

                Route.DataAdmin -> SettingsDataAdminScreen(
                    dataDirPath = remember { appDataDir(context).absolutePath },
                    onOpenImport = { route = Route.SettingsImport },
                    onBack = { route = Route.Settings },
                )

                Route.SettingsImport -> SettingsImportScreen(
                    sourceDirPath = rootPath,
                    // 导入是覆盖：内存里那份 SharedPreferences 已经由导入逻辑写回新值，
                    // 这里重新读一次，界面立刻换成导入后的设置。
                    onImported = {
                        preferences = store.read()
                        sensorSettings = sensorStore.read()
                        route = Route.DataAdmin
                    },
                    onBack = { route = Route.DataAdmin },
                )

                Route.SensorSettings -> if (sensorReady) {
                    SensorSettingsScreen(
                        settings = sensorSettings,
                        onChange = updateSensorSettings,
                        onAddGesture = { route = Route.SensorRecord(null) },
                        onOpenGesture = { route = Route.SensorGestureEdit(it) },
                        onOpenPages = { route = Route.SensorPages },
                        onBack = { route = Route.Settings },
                    )
                } else {
                    SensorPermissionScreen(
                        onOpenSettings = { openAppSettings(context) },
                        onRecheck = { sensorReady = hasAccelerometer(context) },
                        onBack = { route = Route.Settings },
                    )
                }

                Route.SensorPages -> SensorPagesScreen(
                    settings = sensorSettings,
                    onChange = updateSensorSettings,
                    onBack = { route = Route.SensorSettings },
                )

                is Route.SensorGestureEdit -> {
                    val gesture = sensorSettings.gestures.firstOrNull { it.id == current.id }
                    if (gesture == null) {
                        // 删除之后又退回来时的兜底：不显示一个指向空气的详情页。
                        NoticeScreen(
                            title = "手势不在了",
                            message = "这条手势已经被删掉了。",
                            onAction = { route = Route.SensorSettings },
                        )
                    } else {
                        SensorGestureDetailScreen(
                            gesture = gesture,
                            onChange = { updated ->
                                updateSensorSettings(
                                    sensorSettings.copy(
                                        gestures = sensorSettings.gestures.map {
                                            if (it.id == updated.id) updated else it
                                        },
                                    ),
                                )
                            },
                            onRerecord = { route = Route.SensorRecord(gesture.id) },
                            onDelete = {
                                updateSensorSettings(
                                    sensorSettings.copy(
                                        gestures = sensorSettings.gestures
                                            .filterNot { it.id == gesture.id },
                                    ),
                                )
                                route = Route.SensorSettings
                            },
                            onBack = { route = Route.SensorSettings },
                        )
                    }
                }

                is Route.SensorRecord -> SensorGestureRecordScreen(
                    intervalMs = sensorSettings.intervalMs,
                    // 取消：新增的回手势列表，重录的回手势详情。
                    onCancel = {
                        route = current.id
                            ?.let { Route.SensorGestureEdit(it) }
                            ?: Route.SensorSettings
                    },
                    onRecorded = { range ->
                        val existing = current.id
                            ?.let { id -> sensorSettings.gestures.firstOrNull { it.id == id } }
                        val target = (
                            existing ?: SensorGesture(
                                id = UUID.randomUUID().toString(),
                                name = "手势 ${sensorSettings.gestures.size + 1}",
                            )
                            ).copy(
                            minRoll = range.minRoll,
                            maxRoll = range.maxRoll,
                            minPitch = range.minPitch,
                            maxPitch = range.maxPitch,
                        )
                        updateSensorSettings(
                            sensorSettings.copy(
                                gestures = if (existing == null) {
                                    sensorSettings.gestures + target
                                } else {
                                    sensorSettings.gestures.map {
                                        if (it.id == target.id) target else it
                                    }
                                },
                            ),
                        )
                        // 录完直接进详情：名字默认是「手势 N」，动作默认「单击」，让用户自己改。
                        route = Route.SensorGestureEdit(target.id)
                    },
                )

                Route.About -> AboutScreen(
                    onOpenLicenses = { route = Route.Licenses },
                    onBack = { route = Route.Browser },
                )

                Route.Licenses -> LicensesScreen(onBack = { route = Route.About })
            }
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

/** 增量更新缓存（只解压变了的条目）；成功返回 null，失败返回给用户看的消息。 */
private suspend fun runIncrementalUpdate(
    file: File,
    onProgress: (Float) -> Unit,
): String? = try {
    withContext(Dispatchers.IO) {
        EpubExtractor.updateExtracted(file, onProgress = onProgress)
    }
    null
} catch (e: Exception) {
    e.message ?: "更新缓存失败"
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
 * 跳到本应用的系统设置页。
 *
 * 传感器引导页用它：加速度计不需要运行时权限，但如果系统 / ROM 把传感器关了，
 * 用户只能在这里检查（路径见那一页底部的提示）。
 */
@SuppressLint("WearRecents")
private fun openAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    )
    val host = context.findActivity()
    if (host != null) {
        runCatching { host.startActivity(intent) }
        return
    }
    // 兜底：调用点一定在 Activity 的 composition 内，正常走不到这里。
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

/**
 * 跳到系统的「所有文件访问」页面，逐级回退以防某些设备没有对应界面。
 *
 * 注意 `LocalContext.current` 拿到的通常是包了一层的 ContextThemeWrapper，
 * 直接写 `context !is Activity` 会误判成「不是 Activity」，从而多加
 * FLAG_ACTIVITY_NEW_TASK —— Wear 的最近任务栈对这个标志很敏感，
 * 所以这里先把包装层剥开再判断。
 */
@SuppressLint("WearRecents")
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
