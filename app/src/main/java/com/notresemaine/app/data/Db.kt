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
    val goalId: String? = null, // séance générée par un objectif
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

/** Objectif « clé en main » : l'app génère les séances de la semaine à partir de ces réglages. */
@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val domain: String,          // sante, couple, travail, finances, apprentissage, autre
    val sessionsPerWeek: Int,
    val minutesPerSession: Int,
    val preferredTime: String,   // matin, midi, soir
    val preferredDays: String,   // jours ISO séparés par des virgules : "1,3,5"
    val nextAction: String,      // LA prochaine action (méthode GTD / One Thing)
    val isPrivate: Boolean = false,
    val active: Boolean = true,
    val deleted: Boolean = false,
    val updatedAt: Long
)

/** Étape du rituel du matin (S.A.V.E.R.S.) — locale au téléphone. */
@Entity(tableName = "ritual_steps")
data class RitualStepEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val minutes: Int,
    val position: Int,
    val enabled: Boolean = true,
    val updatedAt: Long
)

/** Rituel accompli tel jour — synchronisé (le partenaire voit la série). */
@Entity(tableName = "ritual_logs")
data class RitualLogEntity(
    @PrimaryKey val id: String, // "$userId:$date"
    val userId: String,
    val date: String,
    val minutes: Int,
    val deleted: Boolean = false,
    val updatedAt: Long
)

/** Note en vrac (boîte de réception GTD) — locale au téléphone. */
@Entity(tableName = "inbox_items")
data class InboxItemEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val text: String,
    val processed: Boolean = false,
    val deleted: Boolean = false,
    val updatedAt: Long
)

/** Temps d'écran d'une journée — synchronisé (visibilité mutuelle du Pacte). */
@Entity(tableName = "usage_days")
data class UsageDayEntity(
    @PrimaryKey val id: String, // "$userId:$date"
    val userId: String,
    val date: String,
    val totalMinutes: Int,
    val socialMinutes: Int,
    val unlocks: Int,
    val deleted: Boolean = false,
    val updatedAt: Long
)

/** Demande de déverrouillage du Pacte d'écran, accordée ou non par le partenaire. */
@Entity(tableName = "grace_requests")
data class GraceRequestEntity(
    @PrimaryKey val id: String,
    val fromUser: String,   // celui qui a dépassé sa limite
    val toUser: String,     // le partenaire qui peut accorder
    val date: String,
    val minutes: Int,
    val status: String,     // pending | granted | denied
    val deleted: Boolean = false,
    val updatedAt: Long
)

/** Repas du couple : un midi et un soir par jour, partagés. */
@Entity(tableName = "meals")
data class MealEntity(
    @PrimaryKey val id: String, // "$date:$slot"
    val userId: String,         // qui l'a saisi (pour la synchro)
    val date: String,
    val slot: String,           // midi | soir
    val title: String,
    val ingredients: String,    // "200 g farine, 3 œufs, 1 L lait"
    val deleted: Boolean = false,
    val updatedAt: Long
)

/** Ligne de la liste de courses, générée depuis les menus puis cochable. */
@Entity(tableName = "shopping_items")
data class ShoppingItemEntity(
    @PrimaryKey val id: String, // "$weekStart:$aisle:$label"
    val userId: String,
    val weekStart: String,
    val label: String,
    val aisle: String,
    val checked: Boolean = false,
    val manual: Boolean = false,
    val deleted: Boolean = false,
    val updatedAt: Long
)

