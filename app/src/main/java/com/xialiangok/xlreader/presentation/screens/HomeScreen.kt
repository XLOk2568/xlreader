package com.xialiangok.xlreader.presentation.screens

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListSubHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.xialiangok.xlreader.data.file.FileEntry
import com.xialiangok.xlreader.data.file.displayPath
import com.xialiangok.xlreader.data.file.formatSize
import com.xialiangok.xlreader.data.file.isEpub
import com.xialiangok.xlreader.data.file.isReadableBook
import com.xialiangok.xlreader.data.file.parentWithinRoot
import com.xialiangok.xlreader.data.file.renameEntry
import com.xialiangok.xlreader.data.file.transferEntry
import com.xialiangok.xlreader.data.lan.checkLanFileManagerWifi
import com.xialiangok.xlreader.presentation.BindGestureActions
import com.xialiangok.xlreader.presentation.centeredItemKey
import com.xialiangok.xlreader.presentation.findActivity
import com.xialiangok.xlreader.presentation.scrollByDp
import com.xialiangok.xlreader.presentation.theme.LocalReaderPreferences
import com.xialiangok.xlreader.presentation.theme.ReaderSurface
import com.xialiangok.xlreader.presentation.theme.readerButtonColors
import com.xialiangok.xlreader.presentation.theme.rgbColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// 文件列表只有「最近打开」书名与文件夹名带颜色, 颜色都取设置页的配置.

/**
 * 文件列表的四种模式, 顺序就是单击模式按钮时的轮换顺序.
 *
 * 浏览模式是正常状态: 点条目就是进目录 / 开书, 下面只有退出 / 设置 / 关于.
 * 选择模式每项左侧多一个复选框, 下方换成移动 / 复制 / 粘贴 / 删除;
 * 重命名模式只能选一项, 下方换成重命名; 插件模式列出可用的插件 (目前只有局域网管理文件).
 */
private enum class FileMode(val label: String) {
    Select("选择模式"),
    Rename("重命名模式"),
    Plugin("插件模式"),
    Browse("浏览模式");

    /** 下一个模式: 唯一的模式按钮单击一次就轮到它. */
    fun next(): FileMode = entries[(ordinal + 1) % entries.size]
}

/**
 * 主页：直接就是文件浏览器，进去看到的就是用户存储里的目录内容。
 *
 * 列出的是「当前目录 + 用户目录路径」：子目录和所有文件都显示, 但只有 epub 能点开阅读,
 * 其余文件留给选择 / 重命名 / 插件模式管理.
 * 点目录进去，点 epub 直接开读。
 *
 * 条目分三段: PATH (子文件夹) / EPUB (电子书, 顺序照目录里的 `epubsorting.txt.xlr`) / FILES (其余文件);
 * 某一段没有内容时连标题一起不显示, 空的目录就只剩一句提示。
 *
 * 目录里若有和书并排的 `xlrLatest.txt`（里面记着上次打开的那本 epub 的名字），
 * 它指向的那本 epub 会被标出来, 进来这个目录时列表也自动滚到它上面;
 * 标记文件自己不作为条目出现。
 *
 * @param dirPath        当前所在目录的绝对路径。
 * @param entries        这个目录里读出来的条目（子目录 + epub）；空表示还没读出来。
 * @param loading        目录内容是不是正在读。
 * @param locatedPath    「最近打开」那本书的绝对路径（找不到则为 null）：它就是要定位、要标色的那一项。
 *   目录内容与滚动状态都由根组件持有 —— 从阅读页 / 设置页 / 关于页回来时这一页会重新组合，
 *   状态留在页面里的话，内容会重新读（列表先缩成空的）、滚动位置被夹回顶部。
 * @param listState      文件列表的滚动状态，同样由根组件持有，理由同上。
 * @param autoLocate     本次进入这个目录要不要自动滚到「最近打开」那本书上
 *                       （启动后第一次进来、打开子文件夹、从阅读页返回时为 true；
 *                       打开设置 / 关于再回来时为 false，那时要保持原来的滚动位置）。
 * @param onAutoLocated  这次定位已经处理完（成功与否都算），调用方据此不再重复定位。
 * @param onOpenDirectory 进入子目录。
 * @param onOpenBook     打开一本电子书（绝对路径）。
 * @param onNavigateUp   返回上一级。
 * @param onExitApp      退出本应用（结束 Activity 并把它从最近任务里移除）。
 * @param onOpenLanFileManager 插件模式里打开「局域网管理文件」页（Wi-Fi 检查已通过）。
 * @param onFilesChanged 当前目录被改名 / 删除 / 粘贴改了, 让根组件重新读一遍目录.
 */
