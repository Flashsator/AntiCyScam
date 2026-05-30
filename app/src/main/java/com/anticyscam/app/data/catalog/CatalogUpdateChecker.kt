package com.anticyscam.app.data.catalog

import android.content.Context
import android.util.Log
import com.anticyscam.app.data.prefs.CatalogUpdatePrefs
import com.anticyscam.app.data.repository.ScamInfoRepository
import com.anticyscam.app.domain.model.ScamCatalog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Polls the public companion repo for catalog updates and orchestrates the
 * download → verify → swap → invalidate sequence when the user accepts.
 *
 * Flow:
 *  - [maybeCheck] runs at most once per [CHECK_INTERVAL_MILLIS]. It fetches
 *    `version.json`, compares to the in-memory catalog version and the
 *    user-dismissed version, and emits [State.UpdateAvailable] when a strictly
 *    newer version exists.
 *  - [accept] downloads `scam_catalog.json`, verifies sha256, atomically
 *    replaces `filesDir/scam_catalog.json`, then invalidates the repository
 *    cache so the next read picks up the new content.
 *  - [dismiss] records the version as "user said no" so we don't re-prompt
 *    until a *newer* version is published.
 *
 * Network errors are swallowed to [State.Idle] — a missing update is never
 * worth interrupting the user.
 */
