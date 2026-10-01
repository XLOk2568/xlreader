package com.xialiangok.xlreader.data.epub

import androidx.compose.runtime.Immutable
import java.io.File

/**
 * 一本已经「打开」的 epub。
 *
 * 打开 = 把 epub 解压到同名目录 + 解析元数据。这里**只持有元数据**：
 * 书名、作者、章节名，以及每章在解压目录里的相对路径。
 * 正文一个字都不缓存，[loadChapter] 才是真正去读某一章。
 *
 * 对 Wear 设备来说这一点很关键：一本几十 MB 的图文书如果整本读进内存，
 * 手表很容易被 LMK 干掉；按章读取时，内存里最多只有一章的内容。
 */
@Immutable
class EpubBook internal constructor(
    /** 原始 epub 文件。 */
    val sourceFile: File,
    /** 解压目录（与原 epub 同名、同级的目录）。 */
    val dir: File,
    val title: String,
    val author: String,
    val chapterTitles: List<String>,
    internal val chapterPaths: List<String>,
    /** manifest 里声明为 `image/svg+xml` 的资源（相对解压目录）。 */
    internal val svgPaths: Set<String> = emptySet(),
) {
    val chapterCount: Int
        get() = chapterTitles.size

    /**
     * 读取第 [index] 章的内容（耗时操作，请在 IO 线程调用）。
     *
     * 因为内容已经解压成普通文件，这里就是一次普通的文件读取，
     * 不需要再开 ZIP、也不需要解压。
     */
    fun loadChapter(index: Int): List<EpubBlock> = EpubParser.loadChapter(this, index)
}

/** EPUB 无法读取时抛出，message 会直接展示给用户。 */
class EpubParseException(message: String) : Exception(message)
