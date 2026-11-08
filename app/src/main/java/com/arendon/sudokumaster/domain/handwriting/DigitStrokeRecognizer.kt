package com.arendon.sudokumaster.domain.handwriting

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

data class InkPoint(val x: Float, val y: Float)

sealed class RecognizedInkResult {
    data class Digit(
        val digit: Int,
        val confidence: Float,
        val alternatives: List<Int> = emptyList()
    ) : RecognizedInkResult()

    data object Erase : RecognizedInkResult()
    data object Unrecognized : RecognizedInkResult()
}

/**
 * Offline stylus handwriting recognizer tailored for the Magic Tab 4 & Pencil.
 * Combines topological stroke features (aspect ratio, direction reversals, crossbar detection)
 * with normalized 32-point polyline template matching across multiple stroke variations for digits 1..9,
 * plus scribble/scratch-out erase detection.
 */
object DigitStrokeRecognizer {

    private const val NUM_RESAMPLE_POINTS = 32

    private data class DigitTemplate(
        val digit: Int,
        val normalizedPoints: List<InkPoint>
    )

    private val templates: List<DigitTemplate> by lazy { buildTemplates() }

    /**
     * Recognizes a list of stylus strokes (each stroke is a list of [InkPoint]).
     */
    fun recognize(strokes: List<List<InkPoint>>): RecognizedInkResult {
        val validStrokes = strokes.filter { it.size >= 2 }
        if (validStrokes.isEmpty()) return RecognizedInkResult.Unrecognized

        val allPoints = validStrokes.flatten()
        if (allPoints.size < 3) return RecognizedInkResult.Unrecognized

        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (p in allPoints) {
            minX = min(minX, p.x)
            maxX = max(maxX, p.x)
            minY = min(minY, p.y)
            maxY = max(maxY, p.y)
        }

        val width = maxX - minX
        val height = maxY - minY
        val diag = hypot(width.toDouble(), height.toDouble()).toFloat()
        if (diag < 8f) return RecognizedInkResult.Unrecognized

        // 1. Check for horizontal scribble / scratch-out erase gesture
        if (isScribbleErase(validStrokes, width, height)) {
            return RecognizedInkResult.Erase
        }

        // 2. Check for tall vertical single-stroke '1'
        if (validStrokes.size == 1 && height > 12f && (width / height) < 0.26f) {
            val first = validStrokes[0].first()
            val last = validStrokes[0].last()
            if (last.y > first.y) {
                return RecognizedInkResult.Digit(digit = 1, confidence = 0.96f, alternatives = listOf(7, 4))
            }
        }

        // 3. Check for classic 2-stroke digits (4, 5, 7)
        if (validStrokes.size == 2) {
            val twoStrokeMatch = classifyTwoStrokeDigit(validStrokes, minX, maxX, minY, maxY)
            if (twoStrokeMatch != null) {
                return twoStrokeMatch
            }
        }

        // 4. Normalize and match against 32-point polyline templates
        val combined = resample(allPoints, NUM_RESAMPLE_POINTS)
        val normalized = normalizeToUnitBox(combined)

        val scoresByDigit = mutableMapOf<Int, Float>()
        for (tpl in templates) {
            val dist = pathDistance(normalized, tpl.normalizedPoints)
            val score = (1f / (1f + dist * 2.4f)).coerceIn(0f, 1f)
            val currentBest = scoresByDigit[tpl.digit] ?: 0f
            if (score > currentBest) {
                scoresByDigit[tpl.digit] = score
            }
        }

        val ranked = scoresByDigit.entries.sortedByDescending { it.value }
        if (ranked.isEmpty()) return RecognizedInkResult.Unrecognized

        val best = ranked.first()
        val alts = ranked.drop(1).take(2).map { it.key }
        return RecognizedInkResult.Digit(
            digit = best.key,
            confidence = best.value,
            alternatives = alts
        )
    }

