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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.anticyscam.app.domain.model.ChannelIcon
import com.anticyscam.app.domain.model.ChannelType
import com.anticyscam.app.domain.model.EmergencyChannel
import com.anticyscam.app.domain.model.ScamCategory
import com.anticyscam.app.domain.model.ScamSeverity
import com.anticyscam.app.domain.model.ScamTactic
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
 * 反詐專區 — driven by [ScamInfoRepository] reading
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Header(state) }

        if (state.errorMessage != null) {
            item { ErrorBanner(state.errorMessage!!) }
        }

        item {
            SectionTitle("緊急通報管道")
        }
        items(items = state.channels, key = { it.id }) { channel ->
            ChannelCard(channel) { openChannel(context, channel) }
        }

        item { Spacer(Modifier.height(4.dp)) }
        item {
            SectionTitle("常見手法（${state.allTactics.size} 種）")
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

        item { FooterDisclaimer(state.notice, state.source) }
    }
}

@Composable
private fun Header(state: ScamInfoState) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "反詐專區",
            color = WarningRed,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
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
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text("搜尋手法、關鍵字（例如：LINE、紓困、ATM）", color = TextDisabled) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TextSecondary) },
        singleLine = true,
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
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                }
            }
        }
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
