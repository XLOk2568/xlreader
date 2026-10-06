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

/** 条目是不是 epub (只看名字, 不碰磁盘; 列表分组用). */
fun FileEntry.isEpub(): Boolean = !isDirectory && name.endsWith(".epub", ignoreCase = true)

/**
 * 列出目录内容。
 *
 * 子目录与**所有**普通文件都列出来：文件浏览器兼做文件管理（选择 / 重命名 / 插件），
 * 只显示 epub 的话别的文件就管不到。隐藏文件（`.` 开头）、「最近打开」标记 [XLR_LATEST_MARKER_NAME]
 * 与 epub 顺序文件 [XLR_EPUB_SORTING_NAME] 仍然排除 —— 前者是系统 / 本应用的内部记录，
 * 后两者只给界面用，本身不该当成条目。
 * 没有权限或目录不可读时返回空列表（调用方负责提示权限）。
 */
fun listDirectory(dir: File): List<FileEntry> {
    val children = dir.listFiles() ?: return emptyList()
    val listed = children.asSequence()
        .filter {
            !it.name.startsWith(".") &&
                it.name != XLR_LATEST_MARKER_NAME &&
                it.name != XLR_EPUB_SORTING_NAME
        }
        .map { child ->
            if (child.isDirectory) {
                FileEntry(child.name, child.absolutePath, true, 0L)
            } else {
                FileEntry(child.name, child.absolutePath, false, child.length())
            }
        }
        // 目录在前，其次按名称排序（忽略大小写）。
        .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        .toList()
    // epub 的先后以后面的顺序文件为准, 其余两类保持上面的排序. 文件夹仍然排在最前.
    return listed.filter { it.isDirectory } +
        orderEpubEntries(dir, listed.filter { it.isEpub() }) +
        listed.filter { !it.isDirectory && !it.isEpub() }
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

/**
 * 把一个条目复制 / 移动到 [targetDir] 下.
 *
 * 返回 null 表示成功, 否则是给用户看的失败原因. 同名的目标会被覆盖 / 合并,
 * 与数据管理页的「粘贴」保持一致; 文件先试 renameTo (同一存储内是改名, 不用真的拷贝),
 * 失败再退化成拷贝 + 删原件.
 */
fun transferEntry(source: File, targetDir: File, move: Boolean): String? {
    if (!source.exists()) return "${source.name} 已经不存在了"
    if (!targetDir.isDirectory) return "目标目录不可用"
    val sourcePath = source.absolutePath
    // 把目录搬进它自己 (或它的子目录) 会无限递归, 直接拒绝.
    if (targetDir.absolutePath == sourcePath ||
        targetDir.absolutePath.startsWith(sourcePath + File.separator)
    ) {
        return "${source.name} 不能放进它自己里面"
    }
    val target = File(targetDir, source.name)
    if (target.absolutePath == sourcePath) return "${source.name} 已经在这个目录里"
    return runCatching {
        if (move && source.renameTo(target)) return@runCatching
        source.copyRecursively(target, overwrite = true)
        // 移动 = 拷贝成功之后才删原件; 删不掉就把失败报上去, 让用户知道原文件还在.
        if (move && !source.deleteRecursively()) error("delete failed")
    }.exceptionOrNull()?.let { "${source.name} 操作失败" }
}

/**
 * 重命名一个条目, 返回 null 表示成功, 否则是给用户看的失败原因.
 *
 * 选中 epub 时把旁边同名的解压目录一起改名 —— 那个目录就是 EpubExtractor 的缓存
 * (名字是 epub 去掉扩展名), 不同步改的话改名后的书会被当成没有缓存、重新解压一遍.
 */
fun renameEntry(source: File, newName: String): String? {
    val name = newName.trim()
    if (name.isEmpty()) return "名字不能为空"
    if (name.contains('/') || name.contains('\u0000')) return "名字里不能带 /"
    if (name == "." || name == "..") return "名字不合法"
    val target = File(source.parentFile, name)
    if (target.absolutePath == source.absolutePath) return null
    if (target.exists()) return "$name 已经存在"
    if (!source.renameTo(target)) return "重命名失败"
    if (source.isFile) {
        val oldCache = File(source.parentFile, source.nameWithoutExtension)
        if (oldCache.isDirectory) {
            val newCache = File(target.parentFile, target.nameWithoutExtension)
            if (newCache.absolutePath != oldCache.absolutePath &&
                !oldCache.renameTo(newCache)
            ) {
                return "书已改名, 但解压目录改名失败"
            }
        }
    }
    return null
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
