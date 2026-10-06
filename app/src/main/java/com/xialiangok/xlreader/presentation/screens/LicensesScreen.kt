package com.xialiangok.xlreader.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListSubHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.xialiangok.xlreader.data.BUILD_REFERENCE_NOTE
import com.xialiangok.xlreader.data.OpenSourceProject
import com.xialiangok.xlreader.data.bundledOpenSourceProjects
import com.xialiangok.xlreader.presentation.theme.readerButtonColors

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
                text = "本应用不联网、不采集数据。以下项目随应用一同分发，" +
                    "全部使用 Apache License 2.0。",
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

        item { Spacer(Modifier.height(10.dp)) }
        item {
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth(), shape = RectangleShape,colors = readerButtonColors()) {
                Text("返回", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
        item { Spacer(Modifier.height(28.dp)) }
    }
}

/**
 * 一条开源项目。
 *
 * 这里没有用 Wear 的 `Card`：该版本里 `Card` 的两个重载都强制要求 `onClick`，
 * 而许可条目是只读信息、不该出现点击涟漪，所以用带圆角底色的 Column 自绘。
 */
@Composable
private fun LicenseCard(project: OpenSourceProject) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = project.name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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
