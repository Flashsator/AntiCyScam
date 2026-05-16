package com.anticyscam.app.domain.model

/**
 * In-memory representation of the 防詐專區 catalog (loaded from
 * `assets/scam_catalog.json`). The asset file is intended to be replaced by a
 * future GitHub Actions cron, so the schema is kept additive: new fields
 * should be optional and parsed leniently.
 */
data class ScamCatalog(
    /**
     * Machine-facing build number. Monotonically increasing — [CatalogUpdateChecker]
     * compares this integer to decide whether a newer catalog exists. Never reused
     * for display; the human-facing label is [displayVersion].
     */
    val version: Int,
    /**
     * Human-facing version shown in 防詐專區 (e.g. "v1.0.0"). Mirrors Android's
     * versionName↔versionCode split: [version] drives update detection, this
     * string is purely cosmetic. Empty on legacy catalogs — callers fall back
     * to "v${version}".
     */
    val displayVersion: String = "",
    val lastUpdated: String,
    val source: String,
    val notice: String,
    val channels: List<EmergencyChannel>,
    val categories: List<ScamCategory>,
    val tactics: List<ScamTactic>,
    val suspiciousNames: List<SuspiciousName> = emptyList(),
    val warnedAccounts: List<WarnedAccount> = emptyList()
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
    val protection: String,
    val imageUrl: String? = null,
    val imageAsset: String? = null,
    val sourceUrl: String? = null
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

/**
 * Known scam alias / group / handle the user may receive a message from.
 * Substring-matched against recognition input; a hit alone is enough to push
 * the verdict to HIGH. Curated manually from 165 news releases, press reports,
 * and user submissions — no automated source.
 */
data class SuspiciousName(
    val name: String,
    val aliasType: SuspiciousAliasType,
    val source: String,
    val reportedDate: String,
    val note: String = ""
)

enum class SuspiciousAliasType { PERSON, LINE, IG, FB, GROUP, FAKE_BROKERAGE, OTHER }

/**
 * Known警示／衍生管制／圈存 account number. Cross-checked against any
 * 10-16 digit run detected by [HardRuleEngine]. A hit promotes the input
 * straight to CRITICAL because the user is being asked to deposit into an
 * account the bank has already flagged for fraud.
 */
data class WarnedAccount(
    val account: String,
    val bank: String,
    val source: String,
    val reportedDate: String,
    val note: String = ""
)
