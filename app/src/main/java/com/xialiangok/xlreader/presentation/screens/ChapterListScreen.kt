package com.xialiangok.xlreader.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListSubHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.xialiangok.xlreader.data.epub.EpubBook
import com.xialiangok.xlreader.data.epub.ReadHistory
import com.xialiangok.xlreader.data.epub.ReadingPosition
import com.xialiangok.xlreader.presentation.BindGestureActions
import com.xialiangok.xlreader.presentation.centeredItemKey
import com.xialiangok.xlreader.presentation.scrollByItems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.xialiangok.xlreader.presentation.theme.readerButtonColors

/** 列表项的 key：靠它把「屏幕上正中央的那一项」反查回具体章节。 */
private const val CHAPTER_KEY_PREFIX = "chapter:"
private const val RESUME_KEY = "resume"

/** 顶上那根紫条的底色：用户指定的 rgb(205, 92, 246)。 */
private val MenuBarColor = Color(0xFF6331BF)

/** 紫条的高度：一条窄带子，钉在顶上也不太占地方。 */
private val MenuBarHeight = 28.dp

/**
 * 章节目录页。
 *
 * 打开一本书时只解析到这一层：书名、作者、章节名。正文一个字都还没读，
 * 点进某一章才会去读那一章。
 *
 * 如果 `history.txt` 里有上次读到的位置，列表顶部会出现「继续阅读」。
 *
 * 顶上钉着一根紫色的长条，它是这一页的开关（[WearListScreen] 的 `header`，不跟内容一起滚）：
 * - 目录模式（默认）：紫条上写「菜单 ▾」，下面就是可滚动的书名与章节列表；
 * - 单击紫条：目录整个藏起来，换成三个按钮——「数字跳转章节」（打开
 *   [ChapterListScreenNumber]，输入第几节就跳到第几节）、「返回」（关掉目录、回到进来时那一章）、
 *   「返回文件目录」（离开这本书、回文件列表），此时紫条上写「✕」；
 * - 再单击紫条：菜单收起，目录回来。
 *
 * 这一页是正文页「打开目录」打开的：所以**返回 = 关掉目录、回到进来时那一章**，
 * 不会一路退出这本书、落到文件列表上；要回文件列表用「返回文件目录」。
 *
 * @param onOpenChapter 打开第 N 章。
 * @param onBack 关掉目录，回到进来时那一章。
 * @param onBackToFileList 返回文件列表（明确要离开这本书时才走这里）。
 * @param onOpenNumber 打开数字跳章页（「数字跳转章节」按钮）。
 */
