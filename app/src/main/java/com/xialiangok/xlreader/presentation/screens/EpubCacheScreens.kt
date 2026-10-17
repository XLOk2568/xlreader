package com.xialiangok.xlreader.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.xialiangok.xlreader.presentation.BindGestureActions
import com.xialiangok.xlreader.presentation.scrollByDp
import com.xialiangok.xlreader.presentation.theme.readerButtonBorder
import com.xialiangok.xlreader.presentation.theme.readerButtonColors
import kotlinx.coroutines.launch

/**
 * 解压/询问期间强制屏幕常亮.
 *
 * 手表熄屏很快, 解压一本大书时用户得反复抬手点亮屏幕, 所以这两页不跟「阅读时常亮」设置,
 * 只要还在这一页就一直亮着, 离开时立刻还回去.
 */
@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

/**
 * 解压进度页.
 *
 * 首次打开一本书 (或用户选择「更新缓存」) 时会走这里,
 * 把 epub 解压到与原书同名的目录, 并给出明确的百分比.
 */
@Composable
fun ExtractingScreen(
    fileName: String,
    progress: Float,
) {
    KeepScreenOn()
    val safe = progress.coerceIn(0f, 1f)
    val percent = (safe * 100).toInt()

    WearListScreen {
        item { ListHeader { Text("正在解压", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) } }
        item {
            Text(
                text = fileName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
            ) {
                // 底槽
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.outline),
                )
                // 已完成的进度
                Box(
                    modifier = Modifier
                        .fillMaxWidth(safe)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
        item {
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Text(
                text = "解压结果放在与原书同名的目录里，下次打开就能秒开。",
                style = MaterialTheme.typography.bodyExtraSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * 「已有解压缓存」时的询问页。
 *
 * 说明清楚两件事：更新 = 只补解压变了的条目（快），不更新 = 直接用现有缓存；
 * 以及无论选哪个，阅读进度都不会丢。
 */
@Composable
fun CachePromptScreen(
    fileName: String,
    onKeep: () -> Unit,
    onRefresh: () -> Unit,
    onCancel: () -> Unit,
) {
    KeepScreenOn()

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // 体感手势: 「返回」等于取消(和页面底部那个按钮同一条路), 上下翻页滚动这一页.
    BindGestureActions(
        onBack = onCancel,
        onScrollBy = { delta -> scope.launch { scrollByDp(listState, delta, density) } },
    )

    WearListScreen(listState = listState) {
        item { ListHeader { Text("已有解压缓存", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) } }
        item {
            Text(
                text = fileName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Text(
                text = "这本书之前已经解压过，可以直接打开。\n\n" +
                    "「更新缓存」只把原文件里变了的章节和图片补解压进来，不会整本重来，" +
                    "所以很快——原文件更新过时才需要。\n\n" +
                    "「直接阅读」会记住当前版本，以后不再询问；" +
                    "等原文件变了才会再问一次。\n\n" +
                    "不管选哪个，阅读进度都不会丢。",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            Button(onClick = onKeep, modifier = Modifier.fillMaxWidth(), shape = RectangleShape, colors = readerButtonColors(), border = readerButtonBorder()) {
                Text("直接阅读（不更新）", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
        item {
            Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth(), shape = RectangleShape, colors = readerButtonColors(), border = readerButtonBorder()) {
                Text("更新缓存", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
        item {
            Button(onClick = onCancel, modifier = Modifier.fillMaxWidth(), shape = RectangleShape, colors = readerButtonColors(), border = readerButtonBorder()) {
                Text("取消", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
