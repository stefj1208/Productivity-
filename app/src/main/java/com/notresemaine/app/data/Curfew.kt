package com.notresemaine.app.data

import java.time.LocalTime

/** Couvre-feu : plage horaire qui peut traverser minuit (ex. 22:30 → 06:30). */
object Curfew {

    fun isActive(settings: AppSettings, now: LocalTime = LocalTime.now()): Boolean {
        if (!settings.curfewEnabled) return false
        val start = parse(settings.curfewStart) ?: return false
        val end = parse(settings.curfewEnd) ?: return false
        return if (start <= end) {
            now >= start && now < end
        } else {
            now >= start || now < end // la plage passe minuit
        }
    }

    private fun parse(text: String): LocalTime? =
        runCatching { LocalTime.parse(text) }.getOrNull()

    /** « Couvre-feu de 22:30 à 06:30 » */
    fun label(settings: AppSettings): String =
        "de ${settings.curfewStart} à ${settings.curfewEnd}"

    /**
     * Un engagement ne se relâche pas dans l'instant : plus strict = tout de suite,
     * plus permissif = demain. C'est le cœur du Pacte.
     */
    fun isLooser(
        current: AppSettings,
        newLimit: Int,
        newCurfewEnabled: Boolean,
        newStart: String,
        newEnd: String
    ): Boolean {
        if (newLimit > current.dailyLimitMinutes) return true
        if (current.curfewEnabled && !newCurfewEnabled) return true
        if (current.curfewEnabled && newCurfewEnabled) {
            // Repousser le début du couvre-feu, ou l'écourter le matin, est un relâchement.
            val curStart = parse(current.curfewStart)
            val curEnd = parse(current.curfewEnd)
            val nStart = parse(newStart)
            val nEnd = parse(newEnd)
            if (curStart != null && nStart != null && nStart > curStart) return true
            if (curEnd != null && nEnd != null && nEnd < curEnd) return true
        }
        return false
    }
}
