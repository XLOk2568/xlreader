# XLreader 项目文件说明

此文档由助理自动生成，包含仓库中主要 Kotlin 源文件的逐项说明（作用、主要类型/函数、实现细节与维护建议）。目标是作为维护参考保存到仓库根目录，文件名 info.md。

\---

## 概览

仓库主要用 Kotlin 开发，包含下面几个主要包：

* data：持久化、偏好、文件浏览等基础逻辑。
* data/epub：EPUB 解压、解析、按章读取与相关索引/缓存/历史。
* data/file：文件系统与用户存储相关工具。
* data/sensor：体感手势模型、采样与判定、录制与持久化。
* presentation：Compose UI 根组件与手势分发逻辑。
* presentation/screens：各页面的 Compose 实现（阅读、目录、设置、手势、数据管理等）。
* presentation/preview：预览用例。

下文按照文件路径列出每个 .kt 的说明，维护时建议把本文件与代码一并更新。

\---

## app/src/main/java/com/xialiangok/xlreader/data/OpenSourceLicenses.kt

* 作用：列出应用随包一起分发或参考的开源项目（用于“关于 / 开源许可”页面）。
* 主要符号：`OpenSourceProject` (data class)、`bundledOpenSourceProjects` 列表、`BUILD\\\_REFERENCE\\\_NOTE` 常量。
* 细节/注意：清单来源注明为从 release APK 的 META-INF 中核对；维护时若增加/删除依赖，应同步更新此列表以保证界面一致性。

\---

## app/src/main/java/com/xialiangok/xlreader/data/ReaderPreferences.kt

* 作用：封装阅读器的全部偏好（字号、段距、常亮、文字颜色、亮度等），并提供纯函数工具（颜色打包、步进器等）。
* 主要符号：`ReaderPreferences`、`applyBrightness`、`packRgb`、`stepFontSize`、`spacingLabel`、`formatScreenBrightness` 等。
* 细节/注意：偏好值范围在 companion object 中定义；纯算法函数可用于单元测试；`textColor` 存储为低 24 位 (0xRRGGBB)，保存/读取时需注意掩码处理。

\---

## app/src/main/java/com/xialiangok/xlreader/data/SettingsStore.kt

* 作用：使用 `SharedPreferences` 持久化 `ReaderPreferences` 与浏览目录（`browser\\\_dir`）。
* 主要符号：`SettingsStore` 类，`read()` / `save()` / `readBrowserDir()` / `saveBrowserDir()`。
* 细节/注意：读取时对值做 `coerceIn`，写入采用 `edit{}`（异步 apply）。若改变存储格式，应设计迁移策略以免用户数据丢失。

\---

## app/src/main/java/com/xialiangok/xlreader/data/file/LocalFiles.kt

* 作用：文件浏览与文件类型判断工具，供主页文件浏览与 data 目录管理使用。
* 主要符号：`FileEntry` (数据类)、`hasAllFilesAccess()`、`defaultRootPath()`、`File.isReadableBook()`、`listDirectory()`、`parentWithinRoot()`、`displayPath()`、`formatSize()`。
* 细节/注意：`listDirectory` 仅返回子目录和 `.epub` 文件以适配手表小屏；`hasAllFilesAccess()` 依赖 API 30 的 `Environment.isExternalStorageManager()`。

\---

## app/src/main/java/com/xialiangok/xlreader/data/file/SettingsArchive.kt

* 作用：实现设置导出/导入（把 `shared\\\_prefs` 打包成 zip 或从 zip 解回），并把导入后的 XML 写回 `SharedPreferences` 内存实例。
* 主要符号：`exportSettings(context,destDir): File`、`importSettings(context,zipFile): Int`、`applyPrefs(context)`、`readPrefsXml(file)`。
* 细节/注意：导入会严格校验 zip 条目路径以防 Zip Slip；导入后调用 `applyPrefs` 用 `commit()` 将值同步落盘以便立即生效。

\---

## app/src/main/java/com/xialiangok/xlreader/data/epub/EpubBlock.kt

* 作用：定义章节内容的块模型（文本、图片、不可渲染项）。
* 主要符号：`sealed interface EpubBlock` 与子类 `Text`/`Image`/`Unsupported`。
* 细节/注意：图片以解压后的 `File` 引用，`Unsupported` 用于明确提示不支持（如 SVG）。

