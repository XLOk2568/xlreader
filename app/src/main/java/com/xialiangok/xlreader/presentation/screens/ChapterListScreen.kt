package com.xialiangok.xlreader.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListSubHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.xialiangok.xlreader.data.epub.EpubBook
import com.xialiangok.xlreader.data.epub.ReadHistory
import com.xialiangok.xlreader.data.epub.ReadingPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.xialiangok.xlreader.presentation.theme.readerButtonColors

/**
 * 章节目录页。
 *
 * 打开一本书时只解析到这一层：书名、作者、章节名。正文一个字都还没读，
 * 点进某一章才会去读那一章。
 *
 * 如果 `history.txt` 里有上次读到的位置，顶部会出现「继续阅读」。
 *
 * @param onOpenChapter 打开第 N 章。
 */
@Composable
fun ChapterListScreen(
    book: EpubBook,
    onOpenChapter: (Int) -> Unit,
    onBack: () -> Unit,
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
    val headerItems = 2 + (if (resume != null) 1 else 0)
    val scrollTarget = currentChapter?.let { headerItems + it } ?: 0

    WearListScreen(
        // saved 从 null 变成有值时重算一次滚动位置，保证偏移量是对的。
        resetKey = saved,
        startIndex = scrollTarget,
    ) {
        item {
            ListHeader {
                Text(
                    text = book.title,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
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
            item {
                Card(
                    onClick = { onOpenChapter(resume.chapter) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "继续阅读",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = titles[resume.chapter],
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "第 ${resume.chapter + 1} / ${titles.size} 节",
                        style = MaterialTheme.typography.bodyExtraSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        itemsIndexed(titles, contentType = { _, _ -> "chapter" }) { index, title ->
            Card(
                onClick = { onOpenChapter(index) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
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
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }

        item { Spacer(Modifier.height(10.dp)) }
        item {
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth(), colors = readerButtonColors()) {
                Text("返回文件列表")
            }
        }
        item { Spacer(Modifier.height(28.dp)) }
    }
}
