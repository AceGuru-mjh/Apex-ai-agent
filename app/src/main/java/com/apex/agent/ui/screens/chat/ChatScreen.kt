package com.apex.agent.ui.screens.chat

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Agent 聊天界面 — 完整版。
 *
 * 增强功能：
 * - 输入栏：技能选择 / 深度思考 / 网络搜索 按钮组
 * - 流水式输出（逐字流式显示 Agent 回复）
 * - 打字指示器（三点动画）
 * - 消息 Markdown 基础渲染（代码块/粗体/列表）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(modifier: Modifier = Modifier) {
    var inputText by remember { mutableStateOf("") }
    val messages = remember {
        mutableStateListOf<ChatMessage>(
            ChatMessage("你好，我是 Apex AI 助手。\n\n我可以帮你：\n- 执行任务和命令\n- 分析代码\n- 生成文档和报告\n- 网络搜索\n\n有什么可以帮你的？", isUser = false)
        )
    }
    var isStreaming by remember { mutableStateOf(false) }
    var selectedSkill by remember { mutableStateOf<String?>(null) }
    var deepThinking by remember { mutableStateOf(false) }
    var webSearch by remember { mutableStateOf(false) }
    var showSkillPicker by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // 技能列表
    val skills = remember {
        listOf(
            SkillItem("auto", "自动选择", "🤖", "根据任务自动选择最佳技能"),
            SkillItem("react", "ReAct 推理", "🧠", "推理 + 工具调用循环"),
            SkillItem("cot", "思维链", "🔗", "逐步分解复杂问题"),
            SkillItem("tot", "思维树", "🌳", "多路径探索选最优解"),
            SkillItem("code", "代码生成", "💻", "生成高质量代码"),
            SkillItem("search", "深度搜索", "🔍", "多轮网络搜索 + 信息提取"),
            SkillItem("translate", "翻译", "🌐", "多语言翻译"),
            SkillItem("summarize", "总结", "📝", "长文本摘要"),
            SkillItem("analyze", "分析", "📊", "数据分析与可视化")
        )
    }

    if (showSkillPicker) {
        SkillPickerDialog(
            skills = skills,
            selectedSkillId = selectedSkill,
            onDismiss = { showSkillPicker = false },
            onSelect = { skillId ->
                selectedSkill = skillId
                showSkillPicker = false
            }
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Apex Agent", style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    // 当前激活的功能标记
                    if (deepThinking) {
                        AssistChip(
                            onClick = { deepThinking = false },
                            label = { Text("🧠 深度思考", style = MaterialTheme.typography.labelSmall) }
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    if (webSearch) {
                        AssistChip(
                            onClick = { webSearch = false },
                            label = { Text("🌐 联网搜索", style = MaterialTheme.typography.labelSmall) }
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    if (selectedSkill != null && selectedSkill != "auto") {
                        AssistChip(
                            onClick = { showSkillPicker = true },
                            label = { Text("⚡ ${skills.find { it.id == selectedSkill }?.name}", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.MoreVert, "更多")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 消息列表
            LazyColumn(
                modifier = Modifier.weight(1f),
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages) { message ->
                    MessageBubble(message)
                }
                // 流式输出时的打字指示器
                if (isStreaming) {
                    item { TypingIndicator() }
                }
            }

            // 快捷操作栏
            QuickActionsRow(
                onTerminal = {},
                onTool = {},
                onWorkflow = {},
                onFile = {}
            )

            // 增强输入栏
            EnhancedInputBar(
                text = inputText,
                onTextChange = { inputText = it },
                selectedSkill = selectedSkill,
                onSkillClick = { showSkillPicker = true },
                deepThinking = deepThinking,
                onDeepThinkingToggle = { deepThinking = !deepThinking },
                webSearch = webSearch,
                onWebSearchToggle = { webSearch = !webSearch },
                isStreaming = isStreaming,
                onSend = {
                    if (inputText.isNotBlank() && !isStreaming) {
                        messages.add(ChatMessage(inputText, isUser = true))
                        val userMsg = inputText
                        inputText = ""

                        // 启动流式输出
                        scope.launch {
                            isStreaming = true
                            streamResponse(messages, userMsg, selectedSkill, deepThinking, webSearch)
                            isStreaming = false
                        }
                    }
                },
                onStop = {
                    isStreaming = false
                }
            )
        }
    }
}

// ============================================================
// 流水式输出
// ============================================================

/**
 * 模拟流水式输出 — 逐字添加到消息列表。
 *
 * 实际实现应接入 ApexClient.market.invokeModel（流式 API）或
 * Rage APK 的 executeTask（带 onProgress 回调）。
 */
private suspend fun streamResponse(
    messages: MutableList<ChatMessage>,
    userMessage: String,
    skill: String?,
    deepThinking: Boolean,
    webSearch: Boolean
) {
    // 构造回复
    val response = buildString {
        if (deepThinking) append("🧠 深度思考模式\n\n")
        if (webSearch) append("🌐 正在搜索网络...\n\n")
        if (skill != null && skill != "auto") {
            append("⚡ 技能：${skill}\n\n")
        }
        append("收到你的请求：\"${userMessage.take(50)}\"\n\n")
        append("我来帮你分析：\n\n")
        append("1. **理解需求** — 你的问题是关于 ")
        append(when {
            userMessage.contains("代码") -> "代码分析"
            userMessage.contains("翻译") -> "多语言翻译"
            userMessage.contains("搜索") -> "信息检索"
            userMessage.contains("文档") -> "文档生成"
            else -> "任务执行"
        })
        append("\n2. **制定方案** — 我将分步骤完成\n")
        append("3. **执行任务** — 正在处理...\n")
        append("4. **输出结果** — 完成 ✅\n\n")
        append("```kotlin\nfun process(input: String): String {\n    return input.trim()\n}\n```")
    }

    // 逐字流式添加
    val streamMsg = ChatMessage("", isUser = false)
    messages.add(streamMsg)
    val sb = StringBuilder()
    val chunkSize = 3  // 每次添加 3 个字符
    var i = 0
    while (i < response.length) {
        val end = minOf(i + chunkSize, response.length)
        sb.append(response, i, end)
        val msgIndex = messages.size - 1
        messages[msgIndex] = streamMsg.copy(text = sb.toString())
        i = end
        delay(20)  // 20ms 间隔，模拟流式
    }
}

// ============================================================
// 消息气泡
// ============================================================

@Composable
private fun MessageBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!message.isUser) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text("🤖", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.width(8.dp))
        }

        Surface(
            shape = if (message.isUser) {
                RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
            } else {
                RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)
            },
            color = if (message.isUser) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            tonalElevation = 2.dp,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                // 简单 Markdown 渲染
                RenderMarkdown(message.text, isUser = message.isUser)
            }
        }
    }
}

