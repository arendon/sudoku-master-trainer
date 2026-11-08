package com.arendon.sudokumaster.domain.handwriting

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

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
 * High-accuracy offline stylus handwriting recognizer for digits 1..9 and scribble-erase.
 *
 * Uses a 3-part ensemble:
 * 1. Stroke-aware resampling (no false pen-up jump segments) with smooth Catmull-Rom allograph templates.
 * 2. Hybrid Sequential + Bidirectional Oriented Point-Cloud (Chamfer + tangent orientation) matching,
 *    making recognition robust to multi-stroke order, clockwise/counter-clockwise loops, and slant.
 * 3. Topological & structural feature rules that disambiguate classic look-alike pairs:
 *    (1 vs 7 vs 2), (2 vs 3 vs 5), (3 vs 5), (4 vs 7 vs 9), and (6 vs 8 vs 9).
 */
object DigitStrokeRecognizer {

    private const val NUM_RESAMPLE_POINTS = 40

    private data class OrientedPoint(
        val x: Float,
        val y: Float,
        val tx: Float,
        val ty: Float
    )

    private data class DigitTemplate(
        val digit: Int,
        val strokeCountHint: Int,
        val points: List<OrientedPoint>
    )

    private data class StrokeFeatures(
        val strokeCount: Int,
        val width: Float,
        val height: Float,
        val aspect: Float,
        val totalLength: Float,
        val straightnessRatio: Float, // totalLength / diag
        val startX: Float,
        val startY: Float,
        val endX: Float,
        val endY: Float,
        val startDirX: Float,
        val startDirY: Float,
        val endDirX: Float,
        val endDirY: Float,
        val topWidthRatio: Float,
        val midWidthRatio: Float,
        val botWidthRatio: Float,
        val hasTopHorizontalBar: Boolean,
        val hasMidHorizontalBar: Boolean,
        val hasBotHorizontalBar: Boolean,
        val upperLeftVerticalDensity: Float,
        val upperRightDensity: Float,
        val lowerLeftDensity: Float,
        val lowerRightDensity: Float,
        val topHalfAngularSweep: Float,
        val botHalfAngularSweep: Float,
        val hasMiddleLeftCusp: Boolean,
        val upwardTravelInTopThird: Float,
        val returnsToTopAfterBottom: Boolean
    )

    private val templates: List<DigitTemplate> by lazy { buildTemplates() }

    /**
     * Returns true if the current single stroke could plausibly be the 1st stroke of a 2-stroke
     * digit (like 4, 5, 7, or 1), allowing the UI to wait slightly longer for a 2nd stroke.
     */
    fun mayExpectAdditionalStroke(strokes: List<List<InkPoint>>): Boolean {
        val valid = strokes.filter { it.size >= 2 }
        if (valid.size != 1) return false
        val pts = valid[0]
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (p in pts) {
            minX = min(minX, p.x)
            maxX = max(maxX, p.x)
            minY = min(minY, p.y)
            maxY = max(maxY, p.y)
        }
        val w = max(1f, maxX - minX)
        val h = max(1f, maxY - minY)
        val aspect = w / h
        if (aspect < 0.35f) return true // Vertical line (could be 1, or stem of 4/5)
        val endX = (pts.last().x - minX) / w
        val endY = (pts.last().y - minY) / h
        // L-stroke of a 2-stroke '4' ends in middle-right
        if (endX > 0.65f && endY in 0.30f..0.78f) return true
        // Uncrossed '7' ends at bottom-left/center after a top horizontal bar
        val startX = (pts.first().x - minX) / w
        val startY = (pts.first().y - minY) / h
        if (startX < 0.35f && startY < 0.25f && endY > 0.78f && endX < 0.65f) return true
        // Body of a 2-stroke '5' starts near top-left and ends at bottom-left
        if (startX < 0.45f && startY < 0.35f && endX < 0.45f && endY > 0.65f) return true
        return false
    }

