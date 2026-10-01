package com.xialiangok.xlreader.data.epub

/*
 * 从 XHTML/HTML 里抽出可排版的内容块（文字 + 图片）。
 *
 * 这里刻意**不用 XmlPullParser**：真实世界的 epub 里经常出现
 * 未在 DOCTYPE 中声明的实体（`&nbsp;` 之类），严格 XML 解析器遇到就会直接抛异常。
 * 手写扫描器不会抛，并且能顺带把实体解码、段落切分一次做完。
 */

/** 会开启新段落的标签。 */
private val BLOCK_TAGS = setOf(
    "p", "div", "br", "hr", "li", "tr", "td", "th", "dd", "dt",
    "section", "article", "blockquote", "figcaption", "pre", "figure", "svg",
    "h1", "h2", "h3", "h4", "h5", "h6",
)

/** 可以作为章节标题的标签。 */
private val HEADING_TAGS = setOf("h1", "h2", "h3", "h4", "h5", "h6")

/**
 * 单个段落的最大长度。
 *
 * epub 里偶尔会有整章挤在一个 `<p>` 里的排版，那种超长文本
 * 会让 Compose 一次测量极大的文本，滚动时明显卡顿。
 * 超过就按句末标点拆开，阅读体验几乎无差别，但布局压力小很多。
 */
private const val MAX_PARAGRAPH_CHARS = 1200

/** 超过这个长度的标题文本不再当作章节名（避免整段正文被误认成标题）。 */
private const val MAX_HEADING_CHARS = 80

private const val SENTENCE_ENDINGS = "。！？…；.!?;"

/** 扫描器产出的原始块；路径还没解析。 */
internal sealed interface RawBlock {
    data class Text(val text: String) : RawBlock
    data class Image(val src: String) : RawBlock
}

/*
 * 预编译的正则。
 *
 * 下面这些函数在一次「打开 + 翻章」里会被反复调用：`collapse` 每段一次、
 * `plainText` 每个目录条目一次、`attribute` 每个 <img> 一次，而 `Regex(...)`
 * 每次构造都要重新编译一个 Pattern —— 在手表上这是白白烧掉的 CPU。
 * Pattern 本身是线程安全、不可变的，所以拿顶层 val 缓存起来正合适。
 */
private val SCRIPT = Regex("(?is)<script.*?</script>")
private val STYLE = Regex("(?is)<style.*?</style>")
private val COMMENT = Regex("(?s)<!--.*?-->")
private val HEAD = Regex("(?is)<head.*?</head>")
private val ANY_TAG = Regex("(?s)<[^>]*>")
private val WHITESPACE = Regex("\\s+")

/** 按标签名缓存「取第一段文本」的正则（`title` / `creator` 用得最多）。 */
private val tagRegexes = java.util.concurrent.ConcurrentHashMap<String, Regex>()

internal fun tagRegex(localName: String): Regex = tagRegexes.computeIfAbsent(localName) { name ->
    Regex("(?is)<(?:[\\w.-]+:)?$name\\b[^>]*>(.*?)</(?:[\\w.-]+:)?$name\\s*>")
}

/** 按属性名缓存取值正则。 */
private val attributeRegexes = java.util.concurrent.ConcurrentHashMap<String, Regex>()

private fun attributeRegex(name: String): Regex = attributeRegexes.computeIfAbsent(name) { attribute ->
    Regex("(?is)(?:^|\\s)${Regex.escape(attribute)}\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')")
}

/**
 * 把一份 XHTML 抽成「章节标题 + 内容块」。
 *
 * @param fallbackTitle 文档里没有 `<title>` 也没有标题标签时使用的名字。
 */
internal fun extractBlocks(html: String, fallbackTitle: String): Pair<String, List<RawBlock>> {
    val cleaned = html
        .replace(SCRIPT, " ")
        .replace(STYLE, " ")
        .replace(COMMENT, " ")
        .replace(HEAD, " ")

    val documentTitle = firstTagText(html, "title")

    val blocks = ArrayList<RawBlock>()
    var headingTitle: String? = null
    var headingIndex = -1
    val buffer = StringBuilder()
    var blockIsHeading = false
    var index = 0

    fun flush() {
        val text = collapse(buffer.toString())
        buffer.setLength(0)
        if (text.isEmpty()) return
        if (blockIsHeading && headingTitle == null && text.length <= MAX_HEADING_CHARS) {
            headingTitle = text
            headingIndex = blocks.size
        }
        splitLong(text).forEach { blocks += RawBlock.Text(it) }
    }

    while (index < cleaned.length) {
        val ch = cleaned[index]
        if (ch != '<') {
            buffer.append(ch)
            index++
            continue
        }
        val end = cleaned.indexOf('>', index)
        if (end < 0) break
        val rawTag = cleaned.substring(index + 1, end).trim()
        val isClosing = rawTag.startsWith("/")
        // 取标签的本地名：`<h:p>` -> `p`，`</div >` -> `div`
        val name = rawTag
            .trimStart('/')
            .substringBefore(' ')
            .substringBefore('/')
            .substringAfterLast(':')
            .lowercase()

        // 图片：<img src> 与 SVG 里的 <image xlink:href> 都要接住。
        if (!isClosing && (name == "img" || name == "image")) {
            flush()
            val src = if (name == "img") {
                attribute(rawTag, "src")
            } else {
                attribute(rawTag, "xlink:href") ?: attribute(rawTag, "href")
            }
            if (!src.isNullOrBlank()) {
                blocks += RawBlock.Image(decodeEntities(src).trim())
            }
            index = end + 1
            continue
        }

        if (name in BLOCK_TAGS) {
            flush()
            // 结束标签必须复位：否则 `</h1>` 之后的正文会被持续当成标题累积。
            blockIsHeading = !isClosing && name in HEADING_TAGS
        }
        index = end + 1
    }
    flush()

    // 正文里的第一个标题比 <title> 更可靠：<title> 常常是书名而不是章节名。
    val title = headingTitle ?: documentTitle ?: fallbackTitle

    // 很多 epub 的 <title> 与正文 <h1> 是同一句话，直接留着会在开头重复显示一次。
    val headingBlock = blocks.getOrNull(headingIndex) as? RawBlock.Text
    val body: List<RawBlock> =
        if (headingBlock != null && headingBlock.text == title) {
            blocks.filterIndexed { i, _ -> i != headingIndex }
        } else {
            blocks
        }

    return title to body
}

