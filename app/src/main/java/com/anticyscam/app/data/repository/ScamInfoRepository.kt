package com.anticyscam.app.data.repository

import android.content.Context
import com.anticyscam.app.domain.model.ChannelIcon
import com.anticyscam.app.domain.model.ChannelType
import com.anticyscam.app.domain.model.EmergencyChannel
import com.anticyscam.app.domain.model.ScamCatalog
import com.anticyscam.app.domain.model.ScamCategory
import com.anticyscam.app.domain.model.ScamSeverity
import com.anticyscam.app.domain.model.ScamTactic
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads the bundled scam catalog (`assets/scam_catalog.json`) on first access
 * and caches the parsed result in memory. The asset is the only data source —
 * no network calls. A future GitHub Actions cron is expected to replace the
 * JSON in-tree, and the next release will pick up the new content.
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

    private suspend fun parse(): ScamCatalog = withContext(Dispatchers.IO) {
        val raw = context.assets.open(ASSET_PATH).bufferedReader(Charsets.UTF_8).use { it.readText() }
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
        val tactics: List<TacticDto> = emptyList()
    ) {
        fun toDomain(): ScamCatalog = ScamCatalog(
            version = version,
            lastUpdated = lastUpdated,
            source = source,
            notice = notice,
            channels = channels.map(ChannelDto::toDomain),
            categories = categories.map(CategoryDto::toDomain),
            tactics = tactics.map(TacticDto::toDomain)
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
        val protection: String
    ) {
        fun toDomain(): ScamTactic = ScamTactic(
            id = id,
            categoryId = categoryId,
            title = title,
            severity = parseEnum(severity, ScamSeverity.MEDIUM),
            tags = tags,
            description = description,
            redFlags = redFlags,
            protection = protection
        )
    }

    private companion object {
        const val ASSET_PATH = "scam_catalog.json"

        inline fun <reified E : Enum<E>> parseEnum(raw: String, default: E): E =
            runCatching { enumValueOf<E>(raw.uppercase()) }.getOrDefault(default)
    }
}
