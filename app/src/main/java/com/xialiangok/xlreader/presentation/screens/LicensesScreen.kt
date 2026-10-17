package com.xialiangok.xlreader.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListSubHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.xialiangok.xlreader.data.BUILD_REFERENCE_NOTE
import com.xialiangok.xlreader.data.OpenSourceProject
import com.xialiangok.xlreader.data.bundledOpenSourceProjects
import com.xialiangok.xlreader.presentation.theme.ReaderBlack
import com.xialiangok.xlreader.presentation.theme.readerButtonBorder
import com.xialiangok.xlreader.presentation.theme.readerButtonColors
import com.xialiangok.xlreader.presentation.theme.readerCardBorder

/**
 * 开源许可页：列出随应用一同分发的开源项目。
 *
 * 清单来自 release APK 的实际内容，按「项目」归并后展示；
 * 每一条给出项目名、许可证，以及它在本应用里承担的职责。
 */
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)

    WearListScreen {
        item {
            ListHeader {
                Text(
                    text = "开源许可",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
        item {
            Text(
                text = "本应用不访问互联网、不采集数据。以下项目随应用一同分发" +
                    "除 NanoHTTPD 使用 BSD-3-Clause 外均为 Apache License 2.0，当然本项目是MIT协议",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        items(
            bundledOpenSourceProjects,
            key = { it.name },
            contentType = { "license" },
        ) { project ->
            LicenseCard(project = project)
        }

        item { ListSubHeader { Text("构建参考", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) } }
        item {
            Text(
                text = BUILD_REFERENCE_NOTE,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item { ListSubHeader { Text("感谢测试", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) } }
        item {
            Text(
                text = "NCSmaoliang"+"\nhttps://github.com/NCSmaoliang",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item { ControlSpacer() }
        item {
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth(), shape = RectangleShape,colors = readerButtonColors(), border = readerButtonBorder()) {
                Text("返回", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * 一条开源项目。
 *
 * 这里没有用 Wear 的 `Card`：该版本里 `Card` 的两个重载都强制要求 `onClick`，
 * 而许可条目是只读信息、不该出现点击涟漪，所以用带底色的 Column 自绘。
 * 自绘也得跟列表项保持一致: 纯黑底 + 按钮色描边 + 直角 (2026-10-31 起的统一口径).
 */
@Composable
private fun LicenseCard(project: OpenSourceProject) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RectangleShape)
            .background(ReaderBlack)
            .border(readerCardBorder(), RectangleShape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = project.name,
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = project.license,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = project.note,
            style = MaterialTheme.typography.bodyExtraSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
