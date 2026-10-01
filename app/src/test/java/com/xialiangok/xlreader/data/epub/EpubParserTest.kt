package com.xialiangok.xlreader.data.epub

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * EPUB 解析器 / 解压器的单元测试。
 *
 * 这些代码只用到 `ZipFile` 和字符串处理，**不碰任何 Android API**，
 * 所以可以直接在本地 JVM 上跑（`gradlew :app:testDebugUnitTest`），
 * 不需要手表或模拟器。
 */
class EpubParserTest {

    private val workspaces = mutableListOf<File>()

    @After
    fun cleanup() {
        workspaces.forEach { runCatching { it.deleteRecursively() } }
        workspaces.clear()
    }

    // ---------- 解压到同名目录 ----------

    @Test
    fun `extracts into a same-named directory next to the epub`() {
        val book = EpubParser.open(simpleEpub("<html><body><p>甲。</p></body></html>"))

        assertEquals(
            File(book.sourceFile.parentFile, "book").absolutePath,
            book.dir.absolutePath,
        )
        assertTrue(File(book.dir, "META-INF/container.xml").isFile)
        assertTrue(File(book.dir, "OEBPS/content.opf").isFile)
    }

    /** 已经解压过就必须复用，而不是每次打开都重新解压一遍。 */
    @Test
    fun `reuses an existing extraction instead of extracting again`() {
        val epub = simpleEpub("<html><body><p>甲。</p></body></html>")
        val first = EpubParser.open(epub)

        // 直接改掉解压出来的那一章：如果第二次打开重新解压，改动就没了。
        File(first.dir, "OEBPS/chap1.xhtml")
            .writeText("<html><body><p>改过了。</p></body></html>")

        val second = EpubParser.open(epub)
        assertEquals(listOf("改过了。"), second.texts(0))
    }

    /**
     * 同名目录已存在、但没有我们的完成标记 → 有可能是用户自己的文件夹。
     * 这种情况必须报错，**绝不能删掉用户的数据**。
     */
    @Test
    fun `refuses to touch a foreign same-named directory`() {
        val epub = simpleEpub("<html><body><p>甲。</p></body></html>")
        val foreign = File(epub.parentFile, "book")
        foreign.mkdirs()
        val sentinel = File(foreign, "我的文件.txt").apply { writeText("别删我") }

        val failure = runCatching { EpubParser.open(epub) }.exceptionOrNull()
        assertTrue("应当拒绝打开，实际是 $failure", failure is EpubParseException)
        assertTrue("用户的文件不能被删除", sentinel.isFile)
        assertEquals("别删我", sentinel.readText())
    }

    /** Zip Slip：条目名带 `../` 时不能写到解压目录之外。 */
    @Test
    fun `rejects a zip slip entry and leaves nothing outside`() {
        val epub = epub(
            "META-INF/container.xml" to container("OEBPS/content.opf"),
            "OEBPS/content.opf" to opf("书", "人", listOf("c1" to "c1.xhtml"), listOf("c1")),
            "OEBPS/c1.xhtml" to "<html><body><p>甲。</p></body></html>",
            "../evil.txt" to "pwned",
        )

        val failure = runCatching { EpubParser.open(epub) }.exceptionOrNull()
        assertTrue(failure is EpubParseException)
        assertFalse("不能写到解压目录之外", File(epub.parentFile, "evil.txt").exists())
        assertFalse("失败后不应留下半成品目录", File(epub.parentFile, "book").exists())
    }

    // ---------- 打开：只解析元数据与目录 ----------

    @Test
    fun `open reads metadata and falls back to numbered chapters`() {
        val book = EpubParser.open(
            epub(
                "META-INF/container.xml" to container("OEBPS/content.opf"),
                "OEBPS/content.opf" to opf(
                    title = "测试书名",
                    author = "测试作者",
                    items = listOf("c1" to "chap1.xhtml", "c2" to "text/chap2.xhtml"),
                    spine = listOf("c1", "c2"),
                ),
                "OEBPS/chap1.xhtml" to "<html><body><p>甲。</p></body></html>",
                "OEBPS/text/chap2.xhtml" to "<html><body><p>乙。</p></body></html>",
            ),
        )

        assertEquals("测试书名", book.title)
        assertEquals("测试作者", book.author)
        assertEquals(2, book.chapterCount)
        // 没有目录信息时退化为「第 N 节」。
        assertEquals(listOf("第 1 节", "第 2 节"), book.chapterTitles)
    }

