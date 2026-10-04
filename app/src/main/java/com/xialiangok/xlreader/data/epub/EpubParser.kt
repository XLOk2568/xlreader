package com.xialiangok.xlreader.data.epub

import java.io.File
import java.net.URLDecoder
import java.util.zip.ZipFile

/**
 * 一个不依赖任何第三方库的 EPUB 解析器，**先解压、再按章惰性读取**。
 *
 * 分三步：
 * 1. [EpubExtractor] 把 epub 解压到**同名目录**（已解压过就直接复用）；
 * 2. [open] 只读元数据、spine 和目录（nav / NCX），得到章节名列表，**不碰正文**；
 * 3. [loadChapter] 在真正要看某一章时才读那一个 XHTML 文件。
 *
 * 解压之后所有读取都是普通文件读取，既不用每次开 ZIP，也让图片可以直接
 * 交给 `BitmapFactory.decodeFile` 降采样，不必把压缩字节先读进内存。
 */
object EpubParser {

    /** 打开一本 epub：先确保解压完成，再解析元数据与章节结构。 */
    fun open(epubFile: File): EpubBook {
        if (!epubFile.exists()) throw EpubParseException("文件不存在")
        if (!epubFile.isFile) throw EpubParseException("不是一个文件")
        if (!epubFile.canRead()) throw EpubParseException("没有读取权限")

        // 已经解压过：先看解压目录里的解析索引，命中就**连原 epub 都不用打开**。
        // 这才是「第二次打开也该和第一次一样快」的关键：解压结果本来就在磁盘上，
        // 慢的是每次重新开 ZIP、读 OPF、读目录并跑一遍正则。
        val cachedDir = EpubExtractor.targetDirFor(epubFile)
        val cachedGeneration = EpubExtractor.generationOf(cachedDir)
        if (cachedGeneration != null) {
            EpubIndex.load(cachedDir, cachedGeneration)?.let { cached ->
                return buildBook(epubFile, cachedDir, cached)
            }
        } else if (!containsContainer(epubFile)) {
            // 还没有解压过，先花很小的代价确认它确实是 epub：否则会把一个几十 MB 的
            // 普通压缩包整个解压出来，才发现格式不对。
            // （有完整缓存时不查这一步：缓存本身就证明它当初是合规的 epub。）
            throw EpubParseException("不是有效的 EPUB：缺少 META-INF/container.xml")
        }

        val dir = EpubExtractor.ensureExtracted(epubFile)

        try {
            val opfPath = findOpfPath(File(dir, "META-INF/container.xml"))
                ?: throw EpubParseException("不是有效的 EPUB：找不到 OPF 文件")
            val opfFile = File(dir, opfPath)
            if (!opfFile.isFile) throw EpubParseException("EPUB 内部缺少 $opfPath")
            val opf = opfFile.readText()

            val opfDir = opfPath.substringBeforeLast('/', "")
            val items = parseManifest(opf)
            val spine = parseSpine(opf)
            if (spine.isEmpty()) throw EpubParseException("EPUB 的 spine 为空，无法确定阅读顺序")

            val itemsById = items.associateBy { it.id }
            val paths = spine.mapNotNull { idref ->
                itemsById[idref]?.let { resolvePath(opfDir, it.href) }
            }
            if (paths.isEmpty()) throw EpubParseException("EPUB 的 spine 指向的文件都不存在")

            // 目录只在一个文件里，读它是很便宜的；有了它章节列表才可读。
            val titled = readTitles(dir, items, opf, opfDir)
            val titles = paths.mapIndexed { position, path ->
                titled[path] ?: "第 ${position + 1} 节"
            }

            // 矢量图靠 media-type 判定最准，扩展名只作为兜底。
            val svgPaths = items
                .filter { it.mediaType.equals("image/svg+xml", ignoreCase = true) }
                .map { resolvePath(opfDir, it.href) }
                .toSet()

            val data = IndexData(
                title = firstTagText(opf, "title") ?: epubFile.nameWithoutExtension,
                author = firstTagText(opf, "creator") ?: "未知作者",
                chapterTitles = titles,
                chapterPaths = paths,
                svgPaths = svgPaths,
            )

            // 解析一次就把结果留给下次打开；写不进去也不影响这次阅读。
            EpubExtractor.generationOf(dir)?.let { generation ->
                EpubIndex.save(dir, generation, data)
            }

            return buildBook(epubFile, dir, data)
        } catch (e: EpubParseException) {
            throw e
        } catch (e: Exception) {
            throw EpubParseException(e.message ?: "文件已损坏或格式不受支持")
        }
    }

