package com.arendon.sudokumaster.domain.solver

import com.arendon.sudokumaster.domain.handwriting.DigitStrokeRecognizer
import com.arendon.sudokumaster.domain.handwriting.InkPoint
import com.arendon.sudokumaster.domain.handwriting.RecognizedInkResult
import com.arendon.sudokumaster.domain.model.PatternType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseAndExampleVerificationTest {

    @Test
    fun verifyAll24WalkthroughExamplesPassAll5Invariants() {
        var totalVerified = 0
        for (pattern in PatternType.orderedByComplexity) {
            val examples = CuratedExerciseBank.getWalkthroughExamples(pattern)
            assertTrue("Expected at least 2 examples for $pattern", examples.size >= 2)
            for (example in examples) {
                val report = ExerciseVerifier.verifyWalkthrough(example)
                assertTrue(
                    "Walkthrough verification failed for ${example.id}: ${report.details}",
                    report.passed
                )
                totalVerified++
            }
        }
        assertEquals(24, totalVerified)
    }

    @Test
    fun verifyAll120BundledExercisesPassAll5Invariants() {
        var totalVerified = 0
        for (pattern in PatternType.orderedByComplexity) {
            val exercises = CuratedExerciseBank.getInitialExercises(pattern)
            assertEquals("Expected 10 initial exercises for $pattern", 10, exercises.size)

            val distinctDefinedSets = mutableSetOf<String>()
            for (exercise in exercises) {
                val report = ExerciseVerifier.verifyExercise(exercise)
                assertTrue(
                    "Exercise verification failed for ${exercise.id}: ${report.details}",
                    report.passed
                )
                distinctDefinedSets.add(
                    exercise.patternInstance.definingCells.sorted().joinToString() + ":" +
                        exercise.patternInstance.focusDigits.sorted().joinToString()
                )
                totalVerified++
            }
            assertTrue(
                "Exercises for $pattern should have varied coordinates/digits across the 10 drills",
                distinctDefinedSets.size >= 5
            )
        }
        assertEquals(120, totalVerified)
    }

    @Test
    fun verifyDynamicExerciseGeneratorProducesValidExercises() {
        for (pattern in PatternType.orderedByComplexity) {
            for (idx in 11..15) {
                val generated = CuratedExerciseBank.generateExercise(pattern, idx)
                val report = ExerciseVerifier.verifyExercise(generated)
                assertTrue(
                    "Generated exercise ${generated.id} failed verification: ${report.details}",
                    report.passed
                )
            }
        }
    }

    @Test
    fun verifyStylusHandwritingRecognizerDigitsAndScribbleErase() {
        // Test vertical stroke -> Digit 1
        val stroke1 = listOf(
            listOf(
                InkPoint(50f, 10f),
                InkPoint(50f, 50f),
                InkPoint(50f, 95f)
            )
        )
        val res1 = DigitStrokeRecognizer.recognize(stroke1)
        assertTrue("Expected Digit(1), got $res1", res1 is RecognizedInkResult.Digit && res1.digit == 1)

        // Test horizontal scribble -> Erase
        val scribble = listOf(
            listOf(
                InkPoint(10f, 50f),
                InkPoint(90f, 52f),
                InkPoint(15f, 54f),
                InkPoint(88f, 55f),
                InkPoint(12f, 57f),
                InkPoint(92f, 58f)
            )
        )
        val resErase = DigitStrokeRecognizer.recognize(scribble)
        assertEquals(RecognizedInkResult.Erase, resErase)
    }
}
