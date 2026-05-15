package com.anticyscam.app.data.repository

import android.content.Context
import com.anticyscam.app.domain.model.ChannelIcon
import com.anticyscam.app.domain.model.ChannelType
import com.anticyscam.app.domain.model.EmergencyChannel
import com.anticyscam.app.domain.model.ScamCatalog
import com.anticyscam.app.domain.model.ScamCategory
import com.anticyscam.app.domain.model.ScamSeverity
import com.anticyscam.app.domain.model.ScamTactic
import com.anticyscam.app.domain.model.SuspiciousAliasType
import com.anticyscam.app.domain.model.SuspiciousName
import com.anticyscam.app.domain.model.WarnedAccount
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads the scam catalog with a two-tier source:
 *   1. `filesDir/scam_catalog.json` — written by [CatalogUpdateChecker] after
 *      the user accepts an in-app update prompt. Takes precedence when present.
 *   2. `assets/scam_catalog.json` — baseline shipped in the APK; used on a
 *      fresh install or when no override has been downloaded.
 *
 * The parsed catalog is cached in memory. [invalidate] forces a re-read after
 * a successful download so the new content surfaces without an app restart.
 */
@Singleton
class ScamInfoRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }
    private val mutex = Mutex()
    @Volatile private var cached: ScamCatalog? = null

    suspend fun load(): ScamCatalog {
        cached?.let { return it }
        return mutex.withLock {
            cached ?: parse().also { cached = it }
        }
    }

    suspend fun invalidate() {
        mutex.withLock { cached = null }
    }

    private suspend fun parse(): ScamCatalog = withContext(Dispatchers.IO) {
        val override = File(context.filesDir, OVERRIDE_FILE)
        val raw = if (override.exists() && override.length() > 0L) {
            runCatching { override.readText(Charsets.UTF_8) }
                .getOrElse { context.assets.open(ASSET_PATH).bufferedReader(Charsets.UTF_8).use { it.readText() } }
        } else {
            context.assets.open(ASSET_PATH).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
        val dto = json.decodeFromString(CatalogDto.serializer(), raw)
        dto.toDomain()
    }

    @Serializable
    private data class CatalogDto(
        val version: Int,
        val lastUpdated: String,
        val source: String,
        val notice: String = "",
        val channels: List<ChannelDto> = emptyList(),
        val categories: List<CategoryDto> = emptyList(),
        val tactics: List<TacticDto> = emptyList(),
        val suspiciousNames: List<SuspiciousNameDto> = emptyList(),
        val warnedAccounts: List<WarnedAccountDto> = emptyList()
    ) {
        fun toDomain(): ScamCatalog = ScamCatalog(
            version = version,
            lastUpdated = lastUpdated,
            source = source,
            notice = notice,
            channels = channels.map(ChannelDto::toDomain),
            categories = categories.map(CategoryDto::toDomain),
            tactics = tactics.map(TacticDto::toDomain),
            suspiciousNames = suspiciousNames.mapNotNull(SuspiciousNameDto::toDomainOrNull),
            warnedAccounts = warnedAccounts.mapNotNull(WarnedAccountDto::toDomainOrNull)
        )
    }

    @Serializable
    private data class ChannelDto(
        val id: String,
        val type: String,
        val label: String,
        val value: String,
        val subtitle: String = "",
        val icon: String = "WEB"
    ) {
        fun toDomain(): EmergencyChannel = EmergencyChannel(
            id = id,
            type = parseEnum(type, ChannelType.URL),
            label = label,
            value = value,
            subtitle = subtitle,
            icon = parseEnum(icon, ChannelIcon.WEB)
        )
    }

    @Serializable
    private data class CategoryDto(
        val id: String,
        val displayName: String,
        val shortDesc: String = ""
    ) {
        fun toDomain(): ScamCategory = ScamCategory(
            id = id,
            displayName = displayName,
            shortDesc = shortDesc
        )
    }

    @Serializable
    private data class TacticDto(
        val id: String,
        @SerialName("categoryId") val categoryId: String,
        val title: String,
        val severity: String = "MEDIUM",
        val tags: List<String> = emptyList(),
        val description: String,
        val redFlags: List<String> = emptyList(),
        val protection: String,
        val imageUrl: String? = null,
        val imageAsset: String? = null,
        val sourceUrl: String? = null
    ) {
        fun toDomain(): ScamTactic = ScamTactic(
            id = id,
            categoryId = categoryId,
            title = title,
            severity = parseEnum(severity, ScamSeverity.MEDIUM),
            tags = tags,
            description = description,
            redFlags = redFlags,
            protection = protection,
            imageUrl = imageUrl?.takeIf { it.isNotBlank() },
            imageAsset = imageAsset?.takeIf { it.isNotBlank() },
            sourceUrl = sourceUrl?.takeIf { it.isNotBlank() }
        )
    }

    @Serializable
    private data class SuspiciousNameDto(
        val name: String,
        val aliasType: String = "OTHER",
        val source: String = "",
        val reportedDate: String = "",
        val note: String = ""
    ) {
        fun toDomainOrNull(): SuspiciousName? {
            val cleanName = name.trim()
            if (cleanName.isEmpty()) return null
            return SuspiciousName(
                name = cleanName,
                aliasType = parseEnum(aliasType, SuspiciousAliasType.OTHER),
                source = source,
                reportedDate = reportedDate,
                note = note
            )
        }
    }

    @Serializable
    private data class WarnedAccountDto(
        val account: String,
        val bank: String = "",
        val source: String = "",
        val reportedDate: String = "",
        val note: String = ""
    ) {
        fun toDomainOrNull(): WarnedAccount? {
            val digits = account.filter(Char::isDigit)
            if (digits.length !in MIN_ACCOUNT_DIGITS..MAX_ACCOUNT_DIGITS) return null
            return WarnedAccount(
                account = digits,
                bank = bank,
                source = source,
                reportedDate = reportedDate,
                note = note
            )
        }
    }

    private companion object {
        const val ASSET_PATH = "scam_catalog.json"
        const val OVERRIDE_FILE = "scam_catalog.json"
        const val MIN_ACCOUNT_DIGITS = 8
        const val MAX_ACCOUNT_DIGITS = 16

        inline fun <reified E : Enum<E>> parseEnum(raw: String, default: E): E =
            runCatching { enumValueOf<E>(raw.uppercase()) }.getOrDefault(default)
    }
}
