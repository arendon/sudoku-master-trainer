package com.arendon.sudokumaster.domain.solver

import com.arendon.sudokumaster.domain.model.CandidateElimination
import com.arendon.sudokumaster.domain.model.CellPos
import com.arendon.sudokumaster.domain.model.SudokuBoard

/**
 * Represents an element of the Sudoku symmetry group G (order 9! * 6^8 * 2):
 * - Digit permutation (1..9 -> 1..9)
 * - Band permutation (0..2) and row-within-band permutations
 * - Stack permutation (0..2) and col-within-stack permutations
 * - Optional matrix transposition (swapping rows and columns)
 *
 * Every transformation is a strict Sudoku isomorphism: it preserves unique solutions,
 * row/column/box candidate constraints, and pattern logic while producing a fresh board.
 */
data class IsomorphismSpec(
    val digitMap: IntArray, // size 10, index 0->0, 1..9 -> permuted 1..9
    val rowMap: IntArray,   // size 9, oldRow -> newRow
    val colMap: IntArray,   // size 9, oldCol -> newCol
    val transpose: Boolean = false
) {
    fun mapCell(pos: CellPos): CellPos {
        val r = rowMap[pos.row]
        val c = colMap[pos.col]
        return if (transpose) CellPos(c, r) else CellPos(r, c)
    }

    fun mapDigit(d: Int): Int = if (d in 1..9) digitMap[d] else 0
}

object SudokuIsomorphism {

    /**
     * Deterministically generates a rich variety of valid Sudoku isomorphism transformations
     * from a seed index (0, 1, 2, ...).
     */
    fun createSpec(seedIndex: Int): IsomorphismSpec {
        if (seedIndex == 0) {
            return IsomorphismSpec(
                digitMap = IntArray(10) { it },
                rowMap = IntArray(9) { it },
                colMap = IntArray(9) { it },
                transpose = false
            )
        }

        // Deterministic permutations of 3 elements (all 6 elements of S_3)
        val perms3 = arrayOf(
            intArrayOf(0, 1, 2),
            intArrayOf(0, 2, 1),
            intArrayOf(1, 0, 2),
            intArrayOf(1, 2, 0),
            intArrayOf(2, 0, 1),
            intArrayOf(2, 1, 0)
        )

        val bandPerm = perms3[(seedIndex * 3 + 1) % 6]
        val rBand0 = perms3[(seedIndex * 5 + 2) % 6]
        val rBand1 = perms3[(seedIndex * 7 + 3) % 6]
        val rBand2 = perms3[(seedIndex * 11 + 4) % 6]
        val rSubPerms = arrayOf(rBand0, rBand1, rBand2)

        val stackPerm = perms3[(seedIndex * 2 + 1) % 6]
        val cStack0 = perms3[(seedIndex * 4 + 1) % 6]
        val cStack1 = perms3[(seedIndex * 9 + 2) % 6]
        val cStack2 = perms3[(seedIndex * 13 + 5) % 6]
        val cSubPerms = arrayOf(cStack0, cStack1, cStack2)

        val rowMap = IntArray(9)
        val colMap = IntArray(9)
        for (b in 0..2) {
            val targetBand = bandPerm[b]
            for (i in 0..2) {
                rowMap[b * 3 + i] = targetBand * 3 + rSubPerms[b][i]
            }
            val targetStack = stackPerm[b]
            for (i in 0..2) {
                colMap[b * 3 + i] = targetStack * 3 + cSubPerms[b][i]
            }
        }

        // Deterministic digit relabeling permutation in S_9
        val digits = (1..9).toMutableList()
        var state = (seedIndex * 1103515245L + 12345L) and 0x7fffffffL
        for (i in 8 downTo 1) {
            state = (state * 1103515245L + 12345L) and 0x7fffffffL
            val j = (state % (i + 1)).toInt()
            val tmp = digits[i]
            digits[i] = digits[j]
            digits[j] = tmp
        }
        val digitMap = IntArray(10)
        for (d in 1..9) {
            digitMap[d] = digits[d - 1]
        }

        val transpose = (seedIndex % 2 == 1)
        return IsomorphismSpec(
            digitMap = digitMap,
            rowMap = rowMap,
            colMap = colMap,
            transpose = transpose
        )
    }

    /**
     * Applies [spec] to a [SudokuBoard], mapping all given digits, solution digits,
     * candidates, and prior logic eliminations.
     */
    fun transformBoard(board: SudokuBoard, spec: IsomorphismSpec): SudokuBoard {
        val newGivens = Array(9) { IntArray(9) }
        val newUsers = Array(9) { IntArray(9) }
        val newSolutions = Array(9) { IntArray(9) }
        val newCands = Array(9) { Array(9) { emptySet<Int>() } }
        val newElimsByUser = Array(9) { Array(9) { emptySet<Int>() } }

        for (r in 0..8) {
            for (c in 0..8) {
                val oldPos = CellPos(r, c)
                val newPos = spec.mapCell(oldPos)
                newGivens[newPos.row][newPos.col] = spec.mapDigit(board.givenDigits[r][c])
                newUsers[newPos.row][newPos.col] = spec.mapDigit(board.userDigits[r][c])
                newSolutions[newPos.row][newPos.col] = spec.mapDigit(board.solutionDigits[r][c])
                newCands[newPos.row][newPos.col] = board.candidates[r][c].map { spec.mapDigit(it) }.toSet()
                newElimsByUser[newPos.row][newPos.col] = board.eliminatedByUser[r][c].map { spec.mapDigit(it) }.toSet()
            }
        }

        val newPriorElims = board.priorLogicEliminations.map { elim ->
            CandidateElimination(
                cell = spec.mapCell(elim.cell),
                digit = spec.mapDigit(elim.digit)
            )
        }.toSet()

        val newSelected = board.selectedPatternCells.map { spec.mapCell(it) }.toSet()

        return SudokuBoard(
            givenDigits = newGivens.map { it.toList() },
            userDigits = newUsers.map { it.toList() },
            solutionDigits = newSolutions.map { it.toList() },
            candidates = newCands.map { it.toList() },
            eliminatedByUser = newElimsByUser.map { it.toList() },
            priorLogicEliminations = newPriorElims,
            candidatesPrecomputed = board.candidatesPrecomputed,
            selectedPatternCells = newSelected
        )
    }
}