    /**
     * **惰性解析的核心保证**：`open` 不解析任何一章的正文。
     *
     * 注意解压本身会把整本书的文件都写到磁盘上（这是「不占应用缓存」的代价），
     * 但**解析**仍然只在翻到那一章时才发生，所以内存里最多只有一章的内容。
     *
     * 这里故意让第二章的文件不存在：如果 open 会去解析正文，它要么抛异常、
     * 要么得跳过那一章，两种结果都会让下面的断言失败。
     */
    @Test
    fun `open does not parse chapter bodies`() {
        val book = EpubParser.open(
            epub(
                "META-INF/container.xml" to container("OEBPS/content.opf"),
                "OEBPS/content.opf" to opf(
                    title = "书", author = "人",
                    items = listOf("c1" to "chap1.xhtml", "c2" to "missing.xhtml"),
                    spine = listOf("c1", "c2"),
                ),
                // 只有第一章存在，第二章的文件根本没打进包里。
                "OEBPS/chap1.xhtml" to "<html><body><p>甲。</p></body></html>",
            ),
        )

        assertEquals(2, book.chapterCount)
        assertEquals(listOf("甲。"), book.texts(0))
    }

    @Test
    fun `loading a missing chapter reports an error`() {
        val book = EpubParser.open(
            epub(
                "META-INF/container.xml" to container("OEBPS/content.opf"),
                "OEBPS/content.opf" to opf(
                    title = "书", author = "人",
                    items = listOf("c1" to "chap1.xhtml", "c2" to "missing.xhtml"),
                    spine = listOf("c1", "c2"),
                ),
                "OEBPS/chap1.xhtml" to "<html><body><p>甲。</p></body></html>",
            ),
        )

        val failure = runCatching { book.loadChapter(1) }.exceptionOrNull()
        assertTrue("应当抛出 EpubParseException，实际是 $failure", failure is EpubParseException)
    }

    // ---------- 章节标题：EPUB3 nav 与 EPUB2 NCX ----------

    @Test
    fun `open uses epub3 nav document titles`() {
        val book = EpubParser.open(
            epub(
                "META-INF/container.xml" to container("OEBPS/content.opf"),
                "OEBPS/content.opf" to opf(
                    title = "书", author = "人",
                    items = listOf("c1" to "chap1.xhtml", "c2" to "chap2.xhtml"),
                    spine = listOf("c1", "c2"),
                    navHref = "nav.xhtml",
                ),
                "OEBPS/nav.xhtml" to
                    "<html><body><nav epub:type=\"toc\"><ol>" +
                    "<li><a href=\"chap1.xhtml\">第一章 起</a></li>" +
                    "<li><a href=\"chap2.xhtml#top\">第二章 承</a></li>" +
                    "</ol></nav></body></html>",
                "OEBPS/chap1.xhtml" to "<html><body><p>甲。</p></body></html>",
                "OEBPS/chap2.xhtml" to "<html><body><p>乙。</p></body></html>",
            ),
        )

        assertEquals(listOf("第一章 起", "第二章 承"), book.chapterTitles)
    }

    @Test
    fun `open uses epub2 ncx titles`() {
        val book = EpubParser.open(
            epub(
                "META-INF/container.xml" to container("OEBPS/content.opf"),
                "OEBPS/content.opf" to opf(
                    title = "书", author = "人",
                    items = listOf("c1" to "chap1.xhtml", "c2" to "chap2.xhtml"),
                    spine = listOf("c1", "c2"),
                    ncxHref = "toc.ncx",
                ),
                "OEBPS/toc.ncx" to
                    "<ncx><navMap>" +
                    "<navPoint id=\"n1\"><navLabel><text>上卷</text></navLabel>" +
                    "<content src=\"chap1.xhtml\"/></navPoint>" +
                    "<navPoint id=\"n2\"><navLabel><text>下卷</text></navLabel>" +
                    "<content src=\"chap2.xhtml\"/></navPoint>" +
                    "</navMap></ncx>",
                "OEBPS/chap1.xhtml" to "<html><body><p>甲。</p></body></html>",
                "OEBPS/chap2.xhtml" to "<html><body><p>乙。</p></body></html>",
            ),
        )

        assertEquals(listOf("上卷", "下卷"), book.chapterTitles)
    }

