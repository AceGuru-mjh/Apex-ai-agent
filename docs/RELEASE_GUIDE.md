# APK 构建与发布指南

## 概述

Apex AI Agent 采用 GitHub Actions 自动构建 **9 个 APK** 并发布到 GitHub Releases。

| APK | 模块 | 包名 | 说明 |
|-----|------|------|------|
| Main App | `:app` | `com.apex.agent` | 主 APK（必须先装） |
| Engine | `:apk:engine` | `com.apex.apk.engine` | Shell + 工具 + 无障碍 |
| Rage Mode | `:apk:rage` | `com.apex.apk.rage` | 狂暴模式 + 31 技能 |
| Multi-Agent | `:apk:multi-agent` | `com.apex.apk.multiagent` | 多 Agent 协作 |
| Workflow | `:apk:workflow` | `com.apex.apk.workflow` | 工作流 DAG |
| Market | `:apk:market` | `com.apex.apk.market` | 27 个市场 |
| Terminal | `:apk:terminal` | `com.apex.apk.terminal` | 三块终端 + PTY |
| Working Files | `:apk:working-files` | `com.apex.apk.workingfiles` | 工作文件区 |
| Voice | `:apk:voice` | `com.apex.apk.voice` | TTS + ASR |

## 快速开始

### 1. 生成签名密钥

```bash
./scripts/generate-keystore.sh
```

按提示输入密码和签名信息，脚本会生成 `apex-release.jks` 并显示如何配置 GitHub Secrets。

### 2. 配置 GitHub Secrets

在仓库 **Settings → Secrets and variables → Actions** 中添加 4 个 secret：

| Secret Name | 说明 |
|-------------|------|
| `SIGNING_KEYSTORE` | base64 编码的 `.jks` 文件（`base64 -w0 apex-release.jks`） |
| `SIGNING_STORE_PASSWORD` | keystore 密码 |
| `SIGNING_KEY_ALIAS` | key alias（默认 `apex`） |
| `SIGNING_KEY_PASSWORD` | key 密码 |

> **未配置签名时**：流水线自动降级为 debug 签名（仅用于测试，无法覆盖安装）。

### 3. 触发构建

#### 方式 A：推送 Tag（自动发布 Release）

```bash
git tag v1.0.0
git push origin v1.0.0
```

推送 `v*` 格式的 tag 后，GitHub Actions 自动：
1. 构建 9 个 release APK（带签名）
2. 创建 GitHub Release
3. 上传所有 APK 作为 Release Assets
4. 自动生成 Changelog

#### 方式 B：手动触发

1. 打开仓库 **Actions** 页面
2. 选择 **Build & Release APK** workflow
3. 点击 **Run workflow**
4. 选择构建类型：`release` / `debug` / `both`

手动触发的 Release 标记为 `prerelease`。

## 流水线详情

### 触发条件

| 事件 | 条件 | 行为 |
|------|------|------|
| `push tag` | `v*` 格式 | 构建 release + 创建正式 Release |
| `workflow_dispatch` | 手动 | 按选择的类型构建 + 创建 prerelease |

### 构建环境

- **OS**: Ubuntu Latest
- **JDK**: Temurin 17
- **Android SDK**: API 35 + Build Tools 34.0.0
- **NDK**: 26.3.11579264
- **CMake**: 3.22.1
- **ABI**: arm64-v8a

### 构建命令

```bash
# Release（启用签名 + 混淆）
./gradlew assembleRelease

# Debug（快速构建，无混淆）
./gradlew assembleDebug
```

### 产物命名

| 模块 | 产物文件名 |
|------|-----------|
| `:app` | `main-app-release.apk` |
| `:apk:engine` | `engine-release.apk` |
| `:apk:rage` | `rage-release.apk` |
| `:apk:multi-agent` | `multi-agent-release.apk` |
| `:apk:workflow` | `workflow-release.apk` |
| `:apk:market` | `market-release.apk` |
| `:apk:terminal` | `terminal-release.apk` |
| `:apk:working-files` | `working-files-release.apk` |
| `:apk:voice` | `voice-release.apk` |

## 安装说明

所有 APK **必须用同一签名**安装，否则 `android:process` 共享无法生效。

```bash
# 安装主 APK（必须先装）
adb install main-app-release.apk

# 安装其他 APK（按需）
adb install engine-release.apk
adb install terminal-release.apk
# ...
```

## 本地构建

### 前置要求

- JDK 17
- Android SDK（API 35 + Build Tools 34.0.0）
- NDK 26.3.11579264
- CMake 3.22.1

### 步骤

1. 创建 `local.properties`：

```properties
sdk.dir=/path/to/android/sdk
RELEASE_STORE_FILE=/path/to/apex-release.jks
RELEASE_STORE_PASSWORD=your_store_password
RELEASE_KEY_ALIAS=apex
RELEASE_KEY_PASSWORD=your_key_password
```

2. 构建所有 APK：

```bash
./gradlew assembleRelease
```

3. 产物位于各模块的 `build/outputs/apk/release/` 目录。

## 故障排查

### 构建失败：NDK not found

确保 `local.properties` 中 `sdk.dir` 正确，且安装了 NDK：

```bash
sdkmanager "ndk;26.3.11579264" "cmake;3.22.1"
```

### 签名失败：keystore not found

检查 `SIGNING_KEYSTORE` secret 是否正确配置，base64 解码后文件是否有效：

```bash
echo "$SIGNING_KEYSTORE" | base64 -d | keytool -list -keystore /dev/stdin
```

### APK 互斥：签名不一致

所有 APK 必须用**同一个 keystore** 签名。如果更换 keystore，需先卸载所有旧 APK：

```bash
adb uninstall com.apex.agent
adb uninstall com.apex.apk.engine
# ... 其他 APK
```
