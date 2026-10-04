package com.xialiangok.xlreader.presentation.screens

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
import com.xialiangok.xlreader.presentation.theme.readerButtonColors

/**
 * 只有一个标题、一段说明和一个按钮的简单页面。
 *
 * 加载中、解析失败、没有权限都用它，避免为每种情况各写一套布局。
 */
@Composable
fun NoticeScreen(
    title: String,
    message: String,
    actionLabel: String = "返回",
    onAction: (() -> Unit)? = null,
) {
    WearListScreen {
        item { ListHeader { Text(title, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) } }
        item {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (onAction != null) {
            item { Spacer(Modifier.height(10.dp)) }
            item {
                Button(onClick = onAction, modifier = Modifier.fillMaxWidth(), shape = RectangleShape,colors = readerButtonColors()) {
                    Text(actionLabel, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        item { Spacer(Modifier.height(28.dp)) }
    }
}
