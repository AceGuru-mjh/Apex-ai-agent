# Apex AI Agent

> **多模块单 APK 架构**的移动端 AI 自动化平台 — 26 个 Gradle 模块编译进**单一 APK**，
> 通过 `InProcessRegistry` 实现**零延迟跨模块调用**；`AIDL` + `LocalSocket` 设计为未来
> APK 拆分预留接口，业务代码无需改动即可演进到多 APK 套件。
> 对用户而言，整个套件就是一个 APK，没有任何隔阂。

[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-brightgreen?style=flat-square)]()
[![Architecture](https://img.shields.io/badge/Architecture-Multi--Module%20Single--APK-blue?style=flat-square)]()
[![Build](https://img.shields.io/badge/Build-Gradle%208-orange?style=flat-square)]()
[![License](https://img.shields.io/badge/License-Apache%202.0-blue?style=flat-square)](./LICENSE)

---

## 📐 项目定位

**Apex AI Agent** 是 [Apex-auto-agent](https://github.com/mengjinghao/Apex-auto-agent) 的**重构版**，
按职责拆分为 **26 个 Gradle 模块**，编译进**同一个 APK**（`applicationId = com.apex`）。

设计取舍说明：

- **现状（单 APK 多模块）**：所有 `:sdk:*` / `:lib:*` / `:engine` / `:database` / `:ai-terminal`
  等 26 个模块都被 `:app` 以 `implementation(project(...))` 拉进同一个 APK。
  跨模块调用通过 `ApexBridge` → `InProcessRegistry` 走 JVM 直调（零延迟），
  `EngineService` 仍跑在 `:engine_process` 私有进程里，`android:process` 隔离崩惯域。
- **未来（多 APK 拆分）**：`ApexBridge` 同时支持 AIDL Binder 兜底 + LocalSocket 流式通道；
  `BridgeConnection` / `BridgeRegistryService` / `Watchdog` / `ApexBridgeInitializer` 已就位。
  当某个模块（如 Rage / Workflow / Voice）需要独立成 APK 时，只需：
  1. 新建 `:apk:<name>` 模块，应用 `apex.suite.apk` convention plugin（自动注入
     sharedUserId + BridgeRegistryService + ApexBridgeInitializer + 依赖项）；
  2. 把对应 `:lib:*` 的归属从 `:app` 改到 `:apk:<name>`（`ModuleOwnershipPlugin` 校验）；
  3. 业务代码（`ApexClient.engine.executeShell(...)` 等）**完全不变**——
     `InProcessRegistry` 找不到实现时会自动降级到 AIDL Binder。

### 模块清单（26 个）

| 类别 | 模块 | 角色 |
|------|------|------|
| **应用入口** | `:app` | 主 APK（applicationId=`com.apex`）——UI、Chat、设置、业务编排、热更新 |
| **SDK 基础设施** | `:sdk:common-core` | 常量 / 模型 / `BridgeResult` / `BridgeError` / 日志 |
| | `:sdk:common-ui` | Compose 主题 / 通用组件 |
| | `:sdk:process-bridge` | **★ 通信核心**：`ApexBridge` / `ApexClient` / `InProcessRegistry` / `BridgeConnection` / AIDL |
| | `:sdk:watchdog` | 心跳 + `IBinder.DeathRecipient` + 自愈事件流 |
| | `:sdk:auth` | `PermissionBridge` —— RBAC 路由 |
| | `:sdk:storage` | `ApexDataStore` —— 跨模块共享偏好 |
| **功能库（每个 `:lib:*` 当前打包进 `:app`，未来可独立成 APK）** | `:lib:engine` | 引擎领域层（容器状态机 / 工具目录 / 编排 / Shizuku 策略） |
| | `:lib:multi-agent` | 多 Agent 协作（5 种模式 + 黑板 + 角色分工） |
| | `:lib:rage` | 狂暴模式核心（31 技能 / `RageAgentArchitect` / `RageEngine` / 任务存储） |
| | `:lib:workflow` | 工作流 DAG 编排（8 种节点 + 自定义 handler + 断点续跑契约） |
| | `:lib:market` | 市场核心（27 目录 / 缓存 / 收藏 / 安装状态机） |
| | `:lib:terminal` | 终端领域层（会话 / PTY 契约 / 历史 / 缓冲） |
| | `:lib:voice` | 语音核心（TTS / ASR 契约 / 会话 / 对话缓冲） |
| | `:lib:working-files` | 工作文件夹监听 + 代码预览 + 时间机器 |
| **核心层** | `:core:burst-kernel` | 狂暴模式微内核 |
| | `:core:burst-mode` | 狂暴模式专属库（高级 API / 配置 / 预设 / 监控） |
| | `:core:integration` | 集成市场（skills / mcp / 插件 / 模型平台） |
| | `:domain` | 领域模型（跨模块共享的实体与值对象） |
| **引擎服务层** | `:engine` | AIDL `IEngineService` + Shizuku + 无障碍 + 容器 + 5 工具 + RBAC 网关 |
| **插件层** | `:plugins:burst-base` | 狂暴技能插件抽象层 |
| | `:plugins:burst-builtin` | 内置狂暴技能实现 |
| **数据层** | `:database` | Room 数据库 + 7 DAO + RBAC seed（默认 admin 用户 + super_admin 角色 + 全权限） |
| | `:background` | WorkManager 后台任务 |
| | `:file` | 简易文件读写 / 列表（`:lib:working-files` 依赖） |
| **终端模块** | `:ai-terminal` | C++ PTY + `CommandRiskAssessor` / `DangerousCommandPatterns`（被 `SafeShellTool` 与 `:engine` 复用） |

📖 **架构文档**：
- [模块清单与依赖图](docs/architecture/) —— 26 模块在 `settings.gradle.kts` 中的实际声明
- [API 速查表](docs/architecture/API_REFERENCE.md)
- [`ApexBridge` 跨模块调用契约](sdk/process-bridge/src/main/java/com/apex/sdk/bridge/ApexBridge.kt)

---

## 🏗️ 核心架构

```
┌─────────────────────────────────────────────────────────────┐
│            Apex Suite（单 APK · 26 个 Gradle 模块）           │
│  ┌────┐ ┌──────┐ ┌──────┐ ┌──────────┐ ┌───────┐ ┌───────┐ │
│  │Main│ │Engine│ │ Rage │ │Multi-Agent│ │Workflow│ │Market │ │
│  │:app│ │:lib: │ │:lib: │ │  :lib:   │ │ :lib:  │ │:lib:  │ │
│  └─┬──┘ └──┬───┘ └──┬───┘ └────┬─────┘ └───┬───┘ └───┬───┘ │
│    │       │        │          │           │         │     │
│    └───────┴────────┴──────────┴───────────┴─────────┘     │
│                              │                               │
│                              ▼                               │
│              ┌─────────────────────────────────┐             │
│              │  InProcessRegistry（零延迟路径）  │             │
│              │  + AIDL Binder（多 APK 拆分兜底） │             │
│              │  + LocalSocket（流式传输）        │             │
│              └─────────────────────────────────┘             │
│                              │                               │
│              ┌─────────────────────────────────┐             │
│              │  Watchdog + 心跳 + DeathRecipient │             │
│              └─────────────────────────────────┘             │
└─────────────────────────────────────────────────────────────┘
            applicationId: com.apex
            EngineService 跑在 :engine_process 私有进程（崩溃隔离）
            未来拆 APK 时：sharedUserId="com.apex.agent.suite" + 同 keystore
```

**单 APK 多模块说明**：

- `:app` 通过 `implementation(project(...))` 把 15 个模块（5 个 `:sdk:*` + 8 个 `:lib:*` + `:engine` + `:database` + `:ai-terminal`）拉进编译类路径，最终全部 dex 进同一个 APK。
- `EngineService` 仍跑在 `:engine_process` 私有进程（AIDL Binder 跨进程），所以即使引擎崩溃也不会拖垮主进程；RBAC 在跨进程前已落地（`EngineService.executeCommand` → `DatabaseRepository.hasPermission(userId=1, "engine:shell:execute")`）。
- `ApexBridge` 调用栈：`ApexClient.engine.executeShell(cmd)` → `ApexBridge.invoke("engine/executeShell", ...)` → 先查 `InProcessRegistry`（同进程 JVM 直调，零延迟）；找不到时降级到 AIDL Binder（跨进程，毫秒级）；高频流式场景（PTY / 文件 watch）走 `LocalSocket`。
- `ModuleOwnershipPlugin` + `apex.suite.apk` convention plugin 已在 `build-logic` 就位，但目前**未应用到任何模块**——所有 `:lib:*` 直接由 `:app` 消费。当未来需要拆 APK 时，应用 convention plugin 即可自动注入 `sharedUserId` + `BridgeRegistryService` + `ApexBridgeInitializer`，业务代码完全不变。

---

## 🚀 快速开始

### 构建

```bash
# 构建单一 APK（包含全部 26 个模块）
./gradlew assembleDebug

# 构建产物
# app/build/outputs/apk/debug/app-debug.apk
```

### 安装

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

> 由于当前是**单 APK 多模块**架构，无需安装多个 APK。所有功能（Chat / Engine / Rage / Workflow / Market / Terminal / Voice）都已打包进同一个 `app-debug.apk`。
> 未来若拆分为多 APK，则需用**同一签名**安装全部 APK，`sharedUserId` 才能生效。

### 签名配置

在 `local.properties` 中：

```properties
RELEASE_STORE_FILE=/path/to/apex.keystore
RELEASE_STORE_PASSWORD=...
RELEASE_KEY_ALIAS=apex
RELEASE_KEY_PASSWORD=...
```

---

## 🧩 跨模块调用示例

```kotlin
// :app 进程内调用 Engine 能力（InProcessRegistry 命中 → 零延迟 JVM 直调；
// 若未来 Engine 拆为独立 APK，则自动降级为 AIDL Binder，业务代码不变）
val result = ApexBridge.invoke(
    method = "engine/executeShell",
    argsJson = """{"cmd":"ls /sdcard"}"""
)
println(result.getOrNull())

// 等价的强类型 API
val result2 = ApexClient.engine.executeShell("ls /sdcard")
when (result2) {
    is BridgeResult.Success -> println(result2.value)
    is BridgeResult.Failure -> println(result2.error.message)
}

// 终端流式通道（PTY 通过 LocalSocket 传输，独立进程 :terminal_process 中运行）
val sessionId = ApexBridge.invoke(
    method = "terminal/createNormalSession",
    argsJson = """{"workingDir":"/sdcard"}"""
).getOrNull()

val channelName = ApexBridge.openStream("terminal", "pty.$sessionId").getOrNull()!!
val client = LocalStreamClient(channelName).apply { connect() }
client.send("ls\n".toByteArray())

client.receiveFlow().collect { chunk ->
    print(String(chunk))  // 实时渲染终端输出
}
```

---

## 📦 模块清单（settings.gradle.kts 实际声明）

```
Apex-ai-agent/
├── build-logic/                  # Convention Plugins（apex.suite.apk 等）
├── gradle/libs.versions.toml     # Version Catalog（Hilt / Room / OkHttp / Compose BOM）
├── settings.gradle.kts           # 26 模块声明
│
├── app/                          # ★ 主 APK（applicationId=com.apex）
│
├── sdk/                          # 共享 SDK 层（被所有消费者复用）
│   ├── common-core/              # 常量 / 模型 / 错误 / 日志
│   ├── common-ui/                # Compose 主题 / 通用组件
│   ├── process-bridge/           # ★ 零延迟通信核心（ApexBridge / ApexClient / InProcessRegistry）
│   ├── watchdog/                 # 心跳 + IBinder.DeathRecipient + 自愈事件流
│   ├── auth/                     # PermissionBridge（RBAC 路由）
│   └── storage/                  # ApexDataStore
│
├── lib/                          # 功能库（当前 implementation 进 :app，未来可拆 :apk:* ）
│   ├── multi-agent/              # 多 Agent 协作引擎
│   ├── workflow/                 # 工作流 DAG 编排 + LlmInvoker 注入点
│   ├── working-files/            # 文件监听 + 代码预览
│   ├── engine/                   # 引擎领域层（容器状态机/工具目录/编排）
│   ├── rage/                     # 狂暴模式核心（RageAgentArchitect + RageLlmInvoker 注入点）
│   ├── market/                   # 市场核心（27 目录/缓存/安装状态机）
│   ├── terminal/                 # 终端领域层（PTY 契约/历史/缓冲）
│   └── voice/                    # 语音核心（TTS/ASR 契约/会话）
│
├── core/                         # 原有核心层
│   ├── burst-kernel/             # 狂暴模式微内核
│   ├── burst-mode/               # 狂暴模式专属库
│   └── integration/              # 集成市场（skills / mcp / 插件 / 模型）
├── engine/                       # 引擎服务层（AIDL IEngineService + Shizuku + 无障碍 + RBAC）
├── plugins/                      # 狂暴技能插件（burst-base + burst-builtin）
├── ai-terminal/                  # AI 终端模块（C++ PTY + CommandRiskAssessor）
├── database/                     # Room + 7 DAO + RBAC seed
├── background/                   # WorkManager 后台任务
├── file/                         # 简易文件读写
└── domain/                       # 领域模型
```

> ✅ 26 个模块全部在 `settings.gradle.kts` 中 `include()`，全部参与 Gradle 配置。
> ❌ 不存在 `:apk:*` 目录——多 APK 拆分留待未来；当前所有功能编进 `:app` 一个 APK。

---

## 🛠️ 技术栈

| 层级 | 技术 |
|------|------|
| DI | **Hilt**（`@HiltAndroidApp` / `@AndroidEntryPoint` / `@HiltViewModel` + `hiltViewModel()`，`EngineModule` + `DatabaseModule` + `InvokerModule` 三个 `@Module`） |
| 异步 | Kotlin Coroutines + Flow |
| 持久化 | Room + DataStore + ObjectBox |
| 网络 | OkHttp + Retrofit |
| 后台 | WorkManager |
| 序列化 | kotlinx.serialization + org.json |
| 高性能 | C++ (JNI / NDK) —— `:ai-terminal` 的 PTY 实现 |
| **跨模块通信** | **InProcessRegistry（JVM 直调，零延迟） + AIDL Binder（多 APK 兜底） + LocalSocket（流式）** |
| **自愈** | **Watchdog + 心跳 + `IBinder.DeathRecipient`（被动死亡通知 + 主动心跳双探测）** |
| 构建 | Gradle 8 + Version Catalog + Convention Plugins |

---

## 📦 环境要求

| 工具 | 版本 |
|------|------|
| JDK | 17 |
| Android SDK | compileSdk 35 |
| Min SDK | 26 (Android 8.0) |
| NDK | 26+（编译 `:ai-terminal` 的 C++ PTY 部分） |
| Gradle | 8.5+ |

---

## 📚 文档

- [API 文档](docs/API_Documentation.md)
- [集成指南](docs/INTEGRATION_GUIDE.md)
- [模块搭建指南](docs/MODULE_SETUP_GUIDE.md)
- [技能系统](docs/SKILL_SYSTEM.md)
- [模型切换指南](docs/MODEL_SWITCHER_GUIDE.md)

> ⚠️ `docs/architecture/MULTI_APK_ARCHITECTURE.md` 等"多 APK"主题文档为**未来架构愿景**，
> 当前实际架构以本 README 的"多模块单 APK"描述为准。

---

## 🔄 与 Apex-auto-agent 的差异

| 维度 | Apex-auto-agent | Apex-ai-agent |
|------|-----------------|---------------|
| APK 输出 | 1 个（单体） | **1 个（多模块编译进单 APK）** |
| 模块数 | 14 | **26** |
| 跨模块调用 | 进程内方法调用 | **InProcessRegistry JVM 直调（零延迟） + AIDL Binder 兜底 + LocalSocket 流式** |
| DI | 手写单例 | **Hilt**（`@HiltAndroidApp` + `@HiltViewModel` + 3 个 `@Module`） |
| 自愈机制 | 无 | **Watchdog + 心跳 + `IBinder.DeathRecipient`** |
| 权限共享 | 单 APK 内 | `PermissionBridge` 路由 + Room RBAC（默认 admin + super_admin + 全权限） |
| 命令安全 | 直接 exec | **`CommandRiskAssessor` 双层网关**（`:app` `SafeShellTool` + `:engine` `SystemTool`/`ShizukuManager`） |
| 工具执行循环 | 单轮 | **多轮工具调用循环**（`ChatViewModel` 消费 `StreamEvent.ToolCallEvent`，最多 5 轮） |
| 工作流引擎 | 无 | **`WorkflowExecutor`**（8 种节点 + LlmInvoker 注入 + 自定义 handler） |
| 狂暴模式 | 无 | **`RageAgentArchitect`**（Planner → Searcher → Executor → Critic，RageLlmInvoker 注入） |
| 多 APK 拆分预留 | N/A | **`apex.suite.apk` convention plugin + `ModuleOwnershipPlugin` 就位**，业务代码不变即可演进 |

---

## 🔥 热更新（Hot Update）

主 APK 内置**自更新模块**，从 GitHub Releases 拉取新版本 APK 并调起系统安装器，
无需第三方应用商店。同时内置多套**免费 GitHub 加速镜像**，国内用户可在设置中按需启用或自行添加。

### 工作流程

```
App 启动 → Application.initializeHotUpdate()
              │
              ▼
   镜像源加载（ApexDataStore）
              │
              ▼
   判断是否到达检查间隔（默认 6h）
              │
              ▼
   GET https://api.github.com/repos/{owner}/{repo}/releases/latest
              │
              ▼
   版本号比较（语义化版本）→ 有新版？
              │ 是
              ▼
   StateFlow 刷新为 UpdateAvailable（设置页 / 主界面可观察）
              │ 用户点击"立即更新"
              ▼
   按镜像顺序下载 APK（首个成功即用）
   ├─ GitHub 直连
   ├─ ghproxy.com
   ├─ mirror.ghproxy.com
   ├─ ghps.cc
   ├─ github.moeyy.xyz
   ├─ gh-proxy.com
   ├─ kkgithub.com
   └─ 用户自定义镜像…
              │
              ▼
   完整性校验（Content-Length）
              │
              ▼
   FileProvider 暴露 APK → ACTION_VIEW → 系统安装界面
```

### 模块结构

```
app/src/main/java/com/apex/agent/update/
├── UpdateModels.kt              # 数据模型（UpdateRelease / CheckResult / MirrorSource）
├── UpdateSettings.kt            # 偏好设置 + 语义化版本比较工具
├── MirrorSourceRegistry.kt      # 镜像源注册表（内置 + 自定义）
├── HotUpdateManager.kt          # 核心管理器（检查 / 下载 / 校验 / 安装）
└── ui/
    ├── UpdateDialog.kt          # 更新对话框（版本信息 / 更新日志 / 下载进度）
    └── UpdateSettingsSection.kt # 镜像管理 + 偏好设置 Compose UI
```

### 内置免费镜像

| id | 名称 | 模板 | 特点 |
|----|------|------|------|
| `direct` | GitHub 直连 | `{url}` | 原始地址，海外最快 |
| `ghproxy` | ghproxy.com | `https://ghproxy.com/{url}` | 老牌加速，国内可用 |
| `ghproxy-net` | mirror.ghproxy.com | `https://mirror.ghproxy.com/{url}` | ghproxy 备用节点 |
| `ghps` | ghps.cc | `https://ghps.cc/{url}` | Free CDN mirror |
| `moeyy` | github.moeyy.xyz | `https://github.moeyy.xyz/{url}` | moeyy 加速 |
| `gh-proxy` | gh-proxy.com | `https://gh-proxy.com/{url}` | 公益代理 |
| `kkgithub` | kkgithub.com | 域名替换型 | 替换 github.com → kkgithub.com |
| `gcore` | gh.api.99988866.xyz | `https://gh.api.99988866.xyz/{url}` | 备用节点 |

### 用户操作

1. **设置 → 软件更新 → 检查更新** — 立即检查 GitHub Releases（无视检查间隔）
2. **设置 → 软件更新 → 镜像源管理** — 启用/禁用镜像、添加自定义镜像、测试镜像连通性
3. **设置 → 软件更新 → 镜像源管理 → GitHub 仓库** — 切换检查的仓库（默认 `mengjinghao/Apex-ai-agent`）
4. **设置 → 软件更新 → 镜像源管理 → 启动时自动检查 / 包含预发布 / 仅 Wi-Fi 下载**

### 添加自定义镜像

镜像 URL 模板中使用 `{url}` 作为 GitHub 原始下载地址占位符，例如：

```
https://your-mirror.example.com/{url}
```

下载时，模块会按列表顺序尝试每个**已启用**的镜像，首个成功即用，失败自动回退下一个。
镜像列表通过 `ApexDataStore`（跨 APK 共享）持久化。

### 配置 Release

1. 在 GitHub 仓库 → Releases → Draft a new release
2. Tag 命名建议 `v1.2.3`（语义化版本）
3. 上传 `.apk` 文件作为 Release Asset
4. Release body 作为更新日志，支持 Markdown，对话框会按行渲染 `-` / `*` 开头的列表项

### API 限制与降级

GitHub 未认证 API 限流为 **60 次/小时/IP**。模块已内置：
- 6 小时最小检查间隔（可配置 1–168 小时）
- 404 → 视为"无 Release"，不报错
- 403 → 记录限流日志并降级为"检查失败"
- 所有镜像均失败 → 显示失败状态，用户可手动重试

### 高级特性

| 特性 | 说明 |
|------|------|
| **网络预检** | 检查前先 `NetworkUtils.isNetworkAvailable`，无网络直接返回友好错误，不发请求 |
| **WiFi-only 真实生效** | 下载前检查 `isWifiConnected`，移动网络下直接拒绝并提示用户 |
| **断点续传** | 下载中断后再次尝试同镜像时发送 `Range: bytes=<existing>-`，服务器返回 206 则追加写入；返回 200 则覆盖重下 |
| **SHA-256 校验** | 自动从 release notes 解析 `SHA-256: <hex>` 或 `<apk-name>: <hex>`，校验失败删除文件并报错 |
| **错误分类** | `UpdateError` 区分 NoNetwork / WifiOnly / RateLimited / NoRelease / NetworkError / ParseError / AllMirrorsFailed / IntegrityError / Cancelled / Unknown |
| **系统通知** | 三个通道：`apex.update.available`（发现新版本）、`apex.update.progress`（下载进度，常驻通知栏）、`apex.update.result`（完成/失败） |
| **首次启动延迟** | 首次启动延迟 30 秒检查，避免与冷启动 IO 抢资源 |
| **镜像智能排序** | 上次下载成功的镜像自动前置，加快下一次下载 |
| **取消下载** | `SupervisorJob` + `AtomicReference` 跟踪下载 Job，用户可随时取消 |
| **ProGuard 规则** | 已添加 `@Serializable` 数据类与 sealed class 子类的 keep 规则，release 模式不会崩溃 |
| **单元测试** | `VersionComparatorTest` 覆盖版本比较、SHA-256 解析、镜像 URL 包装等核心逻辑 |

### 模块结构（含优化后）

```
app/src/main/java/com/apex/agent/update/
├── UpdateModels.kt              # 数据模型
├── UpdateError.kt               # 错误分类（10 种）
├── UpdateSettings.kt            # 偏好设置 + 版本比较 + SHA-256 解析
├── MirrorSourceRegistry.kt      # 镜像源注册表
├── HotUpdateManager.kt          # 核心管理器（网络预检/WiFi/续传/SHA256/通知）
├── UpdateNotifier.kt            # 系统通知（3 通道）
└── ui/
    ├── UpdateDialog.kt          # 更新对话框（含 SHA-256 徽章）
    └── UpdateSettingsSection.kt # 镜像管理 + 偏好设置 UI

app/src/test/java/com/apex/agent/update/
└── VersionComparatorTest.kt     # 版本比较 + formatBytes + extractSha256 单元测试
```

---

## 📄 License

Apache License 2.0 — 见 [LICENSE](./LICENSE)
