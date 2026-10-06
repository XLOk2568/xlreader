package com.xialiangok.xlreader.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListSubHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import com.xialiangok.xlreader.data.ReaderPreferences
import com.xialiangok.xlreader.data.TimeDisplay
import com.xialiangok.xlreader.data.blueOf
import com.xialiangok.xlreader.data.greenOf
import com.xialiangok.xlreader.data.packRgb
import com.xialiangok.xlreader.data.redOf
import com.xialiangok.xlreader.data.spacingLabel
import com.xialiangok.xlreader.data.stepAlpha
import com.xialiangok.xlreader.data.stepChannel
import com.xialiangok.xlreader.data.stepControlSpacing
import com.xialiangok.xlreader.data.stepFontSize
import com.xialiangok.xlreader.data.stepMargin
import com.xialiangok.xlreader.data.stepSpacingLevel
import com.xialiangok.xlreader.presentation.theme.readerButtonColors
import com.xialiangok.xlreader.presentation.theme.rgbColor

/**
 * 设置页.
 *
 * 与阅读界面里单击正文弹出的快捷菜单操作的是**同一份** [ReaderPreferences],
 * 所以在哪边改都一样, 改完两边都生效.
 *
 * 阅读排版先收起, 点按钮才展开详细设置.
 * 颜色分成「文字颜色」(正文 / 当前章 / 文件夹名 / 最近打开) 与
 * 「控件颜色」(强调色 / 背景 / 按钮 / 目录开关条) 两大折叠分组, 默认都收起.
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

  // 展开状态只活在这一页; 离开再进来全部收起.
  var layoutExpanded by remember { mutableStateOf(false) }
  var textColorsExpanded by remember { mutableStateOf(false) }
  var controlColorsExpanded by remember { mutableStateOf(false) }
  var timeDisplayExpanded by remember { mutableStateOf(false) }

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

    // ---- 阅读排版 ----
    item(key = "layout.toggle") {
      ExpandButton("阅读排版", layoutExpanded) { layoutExpanded = !layoutExpanded }
    }
    if (layoutExpanded) {
      // 三档字号都直接就是磅值, 用「− / ＋」步进 (按住可连发).
      item(key = "layout.titleFont") {
        StepRow(
          label = "标题字号",
          value = preferences.titleFontSize.toString(),
          onMinus = {
            onPreferencesChange(
              preferences.copy(titleFontSize = stepFontSize(preferences.titleFontSize, -1)),
            )
          },
          onPlus = {
            onPreferencesChange(
              preferences.copy(titleFontSize = stepFontSize(preferences.titleFontSize, +1)),
            )
          },
        )
      }
      item(key = "layout.bodyFont") {
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
      item(key = "layout.smallFont") {
        StepRow(
          label = "小字号",
          value = preferences.smallFontSize.toString(),
          onMinus = {
            onPreferencesChange(
              preferences.copy(smallFontSize = stepFontSize(preferences.smallFontSize, -1)),
            )
          },
          onPlus = {
            onPreferencesChange(
              preferences.copy(smallFontSize = stepFontSize(preferences.smallFontSize, +1)),
            )
          },
        )
      }
      item(key = "layout.spacing") {
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
      // 上下边距替换系统默认的安全留白, 最小 2dp, 每次 1dp, 不封顶.
      item(key = "layout.marginTop") {
        StepRow(
          label = "上边距",
          value = preferences.marginTop.toString(),
          onMinus = {
            onPreferencesChange(preferences.copy(marginTop = stepMargin(preferences.marginTop, -1)))
          },
          onPlus = {
            onPreferencesChange(preferences.copy(marginTop = stepMargin(preferences.marginTop, +1)))
          },
        )
      }
      item(key = "layout.marginBottom") {
        StepRow(
          label = "下边距",
          value = preferences.marginBottom.toString(),
          onMinus = {
            onPreferencesChange(preferences.copy(marginBottom = stepMargin(preferences.marginBottom, -1)))
          },
          onPlus = {
            onPreferencesChange(preferences.copy(marginBottom = stepMargin(preferences.marginBottom, +1)))
          },
        )
      }
      // 页内分隔 Spacer 的统一高度, 0..999, 可以内联直接填整数.
      item(key = "layout.controlSpacing") {
        NumberStepRow(
          label = "大多控件间距",
          value = preferences.controlSpacing,
          inputRange = ReaderPreferences.MIN_CONTROL_SPACING..ReaderPreferences.MAX_CONTROL_SPACING,
          onValueChange = { onPreferencesChange(preferences.copy(controlSpacing = it)) },
          onMinus = {
            onPreferencesChange(
              preferences.copy(controlSpacing = stepControlSpacing(preferences.controlSpacing, -1)),
            )
          },
          onPlus = {
            onPreferencesChange(
              preferences.copy(controlSpacing = stepControlSpacing(preferences.controlSpacing, +1)),
            )
          },
        )
      }
    }

    // ---- 文字颜色: 正文 / 当前章 / 文件夹名 / 最近打开 ----
    item(key = "textColors.toggle") {
      ExpandButton("文字颜色", textColorsExpanded, fillWidth = true) { textColorsExpanded = !textColorsExpanded }
    }
    if (textColorsExpanded) {
      item(key = "textColors.text.preview") {
        ColorPreview(
          label = "正文文字",
          rgb = preferences.effectiveTextColor,
          alpha = preferences.textColorAlpha,
        )
      }
      item(key = "textColors.text.channels") {
        ColorChannelRows(
          rgb = preferences.textColor,
          onRgbChange = { onPreferencesChange(preferences.copy(textColor = it)) },
        )
      }
      item(key = "textColors.text.alpha") {
        AlphaRow(
          value = preferences.textColorAlpha,
          onChange = { onPreferencesChange(preferences.copy(textColorAlpha = it)) },
        )
      }
      colorSection("当前章节颜色", "textColors.currentChapter", preferences.currentChapterColor) {
        onPreferencesChange(preferences.copy(currentChapterColor = it))
      }
      colorSection("文件夹名颜色", "textColors.folder", preferences.folderColor) {
        onPreferencesChange(preferences.copy(folderColor = it))
      }
      colorSection("最近打开颜色", "textColors.latest", preferences.latestColor) {
        onPreferencesChange(preferences.copy(latestColor = it))
      }
      item(key = "textColors.reset") {
        ResetButton("文字颜色恢复默认") {
          onPreferencesChange(
            preferences.copy(
              textColor = ReaderPreferences.DEFAULT_TEXT_COLOR,
              textColorAlpha = ReaderPreferences.DEFAULT_COLOR_ALPHA,
              textBrightness = ReaderPreferences.MAX_TEXT_BRIGHTNESS,
              currentChapterColor = ReaderPreferences.DEFAULT_CURRENT_CHAPTER_COLOR,
              folderColor = ReaderPreferences.DEFAULT_FOLDER_COLOR,
              latestColor = ReaderPreferences.DEFAULT_LATEST_COLOR,
            ),
          )
        }
      }
    }

    // ---- 控件颜色: 强调色 / 背景 / 按钮 / 目录开关条 ----
    item(key = "controlColors.toggle") {
      ExpandButton("控件颜色", controlColorsExpanded, fillWidth = true) {
        controlColorsExpanded = !controlColorsExpanded
      }
    }
    if (controlColorsExpanded) {
      item(key = "controlColors.accent.preview") {
        ColorPreview(
          label = "强调色示例",
          rgb = preferences.accentColor,
          alpha = preferences.accentColorAlpha,
        )
      }
      item(key = "controlColors.accent.channels") {
        ColorChannelRows(
          rgb = preferences.accentColor,
          onRgbChange = { onPreferencesChange(preferences.copy(accentColor = it)) },
        )
      }
      item(key = "controlColors.accent.alpha") {
        AlphaRow(
          value = preferences.accentColorAlpha,
          onChange = { onPreferencesChange(preferences.copy(accentColorAlpha = it)) },
        )
      }
      colorSection("背景色", "controlColors.background", preferences.backgroundColor) {
        onPreferencesChange(preferences.copy(backgroundColor = it))
      }
      colorSection("按钮底色", "controlColors.button", preferences.buttonColor) {
        onPreferencesChange(preferences.copy(buttonColor = it))
      }
      colorSection("目录开关条颜色", "controlColors.menuBar", preferences.menuBarColor) {
        onPreferencesChange(preferences.copy(menuBarColor = it))
      }
      item(key = "controlColors.reset") {
        ResetButton("控件颜色恢复默认") {
          onPreferencesChange(
            preferences.copy(
              accentColor = ReaderPreferences.DEFAULT_ACCENT_COLOR,
              accentColorAlpha = ReaderPreferences.DEFAULT_COLOR_ALPHA,
              backgroundColor = ReaderPreferences.DEFAULT_BACKGROUND_COLOR,
              buttonColor = ReaderPreferences.DEFAULT_BUTTON_COLOR,
              menuBarColor = ReaderPreferences.DEFAULT_MENU_BAR_COLOR,
            ),
          )
        }
      }
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
      // 动效是全应用固定关掉的 (见 theme/Theme.kt), 所以这里只作展示.
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
        Text(text = "阅读时常亮",
          modifier = Modifier.fillMaxWidth(),
          textAlign = TextAlign.Center
        )
      }
    }
    // ---- 时间显示: Wear 原生弧形 / 无弧度数字 / 不显示 ----
    item(key = "display.time.toggle") {
      ExpandButton("时间显示 ${preferences.timeDisplay.label}", timeDisplayExpanded) {
        timeDisplayExpanded = !timeDisplayExpanded
      }
    }
    if (timeDisplayExpanded) {
      // 三个选项都不撑满宽度, 选中的那个带勾.
      TimeDisplay.entries.forEach { mode ->
        item(key = "display.time.${mode.name}") {
          val selected = preferences.timeDisplay == mode
          Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Button(
              onClick = { onPreferencesChange(preferences.copy(timeDisplay = mode)) },
              shape = RectangleShape,
              colors = readerButtonColors(),
            ) {
              Text(
                text = if (selected) "✓ ${mode.label}" else mode.label,
                color = if (selected) {
                  MaterialTheme.colorScheme.primary
                } else {
                  MaterialTheme.colorScheme.onSurface
                },
              )
            }
          }
        }
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
      // 传感器设置是独立的一份数据, 内容比这一页多得多, 所以单独开一页.
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
    item { ControlSpacer() }
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
  }
}

/** 主分区按钮: 收起带 ▸, 展开带 ▾. 颜色分组用 [fillWidth] = false 不撑满宽度. */
@Composable
private fun ExpandButton(
  text: String,
  expanded: Boolean,
  fillWidth: Boolean = true,
  onClick: () -> Unit,
) {
  val label: @Composable () -> Unit = {
    Text(
      text = "$text ${if (expanded) "▾" else "▸"}",
      textAlign = TextAlign.Center,
      modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier,
    )
  }
  if (fillWidth) {
    Button(
      onClick = onClick,
      modifier = Modifier.fillMaxWidth(),
      shape = RectangleShape,
      colors = readerButtonColors(),
    ) {
      label()
    }
  } else {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
      Button(onClick = onClick, shape = RectangleShape, colors = readerButtonColors()) {
        label()
      }
    }
  }
}