    /** 嵌套 navPoint 时也要一一对上，不能错位。 */
    @Test
    fun `ncx nesting does not misalign titles`() {
        val book = EpubParser.open(
            epub(
                "META-INF/container.xml" to container("OEBPS/content.opf"),
                "OEBPS/content.opf" to opf(
                    title = "书", author = "人",
                    items = listOf("c1" to "a.xhtml", "c2" to "b.xhtml"),
                    spine = listOf("c1", "c2"),
                    ncxHref = "toc.ncx",
                ),
                "OEBPS/toc.ncx" to
                    "<ncx><navMap>" +
                    "<navPoint id=\"n1\"><navLabel><text>甲章</text></navLabel>" +
                    "<content src=\"a.xhtml\"/>" +
                    "<navPoint id=\"n1a\"><navLabel><text>甲章之一</text></navLabel>" +
                    "<content src=\"b.xhtml\"/></navPoint>" +
                    "</navPoint>" +
                    "</navMap></ncx>",
                "OEBPS/a.xhtml" to "<html><body><p>甲。</p></body></html>",
                "OEBPS/b.xhtml" to "<html><body><p>乙。</p></body></html>",
            ),
        )

        assertEquals(listOf("甲章", "甲章之一"), book.chapterTitles)
    }

    // ---------- 读正文 ----------

    @Test
    fun `loadChapter returns only that chapter and strips the duplicate heading`() {
        val book = EpubParser.open(
            epub(
                "META-INF/container.xml" to container("OEBPS/content.opf"),
                "OEBPS/content.opf" to opf(
                    title = "书", author = "人",
                    items = listOf("c1" to "chap1.xhtml", "c2" to "chap2.xhtml"),
                    spine = listOf("c1", "c2"),
                ),
                "OEBPS/chap1.xhtml" to
                    "<html><head><title>第一章</title></head><body>" +
                    "<h1>第一章</h1><p>正文甲。</p><p>正文乙。</p></body></html>",
                "OEBPS/chap2.xhtml" to "<html><body><p>第二章正文。</p></body></html>",
            ),
        )

        assertEquals(listOf("正文甲。", "正文乙。"), book.texts(0))
        assertEquals(listOf("第二章正文。"), book.texts(1))
    }

    /**
     * `</h1>` 之后的正文不能被继续当成标题。
     * 这是扫描器最容易写错的地方，单独固定住。
     */
    @Test
    fun `text after a closing heading tag is not treated as a heading`() {
        val book = EpubParser.open(
            simpleEpub("<html><head></head><body><h2>小标题</h2><p>这才是正文。</p></body></html>"),
        )

        assertEquals(listOf("这才是正文。"), book.texts(0))
    }

    /** 章节内部的次级标题应作为独立段落保留，而不是被吞掉。 */
    @Test
    fun `keeps later headings as body paragraphs`() {
        val book = EpubParser.open(
            simpleEpub("<html><body><h1>章名</h1><p>甲。</p><h2>节名</h2><p>乙。</p></body></html>"),
        )

        assertEquals(listOf("甲。", "节名", "乙。"), book.texts(0))
    }

    @Test
    fun `decodes entities and drops unknown ones`() {
        val book = EpubParser.open(
            simpleEpub(
                "<html><body><p>&lt;标签&gt; &amp; &nbsp;实体 &#20013; &#x6587; &foo;结束</p></body></html>",
            ),
        )

        assertEquals(listOf("<标签> & 实体 中 文 结束"), book.texts(0))
    }

    /** 普通的 `&` 不能被当成实体删掉。 */
    @Test
    fun `keeps a bare ampersand`() {
        val book = EpubParser.open(simpleEpub("<html><body><p>A & B 公司</p></body></html>"))

        assertEquals(listOf("A & B 公司"), book.texts(0))
    }

    /** spine 里的 href 要相对 OPF 所在目录解析，并处理 `../` 与百分号编码。 */
    @Test
    fun `resolves hrefs relative to the opf directory`() {
        val book = EpubParser.open(
            epub(
                "META-INF/container.xml" to container("OEBPS/sub/content.opf"),
                "OEBPS/sub/content.opf" to opf(
                    title = "书", author = "人",
                    items = listOf("c1" to "../text/chap%201.xhtml"),
                    spine = listOf("c1"),
                ),
                "OEBPS/text/chap 1.xhtml" to "<html><body><p>空格路径。</p></body></html>",
            ),
        )

        assertEquals(listOf("空格路径。"), book.texts(0))
    }

    // ---------- 图片 ----------

    @Test
    fun `img tags become image blocks in place`() {
        val epub = epub(
            "META-INF/container.xml" to container("OEBPS/content.opf"),
            "OEBPS/content.opf" to opf("书", "人", listOf("c1" to "text/c1.xhtml"), listOf("c1")),
            "OEBPS/text/c1.xhtml" to
                "<html><body><p>前</p><img src=\"../images/pic.jpg\"/><p>后</p></body></html>",
            "OEBPS/images/pic.jpg" to "not-a-real-jpeg",
        )

        val blocks = EpubParser.open(epub).loadChapter(0)
        assertEquals(3, blocks.size)
        assertEquals("前", (blocks[0] as EpubBlock.Text).text)

        val image = blocks[1] as EpubBlock.Image
        // 图片路径要相对章节所在目录解析到解压目录里。
        assertEquals("pic.jpg", image.file.name)
        assertEquals(
            File(EpubExtractor.targetDirFor(epub), "OEBPS/images/pic.jpg").absolutePath,
            image.file.absolutePath,
        )
        assertTrue(image.file.isFile)

        assertEquals("后", (blocks[2] as EpubBlock.Text).text)
    }

