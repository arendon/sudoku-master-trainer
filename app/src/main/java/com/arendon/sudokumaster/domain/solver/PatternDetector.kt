package com.arendon.sudokumaster.domain.solver

import com.arendon.sudokumaster.domain.model.CandidateElimination
import com.arendon.sudokumaster.domain.model.CellPos
import com.arendon.sudokumaster.domain.model.LinkType
import com.arendon.sudokumaster.domain.model.PatternInstance
import com.arendon.sudokumaster.domain.model.PatternLink
import com.arendon.sudokumaster.domain.model.PatternType
import com.arendon.sudokumaster.domain.model.SudokuBoard

sealed class VerificationFeedback {
    data object Success : VerificationFeedback()
    data class Incorrect(val reason: String) : VerificationFeedback()
}

/**
 * Deterministic human-style pattern detector for all 12 curriculum patterns,
 * plus diagnostic verification for the two-step drill workflow.
 */
object PatternDetector {

    /**
     * Finds all instances of [patternType] on [board] using its precomputed candidates.
     */
    fun findAllInstances(board: SudokuBoard, patternType: PatternType): List<PatternInstance> {
        val readyBoard = if (board.candidatesPrecomputed) board else CandidateCalculator.precomputeCandidates(board)
        return when (patternType) {
            PatternType.NAKED_PAIR -> detectNakedPairs(readyBoard)
            PatternType.HIDDEN_PAIR -> detectHiddenPairs(readyBoard)
            PatternType.NAKED_TRIPLE -> detectNakedTriples(readyBoard)
            PatternType.POINTING_PAIR -> detectPointingPairs(readyBoard)
            PatternType.BOX_LINE_REDUCTION -> detectBoxLineReductions(readyBoard)
            PatternType.X_WING -> detectXWings(readyBoard)
            PatternType.SKYSCRAPER -> detectSkyscrapers(readyBoard)
            PatternType.TWO_STRING_KITE -> detectTwoStringKites(readyBoard)
            PatternType.SWORDFISH -> detectSwordfish(readyBoard)
            PatternType.XY_WING -> detectXYWings(readyBoard)
            PatternType.XYZ_WING -> detectXYZWings(readyBoard)
            PatternType.UNIQUE_RECTANGLE_TYPE_1 -> detectUniqueRectanglesType1(readyBoard)
        }
    }

    fun findFirstInstance(board: SudokuBoard, patternType: PatternType): PatternInstance? =
        findAllInstances(board, patternType).firstOrNull()

    /**
     * Scans across all 12 patterns in complexity order to offer live hints during "Keep Solving" mode.
     */
    fun findNextBestPattern(board: SudokuBoard): PatternInstance? {
        val readyBoard = if (board.candidatesPrecomputed) board else CandidateCalculator.precomputeCandidates(board)
        for (type in PatternType.orderedByComplexity) {
            val found = findAllInstances(readyBoard, type).firstOrNull()
            if (found != null) return found
        }
        return null
    }

    /**
     * Verifies Step 1 of the Two-Step Pattern Drill (selecting the pattern's defining cells).
     * Accepts either the exercise's canonical [expected] instance or any other valid instance
     * of the same [PatternType] currently present on the board.
     */
    fun verifyStep1Selection(
        board: SudokuBoard,
        selectedCells: Set<CellPos>,
        expected: PatternInstance
    ): Pair<VerificationFeedback, PatternInstance?> {
        if (selectedCells.isEmpty()) {
            return VerificationFeedback.Incorrect(
                "No pattern cells selected yet. Switch Ink Mode to 'Pattern Cells' (or tap cells) to highlight the cells forming the ${expected.patternType.displayName}."
            ) to null
        }

        if (selectedCells == expected.definingCells) {
            return VerificationFeedback.Success to expected
        }

        // Check if the user found another valid instance of the same pattern on the board
        val allValid = findAllInstances(board, expected.patternType)
        val matched = allValid.firstOrNull { it.definingCells == selectedCells }
        if (matched != null) {
            return VerificationFeedback.Success to matched
        }

        val expectedCount = expected.definingCells.size
        if (selectedCells.size != expectedCount) {
            return VerificationFeedback.Incorrect(
                "You selected ${selectedCells.size} cell(s) (${selectedCells.sorted().joinToString { it.coordLabel }}), " +
                    "but this ${expected.patternType.displayName} requires $expectedCount defining cells."
            ) to null
        }

        val wrongCells = selectedCells - expected.definingCells
        val firstWrong = wrongCells.first()
        val candsAtWrong = CandidateCalculator.precomputeCandidates(board).candidatesAt(firstWrong)
        val focusStr = expected.focusDigits.sorted().joinToString(", ")

        val detail = if ((candsAtWrong intersect expected.focusDigits).isEmpty()) {
            "Cell ${firstWrong.coordLabel} does not contain the focus candidate(s) {$focusStr}."
        } else {
            "Cell ${firstWrong.coordLabel} is not part of the ${expected.patternType.displayName} lock structure. Try inspecting the unit or use a Stage 1 Hint!"
        }

        return VerificationFeedback.Incorrect(detail) to null
    }

    /**
     * Verifies Step 2 of the Two-Step Pattern Drill (eliminating the target candidates).
     */
    fun verifyStep2Eliminations(
        initialBoardWithCandidates: SudokuBoard,
        currentBoard: SudokuBoard,
        activePattern: PatternInstance
    ): VerificationFeedback {
        if (!currentBoard.candidatesPrecomputed) {
            return VerificationFeedback.Incorrect(
                "Tap 'Precompute Candidates' first so you can view and eliminate the target candidate notes."
            )
        }

        // Compute which candidates the user has eliminated relative to the precomputed board
        val userEliminations = mutableSetOf<CandidateElimination>()
        for (r in 0..8) {
            for (c in 0..8) {
                val pos = CellPos(r, c)
                val startCands = initialBoardWithCandidates.candidatesAt(pos)
                val nowCands = currentBoard.candidatesAt(pos)
                for (removed in (startCands - nowCands)) {
                    userEliminations.add(CandidateElimination(pos, removed))
                }
            }
        }

        val target = activePattern.targetEliminations
        val wrongElims = userEliminations - target
        if (wrongElims.isNotEmpty()) {
            val firstWrong = wrongElims.first()
            val isTrueSolution = currentBoard.solutionDigits[firstWrong.cell.row][firstWrong.cell.col] == firstWrong.digit
            val msg = if (isTrueSolution) {
                "Careful! Candidate ${firstWrong.digit} in ${firstWrong.cell.coordLabel} is actually the true solution for that cell and cannot be eliminated."
            } else {
                "Candidate ${firstWrong.digit} in ${firstWrong.cell.coordLabel} is not eliminated by this ${activePattern.patternType.displayName}. Restore it or use Undo."
            }
            return VerificationFeedback.Incorrect(msg)
        }

        val missingElims = target - userEliminations
        if (missingElims.isNotEmpty()) {
            val doneCount = target.size - missingElims.size
            return VerificationFeedback.Incorrect(
                "You have eliminated $doneCount of ${target.size} target candidate(s). " +
                    "Still ${missingElims.size} more candidate elimination(s) dictated by this ${activePattern.patternType.displayName}!"
            )
        }

        return VerificationFeedback.Success
    }

