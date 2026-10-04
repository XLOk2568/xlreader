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
import androidx.wear.compose.material3.ListSubHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import com.xialiangok.xlreader.data.ReaderPreferences
import com.xialiangok.xlreader.data.spacingLabel
import com.xialiangok.xlreader.data.stepFontSize
import com.xialiangok.xlreader.data.stepSpacingLevel
import com.xialiangok.xlreader.presentation.theme.readerButtonColors

/**
 * 设置页。
 *
 * 与阅读界面里单击正文弹出的快捷菜单操作的是**同一份** [ReaderPreferences]，
 * 所以在哪边改都一样，改完两边都生效。
 *
 * 排版相关的项（字号、段间距）都用「− / ＋」步进按钮代替滑块：在圆形小屏上命中率更高，
 * 而且没有任何拖动动画，符合本应用的低动效取向。
 */
@Composable
fun SettingsScreen(
    preferences: ReaderPreferences,
    onPreferencesChange: (ReaderPreferences) -> Unit,
    onOpenSensorSettings: () -> Unit,
    onOpenDataAdmin: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    WearListScreen {
        item {
            ListHeader {
                Text(
                    text = "设置",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
        item {
            ListSubHeader {
                Text(
                    text = "阅读排版",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
        item {
            // 字号是一串连续的磅值（1..999），没法点击循环，
            // 所以和阅读菜单里一样用「− / ＋」步进（按住可连发）。
            StepRow(
                label = "正文字号",
                value = preferences.fontSize.toString(),
                onMinus = {
                    onPreferencesChange(
                        preferences.copy(fontSize = stepFontSize(preferences.fontSize, -1)),
                    )
                },
                onPlus = {
                    onPreferencesChange(
                        preferences.copy(fontSize = stepFontSize(preferences.fontSize, +1)),
                    )
                },
            )
        }
        item {
            // 段间距和字号一样是连续数字（单位 dp），同样用「− / ＋」而不是点击循环：
            // 阅读菜单里就是这么做的，两边保持一致（也是同一份 ReaderPreferences）。
            StepRow(
                label = "段间距",
                value = spacingLabel(preferences.spacingLevel),
                onMinus = {
                    onPreferencesChange(
                        preferences.copy(spacingLevel = stepSpacingLevel(preferences.spacingLevel, -1)),
                    )
                },
                onPlus = {
                    onPreferencesChange(
                        preferences.copy(spacingLevel = stepSpacingLevel(preferences.spacingLevel, +1)),
                    )
                },
            )
        }
        item {
            ListSubHeader {
                Text(
                    text = "显示",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
        item {
            // 动效是全应用固定关掉的（见 theme/Theme.kt），所以这里只作展示：
            // 永远显示打开、不允许再点（关闭它也没用，反而会让人以为界面会动起来）。
            SwitchButton(
                checked = true,
                onCheckedChange = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("减少动效", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
        item {
            SwitchButton(
                checked = preferences.keepScreenOn,
                onCheckedChange = { onPreferencesChange(preferences.copy(keepScreenOn = it)) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text="阅读时常亮",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
        item {
            ListSubHeader {
                Text(
                    text = "体感手势(测试)",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
        item {
            // 传感器设置是独立的一份数据（手势列表 + 检测间隔 + 启用页面），
            // 内容比这一页多得多，所以单独开一页，这里只留入口。
            Button(
                onClick = onOpenSensorSettings,
                modifier = Modifier.fillMaxWidth(),
                shape = RectangleShape,
                colors = readerButtonColors(),
            ) {
                Text(text = "传感器设置", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
        item {
            ListSubHeader {
                Text(
                    text = "备份与还原",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
        item {
            // 备份与还原：进自己的 data 目录做文件操作，以及导出/导入设置 zip。
            Button(
                onClick = onOpenDataAdmin,
                modifier = Modifier.fillMaxWidth(),
                shape = RectangleShape,
                colors = readerButtonColors(),
            ) {
                Text(text = "数据目录与备份", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }

        item {
            Text(
                text = FONT_HINT,
                style = MaterialTheme.typography.bodyExtraSmall,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
        item { Spacer(Modifier.height(10.dp)) }
        item {
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                shape = RectangleShape,
                colors = readerButtonColors(),
            ) {
                Text(text = "完成", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
        item { Spacer(Modifier.height(28.dp)) }
    }
}

private const val FONT_HINT =
    "提示：为提供更流畅的体验「减少动效」强制开启\n\n" +
        "屏幕亮度、文字亮度与文字颜色在阅读界面里调：单击正文即可弹出快捷菜单。"
