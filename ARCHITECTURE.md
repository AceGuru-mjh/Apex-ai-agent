# Apex AI Agent — 架构设计 v4.0

> 面向**复杂功能 + 多 APK 拆分**的分层架构。Rage 模块采用 **Kotlin + C++ (JNI/NDK)** 三层方案。
> 本文档是 ApexArch 设计理念 + 实际落地实现的权威参考。

---

## 1. 设计哲学

### 1.1 从 ApexArch 继承的设计原则

| 原则 | 说明 |
|------|------|
| **分层隔离** | 5 层架构,上层依赖下层,永不反向;同层通过总线通信 |
| **插件化一切** | 所有功能(聊天/工具/记忆/工作流/狂暴)都是插件,可热插拔 |
| **响应式数据流** | 单向数据流 (UDF) + StateFlow/SharedFlow |
| **协程优先** | 所有异步用 Kotlin Coroutines + Flow |
| **Compose 单 UI 框架** | 全部用 Jetpack Compose + M3 |
| **高拓展性** | 新增功能不改核心,只加插件 |
| **可测试** | 每层都可独立测试(纯 Kotlin/纯 C++ 各自可单测) |

### 1.2 对 ApexArch "混乱"问题的修正

| 问题 | 修正方案 |
|------|---------|
| 业务逻辑与任务编排混在同一层 | **严格分层**:Domain 层只放业务规则;Rage/Workflow 独立为 **Compute 层**(C++ 核心) |
| 任务处理方式不统一 | **统一任务模型**:所有任务都是 `Task → StateMachine → Result`,通过 Flow 暴露事件 |
| Agent 编排逻辑散落 Kotlin 各处 | **C++ 核心层统一编排**:Planner/Searcher/Executor/Critic 状态机在 C++ 实现 |
| 缺乏并发控制 | **C++ ParallelScheduler**:线程池 + 资源守卫防 OOM |
| 没有清晰模块边界 | **多 APK 归属规则**:`ModuleOwnershipPlugin` 强制 |

---

## 2. 整体分层架构

```
┌─────────────────────────────────────────────────────────────────┐
│  Layer 5: UI Layer (Compose M3)                                 │  纯展示
├─────────────────────────────────────────────────────────────────┤
│  Layer 4: Feature Layer (ViewModel + Intent)                    │  MVI 状态管理
├─────────────────────────────────────────────────────────────────┤
│  Layer 3: Domain Layer (UseCase)                                │  业务规则,纯 Kotlin
├─────────────────────────────────────────────────────────────────┤
│  Layer 2: Service Layer (Engine + PluginRegistry + EventBus)    │  AI 引擎 + 工具 + 插件
├─────────────────────────────────────────────────────────────────┤
│  Layer 1: Core Layer (Kernel + ConfigStore + ServiceLocator)    │  内核
├─────────────────────────────────────────────────────────────────┤
│  Layer 0: Compute Layer (C++ Native Core)                       │  计算密集:Agent 编排/算法
├─────────────────────────────────────────────────────────────────┤
│  Bridge: JNI / NDK                                              │  Kotlin ↔ C++ 翻译
└─────────────────────────────────────────────────────────────────┘
```

### 2.1 依赖规则(严格执行)

- **上层依赖下层,永不反向**
- **同层通过 EventBus 通信,不直接调用**
- **Layer 0 (C++) 零 Android 依赖**(纯 C++17 + STL,可跨平台单测)
- **Bridge 层只做翻译,不做业务**

---

## 3. 模块清单(多 APK 架构)

```
Apex-ai-agent/
├── sdk/                          # 共享 SDK 层
│   ├── common-core/  common-ui/  process-bridge/  watchdog/  auth/  storage/
├── lib/                          # 功能 APK 私有库
│   ├── multi-agent/  workflow/  working-files/  engine/
│   ├── rage/                     # ★ 狂暴模式 Kotlin 薄壳(委托 native)
│   ├── market/  terminal/  voice/
├── rage-native/                  # ★ NEW: C++ 核心层(计算密集)
│   └── src/main/cpp/             # C++17: core/ agent/ skill/ cache/ jni/
├── rage-jni/                     # ★ NEW: Kotlin JNI 桥接层
├── core/  engine/  ai-terminal/  database/  domain/  file/  background/
├── app/                          # 主 APK(必装)
└── apk/                          # 功能 APK(未来拆分预留)
```

### 3.1 APK 归属规则(ModuleOwnershipPlugin 强制)

| 库 | 唯一允许的消费者 |
|---|---|
| `:lib:rage` + `:rage-native` + `:rage-jni` | `:apk:rage`, `:app`(迁移期) |
| `:lib:multi-agent` | `:apk:multi-agent`, `:app`(迁移期) |
| `:lib:workflow` | `:apk:workflow`, `:app`(迁移期) |
| ... 其他同理 | |

> **迁移期**:当前所有库编译进 `:app` 单 APK(InProcessRegistry 零延迟)。未来拆 APK 时只移依赖,代码零改动。

---

## 4. Rage 模块三层架构

### 4.1 三层职责

