package com.anticyscam.app.ui.recognition.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.vosk.Model
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * Loads the Vosk small Chinese model that ships INSIDE the APK
 * (`assets/vosk-model-small-cn-0.22.zip`, ~44 MB).
 *
 * On first launch we unzip from assets → app-private filesDir, then load via
 * Vosk's `Model(path)`. Subsequent launches reuse the on-disk copy.
 *
 * Why ship in-APK instead of downloading at runtime: requirement is that the
 * recognition feature works fully offline from install — no network needed.
 * The model is too large to read directly from assets every call, and Vosk's
 * `Model` constructor wants a filesystem path anyway.
 */
object VoskModelManager {

    private const val MODEL_DIR_NAME = "vosk-model-small-cn-0.22"
    private const val MODEL_ASSET_ZIP = "vosk-model-small-cn-0.22.zip"

    fun isInstalled(context: Context): Boolean {
        val dir = modelDir(context)
        return dir.exists() && File(dir, "am").exists() && File(dir, "conf").exists()
    }

    fun modelPath(context: Context): String = modelDir(context).absolutePath

    private fun modelDir(context: Context): File =
        File(context.filesDir, MODEL_DIR_NAME)

    /**
     * Unpack (if needed) + open the model. Emits progress (0..100) via [onProgress].
     * Throws on unzip / load failure so the caller can show an error.
     */
    suspend fun ensureModel(
        context: Context,
        onProgress: (phase: String, percent: Int) -> Unit
    ): Model = withContext(Dispatchers.IO) {
        if (!isInstalled(context)) {
            unzipFromAssets(context, onProgress)
        }
        onProgress("載入模型中…", 100)
        Model(modelPath(context))
    }

    private fun unzipFromAssets(
        context: Context,
        onProgress: (phase: String, percent: Int) -> Unit
    ) {
        onProgress("解壓縮中文語音模型…", 0)
        val parentDir = context.filesDir
        // Best-effort cleanup of partial prior run
        runCatching { modelDir(context).deleteRecursively() }

        // We don't have a reliable uncompressed total without scanning twice,
        // so progress is reported per-entry by approximate count.
        var entriesDone = 0
        val approxTotalEntries = 250  // small-cn-0.22 has ~210 files; rough is fine

        context.assets.open(MODEL_ASSET_ZIP).use { input ->
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

        if (!isInstalled(context)) {
            error("模型解壓縮完成但結構不符，請重試")
        }
    }
}