    // =========================================================================
    // 1. NAKED PAIR
    // =========================================================================
    private fun detectNakedPairs(board: SudokuBoard): List<PatternInstance> {
        val results = mutableListOf<PatternInstance>()
        val seenSets = mutableSetOf<Set<CellPos>>()

        for ((unitName, unitCells) in allUnits()) {
            val biValueCells = unitCells.filter { board.candidatesAt(it).size == 2 }
            for (i in biValueCells.indices) {
                for (j in i + 1 until biValueCells.size) {
                    val c1 = biValueCells[i]
                    val c2 = biValueCells[j]
                    val pairCands = board.candidatesAt(c1)
                    if (pairCands == board.candidatesAt(c2)) {
                        val defining = setOf(c1, c2)
                        if (!seenSets.add(defining)) continue

                        // Eliminations in all cells that see both c1 and c2
                        val eliminations = mutableSetOf<CandidateElimination>()
                        for (r in 0..8) {
                            for (c in 0..8) {
                                val p = CellPos(r, c)
                                if (p !in defining && p.isPeerOf(c1) && p.isPeerOf(c2)) {
                                    for (d in pairCands) {
                                        if (d in board.candidatesAt(p)) {
                                            eliminations.add(CandidateElimination(p, d))
                                        }
                                    }
                                }
                            }
                        }

                        if (eliminations.isNotEmpty()) {
                            val dList = pairCands.sorted()
                            val links = listOf(PatternLink(c1, c2, dList.first(), LinkType.STRONG)) +
                                eliminations.map { PatternLink(c1, it.cell, it.digit, LinkType.ELIMINATION_RAY) }

                            results.add(
                                PatternInstance(
                                    patternType = PatternType.NAKED_PAIR,
                                    definingCells = defining,
                                    focusDigits = pairCands,
                                    targetEliminations = eliminations,
                                    visualLinks = links,
                                    stage1Hint = "Inspect $unitName for two bi-value cells sharing the candidates {${dList.joinToString(", ")}}.",
                                    stage2Hint = "Cells ${c1.coordLabel} and ${c2.coordLabel} both contain ONLY {${dList.joinToString(", ")}}—forming a Naked Pair in $unitName.",
                                    stage3Hint = "Since ${c1.coordLabel} and ${c2.coordLabel} lock {${dList.joinToString(", ")}}, eliminate ${eliminations.sortedBy { it.cell }.joinToString(", ") { it.label }}."
                                )
                            )
                        }
                    }
                }
            }
        }
        return results
    }

    // =========================================================================
    // 2. HIDDEN PAIR
    // =========================================================================
    private fun detectHiddenPairs(board: SudokuBoard): List<PatternInstance> {
        val results = mutableListOf<PatternInstance>()
        val seen = mutableSetOf<Pair<Set<CellPos>, Set<Int>>>()

        for ((unitName, unitCells) in allUnits()) {
            val digitPositions = (1..9).associateWith { d ->
                unitCells.filter { d in board.candidatesAt(it) }.toSet()
            }
            val twoPosDigits = (1..9).filter { digitPositions[it]?.size == 2 }
            for (i in twoPosDigits.indices) {
                for (j in i + 1 until twoPosDigits.size) {
                    val d1 = twoPosDigits[i]
                    val d2 = twoPosDigits[j]
                    val cells = digitPositions[d1]!!
                    if (cells == digitPositions[d2]) {
                        val pairDigits = setOf(d1, d2)
                        if (!seen.add(cells to pairDigits)) continue

                        val eliminations = mutableSetOf<CandidateElimination>()
                        for (cell in cells) {
                            for (other in (board.candidatesAt(cell) - pairDigits)) {
                                eliminations.add(CandidateElimination(cell, other))
                            }
                        }

                        if (eliminations.isNotEmpty()) {
                            val sortedCells = cells.sorted()
                            val c1 = sortedCells[0]
                            val c2 = sortedCells[1]
                            results.add(
                                PatternInstance(
                                    patternType = PatternType.HIDDEN_PAIR,
                                    definingCells = cells,
                                    focusDigits = pairDigits,
                                    targetEliminations = eliminations,
                                    visualLinks = listOf(PatternLink(c1, c2, d1, LinkType.STRONG)),
                                    stage1Hint = "Look closely at $unitName for two digits {$d1, $d2} that only appear in the exact same two cells.",
                                    stage2Hint = "In $unitName, candidates $d1 and $d2 only appear in ${c1.coordLabel} and ${c2.coordLabel}, forming a Hidden Pair.",
                                    stage3Hint = "Because ${c1.coordLabel} and ${c2.coordLabel} must hold {$d1, $d2}, eliminate all other candidates inside them: ${eliminations.sortedBy { it.cell }.joinToString(", ") { it.label }}."
                                )
                            )
                        }
                    }
                }
            }
        }
        return results
    }

