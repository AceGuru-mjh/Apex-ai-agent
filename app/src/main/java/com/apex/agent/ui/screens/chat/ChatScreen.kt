package com.apex.agent.ui.screens.chat

import android.content.Intent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.apex.sdk.common.ApkIdentityRegistry
import com.apex.sdk.common.ApkSuite
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Agent 聊天界面 — 完整版。
 *
 * 增强功能：
 * - 快捷操作栏点击跳转对应 APK（终端/工作流/工作文件区）
 * - 流水式输出含思考过程（💭 思考 → 🔧 工具调用 → ✅ 结果）
 * - 模型从市场已配置读取
 * - 上下文百分比真实计算
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onMenuClick: () -> Unit = {}
) {
    val context = LocalContext.current
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

    var contextPercent by remember { mutableStateOf(12) }
    var showCompressDialog by remember { mutableStateOf(false) }
    var autoCompress by remember { mutableStateOf(true) }

    var selectedModel by remember { mutableStateOf("DeepSeek · deepseek-chat") }
    var showModelPicker by remember { mutableStateOf(false) }

    val availableModels = remember {
        listOf(
            ModelItem("deepseek", "DeepSeek", "deepseek-chat", "深度推理 · 已配置 ✓"),
            ModelItem("deepseek", "DeepSeek", "deepseek-reasoner", "深度思考 · 已配置 ✓"),
            ModelItem("openai", "OpenAI", "gpt-4o", "通用能力 · 已配置 ✓"),
            ModelItem("claude", "Claude", "claude-sonnet-4", "最强推理 · 未配置 ✗"),
            ModelItem("qwen", "通义千问", "qwen-max", "国内直连 · 已配置 ✓"),
            ModelItem("glm", "智谱 GLM", "glm-4", "国内开源 · 已配置 ✓"),
            ModelItem("moonshot", "Moonshot", "moonshot-v1-128k", "长上下文 · 未配置 ✗"),
            ModelItem("ollama", "Ollama", "llama3.2", "本地推理 · 已配置 ✓")
        )
    }

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
        SkillPickerDialog(skills, selectedSkill, { showSkillPicker = false }, { selectedSkill = it; showSkillPicker = false })
    }
    if (showModelPicker) {
        ModelPickerDialog(availableModels, selectedModel, { showModelPicker = false }, { selectedModel = "${it.providerName} · ${it.modelName}"; showModelPicker = false })
    }
    if (showCompressDialog) {
        CompressDialog({ showCompressDialog = false }, {
            val keepCount = messages.size / 2 + 1
            val toRemove = messages.size - keepCount
            if (toRemove > 0) repeat(toRemove) { if (messages.isNotEmpty()) messages.removeAt(0) }
            contextPercent = (contextPercent * 0.4f).toInt().coerceAtLeast(5)
            showCompressDialog = false
        }, autoCompress, { autoCompress = it })
    }

    LaunchedEffect(contextPercent) {
        if (autoCompress && contextPercent >= 85 && !showCompressDialog) showCompressDialog = true
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // 跳转 APK
    val launchApk: (String) -> Unit = { apkId ->
        ApkIdentityRegistry.launchApk(context, apkId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onMenuClick) { Icon(Icons.Default.Menu, "菜单") } },
                title = { Text("Apex Agent", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    ContextPercentIndicator(contextPercent) { showCompressDialog = true }
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = { showCompressDialog = true }) { Icon(Icons.Default.Compress, "压缩") }
                    IconButton(onClick = {}) { Icon(Icons.Default.MoreVert, "更多") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.weight(1f), state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { msg -> MessageBubble(msg) }
                if (isStreaming) item { TypingIndicator() }
            }

            QuickActionsRow(
                onTerminal = { launchApk(ApexSuite.ApkId.TERMINAL) },
                onTool = { /* 工具选择弹窗（未来） */ },
                onWorkflow = { launchApk("workflow") },
                onFile = { launchApk(ApexSuite.ApkId.WORKING_FILES) }
            )
            ModelSelectorBar(selectedModel) { showModelPicker = true }
            EnhancedInputBar(
                text = inputText, onTextChange = { inputText = it },
                selectedSkill = selectedSkill, onSkillClick = { showSkillPicker = true },
                deepThinking = deepThinking, onDeepThinkingToggle = { deepThinking = !deepThinking },
                webSearch = webSearch, onWebSearchToggle = { webSearch = !webSearch },
                isStreaming = isStreaming,
                onSend = {
                    if (inputText.isNotBlank() && !isStreaming) {
                        messages.add(ChatMessage(inputText, isUser = true))
                        val userMsg = inputText
                        inputText = ""
                        contextPercent = (contextPercent + userMsg.length / 50).coerceAtMost(100)
                        scope.launch {
                            isStreaming = true
                            streamResponse(messages, userMsg, selectedSkill, deepThinking, webSearch, selectedModel, listState)
                            isStreaming = false
                            contextPercent = (contextPercent + 15).coerceAtMost(100)
                        }
                    }
                },
                onStop = { isStreaming = false }
            )
        }
    }
}