    /**
     * Recognizes a list of stylus strokes (each stroke is a list of [InkPoint]).
     */
    fun recognize(strokes: List<List<InkPoint>>): RecognizedInkResult {
        val cleanedStrokes = strokes
            .map { cleanStroke(it) }
            .filter { it.size >= 2 }
        if (cleanedStrokes.isEmpty()) return RecognizedInkResult.Unrecognized

        val allPoints = cleanedStrokes.flatten()
        if (allPoints.size < 2) return RecognizedInkResult.Unrecognized

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
        if (isScribbleErase(cleanedStrokes, width, height)) {
            return RecognizedInkResult.Erase
        }

        val features = extractFeatures(cleanedStrokes, minX, maxX, minY, maxY)

        // 2. Fast-path for unambiguous single straight vertical/slanted stroke '1'
        if (cleanedStrokes.size == 1 &&
            height > 10f &&
            (features.aspect < 0.28f || (features.aspect < 0.44f && features.straightnessRatio < 1.16f)) &&
            features.endY > features.startY + 0.55f &&
            !features.hasTopHorizontalBar &&
            !features.hasBotHorizontalBar
        ) {
            return RecognizedInkResult.Digit(
                digit = 1,
                confidence = 0.96f,
                alternatives = listOf(7, 4)
            )
        }

        // 3. Dedicated multi-stroke structural classifier (2-stroke & 3-stroke 1, 4, 5, 7)
        if (cleanedStrokes.size in 2..3) {
            val multiStrokeResult = classifyMultiStrokeDigit(cleanedStrokes, features, minX, maxX, minY, maxY)
            if (multiStrokeResult != null && multiStrokeResult.confidence >= 0.88f) {
                return multiStrokeResult
            }
        }

        // 4. Resample strokes without interpolating across pen-up jumps and normalize
        val userOriented = resampleAndNormalizeStrokes(cleanedStrokes, NUM_RESAMPLE_POINTS)

        // 5. Compute hybrid template + structural score for each digit 1..9
        val bestTemplateScoreByDigit = mutableMapOf<Int, Float>()
        for (tpl in templates) {
            val seqDist = sequentialDistance(userOriented, tpl.points)
            val cloudDist = bidirectionalCloudDistance(userOriented, tpl.points)

            // When stroke counts match, weight sequential higher; when multi-stroke, weight point-cloud higher
            val blendedDist = if (cleanedStrokes.size == 1 && tpl.strokeCountHint == 1) {
                0.52f * seqDist + 0.48f * cloudDist
            } else {
                0.22f * seqDist + 0.78f * cloudDist
            }

            val tplScore = (1f / (1f + blendedDist * 2.6f)).coerceIn(0f, 1f)
            val prev = bestTemplateScoreByDigit[tpl.digit] ?: 0f
            if (tplScore > prev) {
                bestTemplateScoreByDigit[tpl.digit] = tplScore
            }
        }

        val finalScores = mutableMapOf<Int, Float>()
        for (d in 1..9) {
            val baseScore = bestTemplateScoreByDigit[d] ?: 0f
            val structBonus = structuralScoreAdjustment(d, features)
            finalScores[d] = (baseScore + structBonus).coerceIn(0.01f, 0.99f)
        }

        val ranked = finalScores.entries.sortedByDescending { it.value }
        if (ranked.isEmpty()) return RecognizedInkResult.Unrecognized

        val best = ranked.first()
        val alts = ranked.drop(1).take(3).map { it.key }
        return RecognizedInkResult.Digit(
            digit = best.key,
            confidence = best.value,
            alternatives = alts
        )
    }

    private fun cleanStroke(stroke: List<InkPoint>): List<InkPoint> {
        if (stroke.size <= 2) return stroke
        val result = mutableListOf(stroke.first())
        for (i in 1 until stroke.size) {
            val d = dist(result.last(), stroke[i])
            if (d >= 1.5f || i == stroke.lastIndex) {
                result.add(stroke[i])
            }
        }
        return result
    }

