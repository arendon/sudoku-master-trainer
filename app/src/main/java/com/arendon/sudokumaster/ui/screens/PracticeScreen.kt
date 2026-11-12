package com.arendon.sudokumaster.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arendon.sudokumaster.domain.handwriting.RecognizedInkResult
import com.arendon.sudokumaster.domain.model.CellPos
import com.arendon.sudokumaster.domain.model.ExerciseDrillPhase
import com.arendon.sudokumaster.domain.model.InkInputMode
import com.arendon.sudokumaster.ui.components.StylusZoomPad
import com.arendon.sudokumaster.ui.components.SudokuBoardCanvas
import com.arendon.sudokumaster.ui.components.ThemeSelectionButton
import com.arendon.sudokumaster.ui.theme.ThemeMode
import com.arendon.sudokumaster.ui.viewmodel.PracticeUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen(
    state: PracticeUiState,
    onBackToLesson: () -> Unit,
    onSelectExerciseIndex: (Int) -> Unit,
    onGenerateMoreExercises: () -> Unit,
    onPrecomputeCandidates: () -> Unit,
    onToggleFilterDigit: (Int) -> Unit,
    onSetInkMode: (InkInputMode) -> Unit,
    onToggleLeftHanded: () -> Unit,
    onTogglePalmRejection: () -> Unit,
    onSelectCell: (CellPos) -> Unit,
    onTogglePatternCell: (CellPos) -> Unit,
    onToggleCandidate: (CellPos, Int) -> Unit,
    onHandwritingRecognized: (CellPos?, RecognizedInkResult) -> Unit,
    onQuickDigitTap: (Int) -> Unit,
    onEraseActiveCell: () -> Unit,
    onVerifyDrillStep: () -> Unit,
    onEnterKeepSolving: () -> Unit,
    onRevealNextHint: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onResetExercise: () -> Unit,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onSelectTheme: (ThemeMode) -> Unit = {}
) {
    val board = state.board ?: return
    val instance = state.activePatternInstance ?: return
    val currentExercise = state.exercisesForSelectedPattern.getOrNull(state.currentExerciseIndex)

    // Determine visual overlays from Hint Stage or Completed Drill state
    val showStage2PatternHighlight = state.revealedHintStage >= 2 ||
        state.drillPhase == ExerciseDrillPhase.STEP_2_ELIMINATE_CANDIDATES ||
        state.drillPhase == ExerciseDrillPhase.COMPLETED_DRILL
    val showStage3EliminationsAndLinks = state.revealedHintStage >= 3 ||
        state.drillPhase == ExerciseDrillPhase.COMPLETED_DRILL

    val highlightedPatternCells = if (showStage2PatternHighlight) instance.definingCells else emptySet()
    val highlightedPivot = if (showStage2PatternHighlight) instance.pivotCell else null
    val highlightedElims = if (showStage3EliminationsAndLinks) instance.targetEliminations else emptySet()
    val visualLinks = if (showStage3EliminationsAndLinks) instance.visualLinks else emptyList()

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBackToLesson) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Lesson",
                            tint = Color.White
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = currentExercise?.title ?: state.selectedPattern.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Exercise ${state.currentExerciseIndex + 1} of ${state.exercisesForSelectedPattern.size} • ${state.selectedPattern.tier.title}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFDBEAFE)
                        )
                    }
                },
                actions = {
                    FilterChip(
                        selected = state.stylusOnlyPalmRejection,
                        onClick = onTogglePalmRejection,
                        label = {
                            Text(
                                text = if (state.stylusOnlyPalmRejection) "Stylus Only: ON" else "Stylus + Touch",
                                color = if (state.stylusOnlyPalmRejection) Color(0xFF065F46) else Color.White,
                                fontSize = 11.sp
                            )
                        },
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    IconButton(onClick = onToggleLeftHanded) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = "Swap Left/Right Tablet Panes",
                            tint = Color.White
                        )
                    }
                    ThemeSelectionButton(
                        currentTheme = themeMode,
                        onSelectTheme = onSelectTheme,
                        iconTint = Color.White
                    )
                    IconButton(onClick = onUndo, enabled = state.canUndo) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Undo",
                            tint = if (state.canUndo) Color.White else Color(0xFF64748B)
                        )
                    }
                    IconButton(onClick = onRedo, enabled = state.canRedo) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Redo,
                            contentDescription = "Redo",
                            tint = if (state.canRedo) Color.White else Color(0xFF64748B)
                        )
                    }
                    IconButton(onClick = onResetExercise) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset Exercise",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E3A8A),
                    titleContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isLandscapeTablet = maxWidth >= 820.dp

            val boardSection: @Composable (Modifier) -> Unit = { mod ->
                Column(
                    modifier = mod,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Precompute Candidates Button + Candidate Digit Filter Bar (1-9)
                    PrecomputeAndFilterBar(
                        candidatesPrecomputed = board.candidatesPrecomputed,
                        filterDigit = state.filterDigit,
                        isLandscape = isLandscapeTablet,
                        onPrecomputeCandidates = onPrecomputeCandidates,
                        onToggleFilterDigit = onToggleFilterDigit
                    )

                    if (isLandscapeTablet) {
                        BoxWithConstraints(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(bottom = 32.dp),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            val availableHeight = (maxHeight - 16.dp).coerceAtLeast(0.dp)
                            val boardSize = minOf(maxWidth, availableHeight)
                            SudokuBoardCanvas(
                                board = board,
                                activeCell = state.activeCell,
                                filterDigit = state.filterDigit,
                                inkMode = state.inkMode,
                                stylusOnlyPalmRejection = state.stylusOnlyPalmRejection,
                                highlightedPatternCells = highlightedPatternCells,
                                pivotCell = highlightedPivot,
                                highlightedEliminations = highlightedElims,
                                visualLinks = visualLinks,
                                readOnly = false,
                                onSelectCell = onSelectCell,
                                onTogglePatternCell = onTogglePatternCell,
                                onToggleCandidate = onToggleCandidate,
                                onRecognizedInCellInk = { cell, res -> onHandwritingRecognized(cell, res) },
                                modifier = Modifier.size(boardSize)
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            SudokuBoardCanvas(
                                board = board,
                                activeCell = state.activeCell,
                                filterDigit = state.filterDigit,
                                inkMode = state.inkMode,
                                stylusOnlyPalmRejection = state.stylusOnlyPalmRejection,
                                highlightedPatternCells = highlightedPatternCells,
                                pivotCell = highlightedPivot,
                                highlightedEliminations = highlightedElims,
                                visualLinks = visualLinks,
                                readOnly = false,
                                onSelectCell = onSelectCell,
                                onTogglePatternCell = onTogglePatternCell,
                                onToggleCandidate = onToggleCandidate,
                                onRecognizedInCellInk = { cell, res -> onHandwritingRecognized(cell, res) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            val controlsSection: @Composable (Modifier) -> Unit = { mod ->
                Column(
                    modifier = mod,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Exercise Navigation Bar (< Prev | #1..#10+ | Next > | +5 More)
                    ExerciseNavigationRow(
                        currentIndex = state.currentExerciseIndex,
                        totalExercises = state.exercisesForSelectedPattern.size,
                        onSelectExercise = onSelectExerciseIndex,
                        onGenerateMore = onGenerateMoreExercises
                    )

                    // Two-Step Pattern Verification Card
                    TwoStepVerificationCard(
                        state = state,
                        onVerifyStep = onVerifyDrillStep,
                        onKeepSolving = onEnterKeepSolving,
                        onNextExercise = {
                            val nextIdx = (state.currentExerciseIndex + 1) % state.exercisesForSelectedPattern.size
                            onSelectExerciseIndex(nextIdx)
                        }
                    )

                    // Ink Mode Selector (Pattern Cells | Candidate Note | Final Digit)
                    InkModeSelectorCard(
                        currentMode = state.inkMode,
                        onSelectMode = onSetInkMode
                    )

                    // Enlarged Magic-Pencil Handwriting Zoom Pad
                    val activeCellCands = state.activeCell?.let { board.candidatesAt(it) } ?: emptySet()
                    StylusZoomPad(
                        activeCell = state.activeCell,
                        activeCellCandidates = activeCellCands,
                        inkMode = state.inkMode,
                        stylusOnlyPalmRejection = state.stylusOnlyPalmRejection,
                        lastRecognizedStatus = state.lastRecognizedInkText,
                        lastAlternatives = state.lastInkAlternatives,
                        onInkRecognized = { res -> onHandwritingRecognized(state.activeCell, res) },
                        onDirectDigitTap = onQuickDigitTap,
                        onEraseTap = onEraseActiveCell
                    )

                    // 3-Stage Progressive Hint Panel
                    ProgressiveHintCard(
                        revealedStage = state.revealedHintStage,
                        stage1Text = instance.stage1Hint,
                        stage2Text = instance.stage2Hint,
                        stage3Text = instance.stage3Hint,
                        onRevealNextStage = onRevealNextHint
                    )
                }
            }

            if (isLandscapeTablet) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    if (!state.isLeftHandedLayout) {
                        boardSection(Modifier.weight(1.05f).fillMaxHeight())
                        controlsSection(Modifier.weight(0.95f).fillMaxHeight().verticalScroll(rememberScrollState()))
                    } else {
                        controlsSection(Modifier.weight(0.95f).fillMaxHeight().verticalScroll(rememberScrollState()))
                        boardSection(Modifier.weight(1.05f).fillMaxHeight())
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    boardSection(Modifier.fillMaxWidth())
                    controlsSection(Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun PrecomputeAndFilterBar(
    candidatesPrecomputed: Boolean,
    filterDigit: Int?,
    isLandscape: Boolean,
    onPrecomputeCandidates: () -> Unit,
    onToggleFilterDigit: (Int) -> Unit
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val unselectedBg = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)
    val unselectedBorder = if (isDark) Color(0xFF475569) else Color(0xFFCBD5E1)
    val unselectedText = if (isDark) Color(0xFFF1F5F9) else Color(0xFF1E293B)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        if (isLandscape) {
            // Sleek single-row layout in landscape to preserve vertical height for the board
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onPrecomputeCandidates,
                    modifier = Modifier.height(34.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (candidatesPrecomputed) Color(0xFF0D9488) else Color(0xFF2563EB)
                    )
                ) {
                    Text(
                        text = if (candidatesPrecomputed) "↻ Candidates" else "⚡ Candidates",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (d in 1..9) {
                        val isSelected = filterDigit == d
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) Color(0xFF0284C7) else unselectedBg)
                                .border(
                                    width = 1.5.dp,
                                    color = if (isSelected) Color(0xFF0369A1) else unselectedBorder,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable { onToggleFilterDigit(d) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = d.toString(),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                                color = if (isSelected) Color.White else unselectedText
                            )
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onPrecomputeCandidates,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (candidatesPrecomputed) Color(0xFF0D9488) else Color(0xFF2563EB)
                        )
                    ) {
                        Text(
                            text = if (candidatesPrecomputed) "↻ Re-Precompute Candidates" else "⚡ Precompute All Candidates",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = if (filterDigit != null) "Filtering Digit $filterDigit" else "Highlight Candidate (1-9):",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (d in 1..9) {
                        val isSelected = filterDigit == d
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0xFF0284C7) else unselectedBg)
                                .border(
                                    width = 1.5.dp,
                                    color = if (isSelected) Color(0xFF0369A1) else unselectedBorder,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { onToggleFilterDigit(d) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = d.toString(),
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isSelected) Color.White else unselectedText
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseNavigationRow(
    currentIndex: Int,
    totalExercises: Int,
    onSelectExercise: (Int) -> Unit,
    onGenerateMore: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = { if (currentIndex > 0) onSelectExercise(currentIndex - 1) },
            enabled = currentIndex > 0
        ) {
            Text("< Prev")
        }

        Text(
            text = "Exercise #${currentIndex + 1} / $totalExercises",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(
                onClick = { if (currentIndex < totalExercises - 1) onSelectExercise(currentIndex + 1) },
                enabled = currentIndex < totalExercises - 1
            ) {
                Text("Next >")
            }
            OutlinedButton(onClick = onGenerateMore) {
                Text("+5 More")
            }
        }
    }
}

@Composable
private fun TwoStepVerificationCard(
    state: PracticeUiState,
    onVerifyStep: () -> Unit,
    onKeepSolving: () -> Unit,
    onNextExercise: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val bgColor = when {
        state.isFeedbackError -> if (isDark) Color(0xFF450A0A) else Color(0xFFFEF2F2)
        state.drillPhase == ExerciseDrillPhase.COMPLETED_DRILL ||
            state.drillPhase == ExerciseDrillPhase.FULL_BOARD_SOLVED ->
            if (isDark) Color(0xFF064E3B) else Color(0xFFECFDF5)
        else -> if (isDark) Color(0xFF172554) else Color(0xFFEFF6FF)
    }
    val borderColor = when {
        state.isFeedbackError -> Color(0xFFF87171)
        state.drillPhase == ExerciseDrillPhase.COMPLETED_DRILL ||
            state.drillPhase == ExerciseDrillPhase.FULL_BOARD_SOLVED -> Color(0xFF10B981)
        else -> Color(0xFF60A5FA)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, borderColor, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val phaseLabel = when (state.drillPhase) {
                ExerciseDrillPhase.STEP_1_SELECT_PATTERN_CELLS -> "Two-Step Drill • Step 1 of 2: Highlight Pattern Cells"
                ExerciseDrillPhase.STEP_2_ELIMINATE_CANDIDATES -> "Two-Step Drill • Step 2 of 2: Eliminate Target Candidates"
                ExerciseDrillPhase.COMPLETED_DRILL -> "Pattern Drill Completed!"
                ExerciseDrillPhase.KEEP_SOLVING_FULL_BOARD -> "Bonus Mode: Keep Solving Full Sudoku"
                ExerciseDrillPhase.FULL_BOARD_SOLVED -> "Full Sudoku Solved!"
            }

            Text(
                text = phaseLabel,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (state.feedbackMessage != null) {
                Text(
                    text = state.feedbackMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.isFeedbackError) (if (isDark) Color(0xFFFCA5A5) else Color(0xFFB91C1C)) else MaterialTheme.colorScheme.onSurface
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (state.drillPhase) {
                    ExerciseDrillPhase.STEP_1_SELECT_PATTERN_CELLS -> {
                        Button(
                            onClick = onVerifyStep,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                        ) {
                            Text("Verify Step 1: Pattern Cells", fontWeight = FontWeight.Bold)
                        }
                    }

                    ExerciseDrillPhase.STEP_2_ELIMINATE_CANDIDATES -> {
                        Button(
                            onClick = onVerifyStep,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                        ) {
                            Text("Verify Step 2: Candidate Eliminations", fontWeight = FontWeight.Bold)
                        }
                    }

                    ExerciseDrillPhase.COMPLETED_DRILL -> {
                        OutlinedButton(
                            onClick = onKeepSolving,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Keep Solving Board")
                        }
                        Button(
                            onClick = onNextExercise,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                        ) {
                            Text("Next Exercise ->", fontWeight = FontWeight.Bold)
                        }
                    }

                    ExerciseDrillPhase.KEEP_SOLVING_FULL_BOARD,
                    ExerciseDrillPhase.FULL_BOARD_SOLVED -> {
                        Button(
                            onClick = onNextExercise,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                        ) {
                            Text("Next Exercise ->", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InkModeSelectorCard(
    currentMode: InkInputMode,
    onSelectMode: (InkInputMode) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Stylus / Pencil Interaction Mode:",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (mode in InkInputMode.entries) {
                    val isSelected = mode == currentMode
                    Button(
                        onClick = { onSelectMode(mode) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text(mode.label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Text(
                text = currentMode.shortHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ProgressiveHintCard(
    revealedStage: Int,
    stage1Text: String,
    stage2Text: String,
    stage3Text: String,
    onRevealNextStage: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "3-Stage Progressive Hints ($revealedStage / 3 Revealed)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) MaterialTheme.colorScheme.primary else Color(0xFF1E3A8A)
                )

                if (revealedStage < 3) {
                    val btnLabel = when (revealedStage) {
                        0 -> "Show Hint 1 (Focus Area)"
                        1 -> "Show Hint 2 (Spot Pattern)"
                        else -> "Show Hint 3 (Full Solution)"
                    }
                    OutlinedButton(onClick = onRevealNextStage) {
                        Text(btnLabel)
                    }
                }
            }

            if (revealedStage == 0) {
                Text(
                    text = "Solve without hints to earn a ★ Clean Solve badge! Stuck? Reveal Hint 1 for a gentle nudge.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (revealedStage >= 1) {
                HintStageBox(
                    title = "Stage 1 — Where to Look:",
                    text = stage1Text,
                    bgColor = if (isDark) Color(0xFF172554) else Color(0xFFEFF6FF),
                    titleColor = if (isDark) Color(0xFF93C5FD) else Color(0xFF1E40AF)
                )
            }
            if (revealedStage >= 2) {
                HintStageBox(
                    title = "Stage 2 — Pattern Cells Highlighted on Board:",
                    text = stage2Text,
                    bgColor = if (isDark) Color(0xFF064E3B) else Color(0xFFECFDF5),
                    titleColor = if (isDark) Color(0xFF6EE7B7) else Color(0xFF065F46)
                )
            }
            if (revealedStage >= 3) {
                HintStageBox(
                    title = "Stage 3 — Full Deduction & Target Eliminations:",
                    text = stage3Text,
                    bgColor = if (isDark) Color(0xFF450A0A) else Color(0xFFFEF2F2),
                    titleColor = if (isDark) Color(0xFFFCA5A5) else Color(0xFF991B1B)
                )
            }
        }
    }
}

@Composable
private fun HintStageBox(
    title: String,
    text: String,
    bgColor: Color,
    titleColor: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = bgColor
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = titleColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
