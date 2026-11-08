package com.arendon.sudokumaster.domain.model

/**
 * Immutable 9x9 Sudoku board state supporting:
 * - Given clues vs user-entered digits
 * - Unique 9x9 solution grid
 * - Candidate notes (with "Precompute Candidates" button support and smart peer pruning)
 * - Manual candidate eliminations (preserved across precomputes and digit placements)
 * - Prior basic eliminations (for advanced mid-puzzle states where simpler cleanup occurred before this step)
 * - Step 1 pattern cell selection
 */
data class SudokuBoard(
    val givenDigits: List<List<Int>>,
    val userDigits: List<List<Int>> = List(9) { List(9) { 0 } },
    val solutionDigits: List<List<Int>>,
    val candidates: List<List<Set<Int>>> = List(9) { List(9) { emptySet() } },
    val eliminatedByUser: List<List<Set<Int>>> = List(9) { List(9) { emptySet() } },
    val priorLogicEliminations: Set<CandidateElimination> = emptySet(),
    val candidatesPrecomputed: Boolean = false,
    val selectedPatternCells: Set<CellPos> = emptySet()
) {
    init {
        require(givenDigits.size == 9 && givenDigits.all { it.size == 9 })
        require(userDigits.size == 9 && userDigits.all { it.size == 9 })
        require(solutionDigits.size == 9 && solutionDigits.all { it.size == 9 })
    }

    fun digitAt(pos: CellPos): Int = digitAt(pos.row, pos.col)

    fun digitAt(row: Int, col: Int): Int {
        val given = givenDigits[row][col]
        return if (given != 0) given else userDigits[row][col]
    }

    fun isGiven(pos: CellPos): Boolean = givenDigits[pos.row][pos.col] != 0

    fun isEmpty(pos: CellPos): Boolean = digitAt(pos) == 0

    fun candidatesAt(pos: CellPos): Set<Int> =
        if (isEmpty(pos)) candidates[pos.row][pos.col] else emptySet()

    fun eliminatedByUserAt(pos: CellPos): Set<Int> =
        eliminatedByUser[pos.row][pos.col]

    fun isSolved(): Boolean {
        for (r in 0..8) {
            for (c in 0..8) {
                if (digitAt(r, c) != solutionDigits[r][c]) return false
            }
        }
        return true
    }

    fun togglePatternCellSelection(pos: CellPos): SudokuBoard {
        if (!isEmpty(pos)) return this
        val next = if (pos in selectedPatternCells) {
            selectedPatternCells - pos
        } else {
            selectedPatternCells + pos
        }
        return copy(selectedPatternCells = next)
    }

    fun clearPatternSelection(): SudokuBoard =
        copy(selectedPatternCells = emptySet())

    fun toggleCandidate(pos: CellPos, digit: Int): SudokuBoard {
        if (!isEmpty(pos) || digit !in 1..9) return this
        val currentCands = candidates[pos.row][pos.col]
        val currentElims = eliminatedByUser[pos.row][pos.col]
        val newCands: Set<Int>
        val newElims: Set<Int>
        if (digit in currentCands) {
            // Eliminate this candidate
            newCands = currentCands - digit
            newElims = currentElims + digit
        } else {
            // Add/restore this candidate
            newCands = currentCands + digit
            newElims = currentElims - digit
        }
        return copy(
            candidates = candidates.mapIndexed { r, rowList ->
                if (r != pos.row) rowList
                else rowList.mapIndexed { c, set -> if (c == pos.col) newCands else set }
            },
            eliminatedByUser = eliminatedByUser.mapIndexed { r, rowList ->
                if (r != pos.row) rowList
                else rowList.mapIndexed { c, set -> if (c == pos.col) newElims else set }
            }
        )
    }

    fun eliminateCandidate(pos: CellPos, digit: Int): SudokuBoard {
        if (!isEmpty(pos) || digit !in 1..9) return this
        val currentCands = candidates[pos.row][pos.col]
        val currentElims = eliminatedByUser[pos.row][pos.col]
        if (digit !in currentCands && digit in currentElims) return this
        return copy(
            candidates = candidates.mapIndexed { r, rowList ->
                if (r != pos.row) rowList
                else rowList.mapIndexed { c, set -> if (c == pos.col) set - digit else set }
            },
            eliminatedByUser = eliminatedByUser.mapIndexed { r, rowList ->
                if (r != pos.row) rowList
                else rowList.mapIndexed { c, set -> if (c == pos.col) set + digit else set }
            }
        )
    }

    fun clearCell(pos: CellPos): SudokuBoard {
        if (isGiven(pos)) return this
        return copy(
            userDigits = userDigits.mapIndexed { r, rowList ->
                if (r != pos.row) rowList
                else rowList.mapIndexed { c, v -> if (c == pos.col) 0 else v }
            }
        )
    }
}
