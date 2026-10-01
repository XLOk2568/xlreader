package com.xialiangok.xlreader.data.epub

import java.io.File

/**
 * 解析结果（书名 / 作者 / 章节名 / 章节路径 / SVG 清单）的缓存。
 *
 * 为什么需要它：没有索引时，**每次**打开一本书都要走一遍
 * 「开原 epub 的 ZIP → 读 container.xml → 读整份 OPF → 读 nav/NCX 目录 → 跑一堆正则」，
 * 在手表上（存储走 FUSE、CPU 又慢）这几步加起来就是用户能感觉到的「打开有点慢」，
 * 而且第二遍、第三遍打开一点都不比第一遍省 —— 因为解压结果虽然在，解析却每次重来。
 *
 * 有了它，第二次以后打开只要读一个小文本文件；连原 epub 都不必打开。
 *
 * 几点刻意的设计：
 * - **只存元数据，不存正文**：正文仍然是翻到哪章读哪章，内存占用和以前一样；
 * - **与解压结果绑定**：索引里记着解压完成标记的代次号（[EpubExtractor.generationOf]），
 *   重新解压会换新目录 + 新代次，旧索引自然失效，不会串味；
 * - **随时可以删**：文件丢了、被写坏了、格式对不上，都只是退化成重新解析一次，
 *   **绝不影响能不能打开这本书**（所有读写都在 `runCatching` 里）。
 *
 * 文本格式（人眼可读，和 `history.txt` 一样便于排查）：
 *
 * ```
 * version=1
 * generation=1f3c9a2b
 * title=书名
 * author=作者
 * svg=OEBPS/images/cover.svg
 * chapter=OEBPS/chap1.xhtml<TAB>第一章
 * ```
 */
internal object EpubIndex {

    /**
     * 索引文件名，放在解压目录里。
     *
     * 刻意用 `.xlreader_` 前缀：和 [EpubExtractor] 的完成标记、版本记录同族，
     * 出问题时一眼能看出是本应用的文件（解压时也会挡掉包内同名条目）。
     */
    const val FILE_NAME = ".xlreader_index"

    /** 索引格式版本。格式有变动就加一，旧索引会被自动忽略。 */
    private const val VERSION = 1

    private const val GENERATION = "generation="

    /** 章节行里「路径」与「标题」的分隔符。路径与标题都不会含它。 */
    private const val SEP = '\t'

    /**
     * 读索引；只要有任何一点对不上（版本、代次、缺字段、格式损坏）就返回 null，
     * 由调用方老老实实重新解析。
     */
    fun load(dir: File, generation: String): IndexData? {
        val file = File(dir, FILE_NAME)
        if (!file.isFile) return null

        val text = runCatching { file.readText() }.getOrNull() ?: return null

        var versionSeen = false
        var generationSeen = false
        var title: String? = null
        var author: String? = null
        val titles = ArrayList<String>()
        val paths = ArrayList<String>()
        val svg = LinkedHashSet<String>()

        for (line in text.lineSequence()) {
            if (line.isEmpty()) continue
            when {
                line.startsWith("version=") -> {
                    if (line.substringAfter('=').trim() != VERSION.toString()) return null
                    versionSeen = true
                }

                line.startsWith(GENERATION) -> {
                    // 代次对不上说明这份索引属于另一份解压结果。
                    if (line.substringAfter('=').trim() != generation) return null
                    generationSeen = true
                }

                line.startsWith("title=") -> title = line.substringAfter('=')
                line.startsWith("author=") -> author = line.substringAfter('=')
                line.startsWith("svg=") -> line.substringAfter('=')
                    .takeIf { it.isNotEmpty() }
                    ?.let { svg += it }

                line.startsWith("chapter=") -> {
                    val body = line.substringAfter('=')
                    val sep = body.indexOf(SEP)
                    if (sep <= 0) return null
                    val path = body.substring(0, sep)
                    val name = body.substring(sep + 1)
                    if (name.isEmpty()) return null
                    paths += path
                    titles += name
                }

                // 不认识的字段只可能来自另一个版本或被手改过，直接放弃。
                else -> return null
            }
        }

        if (!versionSeen || !generationSeen) return null
        if (paths.isEmpty() || paths.size != titles.size) return null
        if (title.isNullOrBlank() || author.isNullOrBlank()) return null

        return IndexData(
            title = title,
            author = author,
            chapterTitles = titles,
            chapterPaths = paths,
            svgPaths = svg,
        )
    }

    /**
     * 写索引；失败（目录不可写等）只是让下次打开变慢，**不报错**，所以返回布尔值而不是抛异常。
     */
    fun save(dir: File, generation: String, data: IndexData): Boolean = runCatching {
        val out = StringBuilder(128 + data.chapterPaths.size * 48)
        out.append("version=").append(VERSION).append('\n')
        out.append(GENERATION).append(generation).append('\n')
        out.append("title=").append(sanitize(data.title)).append('\n')
        out.append("author=").append(sanitize(data.author)).append('\n')
        for (path in data.svgPaths) {
            out.append("svg=").append(sanitize(path)).append('\n')
        }
        for (index in data.chapterPaths.indices) {
            out.append("chapter=")
                .append(sanitize(data.chapterPaths[index]))
                .append(SEP)
                .append(sanitize(data.chapterTitles.getOrElse(index) { "" }))
                .append('\n')
        }
        File(dir, FILE_NAME).writeText(out.toString())
        true
    }.getOrDefault(false)

    /** 行式格式靠换行/制表符分隔，字段里出现它们会破坏结构，写之前统一换成空格。 */
    private fun sanitize(value: String): String = value
        .replace('\n', ' ')
        .replace('\r', ' ')
        .replace('\t', ' ')
}

/** 索引里存的元数据，正好是构造 [EpubBook] 需要的那几项。 */
internal data class IndexData(
    val title: String,
    val author: String,
    val chapterTitles: List<String>,
    val chapterPaths: List<String>,
    val svgPaths: Set<String>,
)
