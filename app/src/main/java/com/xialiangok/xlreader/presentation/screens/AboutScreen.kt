package com.xialiangok.xlreader.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.xialiangok.xlreader.data.bundledOpenSourceProjects
import com.xialiangok.xlreader.presentation.BindGestureActions
import com.xialiangok.xlreader.presentation.scrollByDp
import com.xialiangok.xlreader.presentation.theme.readerButtonBorder
import com.xialiangok.xlreader.presentation.theme.readerButtonColors
import kotlinx.coroutines.launch

/**
 * 关于页：版本、定位、内容来源，以及通往开源许可页的入口。
 *
 * @param onOpenLicenses 打开「开源许可」页。
 */
@Composable
fun AboutScreen(
    onOpenLicenses: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // 体感手势: 这一页只有「返回」和「上下翻页」两个动作.
    BindGestureActions(
        onBack = onBack,
        onScrollBy = { delta -> scope.launch { scrollByDp(listState, delta, density) } },
    )

    WearListScreen(listState = listState) {
        item {
            ListHeader {
                Text(
                    text = "关于",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
        item {
            Text(
                text = "XLreader",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
        item {
            Text(
                text = "版本 1.0.2611.42\n最低支持 Android 11",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
        item {
            Text(
                text = "极简，流畅，自定义，体感，免费，开源",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
        item {
            Text(
                text = "电子书来自你自己的设备存储，应用不内置任何电子书，全程离线",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
        item {
            Text(
                text = "1.新增主题设置,并对外观优化\n"+
                        "2.新增自定义颜色和边距了\n"+
                        "3.支持quickjs插件功能\n"+
                        "4.新增局域网文件管理自定义IP功能\n"+
                        "5.优化了体积"+
                        "6.支持.txt预览"+
                        "相较于1.0.2611.31版本",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            Button(onClick = onOpenLicenses, modifier = Modifier.fillMaxWidth(), shape = RectangleShape, colors = readerButtonColors(), border = readerButtonBorder()) {
                Text("开源许可（${bundledOpenSourceProjects.size} 项）", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }

        item { ControlSpacer() }
        item {
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth(), shape =RectangleShape, colors = readerButtonColors(), border = readerButtonBorder()) {
                Text("返回", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
