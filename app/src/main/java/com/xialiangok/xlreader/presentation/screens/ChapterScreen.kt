package com.xialiangok.xlreader.presentation.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.xialiangok.xlreader.data.ReaderPreferences
import com.xialiangok.xlreader.data.epub.EpubBlock
import com.xialiangok.xlreader.data.epub.EpubBook
import com.xialiangok.xlreader.data.epub.ReadHistory
import com.xialiangok.xlreader.data.epub.ReadingPosition
import com.xialiangok.xlreader.presentation.BindGestureActions
import com.xialiangok.xlreader.presentation.findActivity
import com.xialiangok.xlreader.presentation.scrollByItems
import com.xialiangok.xlreader.presentation.theme.ReadingMetrics
import com.xialiangok.xlreader.presentation.theme.readerButtonColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

/** 单章内容的三种状态 */
private sealed interface ChapterState {
    data object Loading : ChapterState
    data class Ready(val blocks: List<EpubBlock>, val startItem: Int) : ChapterState
    data class Failed(val message: String) : ChapterState
}

/**
 * 进这一章后多久记一次进度（只记一次、不循环）
 *
 * 留点时间让列表滚到 `startIndex`，免得把刚进章时的第 0 条写进去
 */
private const val SAVE_AFTER_LOAD_MS = 500L

/** 单张图片的三种状态 */
private sealed interface ImageState {
    data object Loading : ImageState
    data class Ready(val bitmap: Bitmap) : ImageState
    data object Failed : ImageState
}

/**
 * 正文页：一次只加载**一章**
 *
 * 和别的页面不同的地方：不显示时间（`timeText = {}`）、不画滚动指示条（见 [WearListScreen]）、
 * 单击正文弹出快捷设置菜单（改的就是设置页那份数据，改完立刻生效）
 *
 * 阅读位置写在书的 `history.txt`，但**只在四个时机写**：加载完这一章 500ms（仅一次）、
 * 单击正文弹出菜单、切换章节、离开正文页；**滚动本身不写盘**（见 [ChapterBody]）
 *
 * @param chapterIndex 当前章号
 * @param onOpenChapter 跳到指定章（上一章 / 下一章共用）
 * @param onPositionChange 随时上报「现在读到哪一条」——第一个可见条目一变就报一次，**只进内存**，
 *   写盘由 [onSaveProgress] 或上层离开正文页时做
 * @param onSaveProgress 把「现在读到哪一条」立刻落盘一次，落盘交给上层（根组件作用域），
 *   这样切章 / 离开那一瞬间发起的写入不会被取消，理由见 [ChapterBody]
 * @param onOpenCatalog 打开章节目录，**这是「打开」不是「返回」**：目录里再退一步只是关掉目录、
 *   回到这一章；要离开这本书请用「返回文件列表」；从阅读菜单里打开时还会带着那个菜单回来，
 *   见 [menuVisible]
 * @param onBackToFileList 返回文件列表，**菜单没开时系统返回手势走的是这条**：
 *   往回退的意图多半是「退出这本书」，而不是先退到目录再退一次
 * @param onOpenSettings 打开设置页（阅读菜单里的「设置」），和打开目录一样是「打开」不是「返回」：
 *   在设置页里退一步回到的还是这一章，不会落到文件列表上
 * @param menuVisible 阅读菜单是否开着，这个状态**记在根组件上、正文页不自持**：
 *   从菜单里「打开目录」时不会把它清掉，所以在目录里返回时还是回到那个菜单（而不是直接落回正文）；
 *   从正文底部的「打开目录」进去时它本来就是关着的，返回还是直接回正文
 * @param onMenuVisibleChange 菜单开关状态变了（单击正文、点「关闭菜单」、点菜单空白处）
 * @param menuGestureEnabled 阅读菜单里要不要跑体感光标导航，由「启用页面」里的
 *   **正文阅读(菜单)** 那一页决定：没勾上时菜单开着既不显示选中光标、也不接管体感手势，
 *   只靠手指操作（连检测任务都不注册，一个采样点都不收）
 */
