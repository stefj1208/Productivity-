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
import androidx.room.migration.Migration
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
    val assignedBy: String = "", // identifiant de celui qui a confié la tâche, sinon vide
    val startTime: String = "",  // HH:MM ; vide = pas de créneau réservé
    val durationMinutes: Int = 0,
    val deleted: Boolean = false,
    val updatedAt: Long
)

/**
 * Ce qui se gère à deux, hors repas : l'argent et les enfants.
 * Une seule table, une colonne [section], parce que ce sont les mêmes gestes —
 * noter, dater, cocher — appliqués à deux sujets différents.
 */
@Entity(tableName = "house_items")
data class HouseItemEntity(
    @PrimaryKey val id: String,
    val section: String,          // finance | enfants
    val title: String,
    val detail: String = "",
    val amount: Double = 0.0,     // montant pour la finance ; 0 = sans montant
    val dueDate: String? = null,  // yyyy-MM-dd
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
    // Réglages du Pacte, partagés : chacun voit l'engagement de l'autre.
    val pacteEnabled: Boolean = false,
    val dailyLimitMinutes: Int = 45,
    val curfewEnabled: Boolean = false,
    val curfewStart: String = "22:30",
    val curfewEnd: String = "06:30",
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
    /** Ce qu'on fait pendant l'étape, écrit par soi. Vide = la fiche par défaut. */
    val detail: String = "",
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

/** Repas du couple : un matin, un midi et un soir par jour, partagés. */
@Entity(tableName = "meals")
data class MealEntity(
    @PrimaryKey val id: String, // "$date:$slot"
    val userId: String,         // qui l'a saisi (pour la synchro)
    val date: String,
    val slot: String,           // matin | midi | soir
    val title: String,
    val ingredients: String,    // "200 g farine, 3 œufs, 1 L lait"
    /** Ce qu'il faut dans l'assiette de chacun : « 150 g de pâtes, 120 g de saumon ». */
    val quantities: String = "",
    /** Calories par personne. 0 = non renseigné, jamais affiché comme un zéro. */
    val calories: Int = 0,
    /**
     * Macronutriments d'une portion, en grammes — renseignés par l'assistant quand
     * il compose le menu. Ils suivent le plat lorsqu'on coche « j'ai mangé ça » :
     * sans eux, le chemin le plus emprunté ne dirait rien de la nutrition.
     */
    val protein: Int = 0,
    val carbs: Int = 0,
    val fat: Int = 0,
    val fiber: Int = 0,
    val deleted: Boolean = false,
    val updatedAt: Long
)

/**
 * Ce qui a été **réellement** mangé — à ne pas confondre avec [MealEntity], qui est
 * le menu **prévu**.
 *
 * Les deux tables existent séparément parce que l'écart entre les deux est
 * précisément l'information utile : un menu qu'on remplacerait par ce qu'on a
 * vraiment mangé perdrait toute trace de ce qui était décidé, et donc toute
 * possibilité de dire « le menu tient quatre jours sur sept, et ça lâche le soir ».
 *
 * Contrairement au menu, il peut y en avoir plusieurs par créneau : un déjeuner,
 * puis un goûter. D'où un identifiant libre plutôt que « date:créneau ».
 *
 * Les calories sont une **fourchette**, jamais un chiffre juste : une photo ne dit
 * pas si l'assiette fait 22 ou 28 cm. [caloriesLow] et [caloriesHigh] encadrent,
 * [calories] est le milieu — celui qu'on additionne, faute de mieux.
 */
@Entity(tableName = "meal_logs")
data class MealLogEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val date: String,
    val slot: String,             // matin | midi | soir | encas
    val title: String,
    val detail: String = "",      // les aliments repérés, séparés par des virgules
    val calories: Int = 0,
    val caloriesLow: Int = 0,
    val caloriesHigh: Int = 0,
    val source: String = "manuel", // photo | manuel | menu | jeune
    /**
     * L'heure du repas, « HH:mm ».
     *
     * Elle n'est pas décorative : c'est elle, et rien d'autre, qui permet de dire
     * combien d'heures séparent deux repas — donc de mesurer un jeûne. Sans elle,
     * on saurait qu'on a sauté le déjeuner sans pouvoir dire combien de temps.
     */
    val time: String = "",
    /**
     * Les macronutriments d'une portion, en grammes.
     *
     * Les calories disent combien ; celles-ci disent quoi. Deux repas à 700 kcal
     * n'ont rien à voir selon qu'ils apportent 40 g de protéines ou 3. Zéro
     * signifie « inconnu », jamais « aucun » : les moyennes ne comptent donc que
     * les repas renseignés.
     */
    val protein: Int = 0,
    val carbs: Int = 0,
    val fat: Int = 0,
    val fiber: Int = 0,
    val deleted: Boolean = false,
    val updatedAt: Long
)

