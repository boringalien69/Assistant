package com.assistant.feature.aichat.ui.chatlist

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.assistant.core.database.entity.ConversationEntity
import com.assistant.core.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ChatListScreen(
    onChatClick: (String) -> Unit,
    onNewChat: () -> Unit,
    onOpenModelLibrary: () -> Unit,
    viewModel: ChatListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = AssistantTheme.colors
    val typography = AssistantTheme.typography

    // Error snackbar
    state.error?.let { error ->
        LaunchedEffect(error) {
            viewModel.clearError()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .scanlineOverlay()
    ) {
        // Background geo shape
        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.TopEnd)
                .offset(x = 80.dp, y = (-60).dp)
                .drawBehind {
                    drawRect(colors.accent.copy(alpha = 0.04f))
                }
        )
        Box(
            modifier = Modifier
                .size(180.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-40).dp, y = 40.dp)
                .drawBehind {
                    // Rotated diamond
                    val path = androidx.compose.ui.graphics.Path().apply {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        moveTo(cx, 0f)
                        lineTo(size.width, cy)
                        lineTo(cx, size.height)
                        lineTo(0f, cy)
                        close()
                    }
                    drawPath(path, colors.purple.copy(alpha = 0.05f))
                }
        )

        Column(modifier = Modifier.fillMaxSize()) {
            // ── TOP BAR ──────────────────────────────────────────────────────────────────
            HudTopBar(
                isModelLoaded   = state.isModelLoaded,
                activeModelName = state.activeModelName,
                onModelTap      = onOpenModelLibrary,
            )

            // ── CHAT LIST ────────────────────────────────────────────────────────────────
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 88.dp),
            ) {
                // PINNED ZONE
                if (state.pinned.isNotEmpty()) {
                    item {
                        ZoneHeader(
                            label = "// PINNED — MAX 3 — APPEARS FIRST",
                            color = colors.accentText,
                        )
                    }
                    items(state.pinned, key = { it.id }) { conv ->
                        PinnedChatItem(
                            conv        = conv,
                            accentColor = colors.accentText,
                            onClick     = { onChatClick(conv.id) },
                            onUnpin     = { viewModel.unpinChat(conv.id) },
                            onRename    = { viewModel.renameChat(conv.id, it) },
                            onDelete    = { viewModel.deleteChat(conv.id) },
                        )
                    }
                }

                // TRENDING ZONE
                state.trending?.let { conv ->
                    item {
                        ZoneHeader(
                            label = "// TRENDING — HIGHEST SCORE NON-PINNED",
                            color = colors.purple,
                        )
                    }
                    item(key = "trending_${conv.id}") {
                        TrendingChatItem(
                            conv     = conv,
                            onClick  = { onChatClick(conv.id) },
                            onPin    = { viewModel.pinChat(conv.id) },
                            onRename = { viewModel.renameChat(conv.id, it) },
                            onDelete = { viewModel.deleteChat(conv.id) },
                        )
                    }
                }

                // ALL THREADS ZONE
                if (state.all.isNotEmpty()) {
                    item {
                        ZoneHeader(
                            label = "// ALL THREADS",
                            color = colors.textMuted,
                        )
                    }
                    items(
                        state.all.filter { it.id != state.trending?.id },
                        key = { it.id }
                    ) { conv ->
                        val isExpiring = state.expiringChats.any { it.id == conv.id }
                        RegularChatItem(
                            conv        = conv,
                            isExpiring  = isExpiring,
                            onClick     = { onChatClick(conv.id) },
                            onPin       = { viewModel.pinChat(conv.id) },
                            onRename    = { viewModel.renameChat(conv.id, it) },
                            onDelete    = { viewModel.deleteChat(conv.id) },
                            onKeep      = { viewModel.keepChat(conv.id) },
                        )
                    }
                }

                // Empty state
                if (state.pinned.isEmpty() && state.trending == null && state.all.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(52.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "NO DATA — AWAITING INPUT",
                                style = typography.hudSmall,
                                color = colors.textMuted,
                            )
                        }
                    }
                }
            }
        }

        // ── NEW SESSION BUTTON ───────────────────────────────────────────────────────────
        HudNewChatButton(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            onClick  = onNewChat,
        )

        // ── ERROR BANNER ─────────────────────────────────────────────────────────────────
        state.error?.let { err ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 56.dp, start = 16.dp, end = 16.dp)
                    .background(colors.redGlow)
                    .border(1.dp, colors.red)
                    .padding(12.dp)
            ) {
                Text(err, style = typography.hud, color = colors.red)
            }
        }
    }
}