    /** SVG 不加载，但必须给一个明确的占位，而不是静默漏掉。 */
    @Test
    fun `svg images become unsupported blocks`() {
        val epub = epub(
            "META-INF/container.xml" to container("OEBPS/content.opf"),
            "OEBPS/content.opf" to opf("书", "人", listOf("c1" to "c1.xhtml"), listOf("c1")),
            "OEBPS/c1.xhtml" to "<html><body><img src=\"cover.svg\"/></body></html>",
            "OEBPS/cover.svg" to "<svg xmlns=\"http://www.w3.org/2000/svg\"/>",
        )

        val block = EpubParser.open(epub).loadChapter(0).single()
        assertTrue("SVG 应当是不支持块，实际 $block", block is EpubBlock.Unsupported)
        assertEquals("cover.svg", (block as EpubBlock.Unsupported).name)
    }

    /** 靠 manifest 的 media-type 判断，即使文件名没有扩展名也要认出来。 */
    @Test
    fun `svg declared in the manifest is detected without a file extension`() {
        val epub = epub(
            "META-INF/container.xml" to container("OEBPS/content.opf"),
            "OEBPS/content.opf" to opf(
                title = "书", author = "人",
                items = listOf("c1" to "c1.xhtml"),
                spine = listOf("c1"),
                extraItems = listOf(ItemSpec("art", "art/cover", "image/svg+xml")),
            ),
            "OEBPS/c1.xhtml" to "<html><body><img src=\"art/cover\"/></body></html>",
            "OEBPS/art/cover" to "<svg/>",
        )

        val block = EpubParser.open(epub).loadChapter(0).single()
        assertTrue("manifest 声明为 SVG 时也应视为不支持，实际 $block", block is EpubBlock.Unsupported)
    }

    /** EPUB3 封面常见写法：`<svg><image xlink:href="..."/></svg>`。 */
    @Test
    fun `xlink href images are recognised`() {
        val epub = epub(
            "META-INF/container.xml" to container("OEBPS/content.opf"),
            "OEBPS/content.opf" to opf("书", "人", listOf("c1" to "c1.xhtml"), listOf("c1")),
            "OEBPS/c1.xhtml" to
                "<html><body><svg viewBox=\"0 0 1 1\"><image xlink:href=\"pic.png\"/></svg></body></html>",
            "OEBPS/pic.png" to "x",
        )

        val image = EpubParser.open(epub).loadChapter(0)
            .filterIsInstance<EpubBlock.Image>().single()
        assertEquals("pic.png", image.file.name)
    }

    @Test
    fun `missing image file becomes an unsupported block`() {
        val book = EpubParser.open(
            simpleEpub("<html><body><img src=\"gone.jpg\"/></body></html>"),
        )

        val block = book.loadChapter(0).single()
        assertTrue("图片缺失也应给占位，实际 $block", block is EpubBlock.Unsupported)
    }

    // ---------- 阅读进度 history.txt ----------

    @Test
    fun `history round trips inside the book directory`() {
        val book = EpubParser.open(simpleEpub("<html><body><p>甲。</p></body></html>"))

        assertNull("还没读过时应当没有进度", ReadHistory.load(book.dir))

        ReadHistory.save(book.dir, ReadingPosition(chapter = 2, item = 7))
        assertEquals(ReadingPosition(2, 7), ReadHistory.load(book.dir))
        assertTrue(File(book.dir, "history.txt").isFile)
    }

    @Test
    fun `history survives a corrupt file`() {
        val book = EpubParser.open(simpleEpub("<html><body><p>甲。</p></body></html>"))
        File(book.dir, "history.txt").writeText("垃圾内容\nchapter=abc\nitem=??")

        assertNull(ReadHistory.load(book.dir))
    }

    /** `history.txt` 是后来写进去的，不能让它影响「已解压」的判断。 */
    @Test
    fun `history does not invalidate the extraction marker`() {
        val epub = simpleEpub("<html><body><p>甲。</p></body></html>")
        val first = EpubParser.open(epub)
        ReadHistory.save(first.dir, ReadingPosition(chapter = 1, item = 3))

        val second = EpubParser.open(epub)
        assertEquals(ReadingPosition(1, 3), ReadHistory.load(second.dir))
    }

