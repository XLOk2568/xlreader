package com.xialiangok.xlreader.data.file

import android.os.Environment
import androidx.compose.runtime.Immutable
import java.io.File

/** 目录里的一项。 */
@Immutable
data class FileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
)

/**
 * 是否已获得「所有文件访问」权限。
 *
 * `Environment.isExternalStorageManager()` 是 API 30 起才有的，
 * 而本应用 minSdk 就是 30，所以不需要版本判断。
 */
fun hasAllFilesAccess(): Boolean = Environment.isExternalStorageManager()

/** 浏览起点：内置共享存储的根目录，通常是 `/storage/emulated/0`。 */
fun defaultRootPath(): String = Environment.getExternalStorageDirectory().absolutePath

/** 是否是可阅读的电子书。目前只认 epub。 */
fun File.isReadableBook(): Boolean =
    isFile && name.endsWith(".epub", ignoreCase = true)

/**
 * 列出目录内容。
 *
 * 只保留**子目录**与**电子书**：手表屏幕只有几厘米，
 * 把图片、视频、安装包全列出来，用户要一直划才能找到想看的书。
 * 没有权限或目录不可读时返回空列表（调用方负责提示权限）。
 */
fun listDirectory(dir: File): List<FileEntry> {
    val children = dir.listFiles() ?: return emptyList()
    return children.asSequence()
        .filter { !it.name.startsWith(".") }
        .mapNotNull { child ->
            when {
                child.isDirectory -> FileEntry(child.name, child.absolutePath, true, 0L)
                child.isReadableBook() -> FileEntry(child.name, child.absolutePath, false, child.length())
                else -> null
            }
        }
        // 目录在前，其次按名称排序（忽略大小写）。
        .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        .toList()
}

/**
 * 上一级目录。
 *
 * 会停在存储根目录，不允许翻到 `/storage` 或 `/` —— 那既没有意义，
 * 也容易让用户迷路。
 */
fun parentWithinRoot(current: File, root: File = File(defaultRootPath())): File? {
    if (current.absolutePath == root.absolutePath) return null
    val parent = current.parentFile ?: return null
    return if (parent.absolutePath.length < root.absolutePath.length) null else parent
}

/** 给根目录一个好认的名字，其余情况显示完整路径。 */
fun displayPath(path: String): String =
    if (path == defaultRootPath()) "内部存储 · $path" else path

/** 把字节数格式化成人能读的形式。 */
fun formatSize(bytes: Long): String = when {
    bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
    bytes >= 1024L -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
