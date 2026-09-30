package com.notresemaine.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.notresemaine.app.MainActivity
import com.notresemaine.app.R
import com.notresemaine.app.data.Dates
import com.notresemaine.app.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * Le widget « Ma journée ».
 *
 * L'application sert surtout à répondre à une question — « qu'est-ce que je
 * fais maintenant ? » — et l'ouvrir pour ça, c'est déjà trop de gestes. Le
 * widget y répond depuis l'écran d'accueil : la priorité, ce qui vient
 * ensuite, où en est la journée. Et trois boutons pour ce qu'on fait sans
 * vouloir « ouvrir l'application » : noter un repas, vider sa tête, se
 * concentrer.
 *
 * Il se met à jour quand une tâche change dans l'application, après chaque
 * synchronisation, et au pire toutes les 30 minutes.
 */
class TodayWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        scope.launch {
            try {
                render(context, manager, ids)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** Redessine tous les widgets posés ; sans effet s'il n'y en a aucun. */
        fun refresh(context: Context) {
            val app = context.applicationContext
            scope.launch {
                runCatching {
                    val manager = AppWidgetManager.getInstance(app)
                    val ids = manager.getAppWidgetIds(ComponentName(app, TodayWidget::class.java))
                    if (ids.isNotEmpty()) render(app, manager, ids)
                }
            }
        }

        private suspend fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val views = runCatching { build(context) }.getOrElse {
                RemoteViews(context.packageName, R.layout.widget_today).apply {
                    setTextViewText(R.id.w_priority, "Notre Semaine")
                    setTextViewText(R.id.w_next, "Touchez pour ouvrir")
                }
            }
            ids.forEach { id -> runCatching { manager.updateAppWidget(id, views) } }
        }

        private suspend fun build(context: Context): RemoteViews {
            val repo = Repository.get(context)
            val s = repo.settings.current()
            val today = Dates.todayIso()
            val views = RemoteViews(context.packageName, R.layout.widget_today)

            views.setTextViewText(R.id.w_date, "Aujourd'hui · " + Dates.longLabel(today))

            if (!s.onboarded || s.myUserId.isBlank()) {
                views.setTextViewText(R.id.w_priority, "Bienvenue")
                views.setTextViewText(R.id.w_next, "Ouvrez l'application pour commencer")
                views.setTextViewText(R.id.w_progress, "")
            } else {
                val tasks = repo.db.tasks().byDateOnce(s.myUserId, today)
                val open = tasks.filter { !it.done }
                val priority = tasks.firstOrNull { it.isPriority }
                val nowHm = LocalTime.now().let { "%02d:%02d".format(it.hour, it.minute) }

                views.setTextViewText(
                    R.id.w_priority,
                    when {
                        priority == null && tasks.isEmpty() -> "Rien de prévu"
                        priority == null -> "${open.size} chose(s) à faire"
                        priority.done -> "⭐ ${priority.title} ✓"
                        else -> "⭐ ${priority.title}"
                    }
                )

                val next = open
                    .filter { it.startTime.isNotBlank() && it.startTime >= nowHm }
                    .minByOrNull { it.startTime }
                views.setTextViewText(
                    R.id.w_next,
                    when {
                        s.focusActive() -> {
                            val end = java.time.Instant.ofEpochMilli(s.focusUntil)
                                .atZone(java.time.ZoneId.systemDefault()).toLocalTime()
                            "🎧 Concentration jusqu'à %02d:%02d".format(end.hour, end.minute)
                        }
                        next != null -> "Ensuite · ${next.startTime} ${next.title}"
                        tasks.isEmpty() -> "Touchez pour choisir ce qui compte"
                        open.isEmpty() -> "Tout est fait pour aujourd'hui"
                        else -> "Reste : " + open.filter { !it.isPriority }.take(2)
                            .joinToString(", ") { it.title }.ifBlank { "la priorité" }
                    }
                )

                val meals = repo.db.mealLogs().betweenOnce(s.myUserId, today, today)
                    .count { !it.deleted }
                val done = tasks.count { it.done }
                views.setTextViewText(
                    R.id.w_progress,
                    buildString {
                        if (tasks.isNotEmpty()) append("$done/${tasks.size} fait")
                        if (isNotEmpty()) append(" · ")
                        append(
                            when (meals) {
                                0 -> "aucun repas noté"
                                1 -> "1 repas noté"
                                else -> "$meals repas notés"
                            }
                        )
                    }
                )
            }

            views.setOnClickPendingIntent(R.id.w_root, open(context, "today", 0))
            views.setOnClickPendingIntent(R.id.w_meal, open(context, "meallog", 1))
            views.setOnClickPendingIntent(R.id.w_capture, open(context, "capture", 2))
            views.setOnClickPendingIntent(R.id.w_focus, open(context, "focus", 3))
            return views
        }

        private fun open(context: Context, route: String, code: Int): PendingIntent =
            PendingIntent.getActivity(
                context,
                7700 + code,
                Intent(context, MainActivity::class.java)
                    .setAction("widget.$route")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(MainActivity.EXTRA_ROUTE, route),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
    }
}
