package com.xialiangok.xlreader.data.epub

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/** 同名目录当前的状态。 */
enum class ExtractStatus {
    /** 还没有解压过。 */
    Missing,

    /** 已经有一份完整的解压结果，可以直接读。 */
    Complete,

    /** 同名目录存在，但不是本应用解压的（可能是用户自己的文件夹）。 */
    Foreign,
}

/**
 * 把 epub 解压到它**同名的目录**里，之后所有读取都走普通文件。
 *
 * 这样做的好处：
 * - 不再往应用缓存目录（`cacheDir`）里堆东西，解压结果就在用户自己的存储位置上，
 *   用户看得见、也能自己删；
 * - 按章惰性读取时直接读一个文件即可，不必每次重新开 ZIP；
 * - 图片可以直接用 `BitmapFactory.decodeFile` + `inSampleSize` 两遍读取降采样，
 *   **不必把压缩字节先读进内存**，对手表这种内存紧张的环境很关键。
 *
 * 代价是磁盘占用：解压后通常比 epub 本身大（压缩比没了）。这是刻意的取舍。
 */
internal object EpubExtractor {

    /**
     * 解压完成的标记文件；没有它说明目录不是（或不完整是）本应用解压出来的。
     *
     * 内容形如 `ok 1f3c9a2b`：后面那个是这次解压的**代次**，
     * 用来让解压目录里的解析索引（[EpubIndex]）跟着缓存走 —— 重新解压换了目录和代次，
     * 旧索引自然失效。（更早的版本只写了 `ok`，那种标记被当作代次 `ok`，索引照样能用。）
     */
    private const val MARKER = ".xlreader_complete"

    /**
     * 「这份缓存对应的是哪个版本的 epub」。
     *
     * 记录当时那个 epub 的修改时间与大小。下次打开时如果对得上，就说明缓存和原文件
     * 是同步的，**不必再问要不要更新**；对不上（原文件被换过）才问。
     *
     * 这样两种情况都能免打扰：
     * - 刚解压完 → 写完这份记录，下次直接读；
     * - 用户选了「直接阅读」→ 也记下当前版本，表示「这个版本的缓存我认了」。
     */
    private const val STAMP = ".xlreader_stamp"

    /** 解压中的临时目录前缀（换缓存时的旧目录备份也用这个前缀）。 */
    private const val STAGING_PREFIX = ".xlreader_staging-"

    /** 解压后允许的最大总字节数，防止畸形/恶意 epub 把存储撑爆。 */
    private const val MAX_TOTAL_BYTES = 1L * 1024 * 1024 * 1024

    /** 进度回调的最小间隔，避免小文件也频繁回调。 */
    private const val PROGRESS_STEP_BYTES = 256L * 1024

    private const val COPY_BUFFER = 64 * 1024

    /** 同名目录：`书.epub` → `书/`。 */
    fun targetDirFor(epub: File): File =
        File(epub.parentFile, epub.nameWithoutExtension)

    /**
     * 解压出来的图片统一加这个结尾：`pic.jpg` → `pic.jpg.xlr`。
     *
     * 加完之后文件的**最终扩展名**不再是 `jpg` 这类媒体格式，系统媒体库（相册、
     * 图片选择器）就认不出它是图片，不会把书本自带的插图收进去。
     * 刻意不写成 `pic.xlr.jpg` —— 那样结尾还是 jpg，媒体库照样会收。
     *
     * 代价是图片不能再按 `<img src>` 里的原名找，见 [EpubParser.loadChapter] 的兜底查找；
     * 正文、样式、字体等条目一律原样落盘，路径必须保持原样给解析器用。
     */
    const val IMAGE_SUFFIX = ".xlr"

