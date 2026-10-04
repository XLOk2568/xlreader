package com.xialiangok.xlreader.presentation.screens

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.TimeText

/**
 * 所有页面共用的骨架。
 *
 * 这里刻意选择**普通的 [LazyColumn]**，而不是带缩放/渐变变换的列表，
 * 于是滚动时列表项不会放大、缩小或淡出——这正是本应用“减少视觉效果”的做法：
 * 没有任何逐项动画，滚动跟手且省电。
 *
 * 下面两点也都跟“库里的规格写死、不受主题控制”有关
 * （[androidx.wear.compose.material3.MotionScheme] 与 `LocalReduceMotion` 都够不着它们）：
 * 1. 顶部时间：不把 `scrollState` 交给 [ScreenScaffold]，因而它拿不到
 *    `scrollInfoProvider`（默认 null）。库只有在拿到该 provider 时才会用 `scrollAway`
 *    把时间文本包起来，而那一层里的缩放/淡出/位移是写死时长的 tween，所以留空之后
 *    时间文本就是纯静态的：滚动时不缩、不淡、不移，“停手 2 秒再浮回来”的回弹也没有了。
 * 2. 滚动指示条：全应用都不再显示（[ScreenScaffold] 的 `scrollIndicator` 恒传 `null`）。
 *    这里有个坑值得记下来：库只在「传了 `scrollState`」的那条分支里给指示条套
 *    `Modifier.align(Alignment.CenterEnd)`（见 `ScreenScaffold` 源码里
 *    `scrollInfoProvider?.let { AnimatedIndicator(modifier = Modifier.align(CenterEnd)) } ?: scrollIndicator`），
 *    而本骨架为了关掉时间文本的动效**刻意不传 scrollState**，于是就算自己写指示条，
 *    也会落在 Box 的默认位置 TopStart —— 也就是屏幕上**左上角**，而不是右侧。
 *    反正这个应用不需要它，直接整条不显示。
 *
 * @param timeText  屏幕顶部的时间文本，默认使用系统时间。
 * @param resetKey  变化时把列表滚到 [startIndex]。切换目录、翻章都必须重置，
 *   否则列表状态会被复用，用户一进新内容就停在中间。
 * @param startIndex [resetKey] 变化时滚动到的位置。正文页用它恢复上次读到的段落。
 * @param listState 调用方如果需要在外面读滚动位置（例如记录阅读进度），
 *   可以自己创建状态传进来。
 * @param onTap     单击列表区域（不是点在具体按钮上）的回调。正文页用它切换快捷菜单。
 * @param content   列表内容。
 */
@Composable
fun WearListScreen(
    timeText: @Composable () -> Unit = { TimeText() },
    resetKey: Any? = null,
    startIndex: Int = 0,
    listState: LazyListState = rememberLazyListState(),
    onTap: (() -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    AppScaffold {
        LaunchedEffect(resetKey) {
            // 下标越界时由 LazyList 自己钳制；恢复进度失败不该让页面崩掉。
            runCatching { listState.scrollToItem(startIndex.coerceAtLeast(0)) }
        }
        // 注意：这里**没有传 scrollState**，走的是 [ScreenScaffold] 那个不带滚动状态的
        // 重载，scrollInfoProvider 保持默认的 null —— 这就是上面注释里关掉动效的开关本身。
        ScreenScaffold(
            timeText = timeText,
            // 整条滚动指示条不创建（null = 不显示）。理由见上面类注释第 2 条。
            scrollIndicator = null,
        ) { contentPadding ->
            // 用 rememberUpdatedState 拿到最新的回调，避免每次重组都重建手势检测器。
            val tapHandler = rememberUpdatedState(onTap)
            val tapModifier = if (onTap != null) {
                Modifier.pointerInput(Unit) {
                    // 只认「按下后没有移动」的点击；拖动交给 LazyColumn 自己滚动。
                    // 子级按钮会消费掉自己的点击，所以这里不会误触发。
                    detectTapGestures { tapHandler.value?.invoke() }
                }
            } else {
                Modifier
            }

            LazyColumn(
                state = listState,
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .then(tapModifier),
                content = content,
            )
        }
    }
}