\---

## app/src/main/java/com/xialiangok/xlreader/data/epub/EpubModels.kt

* 作用：`EpubBook` 模型与 `EpubParseException`。`EpubBook` 仅持有元数据（title/author/章节路径），正文按章读取。
* 主要符号：`EpubBook`、`EpubParseException`。
* 细节/注意：按章惰性读取对手表内存友好；`loadChapter()` 委托给 `EpubParser`。

\---

## app/src/main/java/com/xialiangok/xlreader/data/epub/EpubIndex.kt

* 作用：在解压目录保存/读取索引（`.xlreader\\\_index`），缓存解析结果以加速后续打开。
* 主要符号：`EpubIndex` 对象、`IndexData` 数据类、`FILE\\\_NAME` 常量与 `load`/`save`。
* 细节/注意：索引只存元数据并绑定解压代次（generation）；格式有版本号，格式变动应升级 `VERSION` 并兼容或忽略旧索引。

\---

## app/src/main/java/com/xialiangok/xlreader/data/epub/EpubExtractor.kt

* 作用：把 epub 解压到与 epub 同名目录，管理解压标记、代次、缓存有效性与进度回调。
* 主要符号：`EpubExtractor.ensureExtracted`、`targetDirFor`、`isComplete`、`generationOf`、`isUpToDate`、`writeStamp` 等。
* 细节/注意：

  * 解压到同名目录（便于用户查看/删除），代价是更多磁盘使用；
  * 使用 `.xlreader\\\_complete` 标记与 `.xlreader\\\_stamp` 记录版本信息；
  * 有安全保护：Zip Slip 检查、MAX\_TOTAL\_BYTES 限制、阶段性进度回调；
  * 解压失败/回滚逻辑与目录替换机制考虑了数据安全（先改名备份再替换）。

\---

## app/src/main/java/com/xialiangok/xlreader/data/epub/EpubParser.kt

* 作用：EPUB 解析器——解析元数据（OPF/manifest/spine/nav/NCX）并按章惰性读取正文（用 Xhtml 扫描器生成块列表）。
* 主要符号：`EpubParser.open(epubFile): EpubBook`、`loadChapter(book,index): List<EpubBlock>`、内部解析函数（manifest/spine/nav/ncx/resolvePath 等）。
* 细节/注意：

  * 不依赖第三方库，解析与解压设计对手表友好；
  * 打开时优先尝试读取 `EpubIndex`，避免每次都重解析 zip；
  * `loadChapter` 会把 XHTML 转成 `EpubBlock` 列表（文本/图片/unsupported）；
  * 解析使用大量正则与字符串处理，改动需配套大量 epub 测试样本。

\---

## app/src/main/java/com/xialiangok/xlreader/data/epub/Xhtml.kt

* 作用：把 XHTML/HTML 转换成可排版的块（文本与图片），并做实体解码、段落拆分与长段落分割。
* 主要符号：`extractBlocks(html,fallbackTitle)`、`plainText`、`collapse`、`decodeEntities`、`splitLong` 等。
* 细节/注意：

  * 为了容错真实世界 epub，刻意不用严格 XML 解析器，使用自写扫描与预编译的正则（缓存 Regex）；
  * 对超长段落按句末标点拆分以避免 Compose 布局卡顿；
  * 实体解析可识别常用命名实体与数字实体，不识别的实体会丢弃以免正文出现 `\\\&foo;`。

\---

## app/src/main/java/com/xialiangok/xlreader/data/epub/ReadHistory.kt

* 作用：读取与写入每本书目录下的 `history.txt`（记录上次读到的章节和条目），格式简单可人眼编辑。
* 主要符号：`ReadingPosition`、`ReadHistory.load`、`ReadHistory.save`。
* 细节/注意：写失败静默处理（不会影响阅读），放在书目录下便于随书管理。

\---

## app/src/main/java/com/xialiangok/xlreader/data/sensor/SensorGesture.kt

* 作用：定义手势模型、动作枚举与手势设置数据类（包含默认值与范围约束）。
* 主要符号：`GestureAction`、`GesturePage`、`MultiGestureMode`、`SensorGesture`、`SensorSettings`，并含与角度、速度、节流相关的工具函数（`smoothTilt`、`tiltRoll`、`tiltPitch`、`summarizeTilt`、`gestureTolerance`、`.matches()` 等）。
* 细节/注意：数据结构清晰，保存四个角度边界便于手动查看与迁移；函数带有合理容差与约束。