    /** 会被媒体库当成图片收走的扩展名。只有它们需要加 [IMAGE_SUFFIX]。 */
    private val IMAGE_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "gif", "webp", "bmp",
        "heic", "heif", "avif", "tif", "tiff", "svg", "svgz",
    )

    /** 某个 zip 条目落盘时该用的文件名。 */
    private fun outputNameOf(entryName: String): String {
        val extension = entryName.substringAfterLast('/').substringAfterLast('.', "")
        return if (extension.lowercase() in IMAGE_EXTENSIONS) entryName + IMAGE_SUFFIX else entryName
    }

    private fun markerOf(dir: File) = File(dir, MARKER)

    /** 判断是否为一次完整的解压结果。 */
    fun isComplete(dir: File): Boolean = dir.isDirectory && markerOf(dir).isFile

    /**
     * 读取解压结果的「代次」；目录不是本应用完整解压出来的就返回 null。
     *
     * 调用方拿它去校验 [EpubIndex]：索引是这份解压结果的附属品，
     * 换了缓存（重新解压）就该换一份索引。
     */
    fun generationOf(dir: File): String? {
        if (!isComplete(dir)) return null
        val text = runCatching { markerOf(dir).readText() }.getOrNull() ?: return null
        // `ok 1f3c9a2b` → 1f3c9a2b；旧版标记 `ok` → ok。
        return text.trim()
            .split(' ', '\t', '\n', '\r')
            .lastOrNull { it.isNotEmpty() }
    }

    /** 同名目录当前处于哪种状态。 */
    fun status(epub: File): ExtractStatus {
        val target = targetDirFor(epub)
        return when {
            !target.exists() -> ExtractStatus.Missing
            isComplete(target) -> ExtractStatus.Complete
            else -> ExtractStatus.Foreign
        }
    }

    /**
     * 现有缓存是否已经对应**当前这个版本**的 epub。
     *
     * 对得上就说明不用再问用户「要不要更新缓存」——要么刚解压过，
     * 要么用户之前已经明确表示过「这个版本就用现有缓存」。
     */
    fun isUpToDate(epub: File): Boolean {
        val current = epub.lastModified()
        // 拿不到修改时间就没法比对，宁可多问一次。
        if (current <= 0L) return false

        val stamp = File(targetDirFor(epub), STAMP)
        if (!stamp.isFile) return false

        val text = runCatching { stamp.readText() }.getOrNull() ?: return false
        var recordedTime = -1L
        var recordedSize = -1L
        text.lineSequence().forEach { line ->
            val key = line.substringBefore('=', "").trim()
            val value = line.substringAfter('=', "").trim().toLongOrNull() ?: return@forEach
            when (key) {
                "mtime" -> recordedTime = value
                "size" -> recordedSize = value
            }
        }
        return recordedTime == current && recordedSize == epub.length()
    }

    /**
     * 把「当前 epub 版本」记进缓存目录。
     *
     * 刚解压完会自动调用；用户在询问页选「直接阅读」时也要调用一次，
     * 表示「这个版本的缓存我认了，下次别再问」。
     */
    fun writeStamp(epub: File): Boolean {
        val target = targetDirFor(epub)
        if (!target.isDirectory) return false
        return runCatching {
            File(target, STAMP)
                .writeText("mtime=${epub.lastModified()}\nsize=${epub.length()}\n")
            true
        }.getOrDefault(false)
    }

    /** 同名目录被别的程序占用时的提示语。 */
    fun foreignDirectoryMessage(epub: File): String =
        "同名目录已存在，但它不是本应用解压的：\n${targetDirFor(epub).name}\n" +
            "请把它改名或删除后重试。"

    /**
     * 确保解压目录存在并可用，返回该目录。
     *
     * @param force      为 true 时即使已有完整缓存也重新解压（「更新缓存」）。
     * @param onProgress 解压进度 0f..1f，会在后台线程回调；不保证每个字节都回调。
     *
     * 约定：
     * - 已有完整缓存且 `force = false` → 直接复用，**不重复解压**；
     * - 不存在 → 解压到临时目录，成功后**原子改名**为目标目录；
     * - 存在但不是本应用解压的 → 报错，**绝不删除用户的目录**（`force` 也不行）；
     * - 重新解压时，旧的 `history.txt` 会被保留下来，**阅读进度不会丢**。
     */
    fun ensureExtracted(
        epub: File,
        force: Boolean = false,
        onProgress: (Float) -> Unit = {},
    ): File {
        val target = targetDirFor(epub)

        if (!force && isComplete(target)) return target

        if (target.exists() && !isComplete(target)) {
            // 同名目录已存在但没有我们的完成标记。这可能是用户自己的文件夹，
            // 删掉它属于破坏用户数据，所以只报错，让用户自己决定怎么处理。
            throw EpubParseException(foreignDirectoryMessage(epub))
        }

        val parent = epub.parentFile
            ?: throw EpubParseException("无法确定电子书所在目录")
        if (!parent.isDirectory) throw EpubParseException("电子书所在目录不可访问")
        if (!parent.canWrite()) throw EpubParseException("没有写入权限，无法解压")

        cleanupStaleStaging(parent)
        val staging = File(parent, STAGING_PREFIX + System.nanoTime())
        try {
            if (!staging.mkdirs()) throw EpubParseException("无法创建解压目录")
            extractInto(epub, staging, onProgress)

            // 换缓存时把阅读进度带过来 —— 用户更新缓存不该丢掉读到哪儿了。
            if (target.exists()) preserveHistory(from = target, to = staging)

            // 标记写在改名之前：只有出现在目标目录里才算一次完整解压。
            // 代次随标记一起写，让解压目录里的解析索引与这份内容绑定。
            markerOf(staging).writeText("ok ${newGeneration()}\n")
        } catch (e: EpubParseException) {
            staging.deleteRecursively()
            throw e
        } catch (e: Exception) {
            staging.deleteRecursively()
            throw EpubParseException("解压失败：${e.message ?: e::class.simpleName}")
        }

        swapIn(staging = staging, target = target, parent = parent)
        // 记下这次解压对应的是哪个版本的 epub，下次就不用再问要不要更新了。
        writeStamp(epub)
        onProgress(1f)
        return target
    }

    /**
     * 增量更新解压结果：只把「和缓存里对不上」的条目补写进去，另外清掉新版里已经没有的文件。
     *
     * 为什么不让用户重新完整解压：原书更新通常只动了目录和少数章节，整本重解压要把几百 MB
     * 的正文和插图重新写一遍，在手表上是要等很久的。这里按「同名文件大小和包内条目一致就
     * 认为没变」判定条目没变，只解压变了的那些，进度条也就只反映真正要写出去的那部分。
     *
     * 缓存内容变了，完成标记会换一个新代次：绑在旧代次上的解析索引（[EpubIndex]）自然作废，
     * 下次解析会按新内容重新读一遍 OPF 与目录，不会拿着旧章节表去读新文件。
     *
     * 没有完整缓存可增量时退化成整本解压（等价于 [ensureExtracted] 的 `force = true`）。
     * 阅读进度（`history.txt`）不受影响。
     *
     * @param onProgress 进度 0f..1f，会在后台线程回调；只统计真正解压的字节。
     */
    fun updateExtracted(epub: File, onProgress: (Float) -> Unit = {}): File {
        val target = targetDirFor(epub)
        if (!isComplete(target)) return ensureExtracted(epub, force = true, onProgress = onProgress)

        // 新版里该有的相对路径（含图片加的 [IMAGE_SUFFIX]），用来找出该清掉的旧文件。
        val keep = HashSet<String>()
        // 要解压的条目：磁盘上没有，或大小和包里对不上。
        val pending = ArrayList<Pair<ZipEntry, Dest>>()
        var totalBytes = 0L

        ZipFile(epub).use { zip ->
            for (entry in entriesOf(zip)) {
                val dest = destOf(entry, target) ?: continue
                keep += dest.path
                val cached = dest.file
                if (cached.isFile && cached.length() == entry.size) continue
                pending += entry to dest
                totalBytes += entry.size.coerceAtLeast(0L)
            }

            var done = 0L
            var lastReport = 0L
            onProgress(0f)
            for ((entry, dest) in pending) {
                dest.file.parentFile?.mkdirs()
                val next = writeEntry(zip, entry, dest.file, done, lastReport, totalBytes, onProgress)
                done = next.first
                lastReport = next.second
                if (done > MAX_TOTAL_BYTES) {
                    throw EpubParseException("更新后体积异常，已中止")
                }
            }
        }

        // 新版删掉的章节、换掉的图片不能留在缓存里：留着不但白占空间，也容易让人误以为还在。
        pruneStale(target, keep)
        markerOf(target).writeText("ok ${newGeneration()}\n")
        writeStamp(epub)
        onProgress(1f)
        return target
    }

    /**
     * 把新解压结果换到目标位置。
     *
     * 已有旧目录时先把它改名成备份，而不是直接删 —— 万一新目录改名失败，
     * 还能把旧的换回去，不至于把用户的书弄成「打不开」。
     */
    private fun swapIn(staging: File, target: File, parent: File) {
        if (!target.exists()) {
            if (!staging.renameTo(target)) {
                staging.deleteRecursively()
                throw EpubParseException("无法把解压结果移动到：${target.name}")
            }
            return
        }

        val backup = File(parent, STAGING_PREFIX + "old-" + System.nanoTime())
        if (!target.renameTo(backup)) {
            staging.deleteRecursively()
            throw EpubParseException("无法替换已存在的解压目录：${target.name}")
        }
        if (!staging.renameTo(target)) {
            // 回滚：把旧目录换回去，用户至少还能继续读。
            backup.renameTo(target)
            staging.deleteRecursively()
            throw EpubParseException("无法把新解压结果移动到：${target.name}")
        }
        backup.deleteRecursively()
    }

    /** 把旧目录里的阅读进度复制到新解压结果里。 */
    private fun preserveHistory(from: File, to: File) {
        val old = File(from, ReadHistory.FILE_NAME)
        if (!old.isFile) return
        runCatching { old.copyTo(File(to, ReadHistory.FILE_NAME), overwrite = true) }
    }

    /** 清掉上次中断留下的临时目录 / 备份目录（只删我们自己的前缀）。 */
    private fun cleanupStaleStaging(parent: File) {
        val stale = parent.listFiles()?.filter {
            it.isDirectory && it.name.startsWith(STAGING_PREFIX)
        } ?: return
        for (dir in stale) dir.deleteRecursively()
    }

    private fun extractInto(epub: File, target: File, onProgress: (Float) -> Unit) {
        ZipFile(epub).use { zip ->
            val all = entriesOf(zip)

            // 总解压字节数取自 ZIP 目录，用来算百分比。
            val totalBytes = all.sumOf { if (it.isDirectory) 0L else it.size.coerceAtLeast(0L) }
            var done = 0L
            var lastReport = 0L

            onProgress(0f)

            for (entry in all) {
                val dest = destOf(entry, target) ?: continue
                dest.file.parentFile?.mkdirs()
                val next = writeEntry(
                    zip, entry, dest.file, done, lastReport, totalBytes, onProgress,
                )
                done = next.first
                lastReport = next.second
                if (done > MAX_TOTAL_BYTES) {
                    throw EpubParseException("解压后体积异常，已中止")
                }
            }
        }
    }

    /** ZIP 中央目录里的全部条目。 */
    private fun entriesOf(zip: ZipFile): List<ZipEntry> {
        val all = ArrayList<ZipEntry>()
        val iterator = zip.entries()
        while (iterator.hasMoreElements()) all += iterator.nextElement()
        return all
    }

    /** 一个 zip 条目在解压目录里的落点。 */
    private data class Dest(val path: String, val file: File)

    /**
     * 某个 zip 条目该落到哪儿；不该落盘的条目（目录、非法路径、本应用自己写的文件）返回 null。
     *
     * 解压和增量更新共用这一份判断，免得两条路对「哪个条目落在哪个文件」产生分歧。
     */
    private fun destOf(entry: ZipEntry, target: File): Dest? {
        if (entry.isDirectory) return null

        val name = entry.name.replace('\\', '/').removePrefix("./")
        if (name.isEmpty()) return null

        // 图片加 [IMAGE_SUFFIX] 落盘（免得被系统媒体库收录），其余条目原样。
        val relative = outputNameOf(name)
        val out = File(target, relative)
        // 防 Zip Slip：条目名里带 `../` 或绝对路径时会落到目录之外。
        if (!out.canonicalPath.startsWith(target.canonicalFile.path + File.separator)) {
            throw EpubParseException("EPUB 内含非法路径：${entry.name}")
        }
        // 防重名冲突：这几个文件由我们自己写，别被包里的同名条目覆盖 / 冒充。
        if (out.name == MARKER || out.name == STAMP || out.name == EpubIndex.FILE_NAME) return null

        return Dest(relative, out)
    }

    /**
     * 把一个 zip 条目写进 [out]。
     *
     * 返回「写入后累计的字节数 to 上次回调时的字节数」：进度不是每个字节都回调，
     * 攒够 [PROGRESS_STEP_BYTES] 才报一次 —— 小条目很多时报得太密，
     * 在主线程上重组进度条的次数比解压本身还费。
     */
    private fun writeEntry(
        zip: ZipFile,
        entry: ZipEntry,
        out: File,
        done: Long,
        lastReport: Long,
        totalBytes: Long,
        onProgress: (Float) -> Unit,
    ): Pair<Long, Long> {
        var written = done
        var reported = lastReport
        zip.getInputStream(entry).use { input ->
            out.outputStream().use { output ->
                val buffer = ByteArray(COPY_BUFFER)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    written += read
                    if (written - reported >= PROGRESS_STEP_BYTES) {
                        reported = written
                        onProgress(fractionOf(written, totalBytes))
                    }
                }
            }
        }
        return written to reported
    }

    /**
     * 删掉缓存目录里新版已经没有的文件，收掉因此空出来的目录。
     *
     * [keep] 是新版该有的相对路径集合（`/` 分隔）。本应用自己写的那几个文件
     * （完成标记、版本记录、解析索引、阅读进度）一律留下 —— 它们不在包里，
     * 但删了就等于把用户读到哪儿、以及下次打开的秒开能力一起删了。
     */
    private fun pruneStale(target: File, keep: Set<String>) {
        val own = setOf(MARKER, STAMP, EpubIndex.FILE_NAME, ReadHistory.FILE_NAME)
        val stale = target.walkTopDown()
            .filter { it.isFile && it.name !in own }
            .filter { it.relativeTo(target).path.replace(File.separatorChar, '/') !in keep }
            .toList()
        for (file in stale) file.delete()

        val emptyDirs = target.walkBottomUp()
            .filter { it != target && it.isDirectory && it.listFiles()?.isEmpty() == true }
            .toList()
        for (dir in emptyDirs) dir.delete()
    }

    private fun fractionOf(done: Long, total: Long): Float =
        if (total <= 0L) 0f else (done.toFloat() / total.toFloat()).coerceIn(0f, 1f)

    /**
     * 一次解压结果的代次标识。
     *
     * 只要每次解压都换一个新值就够了 —— 新目录里的索引本来就不可能残留，
     * 它在这里是给「索引属于哪份内容」留一个可校验的凭据，顺便防住手改的缓存。
     */
    private fun newGeneration(): String =
        java.util.UUID.randomUUID().toString().substringBefore('-')
}
