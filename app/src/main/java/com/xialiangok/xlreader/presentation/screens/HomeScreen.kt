package com.xialiangok.xlreader.presentation.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListSubHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.xialiangok.xlreader.data.file.FileEntry
import com.xialiangok.xlreader.data.file.displayPath
import com.xialiangok.xlreader.data.file.formatSize
import com.xialiangok.xlreader.data.file.parentWithinRoot
import com.xialiangok.xlreader.presentation.BindGestureActions
import com.xialiangok.xlreader.presentation.centeredItemKey
import com.xialiangok.xlreader.presentation.scrollByItems
import kotlinx.coroutines.launch
import java.io.File
import com.xialiangok.xlreader.presentation.theme.readerButtonColors

/**
 * 「最近打开」那本书的名称颜色：rgb(204, 91, 246)。
 *
 * 整个界面都是纯黑 + 灰阶，只有它（和文件夹的 [FolderNameColor]）带颜色 ——
 * 进这个目录时列表会自动滚到它上面，有颜色才能一眼从一列书名里认出「上次读的就是这本」。
 * 标记文件本身不上色、也不进列表。
 */
private val LatestMarkerColor = Color(0xFFCC5BF6)

/** 文件夹条目的名字颜色：rgb(224, 159, 0)。用来和 .epub 文件一眼区分开。 */
private val FolderNameColor = Color(0xFFE09F00)

/** 列表里排在条目之前的固定项数：标题、路径、计数（有「上一级」按钮时再加一）。 */
private const val ENTRY_HEADER_ITEMS = 3

/**
 * 主页：直接就是文件浏览器，进去看到的就是用户存储里的目录内容。
 *
 * 列出的是「当前目录 + 用户目录路径」，只显示子目录和 epub 文件。
 * 点目录进去，点 epub 直接开读。
 *
 * 目录里若有和书并排的 `xlrLatest.txt`（里面记着上次打开的那本 epub 的名字），
 * 它指向的那本 epub 会被 [LatestMarkerColor] 标出来，进来这个目录时列表也自动滚到它上面；
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
) {
    val currentDir = remember(dirPath) { File(dirPath) }
    val parent = remember(dirPath) { parentWithinRoot(currentDir) }
    val folderCount = entries.count { it.isDirectory }
    val bookCount = entries.size - folderCount

    val scope = rememberCoroutineScope()

    // 要定位就滚到「最近打开」的那本 epub 上（启动后第一次进来、打开子文件夹、从阅读页回来）；
    // 目录内容还没读出来（loading）或者上层说不用定位（从设置 / 关于回来）时保持原位不动。
    // 注意这里**只动滚动位置、不动目录**：进来停在哪个目录仍然由上次浏览的位置决定。
    LaunchedEffect(autoLocate, loading, entries, locatedPath) {
        if (!autoLocate || loading) return@LaunchedEffect
        val index = locatedPath?.let { path -> entries.indexOfFirst { it.path == path } } ?: -1
        if (index >= 0) {
            // 条目前面还有标题 / 路径 / 计数（可能还有「上一级」），都要算进下标里。
            val offset = ENTRY_HEADER_ITEMS + if (parent != null) 1 else 0
            runCatching { listState.scrollToItem(offset + index) }
        }
        // 定位只做这一次，做完就让上层把标志清掉。
        onAutoLocated()
    }

    /** 打开一个条目。 */
    fun openEntry(entry: FileEntry) {
        if (entry.isDirectory) onOpenDirectory(entry.path) else onOpenBook(entry.path)
    }

    // 体感手势：单击 = 打开屏幕上正中央的那一项（列表项用路径当 key，正好能反查回条目）
    // 返回 = 上一级（已经在最外层就没有这个动作），翻页 = 按条目滚动
    BindGestureActions(
        onTap = {
            val path = centeredItemKey(listState) as? String ?: return@BindGestureActions
            entries.firstOrNull { it.path == path }?.let(::openEntry)
        },
        onBack = if (parent != null) onNavigateUp else null,
        onScrollBy = { delta -> scope.launch { scrollByItems(listState, delta) } },
    )

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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
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
                        "$folderCount 个文件夹 · $bookCount 本电子书"
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
                    text = "这个文件夹里既没有子文件夹，也没有 .epub 文件。\n" +
                        "把电子书传到手表后回到这里即可。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        items(entries, key = { it.path }, contentType = { if (it.isDirectory) "dir" else "book" }) { entry ->
            EntryCard(
                entry = entry,
                highlighted = entry.path == locatedPath,
                onClick = { openEntry(entry) },
            )
        }

        item { ListSubHeader { Text(text = "其他", textAlign =TextAlign.Center, modifier = Modifier.fillMaxWidth()) } }
        item {
            // 单击这个子按钮就真的退出：结束 Activity + 从最近任务里移除，不是跳去设置页。
            Button(onClick = onExitApp, modifier = Modifier.fillMaxWidth(), shape = RectangleShape,colors = readerButtonColors()) {
                Text(text ="退出本应用", textAlign =TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
        item {
            Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth(), shape = RectangleShape, colors = readerButtonColors()) {
                Text(text ="设置", textAlign =TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
        item {
            Button(onClick = onOpenAbout, modifier = Modifier.fillMaxWidth(), shape = RectangleShape, colors = readerButtonColors()) {
                Text(text ="关于", textAlign =TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
        item { Spacer(Modifier.height(28.dp)) }
    }
}

@Composable
private fun EntryCard(entry: FileEntry, highlighted: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = entry.name,
            style = MaterialTheme.typography.titleSmall,
            // 「最近打开」的那本书用紫色、文件夹用橙色，其余都跟主题的 contentColor 走。
            color = when {
                highlighted -> LatestMarkerColor
                entry.isDirectory -> FolderNameColor
                else -> Color.Unspecified
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            // 目录 / 文件的「名字」保持左对齐（长文件名要靠左才看得清），只把下面这行说明居中。
            text = if (entry.isDirectory) "文件夹" else formatSize(entry.sizeBytes),
            style = MaterialTheme.typography.bodyExtraSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