    // ---------- 失败路径 ----------

    @Test
    fun `open rejects a zip that is not an epub`() {
        val failure = runCatching { EpubParser.open(epub("hello.txt" to "not an epub")) }
            .exceptionOrNull()
        assertTrue("应当抛出 EpubParseException，实际是 $failure", failure is EpubParseException)
    }

    /** 不是 epub 就不该留下解压目录。 */
    @Test
    fun `a rejected zip leaves no extraction directory`() {
        val epub = epub("hello.txt" to "not an epub")
        runCatching { EpubParser.open(epub) }
        assertFalse(File(epub.parentFile, "book").exists())
    }

    @Test
    fun `open rejects a file that does not exist`() {
        val failure = runCatching {
            EpubParser.open(File(System.getProperty("java.io.tmpdir"), "definitely-missing.epub"))
        }.exceptionOrNull()
        assertTrue(failure is EpubParseException)
    }

    // ---------- 缓存状态与「更新缓存」 ----------

    @Test
    fun `status goes from missing to complete`() {
        val epub = simpleEpub("<html><body><p>甲。</p></body></html>")

        assertEquals(ExtractStatus.Missing, EpubExtractor.status(epub))
        EpubParser.open(epub)
        assertEquals(ExtractStatus.Complete, EpubExtractor.status(epub))
    }

    @Test
    fun `status reports foreign for a same-named directory we did not create`() {
        val epub = simpleEpub("<html><body><p>甲。</p></body></html>")
        File(epub.parentFile, "book").mkdirs()

        assertEquals(ExtractStatus.Foreign, EpubExtractor.status(epub))
    }

    /**
     * **「更新缓存」不能弄丢阅读进度。**
     *
     * 重新解压会重建整个目录，所以 `history.txt` 必须显式带过去 ——
     * 这是用户明确担心的一点。
     */
    @Test
    fun `refresh keeps history txt`() {
        val epub = simpleEpub("<html><body><p>甲。</p></body></html>")
        val first = EpubParser.open(epub)
        ReadHistory.save(first.dir, ReadingPosition(chapter = 2, item = 9))

        EpubExtractor.ensureExtracted(epub, force = true)

        val again = EpubParser.open(epub)
        assertEquals(ReadingPosition(2, 9), ReadHistory.load(again.dir))
    }

    /** 重新解压要真的用上新内容；而不更新时仍然读旧缓存。 */
    @Test
    fun `refresh picks up changed content while keep stays on the cache`() {
        val epub = newEpubFile()
        writeEpub(epub, *simpleEntries("<html><body><p>旧内容。</p></body></html>"))
        assertEquals(listOf("旧内容。"), EpubParser.open(epub).texts(0))

        // 原文件被换成了新版本
        writeEpub(epub, *simpleEntries("<html><body><p>新内容。</p></body></html>"))

        // 不更新 → 依旧读旧缓存（这正是需要询问用户的原因）
        assertEquals(listOf("旧内容。"), EpubParser.open(epub).texts(0))

        // 更新 → 读到新内容
        EpubExtractor.ensureExtracted(epub, force = true)
        assertEquals(listOf("新内容。"), EpubParser.open(epub).texts(0))
    }

    /** 「更新缓存」同样不能碰外来同名目录。 */
    @Test
    fun `force does not allow deleting a foreign directory`() {
        val epub = simpleEpub("<html><body><p>甲。</p></body></html>")
        val foreign = File(epub.parentFile, "book")
        foreign.mkdirs()
        File(foreign, "我的文件.txt").writeText("别删我")

        val failure = runCatching { EpubExtractor.ensureExtracted(epub, force = true) }
            .exceptionOrNull()
        assertTrue("即使选择更新缓存也不能删用户目录，实际 $failure", failure is EpubParseException)
        assertEquals("别删我", File(foreign, "我的文件.txt").readText())
    }

    /** 换缓存过程中不该留下临时目录或备份目录。 */
    @Test
    fun `refresh leaves no staging or backup directories behind`() {
        val epub = simpleEpub("<html><body><p>甲。</p></body></html>")
        EpubParser.open(epub)
        EpubExtractor.ensureExtracted(epub, force = true)

        val leftovers = epub.parentFile?.listFiles()
            ?.filter { it.name.startsWith(".xlreader") }
            ?.map { it.name }
            .orEmpty()
        assertTrue("不该留下临时/备份目录：$leftovers", leftovers.isEmpty())
    }

