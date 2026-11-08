package com.arendon.sudokumaster.domain.handwriting

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.Ink
import kotlinx.coroutines.tasks.await
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Hybrid Neural + Topological Handwriting Recognizer using Google ML Kit Digital Ink Recognition
 * combined with [DigitStrokeRecognizer] for scribble-erase, offline fallback, and 6-vs-8 topological verification.
 */
object MlKitDigitRecognizer {

    private val isDownloading = AtomicBoolean(false)

    @Volatile
    private var isModelReady: Boolean = false

    private val model: DigitalInkRecognitionModel? by lazy {
        try {
            val identifier = DigitalInkRecognitionModelIdentifier.fromLanguageTag("en-US")
            identifier?.let { DigitalInkRecognitionModel.builder(it).build() }
        } catch (_: Exception) {
            null
        }
    }

    private val recognizer: DigitalInkRecognizer? by lazy {
        model?.let {
            try {
                DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(it).build())
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * Triggers a non-blocking background check/download of the ML Kit Digital Ink model.
     */
    fun warmupModelInBackground() {
        val m = model ?: return
        val remoteManager = RemoteModelManager.getInstance()
        remoteManager.isModelDownloaded(m)
            .addOnSuccessListener { downloaded ->
                if (downloaded) {
                    isModelReady = true
                } else if (isDownloading.compareAndSet(false, true)) {
                    val conditions = DownloadConditions.Builder().build()
                    remoteManager.download(m, conditions)
                        .addOnSuccessListener {
                            isModelReady = true
                            isDownloading.set(false)
                        }
                        .addOnFailureListener {
                            isDownloading.set(false)
                        }
                }
            }
    }

    /**
     * Recognizes handwritten strokes using Google ML Kit Digital Ink Recognition when ready,
     * combined with [DigitStrokeRecognizer]'s structural verifier and instant offline fallback.
     */
    suspend fun recognizeHybrid(strokes: List<List<InkPoint>>): RecognizedInkResult {
        val geometricResult = DigitStrokeRecognizer.recognize(strokes)
        if (geometricResult is RecognizedInkResult.Erase ||
            geometricResult is RecognizedInkResult.Unrecognized
        ) {
            return geometricResult
        }

        val geoDigit = geometricResult as RecognizedInkResult.Digit
        if (!isModelReady) {
            warmupModelInBackground()
            return geoDigit
        }

        val client = recognizer ?: return geoDigit
        return try {
            val inkBuilder = Ink.builder()
            var fakeTime = 0L
            for (stroke in strokes) {
                if (stroke.size < 2) continue
                val strokeBuilder = Ink.Stroke.builder()
                for (pt in stroke) {
                    strokeBuilder.addPoint(Ink.Point.create(pt.x, pt.y, fakeTime))
                    fakeTime += 16L
                }
                inkBuilder.addStroke(strokeBuilder.build())
                fakeTime += 120L
            }

            val mlResult = client.recognize(inkBuilder.build()).await()
            val mlDigits = mlResult.candidates
                .mapNotNull { cand -> mapCandidateToSudokuDigit(cand.text) }
                .distinct()

            if (mlDigits.isEmpty()) {
                return geoDigit
            }

            val topMl = mlDigits.first()

            // Structural 6-vs-8 safeguard: if ML Kit guesses 8 on a 1-stroke open-top '6'
            // where the topological verifier has high confidence for 6, prefer 6.
            val chosenDigit = if (topMl == 8 && geoDigit.digit == 6 && geoDigit.confidence >= 0.72f) {
                6
            } else {
                topMl
            }

            val combinedAlts = (mlDigits + listOf(geoDigit.digit) + geoDigit.alternatives)
                .distinct()
                .filter { it != chosenDigit }
                .take(3)

            RecognizedInkResult.Digit(
                digit = chosenDigit,
                confidence = maxOf(0.92f, geoDigit.confidence),
                alternatives = combinedAlts
            )
        } catch (_: Exception) {
            geoDigit
        }
    }

    private fun mapCandidateToSudokuDigit(raw: String): Int? {
        val trimmed = raw.trim()
        if (trimmed.length != 1) return null
        return when (val ch = trimmed[0]) {
            in '1'..'9' -> ch - '0'
            'I', 'l', '|' -> 1
            'Z', 'z' -> 2
            'S', 's' -> 5
            'G', 'b' -> 6
            'B' -> 8
            'g', 'q' -> 9
            else -> null
        }
    }
}
