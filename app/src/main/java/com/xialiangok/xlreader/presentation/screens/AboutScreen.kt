package com.xialiangok.xlreader.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
                text = "版本 1.0.2610.28 · Wear OS\n最低支持 Android 11（API 30）\n以下为GPT5.6L生成",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
        item {
            Text(
                text = "一个安静的 epub 阅读器：黑底灰字、单一强调色，列表与按钮不做缩放渐变，" +
                    "只保留必要的过渡。",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
        item {
            Text(
                text = "电子书来自你自己的手表存储，应用不内置任何内容、不联网、" +
                    "不上传任何文件；唯一需要的「所有文件访问」权限只用于读取你指定的目录。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
        item {
            Text(
                text = "1.增加保持上次打开Path功能\n"+
                        "2.修改目录逻辑\n"+
                        "3.修改部分ui\n"+
                        "4.修复手势和优化手势体感",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            Button(onClick = onOpenLicenses, modifier = Modifier.fillMaxWidth(), shape = RectangleShape, colors = readerButtonColors()) {
                Text("开源许可（${bundledOpenSourceProjects.size} 项）")
            }
        }

        item { Spacer(Modifier.height(10.dp)) }
        item {
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth(), shape =RectangleShape, colors = readerButtonColors()) {
                Text("返回")
            }
        }
        item { Spacer(Modifier.height(28.dp)) }
    }
}
