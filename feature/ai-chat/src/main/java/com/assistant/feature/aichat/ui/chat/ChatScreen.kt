package com.assistant.feature.aichat.ui.chat

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.assistant.core.database.entity.MessageEntity
import com.assistant.core.theme.*
import com.assistant.feature.aichat.ui.components.DynamicIcon
import io.noties.markwon.Markwon

@Composable
fun ChatScreen(
    onBack: () -> Unit,
    onOpenModelLibrary: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = AssistantTheme.colors
    val typography = AssistantTheme.typography
    val listState = rememberLazyListState()

    var inputText by remember { mutableStateOf("") }

    // Auto-scroll to bottom on new messages
    LaunchedEffect(state.messages.size, state.streamingText) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(
                if (state.isStreaming) state.messages.size else state.messages.size - 1
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .scanlineOverlay()
    ) {
        // Background decoration
        Box(
            modifier = Modifier
                .size(240.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 60.dp, y = 60.dp)
                .drawBehind {
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(size.width / 2, 0f)
                        lineTo(size.width, size.height / 2)
                        lineTo(size.width / 2, size.height)
                        lineTo(0f, size.height / 2)
                        close()
                    }
                    drawPath(path, colors.purple.copy(alpha = 0.04f))
                }
        )

        Column(modifier = Modifier.fillMaxSize()) {
            // ── HUD TOP BAR ───────────────────────────────────────────────
            ChatTopBar(
                modelName  = state.modelName,
                status     = state.status,
                isStreaming = state.isStreaming,
                onBack     = onBack,
                onModelTap = onOpenModelLibrary,
            )

            // ── MESSAGE LIST ──────────────────────────────────────────────
            LazyColumn(
                state              = listState,
                modifier           = Modifier.weight(1f),
                contentPadding     = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                items(state.messages.filter { it.role != "system" }, key = { it.id }) { msg ->
                    MessageRow(
                        msg      = msg,
                        aiName   = state.persona?.aiName ?: "ASSISTANT",
                        userName = state.persona?.userName ?: "USER",
                        accentColor = colors.accentText,
                        isActive = false,
                    )
                }

                // Streaming bubble
                if (state.isStreaming && state.streamingText.isNotBlank()) {
                    item(key = "streaming") {
                        StreamingBubble(
                            text       = state.streamingText,
                            aiName     = state.persona?.aiName ?: "ASSISTANT",
                            accentColor = colors.accentText,
                        )
                    }
                }
            }

            // ── INPUT BAR ─────────────────────────────────────────────────
            ChatInputBar(
                text        = inputText,
                onTextChange = { inputText = it },
                isStreaming  = state.isStreaming,
                noModel     = state.status == InferenceStatus.NO_MODEL,
                onSend      = {
                    if (inputText.isNotBlank()) {
                        viewModel.sendMessage(inputText)
                        inputText = ""
                    }
                },
                onHalt      = viewModel::haltInference,
                onModelTap  = onOpenModelLibrary,
            )
        }
    }
}

@Composable
private fun ChatTopBar(
    modelName: String,
    status: InferenceStatus,
    isStreaming: Boolean,
    onBack: () -> Unit,
    onModelTap: () -> Unit,
) {
    val colors = AssistantTheme.colors
    val typography = AssistantTheme.typography

    val statusColor = when (status) {
        InferenceStatus.IDLE       -> colors.accent
        InferenceStatus.PROCESSING -> colors.purple
        InferenceStatus.ERROR      -> colors.red
        InferenceStatus.NO_MODEL   -> colors.red
    }
    val statusLabel = when (status) {
        InferenceStatus.IDLE       -> "READY"
        InferenceStatus.PROCESSING -> "PROCESSING //"
        InferenceStatus.ERROR      -> "ERROR"
        InferenceStatus.NO_MODEL   -> "NO MODEL"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(colors.background)
            .drawBehind {
                drawLine(colors.borderHard, Offset(0f, size.height), Offset(size.width, size.height), 1f)
            }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Back
        Text(
            text     = "<",
            style    = typography.headline,
            color    = colors.accentText,
            modifier = Modifier.clickable(onClick = onBack).padding(4.dp),
        )

        // Model name (center)
        Text(
            text     = modelName.uppercase(),
            style    = typography.hud,
            color    = colors.accentText,
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onModelTap),
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
        )

        // Status
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PulseDot(color = statusColor, size = 6.dp)
            Text(statusLabel, style = typography.hudSmall, color = statusColor)
        }
    }
}