```
┌──────────────────────────────────────────────────────────────┐
│  Kotlin 应用层 (lib/rage)                                     │
│  RageEngine.kt          # 薄壳:委托给 RageNativeBridge       │
│  RageModels.kt          # 数据模型(与 C++ 共享 schema)       │
│  RagePresets.kt  RageSkillCatalog.kt  RageTaskStore.kt       │
├──────────────────────────────────────────────────────────────┤
│  JNI 桥接层 (rage-jni)                                        │
│  RageNative.kt          # external fun 声明                  │
│  RageNativeBridge.kt    # 协程↔C++线程 + 回调注册            │
│  NativeCallbacks.kt     # C++→Kotlin 回调(事件/进度)         │
├──────────────────────────────────────────────────────────────┤
│  C++ 核心层 (rage-native)                                     │
│  core/   TaskStateMachine + ParallelScheduler + Blackboard   │
│  agent/  AgentOrchestrator: Planner→Searcher→Executor→Critic │
│  skill/  SkillGraph(31 技能) + SkillMatcher                  │
│  cache/  AggressiveCache + Prefetcher                        │
│  jni/    rage_jni.cpp (JNI 入口 + 回调)                      │
└──────────────────────────────────────────────────────────────┘
```

### 4.2 数据流

```
RageEngine.startTask("实现 REST API")           [Kotlin]
    │  构造 NativeTask + 注册 NativeCallbacks
    ▼
RageNativeBridge                                 [JNI 桥接]
    │  切到 C++ 线程,调 nativeStartTask()
    ▼
AgentOrchestrator                                [C++ 核心]
    │  1. Planner: 分解任务(回调 Kotlin llmInvoker)
    │  2. Searcher: 检索(best-effort)
    │  3. Executor: 逐 subtask 实现(回调 llmInvoker)
    │  4. Critic: 质检,失败带 feedback 重试
    │  全程 ParallelScheduler 并行 + Blackboard 共享
    │  全程 postEvent 回调 Kotlin(进度/步骤)
    ▼
rage_jni.cpp → 序列化结果 → RageNativeBridge
    │  切回 Kotlin 协程
    ▼
RageEngine → 同步 TaskStore + emit RageEvent
```

### 4.3 JNI 回调机制

C++ 回调 Kotlin 两个能力:
1. **LLM 调用**:C++ 不能直接调 LLM,通过 JNI `CallObjectMethod` 回调 `RageLlmInvoker.invoke()`
2. **事件通知**:C++ `postEvent(type, data)` 回调 `NativeCallbacks.onEvent()`

实现参考 `ai-terminal/src/main/cpp/terminal_jni.cpp`:
- C++ 持有 `JavaVM* gJvm` + `jobject gCallbackObj` 全局引用
- 回调时 `AttachCurrentThread` 获取 `JNIEnv*`
- LLM 同步阻塞用 `MonitorEnter`/`MonitorExit`

---

## 5. 统一任务处理模型

### 5.1 统一状态机

```
PENDING ──start──► RUNNING ──success──► COMPLETED
                     │
                     ├─fail───► FAILED ──retry──► RUNNING
                     └─cancel──► CANCELLED
```

适用于:Rage 任务 / Workflow 节点 / Multi-Agent 会话 / 聊天工具调用

### 5.2 统一事件流

所有任务事件实现 `ApexEvent`,通过 `EventBus` 广播。

---

## 6. 依赖注入(双轨)

| 场景 | 方案 |
|------|------|
| `:app` 主 APK | Hilt (`@HiltAndroidApp` + `@Module`) |
| `:lib:*` 库 | 手写 ServiceLocator(避免 kapt 跨模块) |
| `:rage-jni` | 构造注入 |

---

## 7. 多 APK 通信契约(已实现)

```
ApexClient.rage.startTask(...)
    │
    ├─ 1. InProcessRegistry (同进程零延迟) ◄── 当前阶段
    ├─ 2. AIDL Binder (跨进程) ◄── 未来拆 APK
    └─ 3. LocalSocket (流式) ◄── PTY/文件监听
```

自愈:`IBinder.DeathRecipient` → `Watchdog.reportDeath` → 延迟重连(已实现)

---

## 8. 安全模型

命令执行纵深防御(3 道关卡):
1. `:app` SafeShellTool — `CommandRiskAssessor` 风险评估
2. `:engine` EngineService — RBAC `hasPermission(userId, "engine:shell:execute")`
3. `:engine` SystemTool — `CommandRiskAssessor` 再次拦截

---

## 9. 构建配置

- **NDK**:26+ | **C++**:C++17 | **ABI**:arm64-v8a + armeabi-v7a + x86_64
- **CMake**:3.22.1+
- 模块依赖:`:app` → `:rage-jni` → `:rage-native`(C++ .so)

---

## 10. 演进路线

| 阶段 | 状态 |
|------|------|
| 阶段 1: 单 APK 多模块 | ✅ 当前 |
| 阶段 2: APK 拆分 | 📋 未来 |
| 阶段 3: 动态下发 | 📋 未来 |
| 阶段 4: 插件市场 | 📋 未来 |
