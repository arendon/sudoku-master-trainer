package com.arendon.sudokumaster.domain.solver

import com.arendon.sudokumaster.domain.model.CellPos
import com.arendon.sudokumaster.domain.model.Exercise
import com.arendon.sudokumaster.domain.model.PatternInstance
import com.arendon.sudokumaster.domain.model.PatternType
import com.arendon.sudokumaster.domain.model.SudokuBoard
import com.arendon.sudokumaster.domain.model.WalkthroughExample

data class VerificationReport(
    val id: String,
    val patternType: PatternType,
    val passed: Boolean,
    val details: String
)

/**
 * Enforces the 5 strict mathematical invariants across all walkthrough examples and exercises:
 * 1. Unique 9x9 Solution (`UniqueSolutionSolver.countSolutions == 1` & matches `solutionDigits`)
 * 2. Candidate Constraint Integrity (every candidate is valid under row/col/box rules and true solution is retained)
 * 3. Independent Pattern Detector Proof (`PatternDetector` independently finds the exact `definingCells` and `targetEliminations`)
 * 4. Elimination Soundness (every eliminated candidate exists before the step and is NEVER the true solution digit)
 * 5. End-to-End Two-Step Drill & Hint Alignment (simulates Step 1 & Step 2 and validates all 3 hint stages)
 */
object ExerciseVerifier {

    fun verifyExercise(exercise: Exercise): VerificationReport {
        return verifyBoardAndPattern(
            id = exercise.id,
            board = exercise.initialBoard,
            expectedPattern = exercise.patternInstance
        )
    }

    fun verifyWalkthrough(example: WalkthroughExample): VerificationReport {
        return verifyBoardAndPattern(
            id = example.id,
            board = example.board,
            expectedPattern = example.patternInstance
        )
    }

