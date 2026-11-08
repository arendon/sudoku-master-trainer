package com.arendon.sudokumaster.domain.model

/**
 * Interactive 3-step visual walkthrough example for a lesson screen:
 * Step 1: The Setup (candidates & where to look)
 * Step 2: Pattern Cells & Visual Links (how the pattern locks candidates)
 * Step 3: Resulting Eliminations (which candidates are crossed out and why)
 */
data class WalkthroughExample(
    val id: String,
    val patternType: PatternType,
    val title: String,
    val board: SudokuBoard,
    val patternInstance: PatternInstance,
    val step1SetupText: String,
    val step2LogicText: String,
    val step3EliminationText: String
)

/**
 * A practice exercise for a specific Sudoku solving pattern.
 */
data class Exercise(
    val id: String,
    val patternType: PatternType,
    val indexInPattern: Int,
    val title: String,
    val initialBoard: SudokuBoard,
    val patternInstance: PatternInstance,
    val isGenerated: Boolean = false
)

enum class ExerciseDrillPhase {
    STEP_1_SELECT_PATTERN_CELLS,
    STEP_2_ELIMINATE_CANDIDATES,
    COMPLETED_DRILL,
    KEEP_SOLVING_FULL_BOARD,
    FULL_BOARD_SOLVED
}

enum class InkInputMode(val label: String, val shortHint: String) {
    SELECT_PATTERN("Pattern Cells", "Tap or draw inside cells to highlight the pattern's defining cells"),
    CANDIDATE_NOTE("Candidate Note", "Write 1-9 (or tap mini-grid) to toggle/eliminate candidate notes"),
    FINAL_DIGIT("Final Digit", "Write 1-9 in a cell (or Zoom Pad) to place a solved number; scribble to erase")
}
