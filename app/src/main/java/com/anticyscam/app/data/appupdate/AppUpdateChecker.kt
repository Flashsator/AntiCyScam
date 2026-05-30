package com.anticyscam.app.data.appupdate

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.anticyscam.app.BuildConfig
import com.anticyscam.app.data.prefs.AppUpdatePrefs
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
 * Polls the public companion repo for **APK-binary** updates — the sibling of
 * [com.anticyscam.app.data.catalog.CatalogUpdateChecker], which only updates
 * the scam-data file. This one swaps the whole app.
 *
 * Flow:
 *  - [maybeCheck] runs at most once per [CHECK_INTERVAL_MILLIS]. It fetches
 *    `app_version.json`, compares `versionCode` to the currently-running
 *    [BuildConfig.VERSION_CODE] and the user-dismissed version, and emits
 *    [State.UpdateAvailable] when a strictly newer build is published.
 *  - [accept] downloads the APK into `cacheDir/update/`, verifies sha256, and
 *    moves to [State.ReadyToInstall].
 *  - [install] hands the APK to the system package installer via a
 *    FileProvider `content://` URI. If "install unknown apps" is not granted,
 *    it routes the user to the relevant Settings screen first.
 *  - [dismiss] records the versionCode as "user said no" so we don't re-prompt
 *    until a *newer* build ships.
 *
 * Network errors during a background check are swallowed to [State.Idle] — a
 * missing update is never worth interrupting the user. A user-triggered
 * [forceCheck] surfaces failures so the button visibly responds.
 */