    // =========================================================================
    // 3. NAKED TRIPLE
    // =========================================================================
    private fun detectNakedTriples(board: SudokuBoard): List<PatternInstance> {
        val results = mutableListOf<PatternInstance>()
        val seenSets = mutableSetOf<Set<CellPos>>()

        for ((unitName, unitCells) in allUnits()) {
            val eligible = unitCells.filter { board.candidatesAt(it).size in 2..3 }
            for (i in eligible.indices) {
                for (j in i + 1 until eligible.size) {
                    for (k in j + 1 until eligible.size) {
                        val c1 = eligible[i]
                        val c2 = eligible[j]
                        val c3 = eligible[k]
                        val s1 = board.candidatesAt(c1)
                        val s2 = board.candidatesAt(c2)
                        val s3 = board.candidatesAt(c3)
                        // Skip degenerate triples that contain a Naked Pair
                        if ((s1.size == 2 && s1 == s2) || (s1.size == 2 && s1 == s3) || (s2.size == 2 && s2 == s3)) {
                            continue
                        }
                        val union = s1 union s2 union s3
                        if (union.size == 3) {
                            val defining = setOf(c1, c2, c3)
                            if (!seenSets.add(defining)) continue

                            val eliminations = mutableSetOf<CandidateElimination>()
                            for (other in unitCells) {
                                if (other !in defining) {
                                    for (d in union) {
                                        if (d in board.candidatesAt(other)) {
                                            eliminations.add(CandidateElimination(other, d))
                                        }
                                    }
                                }
                            }

                            if (eliminations.isNotEmpty()) {
                                val dSorted = union.sorted()
                                val links = listOf(
                                    PatternLink(c1, c2, dSorted[0], LinkType.STRONG),
                                    PatternLink(c2, c3, dSorted[1], LinkType.STRONG)
                                )
                                results.add(
                                    PatternInstance(
                                        patternType = PatternType.NAKED_TRIPLE,
                                        definingCells = defining,
                                        focusDigits = union,
                                        targetEliminations = eliminations,
                                        visualLinks = links,
                                        stage1Hint = "Inspect $unitName for three cells whose candidates are restricted to {${dSorted.joinToString(", ")}}.",
                                        stage2Hint = "Cells ${c1.coordLabel}, ${c2.coordLabel}, and ${c3.coordLabel} only contain candidates from {${dSorted.joinToString(", ")}}, forming a Naked Triple in $unitName.",
                                        stage3Hint = "Eliminate {${dSorted.joinToString(", ")}} from the rest of $unitName: ${eliminations.sortedBy { it.cell }.joinToString(", ") { it.label }}."
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
        return results
    }

    // =========================================================================
    // 4. POINTING PAIRS / TRIPLES
    // =========================================================================
    private fun detectPointingPairs(board: SudokuBoard): List<PatternInstance> {
        val results = mutableListOf<PatternInstance>()

        for (box in 0..8) {
            val boxCells = boxCells(box)
            for (d in 1..9) {
                val withD = boxCells.filter { d in board.candidatesAt(it) }
                if (withD.size in 2..3) {
                    val rows = withD.map { it.row }.toSet()
                    val cols = withD.map { it.col }.toSet()
                    if (rows.size == 1) {
                        val r = rows.first()
                        val outsidePeers = rowCells(r).filter { it.boxIndex != box && d in board.candidatesAt(it) }
                        if (outsidePeers.isNotEmpty()) {
                            val defining = withD.toSet()
                            val elims = outsidePeers.map { CandidateElimination(it, d) }.toSet()
                            val links = withD.zipWithNext { a, b -> PatternLink(a, b, d, LinkType.STRONG) } +
                                outsidePeers.map { PatternLink(withD.last(), it, d, LinkType.ELIMINATION_RAY) }
                            results.add(
                                PatternInstance(
                                    patternType = PatternType.POINTING_PAIR,
                                    definingCells = defining,
                                    focusDigits = setOf(d),
                                    targetEliminations = elims,
                                    visualLinks = links,
                                    stage1Hint = "Filter for candidate $d and inspect Box ${box + 1} and Row ${r + 1}.",
                                    stage2Hint = "In Box ${box + 1}, all occurrences of candidate $d (${withD.joinToString { it.coordLabel }}) lie along Row ${r + 1}, forming a Pointing ${if (withD.size == 2) "Pair" else "Triple"}.",
                                    stage3Hint = "Because Box ${box + 1} must place $d in Row ${r + 1}, eliminate $d from the rest of Row ${r + 1}: ${elims.sortedBy { it.cell }.joinToString(", ") { it.label }}."
                                )
                            )
                        }
                    }
                    if (cols.size == 1) {
                        val c = cols.first()
                        val outsidePeers = colCells(c).filter { it.boxIndex != box && d in board.candidatesAt(it) }
                        if (outsidePeers.isNotEmpty()) {
                            val defining = withD.toSet()
                            val elims = outsidePeers.map { CandidateElimination(it, d) }.toSet()
                            val links = withD.zipWithNext { a, b -> PatternLink(a, b, d, LinkType.STRONG) } +
                                outsidePeers.map { PatternLink(withD.last(), it, d, LinkType.ELIMINATION_RAY) }
                            results.add(
                                PatternInstance(
                                    patternType = PatternType.POINTING_PAIR,
                                    definingCells = defining,
                                    focusDigits = setOf(d),
                                    targetEliminations = elims,
                                    visualLinks = links,
                                    stage1Hint = "Filter for candidate $d and inspect Box ${box + 1} and Column ${c + 1}.",
                                    stage2Hint = "In Box ${box + 1}, all occurrences of candidate $d (${withD.joinToString { it.coordLabel }}) lie along Column ${c + 1}, forming a Pointing ${if (withD.size == 2) "Pair" else "Triple"}.",
                                    stage3Hint = "Because Box ${box + 1} must place $d in Column ${c + 1}, eliminate $d from the rest of Column ${c + 1}: ${elims.sortedBy { it.cell }.joinToString(", ") { it.label }}."
                                )
                            )
                        }
                    }
                }
            }
        }
        return results
    }

    // =========================================================================
    // 5. BOX/LINE REDUCTION (CLAIMING)
    // =========================================================================
    private fun detectBoxLineReductions(board: SudokuBoard): List<PatternInstance> {
        val results = mutableListOf<PatternInstance>()

        // Row claiming a Box
        for (r in 0..8) {
            val rCells = rowCells(r)
            for (d in 1..9) {
                val withD = rCells.filter { d in board.candidatesAt(it) }
                if (withD.size in 2..3) {
                    val boxes = withD.map { it.boxIndex }.toSet()
                    if (boxes.size == 1) {
                        val box = boxes.first()
                        val boxOthers = boxCells(box).filter { it.row != r && d in board.candidatesAt(it) }
                        if (boxOthers.isNotEmpty()) {
                            val defining = withD.toSet()
                            val elims = boxOthers.map { CandidateElimination(it, d) }.toSet()
                            val links = withD.zipWithNext { a, b -> PatternLink(a, b, d, LinkType.STRONG) } +
                                boxOthers.map { PatternLink(withD.first(), it, d, LinkType.ELIMINATION_RAY) }
                            results.add(
                                PatternInstance(
                                    patternType = PatternType.BOX_LINE_REDUCTION,
                                    definingCells = defining,
                                    focusDigits = setOf(d),
                                    targetEliminations = elims,
                                    visualLinks = links,
                                    stage1Hint = "Filter for candidate $d and look at where it can go along Row ${r + 1}.",
                                    stage2Hint = "In Row ${r + 1}, all candidates for $d (${withD.joinToString { it.coordLabel }}) fall inside Box ${box + 1} (Box/Line Reduction).",
                                    stage3Hint = "Since Row ${r + 1} claims digit $d for Box ${box + 1}, eliminate $d from the other cells in Box ${box + 1}: ${elims.sortedBy { it.cell }.joinToString(", ") { it.label }}."
                                )
                            )
                        }
                    }
                }
            }
        }

        // Column claiming a Box
        for (c in 0..8) {
            val cCells = colCells(c)
            for (d in 1..9) {
                val withD = cCells.filter { d in board.candidatesAt(it) }
                if (withD.size in 2..3) {
                    val boxes = withD.map { it.boxIndex }.toSet()
                    if (boxes.size == 1) {
                        val box = boxes.first()
                        val boxOthers = boxCells(box).filter { it.col != c && d in board.candidatesAt(it) }
                        if (boxOthers.isNotEmpty()) {
                            val defining = withD.toSet()
                            val elims = boxOthers.map { CandidateElimination(it, d) }.toSet()
                            val links = withD.zipWithNext { a, b -> PatternLink(a, b, d, LinkType.STRONG) } +
                                boxOthers.map { PatternLink(withD.first(), it, d, LinkType.ELIMINATION_RAY) }
                            results.add(
                                PatternInstance(
                                    patternType = PatternType.BOX_LINE_REDUCTION,
                                    definingCells = defining,
                                    focusDigits = setOf(d),
                                    targetEliminations = elims,
                                    visualLinks = links,
                                    stage1Hint = "Filter for candidate $d and look at where it can go along Column ${c + 1}.",
                                    stage2Hint = "In Column ${c + 1}, all candidates for $d (${withD.joinToString { it.coordLabel }}) fall inside Box ${box + 1} (Box/Line Reduction).",
                                    stage3Hint = "Since Column ${c + 1} claims digit $d for Box ${box + 1}, eliminate $d from the other cells in Box ${box + 1}: ${elims.sortedBy { it.cell }.joinToString(", ") { it.label }}."
                                )
                            )
                        }
                    }
                }
            }
        }

        return results
    }

    // =========================================================================
    // 6. X-WING
    // =========================================================================
    private fun detectXWings(board: SudokuBoard): List<PatternInstance> {
        val results = mutableListOf<PatternInstance>()

        for (d in 1..9) {
            // Row-based X-Wing
            val twoColRows = (0..8).mapNotNull { r ->
                val cols = (0..8).filter { c -> d in board.candidatesAt(CellPos(r, c)) }
                if (cols.size == 2) r to cols.toSet() else null
            }
            for (i in twoColRows.indices) {
                for (j in i + 1 until twoColRows.size) {
                    val (r1, cols1) = twoColRows[i]
                    val (r2, cols2) = twoColRows[j]
                    if (cols1 == cols2) {
                        val sortedCols = cols1.sorted()
                        val c1 = sortedCols[0]
                        val c2 = sortedCols[1]
                        val defining = setOf(
                            CellPos(r1, c1), CellPos(r1, c2),
                            CellPos(r2, c1), CellPos(r2, c2)
                        )
                        val elims = mutableSetOf<CandidateElimination>()
                        for (r in 0..8) {
                            if (r != r1 && r != r2) {
                                if (d in board.candidatesAt(CellPos(r, c1))) elims.add(CandidateElimination(CellPos(r, c1), d))
                                if (d in board.candidatesAt(CellPos(r, c2))) elims.add(CandidateElimination(CellPos(r, c2), d))
                            }
                        }
                        if (elims.isNotEmpty()) {
                            val links = listOf(
                                PatternLink(CellPos(r1, c1), CellPos(r1, c2), d, LinkType.STRONG),
                                PatternLink(CellPos(r2, c1), CellPos(r2, c2), d, LinkType.STRONG),
                                PatternLink(CellPos(r1, c1), CellPos(r2, c1), d, LinkType.WEAK),
                                PatternLink(CellPos(r1, c2), CellPos(r2, c2), d, LinkType.WEAK)
                            )
                            results.add(
                                PatternInstance(
                                    patternType = PatternType.X_WING,
                                    definingCells = defining,
                                    focusDigits = setOf(d),
                                    targetEliminations = elims,
                                    visualLinks = links,
                                    stage1Hint = "Filter for candidate $d and inspect Rows ${r1 + 1} and ${r2 + 1}.",
                                    stage2Hint = "In Rows ${r1 + 1} and ${r2 + 1}, candidate $d appears ONLY in Columns ${c1 + 1} and ${c2 + 1}, forming a 4-corner X-Wing (${defining.sorted().joinToString { it.coordLabel }}).",
                                    stage3Hint = "Because Rows ${r1 + 1} and ${r2 + 1} lock $d into Columns ${c1 + 1} and ${c2 + 1}, eliminate $d from all other cells in Columns ${c1 + 1} and ${c2 + 1}: ${elims.sortedBy { it.cell }.joinToString(", ") { it.label }}."
                                )
                            )
                        }
                    }
                }
            }

            // Column-based X-Wing
            val twoRowCols = (0..8).mapNotNull { c ->
                val rows = (0..8).filter { r -> d in board.candidatesAt(CellPos(r, c)) }
                if (rows.size == 2) c to rows.toSet() else null
            }
            for (i in twoRowCols.indices) {
                for (j in i + 1 until twoRowCols.size) {
                    val (c1, rows1) = twoRowCols[i]
                    val (c2, rows2) = twoRowCols[j]
                    if (rows1 == rows2) {
                        val sortedRows = rows1.sorted()
                        val r1 = sortedRows[0]
                        val r2 = sortedRows[1]
                        val defining = setOf(
                            CellPos(r1, c1), CellPos(r2, c1),
                            CellPos(r1, c2), CellPos(r2, c2)
                        )
                        val elims = mutableSetOf<CandidateElimination>()
                        for (c in 0..8) {
                            if (c != c1 && c != c2) {
                                if (d in board.candidatesAt(CellPos(r1, c))) elims.add(CandidateElimination(CellPos(r1, c), d))
                                if (d in board.candidatesAt(CellPos(r2, c))) elims.add(CandidateElimination(CellPos(r2, c), d))
                            }
                        }
                        if (elims.isNotEmpty()) {
                            val links = listOf(
                                PatternLink(CellPos(r1, c1), CellPos(r2, c1), d, LinkType.STRONG),
                                PatternLink(CellPos(r1, c2), CellPos(r2, c2), d, LinkType.STRONG),
                                PatternLink(CellPos(r1, c1), CellPos(r1, c2), d, LinkType.WEAK),
                                PatternLink(CellPos(r2, c1), CellPos(r2, c2), d, LinkType.WEAK)
                            )
                            results.add(
                                PatternInstance(
                                    patternType = PatternType.X_WING,
                                    definingCells = defining,
                                    focusDigits = setOf(d),
                                    targetEliminations = elims,
                                    visualLinks = links,
                                    stage1Hint = "Filter for candidate $d and inspect Columns ${c1 + 1} and ${c2 + 1}.",
                                    stage2Hint = "In Columns ${c1 + 1} and ${c2 + 1}, candidate $d appears ONLY in Rows ${r1 + 1} and ${r2 + 1}, forming a 4-corner X-Wing (${defining.sorted().joinToString { it.coordLabel }}).",
                                    stage3Hint = "Because Columns ${c1 + 1} and ${c2 + 1} lock $d into Rows ${r1 + 1} and ${r2 + 1}, eliminate $d from all other cells in Rows ${r1 + 1} and ${r2 + 1}: ${elims.sortedBy { it.cell }.joinToString(", ") { it.label }}."
                                )
                            )
                        }
                    }
                }
            }
        }
        return results
    }

    // =========================================================================
    // 7. SKYSCRAPER
    // =========================================================================
    private fun detectSkyscrapers(board: SudokuBoard): List<PatternInstance> {
        val results = mutableListOf<PatternInstance>()

        for (d in 1..9) {
            // Row-based Skyscraper
            val conjugateRows = (0..8).mapNotNull { r ->
                val cells = (0..8).map { CellPos(r, it) }.filter { d in board.candidatesAt(it) }
                if (cells.size == 2) r to cells else null
            }
            for (i in conjugateRows.indices) {
                for (j in i + 1 until conjugateRows.size) {
                    val (r1, cells1) = conjugateRows[i]
                    val (r2, cells2) = conjugateRows[j]
                    val cols1 = cells1.map { it.col }.toSet()
                    val cols2 = cells2.map { it.col }.toSet()
                    val sharedCols = cols1 intersect cols2
                    if (sharedCols.size == 1) {
                        val baseCol = sharedCols.first()
                        val base1 = cells1.first { it.col == baseCol }
                        val roof1 = cells1.first { it.col != baseCol }
                        val base2 = cells2.first { it.col == baseCol }
                        val roof2 = cells2.first { it.col != baseCol }
                        val defining = setOf(base1, roof1, base2, roof2)

                        val elims = mutableSetOf<CandidateElimination>()
                        for (r in 0..8) {
                            for (c in 0..8) {
                                val p = CellPos(r, c)
                                if (p !in defining && p.isPeerOf(roof1) && p.isPeerOf(roof2) && d in board.candidatesAt(p)) {
                                    elims.add(CandidateElimination(p, d))
                                }
                            }
                        }

                        if (elims.isNotEmpty()) {
                            val links = listOf(
                                PatternLink(roof1, base1, d, LinkType.STRONG),
                                PatternLink(base1, base2, d, LinkType.WEAK),
                                PatternLink(base2, roof2, d, LinkType.STRONG)
                            ) + elims.flatMap {
                                listOf(
                                    PatternLink(roof1, it.cell, d, LinkType.ELIMINATION_RAY),
                                    PatternLink(roof2, it.cell, d, LinkType.ELIMINATION_RAY)
                                )
                            }
                            results.add(
                                PatternInstance(
                                    patternType = PatternType.SKYSCRAPER,
                                    definingCells = defining,
                                    focusDigits = setOf(d),
                                    targetEliminations = elims,
                                    visualLinks = links,
                                    stage1Hint = "Filter for candidate $d and inspect Rows ${r1 + 1} and ${r2 + 1}, which each have only two positions for $d.",
                                    stage2Hint = "Rows ${r1 + 1} and ${r2 + 1} form a Skyscraper on digit $d: their base cells ${base1.coordLabel} and ${base2.coordLabel} share Column ${baseCol + 1}, while ${roof1.coordLabel} and ${roof2.coordLabel} are the two roof tops.",
                                    stage3Hint = "At least one roof cell (${roof1.coordLabel} or ${roof2.coordLabel}) must be $d. Eliminate $d from any cell that sees both roof tops: ${elims.sortedBy { it.cell }.joinToString(", ") { it.label }}."
                                )
                            )
                        }
                    }
                }
            }

            // Column-based Skyscraper
            val conjugateCols = (0..8).mapNotNull { c ->
                val cells = (0..8).map { CellPos(it, c) }.filter { d in board.candidatesAt(it) }
                if (cells.size == 2) c to cells else null
            }
            for (i in conjugateCols.indices) {
                for (j in i + 1 until conjugateCols.size) {
                    val (c1, cells1) = conjugateCols[i]
                    val (c2, cells2) = conjugateCols[j]
                    val rows1 = cells1.map { it.row }.toSet()
                    val rows2 = cells2.map { it.row }.toSet()
                    val sharedRows = rows1 intersect rows2
                    if (sharedRows.size == 1) {
                        val baseRow = sharedRows.first()
                        val base1 = cells1.first { it.row == baseRow }
                        val roof1 = cells1.first { it.row != baseRow }
                        val base2 = cells2.first { it.row == baseRow }
                        val roof2 = cells2.first { it.row != baseRow }
                        val defining = setOf(base1, roof1, base2, roof2)

                        val elims = mutableSetOf<CandidateElimination>()
                        for (r in 0..8) {
                            for (c in 0..8) {
                                val p = CellPos(r, c)
                                if (p !in defining && p.isPeerOf(roof1) && p.isPeerOf(roof2) && d in board.candidatesAt(p)) {
                                    elims.add(CandidateElimination(p, d))
                                }
                            }
                        }

                        if (elims.isNotEmpty()) {
                            val links = listOf(
                                PatternLink(roof1, base1, d, LinkType.STRONG),
                                PatternLink(base1, base2, d, LinkType.WEAK),
                                PatternLink(base2, roof2, d, LinkType.STRONG)
                            ) + elims.flatMap {
                                listOf(
                                    PatternLink(roof1, it.cell, d, LinkType.ELIMINATION_RAY),
                                    PatternLink(roof2, it.cell, d, LinkType.ELIMINATION_RAY)
                                )
                            }
                            results.add(
                                PatternInstance(
                                    patternType = PatternType.SKYSCRAPER,
                                    definingCells = defining,
                                    focusDigits = setOf(d),
                                    targetEliminations = elims,
                                    visualLinks = links,
                                    stage1Hint = "Filter for candidate $d and inspect Columns ${c1 + 1} and ${c2 + 1}, which each have only two positions for $d.",
                                    stage2Hint = "Columns ${c1 + 1} and ${c2 + 1} form a Skyscraper on digit $d: their base cells ${base1.coordLabel} and ${base2.coordLabel} share Row ${baseRow + 1}, while ${roof1.coordLabel} and ${roof2.coordLabel} are the two roof tops.",
                                    stage3Hint = "At least one roof cell (${roof1.coordLabel} or ${roof2.coordLabel}) must be $d. Eliminate $d from any cell that sees both roof tops: ${elims.sortedBy { it.cell }.joinToString(", ") { it.label }}."
                                )
                            )
                        }
                    }
                }
            }
        }
        return results
    }

    // =========================================================================
    // 8. 2-STRING KITE
    // =========================================================================
    private fun detectTwoStringKites(board: SudokuBoard): List<PatternInstance> {
        val results = mutableListOf<PatternInstance>()
        val seen = mutableSetOf<Pair<Set<CellPos>, Int>>()

        for (d in 1..9) {
            val conjugateRows = (0..8).mapNotNull { r ->
                val cells = (0..8).map { CellPos(r, it) }.filter { d in board.candidatesAt(it) }
                if (cells.size == 2) r to cells else null
            }
            val conjugateCols = (0..8).mapNotNull { c ->
                val cells = (0..8).map { CellPos(it, c) }.filter { d in board.candidatesAt(it) }
                if (cells.size == 2) c to cells else null
            }

            for ((r, rCells) in conjugateRows) {
                for ((c, cCells) in conjugateCols) {
                    for (rIdx in 0..1) {
                        for (cIdx in 0..1) {
                            val rBoxCell = rCells[rIdx]
                            val rTailCell = rCells[1 - rIdx]
                            val cBoxCell = cCells[cIdx]
                            val cTailCell = cCells[1 - cIdx]

                            if (rBoxCell != cBoxCell &&
                                rBoxCell.boxIndex == cBoxCell.boxIndex &&
                                rBoxCell.row != cBoxCell.row &&
                                rBoxCell.col != cBoxCell.col &&
                                rTailCell.boxIndex != rBoxCell.boxIndex &&
                                cTailCell.boxIndex != cBoxCell.boxIndex
                            ) {
                                val defining = setOf(rBoxCell, rTailCell, cBoxCell, cTailCell)
                                if (defining.size < 4) continue

                                val targetCell = CellPos(cTailCell.row, rTailCell.col)
                                if (targetCell !in defining && d in board.candidatesAt(targetCell)) {
                                    if (!seen.add(defining to d)) continue
                                    val elim = CandidateElimination(targetCell, d)
                                    val links = listOf(
                                        PatternLink(rTailCell, rBoxCell, d, LinkType.STRONG),
                                        PatternLink(rBoxCell, cBoxCell, d, LinkType.WEAK),
                                        PatternLink(cBoxCell, cTailCell, d, LinkType.STRONG),
                                        PatternLink(rTailCell, targetCell, d, LinkType.ELIMINATION_RAY),
                                        PatternLink(cTailCell, targetCell, d, LinkType.ELIMINATION_RAY)
                                    )
                                    results.add(
                                        PatternInstance(
                                            patternType = PatternType.TWO_STRING_KITE,
                                            definingCells = defining,
                                            focusDigits = setOf(d),
                                            targetEliminations = setOf(elim),
                                            visualLinks = links,
                                            stage1Hint = "Filter for candidate $d and look at Row ${r + 1}, Column ${c + 1}, and Box ${rBoxCell.boxNumber}.",
                                            stage2Hint = "Row ${r + 1} (${rBoxCell.coordLabel}, ${rTailCell.coordLabel}) and Column ${c + 1} (${cBoxCell.coordLabel}, ${cTailCell.coordLabel}) each have 2 positions for $d and connect inside Box ${rBoxCell.boxNumber}, forming a 2-String Kite.",
                                            stage3Hint = "Since ${rBoxCell.coordLabel} and ${cBoxCell.coordLabel} share Box ${rBoxCell.boxNumber}, at least one outer end (${rTailCell.coordLabel} or ${cTailCell.coordLabel}) must be $d. Eliminate ${elim.label} at their intersection!"
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        return results
    }

    // =========================================================================
    // 9. SWORDFISH
    // =========================================================================
    private fun detectSwordfish(board: SudokuBoard): List<PatternInstance> {
        val results = mutableListOf<PatternInstance>()

        for (d in 1..9) {
            // Row-based Swordfish
            val eligibleRows = (0..8).mapNotNull { r ->
                val cols = (0..8).filter { c -> d in board.candidatesAt(CellPos(r, c)) }.toSet()
                if (cols.size in 2..3) r to cols else null
            }
            for (i in eligibleRows.indices) {
                for (j in i + 1 until eligibleRows.size) {
                    for (k in j + 1 until eligibleRows.size) {
                        val (r1, cols1) = eligibleRows[i]
                        val (r2, cols2) = eligibleRows[j]
                        val (r3, cols3) = eligibleRows[k]
                        // Skip if any two rows already form a 2-column X-Wing
                        if ((cols1.size == 2 && cols1 == cols2) ||
                            (cols1.size == 2 && cols1 == cols3) ||
                            (cols2.size == 2 && cols2 == cols3)
                        ) continue

                        val unionCols = cols1 union cols2 union cols3
                        if (unionCols.size == 3) {
                            val baseRows = setOf(r1, r2, r3)
                            val defining = mutableSetOf<CellPos>()
                            for (r in baseRows) {
                                for (c in unionCols) {
                                    val pos = CellPos(r, c)
                                    if (d in board.candidatesAt(pos)) defining.add(pos)
                                }
                            }
                            val elims = mutableSetOf<CandidateElimination>()
                            for (r in 0..8) {
                                if (r !in baseRows) {
                                    for (c in unionCols) {
                                        val pos = CellPos(r, c)
                                        if (d in board.candidatesAt(pos)) {
                                            elims.add(CandidateElimination(pos, d))
                                        }
                                    }
                                }
                            }
                            if (elims.isNotEmpty()) {
                                val sortedCols = unionCols.sorted()
                                val links = mutableListOf<PatternLink>()
                                for (r in listOf(r1, r2, r3)) {
                                    val rowMatching = defining.filter { it.row == r }.sortedBy { it.col }
                                    links.addAll(rowMatching.zipWithNext { a, b -> PatternLink(a, b, d, LinkType.STRONG) })
                                }
                                results.add(
                                    PatternInstance(
                                        patternType = PatternType.SWORDFISH,
                                        definingCells = defining,
                                        focusDigits = setOf(d),
                                        targetEliminations = elims,
                                        visualLinks = links,
                                        stage1Hint = "Filter for candidate $d and inspect Rows ${r1 + 1}, ${r2 + 1}, and ${r3 + 1}.",
                                        stage2Hint = "In Rows ${r1 + 1}, ${r2 + 1}, and ${r3 + 1}, candidate $d is restricted to the same 3 columns (Columns ${sortedCols.joinToString { (it + 1).toString() }}), forming a Swordfish across ${defining.size} cells.",
                                        stage3Hint = "Because Rows ${r1 + 1}, ${r2 + 1}, and ${r3 + 1} lock $d into Columns ${sortedCols.joinToString { (it + 1).toString() }}, eliminate $d from all other cells in those columns: ${elims.sortedBy { it.cell }.joinToString(", ") { it.label }}."
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Column-based Swordfish
            val eligibleCols = (0..8).mapNotNull { c ->
                val rows = (0..8).filter { r -> d in board.candidatesAt(CellPos(r, c)) }.toSet()
                if (rows.size in 2..3) c to rows else null
            }
            for (i in eligibleCols.indices) {
                for (j in i + 1 until eligibleCols.size) {
                    for (k in j + 1 until eligibleCols.size) {
                        val (c1, rows1) = eligibleCols[i]
                        val (c2, rows2) = eligibleCols[j]
                        val (c3, rows3) = eligibleCols[k]
                        if ((rows1.size == 2 && rows1 == rows2) ||
                            (rows1.size == 2 && rows1 == rows3) ||
                            (rows2.size == 2 && rows2 == rows3)
                        ) continue

                        val unionRows = rows1 union rows2 union rows3
                        if (unionRows.size == 3) {
                            val baseCols = setOf(c1, c2, c3)
                            val defining = mutableSetOf<CellPos>()
                            for (c in baseCols) {
                                for (r in unionRows) {
                                    val pos = CellPos(r, c)
                                    if (d in board.candidatesAt(pos)) defining.add(pos)
                                }
                            }
                            val elims = mutableSetOf<CandidateElimination>()
                            for (c in 0..8) {
                                if (c !in baseCols) {
                                    for (r in unionRows) {
                                        val pos = CellPos(r, c)
                                        if (d in board.candidatesAt(pos)) {
                                            elims.add(CandidateElimination(pos, d))
                                        }
                                    }
                                }
                            }
                            if (elims.isNotEmpty()) {
                                val sortedRows = unionRows.sorted()
                                val links = mutableListOf<PatternLink>()
                                for (c in listOf(c1, c2, c3)) {
                                    val colMatching = defining.filter { it.col == c }.sortedBy { it.row }
                                    links.addAll(colMatching.zipWithNext { a, b -> PatternLink(a, b, d, LinkType.STRONG) })
                                }
                                results.add(
                                    PatternInstance(
                                        patternType = PatternType.SWORDFISH,
                                        definingCells = defining,
                                        focusDigits = setOf(d),
                                        targetEliminations = elims,
                                        visualLinks = links,
                                        stage1Hint = "Filter for candidate $d and inspect Columns ${c1 + 1}, ${c2 + 1}, and ${c3 + 1}.",
                                        stage2Hint = "In Columns ${c1 + 1}, ${c2 + 1}, and ${c3 + 1}, candidate $d is restricted to the same 3 rows (Rows ${sortedRows.joinToString { (it + 1).toString() }}), forming a Swordfish across ${defining.size} cells.",
                                        stage3Hint = "Because Columns ${c1 + 1}, ${c2 + 1}, and ${c3 + 1} lock $d into Rows ${sortedRows.joinToString { (it + 1).toString() }}, eliminate $d from all other cells in those rows: ${elims.sortedBy { it.cell }.joinToString(", ") { it.label }}."
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
        return results
    }

    // =========================================================================
    // 10. XY-WING
    // =========================================================================
    private fun detectXYWings(board: SudokuBoard): List<PatternInstance> {
        val results = mutableListOf<PatternInstance>()
        val biValueCells = mutableListOf<CellPos>()
        for (r in 0..8) {
            for (c in 0..8) {
                val pos = CellPos(r, c)
                if (board.candidatesAt(pos).size == 2) biValueCells.add(pos)
            }
        }

        val seen = mutableSetOf<Triple<CellPos, Set<CellPos>, Int>>()

        for (pivot in biValueCells) {
            val pivotCands = board.candidatesAt(pivot).toList()
            val x = pivotCands[0]
            val y = pivotCands[1]
            val peers = biValueCells.filter { it.isPeerOf(pivot) }

            for (pincer1 in peers) {
                val c1 = board.candidatesAt(pincer1)
                if (x !in c1 || y in c1) continue
                val z = (c1 - x).first()

                for (pincer2 in peers) {
                    if (pincer2 == pincer1) continue
                    val c2 = board.candidatesAt(pincer2)
                    if (c2 == setOf(y, z)) {
                        val defining = setOf(pivot, pincer1, pincer2)
                        if (!seen.add(Triple(pivot, defining, z))) continue

                        val elims = mutableSetOf<CandidateElimination>()
                        for (r in 0..8) {
                            for (c in 0..8) {
                                val p = CellPos(r, c)
                                if (p !in defining && p.isPeerOf(pincer1) && p.isPeerOf(pincer2) && z in board.candidatesAt(p)) {
                                    elims.add(CandidateElimination(p, z))
                                }
                            }
                        }

                        if (elims.isNotEmpty()) {
                            val links = listOf(
                                PatternLink(pivot, pincer1, x, LinkType.WING_BRIDGE),
                                PatternLink(pivot, pincer2, y, LinkType.WING_BRIDGE)
                            ) + elims.flatMap {
                                listOf(
                                    PatternLink(pincer1, it.cell, z, LinkType.ELIMINATION_RAY),
                                    PatternLink(pincer2, it.cell, z, LinkType.ELIMINATION_RAY)
                                )
                            }
                            results.add(
                                PatternInstance(
                                    patternType = PatternType.XY_WING,
                                    definingCells = defining,
                                    pivotCell = pivot,
                                    focusDigits = setOf(x, y, z),
                                    targetEliminations = elims,
                                    visualLinks = links,
                                    stage1Hint = "Look for a bi-value Pivot cell around Box ${pivot.boxNumber} (${pivot.coordLabel}) with candidates {$x, $y}.",
                                    stage2Hint = "Pivot ${pivot.coordLabel} {$x, $y} sees two Pincers: ${pincer1.coordLabel} {$x, $z} and ${pincer2.coordLabel} {$y, $z}, forming an XY-Wing on elimination digit $z.",
                                    stage3Hint = "Whether ${pivot.coordLabel} is $x or $y, one of the Pincers (${pincer1.coordLabel} or ${pincer2.coordLabel}) must be $z. Eliminate $z from any cell seeing both Pincers: ${elims.sortedBy { it.cell }.joinToString(", ") { it.label }}."
                                )
                            )
                        }
                    }
                }
            }
        }
        return results
    }

    // =========================================================================
    // 11. XYZ-WING
    // =========================================================================
    private fun detectXYZWings(board: SudokuBoard): List<PatternInstance> {
        val results = mutableListOf<PatternInstance>()
        val triValueCells = mutableListOf<CellPos>()
        val biValueCells = mutableListOf<CellPos>()

        for (r in 0..8) {
            for (c in 0..8) {
                val pos = CellPos(r, c)
                when (board.candidatesAt(pos).size) {
                    2 -> biValueCells.add(pos)
                    3 -> triValueCells.add(pos)
                }
            }
        }

        val seen = mutableSetOf<Triple<CellPos, Set<CellPos>, Int>>()

        for (pivot in triValueCells) {
            val pivotCands = board.candidatesAt(pivot)
            val peerBiCells = biValueCells.filter { it.isPeerOf(pivot) && pivotCands.containsAll(board.candidatesAt(it)) }

            for (i in peerBiCells.indices) {
                for (j in i + 1 until peerBiCells.size) {
                    val pincer1 = peerBiCells[i]
                    val pincer2 = peerBiCells[j]
                    val c1 = board.candidatesAt(pincer1)
                    val c2 = board.candidatesAt(pincer2)
                    if (c1 == c2) continue

                    val sharedZSet = c1 intersect c2
                    if (sharedZSet.size == 1) {
                        val z = sharedZSet.first()
                        val defining = setOf(pivot, pincer1, pincer2)
                        if (!seen.add(Triple(pivot, defining, z))) continue

                        val elims = mutableSetOf<CandidateElimination>()
                        for (r in 0..8) {
                            for (c in 0..8) {
                                val p = CellPos(r, c)
                                if (p !in defining &&
                                    p.isPeerOf(pivot) &&
                                    p.isPeerOf(pincer1) &&
                                    p.isPeerOf(pincer2) &&
                                    z in board.candidatesAt(p)
                                ) {
                                    elims.add(CandidateElimination(p, z))
                                }
                            }
                        }

                        if (elims.isNotEmpty()) {
                            val links = listOf(
                                PatternLink(pivot, pincer1, (c1 - z).first(), LinkType.WING_BRIDGE),
                                PatternLink(pivot, pincer2, (c2 - z).first(), LinkType.WING_BRIDGE)
                            ) + elims.map { PatternLink(pivot, it.cell, z, LinkType.ELIMINATION_RAY) }

                            val pSorted = pivotCands.sorted()
                            results.add(
                                PatternInstance(
                                    patternType = PatternType.XYZ_WING,
                                    definingCells = defining,
                                    pivotCell = pivot,
                                    focusDigits = pivotCands,
                                    targetEliminations = elims,
                                    visualLinks = links,
                                    stage1Hint = "Look in Box ${pivot.boxNumber} for a 3-candidate Pivot cell ${pivot.coordLabel} with candidates {${pSorted.joinToString(", ")}}.",
                                    stage2Hint = "Pivot ${pivot.coordLabel} {${pSorted.joinToString(", ")}} sees two bi-value Pincers ${pincer1.coordLabel} {${c1.sorted().joinToString(", ")}} and ${pincer2.coordLabel} {${c2.sorted().joinToString(", ")}}, forming an XYZ-Wing with common digit $z.",
                                    stage3Hint = "At least one of ${pivot.coordLabel}, ${pincer1.coordLabel}, or ${pincer2.coordLabel} must be $z. Eliminate $z from any cell seeing all three wing cells: ${elims.sortedBy { it.cell }.joinToString(", ") { it.label }}."
                                )
                            )
                        }
                    }
                }
            }
        }
        return results
    }

    // =========================================================================
    // 12. UNIQUE RECTANGLE (TYPE 1)
    // =========================================================================
    private fun detectUniqueRectanglesType1(board: SudokuBoard): List<PatternInstance> {
        val results = mutableListOf<PatternInstance>()

        for (r1 in 0..7) {
            for (r2 in r1 + 1..8) {
                for (c1 in 0..7) {
                    for (c2 in c1 + 1..8) {
                        val corners = listOf(
                            CellPos(r1, c1),
                            CellPos(r1, c2),
                            CellPos(r2, c1),
                            CellPos(r2, c2)
                        )
                        if (corners.any { !board.isEmpty(it) }) continue
                        val boxes = corners.map { it.boxIndex }.toSet()
                        if (boxes.size != 2) continue // Unique Rectangle must span exactly 2 boxes

                        val biValueCorners = corners.filter { board.candidatesAt(it).size == 2 }
                        if (biValueCorners.size == 3) {
                            val pairSet = board.candidatesAt(biValueCorners[0])
                            if (biValueCorners.all { board.candidatesAt(it) == pairSet }) {
                                val roof = (corners - biValueCorners.toSet()).first()
                                val roofCands = board.candidatesAt(roof)
                                if (roofCands.size > 2 && roofCands.containsAll(pairSet)) {
                                    val defining = corners.toSet()
                                    val elims = pairSet.map { CandidateElimination(roof, it) }.toSet()
                                    val pairSorted = pairSet.sorted()
                                    val links = listOf(
                                        PatternLink(CellPos(r1, c1), CellPos(r1, c2), pairSorted[0], LinkType.STRONG),
                                        PatternLink(CellPos(r2, c1), CellPos(r2, c2), pairSorted[0], LinkType.STRONG),
                                        PatternLink(CellPos(r1, c1), CellPos(r2, c1), pairSorted[1], LinkType.STRONG),
                                        PatternLink(CellPos(r1, c2), CellPos(r2, c2), pairSorted[1], LinkType.STRONG)
                                    )
                                    results.add(
                                        PatternInstance(
                                            patternType = PatternType.UNIQUE_RECTANGLE_TYPE_1,
                                            definingCells = defining,
                                            pivotCell = roof,
                                            focusDigits = pairSet,
                                            targetEliminations = elims,
                                            visualLinks = links,
                                            stage1Hint = "Look for a 4-cell rectangle across Rows ${r1 + 1} & ${r2 + 1} and Columns ${c1 + 1} & ${c2 + 1} with candidates {${pairSorted.joinToString(", ")}}.",
                                            stage2Hint = "Three corners (${biValueCorners.sorted().joinToString { it.coordLabel }}) contain ONLY {${pairSorted.joinToString(", ")}}, while the 4th corner ${roof.coordLabel} contains {${roofCands.sorted().joinToString(", ")}}—forming a Unique Rectangle Type 1.",
                                            stage3Hint = "To avoid a deadly two-solution rectangle, ${roof.coordLabel} cannot be ${pairSorted[0]} or ${pairSorted[1]}. Eliminate ${elims.sortedBy { it.digit }.joinToString(", ") { it.label }}!"
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        return results
    }

    // =========================================================================
    // Unit Helpers
    // =========================================================================
    private fun rowCells(r: Int): List<CellPos> = (0..8).map { CellPos(r, it) }
    private fun colCells(c: Int): List<CellPos> = (0..8).map { CellPos(it, c) }
    private fun boxCells(b: Int): List<CellPos> {
        val startR = (b / 3) * 3
        val startC = (b % 3) * 3
        val list = mutableListOf<CellPos>()
        for (dr in 0..2) {
            for (dc in 0..2) {
                list.add(CellPos(startR + dr, startC + dc))
            }
        }
        return list
    }

    private fun allUnits(): List<Pair<String, List<CellPos>>> {
        val list = mutableListOf<Pair<String, List<CellPos>>>()
        for (r in 0..8) list.add("Row ${r + 1}" to rowCells(r))
        for (c in 0..8) list.add("Column ${c + 1}" to colCells(c))
        for (b in 0..8) list.add("Box ${b + 1}" to boxCells(b))
        return list
    }
}
