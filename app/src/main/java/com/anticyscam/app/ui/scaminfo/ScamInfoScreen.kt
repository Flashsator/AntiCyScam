package com.anticyscam.app.ui.scaminfo

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.anticyscam.app.domain.model.ChannelIcon
import com.anticyscam.app.domain.model.ChannelType
import com.anticyscam.app.domain.model.EmergencyChannel
import com.anticyscam.app.domain.model.ScamCategory
import com.anticyscam.app.domain.model.ScamSeverity
import com.anticyscam.app.domain.model.ScamTactic
import com.anticyscam.app.domain.recognition.RecognitionMode
import com.anticyscam.app.ui.recognition.RecognitionActivity
import com.anticyscam.app.ui.theme.AlertYellow
import com.anticyscam.app.ui.theme.DividerGray
import com.anticyscam.app.ui.theme.SurfaceBlack
import com.anticyscam.app.ui.theme.SurfaceDim
import com.anticyscam.app.ui.theme.SurfaceElevated
import com.anticyscam.app.ui.theme.TextDisabled
import com.anticyscam.app.ui.theme.TextPrimary
import com.anticyscam.app.ui.theme.TextSecondary
import com.anticyscam.app.ui.theme.WarningRed
import com.anticyscam.app.ui.theme.WarningRedLight

/**
 * 防詐專區 — driven by [ScamInfoRepository] reading
 * `assets/scam_catalog.json`. The JSON is the catalog "database" intended to
 * be refreshed by a future GitHub Actions cron; runtime makes no network
 * calls. Provides search, category filter, and direct dialers / links into
 * 165 / CIB / 警示帳戶查詢.
 */
@Composable
fun ScamInfoScreen(
    viewModel: ScamInfoViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    if (state.loading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = WarningRed)
        }
        return
    }

    val focusManager = LocalFocusManager.current
    val lazyListState = rememberLazyListState()
    LaunchedEffect(lazyListState.isScrollInProgress) {
        if (lazyListState.isScrollInProgress) {
            focusManager.clearFocus()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = lazyListState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            RecognitionToolbar(
                onScreenshot = { launchRecognition(context, RecognitionMode.SCREENSHOT) },
                onText = { launchRecognition(context, RecognitionMode.TEXT) },
                onVoice = { launchRecognition(context, RecognitionMode.VOICE) }
            )
        }
        item {
            ShareScamInfoBanner(onClick = { /* TODO: 詐騙資訊分享 — 之後實作 */ })
        }
        item { Header(state) }

        if (state.errorMessage != null) {
            item { ErrorBanner(state.errorMessage!!) }
        }

        item {
            SearchBar(
                value = state.searchQuery,
                onValueChange = viewModel::onSearchChanged
            )
        }
        item {
            CategoryChips(
                categories = state.categories,
                selected = state.selectedCategoryId,
                onSelected = viewModel::onCategorySelected
            )
        }

        val visible = state.visibleTactics
        if (visible.isEmpty()) {
            item { EmptyResult() }
        } else {
            items(items = visible, key = { it.id }) { tactic ->
                TacticCard(
                    tactic = tactic,
                    expanded = tactic.id in state.expandedTacticIds,
                    onToggle = { viewModel.onTacticExpanded(tactic.id) }
                )
            }
        }

        item { Spacer(Modifier.height(4.dp)) }
        item {
            SectionTitle("緊急通報管道")
        }
        items(items = state.channels, key = { it.id }) { channel ->
            ChannelCard(channel) { openChannel(context, channel) }
        }

        item { FooterDisclaimer(state.notice, state.source) }
    }
}

