package com.arendon.sudokumaster.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arendon.sudokumaster.domain.handwriting.DigitStrokeRecognizer
import com.arendon.sudokumaster.domain.handwriting.InkPoint
import com.arendon.sudokumaster.domain.handwriting.MlKitDigitRecognizer
import com.arendon.sudokumaster.domain.handwriting.RecognizedInkResult
import com.arendon.sudokumaster.domain.model.CellPos
import com.arendon.sudokumaster.domain.model.InkInputMode
import kotlinx.coroutines.delay

/**
 * Enlarged Stylus Handwriting Pad & Quick Digit Bar for the Magic Tab 4.
 * Lets the user write digits 1-9 (or scribble to erase) for the currently selected cell,
 * shows live recognition feedback + 1-tap alternative digits, and provides quick digit buttons.
 */
@Composable
fun StylusZoomPad(
    activeCell: CellPos?,
    activeCellCandidates: Set<Int>,
    inkMode: InkInputMode,
    stylusOnlyPalmRejection: Boolean,
    lastRecognizedStatus: String?,
    lastAlternatives: List<Int>,
    onInkRecognized: (RecognizedInkResult) -> Unit,
    onDirectDigitTap: (Int) -> Unit,
    onEraseTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val completedStrokes = remember { mutableStateListOf<List<InkPoint>>() }
    val currentStroke = remember { mutableStateListOf<InkPoint>() }
    var lastStrokeTimestamp by remember { mutableLongStateOf(0L) }
    var isDrawingNow by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        MlKitDigitRecognizer.warmupModelInBackground()
    }

    LaunchedEffect(lastStrokeTimestamp, isDrawingNow) {
        if (!isDrawingNow && lastStrokeTimestamp > 0L && completedStrokes.isNotEmpty()) {
            val waitMs = if (DigitStrokeRecognizer.mayExpectAdditionalStroke(completedStrokes.toList())) 680L else 460L
            delay(waitMs)
            if (!isDrawingNow && completedStrokes.isNotEmpty()) {
                val snapshot = completedStrokes.toList()
                completedStrokes.clear()
                val result = MlKitDigitRecognizer.recognizeHybrid(snapshot)
                onInkRecognized(result)
            }
        }
    }

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val surfaceBg = if (isDark) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFF8FAFC)
    val canvasBg = if (isDark) Color(0xFF0F172A) else Color.White
    val canvasBorder = if (isDark) Color(0xFF475569) else Color(0xFF94A3B8)
    val guideColor = if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)
    val inkColor = if (isDark) Color(0xFFA78BFA) else Color(0xFF6D28D9)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = surfaceBg,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val targetLabel = activeCell?.coordLabel ?: "Tap a cell"
                val candStr = if (activeCellCandidates.isNotEmpty()) {
                    "Candidates: {${activeCellCandidates.sorted().joinToString(", ")}}"
                } else {
                    "No candidates shown"
                }
                Text(
                    text = "Magic-Pencil Pad • $targetLabel ($candStr)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) MaterialTheme.colorScheme.primary else Color(0xFF1E3A8A)
                )

                if (lastRecognizedStatus != null) {
                    Text(
                        text = lastRecognizedStatus,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF059669),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Enlarged Stylus Drawing Canvas
                Box(
                    modifier = Modifier
                        .size(width = 140.dp, height = 115.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(canvasBg)
                        .border(2.dp, canvasBorder, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (completedStrokes.isEmpty() && currentStroke.isEmpty()) {
                        Text(
                            text = if (activeCell == null) "Select a cell first"
                            else "Write 1-9 here\n(${inkMode.label})\nor scribble to erase",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8)
                        )
                    }

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(activeCell, inkMode, stylusOnlyPalmRejection) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull() ?: continue
                                        if (stylusOnlyPalmRejection &&
                                            change.type != PointerType.Stylus &&
                                            change.type != PointerType.Eraser
                                        ) {
                                            continue
                                        }

                                        when (event.type) {
                                            PointerEventType.Press -> {
                                                if (change.type == PointerType.Eraser) {
                                                    onInkRecognized(RecognizedInkResult.Erase)
                                                } else {
                                                    isDrawingNow = true
                                                    currentStroke.clear()
                                                    currentStroke.add(InkPoint(change.position.x, change.position.y))
                                                }
                                                change.consume()
                                            }
                                            PointerEventType.Move -> {
                                                if (isDrawingNow && change.pressed) {
                                                    currentStroke.add(InkPoint(change.position.x, change.position.y))
                                                    change.consume()
                                                }
                                            }
                                            PointerEventType.Release -> {
                                                if (isDrawingNow) {
                                                    if (currentStroke.size >= 2) {
                                                        completedStrokes.add(currentStroke.toList())
                                                        lastStrokeTimestamp = System.currentTimeMillis()
                                                    }
                                                    currentStroke.clear()
                                                    isDrawingNow = false
                                                }
                                                change.consume()
                                            }
                                        }
                                    }
                                }
                            }
                    ) {
                        // Subtle guide crosshairs
                        drawLine(
                            color = guideColor,
                            start = Offset(size.width / 2f, 8f),
                            end = Offset(size.width / 2f, size.height - 8f),
                            strokeWidth = 1f
                        )
                        drawLine(
                            color = guideColor,
                            start = Offset(8f, size.height / 2f),
                            end = Offset(size.width - 8f, size.height / 2f),
                            strokeWidth = 1f
                        )

                        val allStrokes = completedStrokes.toList() + listOf(currentStroke.toList())
                        for (stroke in allStrokes) {
                            if (stroke.size >= 2) {
                                val path = Path().apply {
                                    moveTo(stroke.first().x, stroke.first().y)
                                    for (i in 1 until stroke.size) {
                                        lineTo(stroke[i].x, stroke[i].y)
                                    }
                                }
                                drawPath(
                                    path = path,
                                    color = inkColor,
                                    style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                )
                            }
                        }
                    }
                }

                // Quick 1-9 Tap / Stylus Pad + Alternative override pills
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Quick Tap (${inkMode.label}):",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (d in 1..5) {
                            val isPresent = d in activeCellCandidates
                            QuickDigitButton(
                                digit = d,
                                isCandidatePresent = isPresent,
                                isDark = isDark,
                                onClick = { onDirectDigitTap(d) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (d in 6..9) {
                            val isPresent = d in activeCellCandidates
                            QuickDigitButton(
                                digit = d,
                                isCandidatePresent = isPresent,
                                isDark = isDark,
                                onClick = { onDirectDigitTap(d) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isDark) Color(0xFF450A0A) else Color(0xFFFEE2E2))
                                .border(1.dp, if (isDark) Color(0xFFDC2626) else Color(0xFFF87171), RoundedCornerShape(6.dp))
                                .clickable { onEraseTap() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "CLR",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color(0xFFFCA5A5) else Color(0xFFB91C1C)
                            )
                        }
                    }

                    if (lastAlternatives.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Did you mean:",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                            )
                            for (alt in lastAlternatives) {
                                OutlinedButton(
                                    onClick = { onDirectDigitTap(alt) },
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text(text = alt.toString(), fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickDigitButton(
    digit: Int,
    isCandidatePresent: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = when {
        isCandidatePresent -> if (isDark) Color(0xFF1E3A8A) else Color(0xFFDBEAFE)
        else -> if (isDark) Color(0xFF1E293B) else Color.White
    }
    val border = when {
        isCandidatePresent -> if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB)
        else -> if (isDark) Color(0xFF475569) else Color(0xFFCBD5E1)
    }
    val textColor = when {
        isCandidatePresent -> if (isDark) Color(0xFFDBEAFE) else Color(0xFF1E3A8A)
        else -> if (isDark) Color(0xFFF1F5F9) else Color(0xFF334155)
    }

    Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = digit.toString(),
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = textColor
        )
    }
}