@Singleton
class CatalogUpdateChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: CatalogUpdatePrefs,
    private val repository: ScamInfoRepository
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /**
     * Fire-and-forget. Safe to call from `onCreate`. Returns immediately —
     * the StateFlow surfaces results.
     */
    fun maybeCheck() {
        scope.launch { runCheck(force = false) }
    }

    fun forceCheck() {
        scope.launch { runCheck(force = true) }
    }

    fun dismiss(version: Int) {
        scope.launch {
            prefs.markDismissed(version)
            _state.value = State.Idle
        }
    }

    /** Closes a terminal Done/Failed dialog without changing dismissal state. */
    fun clearTerminalState() {
        _state.value = State.Idle
    }

    fun accept() {
        val current = _state.value
        if (current !is State.UpdateAvailable) return
        scope.launch { runDownload(current) }
    }

    /**
     * Debug-only: wipe DataStore + delete the downloaded override file so the
     * next check behaves like a fresh install. After clearing, immediately
     * triggers a forced check so the user sees the update prompt without
     * waiting for the 24h debounce or having to reinstall the APK.
     */
    fun resetForDebug() {
        scope.launch {
            prefs.clearAll()
            runCatching { File(context.filesDir, OVERRIDE_FILE).delete() }
            runCatching { File(context.filesDir, OVERRIDE_TEMP).delete() }
            repository.invalidate()
            _state.value = State.Idle
            runCheck(force = true)
        }
    }

    private suspend fun runCheck(force: Boolean) {
        if (!force) {
            val now = System.currentTimeMillis()
            val last = prefs.lastCheckedAt()
            if (now - last < CHECK_INTERVAL_MILLIS) return
        }

        prefs.markCheckedAt(System.currentTimeMillis())
        val meta = fetchVersionJson()
        if (meta == null) {
            // 背景檢查吞掉錯誤；使用者手動觸發時要回饋失敗，才知道按鈕有反應
            if (force) _state.value = State.Failed("無法連線詐騙資料庫，請確認網路後再試。")
            return
        }

        val currentCatalog = runCatching { repository.load() }.getOrNull()
        val currentVersion = currentCatalog?.version ?: 0
        val currentDisplayVersion = currentCatalog?.displayVersion.orEmpty()
        // force（使用者按「檢查更新」）時忽略先前的「暫不更新」記錄，
        // 等於使用者改變主意要重新看一次；自動檢查仍尊重 dismissed
        val threshold = if (force) currentVersion else maxOf(currentVersion, prefs.dismissed())
        if (meta.version <= threshold) {
            if (force) _state.value = State.NoUpdate(currentVersion, currentDisplayVersion)
            return
        }

        _state.value = State.UpdateAvailable(
            remoteVersion = meta.version,
            remoteDisplayVersion = meta.displayVersion,
            sha256 = meta.sha256,
            currentVersion = currentVersion,
            currentDisplayVersion = currentDisplayVersion
        )
    }

    private suspend fun runDownload(available: State.UpdateAvailable) {
        _state.value = State.Downloading
        // 必須在 swap 之前讀取，否則 invalidate() 後再 load() 拿到的就是新版本，
        // diff 結果會空白。
        val before = runCatching { repository.load() }.getOrNull()
        val tempFile = File(context.filesDir, OVERRIDE_TEMP)
        val finalFile = File(context.filesDir, OVERRIDE_FILE)
        val ok = runCatching {
            downloadToWithFallback(CATALOG_URLS, tempFile)
            val actualSha = sha256Of(tempFile)
            if (!actualSha.equals(available.sha256, ignoreCase = true)) {
                throw IOException("sha256 mismatch: expected=${available.sha256} actual=$actualSha")
            }
            if (finalFile.exists()) finalFile.delete()
            if (!tempFile.renameTo(finalFile)) {
                throw IOException("failed to swap catalog file into place")
            }
        }.isSuccess

        runCatching { if (tempFile.exists()) tempFile.delete() }

        if (ok) {
            prefs.markApplied(available.remoteVersion)
            repository.invalidate()
            val after = runCatching { repository.load() }.getOrNull()
            val summary = if (before != null && after != null) {
                buildSummary(before, after)
            } else {
                null
            }
            val appliedDisplayVersion = after?.displayVersion
                ?.takeIf { it.isNotBlank() }
                ?: available.remoteDisplayVersion
            _state.value = State.Done(
                version = available.remoteVersion,
                displayVersion = appliedDisplayVersion,
                summary = summary
            )
        } else {
            _state.value = State.Failed("更新失敗，稍後再試。")
        }
    }

    private fun buildSummary(before: ScamCatalog, after: ScamCatalog): UpdateSummary =
        UpdateSummary(
            fromVersion = before.version,
            toVersion = after.version,
            fromDisplayVersion = before.displayVersion,
            toDisplayVersion = after.displayVersion,
            sections = listOf(
                sectionDelta("詐騙手法", before.tactics, after.tactics) { it.id },
                sectionDelta("警示帳號", before.warnedAccounts, after.warnedAccounts) { it.account },
                sectionDelta("可疑名單", before.suspiciousNames, after.suspiciousNames) { it.name },
                sectionDelta("詐騙分類", before.categories, after.categories) { it.id },
                sectionDelta("聯絡管道", before.channels, after.channels) { it.id }
            )
        )

    private fun <T> sectionDelta(
        label: String,
        oldList: List<T>,
        newList: List<T>,
        key: (T) -> String
    ): SectionDelta {
        val oldKeys = oldList.map(key).toSet()
        val newKeys = newList.map(key).toSet()
        return SectionDelta(
            label = label,
            added = (newKeys - oldKeys).size,
            removed = (oldKeys - newKeys).size,
            total = newList.size
        )
    }

    private suspend fun fetchVersionJson(): VersionMeta? = withContext(Dispatchers.IO) {
        val body = httpGetStringWithFallback(VERSION_URLS) ?: return@withContext null
        runCatching { json.decodeFromString(VersionMeta.serializer(), body) }.getOrNull()
    }

    /**
     * 依序嘗試多個來源（GitHub raw → jsDelivr 鏡像）。`raw.githubusercontent.com`
     * 在部分台灣 ISP／行動網路會被 DNS 汙染或間歇封鎖，導致「明明有網路卻更新失敗」。
     * 換不同網域的鏡像可大幅提高觸達率。全部失敗才回傳 null。
     */
    private fun httpGetStringWithFallback(urls: List<String>): String? {
        for (url in urls) {
            val result = runCatching { httpGetString(url) }
            result.getOrNull()?.let { return it }
            Log.w(TAG, "catalog version fetch failed: $url", result.exceptionOrNull())
        }
        return null
    }

    /** 下載亦套用相同的鏡像 fallback；全部失敗才丟出，交給呼叫端標記為失敗。 */
    private fun downloadToWithFallback(urls: List<String>, dest: File) {
        var lastError: Throwable? = null
        for (url in urls) {
            val result = runCatching { downloadTo(url, dest) }
            if (result.isSuccess) return
            lastError = result.exceptionOrNull()
            Log.w(TAG, "catalog download failed: $url", lastError)
        }
        throw IOException("all catalog mirrors failed", lastError)
    }

    private fun httpGetString(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = NET_TIMEOUT_MS
            readTimeout = NET_TIMEOUT_MS
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code on $url")
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun downloadTo(url: String, dest: File) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = NET_TIMEOUT_MS
            readTimeout = NET_TIMEOUT_MS
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code on $url")
            conn.inputStream.use { input ->
                dest.outputStream().use { out -> input.copyTo(out) }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun sha256Of(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString(separator = "") { b -> "%02x".format(b) }
    }

    @Serializable
    private data class VersionMeta(
        val version: Int,
        val displayVersion: String = "",
        val sha256: String = "",
        val updatedAt: String = ""
    )

    sealed interface State {
        data object Idle : State
        data class UpdateAvailable(
            val remoteVersion: Int,
            val remoteDisplayVersion: String,
            val sha256: String,
            val currentVersion: Int,
            val currentDisplayVersion: String
        ) : State

        data object Downloading : State
        data class Done(
            val version: Int,
            val displayVersion: String,
            val summary: UpdateSummary? = null
        ) : State
        data class Failed(val message: String) : State
        /** 使用者手動檢查、確認已是最新版時的一次性回饋。 */
        data class NoUpdate(
            val currentVersion: Int,
            val currentDisplayVersion: String
        ) : State
    }

    /**
     * Per-section delta between two catalog snapshots. `total` is the post-swap
     * row count for that section, so the user sees "新增 3 筆（目前共 47 筆）".
     */
    data class SectionDelta(
        val label: String,
        val added: Int,
        val removed: Int,
        val total: Int
    ) {
        val isChanged: Boolean get() = added > 0 || removed > 0
    }

    data class UpdateSummary(
        val fromVersion: Int,
        val toVersion: Int,
        val fromDisplayVersion: String,
        val toDisplayVersion: String,
        val sections: List<SectionDelta>
    )

    private companion object {
        const val TAG = "CatalogUpdateChecker"
        // 主來源 GitHub raw，鏡像 jsDelivr（不同網域 + 多 CDN，避開 raw 被汙染／封鎖）。
        // 兩者都指向同一 repo 的 main，catalog 本體下載後仍會做 sha256 驗證，鏡像不影響完整性。
        val VERSION_URLS = listOf(
            "https://raw.githubusercontent.com/Flashsator/AntiCyScam/main/catalog/version.json",
            "https://cdn.jsdelivr.net/gh/Flashsator/AntiCyScam@main/catalog/version.json"
        )
        val CATALOG_URLS = listOf(
            "https://raw.githubusercontent.com/Flashsator/AntiCyScam/main/catalog/scam_catalog.json",
            "https://cdn.jsdelivr.net/gh/Flashsator/AntiCyScam@main/catalog/scam_catalog.json"
        )
        const val OVERRIDE_FILE = "scam_catalog.json"
        const val OVERRIDE_TEMP = "scam_catalog.json.tmp"
        const val NET_TIMEOUT_MS = 15_000
        const val CHECK_INTERVAL_MILLIS = 24L * 60 * 60 * 1000
        const val USER_AGENT = "AntiCyScam-Android/1.0 (+catalog-update-check)"
    }
}
