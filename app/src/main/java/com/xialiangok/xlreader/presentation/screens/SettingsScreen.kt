package com.xialiangok.xlreader.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListSubHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import com.xialiangok.xlreader.data.ReaderPreferences
import com.xialiangok.xlreader.data.TimeDisplay
import com.xialiangok.xlreader.data.file.appDataDir
import com.xialiangok.xlreader.data.appendAppLog
import com.xialiangok.xlreader.data.lan.ensureLanIpFile
import com.xialiangok.xlreader.data.spacingLabel
import com.xialiangok.xlreader.data.stepControlSpacing
import com.xialiangok.xlreader.data.stepFontSize
import com.xialiangok.xlreader.data.stepHomeHover
import com.xialiangok.xlreader.data.stepMargin
import com.xialiangok.xlreader.data.stepSpacingLevel
import com.xialiangok.xlreader.presentation.BindGestureActions
import com.xialiangok.xlreader.presentation.scrollByDp
import com.xialiangok.xlreader.presentation.theme.readerButtonBorder
import com.xialiangok.xlreader.presentation.theme.readerButtonColors
import kotlinx.coroutines.launch

/**
 * 设置页.
 *
 * 与阅读界面里单击正文弹出的快捷菜单操作的是**同一份** [ReaderPreferences],
 * 所以在哪边改都一样, 改完两边都生效.
 *
 * 阅读排版先收起, 点按钮才展开详细设置.
 * 2026-10-31 起颜色不再在这一页调: 原来的「文字颜色」「控件颜色」两组删掉,
 * 只留一个「主题设置」按钮, 进独立的 themescreen.kt 里选主题 / 改五色.
 */
@Composable
fun SettingsScreen(
  preferences: ReaderPreferences,
  onPreferencesChange: (ReaderPreferences) -> Unit,
  sensorEnabled: Boolean,
  onSensorEnabledChange: (Boolean) -> Unit,
  onOpenSensorSettings: () -> Unit,
  onOpenLanIp: () -> Unit,
  onOpenDataAdmin: () -> Unit,
  onOpenTheme: () -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)

  // 展开状态只活在这一页; 离开再进来全部收起.
  var layoutExpanded by remember { mutableStateOf(false) }
  var timeDisplayExpanded by remember { mutableStateOf(false) }

  val listState = rememberLazyListState()
  val scope = rememberCoroutineScope()
  val density = LocalDensity.current
  val context = LocalContext.current

  // 体感手势: 这一页只需「返回」和「上下翻页」(列表很长, 靠拨动手腕翻最省力),
  // 没有「单击打开正中央那一项」的概念, 所以不登记单击.
  BindGestureActions(
    onBack = onBack,
    onScrollBy = { delta -> scope.launch { scrollByDp(listState, delta, density) } },
  )

  WearListScreen(listState = listState) {
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
      // 文件列表条目名 (文件夹名 / 文件名) 单独一档字号, 不跟着正文或标题走.
      item(key = "layout.fileListFont") {
        StepRow(
          label = "文件列表字号",
          value = preferences.fileListFontSize.toString(),
          onMinus = {
            onPreferencesChange(
              preferences.copy(fileListFontSize = stepFontSize(preferences.fileListFontSize, -1)),
            )
          },
          onPlus = {
            onPreferencesChange(
              preferences.copy(fileListFontSize = stepFontSize(preferences.fileListFontSize, +1)),
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
      // 顶部悬浮时间是全局那一行时间 (只有 PhoneUI 有), 底部悬浮按钮是文件列表上那个 ▲.
      // 两者都只是贴边距离, 0..999, 每次 1dp.
      item(key = "layout.homeHoverTop") {
        StepRow(
          label = "顶部悬浮上边距",
          value = preferences.homeHoverTop.toString(),
          onMinus = {
            onPreferencesChange(
              preferences.copy(homeHoverTop = stepHomeHover(preferences.homeHoverTop, -1)),
            )
          },
          onPlus = {
            onPreferencesChange(
              preferences.copy(homeHoverTop = stepHomeHover(preferences.homeHoverTop, +1)),
            )
          },
        )
      }
      item(key = "layout.homeHoverBottom") {
        StepRow(
          label = "底部悬浮下边距",
          value = preferences.homeHoverBottom.toString(),
          onMinus = {
            onPreferencesChange(
              preferences.copy(homeHoverBottom = stepHomeHover(preferences.homeHoverBottom, -1)),
            )
          },
          onPlus = {
            onPreferencesChange(
              preferences.copy(homeHoverBottom = stepHomeHover(preferences.homeHoverBottom, +1)),
            )
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

    // ---- 主题: 颜色统一收在独立的主题页里 ----
    item(key = "theme.open") {
      Button(
        onClick = onOpenTheme,
        modifier = Modifier.fillMaxWidth(),
        shape = RectangleShape,
        colors = readerButtonColors(),
        border = readerButtonBorder(),
      ) {
        Text(
          text = "主题设置(using:${preferences.themeName})",
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth(),
        )
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
              border = readerButtonBorder(),
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
      // 总开关放在这里, 和阅读菜单里那个是同一个值: 关掉后根组件直接注销传感器,
      // 不只是在当前页面不生效.
      SwitchButton(
        checked = sensorEnabled,
        onCheckedChange = onSensorEnabledChange,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(text = "体感手势", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
      }
    }
    item {
      // 传感器设置是独立的一份数据, 内容比这一页多得多, 所以单独开一页.
      Button(
        onClick = onOpenSensorSettings,
        modifier = Modifier.fillMaxWidth(),
        shape = RectangleShape,
        colors = readerButtonColors(),
        border = readerButtonBorder(),
      ) {
        Text(text = "传感器设置", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
      }
    }
    item {
      ListSubHeader {
        Text(
          text = "局域网",
          modifier = Modifier.fillMaxWidth(),
          textAlign = TextAlign.Center
        )
      }
    }
    item {
      // 局域网文件管理的地址 / 端口 / 密码存在 data 目录的 lanip.txt.xlr 里,
      // 内容不多但需要配着看, 所以单独开一页.
      Button(
        onClick = {
          // 单击这一项才去建 lanip.txt.xlr: 文件不存在就写一份默认的
          // (地址 192.168.888.888:8888 + key{88888888}); 已存在就原样保留, 不覆盖用户手改的内容.
          // 用户口径是「单击无线局域网设置时检测存不存在」, 不是 App 启动时建.
          // 写失败不在这里提示, 下一页 (lanip.kt) 会把失败原因显示出来并记进 log.txt.
          appendAppLog(context, "点击无线局域网设置")
          runCatching { ensureLanIpFile(appDataDir(context)) }
          onOpenLanIp()
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RectangleShape,
        colors = readerButtonColors(),
        border = readerButtonBorder(),
      ) {
        Text(text = "无线局域网设置", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
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
        border = readerButtonBorder(),
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
        border = readerButtonBorder(),
      ) {
        Text(text = "返回", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
      }
    }
  }
}

/** 主分区按钮: 收起带 ▸, 展开带 ▾. [fillWidth] = false 时按钮只包住文字, 外层居中. */
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
      border = readerButtonBorder(),
    ) {
      label()
    }
  } else {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
      Button(onClick = onClick, shape = RectangleShape, colors = readerButtonColors(), border = readerButtonBorder()) {
        label()
      }
    }
  }
}

private const val FONT_HINT =
  "提示：为提供更流畅的体验「减少动效」强制开启\n\n" +
          "屏幕亮度与文字亮度在阅读界面里调：单击正文即可弹出快捷菜单。"
