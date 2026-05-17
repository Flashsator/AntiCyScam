package com.anticyscam.app.ui.recognition.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.vosk.Model
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * Provisions the Vosk small Chinese model at runtime.
 *
 * 需求 #6：模型不再隨 APK 打包（原本 `assets/vosk-model-small-cn-0.22.zip`
 * 約 44 MB，使整個 APK 過大）。改為首次使用語音辨識時，從公開的 catalog
 * repo 下載 zip → 驗證 SHA-256 → 解壓到 App 私有 filesDir → 以
 * Vosk `Model(path)` 載入。之後啟動直接重用磁碟上的副本。
 *
 * 安全性：下載完成後一定先比對 [MODEL_SHA256] 才解壓，雜湊不符即中止並
 * 刪檔，避免被竄改的模型檔落地。沿用 [com.anticyscam.app.data.appupdate.AppUpdateChecker]
 * 的下載 + 驗證模式。
 *
 * 維運注意：[MODEL_ZIP_URL] 指向的 zip 必須由維運手動上傳到 catalog repo，
 * 且內容須與計算 [MODEL_SHA256] 時的檔案完全一致，否則所有使用者都會驗證失敗。
 */
object VoskModelManager {

    private const val MODEL_DIR_NAME = "vosk-model-small-cn-0.22"

    /**
     * 公開模型 zip 的下載位置 —— catalog repo 的 GitHub Release 資產。
     * 維運若要更換模型，須新建一個 Release 並同步更新此 URL 與
     * [MODEL_SHA256]。Release 資產 URL 會 302 轉址到 GitHub CDN，
     * 已由 [downloadTo] 的 instanceFollowRedirects 處理。
     */
    private const val MODEL_ZIP_URL =
        "https://github.com/Flashsator/anticyscam-catalog/releases/download/" +
            "vosk-model-cn-0.22/vosk-model-small-cn-0.22.zip"

    /** [MODEL_ZIP_URL] 指向檔案的預期 SHA-256（原打包進 APK 的同一份 zip）。 */
    private const val MODEL_SHA256 =
        "3af8b0e7e0f835ae9d414ce5df580237a3cfb08d586c9fbbb0f7ff29ad5b14ba"

    /** 預期檔案大小，用於沒有 Content-Length 標頭時估算下載進度。 */
    private const val MODEL_SIZE_BYTES = 43_898_754L

    private const val NET_TIMEOUT_MS = 20_000
    private const val USER_AGENT = "AntiCyScam-Android/1.0 (+vosk-model-download)"
    private const val DOWNLOAD_TMP_NAME = "vosk-model-download.zip"

    fun isInstalled(context: Context): Boolean {
        val dir = modelDir(context)
        return dir.exists() && File(dir, "am").exists() && File(dir, "conf").exists()
    }

    fun modelPath(context: Context): String = modelDir(context).absolutePath

    private fun modelDir(context: Context): File =
        File(context.filesDir, MODEL_DIR_NAME)

    /**
     * Download (if needed) + open the model. Emits progress via [onProgress];
     * `phase` already embeds a percentage where one is meaningful.
     * Throws on network / verification / unzip failure so the caller can show
     * an error and let the user retry.
     */
    suspend fun ensureModel(
        context: Context,
        onProgress: (phase: String, percent: Int) -> Unit
    ): Model = withContext(Dispatchers.IO) {
        if (!isInstalled(context)) {
            downloadAndInstall(context, onProgress)
        }
        onProgress("載入模型中…", 100)
        Model(modelPath(context))
    }

    private fun downloadAndInstall(
        context: Context,
        onProgress: (phase: String, percent: Int) -> Unit
    ) {
        val zipFile = File(context.cacheDir, DOWNLOAD_TMP_NAME)
        try {
            downloadTo(MODEL_ZIP_URL, zipFile, onProgress)

            onProgress("驗證模型檔…", 100)
            val actualSha = sha256Of(zipFile)
            if (!actualSha.equals(MODEL_SHA256, ignoreCase = true)) {
                throw IOException(
                    "模型檔驗證失敗（雜湊不符），可能下載不完整，請重試。"
                )
            }

            unzip(context, zipFile, onProgress)
            if (!isInstalled(context)) {
                error("模型解壓縮完成但結構不符，請重試。")
            }
        } finally {
            runCatching { zipFile.delete() }
        }
    }

    private fun downloadTo(
        url: String,
        dest: File,
        onProgress: (phase: String, percent: Int) -> Unit
    ) {
        onProgress("下載中文語音模型… 0%", 0)
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = NET_TIMEOUT_MS
            readTimeout = NET_TIMEOUT_MS
            requestMethod = "GET"
            // raw.githubusercontent.com 對大檔可能 302 轉址到 CDN。
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code on $url")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: MODEL_SIZE_BYTES
            conn.inputStream.use { input ->
                FileOutputStream(dest).use { out ->
                    val buf = ByteArray(64 * 1024)
                    var downloaded = 0L
                    var lastPct = -1
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        downloaded += n
                        val pct = (downloaded * 100 / total).toInt().coerceIn(0, 100)
                        if (pct != lastPct) {
                            lastPct = pct
                            onProgress("下載中文語音模型… $pct%", pct)
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun unzip(
        context: Context,
        zipFile: File,
        onProgress: (phase: String, percent: Int) -> Unit
    ) {
        onProgress("解壓縮中文語音模型…", 0)
        val parentDir = context.filesDir
        // Best-effort cleanup of a partial prior run.
        runCatching { modelDir(context).deleteRecursively() }

        // No reliable uncompressed total without scanning twice; report by
        // approximate entry count — small-cn-0.22 has ~210 files.
        var entriesDone = 0
        val approxTotalEntries = 250

        zipFile.inputStream().use { input ->
            ZipInputStream(input).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val outFile = File(parentDir, entry.name)
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { out -> zis.copyTo(out) }
                    }
                    zis.closeEntry()
                    entriesDone += 1
                    val pct = (entriesDone * 99 / approxTotalEntries).coerceIn(0, 99)
                    onProgress("解壓縮中文語音模型… $pct%", pct)
                    entry = zis.nextEntry
                }
            }
        }
    }

    private fun sha256Of(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString(separator = "") { b -> "%02x".format(b) }
    }
}