\---

## app/src/main/java/com/xialiangok/xlreader/data/sensor/SensorStore.kt

* 作用：把 `SensorSettings` 序列化为 JSON 存入 `SharedPreferences`，并能容错地解析旧/损坏设置回默认值或夹位。
* 主要符号：`SensorStore.read()` / `SensorStore.save()`、`encode()` / `decode()`。
* 细节/注意：使用 `org.json` 手动序列化（无第三方依赖）；`decode` 在字段缺失或非法时做合理回退，避免一份坏设置让应用行为异常。

\---

## app/src/main/java/com/xialiangok/xlreader/data/sensor/TiltSensor.kt

* 作用：实现基于加速度计的倾斜手势检测（`TiltGestureDetector` 与录制用 `TiltRecorder`），并提供 `hasAccelerometer(context)` 辅助。
* 主要符号：`TiltGestureDetector`（start/stop、onSensorChanged 节流与匹配逻辑）、`TiltRecorder`（用于录制采样）、`dueSampleTimeMs` 等。
* 细节/注意：

  * 仅注册 accelerometer，并在应用层再次节流（因为设备 ODR 未必符合建议采样率）；
  * 支持多手势策略（默认只执行第一个命中项，也可 simultaneous 或 sequential）；
  * 录制器最多收 `MAX\\\_SAMPLES`，并提供 `result()` 返回角度范围。

\---

## app/src/main/java/com/xialiangok/xlreader/presentation/ActivityExt.kt

* 作用：从可能被 `ContextWrapper` 包裹的 `Context` 中剥离出真实 `Activity`（用于需要 Activity 的操作，例如调整屏幕亮度或跳转设置）。
* 主要符号：`Context.findActivity()` 扩展函数。

\---

## app/src/main/java/com/xialiangok/xlreader/presentation/GestureActions.kt

* 作用：页面与根组件间的手势回调注册/分发机制；包含 `GestureActionHolder`、`LocalGestureActions`、`BindGestureActions` 以及手势辅助函数（`centeredItemKey`、`scrollByItems`）。
* 主要符号：`GestureActionHolder`、`BindGestureActions`、`centeredItemKey`、`scrollByItems`。
* 细节/注意：`BindGestureActions` 在页面组合生命周期内绑定并在退出时解绑，避免回调泄漏；`centeredItemKey` 用来获取屏幕正中央项以支持手势触发中心项操作。

\---

## app/src/main/java/com/xialiangok/xlreader/presentation/MainActivity.kt

* 作用：应用唯一 Activity，安装纯黑 SplashScreen，提取 ACTION\_VIEW 的 incomingUri 并把 `SettingsStore` 传入 Compose 根组件。
* 主要符号：`MainActivity.onCreate()`。
* 细节/注意：纯黑启动屏避免闪白，incomingUri 支持从外部文件管理器直接打开 epub。

\---

## app/src/main/java/com/xialiangok/xlreader/presentation/XlReaderApp.kt

* 作用：应用根组件与路由控制中心，管理偏好、传感器设置、Epub 打开/解压/解析流程、手势分发、页面路由与持久化。
* 主要符号：`sealed interface Route`、`XlReaderApp(store, incomingUri)` 主 Compose、若干私有状态类型 `BookState` / `BookAction` / `BookRequest`、辅助函数（`requestBook`,`openForReading`,`parseBook`,`runExtraction`,`materialize`,`openAppSettings`,`openAllFilesAccessSettings`）等。
* 细节/注意：

  * 负责何时注册 `TiltGestureDetector`（基于总开关 + 当前页面启用 + 个别手势启用），并把命中的手势映射到页面回调或应用层动作；
  * Epub 打开流程将 I/O 放在 `Dispatchers.IO`，并使用 `LaunchedEffect` / `rememberCoroutineScope` 做协程管理与任务隔离；
  * 进度、缓存判断与用户询问页面（CachePrompt）都在这里协调；
  * 修改任何全局行为需谨慎（生命周期、协程取消、读写并发等）。

\---

## app/src/main/java/com/xialiangok/xlreader/presentation/preview/ScreenPreviews.kt