/** 恢复默认按钮: 宽度不撑满, 只包住文字, 外层居中. */
@Composable
private fun ResetButton(text: String, onClick: () -> Unit) {
  Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    Button(onClick = onClick, shape = RectangleShape, colors = readerButtonColors()) {
      Text(text = text, textAlign = TextAlign.Center)
    }
  }
}

/** 颜色预览: 显示当前 RGB 与透明度, 文字本身用这个颜色画. */
@Composable
private fun ColorPreview(label: String, rgb: Int, alpha: Int) {
  Text(
    text = "#%02X%02X%02X  A%d  %s".format(redOf(rgb), greenOf(rgb), blueOf(rgb), alpha, label),
    color = rgbColor(rgb, alpha),
    textAlign = TextAlign.Center,
    modifier = Modifier.fillMaxWidth(),
  )
}

/** 透明度一行: 0..255, 每次走 1. */
@Composable
private fun AlphaRow(value: Int, onChange: (Int) -> Unit) {
  StepRow(
    label = "透明度",
    value = value.toString(),
    onMinus = { onChange(stepAlpha(value, -1)) },
    onPlus = { onChange(stepAlpha(value, +1)) },
  )
}

/** 追加一组「颜色标题 + 预览 + RGB」, 供文字颜色与控件颜色复用. */
private fun LazyListScope.colorSection(
  title: String,
  keyPrefix: String,
  rgb: Int,
  onRgbChange: (Int) -> Unit,
) {
  item(key = "$keyPrefix.header") {
    ListSubHeader {
      Text(
        text = title,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
      )
    }
  }
  item(key = "$keyPrefix.preview") {
    ColorPreview(label = title, rgb = rgb, alpha = ReaderPreferences.MAX_COLOR_ALPHA)
  }
  item(key = "$keyPrefix.channels") {
    ColorChannelRows(rgb = rgb, onRgbChange = onRgbChange)
  }
}

