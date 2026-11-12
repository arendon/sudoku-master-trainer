package com.arendon.sudokumaster.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.arendon.sudokumaster.domain.handwriting.DigitStrokeRecognizer
import com.arendon.sudokumaster.domain.handwriting.InkPoint
import com.arendon.sudokumaster.domain.handwriting.MlKitDigitRecognizer
import com.arendon.sudokumaster.domain.handwriting.RecognizedInkResult
import com.arendon.sudokumaster.domain.model.CandidateElimination
import com.arendon.sudokumaster.domain.model.CellPos
import com.arendon.sudokumaster.domain.model.InkInputMode
import com.arendon.sudokumaster.domain.model.LinkType
import com.arendon.sudokumaster.domain.model.PatternLink
import com.arendon.sudokumaster.domain.model.SudokuBoard
import kotlinx.coroutines.delay
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Interactive 9x9 Sudoku canvas supporting:
 * - R1..R9 and C1..C9 coordinate gutters
 * - 3x3 candidate note mini-grids per empty cell
 * - Candidate digit filtering (1..9)
 * - Step 1 Pattern Cell selection & Pivot highlighting
 * - Visual deduction links (Strong, Weak, Wing Bridge, Elimination Ray)
 * - Target candidate elimination badges
 * - Direct in-cell stylus handwriting recognition (with optional Stylus-Only Palm Rejection)
 */
