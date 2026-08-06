package com.notresemaine.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val date: String?,        // yyyy-MM-dd ; null = pas encore répartie sur un jour
    val weekStart: String?,   // lundi de la semaine, yyyy-MM-dd
    val isPriority: Boolean = false,
    val isSport: Boolean = false,
    val done: Boolean = false,
    val deleted: Boolean = false,
    val updatedAt: Long
)

@Entity(tableName = "day_plans")
data class DayPlanEntity(
    @PrimaryKey val id: String, // "$userId:$date"
    val userId: String,
    val date: String,
    val wakeTime: String? = null,     // "06:30"
    val focusBlocks: String? = null,  // texte libre : "9h-11h écriture"
    val deleted: Boolean = false,
    val updatedAt: Long
)

@Entity(tableName = "week_plans")
data class WeekPlanEntity(
    @PrimaryKey val id: String, // "$userId:$weekStart"
    val userId: String,
    val weekStart: String,
    val priority: String? = null,
    val abandon: String? = null,
    val validatedAt: Long? = null,
    val deleted: Boolean = false,
    val updatedAt: Long
)

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: String, // "A" (bleu) ou "B" (orange)
    val updatedAt: Long
)

@Entity(tableName = "encouragements")
data class EncouragementEntity(
    @PrimaryKey val id: String,
    val fromUser: String,
    val toUser: String,
    val date: String,
    val message: String,
    val deleted: Boolean = false,
    val updatedAt: Long
)

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE userId = :userId AND date = :date AND deleted = 0 ORDER BY isPriority DESC, updatedAt")
    fun byDate(userId: String, date: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE userId = :userId AND date = :date AND deleted = 0")
    suspend fun byDateOnce(userId: String, date: String): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE weekStart = :weekStart AND deleted = 0 ORDER BY date, isPriority DESC")
    fun byWeekAllUsers(weekStart: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE userId = :userId AND weekStart = :weekStart AND deleted = 0")
    fun byWeek(userId: String, weekStart: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE userId = :userId AND weekStart = :weekStart AND deleted = 0")
    suspend fun byWeekOnce(userId: String, weekStart: String): List<TaskEntity>

    @Query("SELECT COUNT(*) FROM tasks WHERE userId = :userId AND date = :date AND deleted = 0 AND isSport = 0")
    suspend fun countForDay(userId: String, date: String): Int

    @Query("SELECT * FROM tasks WHERE userId = :userId AND date = :date AND isPriority = 1 AND deleted = 0 LIMIT 1")
    suspend fun priorityOfDay(userId: String, date: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun byId(id: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE updatedAt > :ts")
    suspend fun modifiedSince(ts: Long): List<TaskEntity>

    @Query("UPDATE tasks SET userId = :newId, updatedAt = :now WHERE userId = :oldId")
    suspend fun migrateUser(oldId: String, newId: String, now: Long)

    @Upsert
    suspend fun upsert(task: TaskEntity)
}

@Dao
interface DayPlanDao {
    @Query("SELECT * FROM day_plans WHERE userId = :userId AND date = :date AND deleted = 0 LIMIT 1")
    fun byDate(userId: String, date: String): Flow<DayPlanEntity?>

    @Query("SELECT * FROM day_plans WHERE updatedAt > :ts")
    suspend fun modifiedSince(ts: Long): List<DayPlanEntity>

    @Query("SELECT * FROM day_plans WHERE id = :id")
    suspend fun byId(id: String): DayPlanEntity?

    @Query("UPDATE day_plans SET userId = :newId, id = :newId || ':' || date, updatedAt = :now WHERE userId = :oldId")
    suspend fun migrateUser(oldId: String, newId: String, now: Long)

    @Upsert
    suspend fun upsert(plan: DayPlanEntity)
}

@Dao
interface WeekPlanDao {
    @Query("SELECT * FROM week_plans WHERE userId = :userId AND weekStart = :weekStart AND deleted = 0 LIMIT 1")
    fun byWeek(userId: String, weekStart: String): Flow<WeekPlanEntity?>

    @Query("SELECT * FROM week_plans WHERE weekStart = :weekStart AND deleted = 0")
    fun byWeekAllUsers(weekStart: String): Flow<List<WeekPlanEntity>>

    @Query("SELECT * FROM week_plans WHERE userId = :userId AND weekStart = :weekStart LIMIT 1")
    suspend fun byWeekOnce(userId: String, weekStart: String): WeekPlanEntity?

    @Query("SELECT * FROM week_plans WHERE updatedAt > :ts")
    suspend fun modifiedSince(ts: Long): List<WeekPlanEntity>

    @Query("SELECT * FROM week_plans WHERE id = :id")
    suspend fun byId(id: String): WeekPlanEntity?

    @Query("UPDATE week_plans SET userId = :newId, id = :newId || ':' || weekStart, updatedAt = :now WHERE userId = :oldId")
    suspend fun migrateUser(oldId: String, newId: String, now: Long)

    @Upsert
    suspend fun upsert(plan: WeekPlanEntity)
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles")
    fun all(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun byId(id: String): ProfileEntity?

    @Query("SELECT * FROM profiles WHERE updatedAt > :ts")
    suspend fun modifiedSince(ts: Long): List<ProfileEntity>

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun delete(id: String)

    @Upsert
    suspend fun upsert(profile: ProfileEntity)
}

@Dao
interface EncouragementDao {
    @Query("SELECT * FROM encouragements WHERE toUser = :userId AND date = :date AND deleted = 0")
    fun forDate(userId: String, date: String): Flow<List<EncouragementEntity>>

    @Query("SELECT * FROM encouragements WHERE updatedAt > :ts")
    suspend fun modifiedSince(ts: Long): List<EncouragementEntity>

    @Query("SELECT * FROM encouragements WHERE id = :id")
    suspend fun byId(id: String): EncouragementEntity?

    @Upsert
    suspend fun upsert(e: EncouragementEntity)
}

@Database(
    entities = [
        TaskEntity::class, DayPlanEntity::class, WeekPlanEntity::class,
        ProfileEntity::class, EncouragementEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDb : RoomDatabase() {
    abstract fun tasks(): TaskDao
    abstract fun dayPlans(): DayPlanDao
    abstract fun weekPlans(): WeekPlanDao
    abstract fun profiles(): ProfileDao
    abstract fun encouragements(): EncouragementDao

    companion object {
        @Volatile private var instance: AppDb? = null
        fun get(context: Context): AppDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDb::class.java, "notre_semaine.db")
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
        }
    }
}