/** R / G / B 三行, 每次走 1 档, 文字色与强调色共用. */
@Composable
private fun ColorChannelRows(rgb: Int, onRgbChange: (Int) -> Unit) {
  val red = redOf(rgb)
  val green = greenOf(rgb)
  val blue = blueOf(rgb)
  Column {
    StepRow(
      label = "R",
      value = red.toString(),
      onMinus = { onRgbChange(packRgb(stepChannel(red, -1), green, blue)) },
      onPlus = { onRgbChange(packRgb(stepChannel(red, +1), green, blue)) },
    )
    StepRow(
      label = "G",
      value = green.toString(),
      onMinus = { onRgbChange(packRgb(red, stepChannel(green, -1), blue)) },
      onPlus = { onRgbChange(packRgb(red, stepChannel(green, +1), blue)) },
    )
    StepRow(
      label = "B",
      value = blue.toString(),
      onMinus = { onRgbChange(packRgb(red, green, stepChannel(blue, -1))) },
      onPlus = { onRgbChange(packRgb(red, green, stepChannel(blue, +1))) },
    )
  }
}

private const val FONT_HINT =
  "提示：为提供更流畅的体验「减少动效」强制开启\n\n" +
          "屏幕亮度与文字亮度在阅读界面里调：单击正文即可弹出快捷菜单。"
