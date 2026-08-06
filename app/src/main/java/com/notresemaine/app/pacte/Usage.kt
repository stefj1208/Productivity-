package com.notresemaine.app.pacte

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
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

    /** Application actuellement au premier plan (approximation via les derniers événements). */
    fun foregroundPackage(context: Context): String? {
        val usm = context.getSystemService(UsageStatsManager::class.java)
        val end = System.currentTimeMillis()
        val events = usm.queryEvents(end - 10_000, end)
        val event = UsageEvents.Event()
        var latest: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) latest = event.packageName
        }
        return latest
    }

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
