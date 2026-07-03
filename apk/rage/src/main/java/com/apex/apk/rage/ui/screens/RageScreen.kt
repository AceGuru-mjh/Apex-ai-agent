package com.apex.apk.rage.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.apex.apk.rage.ui.theme.RageColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 狂暴模式主界面 — 4 Agent 核心架构 + 动态扩容。
 *
 * 设计理念（来自用户架构文档）：
 * - 默认 4 个核心 Agent：Planner（架构师）/ Searcher（领航员）/ Executor（码农）/ Critic（质检员）
 * - 任务执行中动态扩容（spawn_agent）
 * - 黑板架构（全局共享状态）
 * - 强制 JSON 结构化通信
 * - 全局容错（连续 3 次失败 → 终止 → Planner 重新规划）
 * - 代码库 RAG + Git 状态机 + 沙盒执行 + Diff 格式
 * - GitHub 搜索 + 结构化搜索 + 文档抓取
 *
 * UI 结构：
 * 1. TopAppBar — 汉堡菜单 + ⚡标题 + 预设 + 设置
 * 2. Agent 拓扑视图 — 4 核心节点 + 动态扩容节点
 * 3. 执行流水线 — 思考/检索/执行/审查/扩容（流式卡片）
 * 4. 黑板状态 — 全局共享状态
 * 5. 高级输入栏 — 任务输入 + Agent 开关 + 扩容策略
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RageScreen(onMenuClick: () -> Unit = {}) {
    var taskInput by remember { mutableStateOf("") }
    var selectedPreset by remember { mutableStateOf(RagePreset.BALANCED) }
    var isExecuting by remember { mutableStateOf(false) }
    val pipelineSteps = remember { mutableStateListOf<PipelineStep>() }
    val dynamicAgents = remember { mutableStateListOf<DynamicAgent>() }
    val blackboard = remember { mutableStateMapOf<String, String>() }
    val scope = rememberCoroutineScope()

    // Agent 开关
    var plannerEnabled by remember { mutableStateOf(true) }
    var searcherEnabled by remember { mutableStateOf(true) }
    var executorEnabled by remember { mutableStateOf(true) }
    var criticEnabled by remember { mutableStateOf(true) }
    // 扩容策略
    var autoExpand by remember { mutableStateOf(true) }
    var gitBranching by remember { mutableStateOf(true) }
    var sandboxExec by remember { mutableStateOf(true) }
    var githubSearch by remember { mutableStateOf(false) }
    var codeRag by remember { mutableStateOf(true) }

    // 指标
    var totalTasks by remember { mutableStateOf(0L) }
    var successRate by remember { mutableStateOf(0f) }
    var agentCount by remember { mutableStateOf(4) }
    var tokensUsed by remember { mutableStateOf(0L) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onMenuClick) { Icon(Icons.Default.Menu, "菜单") } },
                title = { Text("⚡ 狂暴模式", fontWeight = FontWeight.Bold) },
                actions = {
                    PresetSelector(selectedPreset) { selectedPreset = it }
                    IconButton(onClick = {}) { Icon(Icons.Default.Settings, "设置") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Agent 拓扑视图
            AgentTopology(
                plannerEnabled = plannerEnabled,
                searcherEnabled = searcherEnabled,
                executorEnabled = executorEnabled,
                criticEnabled = criticEnabled,
                dynamicAgents = dynamicAgents,
                isExecuting = isExecuting
            )

            // 执行流水线
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (pipelineSteps.isEmpty() && !isExecuting) {
                    item { EmptyState() }
                }
                items(pipelineSteps) { step -> PipelineStepCard(step) }
                if (isExecuting) item { TypingIndicator() }
            }

            // 黑板状态
            if (blackboard.isNotEmpty()) {
                BlackboardBar(blackboard.toMap())
            }

            // 指标栏
            MetricsBar(totalTasks, successRate, agentCount, tokensUsed)

            // 高级输入栏
            AdvancedInputBar(
                text = taskInput,
                onTextChange = { taskInput = it },
                isExecuting = isExecuting,
                plannerEnabled = plannerEnabled, onPlannerToggle = { plannerEnabled = it },
                searcherEnabled = searcherEnabled, onSearcherToggle = { searcherEnabled = it },
                executorEnabled = executorEnabled, onExecutorToggle = { executorEnabled = it },
                criticEnabled = criticEnabled, onCriticToggle = { criticEnabled = it },
                autoExpand = autoExpand, onAutoExpandToggle = { autoExpand = it },
                gitBranching = gitBranching, onGitBranchingToggle = { gitBranching = it },
                sandboxExec = sandboxExec, onSandboxExecToggle = { sandboxExec = it },
                githubSearch = githubSearch, onGithubSearchToggle = { githubSearch = it },
                codeRag = codeRag, onCodeRagToggle = { codeRag = it },
                onExecute = {
                    if (taskInput.isNotBlank() && !isExecuting) {
                        val task = taskInput; taskInput = ""
                        scope.launch {
                            isExecuting = true
                            agentCount = listOf(plannerEnabled, searcherEnabled, executorEnabled, criticEnabled).count { it }
                            simulatePipeline(
                                pipelineSteps, dynamicAgents, blackboard, task,
                                plannerEnabled, searcherEnabled, executorEnabled, criticEnabled,
                                autoExpand, githubSearch, codeRag
                            )
                            isExecuting = false
                            totalTasks++
                            val lastSuccess = pipelineSteps.lastOrNull()?.type != StepType.FAILED
                            successRate = if (lastSuccess) (successRate + 0.1f).coerceAtMost(1f) else successRate * 0.85f
                            agentCount = 4 + dynamicAgents.size
                            tokensUsed += (800..3000).random().toLong()
                            // 清理动态 Agent
                            if (autoExpand) {
                                delay(2000)
                                dynamicAgents.clear()
                                agentCount = 4
                            }
                        }
                    }
                },
                onStop = { isExecuting = false }
            )
        }
    }
}