@Composable
private fun Header(state: ScamInfoState) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "整合全台常見詐騙手法與通報管道。遇到可疑情況，請立即撥打 165。",
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
        if (state.lastUpdated.isNotEmpty()) {
            Text(
                text = "資料版本 v${state.version}　更新日 ${state.lastUpdated}",
                color = TextDisabled,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDim),
        border = BorderStroke(1.dp, WarningRed)
    ) {
        Text(
            text = message,
            color = WarningRedLight,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = AlertYellow,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun ChannelCard(channel: EmergencyChannel, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
        border = BorderStroke(1.dp, DividerGray)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ChannelIconBadge(channel.icon)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = channel.label,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = channel.subtitle,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = channel.value,
                    color = WarningRedLight,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Icon(
                imageVector = if (channel.type == ChannelType.PHONE) Icons.Filled.Call else Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                tint = WarningRed
            )
        }
    }
}

@Composable
private fun ChannelIconBadge(icon: ChannelIcon) {
    val image = when (icon) {
        ChannelIcon.PHONE -> Icons.Filled.Call
        ChannelIcon.WEB -> Icons.Filled.Public
        ChannelIcon.MAIL -> Icons.Filled.Mail
        ChannelIcon.CHAT -> Icons.AutoMirrored.Filled.Chat
        ChannelIcon.BANK -> Icons.Filled.AccountBalance
    }
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(SurfaceDim, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = image, contentDescription = null, tint = AlertYellow)
    }
}

@Composable
private fun SearchBar(value: String, onValueChange: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text("搜尋手法、關鍵字（例如：LINE、紓困、ATM）", color = TextDisabled) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TextSecondary) },
        trailingIcon = if (value.isNotEmpty()) {
            {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "清除搜尋",
                    tint = TextSecondary,
                    modifier = Modifier.clickable {
                        onValueChange("")
                        focusManager.clearFocus()
                    }
                )
            }
        } else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedBorderColor = WarningRed,
            unfocusedBorderColor = DividerGray,
            cursorColor = WarningRed,
            focusedContainerColor = SurfaceDim,
            unfocusedContainerColor = SurfaceDim
        )
    )
}