    @Test
    fun `extraction reports progress that ends at one`() {
        val epub = simpleEpub("<html><body><p>甲。</p></body></html>")
        val seen = mutableListOf<Float>()

        EpubExtractor.ensureExtracted(epub, force = false) { seen += it }

        assertTrue("至少要回调一次", seen.isNotEmpty())
        assertEquals(0f, seen.first())
        assertEquals(1f, seen.last())
        assertTrue(
            "进度不能倒退：$seen",
            seen.zipWithNext().all { (before, after) -> after >= before },
        )
    }

    // ---------- 记住选择：不再每次打开都问 ----------

    /** 刚解压完就会记下版本，所以第二次打开直接读，不会再问。 */
    @Test
    fun `a fresh extraction is recorded so the next open does not ask`() {
        val epub = simpleEpub("<html><body><p>甲。</p></body></html>")
        assertEquals(ExtractStatus.Missing, EpubExtractor.status(epub))
        assertFalse("还没解压时无所谓最新", EpubExtractor.isUpToDate(epub))

        EpubParser.open(epub)

        assertTrue("刚解压完就应当被认为与当前版本同步", EpubExtractor.isUpToDate(epub))
    }

    /** 原文件换了版本 → 记录对不上 → 会重新问一次。 */
    @Test
    fun `changing the epub invalidates the recorded version`() {
        val epub = newEpubFile()
        writeEpub(epub, *simpleEntries("<html><body><p>短。</p></body></html>"))
        EpubParser.open(epub)
        assertTrue(EpubExtractor.isUpToDate(epub))

        // 换成一个大小明显不同的新版本，保证比对一定失配
        writeEpub(epub, *simpleEntries(longChapter()))

        assertFalse("原文件变了就应当重新询问", EpubExtractor.isUpToDate(epub))
    }

    /** 选「直接阅读」= 认可这个版本的缓存，之后就别再问了。 */
    @Test
    fun `keeping the cache records the version so it stops asking`() {
        val epub = newEpubFile()
        writeEpub(epub, *simpleEntries("<html><body><p>短。</p></body></html>"))
        EpubParser.open(epub)

        writeEpub(epub, *simpleEntries(longChapter()))
        assertFalse(EpubExtractor.isUpToDate(epub))

        // 用户在询问页选了「直接阅读」
        EpubExtractor.writeStamp(epub)

        assertTrue("选过直接阅读后就不该再问", EpubExtractor.isUpToDate(epub))
        // 而且读到的仍然是旧缓存，没有被偷偷替换
        assertEquals(listOf("短。"), EpubParser.open(epub).texts(0))
    }

    /** 选「重新解压」→ 记录更新到新版本，下次也不再问。 */
    @Test
    fun `refresh updates the recorded version`() {
        val epub = newEpubFile()
        writeEpub(epub, *simpleEntries("<html><body><p>短。</p></body></html>"))
        EpubParser.open(epub)

        writeEpub(epub, *simpleEntries(longChapter()))
        assertFalse(EpubExtractor.isUpToDate(epub))

        EpubExtractor.ensureExtracted(epub, force = true)

        assertTrue("重新解压后记录应当更新到新版本", EpubExtractor.isUpToDate(epub))
        assertEquals(listOf(longParagraph()), EpubParser.open(epub).texts(0))
    }

    /** 多了记录文件之后，仍然要算「已完整解压」，不能被误判成外来目录。 */
    @Test
    fun `the recorded version does not break the complete marker`() {
        val epub = simpleEpub("<html><body><p>甲。</p></body></html>")
        EpubParser.open(epub)

        assertEquals(ExtractStatus.Complete, EpubExtractor.status(epub))
    }

    // ---------- 打开加速：解压目录里的解析索引 ----------

    /**
     * **第二次打开不再碰原 epub。**
     *
     * 做法很直接：第一次打开会写下索引，然后把原 epub 换成一段垃圾字节。
     * 如果第二次打开还要去开 ZIP、读 OPF，这里必然报「不是有效的 EPUB」；
     * 能正常拿到书名与章节名，就说明走的是索引 + 解压结果。
     */
    @Test
    fun `a second open is served from the index without touching the epub`() {
        val epub = simpleEpub("<html><body><p>甲。</p></body></html>")
        val first = EpubParser.open(epub)
        val index = File(first.dir, EpubIndex.FILE_NAME)
        assertTrue("第一次打开应当写下索引", index.isFile)

        // 原 epub 变成一段完全不是 ZIP 的内容
        epub.writeText("这不是一个 epub")

        val second = EpubParser.open(epub)
        assertEquals(first.title, second.title)
        assertEquals(first.author, second.author)
        assertEquals(first.chapterTitles, second.chapterTitles)
        // 正文也仍然读得到（来自解压结果，而不是原文件）
        assertEquals(listOf("甲。"), second.texts(0))
    }

