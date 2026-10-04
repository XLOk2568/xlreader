import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

android {
    namespace = "com.xialiangok.xlreader"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.xialiangok.xlreader"
        // Android 11 (API 30) 及以上。
        // Android 11 就是 Wear OS 3，是当前 Wear Compose 组件库能覆盖到的最低一代手表系统
        // （wear compose 各库自身声明 minSdk 25，所以这里没有库层面的下限冲突）。
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.2611.13"
    }

    signingConfigs {
        // 没有提供自己的签名文件时，回退到 debug 签名。
        // 目的是让 assembleRelease 出来的「已优化」包可以直接侧载到手表上。
        // 上架 Google Play 前，请通过环境变量换成自己的 keystore。
        create("release") {
            val keystoreFile = System.getenv("XLREADER_KEYSTORE_FILE")
            val keystorePassword = System.getenv("XLREADER_KEYSTORE_PASSWORD")
            val keyAliasEnv = System.getenv("XLREADER_KEY_ALIAS")
            val keyPasswordEnv = System.getenv("XLREADER_KEY_PASSWORD")

            if (keystoreFile != null && keystorePassword != null && keyAliasEnv != null && keyPasswordEnv != null) {
                storeFile = file(keystoreFile)
                storePassword = keystorePassword
                keyAlias = keyAliasEnv
                keyPassword = keyPasswordEnv
            } else {
                val debugConfig = signingConfigs.getByName("debug")
                storeFile = debugConfig.storeFile
                storePassword = debugConfig.storePassword
                keyAlias = debugConfig.keyAlias
                keyPassword = debugConfig.keyPassword
            }
        }
    }

    buildTypes {
        // debug：**故意不开** R8 优化，保持 Android Studio 的标准开发体验。
        //
        // 之前的版本把 optimization.enable 也设成了 true，结果 debug 变体的字节码
        // 被 R8 处理过，副作用是：
        //   1. Android Studio 里 Compose 预览渲染不出来（预览走 Layoutlib，依赖未混淆的类）；
        //   2. 从 Android Studio 直接 Run 装上去的 debug 包启动即闪退；
        //   3. 断点调试、Apply Changes 也不可靠。
        // 另外 android.r8.gradual.support 目前还是实验性开关（AGP 自己会打印 WARNING），
        // 用在日常调试的变体上本身就不合适。
        //
        // 体积与流畅度的收益体现在要发布的 release 包上，那里保持开启。
        debug {
            optimization {
                enable = false
            }
        }
        // release：打开 R8 代码压缩/优化、资源压缩，并自动合并依赖库的 baseline profile。
        // 这才是体积从 26 MB 降到 1.6 MB、并且滑动跟手的来源。
        release {
            optimization {
                enable = true
            }
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.core.ktx)
    implementation(libs.core.splashscreen)
    implementation(libs.ui)
    implementation(libs.ui.graphics)

    // 预览注解必须放在 implementation：@Preview 与 @WearPreviewDevices 写在 src/main 里，
    // 放在 debugImplementation 会让 main 源集编译不过，预览也就无从谈起。
    // 这两个包只是注解 + 少量接口，R8 会把 release 里真正没被引用的部分全部删掉
    // （实测 release 体积与把它们放进 debugImplementation 时完全一致）。
    implementation(libs.ui.tooling.preview)
    implementation(libs.compose.ui.tooling)

    // 真正用来渲染预览的工具链，只在 debug 变体存在。
    debugImplementation(libs.ui.tooling)

    // EPUB 解析器是纯 JVM 代码（ZipFile + 字符串处理），可以在本地 JVM 上直接测。
    testImplementation(libs.junit)
}
