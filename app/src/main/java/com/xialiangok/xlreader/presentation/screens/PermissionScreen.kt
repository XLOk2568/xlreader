package com.xialiangok.xlreader.presentation.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.xialiangok.xlreader.presentation.theme.readerButtonColors

/**
 * 权限引导页。
 *
 * Android 11 起，读取 `.epub` 这类非媒体文件无法通过 READ_EXTERNAL_STORAGE 完成，
 * 而 Wear OS 又没有系统文件选择器（SAF），所以只能申请「所有文件访问」，
 * 由用户到系统设置里手动打开。
 */
@Composable
fun PermissionScreen(
    onOpenSettings: () -> Unit,
    onRecheck: () -> Unit,
) {
    WearListScreen {
        item {
            ListHeader { Text("需要文件访问权限") }
        }
        item {
            Text(
                text = "XLreader 要浏览你手表上的电子书，需要「所有文件访问」权限。",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Text(
                text = "应用本身不联网、不上传任何文件；这个权限只用于读取你指定的目录和 epub。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item { Spacer(Modifier.height(6.dp)) }
        item {
            Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth(), colors = readerButtonColors()) {
                Text("去系统设置授权")
            }
        }
        item {
            Button(onClick = onRecheck, modifier = Modifier.fillMaxWidth(), colors = readerButtonColors()) {
                Text("已授权，重新检查")
            }
        }

        item {
            Text(
                text = "授权路径：设置 → 应用 → XLreader → 所有文件访问",
                style = MaterialTheme.typography.bodyExtraSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item { Spacer(Modifier.height(28.dp)) }
    }
}