    /** 用一份元数据组装 [EpubBook]（索引命中和现场解析走的是同一条路，保证结果一致）。 */
    private fun buildBook(epubFile: File, dir: File, data: IndexData): EpubBook = EpubBook(
        sourceFile = epubFile,
        dir = dir,
        title = data.title,
        author = data.author,
        chapterTitles = data.chapterTitles,
        chapterPaths = data.chapterPaths,
        svgPaths = data.svgPaths,
    )

    /**
     * 读取某一章的内容块（耗时操作，请在 IO 线程调用）。
     *
     * 内容已经解压成普通文件，所以这里只是一次文件读取 + 一次标签扫描。
     */
    internal fun loadChapter(book: EpubBook, index: Int): List<EpubBlock> {
        val path = book.chapterPaths.getOrNull(index) ?: return emptyList()
        val file = File(book.dir, path)
        if (!file.isFile) throw EpubParseException("这一章的源文件缺失：$path")

        val baseDir = path.substringBeforeLast('/', "")
        val fallback = book.chapterTitles.getOrNull(index) ?: "第 ${index + 1} 节"
        val (_, raw) = extractBlocks(file.readText(), fallback)

        return raw.map { block ->
            when (block) {
                is RawBlock.Text -> EpubBlock.Text(block.text)

                is RawBlock.Image -> {
                    val resolved = resolvePath(baseDir, block.src)
                    val imageFile = imageFileIn(book.dir, resolved)
                    // 给用户看的还是书里写的那个名字，不带我们加的解压后缀。
                    val displayName = resolved.substringAfterLast('/')
                    when {
                        // BitmapFactory 解不了矢量图，明确告诉用户，而不是静默漏掉。
                        isSvg(resolved, book.svgPaths) ->
                            EpubBlock.Unsupported(displayName, "SVG 矢量图暂不支持")

                        !imageFile.isFile ->
                            EpubBlock.Unsupported(displayName, "图片文件缺失")

                        else -> EpubBlock.Image(imageFile)
                    }
                }
            }
        }
    }

    private fun isSvg(path: String, svgPaths: Set<String>): Boolean =
        path in svgPaths ||
            path.endsWith(".svg", ignoreCase = true) ||
            path.endsWith(".svgz", ignoreCase = true)

    /**
     * 找出图片在解压目录里的实际文件。
     *
     * 解压时图片被加了 [EpubExtractor.IMAGE_SUFFIX] 结尾（`pic.jpg` → `pic.jpg.xlr`），
     * 所以磁盘上的名字和 `<img src>` 里写的并不一样。先按原名找，找不到再试带后缀的名字。
     *
     * 「先试原名」同时照顾了升级前解压的旧缓存：那些目录里图片还是原名，照样能读。
     * 两次都找不到就返回原名那个（不存在）的 File，交给调用方报「图片文件缺失」。
     */
    private fun imageFileIn(dir: File, path: String): File {
        val plain = File(dir, path)
        if (plain.isFile) return plain
        return File(dir, path + EpubExtractor.IMAGE_SUFFIX).takeIf { it.isFile } ?: plain
    }
}

/** manifest 里的一项。 */
private data class ManifestItem(
    val id: String,
    val href: String,
    val mediaType: String,
    val properties: String,
)

