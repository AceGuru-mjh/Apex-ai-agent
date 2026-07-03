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
 * - 左上角：汉堡菜单（三条横杠）→ 打开左侧抽屉导航
 * - 右上角：上下文百分比 + 压缩按钮
 * - 输入栏：技能选择 / 深度思考 / 网络搜索
 * - 流水式输出（逐字流式显示）
 * - 智能压缩 + 主动压缩对话
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onMenuClick: () -> Unit = {}
) {
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

    // 上下文管理
    var contextPercent by remember { mutableStateOf(28) }  // 模拟当前上下文使用率
    var showCompressDialog by remember { mutableStateOf(false) }
    var autoCompress by remember { mutableStateOf(true) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

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

    if (showCompressDialog) {
        CompressDialog(
            onDismiss = { showCompressDialog = false },
            onCompress = {
                // 模拟压缩：移除前半部分消息
                val keepCount = messages.size / 2 + 1
                val toRemove = messages.size - keepCount
                if (toRemove > 0) {
                    repeat(toRemove) {
                        if (messages.isNotEmpty()) messages.removeAt(0)
                    }
                }
                contextPercent = (contextPercent * 0.4f).toInt().coerceAtLeast(10)
                showCompressDialog = false
            },
            autoCompress = autoCompress,
            onAutoCompressChange = { autoCompress = it }
        )
    }

    // 智能压缩：超过 85% 自动提示
    LaunchedEffect(contextPercent) {
        if (autoCompress && contextPercent >= 85 && !showCompressDialog) {
            showCompressDialog = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, contentDescription = "菜单")
                    }
                },
                title = { Text("Apex Agent", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    // 上下文百分比
                    ContextPercentIndicator(
                        percent = contextPercent,
                        onClick = { showCompressDialog = true }
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { showCompressDialog = true }) {
                        Icon(Icons.Default.Compress, contentDescription = "压缩对话")
                    }
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多")
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

            // 增强输入栏（深度思考/联网搜索只在输入栏）
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
                        // 每次发送增加上下文使用
                        contextPercent = (contextPercent + 8).coerceAtMost(100)

                        scope.launch {
                            isStreaming = true
                            streamResponse(messages, userMsg, selectedSkill, deepThinking, webSearch)
                            isStreaming = false
                            // 回复后增加上下文
                            contextPercent = (contextPercent + 12).coerceAtMost(100)
                        }
                    }
                },
                onStop = { isStreaming = false }
            )
        }
    }
}

// ============================================================
// 上下文百分比指示器
// ============================================================

@Composable
private fun ContextPercentIndicator(
    percent: Int,
    onClick: () -> Unit
) {
    val color = when {
        percent >= 85 -> MaterialTheme.colorScheme.error
        percent >= 60 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 进度环
        Box(
            modifier = Modifier.size(20.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = { percent / 100f },
                color = color,
                strokeWidth = 2.dp,
                modifier = Modifier.fillMaxSize()
            )
            Text(
                "${percent}%",
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ============================================================
// 压缩对话弹窗
// ============================================================

@Composable
private fun CompressDialog(
    onDismiss: () -> Unit,
    onCompress: () -> Unit,
    autoCompress: Boolean,
    onAutoCompressChange: (Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("压缩对话", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "压缩会保留最近的消息和关键上下文，移除较早的对话历史。\n\n这可以释放上下文空间，让 Agent 继续正常工作。",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("智能压缩", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = autoCompress, onCheckedChange = onAutoCompressChange)
                }
                Text(
                    "开启后，上下文超过 85% 时自动提示压缩",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            FilledButton(onClick = onCompress) { Text("立即压缩") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

// ============================================================
// 流水式输出
// ============================================================

private suspend fun streamResponse(
    messages: MutableList<ChatMessage>,
    userMessage: String,
    skill: String?,
    deepThinking: Boolean,
    webSearch: Boolean
) {
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

    val streamMsg = ChatMessage("", isUser = false)
    messages.add(streamMsg)
    val sb = StringBuilder()
    val chunkSize = 3
    var i = 0
    while (i < response.length) {
        val end = minOf(i + chunkSize, response.length)
        sb.append(response, i, end)
        val msgIndex = messages.size - 1
        messages[msgIndex] = streamMsg.copy(text = sb.toString())
        i = end
        delay(20)
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
            color = if (message.isUser) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 2.dp,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                RenderMarkdown(message.text, isUser = message.isUser)
            }
        }
    }
}

@Composable
private fun RenderMarkdown(text: String, isUser: Boolean) {
    val color = if (isUser) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurfaceVariant

    val blocks = text.split("```")
    blocks.forEachIndexed { index, block ->
        if (index % 2 == 0) {
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
        animationSpec = infiniteRepeatable(animation = tween(600, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "dot1"
    )
    val alpha2 by transition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(600, easing = LinearEasing), repeatMode = RepeatMode.Reverse, delayMillis = 200),
        label = "dot2"
    )
    val alpha3 by transition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(600, easing = LinearEasing), repeatMode = RepeatMode.Reverse, delayMillis = 400),
        label = "dot3"
    )

    Row(horizontalArrangement = Arrangement.Start, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) { Text("🤖", style = MaterialTheme.typography.titleMedium) }
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
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
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Column {
            // 功能按钮行（深度思考/联网搜索只在这里）
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilterChip(
                    selected = selectedSkill != null && selectedSkill != "auto",
                    onClick = onSkillClick,
                    label = { Text("⚡ 技能", style = MaterialTheme.typography.labelMedium) }
                )
                FilterChip(
                    selected = deepThinking,
                    onClick = onDeepThinkingToggle,
                    label = { Text("🧠 深度思考", style = MaterialTheme.typography.labelMedium) }
                )
                FilterChip(
                    selected = webSearch,
                    onClick = onWebSearchToggle,
                    label = { Text("🌐 联网搜索", style = MaterialTheme.typography.labelMedium) }
                )
            }

            // 输入行
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {}) { Icon(Icons.Default.AttachFile, "附件") }
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
                IconButton(onClick = {}) { Icon(Icons.Default.Mic, "语音输入") }

                if (isStreaming) {
                    FilledIconButton(onClick = onStop, shape = RoundedCornerShape(50)) {
                        Icon(Icons.Default.Stop, "停止")
                    }
                } else {
                    FilledIconButton(onClick = onSend, enabled = text.isNotBlank(), shape = RoundedCornerShape(50)) {
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
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

// ============================================================
// 数据类
// ============================================================

data class ChatMessage(val text: String, val isUser: Boolean, val timestamp: Long = System.currentTimeMillis())
data class SkillItem(val id: String, val name: String, val icon: String, val description: String)
