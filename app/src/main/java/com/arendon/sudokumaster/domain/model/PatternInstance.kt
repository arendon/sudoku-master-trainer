package com.arendon.sudokumaster.domain.model

/**
 * Concrete instance of a Sudoku solving pattern detected on a board.
 *
 * @property patternType The pattern category.
 * @property definingCells The exact set of cells that define the pattern (selected in Step 1).
 * @property pivotCell Optional pivot cell (for XY-Wing, XYZ-Wing, or Unique Rectangle target roof cell) for distinct visual emphasis.
 * @property focusDigits The candidate digit(s) involved in the pattern.
 * @property targetEliminations The exact candidate eliminations resulting from the pattern (verified in Step 2).
 * @property visualLinks Visual lines/arrows connecting the pattern cells and target eliminations.
 * @property stage1Hint Progressive Hint 1: Focus area / which digit or unit to examine.
 * @property stage2Hint Progressive Hint 2: Explains the pattern cells and why they lock candidates.
 * @property stage3Hint Progressive Hint 3: Full deduction explaining the exact candidate eliminations.
 */
data class PatternInstance(
    val patternType: PatternType,
    val definingCells: Set<CellPos>,
    val pivotCell: CellPos? = null,
    val focusDigits: Set<Int>,
    val targetEliminations: Set<CandidateElimination>,
    val visualLinks: List<PatternLink>,
    val stage1Hint: String,
    val stage2Hint: String,
    val stage3Hint: String
)
