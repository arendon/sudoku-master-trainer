# Sudoku Master Trainer (`com.arendon.sudokumaster`)

An Android application designed to teach intermediate and advanced Sudoku solving patterns, optimized for large-screen Android tablets (such as the **Magic Tab 4** with stylus/pencil handwriting recognition) as well as phones.

## Features

- **12 Patterns Ordered by Complexity Across 4 Tiers**:
  - **Tier 1 — Subsets**: Naked Pair, Hidden Pair, Naked Triple
  - **Tier 2 — Intersections**: Pointing Pairs / Triples, Box/Line Reduction
  - **Tier 3 — Fish & Single-Digit**: X-Wing, Skyscraper, 2-String Kite, Swordfish
  - **Tier 4 — Wings & Uniqueness**: XY-Wing, XYZ-Wing, Unique Rectangle (Type 1)
- **Interactive Visual Walkthroughs**: Each pattern includes conceptual theory, how-to-spot tips, and 2 step-by-step interactive example boards (*1. The Setup* → *2. Pattern Lock & Links* → *3. Resulting Eliminations*).
- **120+ Verified Practice Exercises (10+ per Pattern)**:
  - Bundled with 10 distinct, forward-solver-verified exercises per pattern (120 out-of-the-box) plus on-device **"+ Generate 5 More"** dynamic exercise creation.
  - **Two-Step Pattern Verification**: Step 1 verifies that you highlighted the pattern's defining cells; Step 2 verifies that you eliminated the exact target candidates, followed by an optional **"Keep Solving Board"** mode.
- **"Precompute All Candidates" Button**: One-tap calculation of all valid candidate numbers (1–9) for every empty cell based on row, column, and 3×3 block constraints, with smart peer candidate pruning when digits are placed.
- **Magic Tab 4 Stylus & Handwriting Support**:
  - Write digits `1–9` directly inside any cell or on the enlarged **Magic-Pencil Zoom Pad**, or scribble horizontally to erase.
  - Includes an **Ink Mode** selector (*Pattern Cells*, *Candidate Note*, *Final Digit*), **Stylus-Only Palm Rejection** toggle, and **Left/Right-Handed Split-Pane Swap**.
- **3-Stage Progressive Hints**:
  - *Stage 1*: Focus area & candidate digit nudge
  - *Stage 2*: Highlights the pattern's defining cells on the board and explains why they lock candidates
  - *Stage 3*: Draws visual deduction links and highlights the exact target candidate eliminations
- **Local Progress Tracking**: Persists per-exercise status (*Solved Cleanly*, *Solved with Hints*, *In Progress* mid-board state) and pattern mastery badges locally using Room.
