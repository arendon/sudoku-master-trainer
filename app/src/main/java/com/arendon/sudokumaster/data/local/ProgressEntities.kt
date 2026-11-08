package com.arendon.sudokumaster.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ExerciseCompletionStatus {
    UNSTARTED,
    IN_PROGRESS,
    SOLVED_WITH_HINTS,
    SOLVED_CLEAN
}

@Entity(tableName = "exercise_progress")
data class ExerciseProgressEntity(
    @PrimaryKey val exerciseId: String,
    val patternTypeName: String,
    val status: String, // ExerciseCompletionStatus.name
    val hintsUsed: Int,
    val drillPhase: String,
    val candidatesPrecomputed: Boolean,
    val serializedUserDigits: String,
    val serializedEliminatedByUser: String,
    val serializedSelectedPatternCells: String,
    val updatedAtEpochMs: Long
)

val ExerciseProgressEntity.completionStatus: ExerciseCompletionStatus
    get() = runCatching { ExerciseCompletionStatus.valueOf(status) }
        .getOrDefault(ExerciseCompletionStatus.UNSTARTED)

@Entity(tableName = "pattern_exercise_counts")
data class PatternExerciseCountEntity(
    @PrimaryKey val patternTypeName: String,
    val totalCount: Int
)
