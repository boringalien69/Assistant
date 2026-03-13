package com.assistant.feature.aichat.ui.model

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.assistant.core.database.entity.DownloadTaskEntity
import com.assistant.core.theme.*
import com.assistant.feature.aichat.data.ModelCatalog

@Composable
fun ModelPickerScreen(
    onModelReady: () -> Unit,
    viewModel: ModelPickerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = AssistantTheme.colors
    val typography = AssistantTheme.typography

    var customUrl by remember { mutableStateOf("") }
    var customName by remember { mutableStateOf("") }
    var showCustomInput by remember { mutableStateOf(false) }
    var customError by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .scanlineOverlay()
    ) {
        // Geo background
        Box(
            modifier = Modifier
                .size(350.dp)
                .align(Alignment.TopEnd)
                .offset(x = 100.dp, y = (-80).dp)
                .drawBehind {
                    drawCircle(colors.accent.copy(alpha = 0.04f))
                }
        )

        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.background)
                    .drawBehind {
                        drawLine(colors.borderHard, Offset(0f, size.height), Offset(size.width, size.height), 1f)
                    }
                    .padding(24.dp)
                    .cornerBrackets(colors.accentText, size = 16.dp),
            ) {
                Column {
                    Text("MODEL ACQUISITION", style = typography.display, color = colors.textPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "SELECT A MODEL TO INITIALIZE THE ASSISTANT. BEST FIT FOR YOUR DEVICE IS HIGHLIGHTED.",
                        style = typography.hudSmall,
                        color = colors.textMuted,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "AVAILABLE RAM: ${state.availableRamBytes / 1_000_000_000L} GB",
                        style = typography.hud,
                        color = colors.accentText,
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Catalog entries
                items(ModelCatalog.entries) { entry ->
                    val isBestFit = entry.id == state.bestFitId
                    val activeDownload = state.activeDownloads.firstOrNull {
                        it.url == entry.downloadUrl
                    }
                    CatalogEntryCard(
                        entry         = entry,
                        isBestFit     = isBestFit,
                        activeDownload = activeDownload,
                        onDownload    = { viewModel.downloadCatalogEntry(entry) },
                    )
                }

                // HUD divider
                item {
                    HudDivider("CUSTOM DIRECT LINK", colors.textMuted)
                }

                // Custom URL input
                item {
                    CustomUrlSection(
                        url           = customUrl,
                        name          = customName,
                        error         = customError,
                        expanded      = showCustomInput,
                        onToggle      = { showCustomInput = !showCustomInput },
                        onUrlChange   = { customUrl = it; customError = "" },
                        onNameChange  = { customName = it },
                        onDownload    = {
                            val ok = viewModel.downloadCustomUrl(customUrl, customName.ifBlank { "Custom Model" })
                            if (!ok) customError = "INVALID — URL MUST END IN .gguf"
                            else { customUrl = ""; customName = "" }
                        },
                    )
                }

                // Skip if downloads are active
                if (state.activeDownloads.any { it.status == "COMPLETE" }) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.accentGlow)
                                .border(1.dp, colors.accentText)
                                .clickable(onClick = onModelReady)
                                .padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("[ PROCEED TO ASSISTANT ]", style = typography.subhead, color = colors.accentText)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogEntryCard(
    entry: ModelCatalog.CatalogEntry,
    isBestFit: Boolean,
    activeDownload: DownloadTaskEntity?,
    onDownload: () -> Unit,
) {
    val colors = AssistantTheme.colors
    val typography = AssistantTheme.typography

    val borderColor = if (isBestFit) colors.accentText else colors.border

    // Pulsing border animation for best fit
    val infiniteTransition = rememberInfiniteTransition(label = "bestfit")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = if (isBestFit) 1.0f else 1.0f,
        targetValue  = if (isBestFit) 0.4f else 1.0f,
        animationSpec = infiniteRepeatable(tween(1200, easing = EaseInOut), RepeatMode.Reverse),
        label = "pulse",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isBestFit) colors.accentGlow else colors.backgroundPanel
            )
            .border(
                width = if (isBestFit) 2.dp else 1.dp,
                color = borderColor.copy(alpha = if (isBestFit) pulseAlpha else 1f),
            )
            .padding(16.dp)
            .cornerBrackets(borderColor, size = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(entry.displayName.uppercase(), style = typography.subhead, color = colors.textPrimary)
                    if (isBestFit) {
                        Text("BEST FIT", style = typography.hudSmall, color = colors.accentText,
                            modifier = Modifier.background(colors.accentGlow).padding(horizontal = 4.dp))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("${entry.quantization} — ${entry.ramTierLabel}", style = typography.hud, color = colors.accentText)
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatChip("SIZE", "${entry.fileSizeBytes / 1_000_000_000L}.${(entry.fileSizeBytes % 1_000_000_000L) / 100_000_000L} GB")
            StatChip("RAM", "${entry.ramRequiredBytes / 1_000_000_000L}.${(entry.ramRequiredBytes % 1_000_000_000L) / 100_000_000L} GB")
            StatChip("SPEED", entry.speedEstimate)
        }

        Spacer(Modifier.height(12.dp))

        // Download state
        when {
            activeDownload?.status == "COMPLETE" -> {
                Text("DOWNLOAD COMPLETE", style = typography.hud, color = colors.accent)
            }
            activeDownload?.status in listOf("ACTIVE", "QUEUED") -> {
                val pct = if ((activeDownload?.totalBytes ?: 0) > 0) {
                    (activeDownload!!.bytesDownloaded * 100 / activeDownload.totalBytes).toInt()
                } else 0
                Column {
                    Text("DOWNLOADING $pct%", style = typography.hud, color = colors.yellow)
                    LinearProgressIndicator(
                        progress   = { pct / 100f },
                        modifier   = Modifier.fillMaxWidth().padding(top = 4.dp),
                        color      = colors.yellow,
                        trackColor = colors.border,
                    )
                }
            }
            activeDownload?.status == "PAUSED" -> {
                Text(
                    "PAUSED — ${activeDownload.bytesDownloaded / 1_000_000L} MB / ${activeDownload.totalBytes / 1_000_000L} MB",
                    style = typography.hud, color = colors.orange,
                )
            }
            activeDownload?.status == "FAILED" -> {
                Text("LINK FAILED — RETRY?", style = typography.hud, color = colors.red)
                Spacer(Modifier.height(6.dp))
                DownloadButton("[ RETRY ]", colors.red, onDownload)
            }
            else -> {
                DownloadButton("[ DOWNLOAD ]", colors.accentText, onDownload)
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    val colors = AssistantTheme.colors
    val typography = AssistantTheme.typography
    Column {
        Text(label, style = typography.hudSmall, color = colors.textMuted)
        Text(value, style = typography.hud, color = colors.textPrimary)
    }
}

@Composable
private fun DownloadButton(label: String, color: com.assistant.core.theme.AssistantColors.() -> androidx.compose.ui.graphics.Color = { accent }, colorValue: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    val colors = AssistantTheme.colors
    val typography = AssistantTheme.typography
    Box(
        modifier = Modifier
            .border(1.dp, colorValue)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(label, style = typography.hud, color = colorValue)
    }
}

@Composable
private fun CustomUrlSection(
    url: String, name: String, error: String, expanded: Boolean,
    onToggle: () -> Unit, onUrlChange: (String) -> Unit,
    onNameChange: (String) -> Unit, onDownload: () -> Unit,
) {
    val colors = AssistantTheme.colors
    val typography = AssistantTheme.typography

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.backgroundPanel)
            .border(1.dp, colors.border)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("PASTE DIRECT .gguf URL", style = typography.hud, color = colors.accentText)
            Text(if (expanded) "[-]" else "[+]", style = typography.hud, color = colors.accentText)
        }

        if (expanded) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value        = url,
                onValueChange = onUrlChange,
                modifier     = Modifier.fillMaxWidth(),
                placeholder  = { Text("https://...model.gguf", style = typography.hud, color = colors.textMuted) },
                label        = { Text("DOWNLOAD URL", style = typography.hudSmall, color = colors.textMuted) },
                textStyle    = typography.hud.copy(color = colors.textPrimary),
                isError      = error.isNotBlank(),
                colors       = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accentText,
                    unfocusedBorderColor = colors.border,
                    errorBorderColor = colors.red,
                    cursorColor = colors.accentText,
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(2.dp),
                singleLine = true,
            )
            if (error.isNotBlank()) {
                Text(error, style = typography.hudSmall, color = colors.red)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value        = name,
                onValueChange = onNameChange,
                modifier     = Modifier.fillMaxWidth(),
                placeholder  = { Text("DISPLAY NAME", style = typography.hud, color = colors.textMuted) },
                textStyle    = typography.hud.copy(color = colors.textPrimary),
                colors       = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accentText,
                    unfocusedBorderColor = colors.border,
                    cursorColor = colors.accentText,
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(2.dp),
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .background(colors.accentText)
                    .clickable(onClick = onDownload)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text("[ INITIATE DOWNLOAD ]", style = typography.hud, color = colors.background)
            }
        }
    }
}
