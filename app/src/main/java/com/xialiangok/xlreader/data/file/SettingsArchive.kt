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
 * 本应用的设置只有两处落盘的文件：`shared_prefs/xlreader_settings.xml`（字号、段间距、
 * 上次目录…）与 `shared_prefs/xlreader_sensor.xml`（体感手势那一份 JSON）。
 * 所以「导出设置」就是把 `shared_prefs` 整个打包成 zip，「导入设置」就是把它解回原处。
 *
 * 打包解包都用 JDK 自带的 `java.util.zip`，不引第三方库 —— 与项目零依赖的取向一致。
 */
private const val PREFS_DIR = "shared_prefs"

/** 只允许还原这两类目录：设置文件本身，以及应用自己放在 files 下的东西。 */
private val RESTORE_PREFIXES = listOf("$PREFS_DIR/", "files/")

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
 * 把设置打包到 [destDir]，返回生成的 zip。
 *
 * 文件名按用户口径是「XLreader + 年月日时分」，落在用户主文件目录（内部存储根），
 * 这样紧接着的「导入设置」页在同一层就能看到它。
 */
fun exportSettings(context: Context, destDir: File): File {
    val stamp = SimpleDateFormat("yyyyMMddHHmm", Locale.US).format(Date())
    val target = File(destDir, "XLreader$stamp.zip")
    if (target.exists()) target.delete()

    ZipOutputStream(target.outputStream().buffered()).use { zip ->
        val prefsDir = File(appDataDir(context), PREFS_DIR)
        prefsDir.listFiles()
            ?.filter { it.isFile }
            ?.sortedBy { it.name }
            ?.forEach { file ->
                zip.putNextEntry(ZipEntry("$PREFS_DIR/${file.name}"))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
    }
    return target
}

/**
 * 把 zip 里的内容解压回数据目录（覆盖同名文件），返回还原的文件数。
 *
 * 只认 [RESTORE_PREFIXES] 这两类路径，且逐项做路径检查：zip 里的 `../` 不能跑到
 * 数据目录外面去（zip-slip）。
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
            if (RESTORE_PREFIXES.none { name.startsWith(it) }) continue

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
