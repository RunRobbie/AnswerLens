package com.example.answerlens

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class OcrProcessor {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    fun recognize(bitmap: Bitmap, callback: (String, String?) -> Unit) {
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val orderedText = visionText.textBlocks
                    .sortedWith(compareBy({ it.boundingBox?.top ?: 0 }, { it.boundingBox?.left ?: 0 }))
                    .joinToString("\n") { block ->
                        block.lines
                            .sortedWith(compareBy({ it.boundingBox?.top ?: 0 }, { it.boundingBox?.left ?: 0 }))
                            .joinToString("\n") { it.text }
                    }
                    .ifBlank { visionText.text }
                callback(orderedText, null)
            }
            .addOnFailureListener { e ->
                callback("", e.message ?: "OCR failed.")
            }
    }
}
