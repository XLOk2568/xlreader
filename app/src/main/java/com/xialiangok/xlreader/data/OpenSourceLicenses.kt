package com.xialiangok.xlreader.data

import androidx.compose.runtime.Immutable

/**
 * 关于页「开源许可」里展示的一个项目。
 *
 * @param name    项目名称
 * @param license 许可证标识
 * @param note    在本应用里承担什么职责（一句话）
 */
@Immutable
data class OpenSourceProject(
    val name: String,
    val license: String,
    val note: String,
)

/**
 * 随应用一同分发的开源项目。
 *
 * 这份清单是从 release APK 里实际的 META-INF 版本标记核对出来的，
 * 不是凭印象写的：androidx 全家桶按「项目」归并（例如 51 个 artifact 归到
 * AndroidX / Compose / Wear Compose 等 8 条），否则手表小屏上根本列不下。
 *
 * 这些项目全部使用 Apache License 2.0。
 */
val bundledOpenSourceProjects: List<OpenSourceProject> = listOf(

    OpenSourceProject(
        name ="xlreader" ,
        license="Apache License 2.0",
        note="https://github.com/XLOk2568/xlreader",
    ),
    OpenSourceProject(
        name = "WearFiles",
        license = "Apache-2.0",
        note = "https://github.com/dertefter/WearFiles"
    ),
    OpenSourceProject(
        name = "AndroidX / Jetpack",
        license = "Apache-2.0",
        note = "core、core-ktx、activity、lifecycle、savedstate、startup、tracing、emoji2 等基础库",
    ),
    OpenSourceProject(
        name = "Jetpack Compose",
        license = "Apache-2.0",
        note = "runtime、ui、foundation、animation、material-ripple 等声明式 UI 运行时",
    ),
    OpenSourceProject(
        name = "Compose for Wear OS",
        license = "Apache-2.0",
        note = "圆形表盘组件：AppScaffold、ScreenScaffold、卡片、开关与时间文本",
    ),
    OpenSourceProject(
        name = "Kotlin 标准库",
        license = "Apache-2.0",
        note = "语言运行时",
    ),
    OpenSourceProject(
        name = "kotlinx.coroutines",
        license = "Apache-2.0",
        note = "协程与主线程调度，Compose 的异步基础",
    ),
    OpenSourceProject(
        name = "androidx.profileinstaller",
        license = "Apache-2.0",
        note = "首次启动时安装 baseline profile，使后续启动与滚动更流畅",
    ),
    OpenSourceProject(
        name = "AndroidX SplashScreen",
        license = "Apache-2.0",
        note = "纯黑启动画面，避免启动瞬间闪白",
    ),
    OpenSourceProject(
        name = "AndroidX graphics-path / shapes",
        license = "Apache-2.0",
        note = "矢量路径与形状的原生实现（Compose 的传递依赖）",
    ),
)

/**
 * 构建配置的参考来源。
 *
 * 说明清楚：WearFiles 不是本应用的代码依赖，只是我在配置构建时参考了它的做法
 * （debug 与 release 都启用 `optimization`、jvmTarget 17 等）。
 * 单独列出来是为了不把「参考」和「使用」混为一谈。
 */
const val BUILD_REFERENCE_NOTE: String =
    "基于 dertefter/WearFiles（Apache-2.0）"
