package com.arendon.sudokumaster.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arendon.sudokumaster.data.local.ExerciseCompletionStatus
import com.arendon.sudokumaster.data.local.ExerciseProgressEntity
import com.arendon.sudokumaster.data.local.completionStatus
import com.arendon.sudokumaster.domain.model.Exercise
import com.arendon.sudokumaster.domain.model.InkInputMode
import com.arendon.sudokumaster.domain.model.PatternType
import com.arendon.sudokumaster.domain.model.WalkthroughExample
import com.arendon.sudokumaster.ui.components.SudokuBoardCanvas

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LessonScreen(
    patternType: PatternType,
    examples: List<WalkthroughExample>,
    selectedExampleIndex: Int,
    walkthroughStep: Int,
    exercises: List<Exercise>,
    progressMap: Map<String, ExerciseProgressEntity>,
    onBack: () -> Unit,
    onSelectExample: (Int) -> Unit,
    onSelectWalkthroughStep: (Int) -> Unit,
    onOpenExercise: (Int) -> Unit,
    onGenerateMoreExercises: () -> Unit
) {
    val activeExample = examples.getOrNull(selectedExampleIndex)

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Curriculum",
                            tint = Color.White
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = "#${patternType.complexityOrder} • ${patternType.displayName}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${patternType.tier.title} — Theory, Interactive Examples & Practice Exercises",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFFDBEAFE)
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = { onOpenExercise(0) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Text("Start Exercises")
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
            val isWideTablet = maxWidth >= 840.dp

            if (isWideTablet && activeExample != null) {
                // Landscape Magic Tab 4 Two-Pane Lesson Layout
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Left column: Theory, How to Spot, and Exercise Bank
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        PatternTheoryCard(patternType = patternType)
                        ExerciseBankCard(
                            patternType = patternType,
                            exercises = exercises,
                            progressMap = progressMap,
                            onOpenExercise = onOpenExercise,
                            onGenerateMore = onGenerateMoreExercises
                        )
                    }

                    // Right column: Interactive Visual Walkthrough Board & Stepper
                    Column(
                        modifier = Modifier
                            .weight(1.05f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        InteractiveWalkthroughCard(
                            examples = examples,
                            selectedExampleIndex = selectedExampleIndex,
                            activeExample = activeExample,
                            walkthroughStep = walkthroughStep,
                            onSelectExample = onSelectExample,
                            onSelectStep = onSelectWalkthroughStep
                        )
                    }
                }
            } else {
                // Portrait / Single-Column Scrollable Lesson Layout
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    PatternTheoryCard(patternType = patternType)
                    if (activeExample != null) {
                        InteractiveWalkthroughCard(
                            examples = examples,
                            selectedExampleIndex = selectedExampleIndex,
                            activeExample = activeExample,
                            walkthroughStep = walkthroughStep,
                            onSelectExample = onSelectExample,
                            onSelectStep = onSelectWalkthroughStep
                        )
                    }
                    ExerciseBankCard(
                        patternType = patternType,
                        exercises = exercises,
                        progressMap = progressMap,
                        onOpenExercise = onOpenExercise,
                        onGenerateMore = onGenerateMoreExercises
                    )
                }
            }
        }
    }
}

