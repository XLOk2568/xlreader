# XLreader

一个面向 **Wear OS** 的 epub 阅读器：打开就是你自己存储里的文件列表，点一本 epub 就能读。
纯黑底、灰阶文字、单一低饱和强调色，列表与按钮**不做缩放 / 渐变 / 位移动画**，
滚动跟手、省电、长时间阅读不刺眼。

* 包名：`com.xialiangok.xlreader`
* 最低支持：**Android 11（API 30）**，即 Wear OS 3
* 编译 SDK：36.1　目标 SDK：36
* 界面：Jetpack Compose for Wear OS（Material 3）
* 体积：**Release APK 2.05 MB**（AS 默认构建的 release 是 26.4 MB）
* 联网：**完全不联网**；唯一的敏感权限是「所有文件访问」，详见下文
* 电子书处理：**零第三方库**（见下文「EPUB 解析」一节）
* 打开方式：解压到**与原书同名的同级目录**再读（不占用应用缓存），阅读位置记在该目录的 `history.txt`
* 打开速度：**解析结果也缓存在同一个目录里**（`.xlreader_index`），所以第二次以后打开不再重新解析、连原 epub 都不碰
* 正文：文字 + 图片混排；**SVG 不加载**，显示为「不支持」占位
* 目录：正文页的「打开目录」才进章节目录；**目录里返回 = 关掉目录、回到进来时那一章**（不再一路退到文件列表），要离开这本书请用目录页底部的「返回文件列表」；若是**从阅读菜单**进的目录，返回时还会**把那个菜单重新打开**（见「正文页的出口」）
* 首次使用请ADB键入 adb shell appops set --uid com.xialiangok.xlreader MANAGE\_EXTERNAL\_STORAGE allow

\---

## 一、体积与流畅度（重点）

