package com.arendon.sudokumaster.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressDao {

    @Query("SELECT * FROM exercise_progress")
    fun observeAllProgress(): Flow<List<ExerciseProgressEntity>>

    @Query("SELECT * FROM exercise_progress WHERE exerciseId = :exerciseId LIMIT 1")
    suspend fun getProgressForExercise(exerciseId: String): ExerciseProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(entity: ExerciseProgressEntity)

    @Query("DELETE FROM exercise_progress WHERE exerciseId = :exerciseId")
    suspend fun resetExerciseProgress(exerciseId: String)

    @Query("SELECT * FROM pattern_exercise_counts")
    fun observePatternExerciseCounts(): Flow<List<PatternExerciseCountEntity>>

    @Query("SELECT totalCount FROM pattern_exercise_counts WHERE patternTypeName = :patternTypeName LIMIT 1")
    suspend fun getExerciseCountForPattern(patternTypeName: String): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPatternExerciseCount(entity: PatternExerciseCountEntity)
}