@Composable
private fun HudTopBar(
    isModelLoaded: Boolean,
    activeModelName: String,
    onModelTap: () -> Unit,
) {
    val colors = AssistantTheme.colors
    val typography = AssistantTheme.typography

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(colors.background)
            .border(width = 1.dp, color = colors.borderHard,
                shape = androidx.compose.ui.graphics.RectangleShape)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ASSISTANT", style = typography.subhead, color = colors.textPrimary)
            Box(modifier = Modifier.width(1.dp).height(16.dp).background(colors.borderHard))
            Text("v1.0", style = typography.hudSmall, color = colors.textMuted)
        }

        Row(
            modifier = Modifier
                .clickable(onClick = onModelTap)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PulseDot(
                color = if (isModelLoaded) colors.accent else colors.textMuted,
                size  = 6.dp,
            )
            Text(
                text  = if (isModelLoaded) activeModelName.uppercase() else "NO MODEL",
                style = typography.hudSmall,
                color = if (isModelLoaded) colors.accentText else colors.textMuted,
            )
        }
    }
}

@Composable
private fun ZoneHeader(label: String, color: Color) {
    val colors = AssistantTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.backgroundPanel2)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Text(label, style = AssistantTheme.typography.hudSmall, color = color)
    }
}

@Composable
private fun PinnedChatItem(
    conv: ConversationEntity,
    accentColor: Color,
    onClick: () -> Unit,
    onUnpin: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val colors = AssistantTheme.colors
    var showMenu by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.backgroundPanel)
            .border(
                width = 3.dp,
                color = accentColor,
                shape = androidx.compose.ui.graphics.RectangleShape
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { showMenu = true },
                )
            }
            .drawBehind {
                val lineColor = accentColor.copy(alpha = 0.05f)
                drawLine(lineColor, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 1f)
                drawLine(lineColor, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1f)
            }
            .cornerBrackets(accentColor.copy(alpha = 0.8f), size = 10.dp, strokeWidth = 1.dp)
    ) {
        ChatItemContent(
            conv          = conv,
            badge         = "PINNED",
            badgeColor    = accentColor,
            titleColor    = accentColor,
            metaColor     = accentColor.copy(alpha = 0.65f),
            dotColor      = accentColor,
            showMenu      = showMenu,
            onDismissMenu = { showMenu = false },
            menuItems     = listOf(
                ContextAction("[ UNPIN ]", colors.yellow) { onUnpin() },
                ContextAction("[ RENAME ]", colors.accentText) { showRename = true },
                ContextAction("[ DELETE ]", colors.red) { onDelete() },
            ),
        )
    }

    if (showRename) {
        RenameDialog(current = conv.title, onConfirm = { onRename(it); showRename = false }, onDismiss = { showRename = false })
    }
}

@Composable
private fun TrendingChatItem(
    conv: ConversationEntity,
    onClick: () -> Unit,
    onPin: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val colors = AssistantTheme.colors
    var showMenu by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.backgroundPanel)
            .borderStart(3.dp, colors.purple)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { showMenu = true },
                )
            }
    ) {
        ChatItemContent(
            conv          = conv,
            badge         = "TRENDING",
            badgeColor    = colors.purple,
            titleColor    = colors.textPrimary,
            metaColor     = colors.textMuted,
            dotColor      = colors.purple,
            showMenu      = showMenu,
            onDismissMenu = { showMenu = false },
            menuItems     = listOf(
                ContextAction("[ PIN ]", colors.yellow) { onPin() },
                ContextAction("[ RENAME ]", colors.accentText) { showRename = true },
                ContextAction("[ DELETE ]", colors.red) { onDelete() },
            ),
        )
    }

    if (showRename) {
        RenameDialog(current = conv.title, onConfirm = { onRename(it); showRename = false }, onDismiss = { showRename = false })
    }
}

@Composable
private fun RegularChatItem(
    conv: ConversationEntity,
    isExpiring: Boolean,
    onClick: () -> Unit,
    onPin: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onKeep: () -> Unit,
) {
    val colors = AssistantTheme.colors
    var showMenu by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }

    val menuItems = buildList {
        add(ContextAction("[ PIN ]", colors.yellow) { onPin() })
        add(ContextAction("[ RENAME ]", colors.accentText) { showRename = true })
        add(ContextAction("[ DELETE ]", colors.red) { onDelete() })
        if (isExpiring) add(ContextAction("[ KEEP ]", colors.orange) { onKeep() })
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .then(
                if (isExpiring) Modifier.borderStart(3.dp, colors.orange)
                else Modifier
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { showMenu = true },
                )
            }
    ) {
        ChatItemContent(
            conv          = conv,
            badge         = if (isExpiring) "EXPIRING" else null,
            badgeColor    = if (isExpiring) colors.orange else colors.textMuted,
            titleColor    = colors.textPrimary,
            metaColor     = colors.textMuted,
            dotColor      = colors.textMuted,
            showMenu      = showMenu,
            onDismissMenu = { showMenu = false },
            menuItems     = menuItems,
            expiryWarning = if (isExpiring) "EXPIRING — TAP TO KEEP" else null,
        )
    }

    if (showRename) {
        RenameDialog(current = conv.title, onConfirm = { onRename(it); showRename = false }, onDismiss = { showRename = false })
    }
}