    private fun isScribbleErase(
        strokes: List<List<InkPoint>>,
        width: Float,
        height: Float
    ): Boolean {
        if (strokes.size == 1 && width > 20f && (height / width) < 0.18f) {
            // Flat horizontal strike-through
            return true
        }
        // Count sharp horizontal X-direction reversals in the primary stroke
        val mainStroke = strokes.maxByOrNull { it.size } ?: return false
        var xReversals = 0
        var lastDirX = 0
        var totalPathLen = 0f
        for (i in 1 until mainStroke.size) {
            val dx = mainStroke[i].x - mainStroke[i - 1].x
            val dy = mainStroke[i].y - mainStroke[i - 1].y
            totalPathLen += hypot(dx.toDouble(), dy.toDouble()).toFloat()
            if (abs(dx) > 4f) {
                val dir = if (dx > 0) 1 else -1
                if (lastDirX != 0 && dir != lastDirX) {
                    xReversals++
                }
                lastDirX = dir
            }
        }
        return xReversals >= 4 && totalPathLen > width * 2.6f
    }

    private fun classifyTwoStrokeDigit(
        strokes: List<List<InkPoint>>,
        minX: Float,
        maxX: Float,
        minY: Float,
        maxY: Float
    ): RecognizedInkResult.Digit? {
        val width = max(1f, maxX - minX)
        val height = max(1f, maxY - minY)
        val s1 = strokes[0]
        val s2 = strokes[1]

        val s2AvgY = (s2.map { it.y }.average().toFloat() - minY) / height
        val s2Dx = abs(s2.last().x - s2.first().x) / width
        val s2Dy = abs(s2.last().y - s2.first().y) / height

        // If second stroke is a top horizontal bar -> 5
        if (s2AvgY < 0.28f && s2Dx > 0.35f && s2Dy < 0.25f) {
            return RecognizedInkResult.Digit(digit = 5, confidence = 0.90f, alternatives = listOf(7, 4))
        }
        // If second stroke is a vertical stem cutting across an L-shape -> 4
        if (s2Dy > 0.55f && s2Dx < 0.30f) {
            val s1HasCorner = s1.any { ((it.y - minY) / height) in 0.35f..0.75f }
            if (s1HasCorner) {
                return RecognizedInkResult.Digit(digit = 4, confidence = 0.91f, alternatives = listOf(1, 7))
            }
        }
        // If second stroke is a small middle horizontal crossbar -> 7
        if (s2AvgY in 0.35f..0.68f && s2Dx in 0.25f..0.75f && s2Dy < 0.22f) {
            return RecognizedInkResult.Digit(digit = 7, confidence = 0.88f, alternatives = listOf(4, 2))
        }
        return null
    }

    private fun resample(points: List<InkPoint>, n: Int): List<InkPoint> {
        if (points.size <= 1) return List(n) { points.firstOrNull() ?: InkPoint(0f, 0f) }
        var totalLen = 0f
        for (i in 1 until points.size) {
            totalLen += dist(points[i - 1], points[i])
        }
        if (totalLen < 1e-4f) return List(n) { points.first() }

        val interval = totalLen / (n - 1)
        val result = mutableListOf(points.first())
        var accDist = 0f
        val working = points.toMutableList()

        var i = 1
        while (i < working.size) {
            val d = dist(working[i - 1], working[i])
            if (accDist + d >= interval && d > 1e-5f) {
                val t = (interval - accDist) / d
                val nx = working[i - 1].x + t * (working[i].x - working[i - 1].x)
                val ny = working[i - 1].y + t * (working[i].y - working[i - 1].y)
                val q = InkPoint(nx, ny)
                result.add(q)
                working.add(i, q)
                accDist = 0f
            } else {
                accDist += d
            }
            i++
        }
        while (result.size < n) {
            result.add(points.last())
        }
        return result.take(n)
    }

    private fun normalizeToUnitBox(points: List<InkPoint>): List<InkPoint> {
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (p in points) {
            minX = min(minX, p.x)
            maxX = max(maxX, p.x)
            minY = min(minY, p.y)
            maxY = max(maxY, p.y)
        }
        val w = max(1e-3f, maxX - minX)
        val h = max(1e-3f, maxY - minY)
        // Preserve aspect ratio moderately for narrow digits like 1 while normalizing scale
        val scale = max(w, h)
        val cx = (minX + maxX) / 2f
        val cy = (minY + maxY) / 2f
        return points.map { InkPoint((it.x - cx) / scale, (it.y - cy) / scale) }
    }

    private fun pathDistance(a: List<InkPoint>, b: List<InkPoint>): Float {
        val count = min(a.size, b.size)
        if (count == 0) return Float.MAX_VALUE
        var sum = 0f
        for (i in 0 until count) {
            sum += dist(a[i], b[i])
        }
        return sum / count
    }

