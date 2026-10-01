package com.xialiangok.xlreader.data.epub

import androidx.compose.runtime.Immutable
import java.io.File

/** 上次读到的位置。 */
@Immutable
data class ReadingPosition(
    /** 章号。 */
    val chapter: Int,
    /** 章内第几个条目（列表下标，含标题等前置项）。 */
    val item: Int,
)

/**
 * 阅读进度：存在解压目录里的 `history.txt`。
 *
 * 放在书自己的目录里，而不是应用私有存储，好处是跟着书走：
 * 用户把这个目录整体删掉，进度也就一起清掉了，不会留下孤儿数据。
 *
 * 格式就是两行纯文本，人眼可读、出问题也好手动改：
 *
 * ```
 * chapter=3
 * item=12
 * ```
 */
object ReadHistory {

    /** 文件名。解压器在「更新缓存」时要靠它把进度带过去，所以是 internal 而不是 private。 */
    internal const val FILE_NAME = "history.txt"

    /** 读取进度；文件不存在或内容损坏都返回 null。 */
    fun load(bookDir: File): ReadingPosition? {
        val file = File(bookDir, FILE_NAME)
        if (!file.isFile) return null
        return runCatching {
            var chapter = -1
            var item = 0
            file.readLines().forEach { line ->
                val key = line.substringBefore('=', "").trim()
                val value = line.substringAfter('=', "").trim().toIntOrNull() ?: return@forEach
                when (key) {
                    "chapter" -> chapter = value
                    "item" -> item = value
                }
            }
            if (chapter >= 0) ReadingPosition(chapter, item.coerceAtLeast(0)) else null
        }.getOrNull()
    }

    /** 写入进度；失败就静默放弃——记不住位置不该影响阅读。 */
    fun save(bookDir: File, position: ReadingPosition) {
        runCatching {
            File(bookDir, FILE_NAME)
                .writeText("chapter=${position.chapter}\nitem=${position.item}\n")
        }
    }
}
