package com.xialiangok.xlreader.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.xialiangok.xlreader.data.bundledOpenSourceProjects
import com.xialiangok.xlreader.presentation.theme.readerButtonColors

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

    WearListScreen {
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
                text = "版本 1.0.2611.24 · Wear OS\n最低支持 Android 11（API 30)",
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
                text = "电子书来自你自己的手表存储，应用不内置任何内容、不访问互联网、" +
                    "不上传任何文件；唯一需要的「所有文件访问」权限只用于读取你指定的目录。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
        item {
            Text(
                text = "1.优化体感\n"+
                        "2.新增自定义颜色和边距了\n"+
                        "3.新增了文件删除等功能"+
                        "4.新增局域网文件管理功能"+
                        "相较于1.0.2611.19版本",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            Button(onClick = onOpenLicenses, modifier = Modifier.fillMaxWidth(), shape = RectangleShape, colors = readerButtonColors()) {
                Text("开源许可（${bundledOpenSourceProjects.size} 项）", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }

        item { ControlSpacer() }
        item {
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth(), shape =RectangleShape, colors = readerButtonColors()) {
                Text("返回", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