* 作用：一组 Compose 预览函数，放在 main 源集以便在 debug/release 都能预览（R8 会移除未被引用的预览函数）。
* 主要符号：多个 `@Composable` 以 `@WearPreviewDevices` / `@WearPreviewLargeRound` 标注的预览函数，以及示例 `previewBook`。
* 细节/注意：仅在开发/IDE 中使用，不会影响正式包体积（R8 剔除）。

\---

## app/src/main/java/com/xialiangok/xlreader/presentation/WearListScreen.kt

* 作用：通用页面骨架（时间文本、LazyColumn 列表、滚动指示器、点击处理），并且追求“减少视觉动效”。
* 主要符号：`WearListScreen` 可接收 `resetKey`/`startIndex`/`onTap`/`showScrollIndicator` 等参数。
* 细节/注意：使用普通 `LazyColumn`（无缩放/渐变效果），在 `resetKey` 改变时跳回 `startIndex`；滚动指示条使用 `snap()` 而非 tween 动画。

\---

## presentation/screens 中的 UI 页面（逐项摘要）

* AboutScreen.kt：关于页，展示版本、应用说明，链接到 LicensesScreen；使用 `bundledOpenSourceProjects` 显示开源项目数量。
* HomeScreen.kt（主页 / 文件浏览）：列出当前目录的子目录与 epub 文件，支持手势（单击＝打开中心项、返回＝上一级、翻页＝按条目滚动），IO 在协程中进行。
* ChapterListScreen.kt（章节列表）：展示书名/作者/章节名，若有历史记录会显示“继续阅读”卡片并自动滚动到当前章节。
* ChapterScreen.kt（正文页）：一次加载一章，支持图片按需降采样、阅读菜单（ReaderMenuOverlay）、阅读进度写入（延时 500ms 写一次）与手势绑定（单击弹菜单、系统返回为返回文件列表等）。
* ReaderMenu.kt（阅读快捷菜单）：覆盖全屏、无淡入淡出、包含字号/段距/亮度/颜色/常亮等设置，修改后直接写回 `ReaderPreferences`；包含 `StepRow` 与 `StepButton` 的步进逻辑（长按连发）。
* SettingsScreen.kt（设置页）：调整字号/段距/显示选项，并提供进入体感手势与数据管理的入口。
* SettingsScreenDataAdmin.kt（data 目录管理）：浏览应用私有 data、复制/粘贴/删除文件，导出设置到内部存储根并支持导入 zip 覆盖。I/O 操作在协程的 IO 线程中执行。
* SensorSettings.kt（手势设置）：体感手势的总开关、检测间隔、多手势策略、启用页面、手势列表与新增入口，页面仅负责展示与修改，实际检测由根组件管理。
* EpubCacheScreens.kt（解压 / 缓存询问）：`ExtractingScreen` 显示解压进度，`CachePromptScreen` 提示已有缓存并提供“直接阅读 / 重新解压 / 取消”选项。
* LicensesScreen.kt：列出开源项目卡片。
* NoticeScreen.kt：通用信息页（标题 + 说明 + 可选按钮），用于加载中、错误、权限引导等。
* PermissionScreen.kt：引导用户去系统设置打开「所有文件访问」权限。

> 注：某些 screen 文件（如 `SensorGestureRecordScreen`、`SensorGestureDetailScreen` 等）已在 `SensorSettings.kt` 或 `XlReaderApp` 中被引用／实现，若需要我可以把这些文件的源码说明逐一补上到本文档。

\---

## 全局维护建议

1. 解析与解压（EpubExtractor / EpubParser / Xhtml）是关键且最容易遇到格式差异与性能瓶颈的部分，改动后请用大量真实 epub 做回归测试（尤其在手表环境下）。
2. 持久化结构变更需设计迁移策略（SettingsStore、SensorStore、EpubIndex）。
3. 体感手势与传感器逻辑涉及能耗与时序（TiltGestureDetector、TiltRecorder），上线前务必做能耗与交互测试。
4. UI 设计强调“减少动效”，若要引入动画请评估对可访问性与功耗的影响。
5. 纯函数（颜色处理、步进器、角度运算等）都很适合编写单元测试，建议补齐单元测试以保证后续改动安全。

\---

（此 info.md 由助手根据仓库源码自动生成。如需我把它保存到仓库并提交为 info.md，请确认：要提交到默认分支（通常是 main）还是新分支？需要自定义提交信息吗？）

