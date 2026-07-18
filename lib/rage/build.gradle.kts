// ============================================================
// ⚠️ 模块归属声明（见 docs/architecture/MODULE_OWNERSHIP.md）
// ============================================================
// 本库（:lib:rage）是 :apk:rage 的私有库。
//
// 打包规则：
//   ✅ 只打包进 :apk:rage
//   ❌ 不打包进 :app（主 APK）  ← 迁移期允许（见 ModuleOwnershipPlugin）
//   ❌ 不打包进其他 :apk:*
//
// 原因：
//   - 狂暴模式 Kotlin 薄壳（RageEngine + 31 技能目录 + 内存任务存储 + 数据模型）
//     只有 Rage APK 需要
//   - 编排逻辑已下沉到 :rage-native（C++17 核心）→ :rage-jni（JNI 桥接）
//   - 其他 APK 通过 ApexClient.rage.* 跨 APK 调用
//
// ARCH-3: 新增 api(project(":rage-jni")) —— RageEngine 委托给 RageNativeBridge
// ============================================================

plugins {
    id("apex.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.apex.lib.rage"
    defaultConfig { consumerProguardFiles("consumer-rules.pro") }
    buildFeatures { buildConfig = true }
}

dependencies {
    api(project(":sdk:common-core"))
    api(project(":sdk:process-bridge"))

    // ARCH-3: 暴露 :rage-jni 的 RageNativeBridge + Native* 数据类给本模块的
    // RageEngine.kt / NativeMappers.kt 使用。用 api() 而非 implementation()，
    // 使得 :app 等消费者通过 :lib:rage 即可传递获得 :rage-jni 类型。
    api(project(":rage-jni"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
}
