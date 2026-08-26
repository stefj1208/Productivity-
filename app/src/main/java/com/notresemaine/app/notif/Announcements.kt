package com.notresemaine.app.notif

import android.content.Context
import com.notresemaine.app.data.Dates
import com.notresemaine.app.data.Repository

/**
 * Ce que le binôme vient de faire, annoncé en plein écran.
 *
 * Deux choses arrivent de l'autre téléphone et ne peuvent pas attendre qu'on
 * pense à ouvrir l'application : une tâche qu'on vous confie, et une demande de
 * minutes supplémentaires. Les découvrir le lendemain revient à ne pas les
 * recevoir du tout.
 *
 * Le code vit ici, et non dans l'écran, parce qu'il doit s'exécuter **après
 * chaque synchronisation** — celle déclenchée par l'application ouverte comme
 * celle du travail de fond horaire. Tant qu'il était rangé dans le modèle de vue,
 * la synchronisation d'arrière-plan n'annonçait rien.
 */
object Announcements {

    suspend fun afterSync(context: Context, repo: Repository) {
        val s = repo.settings.current()
        if (!s.alertsEnabled || s.myUserId.isBlank()) return
        announceAssignedTasks(context, repo)
        announceGraceRequests(context, repo)
    }

    /**
     * Une tâche confiée par l'autre.
     *
     * Confier quelque chose à quelqu'un sans qu'il le sache, ce n'est pas le lui
     * confier : c'est l'espérer. D'où l'écran plein, avec le bouton qui mène là
     * où l'on peut lui réserver un créneau.
     */
    private suspend fun announceAssignedTasks(context: Context, repo: Repository) {
        val s = repo.settings.current()
        val fresh = repo.unannouncedAssignedTasks(s.myUserId)
        if (fresh.isEmpty()) return
        val partner = repo.db.profiles().partnerOf(s.myUserId)?.name ?: "Ton binôme"
        fresh.forEach { task ->
            Alarms.fire(
                context = context,
                emoji = "🤝",
                title = task.title,
                text = "$partner vient de te confier cette tâche.",
                sound = false,
                nudge = true,
                actionRoute = "day/${task.date ?: Dates.todayIso()}",
                actionLabel = "Bloquer un créneau"
            )
        }
        repo.markAssignedAnnounced(fresh.map { it.id })
    }

    /**
     * Une demande de minutes supplémentaires.
     *
     * C'est le seul cas où l'écran de rappel *décide* quelque chose au lieu de
     * renvoyer ailleurs : accorder une pause depuis un menu qu'il faut aller
     * chercher, c'est faire attendre quelqu'un qui est bloqué maintenant. Deux
     * boutons, une réponse, terminé.
     */
    private suspend fun announceGraceRequests(context: Context, repo: Repository) {
        val s = repo.settings.current()
        val known = s.alertedGraceIds.split(",").filter { it.isNotBlank() }.toSet()
        val pending = repo.db.grace().pendingForMe(s.myUserId).filter { it.id !in known }
        if (pending.isEmpty()) return
        val partner = repo.db.profiles().partnerOf(s.myUserId)?.name ?: "Ton binôme"
        pending.forEach { request ->
            Alarms.fire(
                context = context,
                emoji = "🙏",
                title = "$partner demande ${request.minutes} min",
                text = "Sa limite d'écran est atteinte. Tu es le seul à pouvoir " +
                    "ouvrir une pause — ou à dire non, c'était le pacte.",
                sound = false,
                nudge = true,
                actionLabel = "Accorder ${request.minutes} min",
                graceId = request.id,
                graceMinutes = request.minutes
            )
        }
        repo.markGraceAnnounced(pending.map { it.id })
    }
}