    fun verifyBoardAndPattern(
        id: String,
        board: SudokuBoard,
        expectedPattern: PatternInstance
    ): VerificationReport {
        // Invariant 1: Unique 9x9 solution & valid Sudoku solution grid
        if (!isValidSolvedGrid(board.solutionDigits)) {
            return VerificationReport(id, expectedPattern.patternType, false, "Invariant 1 failed: solutionDigits is not a valid 9x9 Latin/Sudoku grid")
        }
        for (r in 0..8) {
            for (c in 0..8) {
                val given = board.givenDigits[r][c]
                if (given != 0 && given != board.solutionDigits[r][c]) {
                    return VerificationReport(id, expectedPattern.patternType, false, "Invariant 1 failed: given digit at R${r + 1}C${c + 1} does not match solutionDigits")
                }
            }
        }
        val solCount = UniqueSolutionSolver.countSolutions(board.givenDigits, limit = 2)
        if (solCount != 1) {
            return VerificationReport(id, expectedPattern.patternType, false, "Invariant 1 failed: expected unique solution (1), found $solCount")
        }

        // Invariant 2: Pure Row/Col/Block Candidate Constraint Integrity
        if (board.priorLogicEliminations.isNotEmpty()) {
            return VerificationReport(id, expectedPattern.patternType, false, "Invariant 2 failed: board requires priorLogicEliminations instead of pure row/col/block candidates")
        }
        val precomputed = CandidateCalculator.precomputeCandidates(board)
        val rawRowColBox = CandidateCalculator.computeRowColBlockCandidates(board)
        for (r in 0..8) {
            for (c in 0..8) {
                val pos = CellPos(r, c)
                if (precomputed.isEmpty(pos)) {
                    val cands = precomputed.candidatesAt(pos)
                    val trueSol = precomputed.solutionDigits[r][c]
                    if (trueSol !in cands) {
                        return VerificationReport(id, expectedPattern.patternType, false, "Invariant 2 failed: true solution $trueSol missing from candidates at ${pos.coordLabel}")
                    }
                    if (cands != rawRowColBox[r][c]) {
                        return VerificationReport(id, expectedPattern.patternType, false, "Invariant 2 failed: candidates $cands at ${pos.coordLabel} do not match pure row/col/box constraints ${rawRowColBox[r][c]}")
                    }
                }
            }
        }

        // Invariant 3: Independent Pattern Detector Proof
        val detectedList = PatternDetector.findAllInstances(precomputed, expectedPattern.patternType)
        val matchingInstance = detectedList.firstOrNull {
            it.definingCells == expectedPattern.definingCells &&
                it.targetEliminations == expectedPattern.targetEliminations
        }
        if (matchingInstance == null) {
            return VerificationReport(
                id,
                expectedPattern.patternType,
                false,
                "Invariant 3 failed: PatternDetector did not independently find definingCells=${expectedPattern.definingCells} and targetEliminations=${expectedPattern.targetEliminations} (found ${detectedList.size} instances)"
            )
        }

        // Invariant 4: Elimination Soundness
        if (expectedPattern.targetEliminations.isEmpty()) {
            return VerificationReport(id, expectedPattern.patternType, false, "Invariant 4 failed: targetEliminations is empty")
        }
        for (elim in expectedPattern.targetEliminations) {
            val candsAtTarget = precomputed.candidatesAt(elim.cell)
            if (elim.digit !in candsAtTarget) {
                return VerificationReport(id, expectedPattern.patternType, false, "Invariant 4 failed: target elimination ${elim.label} is not present in precomputed candidates $candsAtTarget")
            }
            val trueSol = precomputed.solutionDigits[elim.cell.row][elim.cell.col]
            if (elim.digit == trueSol) {
                return VerificationReport(id, expectedPattern.patternType, false, "Invariant 4 CRITICAL failure: target elimination ${elim.label} eliminates the true solution digit!")
            }
        }

        // Invariant 5: End-to-End Two-Step Drill & Hint Alignment
        val (step1Result, _) = PatternDetector.verifyStep1Selection(
            board = precomputed,
            selectedCells = expectedPattern.definingCells,
            expected = expectedPattern
        )
        if (step1Result !is VerificationFeedback.Success) {
            return VerificationReport(id, expectedPattern.patternType, false, "Invariant 5 failed: Step 1 verification rejected definingCells: $step1Result")
        }

        var boardAfterElims = precomputed
        for (elim in expectedPattern.targetEliminations) {
            boardAfterElims = boardAfterElims.eliminateCandidate(elim.cell, elim.digit)
        }
        val step2Result = PatternDetector.verifyStep2Eliminations(
            initialBoardWithCandidates = precomputed,
            currentBoard = boardAfterElims,
            activePattern = expectedPattern
        )
        if (step2Result !is VerificationFeedback.Success) {
            return VerificationReport(id, expectedPattern.patternType, false, "Invariant 5 failed: Step 2 verification rejected targetEliminations: $step2Result")
        }

        if (expectedPattern.stage1Hint.isBlank() || expectedPattern.stage2Hint.isBlank() || expectedPattern.stage3Hint.isBlank()) {
            return VerificationReport(id, expectedPattern.patternType, false, "Invariant 5 failed: one or more hint stages are blank")
        }

        return VerificationReport(
            id = id,
            patternType = expectedPattern.patternType,
            passed = true,
            details = "Verified all 5 invariants: unique solution, valid candidates, ${expectedPattern.definingCells.size} defining cells, ${expectedPattern.targetEliminations.size} sound eliminations."
        )
    }

    private fun isValidSolvedGrid(grid: List<List<Int>>): Boolean {
        if (grid.size != 9 || grid.any { it.size != 9 }) return false
        val fullSet = (1..9).toSet()
        for (r in 0..8) {
            if (grid[r].toSet() != fullSet) return false
        }
        for (c in 0..8) {
            if ((0..8).map { grid[it][c] }.toSet() != fullSet) return false
        }
        for (br in 0..2) {
            for (bc in 0..2) {
                val boxSet = mutableSetOf<Int>()
                for (dr in 0..2) {
                    for (dc in 0..2) {
                        boxSet.add(grid[br * 3 + dr][bc * 3 + dc])
                    }
                }
                if (boxSet != fullSet) return false
            }
        }
        return true
    }
}
