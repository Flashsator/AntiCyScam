package com.anticyscam.app.domain.model

/**
 * In-memory representation of the 反詐專區 catalog (loaded from
 * `assets/scam_catalog.json`). The asset file is intended to be replaced by a
 * future GitHub Actions cron, so the schema is kept additive: new fields
 * should be optional and parsed leniently.
 */
data class ScamCatalog(
    val version: Int,
    val lastUpdated: String,
    val source: String,
    val notice: String,
    val channels: List<EmergencyChannel>,
    val categories: List<ScamCategory>,
    val tactics: List<ScamTactic>
)

data class ScamCategory(
    val id: String,
    val displayName: String,
    val shortDesc: String
)

data class ScamTactic(
    val id: String,
    val categoryId: String,
    val title: String,
    val severity: ScamSeverity,
    val tags: List<String>,
    val description: String,
    val redFlags: List<String>,
    val protection: String
)

enum class ScamSeverity { CRITICAL, HIGH, MEDIUM }

data class EmergencyChannel(
    val id: String,
    val type: ChannelType,
    val label: String,
    val value: String,
    val subtitle: String,
    val icon: ChannelIcon
)

enum class ChannelType { PHONE, URL }

enum class ChannelIcon { PHONE, WEB, MAIL, CHAT, BANK }
