package com.arendon.sudokumaster.domain.model

/**
 * Zero-indexed board coordinate (row in 0..8, col in 0..8) with human-readable R1C1 labels
 * and Sudoku unit (row, column, 3x3 box) helper functions.
 */
data class CellPos(val row: Int, val col: Int) : Comparable<CellPos> {
    init {
        require(row in 0..8 && col in 0..8) { "Invalid CellPos($row, $col)" }
    }

    val boxIndex: Int
        get() = (row / 3) * 3 + (col / 3)

    val boxNumber: Int
        get() = boxIndex + 1

    val rowNumber: Int
        get() = row + 1

    val colNumber: Int
        get() = col + 1

    val coordLabel: String
        get() = "R${rowNumber}C${colNumber}"

    fun sharesRow(other: CellPos): Boolean = this != other && row == other.row
    fun sharesCol(other: CellPos): Boolean = this != other && col == other.col
    fun sharesBox(other: CellPos): Boolean = this != other && boxIndex == other.boxIndex

    fun isPeerOf(other: CellPos): Boolean =
        this != other && (row == other.row || col == other.col || boxIndex == other.boxIndex)

    override fun compareTo(other: CellPos): Int =
        if (row != other.row) row.compareTo(other.row) else col.compareTo(other.col)

    override fun toString(): String = coordLabel
}

/**
 * Represents a candidate digit to be eliminated from a specific cell.
 */
data class CandidateElimination(
    val cell: CellPos,
    val digit: Int
) {
    init {
        require(digit in 1..9) { "Invalid candidate digit $digit" }
    }

    val label: String
        get() = "${cell.coordLabel} ≠ $digit"
}

enum class LinkType {
    STRONG,
    WEAK,
    WING_BRIDGE,
    ELIMINATION_RAY
}

/**
 * Visual link drawn between cells on the Sudoku board during interactive walkthroughs and Stage 3 hints.
 */
data class PatternLink(
    val from: CellPos,
    val to: CellPos,
    val digit: Int? = null,
    val linkType: LinkType = LinkType.STRONG
)