@Composable
private fun PatternTheoryCard(patternType: PatternType) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "How ${patternType.displayName} Works",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF1E3A8A)
            )
            Text(
                text = patternType.theoryExplanation,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF1E293B)
            )

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFF0FDF4)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "How to Spot It:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF065F46)
                    )
                    patternType.howToSpot.forEachIndexed { idx, tip ->
                        Text(
                            text = "${idx + 1}. $tip",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF064E3B)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InteractiveWalkthroughCard(
    examples: List<WalkthroughExample>,
    selectedExampleIndex: Int,
    activeExample: WalkthroughExample,
    walkthroughStep: Int,
    onSelectExample: (Int) -> Unit,
    onSelectStep: (Int) -> Unit
) {
    val instance = activeExample.patternInstance
    val highlightedCells = if (walkthroughStep >= 2) instance.definingCells else emptySet()
    val highlightedElims = if (walkthroughStep >= 3) instance.targetEliminations else emptySet()
    val visualLinks = if (walkthroughStep >= 2) instance.visualLinks else emptyList()
    val filterDigit = if (instance.focusDigits.size == 1) instance.focusDigits.first() else null

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Interactive Visual Examples",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF1E3A8A)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    examples.forEachIndexed { idx, ex ->
                        FilterChip(
                            selected = idx == selectedExampleIndex,
                            onClick = { onSelectExample(idx) },
                            label = { Text("Example ${idx + 1}") }
                        )
                    }
                }
            }

            // 3-Step Walkthrough Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val steps = listOf(
                    1 to "1. The Setup",
                    2 to "2. Pattern & Links",
                    3 to "3. Eliminations"
                )
                for ((stepNum, label) in steps) {
                    val isSelected = walkthroughStep == stepNum
                    Button(
                        onClick = { onSelectStep(stepNum) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) Color(0xFF2563EB) else Color(0xFFE2E8F0),
                            contentColor = if (isSelected) Color.White else Color(0xFF1E293B)
                        )
                    ) {
                        Text(label, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Explanation banner for the current walkthrough step
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = when (walkthroughStep) {
                    1 -> Color(0xFFEFF6FF)
                    2 -> Color(0xFFECFDF5)
                    else -> Color(0xFFFEF2F2)
                }
            ) {
                val stepText = when (walkthroughStep) {
                    1 -> activeExample.step1SetupText
                    2 -> activeExample.step2LogicText
                    else -> activeExample.step3EliminationText
                }
                Text(
                    text = stepText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF0F172A)
                )
            }

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                SudokuBoardCanvas(
                    board = activeExample.board,
                    activeCell = null,
                    filterDigit = filterDigit,
                    inkMode = InkInputMode.SELECT_PATTERN,
                    stylusOnlyPalmRejection = false,
                    highlightedPatternCells = highlightedCells,
                    pivotCell = if (walkthroughStep >= 2) instance.pivotCell else null,
                    highlightedEliminations = highlightedElims,
                    visualLinks = visualLinks,
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(0.92f)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseBankCard(
    patternType: PatternType,
    exercises: List<Exercise>,
    progressMap: Map<String, ExerciseProgressEntity>,
    onOpenExercise: (Int) -> Unit,
    onGenerateMore: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Practice Exercises (${exercises.size} Available)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = "Select any exercise below or generate more on demand",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF64748B)
                    )
                }

                OutlinedButton(onClick = onGenerateMore) {
                    Text("+ Generate 5 More")
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                exercises.forEachIndexed { idx, exercise ->
                    val status = progressMap[exercise.id]?.completionStatus ?: ExerciseCompletionStatus.UNSTARTED
                    val (bgColor, borderColor, textColor, badge) = when (status) {
                        ExerciseCompletionStatus.SOLVED_CLEAN ->
                            Quad(Color(0xFFD1FAE5), Color(0xFF059669), Color(0xFF065F46), "★ Clean")
                        ExerciseCompletionStatus.SOLVED_WITH_HINTS ->
                            Quad(Color(0xFFDBEAFE), Color(0xFF2563EB), Color(0xFF1E3A8A), "✓ Solved")
                        ExerciseCompletionStatus.IN_PROGRESS ->
                            Quad(Color(0xFFFEF3C7), Color(0xFFD97706), Color(0xFF92400E), "In Progress")
                        ExerciseCompletionStatus.UNSTARTED ->
                            Quad(Color(0xFFF8FAFC), Color(0xFFCBD5E1), Color(0xFF334155), "Start")
                    }

                    Box(
                        modifier = Modifier
                            .size(width = 104.dp, height = 64.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(bgColor)
                            .border(1.5.dp, borderColor, RoundedCornerShape(10.dp))
                            .clickable { onOpenExercise(idx) }
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Exercise #${exercise.indexInPattern}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = badge,
                                style = MaterialTheme.typography.labelSmall,
                                color = textColor
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
