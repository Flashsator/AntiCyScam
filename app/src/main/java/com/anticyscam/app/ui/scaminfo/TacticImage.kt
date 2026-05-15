package com.anticyscam.app.ui.scaminfo

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.anticyscam.app.domain.model.ScamTactic
import com.anticyscam.app.ui.theme.DividerGray

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
 */
@Composable
internal fun TacticThumbnail(
    model: Any,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(96.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, DividerGray, RoundedCornerShape(10.dp))
            .clickable { onClick() }
    ) {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
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
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .align(Alignment.Center)
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