@Composable
fun ChapterScreen(
    book: EpubBook,
    chapterIndex: Int,
    preferences: ReaderPreferences,
    onPreferencesChange: (ReaderPreferences) -> Unit,
    onOpenChapter: (Int) -> Unit,
    onPositionChange: (ReadingPosition) -> Unit,
    onSaveProgress: () -> Unit,
    onOpenCatalog: () -> Unit,
    onBackToFileList: () -> Unit,
    onOpenSettings: () -> Unit,
    menuVisible: Boolean,
    onMenuVisibleChange: (Boolean) -> Unit,
    menuGestureEnabled: Boolean,
) {
    val view = LocalView.current
    DisposableEffect(preferences.keepScreenOn) {
        view.keepScreenOn = preferences.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }
    // 系统返回按用户看得见的层级退回：
    // 菜单开着 = 关菜单（菜单铺满整屏，往回退一步的意图显然是关掉它，而不是退出这本书）；
    // 菜单没开 = 退回文件列表（和正文底部的「返回文件列表」一致）
    // 想看目录请用「打开目录」，它只是打开目录，在目录里再返回一次是回到这一章
    BackHandler(
        onBack = if (menuVisible) {
            { onMenuVisibleChange(false) }
        } else {
            onBackToFileList
        },
    )

    ApplyScreenBrightness(preferences.screenBrightness)

    var state by remember(book, chapterIndex) {
        mutableStateOf<ChapterState>(ChapterState.Loading)
    }

    LaunchedEffect(book, chapterIndex) {
        state = ChapterState.Loading
        state = withContext(Dispatchers.IO) {
            runCatching {
                val blocks = book.loadChapter(chapterIndex)
                // 只有上次停在同一章时才恢复段落位置
                val saved = ReadHistory.load(book.dir)
                val start = if (saved?.chapter == chapterIndex) saved.item else 0
                ChapterState.Ready(blocks, start)
            }.getOrElse { ChapterState.Failed(it.message ?: "这一章读不出来") }
        }
    }

    val title = book.chapterTitles.getOrNull(chapterIndex) ?: "第 ${chapterIndex + 1} 节"

    when (val current = state) {
        ChapterState.Loading -> NoticeScreen(
            title = "正在载入",
            message = title,
            // 「返回」和系统返回一致：退回文件列表
            onAction = onBackToFileList,
        )

        is ChapterState.Failed -> NoticeScreen(
            title = "这一章打不开",
            message = current.message,
            actionLabel = "打开目录",
            onAction = onOpenCatalog,
        )

        is ChapterState.Ready -> ChapterBody(
            book = book,
            chapterIndex = chapterIndex,
            title = title,
            blocks = current.blocks,
            startItem = current.startItem,
            preferences = preferences,
            onPreferencesChange = onPreferencesChange,
            onOpenChapter = onOpenChapter,
            onPositionChange = onPositionChange,
            onSaveProgress = onSaveProgress,
            onOpenCatalog = onOpenCatalog,
            onBackToFileList = onBackToFileList,
            onOpenSettings = onOpenSettings,
            menuVisible = menuVisible,
            onMenuVisibleChange = onMenuVisibleChange,
            menuGestureEnabled = menuGestureEnabled,
        )
    }
}

/**
 * 只在阅读页生效地覆盖屏幕亮度，离开时恢复原来的值
 *
 * `screenBrightness` 传 [ReaderPreferences.BRIGHTNESS_SYSTEM] 表示交还给系统
 */
@Composable
private fun ApplyScreenBrightness(brightness: Float) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    // 记住进来时的亮度，离开阅读页时还回去
    val original = remember(activity) {
        activity?.window?.attributes?.screenBrightness ?: ReaderPreferences.BRIGHTNESS_SYSTEM
    }

    LaunchedEffect(activity, brightness) {
        val window = activity?.window ?: return@LaunchedEffect
        window.attributes = window.attributes.apply { screenBrightness = brightness }
    }

    DisposableEffect(activity) {
        onDispose {
            val window = activity?.window ?: return@onDispose
            window.attributes = window.attributes.apply { screenBrightness = original }
        }
    }
}

