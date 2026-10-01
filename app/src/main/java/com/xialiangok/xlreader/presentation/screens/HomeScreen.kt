package com.xialiangok.xlreader.presentation.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import com.xialiangok.xlreader.data.file.listDirectory
import com.xialiangok.xlreader.data.file.parentWithinRoot
import com.xialiangok.xlreader.presentation.BindGestureActions
import com.xialiangok.xlreader.presentation.centeredItemKey
import com.xialiangok.xlreader.presentation.scrollByItems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.xialiangok.xlreader.presentation.theme.readerButtonColors

/**
 * 主页：直接就是文件浏览器，进去看到的就是用户存储里的目录内容。
 *
 * 列出的是「当前目录 + 用户目录路径」，只显示子目录和 epub 文件。
 * 点目录进去，点 epub 直接开读。
 *
 * @param dirPath        当前所在目录的绝对路径。
 * @param onOpenDirectory 进入子目录。
 * @param onOpenBook     打开一本电子书（绝对路径）。
 * @param onNavigateUp   返回上一级。
 * @param onExitApp      退出本应用（结束 Activity 并把它从最近任务里移除）。
 */
@Composable
fun HomeScreen(
    dirPath: String,
    onOpenDirectory: (String) -> Unit,
    onOpenBook: (String) -> Unit,
    onNavigateUp: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onExitApp: () -> Unit,
) {
    var entries by remember(dirPath) { mutableStateOf<List<FileEntry>>(emptyList()) }
    var loading by remember(dirPath) { mutableStateOf(true) }

    // 列目录要走 IO 线程，否则大目录会让首帧掉帧。
    LaunchedEffect(dirPath) {
        loading = true
        entries = withContext(Dispatchers.IO) { listDirectory(File(dirPath)) }
        loading = false
    }

    val currentDir = remember(dirPath) { File(dirPath) }
    val parent = remember(dirPath) { parentWithinRoot(currentDir) }
    val folderCount = entries.count { it.isDirectory }
    val bookCount = entries.size - folderCount

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // 体感手势：单击 = 打开屏幕上正中央的那一项（列表项用路径当 key，正好能反查回条目），
    // 返回 = 上一级（已经在最外层就没有这个动作），翻页 = 按条目滚动。
    BindGestureActions(
        onTap = {
            val path = centeredItemKey(listState) as? String ?: return@BindGestureActions
            entries.firstOrNull { it.path == path }?.let { entry ->
                if (entry.isDirectory) onOpenDirectory(entry.path) else onOpenBook(entry.path)
            }
        },
        onBack = if (parent != null) onNavigateUp else null,
        onScrollBy = { delta -> scope.launch { scrollByItems(listState, delta) } },
    )

    WearListScreen(resetKey = dirPath, listState = listState) {
        item {
            ListHeader {
                Text(
                    text = currentDir.name.ifEmpty { "存储" },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
                )
            }
        }

        if (parent != null) {
            item {
                Button(onClick = onNavigateUp, modifier = Modifier.fillMaxWidth(), colors = readerButtonColors()) {
                    Text("↑ 上一级")
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
                onClick = {
                    if (entry.isDirectory) onOpenDirectory(entry.path) else onOpenBook(entry.path)
                },
            )
        }

        item { ListSubHeader { Text("其他") } }
        item {
            // 单击这个子按钮就真的退出：结束 Activity + 从最近任务里移除，不是跳去设置页。
            Button(onClick = onExitApp, modifier = Modifier.fillMaxWidth(), colors = readerButtonColors()) {
                Text("退出本应用")
            }
        }
        item {
            Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth(), colors = readerButtonColors()) {
                Text("设置")
            }
        }
        item {
            Button(onClick = onOpenAbout, modifier = Modifier.fillMaxWidth(), colors = readerButtonColors()) {
                Text("关于")
            }
        }
        item { Spacer(Modifier.height(28.dp)) }
    }
}

@Composable
private fun EntryCard(entry: FileEntry, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = entry.name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = if (entry.isDirectory) "文件夹" else formatSize(entry.sizeBytes),
            style = MaterialTheme.typography.bodyExtraSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