@Composable
private fun ChatItemContent(
    conv: ConversationEntity,
    badge: String?,
    badgeColor: Color,
    titleColor: Color,
    metaColor: Color,
    dotColor: Color,
    showMenu: Boolean,
    onDismissMenu: () -> Unit,
    menuItems: List<ContextAction>,
    expiryWarning: String? = null,
) {
    val colors = AssistantTheme.colors
    val typography = AssistantTheme.typography

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .borderBottom(1.dp, colors.border),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .border(1.dp, colors.border),
            contentAlignment = Alignment.Center,
        ) {
            Box(modifier = Modifier.size(8.dp).background(dotColor))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = conv.title.uppercase(),
                style = typography.subhead,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(text = formatMeta(conv), style = typography.hudSmall, color = metaColor)
            expiryWarning?.let { Text(it, style = typography.hudSmall, color = colors.orange) }
        }

        Column(horizontalAlignment = Alignment.End) {
            badge?.let { Text(it, style = typography.hudSmall, color = badgeColor) }
            Text("${conv.messageCount} MSGS", style = typography.hudSmall, color = metaColor)
        }
    }

    if (showMenu) {
        DropdownMenu(
            expanded         = showMenu,
            onDismissRequest = onDismissMenu,
            modifier         = Modifier.background(colors.backgroundPanel2),
        ) {
            menuItems.forEach { action ->
                DropdownMenuItem(
                    text    = { Text(action.label, style = typography.hud, color = action.color) },
                    onClick = { action.onClick(); onDismissMenu() },
                )
            }
        }
    }
}

private fun formatMeta(conv: ConversationEntity): String {
    val now = System.currentTimeMillis()
    val diff = now - conv.lastActiveAt
    val timeStr = when {
        diff < 60_000     -> "JUST NOW"
        diff < 3_600_000  -> "${diff / 60_000} MIN AGO"
        diff < 86_400_000 -> "${diff / 3_600_000} HRS AGO"
        else              -> "${diff / 86_400_000} DAYS AGO"
    }
    return "LAST ACTIVE: $timeStr"
}

data class ContextAction(val label: String, val color: Color, val onClick: () -> Unit)

@Composable
private fun RenameDialog(current: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = AssistantTheme.colors
    val typography = AssistantTheme.typography
    var text by remember { mutableStateOf(current) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = colors.backgroundPanel2,
        shape            = androidx.compose.foundation.shape.RoundedCornerShape(2.dp),
        title            = { Text("RENAME THREAD", style = typography.subhead, color = colors.accentText) },
        text = {
            OutlinedTextField(
                value         = text,
                onValueChange = { if (it.length <= 60) text = it },
                textStyle     = typography.hud.copy(color = colors.textPrimary),
                singleLine    = true,
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = colors.accentText,
                    unfocusedBorderColor = colors.border,
                    cursorColor          = colors.accentText,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text("[ CONFIRM ]", style = typography.hud, color = colors.accentText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("[ CANCEL ]", style = typography.hud, color = colors.textMuted)
            }
        },
    )
}

@Composable
private fun HudNewChatButton(modifier: Modifier, onClick: () -> Unit) {
    val colors = AssistantTheme.colors
    val typography = AssistantTheme.typography

    Box(
        modifier = modifier
            .background(colors.accent)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .cornerBrackets(Color.White.copy(alpha = 0.3f), size = 8.dp)
    ) {
        Text("[ NEW SESSION ]", style = typography.hud, color = colors.background)
    }
}

// ── Named extension helpers (avoid shadowing Compose's border() overloads) ───

private fun Modifier.borderStart(width: Dp, color: Color): Modifier = this.drawBehind {
    drawLine(color, Offset(width.toPx() / 2, 0f), Offset(width.toPx() / 2, size.height), width.toPx())
}

private fun Modifier.borderBottom(width: Dp, color: Color): Modifier = this.drawBehind {
    drawLine(color, Offset(0f, size.height), Offset(size.width, size.height), width.toPx())
}
