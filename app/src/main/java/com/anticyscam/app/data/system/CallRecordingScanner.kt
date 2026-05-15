package com.anticyscam.app.data.system

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log

/**
 * MediaStore 掃描：在通話結束後找出 OEM 通話錄音 App 剛寫入的音檔。
 *
 * Scoped Storage 之後 `java.io.File("/sdcard/...")` 已經不可靠，必須走
 * [MediaStore.Audio.Media.EXTERNAL_CONTENT_URI]。我們不知道每家 OEM 把錄音
 * 放在哪一個 RELATIVE_PATH，所以策略是：
 *   1. 用 `DATE_MODIFIED > callStartedAtSec` 把候選縮到「這通通話之後寫入的音檔」
 *   2. 用 [NAME_TOKENS] 比對檔名 / 路徑找出實際是「通話錄音」的那一筆
 *   3. 取最新一筆
 *
 * 若使用者本來就會聽音樂 / 用其他錄音 App，DATE_MODIFIED 預掃可能有多筆，
 * NAME_TOKENS 篩選負責去掉假陽性。沒命中就回 null，UI 不會跳通知。
 */
object CallRecordingScanner {

    private const val TAG = "CallRecordingScanner"

    /**
     * 檔名 / 相對路徑命中其中一個 token 才視為通話錄音。Token 取小寫比對，
     * 涵蓋華語 OEM（小米 / OPPO / 三星 / Vivo / 中文 ROM）常見命名。
     */
    private val NAME_TOKENS = listOf(
        "call",
        "通話",
        "錄音",
        "通话",
        "录音",
        "recording",
        "rec_",
        "voice"
    )

    /**
     * 找出 [sinceMillis] 之後寫入、且檔名 / 路徑像是通話錄音的最新一筆音檔。
     *
     * @param sinceMillis 通話開始時間（System.currentTimeMillis()）。MediaStore
     *   的 DATE_MODIFIED 是 epoch 秒，這裡會自動轉換。
     * @return 可用 [Intent.EXTRA_STREAM] 帶到 RecognitionActivity 的 content://
     *   URI。沒找到回 null。
     */
    fun findLatestRecording(context: Context, sinceMillis: Long): Uri? {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.DATE_MODIFIED
        )
        // DATE_MODIFIED 是 epoch 秒，sinceMillis 是毫秒
        val sinceSec = (sinceMillis / 1000L).coerceAtLeast(0L)
        // 容許 5 秒誤差：有些 OEM 在通話結束 → 寫檔 → MediaStore 索引之間
        // 不同步，落差 1~3 秒很正常。
        val cutoffSec = (sinceSec - 5L).coerceAtLeast(0L)
        val selection = "${MediaStore.Audio.Media.DATE_MODIFIED} > ?"
        val selectionArgs = arrayOf(cutoffSec.toString())
        val sortOrder = "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"

        return runCatching {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameCol).orEmpty().lowercase()
                    val path = cursor.getString(pathCol).orEmpty().lowercase()
                    if (NAME_TOKENS.any { token -> token in name || token in path }) {
                        val id = cursor.getLong(idCol)
                        val uri = ContentUris.withAppendedId(collection, id)
                        Log.i(TAG, "matched call recording: name=$name path=$path → $uri")
                        return@use uri
                    }
                }
                Log.d(TAG, "no call recording matched within ${cursor.count} candidates")
                null
            }
        }.onFailure {
            Log.w(TAG, "MediaStore query failed", it)
        }.getOrNull()
    }
}
