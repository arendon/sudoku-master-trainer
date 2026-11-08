package com.arendon.sudokumaster.domain.handwriting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DigitStrokeRecognizerTest {

    private fun pt(x: Float, y: Float) = InkPoint(x, y)

    private fun interpolateStroke(points: List<InkPoint>, stepsPerSegment: Int = 8): List<InkPoint> {
        if (points.size < 2) return points
        val out = mutableListOf<InkPoint>()
        for (i in 0 until points.size - 1) {
            val a = points[i]
            val b = points[i + 1]
            for (s in 0 until stepsPerSegment) {
                val t = s.toFloat() / stepsPerSegment
                out.add(InkPoint(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t))
            }
        }
        out.add(points.last())
        return out
    }

    private fun assertRecognizesDigit(expected: Int, strokes: List<List<InkPoint>>, label: String) {
        val denseStrokes = strokes.map { interpolateStroke(it) }
        val result = DigitStrokeRecognizer.recognize(denseStrokes)
        assertTrue("Expected Digit for $label, got $result", result is RecognizedInkResult.Digit)
        val digitResult = result as RecognizedInkResult.Digit
        assertEquals("Wrong digit for $label (alts=${digitResult.alternatives})", expected, digitResult.digit)
    }

    @Test
    fun `recognizes all variations of digits 1 to 9 and erase`() {
        // Digit 1: straight vertical
        assertRecognizesDigit(
            1,
            listOf(listOf(pt(50f, 10f), pt(50f, 95f))),
            "1 straight"
        )
        // Digit 1: slanted vertical
        assertRecognizesDigit(
            1,
            listOf(listOf(pt(58f, 10f), pt(42f, 95f))),
            "1 slanted"
        )
        // Digit 1: top serif + vertical stem
        assertRecognizesDigit(
            1,
            listOf(listOf(pt(35f, 24f), pt(52f, 10f), pt(52f, 95f))),
            "1 serifed"
        )

        // Digit 2: classic arch + diagonal + flat base
        assertRecognizesDigit(
            2,
            listOf(
                listOf(
                    pt(20f, 25f), pt(45f, 10f), pt(75f, 15f), pt(82f, 32f),
                    pt(50f, 65f), pt(18f, 90f), pt(85f, 90f)
                )
            ),
            "2 classic"
        )
        // Digit 2: looped bottom-left corner
        assertRecognizesDigit(
            2,
            listOf(
                listOf(
                    pt(20f, 25f), pt(50f, 10f), pt(82f, 25f), pt(65f, 55f),
                    pt(22f, 88f), pt(15f, 78f), pt(35f, 76f), pt(88f, 90f)
                )
            ),
            "2 looped"
        )

        // Digit 3: round double arch
        assertRecognizesDigit(
            3,
            listOf(
                listOf(
                    pt(20f, 18f), pt(50f, 8f), pt(80f, 20f), pt(78f, 38f),
                    pt(38f, 48f), pt(78f, 58f), pt(84f, 78f), pt(50f, 92f), pt(18f, 84f)
                )
            ),
            "3 classic"
        )

        // Digit 4: 1-stroke closed top
        assertRecognizesDigit(
            4,
            listOf(
                listOf(
                    pt(65f, 10f), pt(15f, 60f), pt(88f, 60f), pt(65f, 12f), pt(65f, 95f)
                )
            ),
            "4 1-stroke closed"
        )
        // Digit 4: 2-stroke L-shape + vertical stem
        assertRecognizesDigit(
            4,
            listOf(
                listOf(pt(22f, 12f), pt(18f, 58f), pt(86f, 58f)),
                listOf(pt(64f, 12f), pt(64f, 95f))
            ),
            "4 2-stroke"
        )

        // Digit 5: 1-stroke right-to-left top bar
        assertRecognizesDigit(
            5,
            listOf(
                listOf(
                    pt(82f, 10f), pt(22f, 10f), pt(18f, 45f), pt(58f, 42f),
                    pt(84f, 58f), pt(80f, 82f), pt(50f, 92f), pt(18f, 84f)
                )
            ),
            "5 1-stroke"
        )
        // Digit 5: 2-stroke (body first, top horizontal bar second)
        assertRecognizesDigit(
            5,
            listOf(
                listOf(
                    pt(22f, 12f), pt(18f, 46f), pt(58f, 44f), pt(84f, 60f),
                    pt(80f, 82f), pt(50f, 92f), pt(18f, 84f)
                ),
                listOf(pt(22f, 10f), pt(84f, 10f))
            ),
            "5 2-stroke"
        )

        // Digit 6: top curve into closed bottom loop
        assertRecognizesDigit(
            6,
            listOf(
                listOf(
                    pt(74f, 10f), pt(40f, 24f), pt(18f, 52f), pt(18f, 76f),
                    pt(45f, 92f), pt(78f, 84f), pt(82f, 64f), pt(52f, 50f), pt(20f, 64f)
                )
            ),
            "6 classic"
        )
        // Digit 6: high-closing loop (often confused with 8 if top-return isn't checked)
        assertRecognizesDigit(
            6,
            listOf(
                listOf(
                    pt(82f, 8f), pt(35f, 22f), pt(15f, 50f), pt(20f, 82f),
                    pt(52f, 95f), pt(85f, 78f), pt(82f, 52f), pt(48f, 44f), pt(18f, 56f)
                )
            ),
            "6 high-closing loop"
        )
        // Digit 6: straighter diagonal stem into bottom loop
        assertRecognizesDigit(
            6,
            listOf(
                listOf(
                    pt(62f, 8f), pt(22f, 55f), pt(18f, 78f), pt(48f, 94f),
                    pt(82f, 78f), pt(64f, 52f), pt(24f, 60f)
                )
            ),
            "6 diagonal stem"
        )

        // Digit 7: 1-stroke plain
        assertRecognizesDigit(
            7,
            listOf(listOf(pt(15f, 10f), pt(86f, 10f), pt(34f, 94f))),
            "7 1-stroke"
        )
        // Digit 7: 2-stroke crossed European style
        assertRecognizesDigit(
            7,
            listOf(
                listOf(pt(15f, 10f), pt(86f, 10f), pt(34f, 94f)),
                listOf(pt(26f, 50f), pt(76f, 50f))
            ),
            "7 2-stroke crossed"
        )

        // Digit 8: counter-clockwise S-curve
        assertRecognizesDigit(
            8,
            listOf(
                listOf(
                    pt(50f, 8f), pt(22f, 20f), pt(24f, 36f), pt(50f, 48f),
                    pt(80f, 64f), pt(78f, 84f), pt(50f, 92f), pt(20f, 84f),
                    pt(20f, 64f), pt(50f, 48f), pt(78f, 36f), pt(78f, 20f), pt(50f, 8f)
                )
            ),
            "8 counter-clockwise"
        )
        // Digit 8: clockwise top loop
        assertRecognizesDigit(
            8,
            listOf(
                listOf(
                    pt(50f, 8f), pt(78f, 20f), pt(78f, 36f), pt(50f, 48f),
                    pt(20f, 64f), pt(20f, 84f), pt(50f, 92f), pt(78f, 84f),
                    pt(80f, 64f), pt(50f, 48f), pt(24f, 36f), pt(22f, 20f), pt(50f, 8f)
                )
            ),
            "8 clockwise"
        )
        // Digit 8: center/waist start
        assertRecognizesDigit(
            8,
            listOf(
                listOf(
                    pt(50f, 48f), pt(78f, 34f), pt(78f, 16f), pt(50f, 8f),
                    pt(22f, 16f), pt(22f, 34f), pt(50f, 48f),
                    pt(82f, 64f), pt(80f, 84f), pt(50f, 94f),
                    pt(20f, 84f), pt(18f, 64f), pt(50f, 48f)
                )
            ),
            "8 center start"
        )
        // Digit 8: 2-stroke (top circle + bottom circle)
        assertRecognizesDigit(
            8,
            listOf(
                listOf(
                    pt(50f, 8f), pt(22f, 16f), pt(22f, 34f),
                    pt(50f, 46f), pt(78f, 34f), pt(78f, 16f), pt(50f, 8f)
                ),
                listOf(
                    pt(50f, 46f), pt(18f, 64f), pt(20f, 84f),
                    pt(50f, 94f), pt(80f, 84f), pt(82f, 64f), pt(50f, 46f)
                )
            ),
            "8 2-stroke"
        )

        // Digit 9: top loop + straight right stem
        assertRecognizesDigit(
            9,
            listOf(
                listOf(
                    pt(80f, 24f), pt(52f, 8f), pt(20f, 20f), pt(20f, 38f),
                    pt(50f, 48f), pt(80f, 26f), pt(78f, 94f)
                )
            ),
            "9 straight stem"
        )

        // Scribble erase
        val eraseStroke = interpolateStroke(
            listOf(pt(10f, 50f), pt(90f, 48f), pt(12f, 52f), pt(88f, 49f), pt(14f, 51f), pt(86f, 50f))
        )
        val eraseRes = DigitStrokeRecognizer.recognize(listOf(eraseStroke))
        assertTrue("Expected Erase for scribble", eraseRes is RecognizedInkResult.Erase)
    }
}
