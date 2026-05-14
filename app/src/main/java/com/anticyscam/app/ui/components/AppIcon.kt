package com.anticyscam.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap

/**
 * 取得指定 package 的 launcher icon。包未安裝或無 icon 時回傳 null。
 *
 * 以固定像素尺寸 raster 化以支援 AdaptiveIcon — Drawable 本身對 Compose 沒有
 * 原生 painter，且避免拖入 accompanist-drawablepainter 多一個相依。
 */
@Composable
private fun rememberAppIconBitmap(packageName: String, sizePx: Int = 128): ImageBitmap? {
    val context = LocalContext.current
    return remember(packageName, sizePx) {
        runCatching {
            context.packageManager
                .getApplicationIcon(packageName)
                .toBitmap(width = sizePx, height = sizePx)
                .asImageBitmap()
        }.getOrNull()
    }
}

/**
 * 顯示指定 package 的真實 launcher icon；若無法解析（卸載、停用、無 icon）
 * 則回退到 Material 通用 Apps icon。
 */
@Composable
fun AppIcon(
    packageName: String,
    modifier: Modifier = Modifier,
    fallbackTint: Color = LocalContentColor.current
) {
    val bitmap = rememberAppIconBitmap(packageName)
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
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