@Composable
fun ChapterListScreen(
    book: EpubBook,
    onOpenChapter: (Int) -> Unit,
    onBack: () -> Unit,
    onBackToFileList: () -> Unit,
    onOpenNumber: () -> Unit,
) {
    BackHandler(onBack = onBack)

    var saved by remember(book) { mutableStateOf<ReadingPosition?>(null) }
    LaunchedEffect(book) {
        saved = withContext(Dispatchers.IO) { ReadHistory.load(book.dir) }
    }

    // 章节名列表是稳定的，避免每次重组都重建。
    val titles = remember(book) { book.chapterTitles }
    // 章号可能因为换了版本而对不上，越界就当没有进度。
    val resume = saved?.takeIf { it.chapter in titles.indices }
    val currentChapter = resume?.chapter

    // 列表前两项是书名与副标题；有「继续阅读」卡片时整体再往后挪一位。
    // 这样打开目录时会**自动停在正在读的那一章**，不用用户自己翻。
    // （顶上那根紫条是钉在列表外面的，所以这里不用把它算进去。）
    val headerItems = 2 + (if (resume != null) 1 else 0)
    val scrollTarget = currentChapter?.let { headerItems + it } ?: 0

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // 菜单开着没有：默认关着（页面显示目录），点紫条开、再点一次关。
    var menuOpen by remember(book) { mutableStateOf(false) }

    // 体感手势：单击 = 打开屏幕上正中央的那一项（含「继续阅读」卡片），
    // 返回 = 关掉目录、回到进来时那一章（和系统返回手势同一条路），翻页 = 按条目滚动。
    BindGestureActions(
        onTap = {
            val key = centeredItemKey(listState) as? String ?: return@BindGestureActions
            val index = when {
                key == RESUME_KEY -> resume?.chapter
                key.startsWith(CHAPTER_KEY_PREFIX) ->
                    key.removePrefix(CHAPTER_KEY_PREFIX).toIntOrNull()

                else -> null
            }
            index?.let(onOpenChapter)
        },
        onBack = onBack,
        onScrollBy = { delta -> scope.launch { scrollByItems(listState, delta) } },
    )

    WearListScreen(
        // saved 从 null 变成有值时重算一次滚动位置；开合菜单时也要复位
        // （菜单里只有三个按钮，不复位的话回来时会停在菜单那几项的位置上）。
        resetKey = saved to menuOpen,
        startIndex = if (menuOpen) 0 else scrollTarget,
        listState = listState,
        // 顶上那根紫条：钉住不动，单击在「目录」和「三个按钮」之间切换。
        header = { MenuBar(menuOpen) { menuOpen = !menuOpen } },
    ) {
        if (menuOpen) {
            item {
                Button(
                    onClick = onOpenNumber,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RectangleShape,
                    colors = readerButtonColors(),
                ) {
                    Text("数字跳转章节", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
            item {
                Button(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RectangleShape,
                    colors = readerButtonColors(),
                ) {
                    Text("返回", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
            item {
                Button(
                    onClick = onBackToFileList,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RectangleShape,
                    colors = readerButtonColors(),
                ) {
                    Text("返回文件目录", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
        } else {
            item {
                ListHeader {
                    Text(
                        text = book.title,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                ListSubHeader {
                    Text(
                        text = "${book.author} · 共 ${titles.size} 节",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (resume != null) {
                item(key = RESUME_KEY) {
                    Card(
                        onClick = { onOpenChapter(resume.chapter) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RectangleShape
                    ) {
                        Text(
                            text = "继续阅读",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = titles[resume.chapter],
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = "第 ${resume.chapter + 1} / ${titles.size} 节",
                            style = MaterialTheme.typography.bodyExtraSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            itemsIndexed(
                titles,
                key = { index, _ -> "$CHAPTER_KEY_PREFIX$index" },
                contentType = { _, _ -> "chapter" },
            ) { index, title ->
                Card(
                    onClick = { onOpenChapter(index) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RectangleShape
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        // 只有「正在读」这一章的章节名染成 rgb(99, 49, 191)，其余名字保持默认色，
                        // 滚到位之后一眼就能认出自己在哪一章。
                        color = if (index == currentChapter) {
                            Color(0xFFB15FED)
                        } else {
                            Color.Unspecified
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = if (index == currentChapter) {
                            "正在读 · 第 ${index + 1} / ${titles.size} 节"
                        } else {
                            "第 ${index + 1} / ${titles.size} 节"
                        },
                        style = MaterialTheme.typography.bodyExtraSmall,
                        // 当前章用强调色标出来，滚到位之后一眼能看到自己在哪。
                        color = if (index == currentChapter) {
                            Color(0xFFB15FED)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item { Spacer(Modifier.height(28.dp)) }
        }
    }
}

/**
 * 顶上那根紫条：既是这一页的开关，也报当前状态。
 *
 * 高度只有 [MenuBarHeight]（标准 Wear 按钮是 52dp），纵向内容边距也压到 0，
 * 这样文字还塞得下、一条窄带子也不占地方；默认的 minHeight 是 52dp，
 * 但库只在「上层给的最小高度是 0」时才用它（`defaultMinSize` 的规矩），
 * 所以这里显式写死高度是压得下去的。
 */
@Composable
private fun MenuBar(menuOpen: Boolean, onToggle: () -> Unit) {
    Button(
        onClick = onToggle,
        modifier = Modifier
            .fillMaxWidth()
            .height(MenuBarHeight),
        shape = RectangleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MenuBarColor,
            contentColor = Color.Black,
        ),
        contentPadding = PaddingValues(vertical = 0.dp),
    ) {
        Text(
            // 目录模式提示「点我能开菜单」，菜单模式提示「点我能收起来」。
            text = if (menuOpen) "✕" else "菜单 ▾",
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
