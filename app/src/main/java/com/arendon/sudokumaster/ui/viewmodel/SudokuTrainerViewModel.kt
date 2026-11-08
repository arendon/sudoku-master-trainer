package com.arendon.sudokumaster.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arendon.sudokumaster.data.local.ExerciseCompletionStatus
import com.arendon.sudokumaster.data.local.ExerciseProgressEntity
import com.arendon.sudokumaster.data.local.PatternMasterySummary
import com.arendon.sudokumaster.data.local.ProgressRepository
import com.arendon.sudokumaster.data.local.SudokuDatabase
import com.arendon.sudokumaster.domain.handwriting.RecognizedInkResult
import com.arendon.sudokumaster.domain.model.CellPos
import com.arendon.sudokumaster.domain.model.Exercise
import com.arendon.sudokumaster.domain.model.ExerciseDrillPhase
import com.arendon.sudokumaster.domain.model.InkInputMode
import com.arendon.sudokumaster.domain.model.PatternInstance
import com.arendon.sudokumaster.domain.model.PatternType
import com.arendon.sudokumaster.domain.model.SudokuBoard
import com.arendon.sudokumaster.domain.model.WalkthroughExample
import com.arendon.sudokumaster.domain.solver.CandidateCalculator
import com.arendon.sudokumaster.domain.solver.CuratedExerciseBank
import com.arendon.sudokumaster.domain.solver.PatternDetector
import com.arendon.sudokumaster.domain.solver.VerificationFeedback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppScreen {
    HOME,
    LESSON,
    PRACTICE
}

data class PracticeUiState(
    val currentScreen: AppScreen = AppScreen.HOME,
    val selectedPattern: PatternType = PatternType.NAKED_PAIR,
    // Lesson screen walkthrough state
    val walkthroughExamples: List<WalkthroughExample> = emptyList(),
    val selectedExampleIndex: Int = 0,
    val walkthroughStep: Int = 1, // 1: Setup, 2: Pattern & Links, 3: Eliminations
    // Exercise list & active exercise state
    val exercisesForSelectedPattern: List<Exercise> = emptyList(),
    val currentExerciseIndex: Int = 0,
    val board: SudokuBoard? = null,
    val activePatternInstance: PatternInstance? = null,
    val drillPhase: ExerciseDrillPhase = ExerciseDrillPhase.STEP_1_SELECT_PATTERN_CELLS,
    val inkMode: InkInputMode = InkInputMode.SELECT_PATTERN,
    val activeCell: CellPos? = null,
    val filterDigit: Int? = null,
    val revealedHintStage: Int = 0, // 0..3
    val maxHintsUsedInExercise: Int = 0,
    val feedbackMessage: String? = null,
    val isFeedbackError: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    // Tablet & Stylus ergonomic preferences
    val isLeftHandedLayout: Boolean = false,
    val stylusOnlyPalmRejection: Boolean = false,
    val lastRecognizedInkText: String? = null,
    val lastInkAlternatives: List<Int> = emptyList()
)

class SudokuTrainerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ProgressRepository(
        SudokuDatabase.getInstance(application).progressDao()
    )

    private val _uiState = MutableStateFlow(PracticeUiState())
    val uiState: StateFlow<PracticeUiState> = _uiState.asStateFlow()

    private val undoStack = ArrayDeque<SudokuBoard>()
    private val redoStack = ArrayDeque<SudokuBoard>()

    val progressByExerciseId: StateFlow<Map<String, ExerciseProgressEntity>> =
        repository.allProgressFlow.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyMap()
        )

    val exerciseCountsByPattern: StateFlow<Map<PatternType, Int>> =
        repository.exerciseCountsFlow.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            PatternType.entries.associateWith { 10 }
        )

    val masterySummaries: StateFlow<Map<PatternType, PatternMasterySummary>> =
        combine(progressByExerciseId, exerciseCountsByPattern) { progressMap, countsMap ->
            PatternType.entries.associateWith { type ->
                val total = countsMap[type] ?: 10
                val exercises = repository.getExercisesForPattern(type, total)
                var solved = 0
                var clean = 0
                var inProgress = 0
                for (ex in exercises) {
                    when (progressMap[ex.id]?.completionStatus) {
                        ExerciseCompletionStatus.SOLVED_CLEAN -> {
                            solved++
                            clean++
                        }
                        ExerciseCompletionStatus.SOLVED_WITH_HINTS -> {
                            solved++
                        }
                        ExerciseCompletionStatus.IN_PROGRESS -> {
                            inProgress++
                        }
                        else -> Unit
                    }
                }
                PatternMasterySummary(
                    patternType = type,
                    totalExercises = total,
                    solvedCount = solved,
                    cleanSolvedCount = clean,
                    inProgressCount = inProgress
                )
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyMap()
        )

    init {
        selectPatternForLesson(PatternType.NAKED_PAIR, navigateToLesson = false)
    }

    fun navigateHome() {
        _uiState.update { it.copy(currentScreen = AppScreen.HOME) }
    }

    fun selectPatternForLesson(patternType: PatternType, navigateToLesson: Boolean = true) {
        val examples = CuratedExerciseBank.getWalkthroughExamples(patternType)
        val totalCount = exerciseCountsByPattern.value[patternType] ?: 10
        val exercises = repository.getExercisesForPattern(patternType, totalCount)

        _uiState.update {
            it.copy(
                currentScreen = if (navigateToLesson) AppScreen.LESSON else it.currentScreen,
                selectedPattern = patternType,
                walkthroughExamples = examples,
                selectedExampleIndex = 0,
                walkthroughStep = 1,
                exercisesForSelectedPattern = exercises
            )
        }
    }

    fun selectWalkthroughExample(index: Int) {
        _uiState.update {
            val safeIdx = index.coerceIn(0, (it.walkthroughExamples.size - 1).coerceAtLeast(0))
            it.copy(selectedExampleIndex = safeIdx, walkthroughStep = 1)
        }
    }

    fun setWalkthroughStep(step: Int) {
        _uiState.update { it.copy(walkthroughStep = step.coerceIn(1, 3)) }
    }

    fun generateMoreExercisesForSelectedPattern() {
        val pattern = _uiState.value.selectedPattern
        viewModelScope.launch {
            val newTotal = repository.generateMoreExercises(pattern, additionalCount = 5)
            val updatedList = repository.getExercisesForPattern(pattern, newTotal)
            _uiState.update {
                it.copy(
                    exercisesForSelectedPattern = updatedList,
                    feedbackMessage = "Generated 5 new ${pattern.displayName} exercises! ($newTotal total)",
                    isFeedbackError = false
                )
            }
        }
    }

    fun openExercise(patternType: PatternType, exerciseIndex: Int) {
        val totalCount = exerciseCountsByPattern.value[patternType] ?: 10
        val exercises = repository.getExercisesForPattern(patternType, totalCount)
        val safeIdx = exerciseIndex.coerceIn(0, exercises.lastIndex)
        val exercise = exercises[safeIdx]

        viewModelScope.launch {
            val saved = repository.loadSavedExerciseState(exercise)
            val initialBoard = saved?.first ?: exercise.initialBoard
            val phase = saved?.second ?: ExerciseDrillPhase.STEP_1_SELECT_PATTERN_CELLS
            val hintsUsed = saved?.third ?: 0

            val defaultInkMode = when (phase) {
                ExerciseDrillPhase.STEP_1_SELECT_PATTERN_CELLS -> InkInputMode.SELECT_PATTERN
                ExerciseDrillPhase.STEP_2_ELIMINATE_CANDIDATES -> InkInputMode.CANDIDATE_NOTE
                else -> InkInputMode.FINAL_DIGIT
            }

            undoStack.clear()
            redoStack.clear()

            _uiState.update {
                it.copy(
                    currentScreen = AppScreen.PRACTICE,
                    selectedPattern = patternType,
                    exercisesForSelectedPattern = exercises,
                    currentExerciseIndex = safeIdx,
                    board = initialBoard,
                    activePatternInstance = exercise.patternInstance,
                    drillPhase = phase,
                    inkMode = defaultInkMode,
                    activeCell = exercise.patternInstance.definingCells.minOrNull(),
                    filterDigit = null,
                    revealedHintStage = 0,
                    maxHintsUsedInExercise = hintsUsed,
                    feedbackMessage = buildPromptForPhase(phase, exercise.patternInstance, initialBoard.candidatesPrecomputed),
                    isFeedbackError = false,
                    canUndo = false,
                    canRedo = false,
                    lastRecognizedInkText = null,
                    lastInkAlternatives = emptyList()
                )
            }
        }
    }

    fun resetCurrentExercise() {
        val state = _uiState.value
        val exercise = state.exercisesForSelectedPattern.getOrNull(state.currentExerciseIndex) ?: return
        viewModelScope.launch {
            repository.resetExercise(exercise.id)
            openExercise(state.selectedPattern, state.currentExerciseIndex)
        }
    }

    /**
     * Invoked when the user taps the "Precompute Candidates" button.
     */
    fun precomputeCandidates() {
        val currentBoard = _uiState.value.board ?: return
        pushUndoState(currentBoard)
        val updated = CandidateCalculator.precomputeCandidates(currentBoard)
        val state = _uiState.value
        val instance = state.activePatternInstance
        val msg = if (state.drillPhase == ExerciseDrillPhase.STEP_1_SELECT_PATTERN_CELLS && instance != null) {
            "Candidates precomputed! ${instance.patternType.step1Prompt}"
        } else if (state.drillPhase == ExerciseDrillPhase.STEP_2_ELIMINATE_CANDIDATES && instance != null) {
            "Candidates precomputed! Step 2: Eliminate the target candidate(s) dictated by the ${instance.patternType.displayName}."
        } else {
            "All valid row, column, and 3x3 block candidate suggestions precomputed."
        }
        updateBoardAndPersist(updated, feedbackMessage = msg, isError = false)
    }

    fun toggleCandidateFilter(digit: Int) {
        _uiState.update {
            val next = if (it.filterDigit == digit) null else digit
            it.copy(filterDigit = next)
        }
    }

    fun setInkMode(mode: InkInputMode) {
        _uiState.update { it.copy(inkMode = mode) }
    }

    fun toggleLeftHandedLayout() {
        _uiState.update { it.copy(isLeftHandedLayout = !it.isLeftHandedLayout) }
    }

    fun toggleStylusPalmRejection() {
        _uiState.update { it.copy(stylusOnlyPalmRejection = !it.stylusOnlyPalmRejection) }
    }

    fun selectCell(pos: CellPos) {
        _uiState.update { it.copy(activeCell = pos) }
    }

    fun togglePatternCellSelection(pos: CellPos) {
        val state = _uiState.value
        val currentBoard = state.board ?: return
        pushUndoState(currentBoard)
        val updated = currentBoard.togglePatternCellSelection(pos)
        updateBoardAndPersist(
            updated,
            feedbackMessage = "Selected ${updated.selectedPatternCells.size} pattern cell(s): ${
                updated.selectedPatternCells.sorted().joinToString { it.coordLabel }
            }",
            isError = false
        )
    }

    fun toggleCandidateAt(pos: CellPos, digit: Int) {
        val state = _uiState.value
        var currentBoard = state.board ?: return
        if (!currentBoard.candidatesPrecomputed) {
            currentBoard = CandidateCalculator.precomputeCandidates(currentBoard)
        }
        pushUndoState(currentBoard)
        val updated = currentBoard.toggleCandidate(pos, digit)

        // Check if in Step 2 and all target eliminations are now complete
        val activeInstance = state.activePatternInstance
        if (state.drillPhase == ExerciseDrillPhase.STEP_2_ELIMINATE_CANDIDATES && activeInstance != null) {
            val exercise = state.exercisesForSelectedPattern.getOrNull(state.currentExerciseIndex)
            if (exercise != null) {
                val basePrecomputed = CandidateCalculator.precomputeCandidates(exercise.initialBoard)
                val check = PatternDetector.verifyStep2Eliminations(basePrecomputed, updated, activeInstance)
                if (check is VerificationFeedback.Success) {
                    completePatternDrill(updated, activeInstance)
                    return
                }
            }
        }

        updateBoardAndPersist(updated)
    }

    fun placeFinalDigitAt(pos: CellPos, digit: Int) {
        val state = _uiState.value
        val currentBoard = state.board ?: return
        if (currentBoard.isGiven(pos)) return
        pushUndoState(currentBoard)
        val updated = CandidateCalculator.placeDigitAndPrunePeers(currentBoard, pos, digit)
        val isCorrect = updated.solutionDigits[pos.row][pos.col] == digit

        val nextPhase = if (updated.isSolved()) {
            ExerciseDrillPhase.FULL_BOARD_SOLVED
        } else {
            state.drillPhase
        }

        val feedback = when {
            updated.isSolved() -> "Congratulations! You solved the entire Sudoku puzzle!"
            !isCorrect -> "Digit $digit at ${pos.coordLabel} conflicts with the unique solution. Use Undo or clear the cell."
            else -> "Placed $digit at ${pos.coordLabel} and pruned peer candidates."
        }

        updateBoardAndPersist(
            newBoard = updated,
            newPhase = nextPhase,
            feedbackMessage = feedback,
            isError = !isCorrect
        )
    }

    fun clearCellAt(pos: CellPos) {
        val currentBoard = _uiState.value.board ?: return
        if (currentBoard.isGiven(pos)) return
        pushUndoState(currentBoard)
        val updated = currentBoard.clearCell(pos)
        updateBoardAndPersist(updated, feedbackMessage = "Cleared ${pos.coordLabel}", isError = false)
    }

    /**
     * Handles stylus handwriting recognition results from either direct in-cell writing
     * or the enlarged Magic-Pencil Zoom Pad.
     */
    fun onHandwritingRecognized(targetCell: CellPos?, result: RecognizedInkResult) {
        val state = _uiState.value
        val cell = targetCell ?: state.activeCell
        if (cell == null) {
            _uiState.update {
                it.copy(
                    feedbackMessage = "Tap or write inside a cell on the board first.",
                    isFeedbackError = true
                )
            }
            return
        }

        when (result) {
            is RecognizedInkResult.Erase -> {
                _uiState.update {
                    it.copy(
                        activeCell = cell,
                        lastRecognizedInkText = "Scribble Erase at ${cell.coordLabel}",
                        lastInkAlternatives = emptyList()
                    )
                }
                if (state.inkMode == InkInputMode.SELECT_PATTERN && cell in (state.board?.selectedPatternCells ?: emptySet())) {
                    togglePatternCellSelection(cell)
                } else {
                    clearCellAt(cell)
                }
            }

            is RecognizedInkResult.Digit -> {
                val d = result.digit
                _uiState.update {
                    it.copy(
                        activeCell = cell,
                        lastRecognizedInkText = "Recognized '$d' at ${cell.coordLabel}",
                        lastInkAlternatives = result.alternatives
                    )
                }
                applyDigitByCurrentInkMode(cell, d)
            }

            RecognizedInkResult.Unrecognized -> {
                _uiState.update {
                    it.copy(
                        lastRecognizedInkText = "Could not recognize stroke—try writing larger or use Quick Tap",
                        lastInkAlternatives = emptyList()
                    )
                }
            }
        }
    }

    fun onQuickDigitTap(digit: Int) {
        val cell = _uiState.value.activeCell
        if (cell == null) {
            _uiState.update {
                it.copy(
                    feedbackMessage = "Select a cell on the board first.",
                    isFeedbackError = true
                )
            }
            return
        }
        _uiState.update {
            it.copy(
                lastRecognizedInkText = "Entered '$digit' at ${cell.coordLabel}",
                lastInkAlternatives = emptyList()
            )
        }
        applyDigitByCurrentInkMode(cell, digit)
    }

    private fun applyDigitByCurrentInkMode(cell: CellPos, digit: Int) {
        when (_uiState.value.inkMode) {
            InkInputMode.SELECT_PATTERN -> togglePatternCellSelection(cell)
            InkInputMode.CANDIDATE_NOTE -> toggleCandidateAt(cell, digit)
            InkInputMode.FINAL_DIGIT -> placeFinalDigitAt(cell, digit)
        }
    }

    /**
     * Verifies the current step of the Two-Step Pattern Drill.
     */
    fun verifyCurrentDrillStep() {
        val state = _uiState.value
        val board = state.board ?: return
        val expected = state.activePatternInstance ?: return
        val exercise = state.exercisesForSelectedPattern.getOrNull(state.currentExerciseIndex) ?: return

        when (state.drillPhase) {
            ExerciseDrillPhase.STEP_1_SELECT_PATTERN_CELLS -> {
                val (feedback, matchedInstance) = PatternDetector.verifyStep1Selection(
                    board = board,
                    selectedCells = board.selectedPatternCells,
                    expected = expected
                )
                when (feedback) {
                    is VerificationFeedback.Success -> {
                        val confirmedInstance = matchedInstance ?: expected
                        val boardWithCands = if (board.candidatesPrecomputed) {
                            board
                        } else {
                            CandidateCalculator.precomputeCandidates(board)
                        }
                        _uiState.update {
                            it.copy(
                                activePatternInstance = confirmedInstance,
                                inkMode = InkInputMode.CANDIDATE_NOTE
                            )
                        }
                        updateBoardAndPersist(
                            newBoard = boardWithCands,
                            newPhase = ExerciseDrillPhase.STEP_2_ELIMINATE_CANDIDATES,
                            feedbackMessage = "Step 1 Verified! Great job spotting the ${confirmedInstance.patternType.displayName} cells (${
                                confirmedInstance.definingCells.sorted().joinToString { it.coordLabel }
                            }). Now Step 2: Eliminate the target candidate(s) and tap Verify!",
                            isError = false
                        )
                    }
                    is VerificationFeedback.Incorrect -> {
                        _uiState.update {
                            it.copy(
                                feedbackMessage = feedback.reason,
                                isFeedbackError = true
                            )
                        }
                    }
                }
            }

            ExerciseDrillPhase.STEP_2_ELIMINATE_CANDIDATES -> {
                val basePrecomputed = CandidateCalculator.precomputeCandidates(exercise.initialBoard)
                when (val feedback = PatternDetector.verifyStep2Eliminations(basePrecomputed, board, expected)) {
                    is VerificationFeedback.Success -> {
                        completePatternDrill(board, expected)
                    }
                    is VerificationFeedback.Incorrect -> {
                        _uiState.update {
                            it.copy(
                                feedbackMessage = feedback.reason,
                                isFeedbackError = true
                            )
                        }
                    }
                }
            }

            else -> Unit
        }
    }

    private fun completePatternDrill(board: SudokuBoard, instance: PatternInstance) {
        val cleanSolve = _uiState.value.maxHintsUsedInExercise == 0
        val badgeText = if (cleanSolve) "Clean Solve (0 Hints)!" else "Solved with Hints!"
        updateBoardAndPersist(
            newBoard = board,
            newPhase = ExerciseDrillPhase.COMPLETED_DRILL,
            feedbackMessage = "Pattern Mastered — $badgeText ${instance.stage3Hint} You can move to the Next Exercise or tap 'Keep Solving' to finish the full board.",
            isError = false
        )
    }

    fun enterKeepSolvingMode() {
        val board = _uiState.value.board ?: return
        _uiState.update {
            it.copy(inkMode = InkInputMode.FINAL_DIGIT)
        }
        updateBoardAndPersist(
            newBoard = board,
            newPhase = ExerciseDrillPhase.KEEP_SOLVING_FULL_BOARD,
            feedbackMessage = "Keep Solving Mode: Place final digits (Ink Mode switched to 'Final Digit'). Peer candidates will auto-prune!",
            isError = false
        )
    }

    /**
     * Reveals the next stage of the 3-Stage Progressive Hint system (Stage 1 -> Stage 2 -> Stage 3).
     */
    fun revealNextHintStage() {
        val state = _uiState.value
        val nextStage = (state.revealedHintStage + 1).coerceAtMost(3)
        val newMaxHints = maxOf(state.maxHintsUsedInExercise, nextStage)

        var updatedBoard = state.board
        if (updatedBoard != null && !updatedBoard.candidatesPrecomputed) {
            updatedBoard = CandidateCalculator.precomputeCandidates(updatedBoard)
        }

        _uiState.update {
            it.copy(
                board = updatedBoard,
                revealedHintStage = nextStage,
                maxHintsUsedInExercise = newMaxHints
            )
        }

        val exercise = state.exercisesForSelectedPattern.getOrNull(state.currentExerciseIndex)
        if (exercise != null && updatedBoard != null) {
            viewModelScope.launch {
                repository.saveExerciseState(
                    exercise = exercise,
                    board = updatedBoard,
                    phase = state.drillPhase,
                    hintsUsed = newMaxHints
                )
            }
        }
    }

    fun undo() {
        val current = _uiState.value.board ?: return
        if (undoStack.isEmpty()) return
        redoStack.addLast(current)
        val previous = undoStack.removeLast()
        updateBoardAndPersist(previous, clearStacks = false)
    }

    fun redo() {
        val current = _uiState.value.board ?: return
        if (redoStack.isEmpty()) return
        undoStack.addLast(current)
        val next = redoStack.removeLast()
        updateBoardAndPersist(next, clearStacks = false)
    }

    private fun pushUndoState(board: SudokuBoard) {
        undoStack.addLast(board)
        if (undoStack.size > 50) undoStack.removeFirst()
        redoStack.clear()
    }

    private fun updateBoardAndPersist(
        newBoard: SudokuBoard,
        newPhase: ExerciseDrillPhase = _uiState.value.drillPhase,
        feedbackMessage: String? = _uiState.value.feedbackMessage,
        isError: Boolean = _uiState.value.isFeedbackError,
        clearStacks: Boolean = false
    ) {
        if (clearStacks) {
            undoStack.clear()
            redoStack.clear()
        }
        val state = _uiState.value
        _uiState.update {
            it.copy(
                board = newBoard,
                drillPhase = newPhase,
                feedbackMessage = feedbackMessage,
                isFeedbackError = isError,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }

        val exercise = state.exercisesForSelectedPattern.getOrNull(state.currentExerciseIndex) ?: return
        val hintsUsed = _uiState.value.maxHintsUsedInExercise
        viewModelScope.launch {
            repository.saveExerciseState(
                exercise = exercise,
                board = newBoard,
                phase = newPhase,
                hintsUsed = hintsUsed
            )
        }
    }

    private fun buildPromptForPhase(
        phase: ExerciseDrillPhase,
        instance: PatternInstance,
        candidatesPrecomputed: Boolean
    ): String {
        val precomputeNudge = if (!candidatesPrecomputed) {
            " (Tip: Tap 'Precompute Candidates' first to populate row/col/block suggestions!)"
        } else ""

        return when (phase) {
            ExerciseDrillPhase.STEP_1_SELECT_PATTERN_CELLS ->
                "${instance.patternType.step1Prompt}$precomputeNudge"
            ExerciseDrillPhase.STEP_2_ELIMINATE_CANDIDATES ->
                "Step 2: Eliminate the target candidate(s) dictated by the ${instance.patternType.displayName} (${instance.targetEliminations.size} elimination(s) expected)."
            ExerciseDrillPhase.COMPLETED_DRILL ->
                "Pattern Drill Completed! Tap 'Next Exercise' or 'Keep Solving' to finish the board."
            ExerciseDrillPhase.KEEP_SOLVING_FULL_BOARD ->
                "Keep Solving Mode: Fill in the remaining cells to complete the Sudoku!"
            ExerciseDrillPhase.FULL_BOARD_SOLVED ->
                "Full Sudoku Board Solved!"
        }
    }
}