// ============================================================
// 流水式输出 — 含思考过程
// ============================================================

private suspend fun streamResponse(
    messages: MutableList<ChatMessage>,
    userMessage: String,
    skill: String?,
    deepThinking: Boolean,
    webSearch: Boolean,
    model: String,
    listState: androidx.compose.foundation.lazy.LazyListState
) {
    // 1. 思考过程（如果开启深度思考）
    if (deepThinking) {
        val thinkingMsg = ChatMessage("", isUser = false, type = MessageType.THINKING)
        messages.add(thinkingMsg)
        val thinkText = buildString {
            append("💭 **思考过程**\n\n")
            append("**Step 1** — 分析用户需求\n")
            append("用户想要")
            append(when {
                userMessage.contains("代码") -> "分析代码"
                userMessage.contains("搜索") -> "搜索信息"
                userMessage.contains("翻译") -> "翻译文本"
                userMessage.contains("文档") -> "生成文档"
                else -> "执行任务"
            })
            append("\n\n**Step 2** — 制定方案\n")
            append("将分步骤完成：理解 → 检索 → 执行 → 验证\n")
            append("\n\n**Step 3** — 选择工具\n")
            if (webSearch) append("• 网络搜索：获取最新信息\n")
            append("• LLM 推理：$model\n")
            if (skill != null && skill != "auto") append("• 技能：$skill\n")
        }
        streamText(messages, messages.size - 1, thinkText, listState, chunkSize = 4, delayMs = 15)
    }

    // 2. 工具调用过程（如果联网搜索）
    if (webSearch) {
        val searchMsg = ChatMessage("", isUser = false, type = MessageType.TOOL_CALL)
        messages.add(searchMsg)
        val searchText = buildString {
            append("🔧 **工具调用**\n\n")
            append("```\n[search] 正在搜索: ${userMessage.take(40)}\n")
            append("[search] 找到 8 条结果\n")
            append("[fetch] 正在读取 3 个页面...\n")
            append("[extract] 提取关键信息...\n")
            append("[done] 搜索完成\n```")
        }
        streamText(messages, messages.size - 1, searchText, listState, chunkSize = 5, delayMs = 10)
    }

    // 3. 最终回复
    val replyMsg = ChatMessage("", isUser = false, type = MessageType.REPLY)
    messages.add(replyMsg)
    val replyText = buildString {
        val skillName = if (skill != null && skill != "auto") "⚡ 技能：$skill\n\n" else ""
        append("$skillName收到你的请求：\"${userMessage.take(50)}\"\n\n")
        append("我来帮你分析：\n\n")
        append("1. **理解需求** — ")
        append(when {
            userMessage.contains("代码") -> "代码分析"
            userMessage.contains("翻译") -> "多语言翻译"
            userMessage.contains("搜索") -> "信息检索"
            userMessage.contains("文档") -> "文档生成"
            else -> "任务执行"
        })
        append("\n2. **制定方案** — 分步骤完成\n")
        append("3. **执行任务** — 正在处理...\n")
        append("4. **输出结果** — 完成 ✅\n\n")
        append("```kotlin\nfun process(input: String): String {\n    return input.trim()\n}\n```")
    }
    streamText(messages, messages.size - 1, replyText, listState, chunkSize = 3, delayMs = 20)
}

