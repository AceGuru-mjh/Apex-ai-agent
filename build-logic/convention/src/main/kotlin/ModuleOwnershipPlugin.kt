import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.GradleException

/**
 * 模块归属校验插件 —— 编译时检查 lib:* / rage-native / rage-jni 模块只被授权的消费者引用。
 *
 * **解决的问题**：
 *   `:lib:working-files` 应该只打包进 `:apk:working-files`，
 *   但 Gradle 不会阻止其他模块（如 `:app`）误加 `implementation(project(":lib:working-files"))`。
 *   本插件在 preBuild 阶段检查依赖图，发现违规即让构建失败。
 *
 * **归属规则**（见 docs/architecture/MODULE_OWNERSHIP.md）：
 *   :lib:multi-agent   → 只允许 :apk:multi-agent 引用（迁移期 :app 也允许）
 *   :lib:workflow      → 只允许 :apk:workflow 引用（迁移期 :app 也允许）
 *   :lib:working-files → 只允许 :apk:working-files 引用（迁移期 :app 也允许）
 *   :lib:engine        → 只允许 :apk:engine 引用（迁移期 :app 也允许）
 *   :lib:rage          → 只允许 :apk:rage 引用（迁移期 :app 也允许）
 *   :lib:market        → 只允许 :apk:market 引用（迁移期 :app 也允许）
 *   :lib:terminal      → 只允许 :apk:terminal 引用（迁移期 :app 也允许）
 *   :lib:voice         → 只允许 :apk:voice 引用（迁移期 :app 也允许）
 *   :rage-native       → 只允许 :rage-jni 引用（C++ 核心 .so，只暴露给 JNI 桥）
 *   :rage-jni          → 只允许 :lib:rage + :app 引用（迁移期；拆 APK 后只剩 :lib:rage）
 *   :sdk:*             → 所有 APK 都可引用（共享 SDK）
 *   :core:* / :engine / :plugins:* / :ai-terminal / :domain / :database / :background / :file
 *                     → 按需引用（不限制）
 *
 * **使用方式**：
 *   build.gradle.kts 顶部：
 *     plugins { id("apex.module.ownership") }
 *
 *   或在 settings.gradle.kts 中对所有 :app 和 :apk:* 模块自动应用。
 *
 * **ARCH-3 扩展**：插件 apply 范围从 `:app` + `:apk:*` 扩展到也覆盖 `:lib:*` +
 * `:rage-jni`，使得 :rage-native → :rage-jni 的归属规则可被强制校验
 * （:rage-jni 是 :rage-native 的唯一合法消费者）。
 */
class ModuleOwnershipPlugin : Plugin<Project> {

    /** lib / native / jni 模块 → 允许引用它的 APK/模块 白名单。
     *
     * 当前阶段为单 APK 多模块架构——所有 :lib:* 都允许被 :app 直接 implementation 引用。
     * 保留 :apk:* 条目是为未来拆 APK 时无需再改规则。
     * 若未来 :apk:* 模块落地，可移除此处 :app 条目以恢复严格隔离。
     *
     * ARCH-3 新增 :rage-native / :rage-jni 规则：
     *   - :rage-native 是 C++17 核心 .so，只应被 :rage-jni（JNI 桥）消费
     *   - :rage-jni 在迁移期可被 :lib:rage + :app 直接消费；拆 APK 后应只剩 :lib:rage
     */
    private val ownershipRules: Map<String, Set<String>> = mapOf(
        ":lib:multi-agent" to setOf(":apk:multi-agent", ":app"),
        ":lib:workflow" to setOf(":apk:workflow", ":app"),
        ":lib:working-files" to setOf(":apk:working-files", ":app"),
        ":lib:engine" to setOf(":apk:engine", ":app"),
        ":lib:rage" to setOf(":apk:rage", ":app"),
        ":lib:market" to setOf(":apk:market", ":app"),
        ":lib:terminal" to setOf(":apk:terminal", ":app"),
        ":lib:voice" to setOf(":apk:voice", ":app"),
        // ── ARCH-3: Rage 三层架构归属规则 ──────────────────────────
        ":rage-native" to setOf(":rage-jni"),
        ":rage-jni" to setOf(":lib:rage", ":app")
    )

    override fun apply(target: Project) {
        val path = target.path
        val isAppModule = path == ":app"
        val isApkModule = path.startsWith(":apk:")
        // ARCH-3: 扩展 apply 范围 —— lib:* 与 :rage-jni 也需校验自身依赖,
        // 以强制 :rage-native → :rage-jni 的单向归属
        val isLibModule = path.startsWith(":lib:")
        val isJniBridgeModule = path == ":rage-jni"
        if (!isAppModule && !isApkModule && !isLibModule && !isJniBridgeModule) {
            return
        }

        // 注册检查任务，在 preBuild 之前执行
        val checkTask = target.tasks.register("checkModuleOwnership") {
            doLast {
                val violations = mutableListOf<String>()

                // 遍历所有 configurations 的项目依赖
                target.configurations.configureEach {
                    // 只检查可以解析的 configuration
                    if (!isCanBeResolved) return@configureEach

                    dependencies.forEach { dep ->
                        if (dep is ProjectDependency) {
                            val depPath = dep.dependencyProject.path
                            val allowedOwners = ownershipRules[depPath]
                            if (allowedOwners != null && path !in allowedOwners) {
                                violations.add(
                                    "  ❌ $path 通过 ${this.name} 引用了 $depPath\n" +
                                    "     但 $depPath 只允许以下模块引用：${allowedOwners.joinToString(", ")}\n" +
                                    "     请改用 ApexClient 跨 APK 调用，或检查 docs/architecture/MODULE_OWNERSHIP.md"
                                )
                            }
                        }
                    }
                }

                if (violations.isNotEmpty()) {
                    val msg = buildString {
                        appendLine("========================================")
                        appendLine("❌ 模块归属校验失败")
                        appendLine("========================================")
                        appendLine("以下依赖违反了模块归属规则：")
                        appendLine()
                        violations.forEach { appendLine(it); appendLine() }
                        appendLine("如需在主 APK 中使用这些功能，请通过 ApexClient 跨 APK 调用：")
                        appendLine("  - 多 Agent：ApexClient.multiAgent.*")
                        appendLine("  - 工作流：ApexClient.workflow.*")
                        appendLine("  - 工作文件：ApexClient.workingFiles.*")
                        appendLine("  - 引擎：ApexClient.engine.*")
                        appendLine("  - 狂暴模式：ApexClient.rage.*  (Kotlin 薄壳 + :rage-jni + :rage-native C++ 核心)")
                        appendLine("  - 市场：ApexClient.market.*")
                        appendLine("  - 终端：ApexClient.terminal.*")
                        appendLine("  - 语音：ApexClient.voice.*")
                        appendLine("========================================")
                    }
                    throw GradleException(msg)
                }
            }
        }

        // 让 preBuild 依赖检查任务
        target.tasks.matching { it.name == "preBuild" }.configureEach {
            dependsOn(checkTask)
        }
    }
}