/**
 * Une habitude qui fait la différence : « ne pas grignoter entre les repas »,
 * « une seule chose à la fois ». Elle ne se coche pas — elle se rappelle, à des
 * moments imprévisibles, parce qu'un rappel toujours à la même heure devient un
 * meuble qu'on ne voit plus.
 */
@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val source: String = "",     // le livre ou la raison, en une ligne
    val enabled: Boolean = true,
    val fromHour: Int = 8,       // pas de rappel avant
    val toHour: Int = 21,        // ni après
    val perDay: Int = 2,         // combien de fois par jour, au hasard
    val deleted: Boolean = false,
    val updatedAt: Long
)

/**
 * Une pesée. Donnée de santé, donc personnelle par défaut : le partenaire ne
 * la voit que si son propriétaire a coché le partage dans son profil.
 */
@Entity(tableName = "weights")
data class WeightEntity(
    @PrimaryKey val id: String, // "$userId:$date"
    val userId: String,
    val date: String,
    val kilos: Double,
    val note: String = "",
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

    @Query(
        "SELECT * FROM tasks WHERE userId = :userId AND assignedBy != '' AND assignedBy != :userId " +
            "AND done = 0 AND deleted = 0 ORDER BY updatedAt DESC LIMIT 5"
    )
    suspend fun assignedToMe(userId: String): List<TaskEntity>

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

    @Query("SELECT * FROM inbox_items WHERE userId = :userId AND processed = 0 AND deleted = 0 ORDER BY updatedAt")
    suspend fun pendingOnce(userId: String): List<InboxItemEntity>

    @Query("SELECT COUNT(*) FROM inbox_items WHERE userId = :userId AND processed = 0 AND deleted = 0")
    fun pendingCount(userId: String): Flow<Int>

    @Query("SELECT * FROM inbox_items WHERE userId = :userId AND deleted = 0 ORDER BY updatedAt DESC")
    fun allOf(userId: String): Flow<List<InboxItemEntity>>

    @Query("SELECT * FROM inbox_items WHERE id = :id")
    suspend fun byId(id: String): InboxItemEntity?

    @Upsert
    suspend fun upsert(item: InboxItemEntity)
}

@Dao
interface UsageDao {
    @Query("SELECT * FROM usage_days WHERE date >= :fromDate AND deleted = 0 ORDER BY date")
    fun since(fromDate: String): Flow<List<UsageDayEntity>>

    @Query("SELECT * FROM usage_days WHERE userId = :userId AND date >= :fromDate AND deleted = 0 ORDER BY date")
    suspend fun sinceOnce(userId: String, fromDate: String): List<UsageDayEntity>

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
interface MealLogDao {
    @Query("SELECT * FROM meal_logs WHERE date >= :from AND date <= :to AND deleted = 0 ORDER BY date, slot, updatedAt")
    fun between(from: String, to: String): Flow<List<MealLogEntity>>

    @Query("SELECT * FROM meal_logs WHERE userId = :userId AND date >= :from AND date <= :to AND deleted = 0")
    suspend fun betweenOnce(userId: String, from: String, to: String): List<MealLogEntity>

    @Query("SELECT * FROM meal_logs WHERE id = :id")
    suspend fun byId(id: String): MealLogEntity?

    @Query("SELECT * FROM meal_logs WHERE updatedAt > :ts")
    suspend fun modifiedSince(ts: Long): List<MealLogEntity>

    @Upsert
    suspend fun upsert(log: MealLogEntity)
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
interface HouseItemDao {
    @Query("SELECT * FROM house_items WHERE deleted = 0 ORDER BY done, dueDate, updatedAt DESC")
    fun all(): Flow<List<HouseItemEntity>>

    @Query("SELECT * FROM house_items WHERE section = :section AND deleted = 0 ORDER BY done, dueDate, updatedAt DESC")
    fun bySection(section: String): Flow<List<HouseItemEntity>>

    @Query("SELECT * FROM house_items WHERE id = :id")
    suspend fun byId(id: String): HouseItemEntity?

    @Query("SELECT * FROM house_items WHERE updatedAt > :ts")
    suspend fun modifiedSince(ts: Long): List<HouseItemEntity>

    @Upsert
    suspend fun upsert(item: HouseItemEntity)
}

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits WHERE deleted = 0 ORDER BY enabled DESC, updatedAt")
    fun all(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE userId = :userId AND enabled = 1 AND deleted = 0")
    suspend fun activeOnce(userId: String): List<HabitEntity>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun byId(id: String): HabitEntity?

    @Query("SELECT * FROM habits WHERE updatedAt > :ts")
    suspend fun modifiedSince(ts: Long): List<HabitEntity>

    @Query("UPDATE habits SET userId = :newId, updatedAt = :now WHERE userId = :oldId")
    suspend fun migrateUser(oldId: String, newId: String, now: Long)

    @Upsert
    suspend fun upsert(habit: HabitEntity)
}

@Dao
interface WeightDao {
    @Query("SELECT * FROM weights WHERE deleted = 0 ORDER BY date")
    fun all(): Flow<List<WeightEntity>>

