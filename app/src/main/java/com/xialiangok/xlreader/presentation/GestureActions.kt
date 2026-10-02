package com.xialiangok.xlreader.presentation

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.math.abs

/**
 * 当前页面对体感手势的响应。
 *
 * 体感手势是在根组件层面、由传感器事件触发的，触发时要知道「现在这一页能做什么」，
 * 但页面本身不该为此多出一堆参数，所以各页用 [BindGestureActions] 把回调登记到这里，
 * 根组件拿这个对象去分发。
 *
 * 对象本身是稳定的（每次重组只刷新里面的回调字段，不引起重组）：登记的是回调，
 * 回调里读的还是各自页面自己的 state，所以永远不会拿到旧值。
 */
class GestureActionHolder {

    /** 谁登记的这一份；离开组合时靠它避免把新页面的登记误清掉。 */
    private var owner: Any? = null

    /** 等价于单击屏幕。 */
    var onTap: (() -> Unit)? = null

    /** 等价于系统返回。 */
    var onBack: (() -> Unit)? = null

    /** 按条目滚动，正数向下、负数向上。 */
    var onScrollBy: ((Int) -> Unit)? = null

    fun bind(token: Any) {
        owner = token
    }

    fun unbind(token: Any) {
        if (owner !== token) return
        owner = null
        onTap = null
        onBack = null
        onScrollBy = null
    }
}

/** 根组件提供、页面登记用的那个容器。 */
val LocalGestureActions = staticCompositionLocalOf { GestureActionHolder() }

/**
 * 页面登记自己支持的手势动作；传 null 表示这一页不响应这个动作。
 *
 * 必须在页面里调用：它跟着页面的组合生命周期走，页面一走就自动注销，
 * 于是「离开启用了手势的页面」立刻就不再有任何手势动作可执行。
 */
@Composable
fun BindGestureActions(
    onTap: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onScrollBy: ((Int) -> Unit)? = null,
) {
    val holder = LocalGestureActions.current
    val token = remember { Any() }

    // 每次重组都刷新一遍回调（只是字段赋值，不触发重组）。
    SideEffect {
        holder.bind(token)
        holder.onTap = onTap
        holder.onBack = onBack
        holder.onScrollBy = onScrollBy
    }

    DisposableEffect(token) {
        onDispose { holder.unbind(token) }
    }
}

/** 找屏幕上离正中央最近的那一项的 key；列表还没布局出来时返回 null。 */
fun centeredItemKey(listState: LazyListState): Any? {
    val info = listState.layoutInfo
    if (info.visibleItemsInfo.isEmpty()) return null
    val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
    return info.visibleItemsInfo
        .minByOrNull { abs(it.offset + it.size / 2 - center) }
        ?.key
}

/**
 * 按条目滚动：直接跳到「当前第一条 ± delta」条。
 *
 * 用 [LazyListState.scrollToItem] 而不是按像素滚 —— 手势里说的「速度」就是条目数，
 * 而且这个调用没有任何动画，符合本应用「不做动效」的取向。
 */
suspend fun scrollByItems(listState: LazyListState, delta: Int) {
    if (delta == 0) return
    val target = (listState.firstVisibleItemIndex + delta).coerceAtLeast(0)
    listState.scrollToItem(target)
}
