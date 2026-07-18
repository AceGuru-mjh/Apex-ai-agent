plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.parcelize)
}

android {
    namespace = "com.ai.assistance.apex.engine"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        targetSdk = 35
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        aidl = true
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
            assets.srcDirs("src/main/assets")
            aidl.srcDirs("src/main/aidl")
        }
    }

    packaging {
        jniLibs.useLegacyPackaging = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    implementation("com.squareup.okhttp3:okhttp:4.11.0")

    // :database — 提供 AppDatabase / DatabaseRepository，用于 EngineService.executeCommand
    // 的 RBAC 权限校验（hasPermission(userId, "engine:shell:execute")）
    implementation(project(":database"))
    // 协程 — RBAC 校验调用 suspend fun DatabaseRepository.hasPermission() + CommandRiskAssessor.assessRisk()
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    // :ai-terminal — 提供 CommandRiskAssessor + DangerousCommandPatterns + RiskLevel
    // 用于在 SystemTool.executeShellCommand / ShizukuManager.executeCommand 执行前做风险评估
    // （CRITICAL/HIGH 直接拒绝，MEDIUM 仅记日志后放行，LOW/无匹配放行）
    // 注意：:ai-terminal 不依赖 :engine，因此不会形成循环依赖。
    implementation(project(":ai-terminal"))

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.3")
}
