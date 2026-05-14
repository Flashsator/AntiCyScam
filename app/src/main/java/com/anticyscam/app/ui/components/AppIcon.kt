package com.anticyscam.app.ui.components

import android.content.Context
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Process-wide cache for rasterised launcher icons. Pinned across Composable
 * lifetimes so a LazyColumn row scrolling off-screen and back doesn't trigger
 * another `PackageManager.getApplicationIcon()` + bitmap decode on the UI
 * thread (each ~5–30ms — the source of bind-screen scroll jank).
 *
 * 64 entries × 128×128 ARGB ≈ 4MB upper bound. Negative results (uninstalled
 * package, no icon) are tracked separately so we don't retry the failing path.
 */
private object AppIconCache {
    private val bitmaps = LruCache<String, ImageBitmap>(64)
    private val failed = mutableSetOf<String>()

    fun get(packageName: String): ImageBitmap? = bitmaps.get(packageName)

    fun put(packageName: String, bitmap: ImageBitmap) {
        bitmaps.put(packageName, bitmap)
    }

    fun isFailed(packageName: String): Boolean =
        synchronized(failed) { packageName in failed }

    fun markFailed(packageName: String) {
        synchronized(failed) { failed.add(packageName) }
    }
}

private fun decodeIconSync(context: Context, packageName: String): ImageBitmap? =
    runCatching {
        context.packageManager
            .getApplicationIcon(packageName)
            .toBitmap(width = 128, height = 128)
            .asImageBitmap()
    }.getOrNull()

/**
 * 顯示指定 package 的真實 launcher icon。
 *
 * 第一次出現某個 package 時，會先顯示 fallback Material icon，然後在 IO
 * thread 解碼後 swap 進來；快取命中時直接同步顯示，無 jank。卸載 / 無 icon
 * 的 package 會被記為失敗，不再重試。
 */
@Composable
fun AppIcon(
    packageName: String,
    modifier: Modifier = Modifier,
    fallbackTint: Color = LocalContentColor.current
) {
    val context = LocalContext.current
    var bitmap by remember(packageName) {
        mutableStateOf(AppIconCache.get(packageName))
    }

    LaunchedEffect(packageName) {
        if (bitmap != null) return@LaunchedEffect
        if (AppIconCache.isFailed(packageName)) return@LaunchedEffect
        val decoded = withContext(Dispatchers.IO) {
            decodeIconSync(context, packageName)
        }
        if (decoded != null) {
            AppIconCache.put(packageName, decoded)
            bitmap = decoded
        } else {
            AppIconCache.markFailed(packageName)
        }
    }

    val current = bitmap
    if (current != null) {
        Image(
            bitmap = current,
            contentDescription = null,
            modifier = modifier
        )
    } else {
        Icon(
            imageVector = Icons.Filled.Apps,
            contentDescription = null,
            tint = fallbackTint,
            modifier = modifier
        )
    }
}