@Composable
private fun ChapterBody(
    book: EpubBook,
    chapterIndex: Int,
    title: String,
    blocks: List<EpubBlock>,
    startItem: Int,
    preferences: ReaderPreferences,
    onPreferencesChange: (ReaderPreferences) -> Unit,
    onOpenChapter: (Int) -> Unit,
    onPositionChange: (ReadingPosition) -> Unit,
    onSaveProgress: () -> Unit,
    onOpenCatalog: () -> Unit,
    onBackToFileList: () -> Unit,
    onOpenSettings: () -> Unit,
    menuVisible: Boolean,
    onMenuVisibleChange: (Boolean) -> Unit,
    menuGestureEnabled: Boolean,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // 体感手势：单击 = 弹出 / 收起快捷菜单（和手指单击正文一样），返回 = 退回文件列表，
    // 翻页 = 按条目滚动
    //
    // 菜单开着时**不登记**，由菜单自己接管（见 [ReaderMenuOverlay]）：否则菜单铺满整屏，
    // 翻页手势却滚到底下的正文上、单击又会把菜单直接关掉；整个手势容器只有一个「当前页」，
    // 后登记的会盖掉先登记的，所以这里用 if 而不是两份都留着；菜单那一页没启用手势时
    // 两边都不登记，菜单开着就只有手指能操作
    if (!menuVisible) {
        BindGestureActions(
            onTap = {
                onMenuVisibleChange(!menuVisible)
                onSaveProgress()
            },
            onBack = onBackToFileList,
            onScrollBy = { delta -> scope.launch { scrollByItems(listState, delta) } },
        )
    }

    // 阅读位置只上报给上层，**不在这里写盘**：滚动本身不产生任何文件写入
    // 真正写 history.txt 只有四个时机——加载完这一章 500ms（见下面那个 effect）、单击正文
    // 弹出菜单、切换章节、离开正文页（打开目录 / 返回文件列表 / 返回手势），后三个走
    // [onSaveProgress]；落盘用根组件的作用域 —— 一旦离开正文页（或切章）本页协程就被取消，
    // 最后那一刻的位置就写不进去了
    //
    // 回调用 rememberUpdatedState 包一层：它不能进 LaunchedEffect 的 key，
    // 否则上层每次重组都会把这段上报重新来过
    val reportPosition = rememberUpdatedState(onPositionChange)
    LaunchedEffect(chapterIndex, listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { item -> reportPosition.value(ReadingPosition(chapterIndex, item)) }
    }

    // 进这一章 500ms 后记一次进度，**只记这一次、不循环**：用户不动手时也能把「读到这一章的
    // 第几条」留下来，之后除非他单击弹菜单 / 切章 / 离开正文页，否则不会再写文件
    val saveProgress = rememberUpdatedState(onSaveProgress)
    LaunchedEffect(chapterIndex) {
        delay(SAVE_AFTER_LOAD_MS.milliseconds)
        saveProgress.value()
    }

    val bodyFontSize = ReadingMetrics.bodyFontSize(preferences.fontSize)
    val titleFontSize = ReadingMetrics.titleFontSize(preferences.fontSize)
    /** 本章字数：只数文字块（含标点），图片与不支持的块不算 */
    val chapterCharCount = remember(blocks) { blocks.sumOf { if (it is EpubBlock.Text) it.text.length else 0 } }
    val lineHeight = ReadingMetrics.lineHeight(preferences.fontSize)
    val paragraphSpacing = ReadingMetrics.paragraphSpacing(preferences.spacingLevel)
    val bodyColor = remember(preferences.effectiveTextColor) {
        // 打包成 ARGB：低 24 位是用户选的 RGB，最高位补成不透明
        Color(0xFF000000.toInt() or preferences.effectiveTextColor)
    }

    val hasPrevious = chapterIndex > 0
    val hasNext = chapterIndex < book.chapterCount - 1

    Box(modifier = Modifier.fillMaxSize()) {
        WearListScreen(
            // 阅读界面不要时间，顶部空间留给正文
            timeText = {},
            resetKey = chapterIndex,
            startIndex = startItem,
            listState = listState,
            // 单击正文：弹出菜单，顺手记一次进度（滚动不写盘，所以这里是个便宜的记录点）
            // 菜单打开后铺满整屏、单击由菜单自己消费，走不到这里，所以「打开」只会记一次
            onTap = {
                onMenuVisibleChange(!menuVisible)
                onSaveProgress()
            },
        ) {
            item {
                ListHeader {
                    Text(
                        text = title,
                        fontSize = titleFontSize,
                        // 标题行距收紧到 1.1 倍：标题一般只有一两行，不用按正文的 1.7 倍撑开
                        lineHeight = titleFontSize * 1.1f,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        // 只把标题居中，下面的正文段落保持左对齐（阅读时不居中）
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                Text(
                    // 「第几 / 共几节」下面再起一行，报一下这一节有多少字
                    text = "第 ${chapterIndex + 1} / ${book.chapterCount} 节\n本节 $chapterCharCount 字",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            itemsIndexed(
                blocks,
                contentType = { _, block ->
                    when (block) {
                        is EpubBlock.Text -> "text"
                        is EpubBlock.Image -> "image"
                        is EpubBlock.Unsupported -> "unsupported"
                    }
                },
            ) { index, block ->
                when (block) {
                    is EpubBlock.Text -> Text(
                        text = block.text,
                        fontSize = bodyFontSize,
                        lineHeight = lineHeight,
                        color = bodyColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp)
                            .padding(bottom = if (index == blocks.lastIndex) 0.dp else paragraphSpacing),
                    )

                    is EpubBlock.Image -> EpubImage(block.file)

                    is EpubBlock.Unsupported -> Text(
                        text = "（${block.reason}：${block.name}）",
                        style = MaterialTheme.typography.bodyExtraSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                    )
                }
            }

            if (blocks.isEmpty()) {
                item {
                    Text(
                        text = "（这一节没有可显示的内容）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item { Spacer(Modifier.height(10.dp)) }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (hasPrevious) {
                        Button(
                            onClick = {
                                onSaveProgress()
                                onOpenChapter(chapterIndex - 1)
                            },
                            modifier = if (hasNext) Modifier.weight(1f) else Modifier.fillMaxWidth(),
                            shape = RectangleShape,          // 直角
                            colors = readerButtonColors(),
                        ) {
                            Text(text="上一章", textAlign =TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    if (hasNext) {
                        Button(
                            onClick = {
                                onSaveProgress()
                                onOpenChapter(chapterIndex + 1)
                            },
                            modifier = if (hasPrevious) Modifier.weight(1f) else Modifier.fillMaxWidth(),
                            shape = RectangleShape,          // 直角
                            colors = readerButtonColors(),
                        ) {
                            Text(text="下一章", textAlign =TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
            item {
                Button(
                    onClick = onOpenCatalog,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RectangleShape,          // 直角
                    colors = readerButtonColors(),
                ) {
                    Text(text = "打开目录", textAlign =TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
            item {
                Button(
                    onClick = onBackToFileList,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RectangleShape,          // 直角
                    colors = readerButtonColors(),
                ) {
                    Text(text = "返回文件列表", textAlign =TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
            item { Spacer(Modifier.height(28.dp)) }
        }

        if (menuVisible) {
            ReaderMenuOverlay(
                preferences = preferences,
                onPreferencesChange = onPreferencesChange,
                onOpenCatalog = {
                    // 不在这里把菜单关掉：菜单开着这件事记在根组件上，
                    // 「打开目录 → 在目录里返回」要回到的还是这个菜单（见 [menuVisible]）
                    onOpenCatalog()
                },
                onBackToFileList = {
                    // 离开这本书了，菜单状态要跟着清掉，免得下一本书进来又弹出来
                    onMenuVisibleChange(false)
                    onBackToFileList()
                },
                onOpenSettings = {
                    // 和「打开目录」一样不清菜单状态，从设置页回来时还是这个菜单
                    onOpenSettings()
                },
                onDismiss = { onMenuVisibleChange(false) },
                gestureEnabled = menuGestureEnabled,
            )
        }
    }
}

/**
 * 正文里的插图
 *
 * 两个关键点都是为了手表那点内存：
 * 1. 只在条目真的进入视口时才解码（`produceState` 跟着 item 一起创建 / 销毁）
 * 2. 按屏幕宽度降采样（`inSampleSize`），绝不把原图整张解进内存 ——
 *    一张 1200×1600 的图不降采样就是约 7.7 MB，几张就够把应用撑爆
 *
 * 这里不手动 `recycle()`：Compose 的图层缓存可能还持有引用，强制回收有崩溃风险，
 * API 26 之后位图在 native 堆，交给 GC 更安全
 */
@Composable
private fun EpubImage(file: File) {
    val context = LocalContext.current
    val targetPx = remember(context) {
        context.resources.displayMetrics.widthPixels.coerceAtLeast(320)
    }

    val state by produceState<ImageState>(ImageState.Loading, file, targetPx) {
        value = withContext(Dispatchers.IO) {
            decodeSampled(file, targetPx)
                ?.let { ImageState.Ready(it) }
                ?: ImageState.Failed
        }
    }

    when (val current = state) {
        ImageState.Loading -> Text(
            text = "（正在载入图片…）",
            style = MaterialTheme.typography.bodyExtraSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        )

        ImageState.Failed -> Text(
            text = "（图片无法显示：${file.name}）",
            style = MaterialTheme.typography.bodyExtraSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        )

        is ImageState.Ready -> Image(
            bitmap = current.bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        )
    }
}

/**
 * 按目标边长降采样解码
 *
 * `BitmapFactory` 不能对同一个流解码两次，而这里用的是文件路径（`decodeFile` 可以重复调用），
 * 所以不必先把压缩字节读进内存 —— 这也是「解压到磁盘」换来的好处之一
 */
private fun decodeSampled(file: File, maxSize: Int): Bitmap? {
    if (!file.isFile) return null

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    // 按长边算采样率：横幅图也不会被漏降采样
    val longest = maxOf(bounds.outWidth, bounds.outHeight)
    var sampleSize = 1
    while (longest / (sampleSize * 2) >= maxSize) sampleSize *= 2

    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return BitmapFactory.decodeFile(file.absolutePath, options)
}