@Singleton
class AppUpdateChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: AppUpdatePrefs
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /** Authority must match the `<provider>` declared in AndroidManifest.xml. */
    private val fileProviderAuthority = "${BuildConfig.APPLICATION_ID}.fileprovider"

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

    fun dismiss(versionCode: Int) {
        scope.launch {
            prefs.markDismissed(versionCode)
            _state.value = State.Idle
        }
    }

    /** Closes a terminal Failed/NoUpdate dialog without changing dismissal state. */
    fun clearTerminalState() {
        _state.value = State.Idle
    }

    /** User tapped "立即更新" — download the APK. */
    fun accept() {
        val current = _state.value
        if (current !is State.UpdateAvailable) return
        scope.launch { runDownload(current) }
    }

    /**
     * User tapped "安裝" on the [State.ReadyToInstall] dialog. Routes to the
     * "安裝不明應用程式" settings screen if the permission is missing, otherwise
     * launches the system installer. After granting, the user taps 安裝 again.
     */
    fun install() {
        val current = _state.value
        if (current !is State.ReadyToInstall) return
        if (!context.packageManager.canRequestPackageInstalls()) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
            return
        }
        launchInstaller(File(current.apkPath))
    }

    /**
     * Debug-only: wipe DataStore + delete any downloaded APK so the next check
     * behaves like a fresh install, then force a check immediately.
     */
    fun resetForDebug() {
        scope.launch {
            prefs.clearAll()
            runCatching { updateApkFile().delete() }
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
            if (force) _state.value = State.Failed("無法連線更新伺服器，請確認網路後再試。")
            return
        }

        val currentCode = BuildConfig.VERSION_CODE
        // force（使用者按「檢查 App 更新」）時忽略先前的「暫不更新」記錄；
        // 自動檢查仍尊重 dismissed，避免每天重複打擾。
        val threshold = if (force) currentCode else maxOf(currentCode, prefs.dismissed())
        if (meta.versionCode <= threshold) {
            if (force) _state.value = State.NoUpdate(BuildConfig.VERSION_NAME)
            return
        }
        if (meta.apkUrl.isBlank() || meta.sha256.isBlank()) {
            if (force) _state.value = State.Failed("更新資訊不完整，請稍後再試。")
            return
        }

        _state.value = State.UpdateAvailable(
            versionCode = meta.versionCode,
            versionName = meta.versionName,
            notes = meta.notes,
            apkUrl = meta.apkUrl,
            sha256 = meta.sha256,
            currentVersionName = BuildConfig.VERSION_NAME
        )
    }

    private suspend fun runDownload(available: State.UpdateAvailable) {
        _state.value = State.Downloading
        val apkFile = updateApkFile()
        val ok = runCatching {
            apkFile.parentFile?.mkdirs()
            downloadTo(available.apkUrl, apkFile)
            val actualSha = sha256Of(apkFile)
            if (!actualSha.equals(available.sha256, ignoreCase = true)) {
                throw IOException("sha256 mismatch: expected=${available.sha256} actual=$actualSha")
            }
        }.isSuccess

        if (ok) {
            _state.value = State.ReadyToInstall(
                versionCode = available.versionCode,
                versionName = available.versionName,
                apkPath = apkFile.absolutePath
            )
        } else {
            runCatching { apkFile.delete() }
            _state.value = State.Failed("下載或驗證失敗，稍後再試。")
        }
    }

    private fun launchInstaller(apk: File) {
        if (!apk.exists()) {
            _state.value = State.Failed("更新檔已遺失，請重新下載。")
            return
        }
        val uri = FileProvider.getUriForFile(context, fileProviderAuthority, apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { _state.value = State.Failed("無法開啟安裝程式。") }
    }

    private fun updateApkFile(): File = File(File(context.cacheDir, UPDATE_DIR), APK_NAME)

    private suspend fun fetchVersionJson(): VersionMeta? = withContext(Dispatchers.IO) {
        // 依序嘗試 GitHub raw → jsDelivr 鏡像。raw.githubusercontent.com 在部分台灣
        // ISP／行動網路會被 DNS 汙染或間歇封鎖，換網域鏡像可提高觸達率。
        // 注意：APK 本體仍走 GitHub Releases（jsDelivr 無法鏡像 release 附件），
        // 但 apkUrl 下載後會做 sha256 驗證，完整性無虞。
        for (url in APP_VERSION_URLS) {
            val result = runCatching { httpGetString(url) }
            val body = result.getOrNull()
            if (body != null) {
                return@withContext runCatching {
                    json.decodeFromString(VersionMeta.serializer(), body)
                }.getOrNull()
            }
            Log.w(TAG, "app version fetch failed: $url", result.exceptionOrNull())
        }
        null
    }

    private fun httpGetString(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = NET_TIMEOUT_MS
            readTimeout = NET_TIMEOUT_MS
            requestMethod = "GET"
            instanceFollowRedirects = true
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
            // Release-asset URLs 302-redirect to objects.githubusercontent.com.
            instanceFollowRedirects = true
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
        val versionCode: Int,
        val versionName: String = "",
        val apkUrl: String = "",
        val sha256: String = "",
        val notes: String = ""
    )

    sealed interface State {
        data object Idle : State
        data class UpdateAvailable(
            val versionCode: Int,
            val versionName: String,
            val notes: String,
            val apkUrl: String,
            val sha256: String,
            val currentVersionName: String
        ) : State

        data object Downloading : State
        data class ReadyToInstall(
            val versionCode: Int,
            val versionName: String,
            val apkPath: String
        ) : State
        data class Failed(val message: String) : State
        /** 使用者手動檢查、確認已是最新版時的一次性回饋。 */
        data class NoUpdate(val currentVersionName: String) : State
    }

    private companion object {
        const val TAG = "AppUpdateChecker"
        val APP_VERSION_URLS = listOf(
            "https://raw.githubusercontent.com/Flashsator/AntiCyScam/main/catalog/app_version.json",
            "https://cdn.jsdelivr.net/gh/Flashsator/AntiCyScam@main/catalog/app_version.json"
        )
        const val UPDATE_DIR = "update"
        const val APK_NAME = "anticyscam-update.apk"
        const val NET_TIMEOUT_MS = 15_000
        const val CHECK_INTERVAL_MILLIS = 24L * 60 * 60 * 1000
        const val USER_AGENT = "AntiCyScam-Android/1.0 (+app-update-check)"
    }
}