@Composable
fun SudokuBoardCanvas(
    board: SudokuBoard,
    activeCell: CellPos?,
    filterDigit: Int?,
    inkMode: InkInputMode,
    stylusOnlyPalmRejection: Boolean,
    highlightedPatternCells: Set<CellPos> = emptySet(),
    pivotCell: CellPos? = null,
    highlightedEliminations: Set<CandidateElimination> = emptySet(),
    visualLinks: List<PatternLink> = emptyList(),
    readOnly: Boolean = false,
    onSelectCell: (CellPos) -> Unit = {},
    onTogglePatternCell: (CellPos) -> Unit = {},
    onToggleCandidate: (CellPos, Int) -> Unit = { _, _ -> },
    onRecognizedInCellInk: (CellPos, RecognizedInkResult) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    // Active in-cell handwriting state
    var drawingCell by remember { mutableStateOf<CellPos?>(null) }
    val completedStrokes = remember { mutableStateListOf<List<InkPoint>>() }
    val currentStroke = remember { mutableStateListOf<InkPoint>() }
    var lastStrokeUpTimestamp by remember { mutableLongStateOf(0L) }
    var isDrawingInCell by remember { mutableStateOf(false) }

    // Recognize multi-stroke handwritten digit after a brief pause
    LaunchedEffect(lastStrokeUpTimestamp, isDrawingInCell) {
        if (!isDrawingInCell && lastStrokeUpTimestamp > 0L && completedStrokes.isNotEmpty()) {
            val waitMs = if (DigitStrokeRecognizer.mayExpectAdditionalStroke(completedStrokes.toList())) 680L else 460L
            delay(waitMs)
            val targetCell = drawingCell
            if (!isDrawingInCell && targetCell != null && currentStroke.isEmpty() && completedStrokes.isNotEmpty()) {
                val snapshot = completedStrokes.toList()
                completedStrokes.clear()
                drawingCell = null
                val result = MlKitDigitRecognizer.recognizeHybrid(snapshot)
                if (result !is RecognizedInkResult.Unrecognized) {
                    onRecognizedInCellInk(targetCell, result)
                }
            }
        }
    }

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val boardBackground = if (isDark) Color(0xFF131B2E) else Color.White
    val boardBorder = if (isDark) Color(0xFF334155) else Color(0xFF1E293B)

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(boardBackground)
            .border(2.dp, boardBorder, RoundedCornerShape(12.dp))
            .padding(4.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(readOnly, inkMode, stylusOnlyPalmRejection, board) {
                    if (readOnly) return@pointerInput

                    awaitPointerEventScope {
                        var downPos: Offset? = null
                        var downCell: CellPos? = null
                        var isDraggingStroke = false

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: continue

                            // Palm rejection: when enabled, ignore non-stylus touch events
                            if (stylusOnlyPalmRejection &&
                                change.type != PointerType.Stylus &&
                                change.type != PointerType.Eraser
                            ) {
                                continue
                            }

                            val outerMargin = 2f
                            val boardSide = min(size.width, size.height) - outerMargin * 2f
                            val gutter = boardSide * 0.055f
                            val gridSize = boardSide - gutter
                            val cellSize = gridSize / 9f
                            val startX = (size.width - boardSide) / 2f
                            val startY = (size.height - boardSide) / 2f

                            fun offsetToCell(pt: Offset): CellPos? {
                                val relX = pt.x - startX
                                val relY = pt.y - startY
                                if (relX < gutter || relY < gutter || relX >= gutter + gridSize || relY >= gutter + gridSize) return null
                                val c = ((relX - gutter) / cellSize).toInt()
                                val r = ((relY - gutter) / cellSize).toInt()
                                return if (r in 0..8 && c in 0..8) CellPos(r, c) else null
                            }

                            fun isNearCell(pt: Offset, target: CellPos): Boolean {
                                val left = startX + gutter + target.col * cellSize - cellSize * 0.35f
                                val right = startX + gutter + (target.col + 1) * cellSize + cellSize * 0.35f
                                val top = startY + gutter + target.row * cellSize - cellSize * 0.35f
                                val bottom = startY + gutter + (target.row + 1) * cellSize + cellSize * 0.35f
                                return pt.x in left..right && pt.y in top..bottom
                            }

                            when (event.type) {
                                PointerEventType.Press -> {
                                    val rawCell = offsetToCell(change.position)
                                    val existingDrawCell = drawingCell
                                    val effectiveCell = if (existingDrawCell != null &&
                                        completedStrokes.isNotEmpty() &&
                                        isNearCell(change.position, existingDrawCell)
                                    ) {
                                        existingDrawCell
                                    } else {
                                        rawCell
                                    }

                                    if (effectiveCell != null) {
                                        downPos = change.position
                                        downCell = effectiveCell
                                        isDraggingStroke = false
                                        if (existingDrawCell != null && existingDrawCell != effectiveCell && completedStrokes.isNotEmpty()) {
                                            // Flush previous cell ink immediately
                                            val prevResult = DigitStrokeRecognizer.recognize(completedStrokes.toList())
                                            completedStrokes.clear()
                                            drawingCell = null
                                            if (prevResult !is RecognizedInkResult.Unrecognized) {
                                                onRecognizedInCellInk(existingDrawCell, prevResult)
                                            }
                                        }
                                        currentStroke.clear()
                                        currentStroke.add(InkPoint(change.position.x, change.position.y))
                                        change.consume()
                                    }
                                }

                                PointerEventType.Move -> {
                                    val start = downPos
                                    val cell = downCell
                                    if (start != null && cell != null && change.pressed) {
                                        val dist = hypot(
                                            (change.position.x - start.x).toDouble(),
                                            (change.position.y - start.y).toDouble()
                                        ).toFloat()
                                        if (dist > cellSize * 0.14f && inkMode != InkInputMode.SELECT_PATTERN) {
                                            isDraggingStroke = true
                                            isDrawingInCell = true
                                            if (drawingCell == null) {
                                                drawingCell = cell
                                            }
                                        }
                                        currentStroke.add(InkPoint(change.position.x, change.position.y))
                                        change.consume()
                                    }
                                }

                                PointerEventType.Release -> {
                                    val cell = downCell
                                    val releasePos = change.position
                                    isDrawingInCell = false
                                    if (cell != null) {
                                        if (change.type == PointerType.Eraser) {
                                            onSelectCell(cell)
                                            onRecognizedInCellInk(cell, RecognizedInkResult.Erase)
                                            currentStroke.clear()
                                        } else if (isDraggingStroke && inkMode != InkInputMode.SELECT_PATTERN) {
                                            if (currentStroke.size >= 2) {
                                                val strokeCopy = currentStroke.toList()
                                                if (completedStrokes.isEmpty()) {
                                                    // Resolve target cell from the centroid of the first stroke
                                                    val avgX = strokeCopy.map { it.x }.average().toFloat()
                                                    val avgY = strokeCopy.map { it.y }.average().toFloat()
                                                    val centroidCell = offsetToCell(Offset(avgX, avgY)) ?: cell
                                                    drawingCell = centroidCell
                                                    onSelectCell(centroidCell)
                                                } else {
                                                    onSelectCell(drawingCell ?: cell)
                                                }
                                                completedStrokes.add(strokeCopy)
                                                lastStrokeUpTimestamp = System.currentTimeMillis()
                                            }
                                            currentStroke.clear()
                                        } else {
                                            // Clean tap inside cell
                                            currentStroke.clear()
                                            onSelectCell(cell)
                                            when (inkMode) {
                                                InkInputMode.SELECT_PATTERN -> {
                                                    onTogglePatternCell(cell)
                                                }
                                                InkInputMode.CANDIDATE_NOTE -> {
                                                    // Determine which mini-grid candidate (1..9) was tapped inside the cell
                                                    val cellLeft = startX + gutter + cell.col * cellSize
                                                    val cellTop = startY + gutter + cell.row * cellSize
                                                    val subCol = (((releasePos.x - cellLeft) / (cellSize / 3f)).toInt()).coerceIn(0, 2)
                                                    val subRow = (((releasePos.y - cellTop) / (cellSize / 3f)).toInt()).coerceIn(0, 2)
                                                    val tappedDigit = subRow * 3 + subCol + 1
                                                    onToggleCandidate(cell, tappedDigit)
                                                }
                                                InkInputMode.FINAL_DIGIT -> {
                                                    // Selects cell so user can also write in Zoom Pad
                                                }
                                            }
                                        }
                                    }
                                    downPos = null
                                    downCell = null
                                    isDraggingStroke = false
                                    change.consume()
                                }
                            }
                        }
                    }
                }
        ) {
            val outerMargin = 2f
            val boardSide = min(size.width, size.height) - outerMargin * 2f
            val gutter = boardSide * 0.055f
            val gridSize = boardSide - gutter
            val cellSize = gridSize / 9f
            val startX = (size.width - boardSide) / 2f
            val startY = (size.height - boardSide) / 2f

            val elimLookup = highlightedEliminations.groupBy({ it.cell }, { it.digit })
            val allPatternHighlighted = board.selectedPatternCells union highlightedPatternCells

            // 1. Draw coordinate headers (C1..C9, R1..R9)
            val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (isDark) android.graphics.Color.parseColor("#94A3B8") else android.graphics.Color.parseColor("#64748B")
                textSize = gutter * 0.58f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            for (i in 0..8) {
                val cx = startX + gutter + (i + 0.5f) * cellSize
                val cy = startY + gutter * 0.68f
                drawContext.canvas.nativeCanvas.drawText("C${i + 1}", cx, cy, headerPaint)

                val rx = startX + gutter * 0.48f
                val ry = startY + gutter + (i + 0.5f) * cellSize + headerPaint.textSize * 0.35f
                drawContext.canvas.nativeCanvas.drawText("R${i + 1}", rx, ry, headerPaint)
            }

            // 2. Draw cell backgrounds (peer shading, filter shading, pattern cells, pivot, active cell)
            for (r in 0..8) {
                for (c in 0..8) {
                    val pos = CellPos(r, c)
                    val left = startX + gutter + c * cellSize
                    val top = startY + gutter + r * cellSize

                    val hasFilteredCandidate = filterDigit != null &&
                        (board.digitAt(pos) == filterDigit || filterDigit in board.candidatesAt(pos))

                    val bgColor = when {
                        pos == pivotCell -> if (isDark) Color(0xFF78350F) else Color(0xFFFEF3C7) // Warm Gold for Pivot
                        pos in allPatternHighlighted -> if (isDark) Color(0xFF064E3B) else Color(0xFFD1FAE5) // Emerald tint for Pattern Defining Cells
                        pos == activeCell -> if (isDark) Color(0xFF1E3A8A) else Color(0xFFDBEAFE) // Active selected cell
                        hasFilteredCandidate -> if (isDark) Color(0xFF0369A1).copy(alpha = 0.55f) else Color(0xFFE0F2FE) // Candidate filter highlight
                        activeCell != null && pos.isPeerOf(activeCell) -> if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)
                        else -> boardBackground
                    }

                    drawRect(
                        color = bgColor,
                        topLeft = Offset(left, top),
                        size = Size(cellSize, cellSize)
                    )

                    // Draw distinct border around Pattern Defining Cells
                    if (pos in allPatternHighlighted) {
                        val borderColor = if (pos == pivotCell) Color(0xFFF59E0B) else Color(0xFF10B981)
                        drawRoundRect(
                            color = borderColor,
                            topLeft = Offset(left + 2f, top + 2f),
                            size = Size(cellSize - 4f, cellSize - 4f),
                            cornerRadius = CornerRadius(6f, 6f),
                            style = Stroke(width = 3.2f)
                        )
                    } else if (pos == activeCell) {
                        drawRect(
                            color = if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB),
                            topLeft = Offset(left + 1.5f, top + 1.5f),
                            size = Size(cellSize - 3f, cellSize - 3f),
                            style = Stroke(width = 2.8f)
                        )
                    }
                }
            }

            // 3. Draw grid lines (thin cell lines, thick 3x3 box borders)
            for (i in 0..9) {
                val offset = gutter + i * cellSize
                val isMajor = i % 3 == 0
                val strokeWidth = if (isMajor) 3.5f else 1.1f
                val lineColor = if (isMajor) {
                    if (isDark) Color(0xFF94A3B8) else Color(0xFF0F172A)
                } else {
                    if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1)
                }

                // Vertical line
                drawLine(
                    color = lineColor,
                    start = Offset(startX + offset, startY + gutter),
                    end = Offset(startX + offset, startY + gutter + gridSize),
                    strokeWidth = strokeWidth
                )
                // Horizontal line
                drawLine(
                    color = lineColor,
                    start = Offset(startX + gutter, startY + offset),
                    end = Offset(startX + gutter + gridSize, startY + offset),
                    strokeWidth = strokeWidth
                )
            }

            // 4. Draw Visual Pattern Links (Walkthrough Step 2/3 & Hint Stage 3)
            for (link in visualLinks) {
                val startCenter = Offset(
                    x = startX + gutter + (link.from.col + 0.5f) * cellSize,
                    y = startY + gutter + (link.from.row + 0.5f) * cellSize
                )
                val endCenter = Offset(
                    x = startX + gutter + (link.to.col + 0.5f) * cellSize,
                    y = startY + gutter + (link.to.row + 0.5f) * cellSize
                )

                val linkColor = when (link.linkType) {
                    LinkType.STRONG -> Color(0xFF059669)
                    LinkType.WEAK -> Color(0xFF2563EB)
                    LinkType.WING_BRIDGE -> Color(0xFFD97706)
                    LinkType.ELIMINATION_RAY -> Color(0xFFDC2626)
                }

                val pathEffect = when (link.linkType) {
                    LinkType.STRONG, LinkType.WING_BRIDGE -> null
                    LinkType.WEAK, LinkType.ELIMINATION_RAY ->
                        PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                }

                drawLine(
                    color = linkColor.copy(alpha = 0.78f),
                    start = startCenter,
                    end = endCenter,
                    strokeWidth = 4.2f,
                    cap = StrokeCap.Round,
                    pathEffect = pathEffect
                )

                // Draw arrowhead at endCenter
                val angle = atan2(
                    (endCenter.y - startCenter.y).toDouble(),
                    (endCenter.x - startCenter.x).toDouble()
                ).toFloat()
                val arrowRadius = cellSize * 0.16f
                val tip = Offset(
                    x = endCenter.x - cos(angle) * (cellSize * 0.18f),
                    y = endCenter.y - sin(angle) * (cellSize * 0.18f)
                )
                val arrowPath = Path().apply {
                    moveTo(tip.x, tip.y)
                    lineTo(
                        tip.x - arrowRadius * cos(angle - 0.5f),
                        tip.y - arrowRadius * sin(angle - 0.5f)
                    )
                    lineTo(
                        tip.x - arrowRadius * cos(angle + 0.5f),
                        tip.y - arrowRadius * sin(angle + 0.5f)
                    )
                    close()
                }
                drawPath(arrowPath, color = linkColor.copy(alpha = 0.88f))
            }

            // 5. Draw Given Digits, User Digits, and 3x3 Candidate Mini-Grids
            val givenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (isDark) android.graphics.Color.parseColor("#F8FAFC") else android.graphics.Color.parseColor("#0F172A")
                textSize = cellSize * 0.62f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val userDigitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (isDark) android.graphics.Color.parseColor("#60A5FA") else android.graphics.Color.parseColor("#1D4ED8")
                textSize = cellSize * 0.62f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val wrongUserDigitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.parseColor("#DC2626")
                textSize = cellSize * 0.62f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            val candNormalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (isDark) android.graphics.Color.parseColor("#94A3B8") else android.graphics.Color.parseColor("#334155")
                textSize = cellSize * 0.25f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            val candBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (isDark) android.graphics.Color.parseColor("#FFFFFF") else android.graphics.Color.parseColor("#0F172A")
                textSize = cellSize * 0.27f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val candElimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.parseColor("#FFFFFF")
                textSize = cellSize * 0.25f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            for (r in 0..8) {
                for (c in 0..8) {
                    val pos = CellPos(r, c)
                    val left = startX + gutter + c * cellSize
                    val top = startY + gutter + r * cellSize
                    val digit = board.digitAt(pos)

                    if (digit != 0) {
                        val cx = left + cellSize * 0.5f
                        val cy = top + cellSize * 0.5f + givenPaint.textSize * 0.36f
                        val paint = when {
                            board.isGiven(pos) -> givenPaint
                            digit == board.solutionDigits[r][c] -> userDigitPaint
                            else -> wrongUserDigitPaint
                        }
                        drawContext.canvas.nativeCanvas.drawText(digit.toString(), cx, cy, paint)
                    } else {
                        val cands = board.candidatesAt(pos)
                        val targetElimDigits = elimLookup[pos]?.toSet() ?: emptySet()
                        val subSize = cellSize / 3f

                        for (d in 1..9) {
                            val isPresent = d in cands
                            val isTargetElim = d in targetElimDigits
                            if (!isPresent && !isTargetElim) continue

                            val subR = (d - 1) / 3
                            val subC = (d - 1) % 3
                            val subCenterX = left + (subC + 0.5f) * subSize
                            val subCenterY = top + (subR + 0.5f) * subSize

                            if (isTargetElim) {
                                // Crimson target elimination pill + strike-through
                                drawCircle(
                                    color = Color(0xFFDC2626),
                                    radius = subSize * 0.44f,
                                    center = Offset(subCenterX, subCenterY)
                                )
                                drawContext.canvas.nativeCanvas.drawText(
                                    d.toString(),
                                    subCenterX,
                                    subCenterY + candElimPaint.textSize * 0.35f,
                                    candElimPaint
                                )
                            } else if (filterDigit == d) {
                                // Highlighted candidate badge
                                drawCircle(
                                    color = Color(0xFF38BDF8).copy(alpha = 0.45f),
                                    radius = subSize * 0.44f,
                                    center = Offset(subCenterX, subCenterY)
                                )
                                drawContext.canvas.nativeCanvas.drawText(
                                    d.toString(),
                                    subCenterX,
                                    subCenterY + candBoldPaint.textSize * 0.35f,
                                    candBoldPaint
                                )
                            } else {
                                val paint = if (pos in allPatternHighlighted) candBoldPaint else candNormalPaint
                                drawContext.canvas.nativeCanvas.drawText(
                                    d.toString(),
                                    subCenterX,
                                    subCenterY + paint.textSize * 0.35f,
                                    paint
                                )
                            }
                        }
                    }
                }
            }

            // 6. Render live stylus ink strokes in progress
            val allLiveStrokes = completedStrokes.toList() + listOf(currentStroke.toList())
            for (stroke in allLiveStrokes) {
                if (stroke.size >= 2) {
                    val path = Path().apply {
                        moveTo(stroke.first().x, stroke.first().y)
                        for (i in 1 until stroke.size) {
                            lineTo(stroke[i].x, stroke[i].y)
                        }
                    }
                    drawPath(
                        path = path,
                        color = if (isDark) Color(0xFFA78BFA) else Color(0xFF7C3AED),
                        style = Stroke(width = 5.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
            }
        }
    }
}
