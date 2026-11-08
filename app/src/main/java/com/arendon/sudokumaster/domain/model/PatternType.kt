package com.arendon.sudokumaster.domain.model

enum class ComplexityTier(
    val tierNumber: Int,
    val title: String,
    val subtitle: String
) {
    TIER_1_SUBSETS(
        tierNumber = 1,
        title = "Tier 1: Subsets",
        subtitle = "Locked candidate sets inside a single row, column, or 3x3 box"
    ),
    TIER_2_INTERSECTIONS(
        tierNumber = 2,
        title = "Tier 2: Intersections",
        subtitle = "Interactions where a 3x3 box and a row or column overlap"
    ),
    TIER_3_FISH_SINGLE_DIGIT(
        tierNumber = 3,
        title = "Tier 3: Fish & Single-Digit Patterns",
        subtitle = "Multi-line patterns tracking a single candidate across rows and columns"
    ),
    TIER_4_WINGS_UNIQUENESS(
        tierNumber = 4,
        title = "Tier 4: Wings & Uniqueness",
        subtitle = "Bi-value/tri-value pivot chains and uniqueness constraints"
    )
}

enum class PatternType(
    val complexityOrder: Int,
    val tier: ComplexityTier,
    val displayName: String,
    val shortSummary: String,
    val expectedDefiningCellCount: Int,
    val theoryExplanation: String,
    val howToSpot: List<String>,
    val step1Prompt: String
) {
    NAKED_PAIR(
        complexityOrder = 1,
        tier = ComplexityTier.TIER_1_SUBSETS,
        displayName = "Naked Pair",
        shortSummary = "Two cells in a unit share the exact same 2 candidates, locking those digits out of the rest of the unit.",
        expectedDefiningCellCount = 2,
        theoryExplanation = """
            A Naked Pair occurs when exactly two cells within the same house (row, column, or 3x3 box) contain ONLY the same two candidate digits {X, Y}.
            
            Because one of those two cells must become X and the other must become Y, no other cell in that shared row, column, or box can contain X or Y. Therefore, X and Y can be safely eliminated from all other cells in every house shared by the two cells.
        """.trimIndent(),
        howToSpot = listOf(
            "Precompute candidates and scan for bi-value cells (cells with only 2 candidate notes).",
            "When two cells in the same row, column, or 3x3 box have identical two-digit candidates {X, Y}, you have found a Naked Pair.",
            "Check the rest of that row, column, or box for any other occurrences of X or Y to eliminate."
        ),
        step1Prompt = "Step 1: Select the 2 cells that form the Naked Pair."
    ),

    HIDDEN_PAIR(
        complexityOrder = 2,
        tier = ComplexityTier.TIER_1_SUBSETS,
        displayName = "Hidden Pair",
        shortSummary = "Two candidates appear in only two cells of a unit, allowing all other candidates inside those two cells to be removed.",
        expectedDefiningCellCount = 2,
        theoryExplanation = """
            A Hidden Pair occurs when two candidate digits {X, Y} appear in ONLY two cells within a single house (row, column, or 3x3 box), even though those two cells also contain extra candidate digits.
            
            Since X and Y have nowhere else to go in that house, those two cells are forced to hold X and Y between them. Any other candidates sitting inside those two cells can be immediately eliminated, turning the Hidden Pair into a clean Naked Pair.
        """.trimIndent(),
        howToSpot = listOf(
            "Use the Candidate Digit Filter (1-9) to count how many times each digit appears in a row, column, or box.",
            "Find two digits X and Y that each appear only twice in a house, and occupy the exact same two cells.",
            "Eliminate all OTHER candidates from inside those two cells."
        ),
        step1Prompt = "Step 1: Select the 2 cells that contain the Hidden Pair."
    ),

    NAKED_TRIPLE(
        complexityOrder = 3,
        tier = ComplexityTier.TIER_1_SUBSETS,
        displayName = "Naked Triple",
        shortSummary = "Three cells in a unit contain only candidates from a set of 3 digits {X, Y, Z}, eliminating {X, Y, Z} from the rest of the unit.",
        expectedDefiningCellCount = 3,
        theoryExplanation = """
            A Naked Triple occurs when three cells in the same row, column, or 3x3 box collectively contain only candidates drawn from a set of three digits {X, Y, Z}.
            
            Importantly, each individual cell does NOT need to contain all three digits—combinations like {X,Y}, {Y,Z}, and {X,Z} (or {X,Y,Z}, {X,Y}, {Y,Z}) still lock all three digits into those three cells. You can eliminate X, Y, and Z from all other cells in that shared house.
        """.trimIndent(),
        howToSpot = listOf(
            "Look within a single row, column, or box for three cells that only have 2 or 3 candidates each.",
            "Take the union of their candidates: if the combined set has exactly 3 digits {X, Y, Z}, they form a Naked Triple.",
            "Eliminate X, Y, and Z from all other cells in that house."
        ),
        step1Prompt = "Step 1: Select the 3 cells that form the Naked Triple."
    ),

    POINTING_PAIR(
        complexityOrder = 4,
        tier = ComplexityTier.TIER_2_INTERSECTIONS,
        displayName = "Pointing Pairs / Triples",
        shortSummary = "All occurrences of a candidate inside a 3x3 box lie in a single row or column, eliminating that candidate along the rest of the line.",
        expectedDefiningCellCount = -1, // 2 or 3 cells in the intersection
        theoryExplanation = """
            A Pointing Pair (or Pointing Triple) happens when every cell containing candidate X inside a 3x3 box is aligned along a single row or column.
            
            Because that 3x3 box MUST contain digit X somewhere, one of those aligned intersection cells is guaranteed to be X. Consequently, no cell outside the box along that same row or column can be X.
        """.trimIndent(),
        howToSpot = listOf(
            "Filter by a single digit (1-9) and inspect each 3x3 box.",
            "If all candidates for digit X inside a box lie in the same row (or the same column), they 'point' along that line.",
            "Eliminate digit X from the rest of that row (or column) outside the box."
        ),
        step1Prompt = "Step 1: Select the aligned cells inside the 3x3 box that form the Pointing Pair/Triple."
    ),

    BOX_LINE_REDUCTION(
        complexityOrder = 5,
        tier = ComplexityTier.TIER_2_INTERSECTIONS,
        displayName = "Box/Line Reduction",
        shortSummary = "All occurrences of a candidate in a row or column fall inside a single 3x3 box, eliminating that candidate from the rest of the box.",
        expectedDefiningCellCount = -1, // 2 or 3 cells in the intersection
        theoryExplanation = """
            Box/Line Reduction (also called Claiming) is the mirror image of a Pointing Pair. It occurs when all possible positions for candidate X in an entire row or column fall within a single 3x3 box.
            
            Since that row or column must place X inside that 3x3 box, the line 'claims' X for that box. You can eliminate X from all other cells in that 3x3 box that do not lie on the claiming line.
        """.trimIndent(),
        howToSpot = listOf(
            "Filter by a candidate digit X and scan each row and column.",
            "Find a row or column where every candidate X sits inside a single 3x3 box.",
            "Eliminate candidate X from all other cells inside that 3x3 box."
        ),
        step1Prompt = "Step 1: Select the cells on the line inside the box that claim the digit (Box/Line Reduction)."
    ),

    X_WING(
        complexityOrder = 6,
        tier = ComplexityTier.TIER_3_FISH_SINGLE_DIGIT,
        displayName = "X-Wing",
        shortSummary = "Two rows (or columns) each restrict a digit to the exact same two columns (or rows), forming a rectangle that clears the cross-lines.",
        expectedDefiningCellCount = 4,
        theoryExplanation = """
            An X-Wing is a single-digit pattern across 2 base rows and 2 cover columns (or 2 base columns and 2 cover rows).
            
            Suppose in Row A and Row B, candidate X appears in ONLY two cells each, and those cells lie in the exact same two columns (Col 1 and Col 2), forming four corners of a rectangle. Digit X must occupy two opposite diagonal corners of that rectangle—meaning both Col 1 and Col 2 will receive their X from Row A and Row B. Therefore, candidate X can be eliminated from all other cells in Col 1 and Col 2!
        """.trimIndent(),
        howToSpot = listOf(
            "Highlight a single digit (1-9) using the Candidate Filter.",
            "Look for rows (or columns) where that digit appears in exactly 2 cells (a conjugate pair).",
            "When two rows have their 2 cells aligned in the exact same two columns (or vice versa), those 4 corners form an X-Wing.",
            "Eliminate the digit along the two perpendicular lines outside the 4 corners."
        ),
        step1Prompt = "Step 1: Select the 4 corner cells that form the X-Wing rectangle."
    ),

    SKYSCRAPER(
        complexityOrder = 7,
        tier = ComplexityTier.TIER_3_FISH_SINGLE_DIGIT,
        displayName = "Skyscraper",
        shortSummary = "Two rows (or columns) have conjugate pairs sharing one column (the base) while their other ends (the tops) eliminate common peers.",
        expectedDefiningCellCount = 4,
        theoryExplanation = """
            A Skyscraper is a Turbot Fish variant formed by two rows (or two columns) that each contain candidate X in exactly two cells.
            
            One pair of ends lines up in the same column (forming the shared 'base' of the two towers), while the other two ends ('roof tops') sit in different columns (often inside the same band of boxes). Because both base cells cannot simultaneously be false (otherwise both roof cells would be true, or actually: at most one base cell can be true, so at least one roof cell MUST be true!), any cell that can see BOTH roof cells cannot contain candidate X.
        """.trimIndent(),
        howToSpot = listOf(
            "Filter by candidate digit X and find rows (or columns) where X appears in exactly 2 cells.",
            "Find two such lines where ONE pair of ends shares the same cross-line (the base), and the other two ends (the roof tops) are offset.",
            "Eliminate X from any cell that sees both roof-top cells simultaneously."
        ),
        step1Prompt = "Step 1: Select the 4 cells (2 base cells + 2 roof cells) forming the Skyscraper."
    ),

    TWO_STRING_KITE(
        complexityOrder = 8,
        tier = ComplexityTier.TIER_3_FISH_SINGLE_DIGIT,
        displayName = "2-String Kite",
        shortSummary = "A row conjugate pair and a column conjugate pair linked through a shared 3x3 box eliminate the digit at the intersection of their free ends.",
        expectedDefiningCellCount = 4,
        theoryExplanation = """
            A 2-String Kite links one row where candidate X appears only twice and one column where candidate X appears only twice.
            
            One end of the row pair and one end of the column pair sit inside the SAME 3x3 box (in different cells). Because those two box ends share a 3x3 box, they cannot both be X—which forces at least one of the two outer 'tail' ends (one in the row, one in the column) to be X. Therefore, the cell at the intersection seeing both outer ends cannot contain X!
        """.trimIndent(),
        howToSpot = listOf(
            "Filter by digit X and look for one row with exactly 2 candidates and one column with exactly 2 candidates.",
            "Check if one cell from the row pair and one cell from the column pair fall inside the same 3x3 box.",
            "Find the cell at the intersection of the other two outer ends and eliminate X there."
        ),
        step1Prompt = "Step 1: Select the 4 cells (the row pair and column pair) forming the 2-String Kite."
    ),

    SWORDFISH(
        complexityOrder = 9,
        tier = ComplexityTier.TIER_3_FISH_SINGLE_DIGIT,
        displayName = "Swordfish",
        shortSummary = "Three rows (or columns) restrict a digit to at most the same three columns (or rows), eliminating that digit along the three cover lines.",
        expectedDefiningCellCount = -1, // 6 to 9 cells across 3 base lines and 3 cover lines
        theoryExplanation = """
            A Swordfish generalizes the X-Wing from a 2x2 grid to a 3x3 grid of lines.
            
            When three base rows each contain candidate X only within the same set of three cover columns (each row having 2 or 3 candidates in those columns), then those three rows lock digit X into those three columns. No other row can place X into any of those three cover columns, allowing X to be eliminated from all other cells in those three columns! (Works identically with 3 base columns and 3 cover rows.)
        """.trimIndent(),
        howToSpot = listOf(
            "Filter by candidate digit X and look for rows (or columns) that have only 2 or 3 candidates for X.",
            "Find 3 rows whose candidates for X all align within the same 3 columns.",
            "Eliminate X from all other cells in those 3 cover columns outside the 3 Swordfish rows."
        ),
        step1Prompt = "Step 1: Select all the Swordfish pattern cells across the 3 base lines."
    ),

    XY_WING(
        complexityOrder = 10,
        tier = ComplexityTier.TIER_4_WINGS_UNIQUENESS,
        displayName = "XY-Wing",
        shortSummary = "A bi-value Pivot {X,Y} sees two bi-value Pincers {X,Z} and {Y,Z}, eliminating Z from every cell that sees both Pincers.",
        expectedDefiningCellCount = 3,
        theoryExplanation = """
            An XY-Wing (also called a Y-Wing) uses three bi-value cells (cells with exactly 2 candidates):
            • A Pivot cell with candidates {X, Y}
            • Pincer 1 (which sees the Pivot) with candidates {X, Z}
            • Pincer 2 (which also sees the Pivot) with candidates {Y, Z}
            
            Whichever value the Pivot takes (X or Y), one of the two Pincers is forced to become Z! Therefore, any cell on the board that can see BOTH Pincer 1 and Pincer 2 cannot contain candidate Z.
        """.trimIndent(),
        howToSpot = listOf(
            "Look for bi-value cells (cells with exactly 2 candidates).",
            "Pick a Pivot cell {X, Y} and scan its row, column, and box for two Pincer cells {X, Z} and {Y, Z} sharing a common third digit Z.",
            "Eliminate Z from any cell that is a peer of both Pincers."
        ),
        step1Prompt = "Step 1: Select the 3 cells (1 Pivot + 2 Pincers) that form the XY-Wing."
    ),

    XYZ_WING(
        complexityOrder = 11,
        tier = ComplexityTier.TIER_4_WINGS_UNIQUENESS,
        displayName = "XYZ-Wing",
        shortSummary = "A tri-value Pivot {X,Y,Z} sees two bi-value Pincers {X,Z} and {Y,Z}, eliminating Z from any cell that sees the Pivot and both Pincers.",
        expectedDefiningCellCount = 3,
        theoryExplanation = """
            An XYZ-Wing is a powerful extension of the XY-Wing where the Pivot cell contains all three candidates {X, Y, Z}, and sees two bi-value Pincers {X, Z} and {Y, Z}.
            
            In every case—whether the Pivot is X (forcing Pincer 1 to be Z), Y (forcing Pincer 2 to be Z), or Z itself—at least one of the three cells (Pivot, Pincer 1, or Pincer 2) MUST be Z! Therefore, any cell that can simultaneously see the Pivot AND both Pincers (typically inside the Pivot's 3x3 box) cannot contain Z.
        """.trimIndent(),
        howToSpot = listOf(
            "Look for a 3-candidate Pivot cell {X, Y, Z}.",
            "Find one Pincer {X, Z} in the Pivot's 3x3 box and a second Pincer {Y, Z} in the Pivot's row or column.",
            "Eliminate Z from the remaining cells in the Pivot's box that lie on the same line as the Pivot and see all three wing cells."
        ),
        step1Prompt = "Step 1: Select the 3 cells (the {X,Y,Z} Pivot and the two {X,Z}/{Y,Z} Pincers) forming the XYZ-Wing."
    ),

    UNIQUE_RECTANGLE_TYPE_1(
        complexityOrder = 12,
        tier = ComplexityTier.TIER_4_WINGS_UNIQUENESS,
        displayName = "Unique Rectangle (Type 1)",
        shortSummary = "Three corners of a 2-box rectangle have only {X,Y} while the 4th corner has {X,Y,...}; eliminate X and Y from the 4th corner to avoid a deadly pattern.",
        expectedDefiningCellCount = 4,
        theoryExplanation = """
            Every valid Sudoku has a single unique solution. A 'Deadly Pattern' occurs if four cells forming a rectangle across 2 rows, 2 columns, and 2 boxes contain ONLY the pair {X, Y}—because X and Y could be swapped in those four corners to produce two valid solutions!
            
            In a Unique Rectangle Type 1, three corners of such a 2-box rectangle already contain ONLY {X, Y}, while the fourth corner (the 'roof' cell) contains {X, Y} plus one or more extra candidates. To prevent the deadly two-solution rectangle, the fourth corner CANNOT be X or Y—so you can eliminate both X and Y from that fourth corner!
        """.trimIndent(),
        howToSpot = listOf(
            "Find two bi-value cells with identical candidates {X, Y} in the same row, column, or box.",
            "Look at two corresponding cells in a parallel row/column so all 4 corners span exactly 2 rows, 2 columns, and 2 boxes.",
            "If 3 corners have only {X, Y} and the 4th corner has {X, Y, + extras}, eliminate X and Y from that 4th corner."
        ),
        step1Prompt = "Step 1: Select the 4 corners of the Unique Rectangle."
    );

    companion object {
        val orderedByComplexity: List<PatternType>
            get() = entries.sortedBy { it.complexityOrder }

        fun byTier(tier: ComplexityTier): List<PatternType> =
            orderedByComplexity.filter { it.tier == tier }
    }
}