    /** 索引丢了、被写坏了都只是退化成重新解析一次，而且会顺手补回来。 */
    @Test
    fun `a missing or damaged index falls back to parsing and is repaired`() {
        val epub = simpleEpub("<html><body><p>甲。</p></body></html>")
        val first = EpubParser.open(epub)
        val index = File(first.dir, EpubIndex.FILE_NAME)

        index.delete()
        assertEquals(listOf("甲。"), EpubParser.open(epub).texts(0))
        assertTrue("索引应当被重新写出来", index.isFile)

        index.writeText("version=\u0000 这不是索引\n随便写点什么")
        val book = EpubParser.open(epub)
        assertEquals(first.chapterTitles, book.chapterTitles)
        assertTrue("坏索引应当被修好", index.readText().startsWith("version=1"))
    }

    /**
     * 索引跟着**解压结果**走，而不是跟着 epub 文件走。
     *
     * 用户在询问页选「直接阅读」时认可的是现有缓存，索引必须继续有效；
     * 重新解压换了目录，旧索引不能跟过来（否则会读到上一版书的章节结构）。
     */
    @Test
    fun `the index follows the extraction, not the original file`() {
        val epub = newEpubFile()
        writeEpub(epub, *simpleEntries("<html><body><p>短。</p></body></html>"))
        val first = EpubParser.open(epub)

        // 原文件换版本 + 用户选「直接阅读」（认可现有缓存）
        writeEpub(epub, *simpleEntries(longChapter()))
        EpubExtractor.writeStamp(epub)
        assertEquals(listOf("短。"), EpubParser.open(epub).texts(0))

        // 重新解压：新目录里不该有上一版的索引，解析结果要跟新内容一致
        EpubExtractor.ensureExtracted(epub, force = true)
        val generation = EpubExtractor.generationOf(first.dir)
        assertTrue("重新解压后应当有新的代次", generation != null)
        assertFalse(
            "旧索引不该出现在重新解压的结果里",
            File(first.dir, EpubIndex.FILE_NAME).isFile,
        )

        val refreshed = EpubParser.open(epub)
        assertEquals(listOf(longParagraph()), refreshed.texts(0))
        // 新索引的可用性：再打开一次就应当命中
        assertTrue(File(refreshed.dir, EpubIndex.FILE_NAME).isFile)
        assertEquals(refreshed.chapterTitles, EpubParser.open(epub).chapterTitles)
    }

    /** SVG 清单必须一起存进索引，否则命中索引后会让 SVG 又变回「按扩展名猜」。 */
    @Test
    fun `the index keeps the svg list so unsupported blocks stay correct`() {
        val epub = epub(
            "META-INF/container.xml" to container("OEBPS/content.opf"),
            "OEBPS/content.opf" to opf(
                title = "书", author = "人",
                items = listOf("c1" to "c1.xhtml"),
                spine = listOf("c1"),
                extraItems = listOf(ItemSpec("art", "art/cover", "image/svg+xml")),
            ),
            "OEBPS/c1.xhtml" to "<html><body><img src=\"art/cover\"/></body></html>",
            "OEBPS/art/cover" to "<svg/>",
        )

        EpubParser.open(epub)
        val block = EpubParser.open(epub).loadChapter(0).single()
        assertTrue("索引命中时也要认得 manifest 里的 SVG，实际 $block", block is EpubBlock.Unsupported)
    }

    /** 旧版本只写了「ok」的完成标记也要能配索引，不然升级后每次打开都会白解析一遍。 */
    @Test
    fun `an old style completion marker still supports the index`() {
        val epub = simpleEpub("<html><body><p>甲。</p></body></html>")
        val first = EpubParser.open(epub)

        // 模拟上个版本留下的标记：没有代次
        File(first.dir, ".xlreader_complete").writeText("ok\n")
        val book = EpubParser.open(epub)
        assertEquals(listOf("甲。"), book.texts(0))
        assertEquals(
            "旧标记的代次取标记里的 ok",
            "ok",
            EpubExtractor.generationOf(book.dir),
        )
        assertTrue(File(book.dir, EpubIndex.FILE_NAME).isFile)
    }