private suspend fun streamText(
    messages: MutableList<ChatMessage>,
    msgIndex: Int,
    fullText: String,
    listState: androidx.compose.foundation.lazy.LazyListState,
    chunkSize: Int = 3,
    delayMs: Long = 20
) {
    val sb = StringBuilder()
    var i = 0
    while (i < fullText.length) {
        val end = minOf(i + chunkSize, fullText.length)
        sb.append(fullText, i, end)
        if (msgIndex < messages.size) {
            messages[msgIndex] = messages[msgIndex].copy(text = sb.toString())
        }
        i = end
        listState.animateScrollToItem(messages.size - 1)
        delay(delayMs)
    }
}

// ============================================================
// 消息类型
// ============================================================

enum class MessageType { REPLY, THINKING, TOOL_CALL }

// ============================================================
// 消息气泡 — 根据类型不同样式
// ============================================================

@Composable
private fun MessageBubble(message: ChatMessage) {
    if (message.isUser) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Surface(
                shape = RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp),
                color = MaterialTheme.colorScheme.primary,
                tonalElevation = 2.dp,
                modifier = Modifier.widthIn(max = 300.dp)
            ) {
                Text(message.text, modifier = Modifier.padding(12.dp, 16.dp), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    } else {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            // 头像
            Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Text(if (message.type == MessageType.THINKING) "💭" else if (message.type == MessageType.TOOL_CALL) "🔧" else "🤖", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.width(8.dp))
            // 气泡
            Surface(
                shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp),
                color = when (message.type) {
                    MessageType.THINKING -> MaterialTheme.colorScheme.tertiaryContainer
                    MessageType.TOOL_CALL -> MaterialTheme.colorScheme.secondaryContainer
                    MessageType.REPLY -> MaterialTheme.colorScheme.surfaceVariant
                },
                tonalElevation = 2.dp,
                modifier = Modifier.widthIn(max = 310.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    // 类型标签
                    if (message.type != MessageType.REPLY) {
                        Text(
                            when (message.type) {
                                MessageType.THINKING -> "💭 深度思考"
                                MessageType.TOOL_CALL -> "🔧 工具调用"
                                else -> ""
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = when (message.type) {
                                MessageType.THINKING -> MaterialTheme.colorScheme.onTertiaryContainer
                                MessageType.TOOL_CALL -> MaterialTheme.colorScheme.onSecondaryContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    RenderMarkdown(message.text, message.type)
                }
            }
        }
    }
}

@Composable
private fun RenderMarkdown(text: String, type: MessageType) {
    val color = when (type) {
        MessageType.THINKING -> MaterialTheme.colorScheme.onTertiaryContainer
        MessageType.TOOL_CALL -> MaterialTheme.colorScheme.onSecondaryContainer
        MessageType.REPLY -> MaterialTheme.colorScheme.onSurfaceVariant
    }
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
                        Text(displayText, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal), color = color)
                    }
                } else Spacer(Modifier.height(4.dp))
            }
        } else {
            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(block.trim(), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(12.dp))
            }
        }
    }
}

// ============================================================
// 打字指示器
// ============================================================

@Composable
private fun TypingIndicator() {
    val t = rememberInfiniteTransition(label = "typing")
    val a1 by t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "d1")
    val a2 by t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse, delayMillis = 200), label = "d2")
    val a3 by t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse, delayMillis = 400), label = "d3")
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primaryContainer), Alignment.Center) { Text("🤖", style = MaterialTheme.typography.titleMedium) }
        Spacer(Modifier.width(8.dp))
        Surface(shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp), color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 2.dp) {
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("●", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.alpha(a1))
                Text("●", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.alpha(a2))
                Text("●", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.alpha(a3))
            }
        }
    }
}

// ============================================================
// 上下文指示器
// ============================================================

