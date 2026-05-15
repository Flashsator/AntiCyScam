package com.anticyscam.app.data.catalog

import android.content.Context
import com.anticyscam.app.data.prefs.CatalogUpdatePrefs
import com.anticyscam.app.data.repository.ScamInfoRepository
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
        val meta = fetchVersionJson() ?: return

        val currentVersion = runCatching { repository.load().version }.getOrDefault(0)
        val dismissed = prefs.dismissed()
        val threshold = maxOf(currentVersion, dismissed)
        if (meta.version <= threshold) return

        _state.value = State.UpdateAvailable(
            remoteVersion = meta.version,
            sha256 = meta.sha256,
            currentVersion = currentVersion
        )
    }

    private suspend fun runDownload(available: State.UpdateAvailable) {
        _state.value = State.Downloading
        val tempFile = File(context.filesDir, OVERRIDE_TEMP)
        val finalFile = File(context.filesDir, OVERRIDE_FILE)
        val ok = runCatching {
            downloadTo(CATALOG_URL, tempFile)
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
            _state.value = State.Done(available.remoteVersion)
        } else {
            _state.value = State.Failed("更新失敗，稍後再試。")
        }
    }

    private suspend fun fetchVersionJson(): VersionMeta? = withContext(Dispatchers.IO) {
        val body = runCatching { httpGetString(VERSION_URL) }.getOrNull() ?: return@withContext null
        runCatching { json.decodeFromString(VersionMeta.serializer(), body) }.getOrNull()
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
        val sha256: String = "",
        val updatedAt: String = ""
    )

    sealed interface State {
        data object Idle : State
        data class UpdateAvailable(
            val remoteVersion: Int,
            val sha256: String,
            val currentVersion: Int
        ) : State

        data object Downloading : State
        data class Done(val version: Int) : State
        data class Failed(val message: String) : State
    }

    private companion object {
        const val VERSION_URL =
            "https://raw.githubusercontent.com/Flashsator/anticyscam-catalog/main/version.json"
        const val CATALOG_URL =
            "https://raw.githubusercontent.com/Flashsator/anticyscam-catalog/main/scam_catalog.json"
        const val OVERRIDE_FILE = "scam_catalog.json"
        const val OVERRIDE_TEMP = "scam_catalog.json.tmp"
        const val NET_TIMEOUT_MS = 15_000
        const val CHECK_INTERVAL_MILLIS = 24L * 60 * 60 * 1000
        const val USER_AGENT = "AntiCyScam-Android/1.0 (+catalog-update-check)"
    }
}