/*
 * 预编译的正则。
 *
 * 这些正则都是拿来做「扫描器」的，每次打开一本书都要跑；`parseAttributes` 更是
 * 每个 `<item>` / `<itemref>` 跑一次 —— 一份清单几百项就是几百次 Pattern 编译，
 * 在手表上很吃亏。Pattern 不可变且线程安全，放顶层 val 缓存即可。
 */
private val CONTAINER_FULL_PATH = Regex("""full-path\s*=\s*"([^"]+)"""")
private val NAV_LINK = Regex("(?is)<a\\b[^>]*\\bhref\\s*=\\s*\"([^\"]+)\"[^>]*>(.*?)</a>")
private val NCX_TEXT = Regex("(?is)<text[^>]*>(.*?)</text>")
private val NCX_CONTENT = Regex("(?is)<content\\b[^>]*\\bsrc\\s*=\\s*\"([^\"]+)")
private val SPINE_TOC = Regex("(?is)<spine\\b[^>]*\\btoc\\s*=\\s*\"([^\"]+)\"")
private val MANIFEST_ITEM = Regex("(?is)<item\\b[^>]*>")
private val SPINE_ITEMREF = Regex("(?is)<itemref\\b[^>]*>")
private val ATTRIBUTE = Regex("""([\w:.-]+)\s*=\s*"([^"]*)"""")

/** 只读 ZIP 中央目录确认有 `META-INF/container.xml`；损坏的包也会返回 false。 */
private fun containsContainer(epub: File): Boolean = runCatching {
    ZipFile(epub).use { zip -> zip.getEntry("META-INF/container.xml") != null }
}.getOrDefault(false)

private fun findOpfPath(container: File): String? {    if (!container.isFile) return null
    val text = runCatching { container.readText() }.getOrNull() ?: return null
    val match = CONTAINER_FULL_PATH.find(text) ?: return null
    return match.groupValues[1].takeIf { it.isNotBlank() }
}

/**
 * 从 nav 文档（EPUB3）或 NCX（EPUB2）里取章节标题。
 *
 * 拿不到就返回空表，调用方退化为「第 N 节」——不影响能不能读，
 * 只是章节列表没那么好看。
 */
private fun readTitles(
    dir: File,
    items: List<ManifestItem>,
    opf: String,
    opfDir: String,
): Map<String, String> {
    val entries = ArrayList<Pair<String, String>>()

    // EPUB3：manifest 里 properties 含 nav 的那份文档。
    val navHref = items.firstOrNull { item ->
        item.properties.split(' ', '\t', '\n').any { it == "nav" }
    }?.href

    if (navHref != null) {
        val navPath = resolvePath(opfDir, navHref)
        val navDir = navPath.substringBeforeLast('/', "")
        runCatching { File(dir, navPath).readText() }.getOrNull()?.let { html ->
            // nav 里的链接是相对 nav 文档自身的，不是相对 OPF。
            parseNavDocument(html).forEach { (href, title) ->
                entries += resolvePath(navDir, href) to title
            }
        }
    }

    // EPUB2：NCX。
    if (entries.isEmpty()) {
        val ncxHref = items.firstOrNull { it.mediaType == "application/x-dtbncx+xml" }?.href
            ?: parseSpineTocId(opf)?.let { id -> items.firstOrNull { it.id == id }?.href }
        if (ncxHref != null) {
            val ncxPath = resolvePath(opfDir, ncxHref)
            val ncxDir = ncxPath.substringBeforeLast('/', "")
            runCatching { File(dir, ncxPath).readText() }.getOrNull()?.let { ncx ->
                parseNcx(ncx).forEach { (src, title) ->
                    entries += resolvePath(ncxDir, src) to title
                }
            }
        }
    }

    val result = HashMap<String, String>()
    for ((path, title) in entries) {
        if (path.isNotEmpty() && title.isNotBlank()) {
            // 同一章被目录引用多次时保留第一次出现的标题。
            result.putIfAbsent(path, title)
        }
    }
    return result
}

/** EPUB3 nav 文档里所有 `<a href="...">标题</a>`。 */
private fun parseNavDocument(html: String): List<Pair<String, String>> =
    NAV_LINK
        .findAll(html)
        .mapNotNull { match ->
            val title = plainText(match.groupValues[2])
            if (title.isBlank()) null else match.groupValues[1] to title
        }
        .toList()

/**
 * NCX 里的 `<navLabel><text>标题</text></navLabel><content src="..."/>`。
 *
 * navPoint 可以嵌套，简单正则配对会错位，所以改成「为每个 content 找它前面
 * 最近的一个 text」。
 */
private fun parseNcx(ncx: String): List<Pair<String, String>> {
    val labels = NCX_TEXT
        .findAll(ncx)
        .map { it.range.first to plainText(it.groupValues[1]) }
        .filter { it.second.isNotBlank() }
        .toList()

    // labels 与 content 都是按文档顺序出现的，所以用一个游标线性往下走就够了。
    // 以前对每个 content 都 `lastOrNull { ... }` 扫一遍 labels，章节目录大的书
    // （上千条 navPoint）会退化成 O(n²)，打开时白白卡住。
    var cursor = 0
    val result = ArrayList<Pair<String, String>>()
    for (match in NCX_CONTENT.findAll(ncx)) {
        val start = match.range.first
        while (cursor < labels.size && labels[cursor].first < start) cursor++
        val title = labels.getOrNull(cursor - 1)?.second
        if (!title.isNullOrBlank()) result += match.groupValues[1] to title
    }
    return result
}

/** `<spine toc="ncx-id">`。 */
private fun parseSpineTocId(opf: String): String? =
    SPINE_TOC.find(opf)?.groupValues?.get(1)

/** `<item id="x" href="y" media-type="z" properties="..."/>`。 */
private fun parseManifest(opf: String): List<ManifestItem> =
    MANIFEST_ITEM.findAll(opf).mapNotNull { match ->
        val attrs = parseAttributes(match.value)
        val id = attrs["id"] ?: return@mapNotNull null
        val href = attrs["href"] ?: return@mapNotNull null
        ManifestItem(
            id = id,
            href = href,
            mediaType = attrs["media-type"].orEmpty(),
            properties = attrs["properties"].orEmpty(),
        )
    }.toList()

/** spine 里 `<itemref idref="x"/>` 的先后顺序就是阅读顺序。 */
private fun parseSpine(opf: String): List<String> {
    val result = ArrayList<String>()
    for (match in SPINE_ITEMREF.findAll(opf)) {
        parseAttributes(match.value)["idref"]?.let { result += it }
    }
    return result
}

/** 把标签里的属性抽出来，不依赖属性顺序。 */
private fun parseAttributes(tag: String): Map<String, String> {
    val result = HashMap<String, String>()
    for (match in ATTRIBUTE.findAll(tag)) {
        result[match.groupValues[1].lowercase()] = match.groupValues[2]
    }
    return result
}

/** 取某个标签的第一段文本内容（正则按标签名缓存，见 [tagRegex]）。 */
private fun firstTagText(xml: String, localName: String): String? {
    val raw = tagRegex(localName).find(xml)?.groupValues?.get(1) ?: return null
    return collapse(raw).ifEmpty { null }
}

/**
 * 把相对路径解析成解压目录内的绝对（相对根的）路径。
 *
 * 需要处理 `../`、URL 百分号编码（`%20` 很常见）以及片段标识（`#anchor`）。
 */
private fun resolvePath(baseDir: String, href: String): String {
    val withoutFragment = href.substringBefore('#')
    val decoded = runCatching { URLDecoder.decode(withoutFragment, "UTF-8") }
        .getOrDefault(withoutFragment)

    val segments = ArrayList<String>()
    if (baseDir.isNotEmpty()) {
        baseDir.split('/').filterTo(segments) { it.isNotEmpty() }
    }
    for (segment in decoded.split('/')) {
        when (segment) {
            "", "." -> Unit
            ".." -> if (segments.isNotEmpty()) segments.removeAt(segments.size - 1)
            else -> segments += segment
        }
    }
    return segments.joinToString("/")
}