// ============================================================
// Agent 拓扑视图 — 4 核心 + 动态扩容
// ============================================================

@Composable
private fun AgentTopology(
    plannerEnabled: Boolean, searcherEnabled: Boolean,
    executorEnabled: Boolean, criticEnabled: Boolean,
    dynamicAgents: List<DynamicAgent>, isExecuting: Boolean
) {
    Surface(tonalElevation = 1.dp) {
        Column(Modifier.padding(12.dp)) {
            Text("Agent 拓扑", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            // 4 核心节点
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                AgentNode("🏛️", "Planner", "架构师", plannerEnabled, isExecuting, RageColors.Thinking)
                AgentNode("🔍", "Searcher", "领航员", searcherEnabled, isExecuting, RageColors.DarkPrimary)
                AgentNode("💻", "Executor", "码农", executorEnabled, isExecuting, RageColors.Executing)
                AgentNode("✅", "Critic", "质检员", criticEnabled, isExecuting, RageColors.Success)
            }
            // 动态扩容节点
            if (dynamicAgents.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                Spacer(Modifier.height(6.dp))
                Text("动态扩容 (${dynamicAgents.size})", style = MaterialTheme.typography.labelSmall, color = RageColors.DarkSecondary)
                Spacer(Modifier.height(4.dp))
                dynamicAgents.forEach { agent ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("  ↳ ${agent.icon}", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.width(6.dp))
                        Text(agent.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.width(6.dp))
                        Text(agent.status, style = MaterialTheme.typography.labelSmall, color = RageColors.Executing)
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentNode(icon: String, name: String, role: String, enabled: Boolean, active: Boolean, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.alpha(if (enabled) 1f else 0.3f)
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(color.copy(alpha = if (active && enabled) 0.3f else 0.1f)).border(
                if (active && enabled) 2.dp else 0.dp, color, CircleShape
            ),
            contentAlignment = Alignment.Center
        ) {
            Text(icon, style = MaterialTheme.typography.titleMedium)
            if (active && enabled) {
                // 活跃脉冲
                val pulse by rememberInfiniteTransition("pulse_$name").animateFloat(
                    0.3f, 0.8f, infiniteRepeatable(tween(800), RepeatMode.Reverse), "p_$name"
                )
                Box(Modifier.size(44.dp).clip(CircleShape).background(color.copy(alpha = pulse * 0.2f)))
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(name, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (enabled) color else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(role, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ============================================================
// 空状态
// ============================================================

@Composable
private fun EmptyState() {
    Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("⚡", style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(12.dp))
        Text("狂暴模式就绪", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("4 核心 Agent · 动态扩容 · 黑板架构", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text("Planner(架构师) → Searcher(领航员) → Executor(码农) → Critic(质检员)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        Text("输入任务，系统自动拆解 DAG · 动态扩容 · Git 分支 · 沙盒执行", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

// ============================================================
// 流水线步骤卡片
// ============================================================

@Composable
private fun PipelineStepCard(step: PipelineStep) {
    val color = when (step.type) {
        StepType.PLANNER -> RageColors.Thinking
        StepType.SEARCHER -> RageColors.DarkPrimary
        StepType.EXECUTOR -> RageColors.Executing
        StepType.CRITIC -> RageColors.Success
        StepType.EXPAND -> RageColors.DarkSecondary
        StepType.FAILED -> RageColors.Failed
    }
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(color.copy(alpha = 0.2f)), Alignment.Center) {
                    Text(step.icon, style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.width(8.dp))
                Text(step.agent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color)
                Spacer(Modifier.width(6.dp))
                Text(step.action, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text("${step.durationMs}ms", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (step.detail.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(step.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (step.output.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFF0D0D0D)) {
                    Text(step.output, Modifier.padding(6.dp, 4.dp), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp), color = RageColors.Success)
                }
            }
        }
    }
}

// ============================================================
// 打字指示器
// ============================================================

@Composable
private fun TypingIndicator() {
    val t = rememberInfiniteTransition("tp")
    val a1 by t.animateFloat(.3f, 1f, infiniteRepeatable(tween(500), RepeatMode.Reverse), "1")
    val a2 by t.animateFloat(.3f, 1f, infiniteRepeatable(tween(500), RepeatMode.Reverse, delayMillis = 150), "2")
    val a3 by t.animateFloat(.3f, 1f, infiniteRepeatable(tween(500), RepeatMode.Reverse, delayMillis = 300), "3")
    Row(Modifier.padding(horizontal = 16.dp)) {
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(Modifier.padding(12.dp, 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("●", color = RageColors.Executing, modifier = Modifier.alpha(a1))
                Text("●", color = RageColors.Executing, modifier = Modifier.alpha(a2))
                Text("●", color = RageColors.Executing, modifier = Modifier.alpha(a3))
            }
        }
    }
}

// ============================================================
// 黑板状态栏
// ============================================================

@Composable
private fun BlackboardBar(entries: Map<String, String>) {
    Surface(tonalElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("📋 黑板", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = RageColors.DarkTertiary)
            entries.take(3).forEach { (k, v) ->
                Text("$k: ${v.take(20)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            if (entries.size > 3) Text("+${entries.size - 3}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ============================================================
// 指标栏
// ============================================================

@Composable
private fun MetricsBar(total: Long, rate: Float, agents: Int, tokens: Long) {
    Surface(tonalElevation = 1.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            MetricItem("任务", total.toString(), RageColors.DarkPrimary)
            MetricItem("成功率", "${(rate * 100).toInt()}%", RageColors.Success)
            MetricItem("Agent", agents.toString(), RageColors.Executing)
            MetricItem("Token", if (tokens > 1000) "${tokens / 1000}k" else tokens.toString(), RageColors.Thinking)
        }
    }
}

@Composable
private fun MetricItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ============================================================
// 预设选择器
// ============================================================

@Composable
private fun PresetSelector(selected: RagePreset, onSelect: (RagePreset) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(selected = true, onClick = { expanded = true }, label = { Text("${selected.icon} ${selected.displayName}", style = MaterialTheme.typography.labelMedium) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = RageColors.DarkPrimaryContainer, selectedLabelColor = RageColors.DarkOnPrimaryContainer))
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            RagePreset.values().forEach { DropdownMenuItem(text = { Text("${preset.icon} ${preset.displayName} — ${preset.desc}") }, onClick = { onSelect(it); expanded = false }) }
        }
    }
}

// ============================================================
// 高级输入栏 — Agent 开关 + 扩容策略
// ============================================================

@Composable
private fun AdvancedInputBar(
    text: String, onTextChange: (String) -> Unit, isExecuting: Boolean,
    plannerEnabled: Boolean, onPlannerToggle: (Boolean) -> Unit,
    searcherEnabled: Boolean, onSearcherToggle: (Boolean) -> Unit,
    executorEnabled: Boolean, onExecutorToggle: (Boolean) -> Unit,
    criticEnabled: Boolean, onCriticToggle: (Boolean) -> Unit,
    autoExpand: Boolean, onAutoExpandToggle: (Boolean) -> Unit,
    gitBranching: Boolean, onGitBranchingToggle: (Boolean) -> Unit,
    sandboxExec: Boolean, onSandboxExecToggle: (Boolean) -> Unit,
    githubSearch: Boolean, onGithubSearchToggle: (Boolean) -> Unit,
    codeRag: Boolean, onCodeRagToggle: (Boolean) -> Unit,
    onExecute: () -> Unit, onStop: () -> Unit
) {
    var showAdvanced by remember { mutableStateOf(false) }
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Column {
            // 高级设置（可展开）
            if (showAdvanced) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("核心 Agent", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ToggleRow("🏛️ Planner（架构师）", plannerEnabled, onPlannerToggle)
                    ToggleRow("🔍 Searcher（领航员）", searcherEnabled, onSearcherToggle)
                    ToggleRow("💻 Executor（码农）", executorEnabled, onExecutorToggle)
                    ToggleRow("✅ Critic（质检员）", criticEnabled, onCriticToggle)
                    Spacer(Modifier.height(4.dp))
                    Text("扩容策略", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ToggleRow("🧬 动态扩容（spawn_agent）", autoExpand, onAutoExpandToggle)
                    ToggleRow("🌿 Git 分支管理", gitBranching, onGitBranchingToggle)
                    ToggleRow("📦 沙盒执行（Docker）", sandboxExec, onSandboxExecToggle)
                    ToggleRow("🐙 GitHub 搜索", githubSearch, onGithubSearchToggle)
                    ToggleRow("📚 代码库 RAG", codeRag, onCodeRagToggle)
                }
            }
            // 输入行
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { showAdvanced = !showAdvanced }) {
                    Icon(if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (showAdvanced) "收起" else "高级设置")
                }
                OutlinedTextField(value = text, onValueChange = onTextChange, modifier = Modifier.weight(1f), placeholder = { Text("输入任务，4 Agent 自动拆解执行...") }, shape = RoundedCornerShape(24.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = RageColors.DarkPrimary, unfocusedBorderColor = MaterialTheme.colorScheme.outline), maxLines = 4)
                Spacer(Modifier.width(4.dp))
                if (isExecuting) {
                    FilledIconButton(onStop, shape = RoundedCornerShape(50), colors = FilledIconButtonDefaults.colors(containerColor = RageColors.Failed)) { Icon(Icons.Default.Stop, "停止") }
                } else {
                    FilledIconButton(onExecute, enabled = text.isNotBlank(), shape = RoundedCornerShape(50), colors = FilledIconButtonDefaults.colors(containerColor = RageColors.DarkPrimary)) { Icon(Icons.AutoMirrored.Filled.Send, "执行") }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onToggle, modifier = Modifier.scale(0.8f))
    }
}

// ============================================================
// 模拟执行 — 4 Agent 流水线 + 动态扩容
// ============================================================

private suspend fun simulatePipeline(
    steps: MutableList<PipelineStep>,
    dynamicAgents: MutableList<DynamicAgent>,
    blackboard: MutableMap<String, String>,
    task: String,
    planner: Boolean, searcher: Boolean, executor: Boolean, critic: Boolean,
    autoExpand: Boolean, githubSearch: Boolean, codeRag: Boolean
) {
    // 1. Planner — 拆解 DAG
    if (planner) {
        steps.add(PipelineStep(StepType.PLANNER, "🏛️", "Planner", "拆解任务 DAG", "理解：${task.take(50)}\n拆分为 3 个子任务\n写入黑板: task_plan, file_list", "", 0))
        delay(800)
        steps[steps.lastIndex] = steps.last().copy(durationMs = 800)
        blackboard["task_plan"] = "3 subtasks"
        blackboard["architecture"] = "modular"
    }

    // 2. Searcher — 检索定位
    if (searcher) {
        val searchDetail = buildString {
            append("定位相关文件和依赖\n")
            if (codeRag) append("• 代码库 RAG: 找到 5 个相关文件\n")
            if (githubSearch) append("• GitHub 搜索: 找到 2 个参考实现\n")
            append("• AST 解析: 提取调用关系图\n")
            append("写入黑板: file_map, dependencies")
        }
        steps.add(PipelineStep(StepType.SEARCHER, "🔍", "Searcher", "检索定位", searchDetail, "", 0))
        delay(600)
        steps[steps.lastIndex] = steps.last().copy(durationMs = 600)
        blackboard["file_map"] = "5 files"
        blackboard["dependencies"] = "3 deps"
    }

    // 3. 动态扩容（如果开启）
    if (autoExpand) {
        val spawnAgent = DynamicAgent("🔧", "Frontend Expert", "执行中")
        dynamicAgents.add(spawnAgent)
        steps.add(PipelineStep(StepType.EXPAND, "🧬", "Planner", "动态扩容", "spawn_agent: Frontend Expert\nSystem Prompt: React 性能优化专家\n工具: edit_file, npm_run\n生命周期: 即用即毁", "", 200))
        delay(300)
    }

    // 4. Executor — 执行
    if (executor) {
        val execOutput = buildString {
            append("diff --git a/src/main.kt b/src/main.kt\n")
            append("@@ -45,5 +45,8 @@\n")
            append(" fun process(input: String): String {\n")
            append("-    return input\n")
            append("+    return input.trim()\n")
            append("+    // Optimized by Executor\n")
            append("+    // Reviewed by Critic pending\n")
            append(" }\n")
            if (autoExpand) append("\n[Frontend Expert] 同步修改了 UI 组件")
        }
        steps.add(PipelineStep(StepType.EXECUTOR, "💻", "Executor", "生成 Diff 补丁", "基于黑板 file_map 修改文件\n沙盒执行: docker exec\n输出: unified diff", execOutput, 0))
        delay(1000)
        steps[steps.lastIndex] = steps.last().copy(durationMs = 1000)
        blackboard["patch"] = "diff applied"
    }

    // 5. Critic — 审查
    if (critic) {
        val passed = (1..10).random() > 3  // 70% 通过率
        if (passed) {
            steps.add(PipelineStep(StepType.CRITIC, "✅", "Critic", "审查通过", "静态分析: ✓ 无问题\n单元测试: ✓ 全部通过\nLinter: ✓ 代码规范\nGit: 合并到主分支", "npm test → 12 passed\nflake8 → 0 errors\ngit merge → success", 0))
            delay(500)
            steps[steps.lastIndex] = steps.last().copy(durationMs = 500)
            blackboard["status"] = "merged"
        } else {
            steps.add(PipelineStep(StepType.FAILED, "❌", "Critic", "审查失败", "静态分析: ✗ 发现 2 个问题\n单元测试: ✗ 1 个失败\n重试次数: 3/3\n→ 终止动态 Agent，通知 Planner 重新规划", "npm test → 1 failed\nError: TypeError in line 47\n→ git reset --hard HEAD~1", 0))
            delay(500)
            steps[steps.lastIndex] = steps.last().copy(durationMs = 500)
            blackboard["status"] = "failed_rollback"
            // 清理动态 Agent
            if (autoExpand) dynamicAgents.clear()
        }
    }
}

// ============================================================
// 数据
// ============================================================

enum class RagePreset(val displayName: String, val icon: String, val desc: String) {
    PERFORMANCE("性能", "🚀", "最大并发 · 长超时"),
    BALANCED("平衡", "⚖️", "适中并发 · 合理超时"),
    POWER_SAVER("省电", "🔋", "低并发 · 短超时"),
    LOCAL("本地", "💻", "离线推理"),
    CLOUD("云端", "☁️", "DeepSeek API"),
    STREAMING("流式", "🌊", "超大文本增量"),
    EXTREME("极限", "🔥", "多路径并行 + 红蓝对抗")
}

enum class StepType { PLANNER, SEARCHER, EXECUTOR, CRITIC, EXPAND, FAILED }

data class PipelineStep(
    val type: StepType, val icon: String, val agent: String,
    val action: String, val detail: String, val output: String, val durationMs: Long
)

data class DynamicAgent(val icon: String, val name: String, val status: String)

// 扩展：RageColors 中缺少的
private val RageColors.DarkTertiary get() = com.apex.apk.rage.ui.theme.RageColors.DarkTertiary
private val RageColors.DarkSecondary get() = com.apex.apk.rage.ui.theme.RageColors.DarkSecondary

// Modifier.scale 扩展
private fun Modifier.scale(s: Float) = this.then(Modifier).let { it }
