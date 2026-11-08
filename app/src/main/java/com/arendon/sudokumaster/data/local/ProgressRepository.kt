package com.arendon.sudokumaster.data.local

import com.arendon.sudokumaster.domain.model.CellPos
import com.arendon.sudokumaster.domain.model.Exercise
import com.arendon.sudokumaster.domain.model.ExerciseDrillPhase
import com.arendon.sudokumaster.domain.model.PatternType
import com.arendon.sudokumaster.domain.model.SudokuBoard
import com.arendon.sudokumaster.domain.solver.CandidateCalculator
import com.arendon.sudokumaster.domain.solver.CuratedExerciseBank
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class PatternMasterySummary(
    val patternType: PatternType,
    val totalExercises: Int,
    val solvedCount: Int,
    val cleanSolvedCount: Int,
    val inProgressCount: Int
)

/**
 * Coordinates local Room persistence for exercise progress, board state serialization,
 * and on-demand exercise generation counts.
 */
class ProgressRepository(private val dao: ProgressDao) {

    val allProgressFlow: Flow<Map<String, ExerciseProgressEntity>> =
        dao.observeAllProgress().map { list -> list.associateBy { it.exerciseId } }

    val exerciseCountsFlow: Flow<Map<PatternType, Int>> =
        dao.observePatternExerciseCounts().map { list ->
            val map = list.mapNotNull { entity ->
                runCatching { PatternType.valueOf(entity.patternTypeName) to entity.totalCount }.getOrNull()
            }.toMap()
            PatternType.entries.associateWith { type -> (map[type] ?: 10).coerceAtLeast(10) }
        }

    fun getExercisesForPattern(patternType: PatternType, totalCount: Int): List<Exercise> {
        val count = totalCount.coerceAtLeast(10)
        val initial = CuratedExerciseBank.getInitialExercises(patternType)
        if (count <= 10) return initial
        val extra = (11..count).map { idx ->
            CuratedExerciseBank.generateExercise(patternType, idx)
        }
        return initial + extra
    }

    suspend fun generateMoreExercises(patternType: PatternType, additionalCount: Int = 5): Int {
        val current = (dao.getExerciseCountForPattern(patternType.name) ?: 10).coerceAtLeast(10)
        val next = current + additionalCount
        dao.upsertPatternExerciseCount(
            PatternExerciseCountEntity(
                patternTypeName = patternType.name,
                totalCount = next
            )
        )
        return next
    }

    suspend fun loadSavedExerciseState(exercise: Exercise): Triple<SudokuBoard, ExerciseDrillPhase, Int>? {
        val entity = dao.getProgressForExercise(exercise.id) ?: return null
        val userDigits = deserializeDigits(entity.serializedUserDigits)
        val eliminated = deserializeCellDigitSets(entity.serializedEliminatedByUser)
        val selectedCells = deserializeCellSet(entity.serializedSelectedPatternCells)

        var board = exercise.initialBoard.copy(
            userDigits = userDigits,
            eliminatedByUser = eliminated,
            selectedPatternCells = selectedCells
        )
        if (entity.candidatesPrecomputed) {
            board = CandidateCalculator.precomputeCandidates(board)
        }

        val phase = runCatching { ExerciseDrillPhase.valueOf(entity.drillPhase) }
            .getOrDefault(ExerciseDrillPhase.STEP_1_SELECT_PATTERN_CELLS)

        return Triple(board, phase, entity.hintsUsed)
    }

    suspend fun saveExerciseState(
        exercise: Exercise,
        board: SudokuBoard,
        phase: ExerciseDrillPhase,
        hintsUsed: Int
    ) {
        val existing = dao.getProgressForExercise(exercise.id)
        val isDrillComplete = phase == ExerciseDrillPhase.COMPLETED_DRILL ||
            phase == ExerciseDrillPhase.KEEP_SOLVING_FULL_BOARD ||
            phase == ExerciseDrillPhase.FULL_BOARD_SOLVED

        val newStatus = when {
            existing?.completionStatus == ExerciseCompletionStatus.SOLVED_CLEAN ->
                ExerciseCompletionStatus.SOLVED_CLEAN // Never downgrade a clean solve badge
            isDrillComplete && hintsUsed == 0 ->
                ExerciseCompletionStatus.SOLVED_CLEAN
            isDrillComplete ->
                ExerciseCompletionStatus.SOLVED_WITH_HINTS
            existing?.completionStatus == ExerciseCompletionStatus.SOLVED_WITH_HINTS ->
                ExerciseCompletionStatus.SOLVED_WITH_HINTS
            else ->
                ExerciseCompletionStatus.IN_PROGRESS
        }

        val entity = ExerciseProgressEntity(
            exerciseId = exercise.id,
            patternTypeName = exercise.patternType.name,
            status = newStatus.name,
            hintsUsed = hintsUsed,
            drillPhase = phase.name,
            candidatesPrecomputed = board.candidatesPrecomputed,
            serializedUserDigits = serializeDigits(board.userDigits),
            serializedEliminatedByUser = serializeCellDigitSets(board.eliminatedByUser),
            serializedSelectedPatternCells = serializeCellSet(board.selectedPatternCells),
            updatedAtEpochMs = System.currentTimeMillis()
        )
        dao.upsertProgress(entity)
    }

    suspend fun resetExercise(exerciseId: String) {
        dao.resetExerciseProgress(exerciseId)
    }

    private fun serializeDigits(grid: List<List<Int>>): String =
        buildString(81) {
            for (r in 0..8) {
                for (c in 0..8) {
                    append(grid[r][c].coerceIn(0, 9))
                }
            }
        }

    private fun deserializeDigits(raw: String): List<List<Int>> {
        if (raw.length != 81) return List(9) { List(9) { 0 } }
        return List(9) { r ->
            List(9) { c ->
                (raw[r * 9 + c] - '0').coerceIn(0, 9)
            }
        }
    }

    private fun serializeCellDigitSets(sets: List<List<Set<Int>>>): String {
        val parts = mutableListOf<String>()
        for (r in 0..8) {
            for (c in 0..8) {
                val s = sets[r][c]
                if (s.isNotEmpty()) {
                    parts.add("$r,$c:${s.sorted().joinToString("")}")
                }
            }
        }
        return parts.joinToString("|")
    }

    private fun deserializeCellDigitSets(raw: String): List<List<Set<Int>>> {
        val grid = Array(9) { Array(9) { emptySet<Int>() } }
        if (raw.isBlank()) return grid.map { it.toList() }
        for (token in raw.split("|")) {
            val split = token.split(":")
            if (split.size == 2) {
                val rc = split[0].split(",")
                if (rc.size == 2) {
                    val r = rc[0].toIntOrNull()
                    val c = rc[1].toIntOrNull()
                    if (r != null && c != null && r in 0..8 && c in 0..8) {
                        val digits = split[1].mapNotNull { ch -> (ch - '0').takeIf { it in 1..9 } }.toSet()
                        grid[r][c] = digits
                    }
                }
            }
        }
        return grid.map { it.toList() }
    }

    private fun serializeCellSet(cells: Set<CellPos>): String =
        cells.sorted().joinToString("|") { "${it.row},${it.col}" }

    private fun deserializeCellSet(raw: String): Set<CellPos> {
        if (raw.isBlank()) return emptySet()
        return raw.split("|").mapNotNull { token ->
            val rc = token.split(",")
            if (rc.size == 2) {
                val r = rc[0].toIntOrNull()
                val c = rc[1].toIntOrNull()
                if (r != null && c != null && r in 0..8 && c in 0..8) CellPos(r, c) else null
            } else null
        }.toSet()
    }
}