@Composable
private fun CategoryChips(
    categories: List<ScamCategory>,
    selected: String?,
    onSelected: (String?) -> Unit
) {
    // Phase L1: phone testing showed users didn't realise more categories sit
    // off-screen. Overlay a chevron at the right edge as a swipe affordance,
    // and pad the LazyRow's trailing edge so the last chip isn't hidden under
    // the chevron pill.
    Box(modifier = Modifier.fillMaxWidth()) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(end = 32.dp)
        ) {
            item {
                FilterChip(
                    selected = selected == null,
                    onClick = { onSelected(null) },
                    label = { Text("全部") },
                    colors = chipColors()
                )
            }
            items(items = categories, key = { it.id }) { category ->
                FilterChip(
                    selected = selected == category.id,
                    onClick = { onSelected(if (selected == category.id) null else category.id) },
                    label = { Text(category.displayName) },
                    colors = chipColors()
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .background(SurfaceBlack.copy(alpha = 0.85f), CircleShape)
                .padding(2.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "向右滑動查看更多分類",
                tint = TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    containerColor = SurfaceDim,
    labelColor = TextSecondary,
    selectedContainerColor = WarningRedLight,
    selectedLabelColor = SurfaceBlack
)

@Composable
private fun TacticCard(tactic: ScamTactic, expanded: Boolean, onToggle: () -> Unit) {
    val borderColor = when (tactic.severity) {
        ScamSeverity.CRITICAL -> WarningRed
        ScamSeverity.HIGH -> AlertYellow
        ScamSeverity.MEDIUM -> DividerGray
    }
    val context = LocalContext.current
    val imageModel = remember(tactic.imageUrl, tactic.imageAsset) { tacticImageModel(tactic) }
    var showFullscreen by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDim),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.WarningAmber,
                    contentDescription = null,
                    tint = borderColor
                )
                Text(
                    text = tactic.title,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                SeverityPill(tactic.severity)
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = TextSecondary
                )
            }
            Text(
                text = tactic.description,
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
            if (tactic.tags.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    tactic.tags.take(4).forEach { tag ->
                        Text(
                            text = "#$tag",
                            color = WarningRedLight,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (imageModel != null) {
                        TacticThumbnail(
                            model = imageModel,
                            contentDescription = tactic.title,
                            onClick = { showFullscreen = true }
                        )
                    }
                    if (tactic.redFlags.isNotEmpty()) {
                        Text(
                            text = "辨識特徵",
                            color = AlertYellow,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        tactic.redFlags.forEach { flag ->
                            Text(
                                text = "・$flag",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    Text(
                        text = "保護方式",
                        color = AlertYellow,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = tactic.protection,
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (tactic.sourceUrl != null) {
                        SourceLinkRow(url = tactic.sourceUrl) {
                            openExternalUrl(context, tactic.sourceUrl)
                        }
                    }
                }
            }
        }
    }

    if (showFullscreen && imageModel != null) {
        FullscreenImageDialog(
            model = imageModel,
            contentDescription = tactic.title,
            onDismiss = { showFullscreen = false }
        )
    }
}

@Composable
private fun SourceLinkRow(url: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = WarningRedLight
        )
        Text(
            text = "查看文章來源",
            color = WarningRedLight,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = url,
            color = TextDisabled,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

private fun openExternalUrl(context: android.content.Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
        .onFailure { e ->
            if (e !is ActivityNotFoundException) throw e
        }
}

@Composable
private fun SeverityPill(severity: ScamSeverity) {
    val (label, fg, bg) = when (severity) {
        ScamSeverity.CRITICAL -> Triple("極高", Color.White, WarningRed)
        ScamSeverity.HIGH -> Triple("高", SurfaceBlack, AlertYellow)
        ScamSeverity.MEDIUM -> Triple("中", TextSecondary, SurfaceElevated)
    }
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(text = label, color = fg, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun EmptyResult() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDim),
        border = BorderStroke(1.dp, DividerGray)
    ) {
        Text(
            text = "目前查無符合條件的詐騙手法。",
            color = TextSecondary,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun FooterDisclaimer(notice: String, source: String) {
    Column(
        modifier = Modifier.padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (notice.isNotEmpty()) {
            Text(text = notice, color = TextDisabled, style = MaterialTheme.typography.bodySmall)
        }
        if (source.isNotEmpty()) {
            Text(text = "資料來源：$source", color = TextDisabled, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun RecognitionToolbar(
    onScreenshot: () -> Unit,
    onText: () -> Unit,
    onVoice: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RecognitionButton(
            icon = Icons.Filled.Image,
            label = "圖片辨識",
            onClick = onScreenshot,
            modifier = Modifier.weight(1f)
        )
        RecognitionButton(
            icon = Icons.Filled.TextFields,
            label = "文字辨識",
            onClick = onText,
            modifier = Modifier.weight(1f)
        )
        RecognitionButton(
            icon = Icons.Filled.Mic,
            label = "語音辨識",
            onClick = onVoice,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun RecognitionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(84.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDim),
        border = BorderStroke(1.dp, WarningRed)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = WarningRed,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                color = TextPrimary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ShareScamInfoBanner(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDim),
        border = BorderStroke(1.dp, AlertYellow)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Share,
                contentDescription = null,
                tint = AlertYellow,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = "詐騙資訊分享",
                color = TextPrimary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private fun launchRecognition(context: android.content.Context, mode: RecognitionMode) {
    val intent = RecognitionActivity.intent(context, mode)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

private fun openChannel(context: android.content.Context, channel: EmergencyChannel) {
    val intent = when (channel.type) {
        ChannelType.PHONE -> Intent(Intent.ACTION_DIAL, Uri.parse("tel:${channel.value}"))
        ChannelType.URL -> Intent(Intent.ACTION_VIEW, Uri.parse(channel.value))
    }.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    runCatching { context.startActivity(intent) }
        .onFailure { e ->
            if (e !is ActivityNotFoundException) throw e
        }
}