    @Query("SELECT * FROM weights WHERE userId = :userId AND deleted = 0 ORDER BY date DESC LIMIT :limit")
    suspend fun lastOnce(userId: String, limit: Int): List<WeightEntity>

    @Query("SELECT * FROM weights WHERE id = :id")
    suspend fun byId(id: String): WeightEntity?

    @Query("SELECT * FROM weights WHERE updatedAt > :ts")
    suspend fun modifiedSince(ts: Long): List<WeightEntity>

    @Query("UPDATE weights SET userId = :newId, id = :newId || ':' || date, updatedAt = :now WHERE userId = :oldId")
    suspend fun migrateUser(oldId: String, newId: String, now: Long)

    @Upsert
    suspend fun upsert(entry: WeightEntity)
}

@Dao
interface HealthDao {
    @Query("SELECT * FROM health_days WHERE date >= :fromDate AND deleted = 0 ORDER BY date")
    fun since(fromDate: String): Flow<List<HealthDayEntity>>

    @Query("SELECT * FROM health_days WHERE userId = :userId AND date >= :fromDate AND date <= :toDate AND deleted = 0 ORDER BY date")
    suspend fun betweenOnce(userId: String, fromDate: String, toDate: String): List<HealthDayEntity>

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
        MealEntity::class, ShoppingItemEntity::class, HealthDayEntity::class,
        HouseItemEntity::class, WeightEntity::class, HabitEntity::class,
        MealLogEntity::class
    ],
    version = 11,
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
    abstract fun mealLogs(): MealLogDao
    abstract fun shopping(): ShoppingDao
    abstract fun health(): HealthDao
    abstract fun houseItems(): HouseItemDao
    abstract fun weights(): WeightDao
    abstract fun habits(): HabitDao

    companion object {
        /**
         * Passages d'une version de base à la suivante, **sans rien effacer**.
         *
         * Jusqu'ici la base était simplement remise à zéro à chaque changement
         * de structure : ce qui n'était pas synchronisé disparaissait. À partir
         * de la version 7, chaque évolution s'écrit ici.
         *
         * La règle pour la suite : ajouter une colonne se fait toujours en
         * `ALTER TABLE … ADD COLUMN … NOT NULL DEFAULT …`, et une nouvelle table
         * en `CREATE TABLE IF NOT EXISTS`, avec exactement les types que Room
         * attend — sinon Room refuse d'ouvrir la base au démarrage suivant.
         */
        private val MIGRATIONS: Array<Migration> = arrayOf(
            // 7 → 8 : chaque étape du rituel peut porter sa propre consigne.
            object : Migration(7, 8) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE ritual_steps ADD COLUMN detail TEXT NOT NULL DEFAULT ''")
                }
            },
            // 8 → 9 : ce qu'on a réellement mangé, à côté de ce qui était prévu.
            object : Migration(8, 9) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS meal_logs (" +
                            "id TEXT NOT NULL PRIMARY KEY, " +
                            "userId TEXT NOT NULL, " +
                            "date TEXT NOT NULL, " +
                            "slot TEXT NOT NULL, " +
                            "title TEXT NOT NULL, " +
                            "detail TEXT NOT NULL DEFAULT '', " +
                            "calories INTEGER NOT NULL DEFAULT 0, " +
                            "caloriesLow INTEGER NOT NULL DEFAULT 0, " +
                            "caloriesHigh INTEGER NOT NULL DEFAULT 0, " +
                            "source TEXT NOT NULL DEFAULT 'manuel', " +
                            "deleted INTEGER NOT NULL DEFAULT 0, " +
                            "updatedAt INTEGER NOT NULL)"
                    )
                }
            },
            // 9 → 10 : l'heure du repas, sans laquelle aucun jeûne n'est mesurable.
            object : Migration(9, 10) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE meal_logs ADD COLUMN time TEXT NOT NULL DEFAULT ''")
                }
            },
            // 10 → 11 : les macronutriments. Les calories disent combien, elles
            // disent quoi — deux repas à 700 kcal n'ont rien à voir.
            object : Migration(10, 11) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    listOf("meal_logs", "meals").forEach { table ->
                        listOf("protein", "carbs", "fat", "fiber").forEach { column ->
                            db.execSQL(
                                "ALTER TABLE $table ADD COLUMN $column INTEGER NOT NULL DEFAULT 0"
                            )
                        }
                    }
                }
            }
        )

        @Volatile private var instance: AppDb? = null
        fun get(context: Context): AppDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDb::class.java, "notre_semaine.db")
                .addMigrations(*MIGRATIONS)
                // Filet de sécurité : si une migration manque, mieux vaut une base
                // vide qu'une application qui refuse de démarrer. La synchronisation
                // Supabase reste alors le seul moyen de retrouver ses données.
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
        }
    }
}