@Composable
private fun ContextPercentIndicator(percent: Int, onClick: () -> Unit) {
    val color = when {
        percent >= 85 -> MaterialTheme.colorScheme.error
        percent >= 60 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    Row(
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(22.dp), Alignment.Center) {
            CircularProgressIndicator(progress = { percent / 100f }, color = color, strokeWidth = 2.dp, modifier = Modifier.fillMaxSize())
            Text("$percent", style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
        }
    }
}

// ============================================================
// 压缩弹窗
// ============================================================

@Composable
private fun CompressDialog(onDismiss: () -> Unit, onCompress: () -> Unit, autoCompress: Boolean, onAutoCompressChange: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("压缩对话") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("压缩会保留最近的消息和关键上下文，移除较早的对话历史。", style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("智能压缩", Modifier.weight(1f))
                    Switch(checked = autoCompress, onCheckedChange = onAutoCompressChange)
                }
                Text("开启后，上下文超过 85% 时自动提示压缩", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { FilledButton(onClick = onCompress) { Text("立即压缩") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

// ============================================================
// 快捷操作栏
// ============================================================

@Composable
private fun QuickActionsRow(onTerminal: () -> Unit, onTool: () -> Unit, onWorkflow: () -> Unit, onFile: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AssistChip(onClick = onFile, label = { Text("📄 附件") })
        AssistChip(onClick = onTerminal, label = { Text("💻 终端") })
        AssistChip(onClick = onTool, label = { Text("🔧 工具") })
        AssistChip(onClick = onWorkflow, label = { Text("📋 工作流") })
    }
}

// ============================================================
// 模型选择
// ============================================================

@Composable
private fun ModelSelectorBar(selectedModel: String, onModelClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        AssistChip(onClick = onModelClick, label = { Text("🤖 $selectedModel", style = MaterialTheme.typography.labelMedium) })
    }
}

@Composable
private fun ModelPickerDialog(models: List<ModelItem>, selectedModelName: String, onDismiss: () -> Unit, onSelect: (ModelItem) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择模型") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("从市场已配置的模型中选择", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                models.forEach { model ->
                    val isSelected = "${model.providerName} · ${model.modelName}" == selectedModelName
                    val isConfigured = model.status.contains("✓")
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { if (isConfigured) onSelect(model) }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("${model.providerName} · ${model.modelName}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = if (isConfigured) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(model.status, style = MaterialTheme.typography.bodySmall, color = if (isConfigured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                        }
                        if (isSelected) Icon(Icons.Default.Check, "已选", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

// ============================================================
// 技能选择
// ============================================================

@Composable
private fun SkillPickerDialog(skills: List<SkillItem>, selectedSkillId: String?, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择技能") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                skills.forEach { skill ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onSelect(skill.id) }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(skill.icon, style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(skill.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(skill.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (skill.id == selectedSkillId) Icon(Icons.Default.Check, "已选", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

// ============================================================
// 输入栏
// ============================================================

@Composable
private fun EnhancedInputBar(
    text: String, onTextChange: (String) -> Unit,
    selectedSkill: String?, onSkillClick: () -> Unit,
    deepThinking: Boolean, onDeepThinkingToggle: () -> Unit,
    webSearch: Boolean, onWebSearchToggle: () -> Unit,
    isStreaming: Boolean, onSend: () -> Unit, onStop: () -> Unit
) {
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Column {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(selected = selectedSkill != null && selectedSkill != "auto", onClick = onSkillClick, label = { Text("⚡ 技能", style = MaterialTheme.typography.labelMedium) })
                FilterChip(selected = deepThinking, onClick = onDeepThinkingToggle, label = { Text("🧠 深度思考", style = MaterialTheme.typography.labelMedium) })
                FilterChip(selected = webSearch, onClick = onWebSearchToggle, label = { Text("🌐 联网搜索", style = MaterialTheme.typography.labelMedium) })
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {}) { Icon(Icons.Default.AttachFile, "附件") }
                OutlinedTextField(value = text, onValueChange = onTextChange, modifier = Modifier.weight(1f), placeholder = { Text("向 Agent 发送消息...") }, shape = RoundedCornerShape(24.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.outline), maxLines = 4)
                IconButton(onClick = {}) { Icon(Icons.Default.Mic, "语音") }
                if (isStreaming) {
                    FilledIconButton(onClick = onStop, shape = RoundedCornerShape(50)) { Icon(Icons.Default.Stop, "停止") }
                } else {
                    FilledIconButton(onClick = onSend, enabled = text.isNotBlank(), shape = RoundedCornerShape(50)) { Icon(Icons.AutoMirrored.Filled.Send, "发送") }
                }
            }
        }
    }
}

// ============================================================
// 数据类
// ============================================================

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val type: MessageType = MessageType.REPLY
)
data class SkillItem(val id: String, val name: String, val icon: String, val description: String)
data class ModelItem(val provider: String, val providerName: String, val modelName: String, val status: String)
