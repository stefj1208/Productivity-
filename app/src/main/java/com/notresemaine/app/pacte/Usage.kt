package com.notresemaine.app.pacte

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import android.provider.Settings
import java.time.LocalDate
import java.time.ZoneId

data class DayUsage(val totalMinutes: Int, val socialMinutes: Int, val unlocks: Int)

data class InstalledApp(val packageName: String, val label: String)

/** Lecture du temps d'écran via UsageStatsManager (permission « Accès aux données d'utilisation »). */
object Usage {

    /** Applications généralement considérées comme réseaux sociaux, précochées dans la liste. */
    val KNOWN_SOCIAL = setOf(
        "com.instagram.android", "com.zhiliaoapp.musically", "com.ss.android.ugc.trill",
        "com.facebook.katana", "com.snapchat.android", "com.twitter.android",
        "com.google.android.youtube", "com.reddit.frontpage", "com.pinterest",
        "com.linkedin.android", "com.facebook.orca", "org.telegram.messenger",
        "com.whatsapp"
    )

    fun hasPermission(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun startOfToday(): Long =
        LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun today(context: Context, socialPackages: Set<String>): DayUsage {
        val usm = context.getSystemService(UsageStatsManager::class.java)
        val start = startOfToday()
        val end = System.currentTimeMillis()

        val stats = usm.queryAndAggregateUsageStats(start, end)
        var totalMs = 0L
        var socialMs = 0L
        stats.forEach { (pkg, s) ->
            totalMs += s.totalTimeInForeground
            if (pkg in socialPackages) socialMs += s.totalTimeInForeground
        }

        var unlocks = 0
        val events = usm.queryEvents(start, end)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.KEYGUARD_HIDDEN) unlocks++
        }
        return DayUsage((totalMs / 60_000).toInt(), (socialMs / 60_000).toInt(), unlocks)
    }

    /**
     * Application actuellement au premier plan.
     *
     * On remonte loin dans l'historique des événements, et c'est le point clé :
     * Android n'émet un événement qu'au *changement* d'application. Quelqu'un
     * qui fait défiler Instagram depuis dix minutes n'a produit aucun événement
     * récent. L'ancienne version ne regardait que les 10 dernières secondes :
     * elle rendait « rien au premier plan » et le blocage ne partait jamais
     * pendant qu'on scrollait — exactement le moment où il sert.
     */
    fun foregroundPackage(context: Context): String? {
        val usm = context.getSystemService(UsageStatsManager::class.java)
        val end = System.currentTimeMillis()
        // Quatre heures couvrent une très longue session ; à défaut, la journée.
        return lastResumed(usm, end - 4 * 60 * 60_000L, end)
            ?: lastResumed(usm, minOf(startOfToday(), end - 24 * 60 * 60_000L), end)
    }

    private fun lastResumed(usm: UsageStatsManager, from: Long, to: Long): String? {
        val events = usm.queryEvents(from, to)
        val event = UsageEvents.Event()
        var latest: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> latest = event.packageName
                // Application quittée (y compris quand on verrouille) : plus rien devant.
                UsageEvents.Event.ACTIVITY_STOPPED ->
                    if (event.packageName == latest) latest = null
            }
        }
        return latest
    }

    /**
     * « Afficher par-dessus les autres applications ». Sans elle, Android
     * interdit à un service d'ouvrir un écran : le blocage part dans le vide.
     */
    fun canOverlay(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun overlaySettingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )

    /** Applications installées lançables, pour choisir la liste « réseaux sociaux ». */
    fun launchableApps(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == context.packageName) return@mapNotNull null
                InstalledApp(pkg, info.loadLabel(pm).toString())
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
}