/** 取某个标签的第一段文本内容，先做实体解码。 */
private fun firstTagText(html: String, localName: String): String? {
    val raw = tagRegex(localName).find(html)?.groupValues?.get(1) ?: return null
    return collapse(raw).ifEmpty { null }
}

/**
 * 把一小段 HTML 片段（例如目录里的 `<a>` 内容）压成纯文本。
 *
 * 与 [extractBlocks] 不同，这里不切段、不关心块级标签，
 * 只用于目录标题这种很短的片段。
 */
internal fun plainText(fragment: String): String =
    collapse(fragment.replace(ANY_TAG, " "))

/**
 * 取标签上某个属性的值。
 *
 * 属性名前面必须是空白或标签开头，否则 `data-src` 会被误当成 `src`。
 */
private fun attribute(tag: String, name: String): String? {
    val match = attributeRegex(name).find(tag) ?: return null
    return match.groupValues[1].ifEmpty { match.groupValues[2] }
}

/** 解码实体 + 折叠空白。解析器里多处要「把一段 HTML 文本压成一行」，共用这一个。 */
internal fun collapse(text: String): String =
    decodeEntities(text).replace(WHITESPACE, " ").trim()

private val NAMED_ENTITIES = mapOf(
    "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
    "nbsp" to " ", "ensp" to " ", "emsp" to " ", "thinsp" to " ", "shy" to "",
    "mdash" to "—", "ndash" to "–", "hellip" to "…", "middot" to "·",
    "ldquo" to "“", "rdquo" to "”", "lsquo" to "‘", "rsquo" to "’",
    "laquo" to "«", "raquo" to "»", "times" to "×", "divide" to "÷",
    "copy" to "©", "reg" to "®", "deg" to "°", "bull" to "•",
    "sect" to "§", "para" to "¶", "dagger" to "†", "permil" to "‰",
)

/**
 * 解码 HTML 实体。
 *
 * 认得出是实体但不认识的，直接丢掉（而不是原样保留），
 * 否则正文里会冒出一堆 `&foo;` 干扰阅读；
 * 而普通的 `&`（后面不是合法实体名）按字面保留，不会误删内容。
 */
internal fun decodeEntities(text: String): String {
    if ('&' !in text) return text
    val out = StringBuilder(text.length)
    var i = 0
    while (i < text.length) {
        val ch = text[i]
        if (ch != '&') {
            out.append(ch)
            i++
            continue
        }
        val semicolon = text.indexOf(';', i + 1)
        // 实体最长也就十来个字符，超了就当普通 & 处理。
        if (semicolon < 0 || semicolon - i > 12) {
            out.append(ch)
            i++
            continue
        }
        val body = text.substring(i + 1, semicolon)

        // 只有形如 `amp` / `#20013` / `#x4e2d` 的才算实体，否则只是普通的 & 字符。
        val isNamed = body.isNotEmpty() &&
            body[0].isLetter() &&
            body.all { it.isLetterOrDigit() }
        val isNumeric = body.startsWith("#")

        val decoded = when {
            body.startsWith("#x", ignoreCase = true) ->
                body.drop(2).toIntOrNull(16)?.let(::codePointToString)

            body.startsWith("#") ->
                body.drop(1).toIntOrNull()?.let(::codePointToString)

            isNamed -> NAMED_ENTITIES[body.lowercase()]

            else -> null
        }

        when {
            decoded != null -> {
                out.append(decoded)
                i = semicolon + 1
            }
            // 是实体但认不出（或数字非法）：整段丢掉。
            isNamed || isNumeric -> i = semicolon + 1
            // 只是普通的 &。
            else -> {
                out.append(ch)
                i++
            }
        }
    }
    return out.toString()
}

private fun codePointToString(codePoint: Int): String? = when {
    codePoint <= 0 || codePoint > 0x10FFFF -> null
    codePoint in 0xD800..0xDFFF -> null // 孤立的代理项
    else -> String(Character.toChars(codePoint))
}

/** 把超长段落按句末标点切开。 */
private fun splitLong(paragraph: String): List<String> {
    if (paragraph.length <= MAX_PARAGRAPH_CHARS) return listOf(paragraph)
    val result = ArrayList<String>()
    var start = 0
    while (start < paragraph.length) {
        val limit = minOf(start + MAX_PARAGRAPH_CHARS, paragraph.length)
        if (limit == paragraph.length) {
            result += paragraph.substring(start)
            break
        }
        var cut = -1
        for (i in limit downTo start + MAX_PARAGRAPH_CHARS / 2) {
            if (paragraph[i - 1] in SENTENCE_ENDINGS) {
                cut = i
                break
            }
        }
        if (cut <= start) cut = limit
        result += paragraph.substring(start, cut).trim()
        start = cut
    }
    return result.filter { it.isNotEmpty() }
}
