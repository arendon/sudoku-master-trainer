package com.arendon.sudokumaster.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
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
import com.arendon.sudokumaster.data.local.PatternMasterySummary
import com.arendon.sudokumaster.domain.model.ComplexityTier
import com.arendon.sudokumaster.domain.model.PatternType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    masterySummaries: Map<PatternType, PatternMasterySummary>,
    onOpenLesson: (PatternType) -> Unit,
    onStartPractice: (PatternType) -> Unit
) {
    val totalExercises = masterySummaries.values.sumOf { it.totalExercises }.coerceAtLeast(120)
    val totalSolved = masterySummaries.values.sumOf { it.solvedCount }
    val totalClean = masterySummaries.values.sumOf { it.cleanSolvedCount }
    val patternsMastered = masterySummaries.values.count { it.solvedCount >= 10 }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Advanced Sudoku Pattern Trainer",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "12 Patterns Ordered by Complexity • Magic Tab 4 & Pencil Ready",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFFDBEAFE)
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                MasteryDashboardCard(
                    totalSolved = totalSolved,
                    totalExercises = totalExercises,
                    totalClean = totalClean,
                    patternsMastered = patternsMastered
                )
            }

            for (tier in ComplexityTier.entries) {
                item(key = "tier_header_${tier.name}") {
                    TierHeader(tier = tier)
                }

                val patterns = PatternType.byTier(tier)
                items(patterns, key = { it.name }) { pattern ->
                    val summary = masterySummaries[pattern] ?: PatternMasterySummary(
                        patternType = pattern,
                        totalExercises = 10,
                        solvedCount = 0,
                        cleanSolvedCount = 0,
                        inProgressCount = 0
                    )
                    PatternCurriculumCard(
                        pattern = pattern,
                        summary = summary,
                        onOpenLesson = { onOpenLesson(pattern) },
                        onStartPractice = { onStartPractice(pattern) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MasteryDashboardCard(
    totalSolved: Int,
    totalExercises: Int,
    totalClean: Int,
    patternsMastered: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Your Local Training Progress",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatMetricItem(
                    value = "$totalSolved / $totalExercises",
                    label = "Exercises Solved"
                )
                StatMetricItem(
                    value = "$totalClean",
                    label = "Clean Solves (0 Hints)"
                )
                StatMetricItem(
                    value = "$patternsMastered / 12",
                    label = "Patterns Mastered (10+)"
                )
            }

            LinearProgressIndicator(
                progress = { (totalSolved.toFloat() / totalExercises.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = Color(0xFF10B981),
                trackColor = Color(0xFF334155)
            )
        }
    }
}

@Composable
private fun StatMetricItem(value: String, label: String) {
    Column {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF38BDF8)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFFCBD5E1)
        )
    }
}

@Composable
private fun TierHeader(tier: ComplexityTier) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Text(
            text = tier.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF0F172A)
        )
        Text(
            text = tier.subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF475569)
        )
    }
}

@Composable
private fun PatternCurriculumCard(
    pattern: PatternType,
    summary: PatternMasterySummary,
    onOpenLesson: () -> Unit,
    onStartPractice: () -> Unit
) {
    val isMastered = summary.solvedCount >= 10
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFDBEAFE))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "#${pattern.complexityOrder}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF1E3A8A)
                        )
                    }

                    Text(
                        text = pattern.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isMastered) Color(0xFFD1FAE5) else Color(0xFFF1F5F9)
                ) {
                    Text(
                        text = if (isMastered) {
                            "Mastered • ${summary.solvedCount}/${summary.totalExercises}"
                        } else {
                            "${summary.solvedCount}/${summary.totalExercises} Solved (${summary.cleanSolvedCount} Clean)"
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isMastered) Color(0xFF065F46) else Color(0xFF334155)
                    )
                }
            }

            Text(
                text = pattern.shortSummary,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF334155)
            )

            LinearProgressIndicator(
                progress = {
                    (summary.solvedCount.toFloat() / summary.totalExercises.coerceAtLeast(1).toFloat())
                        .coerceIn(0f, 1f)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (isMastered) Color(0xFF059669) else Color(0xFF2563EB),
                trackColor = Color(0xFFE2E8F0)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(onClick = onOpenLesson) {
                    Text("1. Learn & Examples")
                }
                Spacer(modifier = Modifier.width(10.dp))
                Button(onClick = onStartPractice) {
                    Text("2. Practice (${summary.totalExercises} Exercises)")
                }
            }
        }
    }
}
