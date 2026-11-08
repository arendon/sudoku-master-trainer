package com.arendon.sudokumaster.domain.solver

import com.arendon.sudokumaster.domain.model.CellPos
import com.arendon.sudokumaster.domain.model.SudokuBoard

/**
 * Computes candidate suggestions given row, column, and 3x3 block constraints,
 * and handles smart candidate pruning when digits are placed.
 */
object CandidateCalculator {

    /**
     * Computes all valid candidate digits (1..9) for every empty cell based purely on
     * row, column, and 3x3 block constraints of currently placed digits.
     */
    fun computeRowColBlockCandidates(board: SudokuBoard): List<List<Set<Int>>> {
        val rowUsed = Array(9) { BooleanArray(10) }
        val colUsed = Array(9) { BooleanArray(10) }
        val boxUsed = Array(9) { BooleanArray(10) }

        for (r in 0..8) {
            for (c in 0..8) {
                val d = board.digitAt(r, c)
                if (d in 1..9) {
                    rowUsed[r][d] = true
                    colUsed[c][d] = true
                    boxUsed[(r / 3) * 3 + (c / 3)][d] = true
                }
            }
        }

        return List(9) { r ->
            List(9) { c ->
                if (board.digitAt(r, c) != 0) {
                    emptySet()
                } else {
                    val b = (r / 3) * 3 + (c / 3)
                    val allowed = mutableSetOf<Int>()
                    for (d in 1..9) {
                        if (!rowUsed[r][d] && !colUsed[c][d] && !boxUsed[b][d]) {
                            allowed.add(d)
                        }
                    }
                    allowed
                }
            }
        }
    }

    /**
     * Invoked when the user taps the "Precompute Candidates" button.
     * Populates all valid row/column/block candidate numbers for each empty cell,
     * applying any prior step eliminations for the exercise and preserving the user's
     * own manual eliminations.
     */
    fun precomputeCandidates(board: SudokuBoard): SudokuBoard {
        val base = computeRowColBlockCandidates(board)
        val priorByCell = board.priorLogicEliminations.groupBy({ it.cell }, { it.digit })

        val updatedCandidates = List(9) { r ->
            List(9) { c ->
                val pos = CellPos(r, c)
                val priorElims = priorByCell[pos]?.toSet() ?: emptySet()
                val userElims = board.eliminatedByUser[r][c]
                base[r][c] - priorElims - userElims
            }
        }

        return board.copy(
            candidates = updatedCandidates,
            candidatesPrecomputed = true
        )
    }

    /**
     * Places a final digit in [pos] and smartly prunes [digit] from all peer cells
     * in the same row, column, and 3x3 box while preserving manual candidate eliminations.
     */
    fun placeDigitAndPrunePeers(board: SudokuBoard, pos: CellPos, digit: Int): SudokuBoard {
        if (board.isGiven(pos) || digit !in 1..9) return board

        val newUserDigits = board.userDigits.mapIndexed { r, rowList ->
            if (r != pos.row) rowList
            else rowList.mapIndexed { c, oldVal -> if (c == pos.col) digit else oldVal }
        }

        val newCandidates = board.candidates.mapIndexed { r, rowList ->
            rowList.mapIndexed { c, candSet ->
                val cellPos = CellPos(r, c)
                when {
                    cellPos == pos -> emptySet()
                    cellPos.isPeerOf(pos) -> candSet - digit
                    else -> candSet
                }
            }
        }

        return board.copy(
            userDigits = newUserDigits,
            candidates = newCandidates,
            selectedPatternCells = board.selectedPatternCells - pos
        )
    }
}