/** Sommeil, pas et sport d'une journée — Health Connect ou saisie manuelle. */
@Entity(tableName = "health_days")
data class HealthDayEntity(
    @PrimaryKey val id: String, // "$userId:$date"
    val userId: String,
    val date: String,
    val sleepMinutes: Int = 0,
    val steps: Int = 0,
    val exerciseMinutes: Int = 0,
    val source: String = "manuel", // health_connect | manuel
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

    @Query("SELECT COUNT(*) FROM tasks WHERE userId = :userId AND date = :date AND deleted = 0 AND isSport = 0 AND goalId IS NULL")
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

    @Query("SELECT * FROM profiles WHERE id != :myId LIMIT 1")
    suspend fun partnerOf(myId: String): ProfileEntity?

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

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals WHERE deleted = 0 ORDER BY active DESC, updatedAt DESC")
    fun all(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE userId = :userId AND active = 1 AND deleted = 0")
    suspend fun activeOnce(userId: String): List<GoalEntity>

    @Query("SELECT COUNT(*) FROM goals WHERE userId = :userId AND active = 1 AND deleted = 0")
    suspend fun countActive(userId: String): Int

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun byId(id: String): GoalEntity?

    @Query("SELECT * FROM goals WHERE updatedAt > :ts")
    suspend fun modifiedSince(ts: Long): List<GoalEntity>

    @Query("UPDATE goals SET userId = :newId, updatedAt = :now WHERE userId = :oldId")
    suspend fun migrateUser(oldId: String, newId: String, now: Long)

    @Upsert
    suspend fun upsert(goal: GoalEntity)
}

@Dao
interface RitualDao {
    @Query("SELECT * FROM ritual_steps WHERE userId = :userId ORDER BY position")
    fun steps(userId: String): Flow<List<RitualStepEntity>>

    @Query("SELECT * FROM ritual_steps WHERE userId = :userId ORDER BY position")
    suspend fun stepsOnce(userId: String): List<RitualStepEntity>

    @Query("DELETE FROM ritual_steps WHERE id = :id")
    suspend fun deleteStep(id: String)

    @Upsert
    suspend fun upsertStep(step: RitualStepEntity)

    @Query("SELECT * FROM ritual_logs WHERE deleted = 0 ORDER BY date DESC LIMIT 90")
    fun logs(): Flow<List<RitualLogEntity>>

    @Query("SELECT * FROM ritual_logs WHERE id = :id")
    suspend fun logById(id: String): RitualLogEntity?

    @Query("SELECT * FROM ritual_logs WHERE updatedAt > :ts")
    suspend fun logsModifiedSince(ts: Long): List<RitualLogEntity>

    @Upsert
    suspend fun upsertLog(log: RitualLogEntity)
}

@Dao
interface InboxDao {
    @Query("SELECT * FROM inbox_items WHERE userId = :userId AND processed = 0 AND deleted = 0 ORDER BY updatedAt")
    fun pending(userId: String): Flow<List<InboxItemEntity>>

    @Query("SELECT COUNT(*) FROM inbox_items WHERE userId = :userId AND processed = 0 AND deleted = 0")
    fun pendingCount(userId: String): Flow<Int>

    @Query("SELECT * FROM inbox_items WHERE id = :id")
    suspend fun byId(id: String): InboxItemEntity?

    @Upsert
    suspend fun upsert(item: InboxItemEntity)
}

@Dao
interface UsageDao {
    @Query("SELECT * FROM usage_days WHERE date >= :fromDate AND deleted = 0 ORDER BY date")
    fun since(fromDate: String): Flow<List<UsageDayEntity>>

    @Query("SELECT * FROM usage_days WHERE id = :id")
    suspend fun byId(id: String): UsageDayEntity?

    @Query("SELECT * FROM usage_days WHERE updatedAt > :ts")
    suspend fun modifiedSince(ts: Long): List<UsageDayEntity>

    @Upsert
    suspend fun upsert(day: UsageDayEntity)
}

@Dao
interface GraceDao {
    @Query("SELECT * FROM grace_requests WHERE date = :date AND deleted = 0 ORDER BY updatedAt DESC")
    fun forDate(date: String): Flow<List<GraceRequestEntity>>

    @Query("SELECT * FROM grace_requests WHERE fromUser = :userId AND date = :date AND status = 'granted' AND deleted = 0 ORDER BY updatedAt DESC LIMIT 1")
    suspend fun lastGranted(userId: String, date: String): GraceRequestEntity?

    @Query("SELECT * FROM grace_requests WHERE id = :id")
    suspend fun byId(id: String): GraceRequestEntity?

    @Query("SELECT * FROM grace_requests WHERE updatedAt > :ts")
    suspend fun modifiedSince(ts: Long): List<GraceRequestEntity>

    @Upsert
    suspend fun upsert(request: GraceRequestEntity)
}

@Dao
interface MealDao {
    @Query("SELECT * FROM meals WHERE date >= :from AND date <= :to AND deleted = 0 ORDER BY date, slot")
    fun between(from: String, to: String): Flow<List<MealEntity>>

    @Query("SELECT * FROM meals WHERE date >= :from AND date <= :to AND deleted = 0")
    suspend fun betweenOnce(from: String, to: String): List<MealEntity>

    @Query("SELECT * FROM meals WHERE id = :id")
    suspend fun byId(id: String): MealEntity?

    @Query("SELECT * FROM meals WHERE updatedAt > :ts")
    suspend fun modifiedSince(ts: Long): List<MealEntity>

    @Upsert
    suspend fun upsert(meal: MealEntity)
}

@Dao
interface ShoppingDao {
    @Query("SELECT * FROM shopping_items WHERE weekStart = :weekStart AND deleted = 0 ORDER BY aisle, label")
    fun forWeek(weekStart: String): Flow<List<ShoppingItemEntity>>

    @Query("SELECT * FROM shopping_items WHERE weekStart = :weekStart AND deleted = 0")
    suspend fun forWeekOnce(weekStart: String): List<ShoppingItemEntity>

    @Query("SELECT * FROM shopping_items WHERE id = :id")
    suspend fun byId(id: String): ShoppingItemEntity?

    @Query("SELECT * FROM shopping_items WHERE updatedAt > :ts")
    suspend fun modifiedSince(ts: Long): List<ShoppingItemEntity>

    @Upsert
    suspend fun upsert(item: ShoppingItemEntity)
}

@Dao
interface HealthDao {
    @Query("SELECT * FROM health_days WHERE date >= :fromDate AND deleted = 0 ORDER BY date")
    fun since(fromDate: String): Flow<List<HealthDayEntity>>

    @Query("SELECT * FROM health_days WHERE id = :id")
    suspend fun byId(id: String): HealthDayEntity?

    @Query("SELECT * FROM health_days WHERE updatedAt > :ts")
    suspend fun modifiedSince(ts: Long): List<HealthDayEntity>

    @Upsert
    suspend fun upsert(day: HealthDayEntity)
}

@Database(
    entities = [
        TaskEntity::class, DayPlanEntity::class, WeekPlanEntity::class,
        ProfileEntity::class, EncouragementEntity::class,
        GoalEntity::class, RitualStepEntity::class, RitualLogEntity::class,
        InboxItemEntity::class, UsageDayEntity::class, GraceRequestEntity::class,
        MealEntity::class, ShoppingItemEntity::class, HealthDayEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDb : RoomDatabase() {
    abstract fun tasks(): TaskDao
    abstract fun dayPlans(): DayPlanDao
    abstract fun weekPlans(): WeekPlanDao
    abstract fun profiles(): ProfileDao
    abstract fun encouragements(): EncouragementDao
    abstract fun goals(): GoalDao
    abstract fun ritual(): RitualDao
    abstract fun inbox(): InboxDao
    abstract fun usage(): UsageDao
    abstract fun grace(): GraceDao
    abstract fun meals(): MealDao
    abstract fun shopping(): ShoppingDao
    abstract fun health(): HealthDao

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
