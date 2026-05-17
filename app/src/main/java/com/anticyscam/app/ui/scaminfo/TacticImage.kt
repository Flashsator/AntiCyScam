package com.anticyscam.app.ui.scaminfo

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.anticyscam.app.domain.model.ScamTactic
import com.anticyscam.app.ui.theme.DividerGray
import com.anticyscam.app.ui.theme.SurfaceDim
import com.anticyscam.app.ui.theme.TextSecondary

/**
 * Resolves a tactic's image source for Coil. `imageUrl` (https://) wins;
 * falls back to `imageAsset` mapped to `file:///android_asset/<asset>`; returns
 * null when the tactic has no image at all so callers can skip rendering.
 */
internal fun tacticImageModel(tactic: ScamTactic): Any? {
    tactic.imageUrl?.let { return it }
    tactic.imageAsset?.let { return Uri.parse("file:///android_asset/$it") }
    return null
}

/**
 * Square-ish thumbnail rendered inside the expanded card. Tapping opens the
 * fullscreen viewer — the source link is a separate button so users can pick
 * which action they want.
 *
 * Remote `imageUrl` images are government-hosted hotlinks fetched over the
 * network on first view; the `loading` slot shows a spinner instead of a blank
 * box, and `crossfade` fades the image in once decoded so it does not pop in
 * abruptly. Coil's default disk cache makes every later view instant.
 */
@Composable
internal fun TacticThumbnail(
    model: Any,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val request = remember(model) {
        ImageRequest.Builder(context)
            .data(model)
            .crossfade(true)
            .build()
    }
    Box(
        modifier = modifier
            .size(96.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, DividerGray, RoundedCornerShape(10.dp))
            .clickable { onClick() }
    ) {
        SubcomposeAsyncImage(
            model = request,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            loading = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(SurfaceDim),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = TextSecondary
                    )
                }
            },
            error = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(SurfaceDim),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.BrokenImage,
                        contentDescription = null,
                        tint = TextSecondary
                    )
                }
            }
        )
    }
}

/**
 * Fullscreen image viewer used when the user taps a tactic thumbnail. Black
 * scrim, image fit-center, tap outside or press the close button to dismiss.
 * Pinch-zoom is not implemented — fit-center is fine for the kind of evidence
 * screenshots and infographics we publish; revisit if users need to inspect
 * fine print.
 */
@Composable
internal fun FullscreenImageDialog(
    model: Any,
    contentDescription: String?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val request = remember(model) {
        ImageRequest.Builder(context)
            .data(model)
            .crossfade(true)
            .build()
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable { onDismiss() }
        ) {
            SubcomposeAsyncImage(
                model = request,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .align(Alignment.Center),
                loading = {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(40.dp)
                            .align(Alignment.Center),
                        color = Color.White
                    )
                },
                error = {
                    Icon(
                        imageVector = Icons.Filled.BrokenImage,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .size(64.dp)
                            .align(Alignment.Center)
                    )
                }
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "關閉",
                    tint = Color.White
                )
            }
        }
    }
}