@Composable
private fun MessageRow(
    msg: MessageEntity,
    aiName: String,
    userName: String,
    accentColor: Color,
    isActive: Boolean,
) {
    val colors = AssistantTheme.colors
    val typography = AssistantTheme.typography
    val isAi = msg.role == "assistant"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Header: icon + name + timestamp
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DynamicIcon(
                name = if (isAi) aiName else userName,
                size = 28.dp,
                isActive = isAi && isActive,
                backgroundColor = if (isAi) accentColor else colors.backgroundPanel2,
            )
            Text(
                text  = if (isAi) aiName.uppercase() else userName.uppercase(),
                style = typography.hudSmall,
                color = if (isAi) accentColor else colors.textMuted,
            )
            Text(
                text  = formatTimestamp(msg.timestamp),
                style = typography.hudSmall,
                color = colors.textMuted,
            )
            if (msg.isPartial) {
                Text("HALTED", style = typography.hudSmall, color = colors.orange)
            }
        }

        Spacer(Modifier.height(4.dp))

        // Message bubble — max 90% of screen width
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .then(if (!isAi) Modifier.align(Alignment.End) else Modifier)
                .background(
                    if (isAi) colors.backgroundPanel else colors.backgroundPanel2
                )
                .padding(10.dp)
        ) {
            MarkdownText(
                text  = msg.content,
                color = colors.textPrimary,
            )
        }
    }
}

@Composable
private fun StreamingBubble(
    text: String,
    aiName: String,
    accentColor: Color,
) {
    val colors = AssistantTheme.colors
    val typography = AssistantTheme.typography

    // Cursor blink
    val blink by rememberInfiniteTransition(label = "cursor").animateFloat(
        initialValue = 1f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(650, easing = LinearEasing), RepeatMode.Reverse),
        label = "blink",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DynamicIcon(name = aiName, size = 28.dp, isActive = true, backgroundColor = accentColor)
            Text(aiName.uppercase(), style = typography.hudSmall, color = accentColor)
            PulseDot(color = colors.purple, size = 5.dp)
            Text("PROCESSING //", style = typography.hudSmall, color = colors.purple)
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .background(colors.backgroundPanel)
                .padding(10.dp)
        ) {
            Text(
                text  = text + if (blink > 0.5f) "_" else " ",
                style = typography.body,
                color = colors.textPrimary,
            )
        }
    }
}

@Composable
private fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    isStreaming: Boolean,
    noModel: Boolean,
    onSend: () -> Unit,
    onHalt: () -> Unit,
    onModelTap: () -> Unit,
) {
    val colors = AssistantTheme.colors
    val typography = AssistantTheme.typography

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.backgroundPanel)
            .drawBehind {
                drawLine(colors.borderHard, Offset(0f, 0f), Offset(size.width, 0f), 1f)
            }
            .padding(12.dp)
    ) {
        if (noModel) {
            // No model overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onModelTap)
                    .background(colors.backgroundPanel2)
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "NO MODEL LOADED — SELECT FILE TO INITIALIZE",
                    style = typography.hud,
                    color = colors.textMuted,
                )
            }
        } else {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value        = text,
                    onValueChange = onTextChange,
                    enabled      = !isStreaming,
                    modifier     = Modifier.weight(1f),
                    placeholder  = {
                        Text("ENTER COMMAND_", style = typography.hud, color = colors.textMuted)
                    },
                    textStyle    = typography.body.copy(color = colors.textPrimary),
                    colors       = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = colors.accentText,
                        unfocusedBorderColor = colors.border,
                        disabledBorderColor  = colors.border.copy(alpha = 0.4f),
                        cursorColor          = colors.accentText,
                    ),
                    shape        = androidx.compose.foundation.shape.RoundedCornerShape(2.dp),
                    maxLines     = 6,
                )

                // Send / Halt button
                if (isStreaming) {
                    Box(
                        modifier = Modifier
                            .background(colors.redGlow)
                            .border(1.dp, colors.red)
                            .clickable(onClick = onHalt)
                            .padding(horizontal = 14.dp, vertical = 16.dp),
                    ) {
                        Text("[ HALT ]", style = typography.hud, color = colors.red)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .background(colors.accent)
                            .clickable(enabled = text.isNotBlank(), onClick = onSend)
                            .padding(horizontal = 14.dp, vertical = 16.dp),
                    ) {
                        Text("[ EXECUTE ]", style = typography.hud, color = colors.background)
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkdownText(text: String, color: Color) {
    // Markwon — renders markdown in Android TextView
    AndroidView(
        factory = { ctx ->
            val markwon = Markwon.builder(ctx)
                .usePlugin(io.noties.markwon.ext.strikethrough.StrikethroughPlugin.create())
                .usePlugin(io.noties.markwon.ext.tables.TablePlugin.create(ctx))
                .build()
            TextView(ctx).apply {
                setTextColor(color.hashCode())
                movementMethod = LinkMovementMethod.getInstance()
                textSize = 14f
            }.also { tv ->
                markwon.setMarkdown(tv, text)
            }
        },
        update = { tv ->
            val markwon = Markwon.create(tv.context)
            markwon.setMarkdown(tv, text)
        }
    )
}

private fun formatTimestamp(epochMs: Long): String {
    val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US)
    return sdf.format(java.util.Date(epochMs))
}
