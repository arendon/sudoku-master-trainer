package com.arendon.sudokumaster.domain.solver

/**
 * High-speed backtracking Sudoku solver with Minimum Remaining Values (MRV) heuristic.
 * Used to verify that every lesson example and exercise board has a unique 9x9 solution.
 */
object UniqueSolutionSolver {

    /**
     * Counts the number of valid 9x9 Sudoku solutions for [grid], stopping once [limit] is reached.
     * A valid Sudoku puzzle must return exactly 1 when [limit] = 2.
     */
    fun countSolutions(grid: List<List<Int>>, limit: Int = 2): Int {
        val board = Array(9) { r -> IntArray(9) { c -> grid[r][c] } }
        val rowMask = IntArray(9)
        val colMask = IntArray(9)
        val boxMask = IntArray(9)

        for (r in 0..8) {
            for (c in 0..8) {
                val d = board[r][c]
                if (d in 1..9) {
                    val bit = 1 shl d
                    val b = (r / 3) * 3 + (c / 3)
                    if ((rowMask[r] and bit) != 0 || (colMask[c] and bit) != 0 || (boxMask[b] and bit) != 0) {
                        return 0 // Invalid initial state
                    }
                    rowMask[r] = rowMask[r] or bit
                    colMask[c] = colMask[c] or bit
                    boxMask[b] = boxMask[b] or bit
                }
            }
        }

        var count = 0

        fun backtrack() {
            if (count >= limit) return

            // Find empty cell with Minimum Remaining Values (MRV)
            var bestR = -1
            var bestC = -1
            var bestAvailableMask = 0
            var minCandidates = 10

            for (r in 0..8) {
                for (c in 0..8) {
                    if (board[r][c] == 0) {
                        val b = (r / 3) * 3 + (c / 3)
                        val used = rowMask[r] or colMask[c] or boxMask[b]
                        val available = used.inv() and 0x3FE // bits 1..9
                        val numCands = Integer.bitCount(available)
                        if (numCands == 0) return // Dead end
                        if (numCands < minCandidates) {
                            minCandidates = numCands
                            bestR = r
                            bestC = c
                            bestAvailableMask = available
                            if (numCands == 1) break
                        }
                    }
                }
                if (minCandidates == 1) break
            }

            if (bestR == -1) {
                // All cells filled
                count++
                return
            }

            val b = (bestR / 3) * 3 + (bestC / 3)
            var mask = bestAvailableMask
            while (mask != 0) {
                val bit = mask and -mask
                val d = Integer.numberOfTrailingZeros(bit)
                mask = mask xor bit

                board[bestR][bestC] = d
                rowMask[bestR] = rowMask[bestR] or bit
                colMask[bestC] = colMask[bestC] or bit
                boxMask[b] = boxMask[b] or bit

                backtrack()
                if (count >= limit) return

                board[bestR][bestC] = 0
                rowMask[bestR] = rowMask[bestR] xor bit
                colMask[bestC] = colMask[bestC] xor bit
                boxMask[b] = boxMask[b] xor bit
            }
        }

        backtrack()
        return count
    }

    /**
     * Solves [grid] and returns its solution if at least one exists, or null if unsolvable.
     */
    fun solve(grid: List<List<Int>>): List<List<Int>>? {
        val board = Array(9) { r -> IntArray(9) { c -> grid[r][c] } }
        val rowMask = IntArray(9)
        val colMask = IntArray(9)
        val boxMask = IntArray(9)

        for (r in 0..8) {
            for (c in 0..8) {
                val d = board[r][c]
                if (d in 1..9) {
                    val bit = 1 shl d
                    val b = (r / 3) * 3 + (c / 3)
                    if ((rowMask[r] and bit) != 0 || (colMask[c] and bit) != 0 || (boxMask[b] and bit) != 0) {
                        return null
                    }
                    rowMask[r] = rowMask[r] or bit
                    colMask[c] = colMask[c] or bit
                    boxMask[b] = boxMask[b] or bit
                }
            }
        }

        fun backtrack(): Boolean {
            var bestR = -1
            var bestC = -1
            var bestAvailableMask = 0
            var minCandidates = 10

            for (r in 0..8) {
                for (c in 0..8) {
                    if (board[r][c] == 0) {
                        val b = (r / 3) * 3 + (c / 3)
                        val used = rowMask[r] or colMask[c] or boxMask[b]
                        val available = used.inv() and 0x3FE
                        val numCands = Integer.bitCount(available)
                        if (numCands == 0) return false
                        if (numCands < minCandidates) {
                            minCandidates = numCands
                            bestR = r
                            bestC = c
                            bestAvailableMask = available
                            if (numCands == 1) break
                        }
                    }
                }
                if (minCandidates == 1) break
            }

            if (bestR == -1) return true

            val b = (bestR / 3) * 3 + (bestC / 3)
            var mask = bestAvailableMask
            while (mask != 0) {
                val bit = mask and -mask
                val d = Integer.numberOfTrailingZeros(bit)
                mask = mask xor bit

                board[bestR][bestC] = d
                rowMask[bestR] = rowMask[bestR] or bit
                colMask[bestC] = colMask[bestC] or bit
                boxMask[b] = boxMask[b] or bit

                if (backtrack()) return true

                board[bestR][bestC] = 0
                rowMask[bestR] = rowMask[bestR] xor bit
                colMask[bestC] = colMask[bestC] xor bit
                boxMask[b] = boxMask[b] xor bit
            }
            return false
        }

        return if (backtrack()) {
            List(9) { r -> List(9) { c -> board[r][c] } }
        } else {
            null
        }
    }
}