    /**
     * 目录很长的 NCX：每个 `<content>` 都要配上它**前面最近**的那个 `<text>`。
     *
     * 这条同时守着「游标线性扫」的实现：取错一个位置，中段标题就会整体错位。
     */
    @Test
    fun `a long ncx keeps every title aligned`() {
        val count = 200
        val items = (0 until count).map { "c$it" to "c$it.xhtml" }
        val navPoints = (0 until count).joinToString("") { i ->
            "<navPoint id=\"n$i\"><navLabel><text>第 $i 章</text></navLabel>" +
                "<content src=\"c$i.xhtml\"/></navPoint>"
        }
        val entries = ArrayList<Pair<String, String>>()
        entries += "META-INF/container.xml" to container("OEBPS/content.opf")
        entries += "OEBPS/content.opf" to opf(
            title = "书",
            author = "人",
            items = items,
            spine = items.map { it.first },
            ncxHref = "toc.ncx",
        )
        for (i in 0 until count) {
            entries += "OEBPS/c$i.xhtml" to "<html><body><p>第 $i 段。</p></body></html>"
        }
        entries += "OEBPS/toc.ncx" to
            "<ncx xmlns=\"http://www.daisy.org/z3986/2005/ncx/\"><navMap>$navPoints</navMap></ncx>"

        val titles = EpubParser.open(epub(*entries.toTypedArray())).chapterTitles

        assertEquals(count, titles.size)
        assertEquals("第 0 章", titles.first())
        assertEquals("第 137 章", titles[137])
        assertEquals("第 199 章", titles.last())
    }

    // ---------- 测试辅助 ----------

    private fun workspace(): File {
        val dir = File.createTempFile("xlreader-test", "").apply {
            delete()
            mkdirs()
        }
        workspaces += dir
        return dir
    }

    /** 在独立工作目录里新建一本 epub 的路径（还没写内容）。 */
    private fun newEpubFile(): File = File(workspace(), "book.epub")

    /** 往指定路径写一个 epub，可重复调用以模拟「原文件更新了」。 */
    private fun writeEpub(file: File, vararg entries: Pair<String, String>) {
        ZipOutputStream(file.outputStream()).use { zip ->
            for ((name, content) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
    }

    /** 把若干文件打成一个临时 epub（放在独立工作目录里，方便连带清理解压结果）。 */
    private fun epub(vararg entries: Pair<String, String>): File =
        newEpubFile().also { writeEpub(it, *entries) }

    private fun simpleEntries(chapterHtml: String): Array<Pair<String, String>> = arrayOf(
        "META-INF/container.xml" to container("OEBPS/content.opf"),
        "OEBPS/content.opf" to opf("书", "人", listOf("c1" to "chap1.xhtml"), listOf("c1")),
        "OEBPS/chap1.xhtml" to chapterHtml,
    )

    /** 最常见的单章场景。 */
    private fun simpleEpub(chapterHtml: String): File = epub(*simpleEntries(chapterHtml))

    /** 一个明显更长的段落，用来让 epub 的大小与「短。」版本不同。 */
    private fun longParagraph(): String = "长".repeat(200) + "。"

    private fun longChapter(): String = "<html><body><p>${longParagraph()}</p></body></html>"
}

/** 只取文字块，忽略图片。 */
private fun EpubBook.texts(index: Int): List<String> =
    loadChapter(index).filterIsInstance<EpubBlock.Text>().map { it.text }

private fun container(opfPath: String): String = """
<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="$opfPath" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>
""".trimIndent()

private data class ItemSpec(
    val id: String,
    val href: String,
    val mediaType: String,
    val properties: String? = null,
)

private fun opf(
    title: String,
    author: String,
    items: List<Pair<String, String>>,
    spine: List<String>,
    navHref: String? = null,
    ncxHref: String? = null,
    extraItems: List<ItemSpec> = emptyList(),
): String = buildString {
    append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
    append("<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\" unique-identifier=\"id\">")
    append("<metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">")
    append("<dc:title>").append(title).append("</dc:title>")
    append("<dc:creator>").append(author).append("</dc:creator>")
    append("</metadata><manifest>")
    for ((id, href) in items) {
        append("<item id=\"").append(id)
            .append("\" href=\"").append(href)
            .append("\" media-type=\"application/xhtml+xml\"/>")
    }
    for (item in extraItems) {
        append("<item id=\"").append(item.id)
            .append("\" href=\"").append(item.href)
            .append("\" media-type=\"").append(item.mediaType).append("\"")
        item.properties?.let { append(" properties=\"").append(it).append("\"") }
        append("/>")
    }
    if (navHref != null) {
        append("<item id=\"nav\" href=\"").append(navHref)
            .append("\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>")
    }
    if (ncxHref != null) {
        append("<item id=\"ncx\" href=\"").append(ncxHref)
            .append("\" media-type=\"application/x-dtbncx+xml\"/>")
    }
    append("</manifest>")
    append("<spine")
    if (ncxHref != null) append(" toc=\"ncx\"")
    append(">")
    for (idref in spine) {
        append("<itemref idref=\"").append(idref).append("\"/>")
    }
    append("</spine></package>")
}
