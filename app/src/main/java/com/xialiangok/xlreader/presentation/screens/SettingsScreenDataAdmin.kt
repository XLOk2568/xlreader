package com.xialiangok.xlreader.presentation.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
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
import com.xialiangok.xlreader.data.file.defaultRootPath
import com.xialiangok.xlreader.data.file.exportSettings
import com.xialiangok.xlreader.data.file.formatSize
import com.xialiangok.xlreader.data.file.importSettings
import com.xialiangok.xlreader.data.file.listDataDirectory
import com.xialiangok.xlreader.data.file.parentWithinDataDir
import com.xialiangok.xlreader.presentation.theme.readerButtonColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * data 目录管理页。
 *
 * 展示的是本应用自己的私有数据目录（`/data/user/0/<包名>`）——设置就存在它的
 * `shared_prefs` 下。列表里单击：文件夹进去、文件选中；下面对选中的那项做删除 / 复制，
 * 「粘贴」把复制过的那份放回当前目录（同名覆盖）。
 *
 * 底部是导入 / 导出：导出把设置打包进用户主文件目录，导入从那里挑一个 zip 还原回来。
 */
@Composable
fun SettingsDataAdminScreen(
    dataDirPath: String,
    onOpenImport: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var dirPath by remember { mutableStateOf(dataDirPath) }
    var entries by remember { mutableStateOf<List<FileEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<String?>(null) }
    // 复制过的那一项（绝对路径）；粘贴就是把它拷进当前目录。
    var clipboard by remember { mutableStateOf<String?>(null) }
    // 删除 / 粘贴之后 +1，让列表重新读一遍磁盘。
    var revision by remember { mutableIntStateOf(0) }
    var exporting by remember { mutableStateOf(false) }

    LaunchedEffect(dirPath, revision) {
        loading = true
        entries = withContext(Dispatchers.IO) { listDataDirectory(File(dirPath)) }
        loading = false
    }

    val currentDir = remember(dirPath) { File(dirPath) }
    val rootDir = remember(dataDirPath) { File(dataDirPath) }
    val parent = remember(dirPath) { parentWithinDataDir(currentDir, rootDir) }
    val selectedFile = selected?.let(::File)

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    WearListScreen(resetKey = dirPath) {
        item { ListHeader { Text("数据目录", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) } }
        item {
            ListSubHeader {
                Text(
                    text = dirPath,
                    style = MaterialTheme.typography.bodyExtraSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        item {
            ListSubHeader {
                Text(
                    text = if (loading) {
                        "正在读取…"
                    } else {
                        "${entries.count { it.isDirectory }} 个文件夹 · " +
                            "${entries.count { !it.isDirectory }} 个文件"
                    },
                    style = MaterialTheme.typography.bodyExtraSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (parent != null) {
            item {
                Button(
                    onClick = {
                        selected = null
                        dirPath = parent.absolutePath
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RectangleShape,
                    colors = readerButtonColors(),
                ) {
                    Text("上一级", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        if (!loading && entries.isEmpty()) {
            item {
                Text(
                    text = "这个目录是空的。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        items(entries, key = { it.path }) { entry ->
            val isSelected = entry.path == selected
            Card(
                onClick = {
                    if (entry.isDirectory) {
                        selected = null
                        dirPath = entry.path
                    } else {
                        // 再点一下取消选中，免得误删。
                        selected = if (isSelected) null else entry.path
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (isSelected) "✓ ${entry.name}" else entry.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = if (entry.isDirectory) "文件夹" else formatSize(entry.sizeBytes),
                    style = MaterialTheme.typography.bodyExtraSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item { ListSubHeader { Text("文件操作", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) } }
        item {
            Text(
                text = if (selectedFile != null) {
                    "已选中：${selectedFile.name}"
                } else {
                    "单击一个文件来选中它。"
                },
                style = MaterialTheme.typography.bodyExtraSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Button(
                onClick = {
                    val file = selectedFile ?: return@Button toast("先选中一个文件")
                    if (file.isDirectory) return@Button toast("文件夹不能复制")
                    clipboard = file.absolutePath
                    toast("已复制 ${file.name}")
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RectangleShape,
                colors = readerButtonColors(),
            ) {
                Text("复制", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
        item {
            Button(
                onClick = {
                    val source = clipboard?.let(::File) ?: return@Button toast("还没有复制任何文件")
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            runCatching {
                                source.copyTo(File(dirPath, source.name), overwrite = true)
                            }.isSuccess
                        }
                        toast(if (ok) "已粘贴 ${source.name}" else "粘贴失败")
                        if (ok) revision += 1
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RectangleShape,
                colors = readerButtonColors(),
            ) {
                Text(if (clipboard == null) "粘贴" else "粘贴 ${File(clipboard!!).name}", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
        item {
            Button(
                onClick = {
                    val file = selectedFile ?: return@Button toast("先选中一个文件")
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { file.deleteRecursively() }
                        toast(if (ok) "已删除 ${file.name}" else "删除失败")
                        selected = null
                        revision += 1
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RectangleShape,
                colors = readerButtonColors(),
            ) {
                Text("删除", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }

        item { ListSubHeader { Text("设置备份", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) } }
        item {
            // 导出到用户主文件目录（内部存储根），导入页在同一层就能看到这个 zip。
            Button(
                onClick = {
                    if (exporting) return@Button
                    exporting = true
                    scope.launch {
                        val file = withContext(Dispatchers.IO) {
                            runCatching { exportSettings(context, File(defaultRootPath())) }.getOrNull()
                        }
                        exporting = false
                        toast(if (file != null) "已导出 ${file.name}" else "导出失败")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RectangleShape,
                colors = readerButtonColors(),
            ) {
                Text("导出设置", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
        item {
            Button(
                onClick = onOpenImport,
                modifier = Modifier.fillMaxWidth(),
                shape = RectangleShape,
                colors = readerButtonColors(),
            ) {
                Text("导入设置", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }

        item { Spacer(Modifier.height(10.dp)) }
        item {
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                shape = RectangleShape,
                colors = readerButtonColors(),
            ) {
                Text("返回", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
        item { Spacer(Modifier.height(28.dp)) }
    }
}

/**
 * 导入设置：在用户主文件目录里列出 .zip，单击一个就解压并覆盖当前 data。
 *
 * @param sourceDirPath 用户主文件目录（内部存储根）。
 * @param onImported    导入完成后回调：根组件要重新读一遍设置，界面才会立刻用上新值。
 */
@Composable
fun SettingsImportScreen(
    sourceDirPath: String,
    onImported: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var entries by remember(sourceDirPath) { mutableStateOf<List<FileEntry>>(emptyList()) }
    var loading by remember(sourceDirPath) { mutableStateOf(true) }
    var importing by remember { mutableStateOf(false) }

    LaunchedEffect(sourceDirPath) {
        loading = true
        entries = withContext(Dispatchers.IO) {
            listDataDirectory(File(sourceDirPath))
                .filter { !it.isDirectory && it.name.endsWith(".zip", ignoreCase = true) }
        }
        loading = false
    }

    WearListScreen(resetKey = sourceDirPath) {
        item { ListHeader { Text("导入设置", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) } }
        item {
            ListSubHeader {
                Text(
                    text = sourceDirPath,
                    style = MaterialTheme.typography.bodyExtraSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        item {
            Text(
                // 说清楚后果：导入是覆盖，不合并。
                text = "单击一个 zip 就解压还原，同名文件会被直接覆盖。" +
                    "导出设置生成的 zip 就放在这个目录里。",
                style = MaterialTheme.typography.bodyExtraSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (!loading && entries.isEmpty()) {
            item {
                Text(
                    text = "这个目录里没有 .zip 文件。\n先用「导出设置」生成一个，或自己把备份放进来。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        items(entries, key = { it.path }) { entry ->
            Card(
                onClick = {
                    if (importing) return@Card
                    importing = true
                    scope.launch {
                        val count = withContext(Dispatchers.IO) {
                            runCatching { importSettings(context, File(entry.path)) }.getOrNull()
                        }
                        importing = false
                        Toast.makeText(
                            context,
                            if (count == null) "导入失败：文件读不出来" else "已导入 $count 个文件",
                            Toast.LENGTH_SHORT,
                        ).show()
                        if (count != null) onImported()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = formatSize(entry.sizeBytes),
                    style = MaterialTheme.typography.bodyExtraSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item { Spacer(Modifier.height(10.dp)) }
        item {
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                shape = RectangleShape,
                colors = readerButtonColors(),
            ) {
                Text("返回", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
        item { Spacer(Modifier.height(28.dp)) }
    }
}