/**
 * 简单 Markdown 渲染（代码块 / 粗体 / 列表）。
 */
@Composable
private fun RenderMarkdown(text: String, isUser: Boolean) {
    val color = if (isUser) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurfaceVariant

    val blocks = text.split("```")
    blocks.forEachIndexed { index, block ->
        if (index % 2 == 0) {
            // 普通文本
            block.split("\n").forEach { line ->
                if (line.isNotBlank()) {
                    val isBold = line.startsWith("**") && line.endsWith("**")
                    val isListItem = line.trim().startsWith(Regex("\\d+\\.|[-•]"))
                    val displayText = if (isBold) line.removePrefix("**").removeSuffix("**") else line
                    Row(modifier = Modifier.fillMaxWidth()) {
                        if (isListItem) Spacer(Modifier.width(8.dp))
                        Text(
                            text = displayText,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = color
                        )
                    }
                } else {
                    Spacer(Modifier.height(4.dp))
                }
            }
        } else {
            // 代码块
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Text(
                    text = block.trim(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

// ============================================================
// 打字指示器
// ============================================================

@Composable
private fun TypingIndicator() {
    val transition = rememberInfiniteTransition(label = "typing")
    val alpha1 by transition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
            delayMillis = 0
        ), label = "dot1"
    )
    val alpha2 by transition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
            delayMillis = 200
        ), label = "dot2"
    )
    val alpha3 by transition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
            delayMillis = 400
        ), label = "dot3"
    )

    Row(
        horizontalArrangement = Arrangement.Start,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text("🤖", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("●", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.alpha(alpha1))
                Text("●", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.alpha(alpha2))
                Text("●", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.alpha(alpha3))
            }
        }
    }
}

// ============================================================
// 快捷操作栏
// ============================================================

@Composable
private fun QuickActionsRow(
    onTerminal: () -> Unit,
    onTool: () -> Unit,
    onWorkflow: () -> Unit,
    onFile: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AssistChip(onClick = onFile, label = { Text("📄 附件") })
        AssistChip(onClick = onTerminal, label = { Text("💻 终端") })
        AssistChip(onClick = onTool, label = { Text("🔧 工具") })
        AssistChip(onClick = onWorkflow, label = { Text("📋 工作流") })
    }
}

// ============================================================
// 增强输入栏
// ============================================================

@Composable
private fun EnhancedInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    selectedSkill: String?,
    onSkillClick: () -> Unit,
    deepThinking: Boolean,
    onDeepThinkingToggle: () -> Unit,
    webSearch: Boolean,
    onWebSearchToggle: () -> Unit,
    isStreaming: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 8.dp
    ) {
        Column {
            // 功能按钮行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 技能选择按钮
                FilterChip(
                    selected = selectedSkill != null && selectedSkill != "auto",
                    onClick = onSkillClick,
                    label = {
                        Text(
                            if (selectedSkill != null && selectedSkill != "auto") "⚡ 技能" else "⚡ 技能",
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    leadingIcon = null
                )

                // 深度思考按钮
                FilterChip(
                    selected = deepThinking,
                    onClick = onDeepThinkingToggle,
                    label = { Text("🧠 深度思考", style = MaterialTheme.typography.labelMedium) }
                )

                // 网络搜索按钮
                FilterChip(
                    selected = webSearch,
                    onClick = onWebSearchToggle,
                    label = { Text("🌐 联网搜索", style = MaterialTheme.typography.labelMedium) }
                )
            }

            // 输入行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {}) {
                    Icon(Icons.Default.AttachFile, "附件")
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("向 Agent 发送消息...") },
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    maxLines = 4
                )
                IconButton(onClick = {}) {
                    Icon(Icons.Default.Mic, "语音输入")
                }

                if (isStreaming) {
                    // 停止按钮
                    FilledIconButton(
                        onClick = onStop,
                        shape = RoundedCornerShape(50)
                    ) {
                        Icon(Icons.Default.Stop, "停止")
                    }
                } else {
                    // 发送按钮
                    FilledIconButton(
                        onClick = onSend,
                        enabled = text.isNotBlank(),
                        shape = RoundedCornerShape(50)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, "发送")
                    }
                }
            }
        }
    }
}

// ============================================================
// 技能选择弹窗
// ============================================================

@Composable
private fun SkillPickerDialog(
    skills: List<SkillItem>,
    selectedSkillId: String?,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择技能", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                skills.forEach { skill ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(skill.id) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(skill.icon, style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(skill.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(skill.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (skill.id == selectedSkillId) {
                            Icon(Icons.Default.Check, "已选", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

// ============================================================
// 数据类
// ============================================================

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

data class SkillItem(
    val id: String,
    val name: String,
    val icon: String,
    val description: String
)