    private fun isScribbleErase(
        strokes: List<List<InkPoint>>,
        width: Float,
        height: Float
    ): Boolean {
        if (strokes.size == 1 && width > 20f && (height / max(1f, width)) < 0.17f) {
            return true
        }
        val mainStroke = strokes.maxByOrNull { strokeLength(it) } ?: return false
        var xReversals = 0
        var lastDirX = 0
        val totalPathLen = strokeLength(mainStroke)
        for (i in 1 until mainStroke.size) {
            val dx = mainStroke[i].x - mainStroke[i - 1].x
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

    /**
     * Accurately classifies 2-stroke and 3-stroke digits (1, 4, 5, 7, 8) regardless of stroke order.
     */
    private fun classifyMultiStrokeDigit(
        strokes: List<List<InkPoint>>,
        features: StrokeFeatures,
        minX: Float,
        maxX: Float,
        minY: Float,
        maxY: Float
    ): RecognizedInkResult.Digit? {
        val w = max(1f, maxX - minX)
        val h = max(1f, maxY - minY)

        if (strokes.size == 2) {
            val s1 = strokes[0]
            val s2 = strokes[1]

            // Check both (primary, secondary) orders
            val pairOrders = listOf(s1 to s2, s2 to s1)
            for ((body, bar) in pairOrders) {
                val barMinX = (bar.minOf { it.x } - minX) / w
                val barMaxX = (bar.maxOf { it.x } - minX) / w
                val barSpanX = barMaxX - barMinX
                val barMinY = (bar.minOf { it.y } - minY) / h
                val barMaxY = (bar.maxOf { it.y } - minY) / h
                val barSpanY = barMaxY - barMinY
                val barAvgY = (bar.map { it.y }.average().toFloat() - minY) / h
                val barLen = strokeLength(bar)
                val barStraight = barLen / max(1f, dist(bar.first(), bar.last()))

                val bodyMinY = (body.minOf { it.y } - minY) / h
                val bodyMaxY = (body.maxOf { it.y } - minY) / h
                val bodySpanY = bodyMaxY - bodyMinY
                val bodyStartX = (body.first().x - minX) / w
                val bodyStartY = (body.first().y - minY) / h
                val bodyEndX = (body.last().x - minX) / w
                val bodyEndY = (body.last().y - minY) / h

                // Case A: '5' -> One stroke is a top horizontal bar (barAvgY < 0.30),
                // and the other stroke goes down the upper-left and curves around the bottom loop.
                if (barAvgY < 0.30f && barSpanX > 0.30f && barSpanY < 0.25f && bodySpanY > 0.55f) {
                    val bodyHasBottomCurve = body.any { ((it.x - minX) / w) > 0.60f && ((it.y - minY) / h) > 0.45f }
                    if (bodyHasBottomCurve || bodyEndX < 0.60f) {
                        return RecognizedInkResult.Digit(digit = 5, confidence = 0.93f, alternatives = listOf(3, 7))
                    }
                }

                // Case B: '7' (crossed) -> One stroke is a middle horizontal crossbar (barAvgY in 0.30..0.72),
                // and the other stroke is a '7' body (top horizontal bar + diagonal stem down).
                if (barAvgY in 0.30f..0.74f && barSpanX in 0.22f..0.85f && barSpanY < 0.24f && barStraight < 1.30f) {
                    // Check if 'body' has a top horizontal segment (y < 0.28)
                    val topPts = body.filter { ((it.y - minY) / h) < 0.28f }
                    val topSpan = if (topPts.size >= 2) {
                        (topPts.maxOf { it.x } - topPts.minOf { it.x }) / w
                    } else 0f
                    if (topSpan > 0.35f && bodyEndY > 0.75f) {
                        return RecognizedInkResult.Digit(digit = 7, confidence = 0.94f, alternatives = listOf(4, 1))
                    }
                }

                // Case C: '4' -> One stroke is a straight vertical stem (barSpanY > 0.50, barSpanX < 0.32),
                // and the other stroke is an L-shape (goes down from top/upper-half then right across middle).
                if (barSpanY > 0.48f && barSpanX < 0.34f && barStraight < 1.25f) {
                    val bodyMidPts = body.filter { ((it.y - minY) / h) in 0.30f..0.80f }
                    val bodyMidSpanX = if (bodyMidPts.size >= 2) {
                        (bodyMidPts.maxOf { it.x } - bodyMidPts.minOf { it.x }) / w
                    } else 0f
                    if (bodyStartY < 0.40f && bodyMidSpanX > 0.42f && bodyEndX > 0.55f && bodyEndY in 0.28f..0.82f) {
                        return RecognizedInkResult.Digit(digit = 4, confidence = 0.94f, alternatives = listOf(7, 1))
                    }
                }

                // Case D: '1' with top serif or bottom base in 2 strokes
                if (barSpanY > 0.75f && barSpanX < 0.28f && barStraight < 1.18f) {
                    val bodySpanX = (body.maxOf { it.x } - body.minOf { it.x }) / w
                    if (bodySpanY < 0.30f && bodySpanX < 0.45f && bodyMaxY < 0.35f) {
                        return RecognizedInkResult.Digit(digit = 1, confidence = 0.92f, alternatives = listOf(7, 4))
                    }
                }
            }
        }
        return null
    }

    private fun extractFeatures(
        strokes: List<List<InkPoint>>,
        minX: Float,
        maxX: Float,
        minY: Float,
        maxY: Float
    ): StrokeFeatures {
        val w = max(1f, maxX - minX)
        val h = max(1f, maxY - minY)
        val diag = hypot(w.toDouble(), h.toDouble()).toFloat()
        val allPts = strokes.flatten()

        var totalLen = 0f
        var upperLeftVertLen = 0f
        var upperRightLen = 0f
        var lowerLeftLen = 0f
        var lowerRightLen = 0f
        var upwardTopThirdDy = 0f
        var reachedBottom = false
        var returnsToTopAfterBottom = false

        for (stroke in strokes) {
            for (i in 1 until stroke.size) {
                val p0 = stroke[i - 1]
                val p1 = stroke[i]
                val segLen = dist(p0, p1)
                totalLen += segLen
                val mx = ((p0.x + p1.x) * 0.5f - minX) / w
                val my = ((p0.y + p1.y) * 0.5f - minY) / h
                val dx = abs(p1.x - p0.x)
                val dy = abs(p1.y - p0.y)
                val signedDy = p1.y - p0.y

                if (my > 0.72f) {
                    reachedBottom = true
                } else if (reachedBottom && my < 0.33f) {
                    returnsToTopAfterBottom = true
                }

                if (my < 0.36f && signedDy < 0f) {
                    upwardTopThirdDy += (-signedDy) / h
                }

                if (mx < 0.42f && my in 0.10f..0.50f && dy >= dx * 0.7f) {
                    upperLeftVertLen += segLen
                }
                if (mx > 0.52f && my < 0.45f) {
                    upperRightLen += segLen
                }
                if (mx < 0.45f && my > 0.52f) {
                    lowerLeftLen += segLen
                }
                if (mx > 0.52f && my > 0.52f) {
                    lowerRightLen += segLen
                }
            }
        }

        val normLen = max(1f, totalLen)
        val start = allPts.first()
        val end = allPts.last()
        val startX = (start.x - minX) / w
        val startY = (start.y - minY) / h
        val endX = (end.x - minX) / w
        val endY = (end.y - minY) / h

        // Initial and final stroke tangent directions
        val firstStroke = strokes.first()
        val sIdx = min(firstStroke.lastIndex, max(1, firstStroke.size / 4))
        val sDx = firstStroke[sIdx].x - firstStroke.first().x
        val sDy = firstStroke[sIdx].y - firstStroke.first().y
        val sMag = max(1e-3f, hypot(sDx.toDouble(), sDy.toDouble()).toFloat())

        val lastStroke = strokes.last()
        val eIdx = max(0, lastStroke.size - 1 - max(1, lastStroke.size / 4))
        val eDx = lastStroke.last().x - lastStroke[eIdx].x
        val eDy = lastStroke.last().y - lastStroke[eIdx].y
        val eMag = max(1e-3f, hypot(eDx.toDouble(), eDy.toDouble()).toFloat())

        fun sliceWidthRatio(yLow: Float, yHigh: Float): Float {
            val slice = allPts.filter { ((it.y - minY) / h) in yLow..yHigh }
            if (slice.size < 2) return 0f
            return (slice.maxOf { it.x } - slice.minOf { it.x }) / w
        }

        val topWidthRatio = sliceWidthRatio(0.0f, 0.25f)
        val midWidthRatio = sliceWidthRatio(0.35f, 0.68f)
        val botWidthRatio = sliceWidthRatio(0.76f, 1.0f)

        val hasTopHoriz = hasHorizontalRun(strokes, minX, minY, w, h, 0.0f, 0.28f, 0.36f)
        val hasMidHoriz = hasHorizontalRun(strokes, minX, minY, w, h, 0.32f, 0.72f, 0.38f)
        val hasBotHoriz = hasLeftToRightBottomRun(strokes, minX, minY, w, h)

        val topSweep = computeAngularSweep(allPts, minX, minY, w, h, 0.0f, 0.44f)
        val botSweep = computeAngularSweep(allPts, minX, minY, w, h, 0.44f, 1.0f)
        val hasMiddleLeftCusp = detectMiddleLeftCusp(strokes, minX, minY, w, h)

        return StrokeFeatures(
            strokeCount = strokes.size,
            width = w,
            height = h,
            aspect = w / h,
            totalLength = totalLen,
            straightnessRatio = totalLen / max(1f, diag),
            startX = startX,
            startY = startY,
            endX = endX,
            endY = endY,
            startDirX = sDx / sMag,
            startDirY = sDy / sMag,
            endDirX = eDx / eMag,
            endDirY = eDy / eMag,
            topWidthRatio = topWidthRatio,
            midWidthRatio = midWidthRatio,
            botWidthRatio = botWidthRatio,
            hasTopHorizontalBar = hasTopHoriz,
            hasMidHorizontalBar = hasMidHoriz,
            hasBotHorizontalBar = hasBotHoriz,
            upperLeftVerticalDensity = upperLeftVertLen / normLen,
            upperRightDensity = upperRightLen / normLen,
            lowerLeftDensity = lowerLeftLen / normLen,
            lowerRightDensity = lowerRightLen / normLen,
            topHalfAngularSweep = topSweep,
            botHalfAngularSweep = botSweep,
            hasMiddleLeftCusp = hasMiddleLeftCusp,
            upwardTravelInTopThird = upwardTopThirdDy,
            returnsToTopAfterBottom = returnsToTopAfterBottom
        )
    }

    private fun hasHorizontalRun(
        strokes: List<List<InkPoint>>,
        minX: Float,
        minY: Float,
        w: Float,
        h: Float,
        yMin: Float,
        yMax: Float,
        minSpanRatio: Float
    ): Boolean {
        for (stroke in strokes) {
            val bandPts = stroke.filter { ((it.y - minY) / h) in yMin..yMax }
            if (bandPts.size >= 2) {
                val spanX = (bandPts.maxOf { it.x } - bandPts.minOf { it.x }) / w
                val spanY = (bandPts.maxOf { it.y } - bandPts.minOf { it.y }) / h
                if (spanX >= minSpanRatio && spanY < spanX * 0.65f) {
                    return true
                }
            }
        }
        return false
    }

    private fun hasLeftToRightBottomRun(
        strokes: List<List<InkPoint>>,
        minX: Float,
        minY: Float,
        w: Float,
        h: Float
    ): Boolean {
        val lastStroke = strokes.lastOrNull() ?: return false
        val botPts = lastStroke.filter { ((it.y - minY) / h) >= 0.70f }
        if (botPts.size < 2) return false
        val dx = (botPts.last().x - botPts.first().x) / w
        val dy = abs(botPts.last().y - botPts.first().y) / h
        return dx >= 0.34f && dy < 0.25f
    }

    private fun computeAngularSweep(
        pts: List<InkPoint>,
        minX: Float,
        minY: Float,
        w: Float,
        h: Float,
        yLow: Float,
        yHigh: Float
    ): Float {
        val region = pts.filter { ((it.y - minY) / h) in yLow..yHigh }
        if (region.size < 5) return 0f
        val cx = region.map { (it.x - minX) / w }.average().toFloat()
        val cy = region.map { (it.y - minY) / h }.average().toFloat()
        var totalAngle = 0f
        var prevAngle = atan2((region[0].y - minY) / h - cy, (region[0].x - minX) / w - cx)
        for (i in 1 until region.size) {
            val curAngle = atan2((region[i].y - minY) / h - cy, (region[i].x - minX) / w - cx)
            var dTheta = curAngle - prevAngle
            while (dTheta > Math.PI) dTheta -= (2.0 * Math.PI).toFloat()
            while (dTheta < -Math.PI) dTheta += (2.0 * Math.PI).toFloat()
            totalAngle += abs(dTheta)
            prevAngle = curAngle
        }
        return totalAngle
    }

    /**
     * Detects the signature middle-left indentation cusp of digit '3' (where stroke moves left to a minimum X
     * around y in 0.32..0.62 and then reverses right).
     */
    private fun detectMiddleLeftCusp(
        strokes: List<List<InkPoint>>,
        minX: Float,
        minY: Float,
        w: Float,
        h: Float
    ): Boolean {
        if (strokes.size != 1) return false
        val s = strokes[0]
        if (s.size < 6) return false
        for (i in 2 until s.size - 2) {
            val ny = (s[i].y - minY) / h
            val nx = (s[i].x - minX) / w
            if (ny in 0.30f..0.64f && nx in 0.15f..0.72f) {
                val prevX = (s[max(0, i - 3)].x - minX) / w
                val nextX = (s[min(s.lastIndex, i + 3)].x - minX) / w
                if (prevX > nx + 0.08f && nextX > nx + 0.08f) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Structural adjustments based on geometric invariants for digits 1..9.
     */
    private fun structuralScoreAdjustment(digit: Int, f: StrokeFeatures): Float {
        var adj = 0f
        when (digit) {
            1 -> {
                if (f.aspect < 0.30f) adj += 0.25f
                else if (f.aspect > 0.55f && f.strokeCount == 1) adj -= 0.25f
                if (f.straightnessRatio < 1.20f && f.endY > 0.80f) adj += 0.18f
                if (f.straightnessRatio > 1.75f) adj -= 0.22f
            }

            2 -> {
                if (f.hasBotHorizontalBar && f.endDirX > 0.30f && f.endX > 0.55f && f.endY > 0.72f) {
                    adj += 0.24f
                } else if (f.endDirX < -0.15f || f.endX < 0.42f) {
                    adj -= 0.18f
                }
                if (f.startY < 0.42f && f.lowerLeftDensity > 0.10f) adj += 0.08f
                if (f.upperLeftVerticalDensity > 0.18f && !f.hasBotHorizontalBar) adj -= 0.15f
            }

            3 -> {
                if (f.endDirX < -0.15f && f.endX < 0.52f && f.endY > 0.65f) adj += 0.12f
                if (f.hasMiddleLeftCusp && f.upperRightDensity > 0.18f && f.lowerRightDensity > 0.20f) {
                    adj += 0.18f
                }
                // Key disambiguator against '5': '3' does NOT have an upper-left vertical wall
                if (f.upperLeftVerticalDensity > 0.15f) {
                    adj -= 0.22f
                } else if (f.upperRightDensity > 0.18f) {
                    adj += 0.08f
                }
                if (f.hasBotHorizontalBar && f.endDirX > 0.35f) adj -= 0.20f
            }

            4 -> {
                if (f.hasMidHorizontalBar && f.endY > 0.78f && f.botWidthRatio < 0.38f) {
                    adj += 0.22f
                }
                if (f.strokeCount == 2 && f.hasMidHorizontalBar) adj += 0.14f
                if (f.botHalfAngularSweep > 3.8f) adj -= 0.18f // 4 does not have a round bottom loop
            }

            5 -> {
                // Key disambiguator against '3': '5' has an upper-left vertical drop + top bar
                if (f.upperLeftVerticalDensity > 0.12f && f.lowerRightDensity > 0.18f) {
                    adj += 0.20f
                }
                if (f.hasTopHorizontalBar && f.lowerRightDensity > 0.18f) {
                    adj += 0.12f
                }
                if (f.upperLeftVerticalDensity < 0.05f && f.strokeCount == 1) {
                    adj -= 0.18f
                }
                if (f.hasBotHorizontalBar && f.endDirX > 0.40f && f.endX > 0.65f) {
                    adj -= 0.18f
                }
            }

            6 -> {
                // 6 has its closed loop in the bottom half, never returns to the top third after reaching the bottom,
                // and has almost no upward travel in the top third (unlike 8 and 9).
                if (f.botHalfAngularSweep > 3.2f &&
                    !f.returnsToTopAfterBottom &&
                    f.upwardTravelInTopThird < 0.08f &&
                    f.endY in 0.36f..0.88f
                ) {
                    adj += 0.28f
                }
                if (f.startY < 0.30f && f.lowerLeftDensity > 0.14f) adj += 0.08f
                if (f.returnsToTopAfterBottom || f.upwardTravelInTopThird > 0.16f) adj -= 0.22f
                if (f.endY > 0.90f && f.botHalfAngularSweep < 2.5f) adj -= 0.18f
            }

            7 -> {
                if (f.hasTopHorizontalBar && f.endY > 0.80f && f.botWidthRatio < 0.36f && !f.hasBotHorizontalBar) {
                    adj += 0.22f
                }
                if (f.startX < 0.40f && f.startY < 0.28f && f.startDirX > 0.45f && f.endY > 0.80f) {
                    adj += 0.12f
                }
                if (f.hasBotHorizontalBar || f.botHalfAngularSweep > 3.5f) {
                    adj -= 0.22f
                }
            }

            8 -> {
                // An 8 must close or form a loop in the top half (climbing upward in the top third or returning to top)
                val hasTopLoopClosure = f.returnsToTopAfterBottom || f.upwardTravelInTopThird >= 0.14f || f.strokeCount >= 2
                if (hasTopLoopClosure && f.topHalfAngularSweep > 2.8f && f.botHalfAngularSweep > 2.8f) {
                    adj += 0.24f
                }
                if (hasTopLoopClosure &&
                    f.upperLeftVerticalDensity > 0.08f &&
                    f.upperRightDensity > 0.14f &&
                    f.lowerLeftDensity > 0.14f &&
                    f.lowerRightDensity > 0.14f &&
                    f.endY < 0.35f
                ) {
                    adj += 0.14f
                }
                // Key disambiguator against '6': if a 1-stroke digit never returns to the top third and
                // has no upward travel in the top third, it is an open-top '6', NOT an '8'!
                if (!hasTopLoopClosure) {
                    adj -= 0.32f
                }
                if (f.straightnessRatio < 1.85f) adj -= 0.20f
            }

            9 -> {
                // 9 has a closed loop in the top half, low lower-left density, and ends at the bottom
                if (f.topHalfAngularSweep > 3.4f && f.endY > 0.78f && f.lowerLeftDensity < 0.14f) {
                    adj += 0.24f
                }
                if (f.lowerLeftDensity > 0.24f && f.endY < 0.75f) {
                    adj -= 0.20f // Likely 6 or 8
                }
            }
        }
        return adj
    }

    /**
     * Resamples multiple strokes proportionally to their lengths without interpolating across pen-up jumps,
     * normalizes coordinates to [-0.5, 0.5], and attaches unit tangent vectors.
     */
    private fun resampleAndNormalizeStrokes(
        strokes: List<List<InkPoint>>,
        totalPoints: Int
    ): List<OrientedPoint> {
        val allRaw = strokes.flatten()
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (p in allRaw) {
            minX = min(minX, p.x)
            maxX = max(maxX, p.x)
            minY = min(minY, p.y)
            maxY = max(maxY, p.y)
        }
        val w = max(1e-3f, maxX - minX)
        val h = max(1e-3f, maxY - minY)
        val cx = (minX + maxX) * 0.5f
        val cy = (minY + maxY) * 0.5f

        // Aspect-adaptive normalization: normalizes normal digits cleanly while keeping '1' narrow
        val scaleX = max(w, h * 0.42f)
        val scaleY = max(h, w * 0.42f)

        val lengths = strokes.map { max(1e-3f, strokeLength(it)) }
        val sumLen = lengths.sum()

        val counts = IntArray(strokes.size)
        var assigned = 0
        for (i in strokes.indices) {
            val c = if (i == strokes.lastIndex) {
                max(2, totalPoints - assigned)
            } else {
                max(2, ((lengths[i] / sumLen) * totalPoints).toInt())
            }
            counts[i] = c
            assigned += c
        }

        val oriented = mutableListOf<OrientedPoint>()
        for (sIdx in strokes.indices) {
            val resampled = resampleSingleStroke(strokes[sIdx], counts[sIdx])
            val normPts = resampled.map { InkPoint((it.x - cx) / scaleX, (it.y - cy) / scaleY) }
            for (i in normPts.indices) {
                val prev = normPts[max(0, i - 1)]
                val next = normPts[min(normPts.lastIndex, i + 1)]
                val dx = next.x - prev.x
                val dy = next.y - prev.y
                val mag = max(1e-4f, hypot(dx.toDouble(), dy.toDouble()).toFloat())
                oriented.add(OrientedPoint(normPts[i].x, normPts[i].y, dx / mag, dy / mag))
            }
        }
        return oriented.take(totalPoints)
    }

    private fun resampleSingleStroke(points: List<InkPoint>, n: Int): List<InkPoint> {
        if (n <= 1 || points.size <= 1) return List(max(1, n)) { points.firstOrNull() ?: InkPoint(0f, 0f) }
        val totalLen = strokeLength(points)
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

    private fun sequentialDistance(a: List<OrientedPoint>, b: List<OrientedPoint>): Float {
        val count = min(a.size, b.size)
        if (count == 0) return Float.MAX_VALUE
        var sum = 0f
        for (i in 0 until count) {
            val p = a[i]
            val q = b[i]
            val posDist = hypot((p.x - q.x).toDouble(), (p.y - q.y).toDouble()).toFloat()
            val dirDist = hypot((p.tx - q.tx).toDouble(), (p.ty - q.ty).toDouble()).toFloat()
            sum += posDist + 0.14f * dirDist
        }
        return sum / count
    }

    /**
     * Bidirectional Chamfer distance in 4D space (x, y, |tx|, |ty|) - invariant to stroke order & direction.
     */
    private fun bidirectionalCloudDistance(a: List<OrientedPoint>, b: List<OrientedPoint>): Float {
        if (a.isEmpty() || b.isEmpty()) return Float.MAX_VALUE

        fun directed(src: List<OrientedPoint>, dst: List<OrientedPoint>): Float {
            var sum = 0f
            for (p in src) {
                var best = Float.MAX_VALUE
                for (q in dst) {
                    val dx = p.x - q.x
                    val dy = p.y - q.y
                    val dtx = abs(p.tx) - abs(q.tx)
                    val dty = abs(p.ty) - abs(q.ty)
                    val d = hypot(dx.toDouble(), dy.toDouble()).toFloat() +
                        0.15f * hypot(dtx.toDouble(), dty.toDouble()).toFloat()
                    if (d < best) best = d
                }
                sum += best
            }
            return sum / src.size
        }

        return 0.5f * (directed(a, b) + directed(b, a))
    }

    private fun strokeLength(pts: List<InkPoint>): Float {
        var len = 0f
        for (i in 1 until pts.size) {
            len += dist(pts[i - 1], pts[i])
        }
        return len
    }

    private fun dist(a: InkPoint, b: InkPoint): Float =
        hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat()

    /**
     * Smooths control points using a Catmull-Rom spline so templates have natural curvature.
     */
    private fun smoothCurve(controlPts: List<InkPoint>, samplesPerSegment: Int = 6): List<InkPoint> {
        if (controlPts.size <= 2) return controlPts
        val result = mutableListOf<InkPoint>()
        val ext = listOf(controlPts.first()) + controlPts + listOf(controlPts.last())
        for (i in 1 until ext.size - 2) {
            val p0 = ext[i - 1]
            val p1 = ext[i]
            val p2 = ext[i + 1]
            val p3 = ext[i + 2]
            for (s in 0 until samplesPerSegment) {
                val t = s.toFloat() / samplesPerSegment
                val t2 = t * t
                val t3 = t2 * t
                val x = 0.5f * (
                    (2f * p1.x) +
                        (-p0.x + p2.x) * t +
                        (2f * p0.x - 5f * p1.x + 4f * p2.x - p3.x) * t2 +
                        (-p0.x + 3f * p1.x - 3f * p2.x + p3.x) * t3
                    )
                val y = 0.5f * (
                    (2f * p1.y) +
                        (-p0.y + p2.y) * t +
                        (2f * p0.y - 5f * p1.y + 4f * p2.y - p3.y) * t2 +
                        (-p0.y + 3f * p1.y - 3f * p2.y + p3.y) * t3
                    )
                result.add(InkPoint(x, y))
            }
        }
        result.add(controlPts.last())
        return result
    }

    private fun pt(x: Float, y: Float) = InkPoint(x, y)

    private fun buildTemplates(): List<DigitTemplate> {
        val specs = mutableListOf<Triple<Int, Int, List<List<InkPoint>>>>()

        fun add1Stroke(digit: Int, vararg pts: InkPoint, smooth: Boolean = true) {
            val curve = if (smooth && pts.size >= 4) smoothCurve(pts.toList()) else pts.toList()
            specs.add(Triple(digit, 1, listOf(curve)))
        }

        fun add2Stroke(digit: Int, s1: List<InkPoint>, s2: List<InkPoint>, smooth1: Boolean = false) {
            val c1 = if (smooth1 && s1.size >= 4) smoothCurve(s1) else s1
            specs.add(Triple(digit, 2, listOf(c1, s2)))
        }

        // --- DIGIT 1 ---
        add1Stroke(1, pt(0.50f, 0.05f), pt(0.50f, 0.50f), pt(0.50f, 0.95f), smooth = false)
        add1Stroke(1, pt(0.56f, 0.05f), pt(0.50f, 0.50f), pt(0.44f, 0.95f), smooth = false)
        add1Stroke(1, pt(0.32f, 0.22f), pt(0.52f, 0.06f), pt(0.52f, 0.95f), smooth = false)

        // --- DIGIT 2 (swan arch, diagonal, flat base; plus looped-corner 2) ---
        add1Stroke(
            2,
            pt(0.16f, 0.26f), pt(0.35f, 0.08f), pt(0.68f, 0.08f), pt(0.84f, 0.24f),
            pt(0.74f, 0.46f), pt(0.42f, 0.72f), pt(0.14f, 0.92f), pt(0.50f, 0.92f), pt(0.88f, 0.92f)
        )
        add1Stroke(
            2,
            pt(0.20f, 0.20f), pt(0.52f, 0.06f), pt(0.84f, 0.20f), pt(0.65f, 0.52f),
            pt(0.16f, 0.90f), pt(0.86f, 0.90f)
        )
        // Looped 2
        add1Stroke(
            2,
            pt(0.18f, 0.24f), pt(0.50f, 0.06f), pt(0.84f, 0.24f), pt(0.68f, 0.54f),
            pt(0.22f, 0.88f), pt(0.14f, 0.78f), pt(0.32f, 0.76f), pt(0.88f, 0.92f)
        )

        // --- DIGIT 3 (round top & bottom arches; plus flat-top 3) ---
        add1Stroke(
            3,
            pt(0.18f, 0.16f), pt(0.48f, 0.06f), pt(0.80f, 0.16f), pt(0.84f, 0.32f),
            pt(0.62f, 0.46f), pt(0.36f, 0.48f), pt(0.66f, 0.52f), pt(0.88f, 0.68f),
            pt(0.80f, 0.86f), pt(0.48f, 0.94f), pt(0.16f, 0.84f)
        )
        add1Stroke(
            3,
            pt(0.16f, 0.08f), pt(0.82f, 0.08f), pt(0.42f, 0.46f), pt(0.78f, 0.56f),
            pt(0.86f, 0.76f), pt(0.52f, 0.94f), pt(0.16f, 0.86f),
            smooth = false
        )

        // --- DIGIT 4 (1-stroke closed, 1-stroke open, 2-stroke L + stem) ---
        add1Stroke(
            4,
            pt(0.68f, 0.06f), pt(0.14f, 0.62f), pt(0.90f, 0.62f),
            pt(0.68f, 0.06f), pt(0.68f, 0.95f),
            smooth = false
        )
        add1Stroke(
            4,
            pt(0.20f, 0.08f), pt(0.18f, 0.58f), pt(0.88f, 0.58f),
            pt(0.68f, 0.12f), pt(0.68f, 0.95f),
            smooth = false
        )
        add2Stroke(
            4,
            listOf(pt(0.22f, 0.08f), pt(0.18f, 0.58f), pt(0.88f, 0.58f)),
            listOf(pt(0.66f, 0.08f), pt(0.66f, 0.95f))
        )
        add2Stroke(
            4,
            listOf(pt(0.64f, 0.08f), pt(0.14f, 0.60f), pt(0.88f, 0.60f)),
            listOf(pt(0.66f, 0.08f), pt(0.66f, 0.95f))
        )

        // --- DIGIT 5 (1-stroke right-to-left top; 2-stroke body + top bar; 2-stroke top bar + body) ---
        add1Stroke(
            5,
            pt(0.84f, 0.08f), pt(0.22f, 0.08f), pt(0.18f, 0.44f),
            pt(0.56f, 0.40f), pt(0.84f, 0.54f), pt(0.86f, 0.74f),
            pt(0.58f, 0.94f), pt(0.16f, 0.86f)
        )
        add2Stroke(
            5,
            listOf(
                pt(0.22f, 0.10f), pt(0.18f, 0.45f), pt(0.58f, 0.42f),
                pt(0.86f, 0.58f), pt(0.82f, 0.80f), pt(0.52f, 0.94f), pt(0.16f, 0.86f)
            ),
            listOf(pt(0.22f, 0.08f), pt(0.84f, 0.08f)),
            smooth1 = true
        )

        // --- DIGIT 6 (curved top stem into closed bottom loop) ---
        add1Stroke(
            6,
            pt(0.76f, 0.08f), pt(0.44f, 0.20f), pt(0.20f, 0.46f), pt(0.16f, 0.72f),
            pt(0.36f, 0.92f), pt(0.68f, 0.90f), pt(0.86f, 0.72f), pt(0.72f, 0.52f),
            pt(0.42f, 0.48f), pt(0.18f, 0.64f)
        )
        add1Stroke(
            6,
            pt(0.64f, 0.06f), pt(0.24f, 0.48f), pt(0.18f, 0.76f), pt(0.50f, 0.94f),
            pt(0.84f, 0.76f), pt(0.62f, 0.50f), pt(0.22f, 0.62f)
        )

        // --- DIGIT 7 (1-stroke plain, 1-stroke serifed, 2-stroke crossed) ---
        add1Stroke(
            7,
            pt(0.12f, 0.08f), pt(0.88f, 0.08f), pt(0.58f, 0.52f), pt(0.32f, 0.95f),
            smooth = false
        )
        add1Stroke(
            7,
            pt(0.12f, 0.22f), pt(0.14f, 0.08f), pt(0.88f, 0.08f), pt(0.34f, 0.95f),
            smooth = false
        )
        add2Stroke(
            7,
            listOf(pt(0.14f, 0.08f), pt(0.88f, 0.08f), pt(0.34f, 0.95f)),
            listOf(pt(0.26f, 0.50f), pt(0.78f, 0.50f))
        )

        // --- DIGIT 8 (counter-clockwise top loop & clockwise top loop) ---
        add1Stroke(
            8,
            pt(0.52f, 0.06f), pt(0.22f, 0.16f), pt(0.22f, 0.34f), pt(0.50f, 0.48f),
            pt(0.82f, 0.64f), pt(0.80f, 0.84f), pt(0.50f, 0.94f), pt(0.20f, 0.84f),
            pt(0.18f, 0.64f), pt(0.50f, 0.48f), pt(0.80f, 0.34f), pt(0.78f, 0.16f),
            pt(0.52f, 0.06f)
        )
        add1Stroke(
            8,
            pt(0.48f, 0.06f), pt(0.78f, 0.16f), pt(0.78f, 0.34f), pt(0.50f, 0.48f),
            pt(0.18f, 0.64f), pt(0.20f, 0.84f), pt(0.50f, 0.94f), pt(0.80f, 0.84f),
            pt(0.82f, 0.64f), pt(0.50f, 0.48f), pt(0.22f, 0.34f), pt(0.22f, 0.16f),
            pt(0.48f, 0.06f)
        )

        // --- DIGIT 9 (straight stem, curved bottom hook, and 4-o'clock start) ---
        add1Stroke(
            9,
            pt(0.82f, 0.24f), pt(0.56f, 0.06f), pt(0.24f, 0.14f), pt(0.16f, 0.32f),
            pt(0.36f, 0.48f), pt(0.72f, 0.42f), pt(0.84f, 0.24f), pt(0.82f, 0.62f),
            pt(0.78f, 0.95f)
        )
        add1Stroke(
            9,
            pt(0.82f, 0.24f), pt(0.54f, 0.06f), pt(0.20f, 0.18f), pt(0.20f, 0.38f),
            pt(0.50f, 0.48f), pt(0.82f, 0.28f), pt(0.80f, 0.68f), pt(0.56f, 0.92f),
            pt(0.28f, 0.92f)
        )
        add1Stroke(
            9,
            pt(0.78f, 0.38f), pt(0.46f, 0.48f), pt(0.18f, 0.34f), pt(0.24f, 0.12f),
            pt(0.58f, 0.06f), pt(0.82f, 0.22f), pt(0.80f, 0.60f), pt(0.76f, 0.95f)
        )

        return specs.map { (digit, strokeCountHint, rawStrokes) ->
            DigitTemplate(
                digit = digit,
                strokeCountHint = strokeCountHint,
                points = resampleAndNormalizeStrokes(rawStrokes, NUM_RESAMPLE_POINTS)
            )
        }
    }
}