@Composable
fun HomeScreen(
    dirPath: String,
    entries: List<FileEntry>,
    loading: Boolean,
    locatedPath: String?,
    listState: LazyListState,
    autoLocate: Boolean,
    onAutoLocated: () -> Unit,
    onOpenDirectory: (String) -> Unit,
    onOpenBook: (String) -> Unit,
    onNavigateUp: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onExitApp: () -> Unit,
    onOpenLanFileManager: () -> Unit,
    onFilesChanged: () -> Unit,
) {
    val currentDir = remember(dirPath) { File(dirPath) }
    val parent = remember(dirPath) { parentWithinRoot(currentDir) }
    val folderCount = entries.count { it.isDirectory }
    val fileCount = entries.size - folderCount

    // 三段分组: 文件夹 / epub / 其余文件. epub 的先后已经由 listDirectory 按顺序文件排好.
    val folders = entries.filter { it.isDirectory }
    val epubs = entries.filter { it.isEpub() }
    val otherFiles = entries.filter { !it.isDirectory && !it.isEpub() }

    // 定位滚动要算 LazyColumn 里的真实下标: 标题 / 路径 / 计数 / 「上一级」, 再加各段标题.
    val sectionsStart = 3 + if (parent != null) 1 else 0
    val foldersStart = sectionsStart + if (folders.isNotEmpty()) 1 else 0
    val epubsStart = foldersStart + folders.size + if (epubs.isNotEmpty()) 1 else 0
    val otherFilesStart = epubsStart + epubs.size + if (otherFiles.isNotEmpty()) 1 else 0

    /** 某个绝对路径在列表里的下标, 不在这三段里时返回 -1. */
    fun listIndexOf(path: String): Int {
        val inFolders = folders.indexOfFirst { it.path == path }
        if (inFolders >= 0) return foldersStart + inFolders
        val inEpubs = epubs.indexOfFirst { it.path == path }
        if (inEpubs >= 0) return epubsStart + inEpubs
        val inOthers = otherFiles.indexOfFirst { it.path == path }
        if (inOthers >= 0) return otherFilesStart + inOthers
        return -1
    }

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val context = LocalContext.current

    /** 当前模式. 默认浏览模式: 打开 app 就是正常界面. */
    var mode by remember { mutableStateOf(FileMode.Browse) }
    /** 勾选 / 单选中的条目路径. 只可能是当前目录里的条目, 换目录就清空. */
    var selectedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    /** 复制 / 移动过的条目路径, 等用户进到目标目录再粘贴. */
    var clipboardPaths by remember { mutableStateOf<List<String>>(emptyList()) }
    /** 剪贴板里是「移动」而不是「复制」. */
    var clipboardMove by remember { mutableStateOf(false) }
    /** 正在重命名的条目; 非 null 就弹输入框. */
    var renamingEntry by remember { mutableStateOf<FileEntry?>(null) }
    var renameText by remember { mutableStateOf("") }
    /** 删除前的二次确认. */
    var confirmDelete by remember { mutableStateOf(false) }
    /** 开局域网文件管理前检查不通过的原因; 非 null 就弹提示框. */
    var wifiProblem by remember { mutableStateOf<String?>(null) }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    /** 插件: 先确认 Wi-Fi 和本机地址都拿得到, 通过了才进服务页. */
    fun startLanFileManager() {
        val wifi = checkLanFileManagerWifi(context)
        if (wifi.problem != null) wifiProblem = wifi.problem else onOpenLanFileManager()
    }

    /** 打开系统的 Wi-Fi 设置页; 拿不到 Activity 时退化成加 NEW_TASK 直接起. */
    fun openWifiSettings() {
        wifiProblem = null
        val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
        val host = context.findActivity()
        if (host != null) {
            runCatching { host.startActivity(intent) }
        } else {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
        }
    }

    // 换目录就清掉勾选: 勾选只对当前这一屏的条目有意义, 复制 / 移动要的东西
    // 在点按钮那一刻已经进了剪贴板, 不会因为换目录而丢.
    LaunchedEffect(dirPath) { selectedPaths = emptySet() }

    // 要定位就滚到「最近打开」的那本 epub 上（启动后第一次进来、打开子文件夹、从阅读页回来）；
    // 目录内容还没读出来（loading）或者上层说不用定位（从设置 / 关于回来）时保持原位不动。
    // 注意这里**只动滚动位置、不动目录**：进来停在哪个目录仍然由上次浏览的位置决定。
    LaunchedEffect(autoLocate, loading, entries, locatedPath) {
        if (!autoLocate || loading) return@LaunchedEffect
        val index = locatedPath?.let(::listIndexOf) ?: -1
        if (index >= 0) runCatching { listState.scrollToItem(index) }
        // 定位只做这一次，做完就让上层把标志清掉。
        onAutoLocated()
    }

    /** 切模式: 勾选状态只在同一个模式里有效, 顺手清掉. */
    fun switchMode(target: FileMode) {
        mode = target
        selectedPaths = emptySet()
    }

    /** 打开一个条目: 目录进下一层, epub 开读, 其他文件只提示一句 (它们只在管理模式里有用). */
    fun openEntry(entry: FileEntry) {
        when {
            entry.isDirectory -> onOpenDirectory(entry.path)
            File(entry.path).isReadableBook() -> onOpenBook(entry.path)
            else -> toast("${entry.name} 不是 epub, 打不开")
        }
    }

    /** 单击条目本体: 选择模式照常进目录 / 开书, 重命名模式是单选. */
    fun activateEntry(entry: FileEntry) {
        when (mode) {
            FileMode.Rename -> selectedPaths = setOf(entry.path)
            else -> openEntry(entry)
        }
    }

    /** 单击左侧复选框: 选择模式可多选, 重命名模式只能留一个. */
    fun toggleEntry(entry: FileEntry) {
        val checked = entry.path in selectedPaths
        selectedPaths = when (mode) {
            FileMode.Select -> if (checked) selectedPaths - entry.path else selectedPaths + entry.path
            FileMode.Rename -> if (checked) emptySet() else setOf(entry.path)
            else -> selectedPaths
        }
    }

    /** 把勾选的条目放进剪贴板, 真正的复制 / 移动等粘贴时再做. */
    fun takeSelection(move: Boolean) {
        if (selectedPaths.isEmpty()) {
            return toast(if (move) "先勾选要移动的文件" else "先勾选要复制的文件")
        }
        clipboardPaths = selectedPaths.toList()
        clipboardMove = move
        selectedPaths = emptySet()
        toast("已${if (move) "剪切" else "复制"} ${clipboardPaths.size} 项, 进目标文件夹后点粘贴")
    }

    /** 把剪贴板里的条目放进当前目录. */
    fun paste() {
        if (clipboardPaths.isEmpty()) return toast("还没有复制或移动任何文件")
        val sources = clipboardPaths.map(::File)
        val move = clipboardMove
        scope.launch {
            val (done, firstError) = withContext(Dispatchers.IO) {
                var ok = 0
                var error: String? = null
                for (source in sources) {
                    val failure = transferEntry(source, currentDir, move)
                    if (failure == null) ok++ else if (error == null) error = failure
                }
                ok to error
            }
            selectedPaths = emptySet()
            if (done > 0) {
                onFilesChanged()
                // 移动完原件就没了, 剪贴板再留着也贴不出东西; 复制则保留, 方便贴到多个目录.
                if (move) clipboardPaths = emptyList()
                toast(
                    if (firstError == null) {
                        "已${if (move) "移动" else "粘贴"} $done 项"
                    } else {
                        "已${if (move) "移动" else "粘贴"} $done 项, ${sources.size - done} 项失败: $firstError"
                    },
                )
            } else {
                toast(firstError ?: "粘贴失败")
            }
        }
    }

    /** 弹出删除确认. */
    fun requestDelete() {
        if (selectedPaths.isEmpty()) return toast("先勾选要删除的条目")
        confirmDelete = true
    }

    /** 确认后真的删掉勾选的条目. */
    fun deleteSelected() {
        val targets = selectedPaths.map(::File)
        confirmDelete = false
        scope.launch {
            val deleted = withContext(Dispatchers.IO) { targets.count { it.deleteRecursively() } }
            selectedPaths = emptySet()
            if (deleted > 0) onFilesChanged()
            toast(if (deleted == targets.size) "已删除 $deleted 项" else "删除 $deleted/${targets.size} 项")
        }
    }

    /** 打开重命名输入框, 初始值就是原来的名字. */
    fun beginRename() {
        val path = selectedPaths.firstOrNull() ?: return toast("先点一个文件或文件夹")
        val entry = entries.firstOrNull { it.path == path } ?: return toast("选中的条目已经不在这个目录里")
        renameText = entry.name
        renamingEntry = entry
    }

    /** 按输入框里的名字真的改名. */
    fun applyRename() {
        val entry = renamingEntry ?: return
        val name = renameText.trim()
        if (name.isEmpty()) return toast("名字不能为空")
        renamingEntry = null
        if (name == entry.name) return
        scope.launch {
            val failure = withContext(Dispatchers.IO) { renameEntry(File(entry.path), name) }
            if (failure == null) {
                selectedPaths = emptySet()
                onFilesChanged()
                toast("已重命名为 $name")
            } else {
                toast(failure)
            }
        }
    }

    // 体感手势：单击 = 打开屏幕上正中央的那一项（列表项用路径当 key，正好能反查回条目）
    // 返回 = 上一级（已经在最外层就没有这个动作），翻页 = 按像素（dp）滚动
    BindGestureActions(
        onTap = {
            val path = centeredItemKey(listState) as? String ?: return@BindGestureActions
            entries.firstOrNull { it.path == path }?.let(::activateEntry)
        },
        onBack = if (parent != null) onNavigateUp else null,
        onScrollBy = { delta -> scope.launch { scrollByDp(listState, delta, density) } },
    )

    /** 一条条目: 三段共用同一套高亮 / 勾选 / 点击逻辑. */
    val entryRow: @Composable (FileEntry) -> Unit = { entry ->
        EntryCard(
            entry = entry,
            highlighted = entry.path == locatedPath,
            // 选择 / 重命名模式下每项左侧都显示复选框, 勾就用设置里的当前章节颜色.
            checkable = mode == FileMode.Select || mode == FileMode.Rename,
            checked = entry.path in selectedPaths,
            onClick = { activateEntry(entry) },
            onToggleCheck = { toggleEntry(entry) },
        )
    }

    WearListScreen(
        // resetKey 传 null：滚动状态由根组件持有，进出组合都不许重置 ——
        // 换目录时的「回到顶部」由根组件的 setBrowserDir 自己做。
        resetKey = null,
        listState = listState,
    ) {
        item {
            ListHeader {
                Text(
                    text = currentDir.name.ifEmpty { "存储" },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        item {
            // 用户目录路径
            ListSubHeader {
                Text(
                    text = displayPath(dirPath),
                    style = MaterialTheme.typography.bodyExtraSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        item {
            ListSubHeader {
                Text(
                    text = if (loading) {
                        "正在读取…"
                    } else {
                        "$folderCount 个文件夹 · $fileCount 个文件"
                    },
                    style = MaterialTheme.typography.bodyExtraSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (parent != null) {
            item {
                Button(onClick = onNavigateUp, modifier = Modifier.fillMaxWidth(), shape = RectangleShape, colors = readerButtonColors()) {
                    Text(text = "上一级", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        if (!loading && entries.isEmpty()) {
            item {
                Text(
                    text = "这个文件夹里既没有子文件夹，也没有文件。\n" +
                        "把电子书传到手表后回到这里即可。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (folders.isNotEmpty()) {
            item(key = "section:path") { SectionLabel("PATH") }
            items(folders, key = { it.path }, contentType = { "dir" }) { entry -> entryRow(entry) }
        }
        if (epubs.isNotEmpty()) {
            item(key = "section:epub") { SectionLabel("EPUB") }
            items(epubs, key = { it.path }, contentType = { "book" }) { entry -> entryRow(entry) }
        }
        if (otherFiles.isNotEmpty()) {
            item(key = "section:files") { SectionLabel("FILES") }
            items(otherFiles, key = { it.path }, contentType = { "file" }) { entry -> entryRow(entry) }
        }

        // 模式只用这一个按钮: 名字就是当前模式, 单击按顺序轮换到下一个, 下面的操作区跟着换.
        item { ListSubHeader { Text(text = "模式", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) } }
        item(key = "mode") {
            ModeButton(label = mode.label, onClick = { switchMode(mode.next()) })
        }

        item { ListSubHeader { Text(text = "其他", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) } }
        when (mode) {
            FileMode.Select -> {
                item { ActionButton("移动") { takeSelection(move = true) } }
                item { ActionButton("复制") { takeSelection(move = false) } }
                item { ActionButton("粘贴") { paste() } }
                item { ActionButton("删除") { requestDelete() } }
            }

            FileMode.Rename -> item { ActionButton("重命名") { beginRename() } }

            // 插件模式只列插件: 现在只有「局域网管理文件」这一个.
            FileMode.Plugin -> item { ActionButton("局域网管理文件") { startLanFileManager() } }

            FileMode.Browse -> {
                item {
                    // 单击这个子按钮就真的退出：结束 Activity + 从最近任务里移除，不是跳去设置页。
                    ActionButton("退出本应用", onExitApp)
                }
                item { ActionButton("设置", onOpenSettings) }
                item { ActionButton("关于", onOpenAbout) }
            }
        }
    }

    // 重命名输入框: 用 ui.window.Dialog 自己画, 理由和数据管理页的询问框一样
    // (Wear 的 AlertDialog 强制底部双图标按钮).
    val renaming = renamingEntry
    if (renaming != null) {
        Dialog(onDismissRequest = { renamingEntry = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "重命名",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ReaderSurface)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    BasicTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleSmall.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Button(
                    onClick = { applyRename() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RectangleShape,
                    colors = readerButtonColors(),
                ) {
                    Text("确定", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
                Button(
                    onClick = { renamingEntry = null },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RectangleShape,
                    colors = readerButtonColors(),
                ) {
                    Text("取消", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }

    // 删除确认: 手表屏幕上误触很容易, 删之前一定再问一句.
    if (confirmDelete) {
        Dialog(onDismissRequest = { confirmDelete = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "删除选中的 ${selectedPaths.size} 项?",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "删除后不能恢复.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { deleteSelected() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RectangleShape,
                    colors = readerButtonColors(),
                ) {
                    Text("删除", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
                Button(
                    onClick = { confirmDelete = false },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RectangleShape,
                    colors = readerButtonColors(),
                ) {
                    Text("取消", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }

    // 开局域网文件管理前 Wi-Fi 检查没过: 说清原因, 并给一个直接去开 Wi-Fi 的按钮.
    if (wifiProblem != null) {
        Dialog(onDismissRequest = { wifiProblem = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "需要连接 Wi-Fi",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = wifiProblem ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { openWifiSettings() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RectangleShape,
                    colors = readerButtonColors(),
                ) {
                    Text("打开 Wi-Fi 设置", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
                Button(
                    onClick = { wifiProblem = null },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RectangleShape,
                    colors = readerButtonColors(),
                ) {
                    Text("取消", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** 分区标题: PATH / EPUB / FILES, 那段没有条目时整个标题都不出现. */
@Composable
private fun SectionLabel(text: String) {
    ListSubHeader {
        Text(text = text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

/** 唯一的模式按钮: 显示当前模式, 单击切到下一个模式. */
@Composable
private fun ModeButton(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RectangleShape, colors = readerButtonColors()) {
        Text(
            text = label,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 下方「其他」区里的普通操作按钮. */
@Composable
private fun ActionButton(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RectangleShape, colors = readerButtonColors()) {
        Text(text = label, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun EntryCard(
    entry: FileEntry,
    highlighted: Boolean,
    checkable: Boolean,
    checked: Boolean,
    onClick: () -> Unit,
    onToggleCheck: () -> Unit,
) {
    val prefs = LocalReaderPreferences.current
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (checkable) {
                EntryCheckbox(checked = checked, onToggle = onToggleCheck)
                Spacer(Modifier.width(8.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.titleSmall,
                    // 「最近打开」用设置里的紫色, 文件夹用设置里的橙色, 其余跟主题 contentColor.
                    color = when {
                        highlighted -> rgbColor(prefs.latestColor)
                        entry.isDirectory -> rgbColor(prefs.folderColor)
                        else -> Color.Unspecified
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    // 目录 / 文件的「名字」保持左对齐（长文件名要靠左才看得清），只把下面这行说明居中。
                    text = if (entry.isDirectory) "文件夹" else formatSize(entry.sizeBytes),
                    style = MaterialTheme.typography.bodyExtraSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * 条目左侧的复选框: 颜色用设置里的「当前章节颜色」, 勾的大小跟随正文字号.
 *
 * 只有这一小块能切换勾选, 点条目本体仍然是进目录 / 开书, 这样选择模式里也能继续往下翻.
 */
@Composable
private fun EntryCheckbox(checked: Boolean, onToggle: () -> Unit) {
    val prefs = LocalReaderPreferences.current
    val color = rgbColor(prefs.currentChapterColor)
    val shape = RoundedCornerShape(4.dp)
    // 外框比勾大一圈, 勾本身用正文字号 (bodyLarge), 用户调大正文时框跟着变大.
    val boxSize = with(LocalDensity.current) { (prefs.fontSize + 8).sp.toDp() }
    Box(
        modifier = Modifier
            .size(boxSize)
            .clip(shape)
            .border(1.dp, color, shape)
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Text(
                text = "✓",
                style = MaterialTheme.typography.bodyLarge,
                color = color,
                textAlign = TextAlign.Center,
            )
        }
    }
}
