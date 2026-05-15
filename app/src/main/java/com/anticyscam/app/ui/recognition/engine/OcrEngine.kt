package com.anticyscam.app.ui.recognition.engine

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Thin coroutine wrapper around ML Kit's on-device Chinese text recognizer.
 * Free, runs on-device after the first model download (ML Kit handles that
 * transparently). Returns the full extracted text or throws.
 */
object OcrEngine {

    suspend fun recognize(context: Context, imageUri: Uri): String =
        suspendCancellableCoroutine { cont ->
            val recognizer = TextRecognition.getClient(
                ChineseTextRecognizerOptions.Builder().build()
            )
            cont.invokeOnCancellation { recognizer.close() }
            runCatching {
                val image = InputImage.fromFilePath(context, imageUri)
                recognizer.process(image)
                    .addOnSuccessListener { result ->
                        recognizer.close()
                        cont.resume(result.text)
                    }
                    .addOnFailureListener { err ->
                        recognizer.close()
                        cont.resumeWithException(err)
                    }
            }.onFailure { err ->
                recognizer.close()
                cont.resumeWithException(err)
            }
        }
}