参照 [dertefter/WearFiles](https://github.com/dertefter/WearFiles) 的构建配置，做了两件事。

### 1\. 打开 AGP 9 的 `optimization`（最主要的一步）

Android Studio 的 Wear 模板默认给 release 写了 `optimization { enable = false }`，
debug 则完全没有这个块 —— 结果就是**装到手表上的是完全未压缩、未优化的字节码**，
这既是体积大的原因，也是「有点卡」的主因。

现在 **只在 release 上打开**：

```kotlin
buildTypes {
    debug   { optimization { enable = false } }   // 保持标准开发体验
    release { optimization { enable = true }  }   // 体积 + 流畅度收益在这里
}
```

`optimization.enable = true` 一次性打开三件事：R8 代码压缩/优化、资源压缩、
以及**自动合并依赖库自带的 baseline profile**。

> ⚠️ \*\*不要把 debug 也打开。\*\* 我一度在 debug 上也设了 `true`，代价是：
> Android Studio 里 Compose 预览渲染不出来，从 AS 直接 Run 装上的 debug 包启动即闪退，
> 断点调试也不可靠。原因就是 R8 处理过的字节码会破坏预览（Layoutlib）与调试链路。
> 加上 `android.r8.gradual.support` 本身还是实验性开关（AGP 会打印 WARNING），
> 更不该用在日常调试的变体上。

> AGP 9.2 要求先显式声明 `android.r8.gradual.support=true`（已写入 `gradle.properties`），
> 否则报 `Cannot use optimization.enable=true without setting android.r8.gradual.support flag`。

### 2\. 效果（实测）

||未优化（AS 默认）|release（本配置）|
|-|-|-|
|debug APK|25.0 MB（8 个 dex）|25.0 MB（标准开发包，不作优化）|
|**release APK**|26.4 MB|**2.05 MB**|
|release dex|25.6 MB / 7 个|**1.81 MB / 1 个**|
|baseline profile|无|**有**（`assets/dexopt/baseline.prof`）|

**要看体积与流畅度，请认准 release 包。** debug 包大且慢是 Android Studio 开发的正常状态。

> 体感手势（2026-10）把 release 从 1.78 MB 推到 2.05 MB：其中约 **0.22 MB** 来自手势详情页那个
> **自由改名的输入框** —— 它会把系统 IME / 文本编辑那一整套代码从「被 R8 剥掉」变成「被保留」
> （依赖本身早就在，`androidx.emoji2` / `autofill` / `appcompat-resources` 这些版本标记改动前后
> 一模一样）。手势检测引擎与四个新页面本身只占约 0.05 MB；不需要自由改名的话，换成一组预设名字
> 就能把这 0.22 MB 省回来。

### 3\. 为什么 release 比 debug 更流畅

两个变体的差别不只是体积，还有 **baseline profile 只有 release 才会编译进去**
（`compileReleaseArtProfile` 任务）。它让 ART 对热点路径提前做 AOT 编译，
而不是边跑边解释——这在手表这种小核心、低功耗的芯片上差别很明显。

**所以：想要顺畅，请装 release 包，不要装 debug 包。**

```powershell
adb install -r app\\build\\outputs\\apk\\release\\app-release.apk
```

在 Android Studio 里想直接跑 release，把左下角 **Build Variants** 面板切成 `release` 再点 ▶ 即可
（release 的签名配置在没有自己的 keystore 时会回退到 debug 签名，所以能直接装上去）。

> 建议：\*\*平时开发和看预览用 `debug` 变体，只有要判断「顺不顺」时才切到 `release`。\*\*
> 预览在 `debug` 变体下最稳定。

首次启动时 `ProfileInstaller` 会把 profile 装进 ART，**第二次启动起会明显更顺**。

### 4\. 代码层面的流畅度处理

* **`@Immutable` 标注 `Article`**：`paragraphs` 是 `List<String>`，Compose 默认把 `List`
当作不稳定类型，会导致列表项**永远无法跳过重组**，滚动时反复白重组。
加上注解后 Compose 可以跳过未变化的列表项。
* **`items(..., contentType = { "article" })`**：让 `LazyColumn` 复用同类列表项节点。
* **普通 `LazyColumn`**，不用带形变联动的 `TransformingLazyColumn`／`ScalingLazyColumn`，
滚动时没有逐项动画，也就没有逐帧的动画开销。

### 5\. 依赖瘦身

|处理|原因|
|-|-|
|删掉 `play-services-wearable`|代码里从未使用，却会连带引入 play-services-base / basement / tasks|
|渲染工具链 `androidx.compose.ui:ui-tooling` 放 `debugImplementation`|真正的预览渲染器体积不小，正式包里不需要|
|预览**注解**（`ui-tooling-preview`、`compose-ui-tooling`）保留在 `implementation`|`@Preview` / `@WearPreviewDevices` 写在 `src/main`，放 debug 会让 main 编译不过、预览也就没了。它们只是注解，R8 会把 release 里没被引用的部分删干净（实测体积一致）|
|删掉 `wear-tooling-preview`|预览用的是 `@WearPreviewDevices`，没有引用它|
|删掉 `androidTest` 相关依赖|项目没有测试源集|
|删掉 `WAKE\_LOCK` 权限、`useLibrary("wear-sdk")`|都没有实际使用|

> 预览函数放在 `src/main`（\[`ScreenPreviews.kt`](app/src/main/java/com/xialiangok/xlreader/presentation/preview/ScreenPreviews.kt)），
> \*\*不要\*\*放进 `src/debug`：那样只有在 Build Variants 里选中 debug 时才看得到预览，
> 一切到 release，整个文件都不在源集里，预览面板就是一片空白。

\---

## 二、功能

|页面|说明|
|-|-|
|**文件浏览（主页）**|打开即显示用户存储（`/storage/emulated/0`）的目录内容，顶部显示当前目录名与完整路径；只列子目录和 `.epub`，可逐级进入、可「上一级」返回；**点开 epub 直接进正文页**（接着上次那章，没进度就从第一章开始），不先停在目录；页面底部是「退出本应用 / 设置 / 关于」，其中**「退出本应用」会真的结束任务**（`finishAndRemoveTask`，同时从最近任务列表里移除）；**当前目录会被记住**（换目录时即写入偏好，切页面 / 退出应用都不丢），下次启动直接回到上次那个目录，目录已被删掉时退回存储根目录|
|**章节目录**|从正文页的「打开目录」进入；**只解析元数据与章节名**（优先用 epub 自带的 nav / NCX 目录标题）；打开时**自动定位到正在读的那一章**并把该章标成强调色，不用自己翻；**在这一页返回 = 关掉目录、回到刚才那一章**（不再退到文件列表；若目录是从阅读菜单进来的，返回后那个菜单还是开着的）|
|**正文**|一次只加载**一章**；文字与图片混排，图片按屏幕宽度降采样后才解码；「← 上一章 / 打开目录 / 返回文件列表 / 下一章 →」串联；**系统返回手势直接退回文件列表**（见下）；章标题行距收紧到字号的 1.1 倍，「第 N / M 节」下面一行标出**本节字数**；**不显示时间**、**不显示右侧的滚动指示条**，顶部与两侧空间全留给正文|
|**阅读菜单**|**单击正文**弹出快捷设置，再次单击（面板空白处）或点「完成」关闭；可直接改字号（数字磅值，「− / ＋」一次走一磅）、**段间距（数字，单位 dp，`− / ＋` 一次 1 dp）**、常亮、屏幕亮度、文字亮度、文字颜色，改完立刻作用于当前阅读界面；「关闭菜单」下面依序是**「打开目录」「返回文件列表」**，与正文底部那两个同名按钮走同一条出口（不用先关菜单、再滚到本章末尾才能翻目录）；从这里进目录，返回时回到的**还是这个菜单**|
|**图片**|支持 jpg / png / gif / webp；**SVG 不加载**，在原文位置显示「SVG 矢量图暂不支持」占位|
|**阅读进度**|**滚动本身不写盘**，只在四个时机把位置写进书的 `history.txt`：加载完这一章 500ms（仅一次）、单击正文弹出菜单、切换章节、离开正文页（按钮 / 返回手势）；再打开这本书时**直接回到那一章**，目录页也会自动滚到该章并显示「继续阅读」卡片|
|**缓存更新**|已有完整解压缓存、且与原文件版本对不上时，打开前会询问「重新解压 / 直接阅读」；解压过程显示百分比进度，更新缓存不会清掉阅读进度|
|**记住选择**|解压完或选过「直接阅读」后会记下当前 epub 版本，**之后不再询问**；等原文件变了才会再问一次|
|权限引导|未授权时先引导去系统设置打开「所有文件访问」，并带「已授权，重新检查」|
|设置|正文字号（磅值 1..999，`− / ＋` 一次一磅）、段间距（dp 值 0..999，`− / ＋` 一次 1 dp）、减少动效（固定为开、不可点）、阅读时常亮；下面是**「传感器设置」入口**|
|**体感手势**|设置 →「传感器设置」：**进这一页前先检查加速度计**，读不到就先给一页与「所有文件访问」同款的授权引导（「去系统设置查看 / 已授权，重新检查」，路径提示在页底）—— 注意加速度计本身**不需要运行时权限**，这一页挡住的其实是「设备没有该传感器」或「系统 / ROM 把它关了」。**新增手势**时先倒计时 **3 秒**，再在 **10 秒**内最多采 **200 个点**，期间**单击屏幕立刻结束**；两种情况都马上整理，只保存横滚/俯仰的**最大与最小角度**。每条手势可**改名**、选**动作**（退出 / 单击 / 返回 / 向下翻页 / 向上翻页，翻页类可设**速度**＝一次滚动多少条目）、**单独开关**、**删除**（详情页里还有「重新录制」）。**检测间隔**（默认 200 ms）对所有手势通用；**启用页面**可勾选「文件列表 / 章节目录 / 正文阅读」，总开关关掉、进入任何没有启用手势的页面、以及**应用退到后台或表盘息屏**，都会**立刻注销传感器**；进入启用了手势的页面时会提示一句「手势检测任务已启用」|
|关于|版本、最低系统要求、定位说明；入口通往「开源许可」|
|开源许可|列出随应用一同分发的开源项目、许可证，以及各项目在应用里的职责|

### 打开一本书，与正文页的出口

**点 epub 就直接进正文页**，不再先停在目录页等用户再点一次：打开一本书的意图就是接着读。
进哪一章跟「继续阅读」同一个口径 —— 有有效进度就接着上次那一章（章号越界、或换了版本
导致章数对不上，就当没有进度），否则从第一章开始。目录页于是变成一个**从正文页进去的
二级页面**（`Route.Book` 仍然存在，只是不再由文件列表直接进入）。

正文页不比别处，退出的口子多，所以每个口子通向哪里是明确的：

|出口|去处|备注|
|-|-|-|
|**系统返回手势**|**文件列表**|从正文页往回退，意图多半是「退出这本书」，不走目录中转|
|正文底部「打开目录」、阅读菜单「打开目录」|章节目录|两个按钮是同一条出口（`onOpenCatalog`）；**目录里再返回 = 回到刚才那一章**（`closeCatalog()`），退不回文件列表；区别只在返回时**菜单开着还是关着**——从菜单进的目录返回后菜单重新出现（菜单开关状态 `readerMenuVisible` 记在 `XlReaderApp`、进目录时不清掉）|
|正文底部「返回文件列表」、阅读菜单「返回文件列表」|文件列表|想换一本书时一步到位，省掉「先回目录、再滚到目录页最底下退出」|
|正文底部「← 上一章 / 下一章 →」|同一本书的相邻章|不离开这本书|

前三行的三个出口都要经过 `XlReaderApp.saveProgressOnLeave()` 记一次进度；
第四行「← 上一章 / 下一章 →」也在切章前记一笔，所以这两种走向都不会丢最后读到的位置。

**应用不内置任何电子书内容**，全部来自你自己的手表存储。

还注册了 epub 的 `ACTION\_VIEW`：在别的应用或文件管理器里点开 `.epub`，
可以直接用 XLreader 打开（`application/epub+zip`；另外补了一条
`application/octet-stream` + `.\*\\.epub` 的规则，兼容不带正确 MIME 的来源）。

### 权限与存储：为什么需要「所有文件访问」

这是本项目唯一需要解释的权限。

* Android 11（API 30）起，`READ\_EXTERNAL\_STORAGE` 只能读到**媒体文件**（图片/音频/视频），
读不到 `.epub` 这种普通文件。
* 官方推荐的替代方案是 SAF（`ACTION\_OPEN\_DOCUMENT\_TREE`），
但 **Wear OS 没有系统文件选择器**，调用后无人响应，应用会表现得像坏了。
* 因此按文件浏览器类应用的通行做法申请 `MANAGE\_EXTERNAL\_STORAGE`，
由用户到 **设置 → 应用 → XLreader → 所有文件访问** 显式授权。

授权前会停在引导页；授权后回到应用点「已授权，重新检查」即可。
应用不联网、不上传任何文件，这个权限只用于读取你指定的目录。

> 参考实现：\[dertefter/WearFiles](https://github.com/dertefter/WearFiles) 在 Wear OS 上同样使用
> `MANAGE\_EXTERNAL\_STORAGE`，这也是我判断 SAF 在手表上不可用的依据。

### EPUB 解析：为什么自己写

EPUB 本质就是一个 ZIP：`META-INF/container.xml` 指向 OPF 文件，OPF 里有书名/作者（metadata）、
资源清单（manifest）和阅读顺序（spine），spine 每项对应一个 XHTML 文档。用 JDK 自带的
`ZipFile` 加一点字符串处理就能读完，所以**没有引入 readium / epublib**——那会给这个
2.05 MB 的包凭空加上好几 MB。

几个刻意的取舍：

* **先解压到同名目录，再按章惰性读取。** 打开 `我的书.epub` 时：

  * 同名目录 `我的书/` 不存在 → 解压（界面上有 **百分比进度**）；
  * 已存在且是完整解压结果 → 比较「缓存记录的原文件版本」与当前 epub：

    * **一致** → 直接读，**不问**；
    * **不一致** → 问一句「重新解压 / 直接阅读」；
  * 已存在但**不是**本应用解压的 → 报错，**绝不删除用户自己的目录**（选「更新」也不行）。

  「记住选择」用的是一份版本记录：解压目录里放一个 `.xlreader\_stamp`，
记下当时那个 epub 的**修改时间与大小**。刚解压完会写一次；
用户选「直接阅读」时也写一次（表示「这个版本的缓存我认了」）。
所以只有**原文件真的被换过**才会再问，平时打开都是直接的。
同时也保证了两种情况下都**不会弄丢 `history.txt`** 里的阅读进度。

  重新解压时不是直接删掉旧目录：先把新结果解压到临时目录，把旧的 `history.txt` 复制过去，
再把旧目录改名成备份、新目录换成目标名；万一改名失败还会把备份换回去，
不至于把一本能读的书弄成打不开。

  好处是：不再往应用缓存（`cacheDir`）里堆东西，解压结果就在用户自己的存储位置上、
看得见也能自己删；按章读取时是普通文件读取，不必每次开 ZIP；
图片还能直接交给 `BitmapFactory.decodeFile` 降采样，**不用把压缩字节先读进内存**。
代价是磁盘占用——解压后通常比 epub 本身大（压缩比没了），这是刻意换来的。

* **只有「解析」是惰性的。** `open()` 只读 OPF 与目录文件得到章节名，
正文一个字都不解析；翻到某一章时才读那一个 XHTML。所以内存里最多只有一章的内容。
首次打开还要做一次全量解压（磁盘 IO），这一步没有捷径；但**第二次以后的打开
不该再付解析的代价**，见下一条。
* **解析结果缓存在解压目录里（`.xlreader_index`）。** 光有解压结果还不够快：
以前每次打开都要重开原 epub 的 ZIP、读 `container.xml`、读整份 OPF、读 nav/NCX，
再跑一遍正则——第二遍、第三遍打开一点都不比第一遍省。现在第一次解析完就把
「书名 / 作者 / 章节名 / 章节路径 / SVG 清单」写成几行文本，之后打开只读这一个小文件，
**连原 epub 都不碰**（手表上的存储走 FUSE，开一次 ZIP、读几个文件就是实打实的开销）。

  实测（600 章 + 200 张图的书，本地 JVM）：命中索引 **2–3 ms**，
  同样这本已经解压好的书走「重新解析」是 **15 ms**，而且那还没算真机上开原 ZIP 的钱。

  索引**只存元数据、不存正文**（内存占用与以前一样）；它靠解压完成标记里的「代次号」
  与这次解压结果绑定，重新解压换了目录就自然失效；文件丢了、被写坏了、格式对不上，
  都只是退化成重新解析一次并顺手补写回来，**绝不影响能不能打开这本书**。
* **正则全部预编译。** `collapse()` 每段一次、`plainText()` 每个目录条目一次、
  属性正则每个 `<img>` 一次——这些以前都是现场 `Regex(...)`（等于每次重新编译一个
  Pattern）。现在按名字缓存成顶层 val，Pattern 不可变且线程安全。
  另外 NCX 的「给每个 `<content>` 找前面最近的 `<text>`」原来是 O(n²)，
  改成游标线性扫，上千条 navPoint 的大目录不会在打开时卡一下。
* **打开流程里的磁盘探测都在 IO 线程。** `status()` 与 `isUpToDate()` 都要读原文件的
  修改时间/大小与版本记录，以前 `isUpToDate()` 是在主线程上跑的，光那几次 stat
  就够让「点下去」到「正在读取」之间卡一下。
* **防 Zip Slip、防撑爆。** 解压时逐条校验目标路径必须落在解压目录内（挡掉 `../` 这类条目），
并限制解压总量，避免畸形 epub 把存储写满。
* **正文抽取不用 `XmlPullParser`。** 真实 epub 里经常出现未在 DOCTYPE 中声明的实体
（`\&nbsp;` 之类），严格 XML 解析器遇到就直接抛异常。手写扫描器不会抛，
还能顺带把实体解码、段落切分一次做完。无法识别的命名实体会被丢掉，
但普通的 `\&`（后面不是合法实体名）按字面保留。
* **章节标题优先取 epub 自带的目录（nav / NCX）**，拿不到才退化为「第 N 节」。
单个章节内部的标题仍以正文里的第一个 `<h1>` 为准，且与章名重复时会去掉那一段。
* **超过 1200 字的段落按句末标点切开。** epub 里偶尔有整章挤在一个 `<p>` 里的排版，
那种超长文本会让 Compose 一次测量极大的文本、滚动明显卡顿。

### 图片：为什么要降采样，以及 SVG 为什么不做

`<img src>` 与 SVG 里的 `<image xlink:href>` 都会被解析成图片块，按原文位置参与排版
（以前这些标签是被静默丢掉的，正文里连占位都没有）。

**必须降采样。** 一张 1200×1600 的图解成 `ARGB\_8888` 是**约 7.7 MB**，
手表那点内存几张就爆。所以解码前先用 `inJustDecodeBounds` 读尺寸，
再按长边算 `inSampleSize`，只解到屏幕宽度量级（几百 KB）。
又因为解压后是文件路径，`decodeFile` 可以读两遍，
所以**不需要先把压缩字节整个读进内存**——这正是「解压到磁盘」换来的好处之一。

**解码只在条目进入视口时发生**（`produceState` 跟着列表项一起创建/销毁），
滚过去的图片会被回收，不会出现「翻两页就把整本书的图都解出来」。

**SVG 不加载，但一定给占位。** `BitmapFactory` 完全不能解矢量图，而 EPUB3 的封面
常常正是 `<svg><image href="cover.jpg"/></svg>` 这种写法。为了不静默漏掉，
这种情况会在原文位置显示「（SVG 矢量图暂不支持：cover.svg）」。
判定同时看两处：manifest 里的 `media-type="image/svg+xml"`，以及文件名扩展名。
（另外图片文件缺失时也会给占位，而不是留一片空白。）

### 阅读进度 history.txt

存在书的解压目录里，就是两行人眼可读的纯文本：

```
chapter=3
item=12
```

放在书自己的目录里而不是应用私有存储，好处是跟着书走：用户把目录整体删掉，
进度也一起没了，不会留下孤儿数据。

写入时机**只有四个**，滚动一个字节都不写：

|时机|说明|
|-|-|
|加载完这一章 500ms|**只记一次、不循环**（`LaunchedEffect(chapterIndex)` + `delay(500)`），先给列表时间滚到自己该在的位置|
|单击正文弹出菜单|菜单铺满整屏、把后续单击都吃掉，所以一次打开只记一次|
|切换章节|正文底部「← 上一章 / 下一章 →」在切章前先记一笔|
|离开正文页|返回目录 / 返回文件列表 / 系统返回手势三条出口|

后三处都汇到 `XlReaderApp.saveProgressOnLeave()`：位置的来源是正文页随时上报给根组件的
「第一个可见条目」（**只进内存、不写盘**），落盘用**根组件的作用域**——
正文页自己的协程活不过一次切页（一离开组合就被取消），最后那一刻的位置就写不进了。
文件损坏或缺失都当作「没有进度」，记不住位置不该影响阅读。

解压目录里由本应用写的文件一共就这几个（都是纯文本，出问题时可以直接看）：

| 文件 | 作用 | 删掉会怎样 |
|-|-|-|
| `history.txt` | 阅读进度（章号 + 段落下标） | 下次从本章开头开始 |
| `.xlreader_complete` | 解压完成标记，内含这次解压的**代次号** | 会被当成「不是本应用解压的目录」，需要重新解压 |
| `.xlreader_stamp` | 缓存对应的原 epub 版本（修改时间 + 大小） | 下次多问一句「重新解压 / 直接阅读」 |
| `.xlreader_index` | 解析结果缓存（章节名/路径/SVG 清单） | 下次打开慢一点，会自动重建 |

### 单元测试

解析器只用到 `ZipFile` 和字符串处理，**不碰任何 Android API**，因此可以直接在本地 JVM 上跑：

```powershell
.\\gradlew.bat :app:testDebugUnitTest
```

[`EpubParserTest.kt`](app/src/test/java/com/xialiangok/xlreader/data/epub/EpubParserTest.kt)
共 76 个用例（`EpubParserTest` 45 个 + `ReaderPreferencesTest` 13 个 + `SensorGestureTest` 18 个），覆盖：

* **解压与缓存**：解压到同名目录、已解压则复用（改了文件再打开，改动还在）、
缓存状态判定（missing / complete / foreign）、**「更新缓存」后仍保留 history.txt**、
**更新后确实读到新内容而不更新仍读旧缓存**、更新后不留下临时/备份目录、
解压进度回调单调不减且以 100% 结束；
* **打开加速（解析索引）**：**第二次打开不再碰原 epub**（把原文件换成垃圾字节仍能读出章节名与正文）、
索引缺失/损坏时退化为重新解析并**顺手补写**、**索引跟着解压结果走而不是跟着原文件走**
（选「直接阅读」后仍然命中，重新解压不会把旧索引带过来）、SVG 清单一起进索引、
旧版只有 `ok` 的完成标记也能配上索引；
* **记住选择**：刚解压完就记为「与当前版本同步」、**原文件变了记录失效**、
**选过「直接阅读」后不再询问且不会被偷偷替换缓存**、重新解压后记录更新到新版本、
版本记录文件不会把缓存误判成外来目录；
* **安全**：**遇到外来同名目录时报错且不删除用户文件**（选「更新」也不行）、
**Zip Slip 条目被拒绝且目录外不留东西**、非 epub 不留下解压目录；
* **元数据与目录**：书名/作者/章节兜底命名、EPUB3 nav 与 EPUB2 NCX 两种目录解析
（含嵌套 navPoint 不错位、**200 条 navPoint 的中段标题不错位**）；
* **正文**：标题去重、`</h1>` 之后正文不被误判为标题、章内次级标题保留、
实体解码与未知实体丢弃、裸 `\&` 保留、`../` 与百分号编码的相对路径解析；
* **图片**：`<img>` 变成图片块且位置正确、`xlink:href` 也能认、
SVG 变不支持块、**靠 manifest 的 media-type 认出头无扩展名的 SVG**、图片缺失给占位；
* **进度**：`history.txt` 读写往返、损坏文件当没有进度、**写进度不会让解压结果失效**；
* **失败路径**：非 epub、文件不存在、章节文件缺失；
* **颜色与亮度**（`ReaderPreferencesTest`）：RGB 打包/拆包、通道越界被夹住、
亮度 1.0 不变色、>1 一律夹到 1（**只压暗不外扩**）、<0 变全黑、
按比例压暗、文字颜色与亮度叠加后的最终值、RGB 每次走 1 档且两端夹住、
字号每次走 1 磅且夹在 1..999、默认字号 16 磅、
屏幕亮度格式化（负数按「跟随系统」哨兵处理）；
* **体感手势**（`SensorGestureTest`）：平放为 0°、左右与前后立起来为 ±90°、
低通滤波只吃新值的五分之一、录制整理只留最大最小角度（**空样本返回 null**）、
容差的下限与上限（录得越窄越宽容、录到一大片也不会宽得离谱）、
范围内 / 范围外 / 两个角度必须同时命中的判定、速度 1..50 与检测间隔 50..2000 的步进夹取、
以及「总开关 / 启用页面 / 单个手势开关」三个条件对检测列表的影响
（**总开关关掉或页面上一个手势都没开时，检测列表为空**）。

其中 **`open does not parse chapter bodies` 是惰性解析的回归测试**：故意让第二章的文件
不存在，如果哪天 `open()` 又开始预解析正文，它要么抛异常、要么得跳过那一章，
两种结果都会让断言失败。

### 开源许可清单

「关于」→「开源许可」页里的清单**不是凭印象写的**，而是从 release APK 里实际的
`META-INF` 版本标记核对出来的：包里共 51 个 artifact，按「项目」归并为 8 条
（AndroidX / Jetpack、Jetpack Compose、Compose for Wear OS、Kotlin 标准库、
kotlinx.coroutines、androidx.profileinstaller、AndroidX SplashScreen、
AndroidX graphics-path / shapes），**全部为 Apache License 2.0**。

清单数据在 [`OpenSourceLicenses.kt`](app/src/main/java/com/xialiangok/xlreader/data/OpenSourceLicenses.kt)，
界面在 [`LicensesScreen.kt`](app/src/main/java/com/xialiangok/xlreader/presentation/screens/LicensesScreen.kt)。

页面上另外单列了一条「构建参考」，说明构建配置参考了
[dertefter/WearFiles](https://github.com/dertefter/WearFiles)（Apache-2.0）——
它是参考来源、**不是本应用的代码依赖**，所以不混进依赖清单里。

### 应用图标

图标是一张 1024×1024 的成品图（[`icon-XLR-1024x1024.png`]）：圆正好内切画布、圆外透明，
深色圆底 + 蓝色 `XLR`。落到 `res/` 里之后是这样分层的：

| 文件 | 内容 |
|-|-|
| `mipmap-anydpi-v26/ic_launcher.xml`、`ic_launcher_round.xml` | 自适应图标：纯黑背景层 + 前景层 + 单色层 |
| `mipmap-*/ic_launcher_foreground.webp` | 前景层。**108dp 画布，内容只占中央 72dp**（四周 18dp 透明）——72dp 就是系统遮罩的可见区域，圆正好内切，圆屏上不多不少正好铺满 |
| `mipmap-*/ic_launcher_monochrome.webp` | 单色层：从原图里把蓝色文字抠成白色蒙版（去掉圆形底），系统做主题图标时自己染色 |
| `drawable/ic_launcher_background.xml` | 背景层，纯黑，与图标上缘一致；只有方形遮罩才会露出来 |
| `mipmap-*/ic_launcher.webp`、`ic_launcher_round.webp` | 旧式位图图标（48/72/96/144/192px）。minSdk 30 下系统走的是上面那套自适应图标，这两个只是兜底 |

每档密度都齐了 mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi 五份，全部是有损 WebP（q=95）——
无损在渐变底上要大一倍多，实测这个尺寸看不出差别（整套约 53 KB）。

> 启动画面用的是另一份资源：`drawable/splash_icon.xml` —— 黑色底上的蓝色 `XLReader`
> （`#2F05FF`，字号 23）。它是由 Segoe UI Bold 的字形轮廓导出的**矢量图**：
> viewport 按「1 单位 = 1dp / 字号 23」设计，所以文件里的字号就是 23，换密度也不失真。

\---

## 三、「减少视觉效果」是怎么做的

1. **列表用普通 `LazyColumn`**，滚动时列表项不放大、不缩小、不淡出。
见 [`WearListScreen.kt`](app/src/main/java/com/xialiangok/xlreader/presentation/screens/WearListScreen.kt)。
2. **动效固定全关，设置里也不给关**：主题的 `motionScheme` 换成一份六个 spec 全是 `snap` 的方案
（Wear M3 组件的动画时长都取自它），`LocalReduceMotion` 固定为 `true`，列表右侧的滚动指示条也用 `snap` 定位；
设置页的「减少动效」开关只作展示、点不动。触摸反馈的水波纹由组件库内部创建，
关掉它得把动画时长缩放压成 0，会连惯性滑动一起变成瞬移，所以保留。
见 [`Theme.kt`](app/src/main/java/com/xialiangok/xlreader/presentation/theme/Theme.kt)。
3. **不使用 `SurfaceTransformation`**，按钮与卡片不参与列表的形变联动。
4. **配色纯黑 + 灰阶 + 单一强调色**，无渐变、无大面积亮色，在 AMOLED 表盘上直接省电。
5. **按钮统一底色 `#2C2C2E`**（比背景纯黑和卡片 `#121212` 亮一档），文字用 `onSurface` 浅灰。
全应用 18 个按钮共用 [`readerButtonColors()`](app/src/main/java/com/xialiangok/xlreader/presentation/theme/Theme.kt)，
想调整只改一处。

> 注意必须连文字色一起指定：Wear 默认按钮的文字色取自 `onPrimary`，
   > 而本主题里 `onPrimary` 是纯黑，只改底色会让字看不见。

6. **启动画面纯黑底 + 蓝色 `XLReader`**（矢量，见「应用图标」一节），与主界面背景一致，
   启动过程不会亮色闪一下。
7. **设置项不用滑块**：连续数字的（正文字号 1..999 磅、段间距 0..999 dp）与开关类的常亮统一成
   「− / ＋」步进 —— 圆屏命中率高、没有拖动动画，而且**阅读菜单与设置页长得一模一样**
   （同一份 `ReaderPreferences`、同一个 `StepRow`）；字号、段间距按住还能连发，省得点几百次。
   步进按钮的点击判定与普通按钮一致：抬手且没被滑动带走才算一次，详见「关键技术点」。
8. **正文页关掉右侧的滚动指示条**（`showScrollIndicator = false`）：`ScreenScaffold` 默认会在
   屏幕右缘画一条 `ScrollIndicator`，阅读时它贴着正文，关掉后正文两侧不留装饰。
   其余列表页保持默认（那里正需要它提示还有多少内容可滚）。
9. **关掉系统自带的标题栏**：`styles.xml` 的 `MainActivityTheme` 把 `windowNoTitle` 设为 `true`、
   `windowActionBar` 设为 `false`（启动窗口 `MainActivityTheme.Starting` 也带上这两项）。
   `Theme.DeviceDefault` 在手机等非手表设备上默认带 ActionBar，会在左上角画出应用名
   「XLreader」，看着像一条顶部标题栏；界面全部由 Compose 自己画，不需要它。

> 没有跟着改的两处：设置页的 `SwitchButton`（它是开关不是按钮，轨道用的是另一套颜色槽）、
> 以及文件/章节列表里的 `Card`（容器底色仍是 `#121212`）。需要的话也可以统一。

\---

## 四、用 Android Studio 打开

1. `File` → `Open`，选择目录 **`D:\\Kotlin\\xlreader`**（选目录本身，不要选里面的 `app`）。
2. 弹出 Gradle 同步提示时点 `Trust Project` / `Sync Now`。
3. 同步完成后选 `app` 运行配置 + Wear OS 模拟器或已配对的手表，点 ▶ 运行。

> 项目里的 `local.properties` 指向本机 SDK：`sdk.dir=E:\\ASSDK`。换机器请改掉。

### 建议的模拟器

`Device Manager` → `Create Device` → 选 **Wear OS** 分类（如 `Wear OS Small Round`），
系统镜像选 **API 30（Android 11）或更高**。

\---

## 五、命令行构建

```powershell
cd D:\\Kotlin\\xlreader

# 装到手表上的「顺滑」包（含 baseline profile）——推荐
.\\gradlew.bat :app:assembleRelease
adb install -r app\\build\\outputs\\apk\\release\\app-release.apk

# 调试包（R8 已优化，但没有 baseline profile，体积也更大）
.\\gradlew.bat :app:assembleDebug

# 静态检查
.\\gradlew.bat :app:lintDebug
```

### 关于 release 签名

`app/build.gradle.kts` 里的 `release` 签名配置会优先读下面四个环境变量，
**读不到就回退到 debug 签名**，目的是让 `assembleRelease` 出来的包能直接侧载到手表：

```
XLREADER\_KEYSTORE\_FILE / XLREADER\_KEYSTORE\_PASSWORD / XLREADER\_KEY\_ALIAS / XLREADER\_KEY\_PASSWORD
```

上架 Google Play 前请换成自己的 keystore。

\---

## 六、目录结构

```
xlreader/
├─ settings.gradle.kts              # 仓库与模块声明
├─ build.gradle.kts                 # 顶层插件
├─ gradle.properties                # 并行/缓存/configuration-cache/r8 开关
├─ gradle/libs.versions.toml        # 版本目录：AGP 9.2.1 / Kotlin 2.2.10 / Wear Compose 1.5.6
├─ local.properties                 # 本机 Android SDK 路径
└─ app/
   ├─ build.gradle.kts              # minSdk 30（Android 11）；release 开 optimization，debug 不开
   └─ src/
      ├─ main/
      │  ├─ AndroidManifest.xml     # 手表硬件、所有文件访问权限、epub 的 VIEW intent-filter
      │  ├─ keepRules/rules.keep    # R8 规则（AGP 9 已内置默认规则）
      │  ├─ res/                    # 中文字符串、纯黑启动主题、图标、备份规则
      │  └─ java/com/xialiangok/xlreader/
      │     ├─ presentation/MainActivity.kt   # 唯一 Activity，接收 VIEW intent
      │     ├─ presentation/XlReaderApp.kt    # 根组件：路由 / 阅读偏好 / 手势检测任务 / 权限跳转
      │     ├─ presentation/ActivityExt.kt    # 从 Context 里剥出 Activity（调屏幕亮度用）
      │     ├─ presentation/GestureActions.kt # 页面登记体感手势动作的容器 + 居中项/按条目滚动
      │     ├─ presentation/screens/          # 文件浏览 / 章节列表 / 正文 / 权限 / 设置 / 关于 / 开源许可
      │     │                                 #   + ReaderMenu（阅读快捷菜单）/ WearListScreen（公用骨架）
      │     │                                 #   + SensorSettings（手势设置 / 录制 / 详情 / 启用页面）
      │     ├─ presentation/preview/          # 所有 @Preview（放 main，两个变体都能预览）
      │     ├─ presentation/theme/Theme.kt    # 配色、主题、排版参数
      │     └─ data/
      │        ├─ file/LocalFiles.kt          # 目录列举、路径处理、大小格式化
      │        ├─ epub/EpubExtractor.kt       # 解压到同名目录（复用/原子改名/Zip Slip 防护）
      │        ├─ epub/EpubParser.kt          # OPF + spine + nav/NCX 解析、按章读取（零第三方依赖）
      │        ├─ epub/EpubIndex.kt           # 解析结果缓存（.xlreader_index），重复打开不再解析
      │        ├─ epub/Xhtml.kt               # XHTML → 文字块/图片块（扫描器 + 实体解码）
      │        ├─ epub/EpubBlock.kt           # 文字 / 图片 / 不支持 三种内容块
      │        ├─ epub/EpubModels.kt          # EpubBook（只存元数据，正文按需加载）
      │        ├─ epub/ReadHistory.kt         # history.txt 读写（上次阅读位置）
      │        ├─ ReaderPreferences.kt        # 全部阅读偏好 + RGB/亮度纯函数
      │        ├─ SettingsStore.kt            # SharedPreferences 读写偏好
      │        ├─ sensor/SensorGesture.kt     # 体感手势模型 + 角度换算/滤波/整理/判定（纯函数）
      │        ├─ sensor/SensorStore.kt       # 手势设置的 SharedPreferences（JSON）读写
      │        ├─ sensor/TiltSensor.kt        # 加速度计检测器 + 录制采样器
      │        └─ OpenSourceLicenses.kt       # 开源项目清单
      ├─ test/java/.../EpubParserTest.kt      # 解析器的 JVM 单元测试（45 个用例）
      ├─ test/java/.../ReaderPreferencesTest.kt # RGB / 亮度 / 字号 / 段间距的单元测试（13 个用例）
      ├─ test/java/.../sensor/SensorGestureTest.kt # 体感手势纯计算单测（18 个用例）
      └─ (没有 debug 源集)
```

\---

## 七、关键技术点

* **路由**：手表页面很少，用 `sealed interface Route` + `when` 手写路由，不引入 Navigation 库。
当前目录存在 `browserDir`，这样切到设置再回来不会丢位置；**它同时写进 `SettingsStore` 的 `browser_dir`**，
所以退出应用、重启后也还是上次那个目录（目录已不存在就退回存储根）。
* **打开即阅读**：三条打开路径（首次解压 / 命中缓存 / 重新解压）最后都汇到 `XlReaderApp.openForReading()`，
它解析完元数据就把路由直接设成 `Route.Chapter(resumeChapter(book))` —— 打开一本书的意图就是接着读，
目录页退化成正文页「返回目录」才进的二级页面。
* **IO 不占主线程**：列目录、解压/解析元数据、加载单章正文、解码图片都在 `Dispatchers.IO` 上做，
界面先给「正在读取…/正在解压并读取…/正在载入」。打开一本书时的缓存状态与版本探测
（`status()` / `isUpToDate()`）也一起挪进 IO，免得几次 `stat` 就卡住点击反馈。
* **重复打开不重复解析**：解析结果写在解压目录的 `.xlreader_index`（见「EPUB 解析」一节），
命中索引后打开只需要读一个小文本文件；正则预编译、NCX 标题配对从 O(n²) 改为线性扫。
* **阅读渲染**：正文页把当前章的内容块交给 `LazyColumn`，每块是独立条目（文字/图片/占位），
长篇也只组合可见的那几屏；换章时用 `resetKey` 重置列表，并用 `startIndex` 恢复到上次读到的位置。
* **单击弹菜单**：`detectTapGestures` 加在 `LazyColumn` 上 —— 它只认「按下后没移动」的点击，
拖动仍然交给列表滚动；列表里的按钮会消费掉自己的点击，所以点按钮不会误弹菜单。
* **正文页的两个「返回」**：菜单里的「打开目录」「返回文件列表」分别就是正文底部那两个
同名按钮的同一条出口（`onOpenCatalog` / `onBackToFileList`），所以菜单按钮与底部按钮行为
完全一致（含进度落盘与离开时的资源还原）；唯一的区别是菜单里那个「打开目录」不清掉
菜单开关状态，返回时菜单会重新出现。
* **返回手势退回文件列表**：正文页的 `BackHandler` 接的是 `onBackToFileList`，
不是「返回目录」——连续两次返回才退出一本书太啰嗦。想回目录用页内那两个「返回目录」按钮；
载入中那一屏的「返回」也跟着走文件列表，保持「从正文页往回退 = 退出这本书」的一致。
* **菜单的「点空白关闭」**：面板根部的 `Box` 挂了点击关闭，而里面的按钮/开关/步进器都消费自己的事件，
所以点控件不会误关。步进器还额外 `consume()` 了 down/up，确保不会被父级抢走。
* **RGB 步进 1 档 + 按住连发**：步长 1 保证精准，但从 0 调到 255 要点 255 次，
所以按住超过 500ms 后每 60ms 走一档（`LaunchedEffect(pressed)` 实现，抬手即取消），
兼顾精度和手感。
* **步进按钮的防误触**：`StepButton` 的点击判定与普通 `Button` 一致 —— **抬手时**才算一次，
且中途被滑动带走（`waitForUpOrCancellation` 返回 null）就整次作废，所以想滑列表时手指
落在「− / ＋」上也不会把值改掉；比 500ms 短的按压在抬手时补走那一档，与连发不重复
（按下时不再立刻改值，那正是旧版误触的来源）。
* **屏幕亮度**：只在阅读页生效（`LaunchedEffect` 应用 + `DisposableEffect` 在离开时还原成进来时的值）。
* **内存**：全局只长期持有无数据的 `EpubBook`（书名/作者/章节名/路径），
正文文本与解码后的位图都只活在正文页的局部状态里，离开即回收。
* **体感手势**：只注册 `TYPE_ACCELEROMETER`（**不开陀螺仪、不用 Rotation Vector**，示例方案里
功耗最低的一种），按设置的检测间隔采样（默认 200 ms ≈ 5 Hz），xyz 先过一阶低通滤波再换算成
横滚 / 俯仰角。录制时只保留这段时间里角度的**最大与最小**值；判定时两侧各放宽一个
**随范围变化、上下都夹住**的容差（静止录到的姿势只有一两度，全靠容差才复现得了），
同一个姿势只触发一次 —— 要**离开范围**才重新武装，否则一直摆着就会每个采样周期触发一下。
检测任务由「总开关 + 当前页在启用列表里 + 这一页至少有一个没被单独关掉的手势」三个条件共同
决定，任何一条不满足就立刻 `unregisterListener`（进别的页面、关掉总开关都是立刻停），
另外再压一条**应用不在前台就停**：跟着宿主 Activity 的 `ON_PAUSE`（含表盘息屏）注销、
`ON_RESUME` 再注册 —— 用户看不见的时候不该还在采样耗电，也不该在后台把手势动作执行出来。
动作只落在**当前页面**上：页面用 `BindGestureActions` 把自己支持的动作登记到根组件（回调存在
一个稳定对象里，每次重组只刷新字段、不引起重组），没登记的动作就什么都不做，不会串页。
正文页的「单击 / 返回 / 翻页」= 单击正文弹菜单 / 系统返回（回文件列表）/ 按条目滚动；
列表页的「单击」= 打开屏幕上正中央的那一项（靠 LazyColumn 的 key 反查回条目）。
* **持久化**：阅读偏好用 `SharedPreferences` 包成 `SettingsStore`（core-ktx 的 `edit {}`），
文件浏览器上次停留的目录（`browser_dir`）也放在同一份里；
阅读位置写在书的 `history.txt` 里，跟着书走。
* **返回手势**：`BackHandler` 接管系统返回，页面内没有同名按钮时行为与「退回上一层」一致；
正文页例外——返回手势直接回文件列表（理由见上）。
* **常亮**：阅读页按开关设置 `LocalView.current.keepScreenOn`，并用 `DisposableEffect` 在离开页面时清除。
* **排版参数集中管理**：`ReadingMetrics` 统一提供字号、行高（正文 = 字号 ×1.7）、段间距。
字号是一个磅值（1..999，默认 16），**标题 = 字号 ×1.25**、在正文页里行距再按 ×1.1 收紧 ——
也就是说用户在菜单里加一磅，正文、行高、标题会一起跟着变。
* **外部 epub**：`MainActivity` 取 `ACTION\_VIEW` 的 Uri；`content://` 没有真实路径，
只能先落到 `cacheDir` 再按同一套逻辑解压，`file://` 直接用原路径。
也就是说**只有「从别的应用打开」这一条路径会用到应用缓存**，
在应用内浏览文件打开的书完全不会占用缓存。
* **目录是「打开」不是「返回」**：正文页与阅读菜单里那个按钮叫「打开目录」（回调 `onOpenCatalog`），
`XlReaderApp` 顺手记下进来时在第几章（`catalogReturnChapter`）；目录页的返回 —— `BackHandler`
与体感手势的「返回」都走它 —— 是 `closeCatalog()`：关掉目录、回到那一章，
不再一路退出这本书、落到文件列表上。要离开这本书得用目录页底部的「返回文件列表」，
或者回到正文后按系统返回。
* **从菜单进目录，返回回到菜单**：阅读菜单开没开记在 `XlReaderApp` 的 `readerMenuVisible` 上
（正文页不自持这个状态）；菜单里的「打开目录」**不清掉它**，所以 `closeCatalog()` 回到正文页时
菜单还在，而不是直接落回正文。从正文底部「打开目录」进去时它本来就是 `false`，返回行为不变；
换书（`requestBook()`）、在目录里直接挑别的章、以及「返回文件列表」都会把它清掉。