    private fun dist(a: InkPoint, b: InkPoint): Float =
        hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat()

    private fun buildTemplates(): List<DigitTemplate> {
        val raw = listOf(
            // Digit 1: straight vertical & serifed 1
            1 to listOf(InkPoint(0.5f, 0.05f), InkPoint(0.5f, 0.5f), InkPoint(0.5f, 0.95f)),
            1 to listOf(InkPoint(0.35f, 0.2f), InkPoint(0.5f, 0.05f), InkPoint(0.5f, 0.95f)),

            // Digit 2: top arch, diagonal down-left, flat bottom right
            2 to listOf(
                InkPoint(0.15f, 0.25f), InkPoint(0.5f, 0.05f), InkPoint(0.85f, 0.22f),
                InkPoint(0.75f, 0.45f), InkPoint(0.15f, 0.92f), InkPoint(0.88f, 0.92f)
            ),

            // Digit 3: two right-facing loops
            3 to listOf(
                InkPoint(0.18f, 0.15f), InkPoint(0.6f, 0.05f), InkPoint(0.85f, 0.25f),
                InkPoint(0.35f, 0.48f), InkPoint(0.88f, 0.72f), InkPoint(0.55f, 0.95f),
                InkPoint(0.15f, 0.85f)
            ),

            // Digit 4: down-left to middle, across right, and stem down
            4 to listOf(
                InkPoint(0.65f, 0.08f), InkPoint(0.12f, 0.62f), InkPoint(0.90f, 0.62f),
                InkPoint(0.65f, 0.08f), InkPoint(0.65f, 0.95f)
            ),
            4 to listOf(
                InkPoint(0.2f, 0.08f), InkPoint(0.2f, 0.55f), InkPoint(0.85f, 0.55f),
                InkPoint(0.7f, 0.08f), InkPoint(0.7f, 0.95f)
            ),

            // Digit 5: top bar right-to-left, down to mid, bottom right loop
            5 to listOf(
                InkPoint(0.82f, 0.08f), InkPoint(0.20f, 0.08f), InkPoint(0.18f, 0.45f),
                InkPoint(0.65f, 0.42f), InkPoint(0.88f, 0.68f), InkPoint(0.55f, 0.94f),
                InkPoint(0.15f, 0.85f)
            ),

            // Digit 6: top-right curve down to bottom closed loop
            6 to listOf(
                InkPoint(0.78f, 0.08f), InkPoint(0.30f, 0.30f), InkPoint(0.15f, 0.68f),
                InkPoint(0.48f, 0.95f), InkPoint(0.85f, 0.72f), InkPoint(0.55f, 0.48f),
                InkPoint(0.18f, 0.65f)
            ),

            // Digit 7: top horizontal left-to-right, then diagonal down-left
            7 to listOf(
                InkPoint(0.12f, 0.08f), InkPoint(0.88f, 0.08f),
                InkPoint(0.55f, 0.52f), InkPoint(0.32f, 0.95f)
            ),

            // Digit 8: figure-eight S-curve up-and-closed
            8 to listOf(
                InkPoint(0.50f, 0.06f), InkPoint(0.18f, 0.24f), InkPoint(0.50f, 0.48f),
                InkPoint(0.84f, 0.74f), InkPoint(0.50f, 0.95f), InkPoint(0.16f, 0.74f),
                InkPoint(0.50f, 0.48f), InkPoint(0.82f, 0.24f), InkPoint(0.50f, 0.06f)
            ),

            // Digit 9: top loop then right stem down
            9 to listOf(
                InkPoint(0.82f, 0.28f), InkPoint(0.50f, 0.06f), InkPoint(0.16f, 0.28f),
                InkPoint(0.48f, 0.50f), InkPoint(0.82f, 0.28f), InkPoint(0.80f, 0.65f),
                InkPoint(0.45f, 0.95f)
            ),
            9 to listOf(
                InkPoint(0.82f, 0.28f), InkPoint(0.50f, 0.06f), InkPoint(0.16f, 0.28f),
                InkPoint(0.48f, 0.50f), InkPoint(0.82f, 0.28f), InkPoint(0.82f, 0.95f)
            )
        )

        return raw.map { (digit, pts) ->
            DigitTemplate(
                digit = digit,
                normalizedPoints = normalizeToUnitBox(resample(pts, NUM_RESAMPLE_POINTS))
            )
        }
    }
}
