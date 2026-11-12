package com.arendon.sudokumaster

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arendon.sudokumaster.ui.screens.HomeScreen
import com.arendon.sudokumaster.ui.screens.LessonScreen
import com.arendon.sudokumaster.ui.screens.PracticeScreen
import com.arendon.sudokumaster.ui.theme.SudokuMasterTheme
import com.arendon.sudokumaster.ui.viewmodel.AppScreen
import com.arendon.sudokumaster.ui.viewmodel.SudokuTrainerViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: SudokuTrainerViewModel = viewModel()
            val themeMode by vm.themeMode.collectAsState()

            SudokuMasterTheme(themeMode = themeMode) {
                val uiState by vm.uiState.collectAsState()
                val masterySummaries by vm.masterySummaries.collectAsState()
                val progressMap by vm.progressByExerciseId.collectAsState()

                BackHandler(enabled = uiState.currentScreen != AppScreen.HOME) {
                    when (uiState.currentScreen) {
                        AppScreen.PRACTICE -> vm.selectPatternForLesson(uiState.selectedPattern, navigateToLesson = true)
                        AppScreen.LESSON -> vm.navigateHome()
                        AppScreen.HOME -> Unit
                    }
                }

                when (uiState.currentScreen) {
                    AppScreen.HOME -> {
                        HomeScreen(
                            masterySummaries = masterySummaries,
                            onOpenLesson = { pattern ->
                                vm.selectPatternForLesson(pattern, navigateToLesson = true)
                            },
                            onStartPractice = { pattern ->
                                vm.openExercise(pattern, 0)
                            },
                            themeMode = themeMode,
                            onSelectTheme = { vm.setThemeMode(it) }
                        )
                    }

                    AppScreen.LESSON -> {
                        LessonScreen(
                            patternType = uiState.selectedPattern,
                            examples = uiState.walkthroughExamples,
                            selectedExampleIndex = uiState.selectedExampleIndex,
                            walkthroughStep = uiState.walkthroughStep,
                            exercises = uiState.exercisesForSelectedPattern,
                            progressMap = progressMap,
                            onBack = { vm.navigateHome() },
                            onSelectExample = { idx -> vm.selectWalkthroughExample(idx) },
                            onSelectWalkthroughStep = { step -> vm.setWalkthroughStep(step) },
                            onOpenExercise = { idx -> vm.openExercise(uiState.selectedPattern, idx) },
                            onGenerateMoreExercises = { vm.generateMoreExercisesForSelectedPattern() },
                            themeMode = themeMode,
                            onSelectTheme = { vm.setThemeMode(it) }
                        )
                    }

                    AppScreen.PRACTICE -> {
                        PracticeScreen(
                            state = uiState,
                            onBackToLesson = {
                                vm.selectPatternForLesson(uiState.selectedPattern, navigateToLesson = true)
                            },
                            onSelectExerciseIndex = { idx ->
                                vm.openExercise(uiState.selectedPattern, idx)
                            },
                            onGenerateMoreExercises = {
                                vm.generateMoreExercisesForSelectedPattern()
                            },
                            onPrecomputeCandidates = { vm.precomputeCandidates() },
                            onToggleFilterDigit = { d -> vm.toggleCandidateFilter(d) },
                            onSetInkMode = { mode -> vm.setInkMode(mode) },
                            onToggleLeftHanded = { vm.toggleLeftHandedLayout() },
                            onTogglePalmRejection = { vm.toggleStylusPalmRejection() },
                            onSelectCell = { pos -> vm.selectCell(pos) },
                            onTogglePatternCell = { pos -> vm.togglePatternCellSelection(pos) },
                            onToggleCandidate = { pos, d -> vm.toggleCandidateAt(pos, d) },
                            onHandwritingRecognized = { pos, res -> vm.onHandwritingRecognized(pos, res) },
                            onQuickDigitTap = { d -> vm.onQuickDigitTap(d) },
                            onEraseActiveCell = {
                                uiState.activeCell?.let { vm.clearCellAt(it) }
                            },
                            onVerifyDrillStep = { vm.verifyCurrentDrillStep() },
                            onEnterKeepSolving = { vm.enterKeepSolvingMode() },
                            onRevealNextHint = { vm.revealNextHintStage() },
                            onUndo = { vm.undo() },
                            onRedo = { vm.redo() },
                            onResetExercise = { vm.resetCurrentExercise() },
                            themeMode = themeMode,
                            onSelectTheme = { vm.setThemeMode(it) }
                        )
                    }
                }
            }
        }
    }
}
