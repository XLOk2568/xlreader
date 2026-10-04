package com.xialiangok.xlreader.data.file

import android.content.Context
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * 设置的导入导出。
 *
 * 「导出设置」把整个应用私有数据目录（`/data/user/0/<包名>`，即 `com.xialiangok.xlreader`）
 * 连同它下面的所有子文件夹、子文件一起打包成 zip；「导入设置」再把它整个解回原处。
 * 早先只打包 `shared_prefs`，但设置、解压出来的书、索引缓存都散在数据目录各处，
 * 整个目录一起带走才算真的备份完整。
 *
 * 打包解包都用 JDK 自带的 `java.util.zip`，不引第三方库 —— 与项目零依赖的取向一致。
 */
private const val PREFS_DIR = "shared_prefs"

/** 应用私有数据目录（`/data/user/0/<包名>`）。 */
fun appDataDir(context: Context): File = context.dataDir

/**
 * 列出数据目录的内容。
 *
 * 和文件浏览器那份 [listDirectory] 不同：这里**不过滤**，也不藏隐藏文件 ——
 * 既然让用户管理 data 目录，就得让他看见真实的东西。
 */
fun listDataDirectory(dir: File): List<FileEntry> =
    dir.listFiles()
        ?.map { FileEntry(it.name, it.absolutePath, it.isDirectory, if (it.isDirectory) 0L else it.length()) }
        ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        .orEmpty()

/** 上一级；到数据目录根部就停下，不允许往上翻到别的应用的数据目录去。 */
fun parentWithinDataDir(current: File, root: File): File? =
    if (current.absolutePath == root.absolutePath) null else current.parentFile

/**
 * 把整个数据目录打包到 [destDir]，返回生成的 zip。
 *
 * 文件名按用户口径是「XLreader + 年月日 + _ + 时分 + _Settings」，落在用户主文件目录
 * （内部存储根），这样紧接着的「导入设置」页在同一层就能看到它。
 */
fun exportSettings(context: Context, destDir: File): File {
    val root = appDataDir(context).canonicalFile
    val stamp = SimpleDateFormat("yyyyMMdd'_'HHmm", Locale.US).format(Date())
    val target = File(destDir, "XLreader${stamp}_Settings.zip")
    if (target.exists()) target.delete()

    ZipOutputStream(target.outputStream().buffered()).use { zip ->
        dataFiles(root).forEach { file ->
            // zip 里的路径用正斜杠；解包端也按这个约定还原。
            val name = file.relativeTo(root).path.replace(File.separatorChar, '/')
            zip.putNextEntry(ZipEntry(name))
            file.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }
    return target
}

/**
 * 递归列出数据目录里要打包的所有文件。
 *
 * 只收 canonical 路径仍在数据目录内的东西：`lib` 是系统指到 `/data/app/.../lib` 的符号链接，
 * 跟着走会把整个原生库目录也塞进包里；顺带保证 zip 条目名一定落在数据目录之内。
 */
private fun dataFiles(root: File): List<File> =
    root.listFiles()
        ?.sortedBy { it.name }
        ?.flatMap { file ->
            when {
                file.isDirectory -> {
                    val canonical = runCatching { file.canonicalFile }.getOrNull()
                    if (canonical != null && canonical.startsWith(root)) dataFiles(canonical) else emptyList()
                }
                file.isFile -> listOf(file)
                else -> emptyList()
            }
        }
        .orEmpty()

/**
 * 把 zip 里的内容解压回数据目录（覆盖同名文件），返回还原的文件数。
 *
 * zip 里是什么路径就还原成什么路径（整个数据目录的镜像），但逐项做路径检查：
 * 条目必须 canonical 后仍落在数据目录内，`../` 跑不到外面去（zip-slip）。
 *
 * 解压只是把文件放回原位，**内存里那份 SharedPreferences 还是旧值**，所以最后要把
 * [PREFS_DIR] 下的 XML 再按原样写回一次（见 [applyPrefs]），不重启就能生效。
 */
fun importSettings(context: Context, zipFile: File): Int {
    val root = appDataDir(context).canonicalFile
    var restored = 0

    ZipFile(zipFile).use { zip ->
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (entry.isDirectory) continue

            val name = entry.name.replace('\\', '/')
            val target = File(root, name).canonicalFile
            if (!target.path.startsWith(root.path + File.separator)) continue

            target.parentFile?.mkdirs()
            zip.getInputStream(entry).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            restored++
        }
    }

    applyPrefs(context)
    return restored
}

/**
 * 把 `shared_prefs` 里每个 XML 的内容写回对应的 SharedPreferences。
 *
 * 为什么要多这一步：SharedPreferences 的实例是**进程内单例**，值读进内存后就一直用那份，
 * 我们刚刚从外部覆盖了它的 XML 文件，内存里的旧值不会自己刷新；只有再 `put` 一次
 * 才会走「编辑 → 内存 + 落盘」那条正路。键值只按文件里有的写，所以没被导入的键保持原样。
 */
private fun applyPrefs(context: Context) {
    val dir = File(appDataDir(context), PREFS_DIR)
    dir.listFiles()?.filter { it.isFile && it.name.endsWith(".xml") }?.forEach { file ->
        val values = runCatching { readPrefsXml(file) }.getOrDefault(emptyMap())
        if (values.isEmpty()) return@forEach

        val editor = context
            .getSharedPreferences(file.name.removeSuffix(".xml"), Context.MODE_PRIVATE)
            .edit()
        for ((key, value) in values) {
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
        // 用 commit 而不是 apply：导入是用户显式点的动作，落盘要看得见结果。
        editor.commit()
    }
}

/** 读一份 SharedPreferences 的 XML（`<map>` 下按类型分标签，带 name 属性），只取键值。 */
private fun readPrefsXml(file: File): Map<String, Any> {
    val values = mutableMapOf<String, Any>()

    file.inputStream().use { input ->
        val parser = Xml.newPullParser()
        parser.setInput(input, null)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                val name = parser.getAttributeValue(null, "name")
                val value = parser.getAttributeValue(null, "value")
                when (parser.name) {
                    "string" -> if (name != null) values[name] = parser.nextText()
                    "int" -> if (name != null) values[name] = value?.toIntOrNull() ?: 0
                    "long" -> if (name != null) values[name] = value?.toLongOrNull() ?: 0L
                    "float" -> if (name != null) values[name] = value?.toFloatOrNull() ?: 0f
                    "boolean" -> if (name != null) values[name] = value == "true"

                    // 字符串集合：`<set>` 里包着一串 `<string>`。
                    "set" -> if (name != null) {
                        val set = mutableSetOf<String>()
                        loop@ while (true) {
                            when (parser.next()) {
                                XmlPullParser.START_TAG ->
                                    if (parser.name == "string") set.add(parser.nextText())

                                XmlPullParser.END_TAG ->
                                    if (parser.name == "set") break@loop

                                XmlPullParser.END_DOCUMENT -> break@loop
                                else -> Unit
                            }
                        }
                        values[name] = set
                    }
                }
            }
            event = parser.next()
        }
    }

    return values
}
