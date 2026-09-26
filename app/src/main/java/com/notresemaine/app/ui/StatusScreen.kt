package com.notresemaine.app.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.notresemaine.app.data.AppSettings
import com.notresemaine.app.data.Dates
import com.notresemaine.app.health.Health
import com.notresemaine.app.pacte.BlockerService
import com.notresemaine.app.pacte.Usage

/**
 * « Est-ce que tout marche ? » — la question qui a coûté le plus de temps.
 *
 * Chaque fois qu'une mesure restait vide, il a fallu deviner : autorisation
 * retirée ? synchronisation en panne ? assistant éteint ? fonction jamais
 * activée ? L'application savait à chaque fois la réponse, mais ne la disait
 * nulle part.
 *
 * Cet écran la dit. Une ligne par rouage, un ✅ ou un ⚠️, et pour chaque ⚠️ le
 * bouton qui mène exactement là où ça se règle. Il ne modifie rien de lui-même :
 * il constate et il oriente. C'est volontaire — un écran de diagnostic qui
 * répare tout seul finit par réparer ce qu'on ne voulait pas.
 */
@Composable
fun StatusScreen(
    vm: AppViewModel,
    settings: AppSettings,
    onSync: () -> Unit,
    onAssistant: () -> Unit,
    onScreenTime: () -> Unit,
    onHealth: () -> Unit,
    onReminders: () -> Unit,
    onCalendar: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val syncStatus by vm.syncStatus.collectAsState()

    // Ces trois états se lisent auprès du système, pas de la base : c'est
    // précisément là que les choses se défont sans prévenir (autorisation
    // retirée par Android, service tué par l'économiseur de batterie).
    val healthOk by produceState(initialValue = false, context) {
        value = runCatching { Health.hasPermissions(context) }.getOrDefault(false)
    }
    val usageOk = remember(context) { runCatching { Usage.hasPermission(context) }.getOrDefault(false) }
    val overlayOk = remember(context) { runCatching { Usage.canOverlay(context) }.getOrDefault(false) }
    val notifOk = remember(context) { canPostNotifications(context) }

    val profiles by remember { vm.repo.db.profiles().all() }.collectAsState(initial = emptyList())
    val partner = profiles.firstOrNull { it.id != settings.myUserId }

    val syncConfigured = settings.supabaseUrl.isNotBlank() && settings.supabaseKey.isNotBlank()
    val syncSignedIn = settings.refreshToken.isNotBlank()
    val syncPaired = settings.coupleCode.isNotBlank()
    val lastSync = settings.lastPullTs

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            ScreenHeader(
                title = "🩺 Tout fonctionne ?",
                subtitle = "Ce qui tourne, et ce qui manque",
                onBack = onBack
            )

            Spacer(Modifier.height(12.dp))
            SectionLabel("À DEUX")
            StatusLine(
                ok = syncConfigured && syncSignedIn && syncPaired,
                label = when {
                    !syncConfigured -> "Synchronisation non configurée — l'application marche, mais seule sur ce téléphone"
                    !syncSignedIn -> "Compte non connecté : rien ne part ni n'arrive"
                    !syncPaired -> "Espace du couple non rejoint : vos deux téléphones s'ignorent"
                    else -> "Reliée à ${settings.authEmail}"
                },
                actionLabel = if (syncConfigured && syncSignedIn && syncPaired) null else "Configurer",
                onAction = onSync
            )
            StatusLine(
                ok = partner != null,
                label = if (partner != null) "Binôme visible : ${partner.name}"
                else "Aucun binôme reçu pour l'instant — il apparaîtra à la première synchronisation réussie",
                actionLabel = if (partner == null) "Voir" else null,
                onAction = onSync
            )
            StatusLine(
                ok = lastSync > 0,
                label = if (lastSync > 0) "Dernier échange : ${ago(lastSync)}"
                else "Aucun échange réussi jusqu'ici",
                actionLabel = "Synchroniser",
                onAction = { vm.syncNowManual() }
            )
            if (syncStatus.isNotBlank()) {
                Text(
                    text = syncStatus,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(start = 34.dp, bottom = 8.dp)
                )
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel("CE QUI DOIT SONNER")
            StatusLine(
                ok = notifOk,
                label = if (notifOk) "Notifications autorisées"
                else "Notifications refusées : aucun rappel ne s'affichera",
                actionLabel = if (notifOk) null else "Régler",
                onAction = onReminders
            )
            StatusLine(
                ok = overlayOk,
                label = if (overlayOk) "Affichage par-dessus les autres applications"
                else "Sans « afficher par-dessus », les rappels et le blocage arrivent en bandeau au lieu du plein écran",
                actionLabel = if (overlayOk) null else "Régler",
                onAction = onScreenTime
            )
            StatusLine(
                ok = settings.alertsEnabled,
                label = if (settings.alertsEnabled) "Rappels en plein écran"
                else "Rappels réduits à deux notifications par jour",
                actionLabel = if (settings.alertsEnabled) null else "Changer",
                onAction = onReminders
            )
            StatusLine(
                ok = settings.mealRemindersEnabled,
                label = if (settings.mealRemindersEnabled)
                    "Repas rappelés à ${settings.mealReminderMorning}, ${settings.mealReminderNoon} et ${settings.mealReminderEvening}"
                else "Aucun rappel de repas : il faut penser à noter soi-même",
                actionLabel = if (settings.mealRemindersEnabled) null else "Activer",
                onAction = onReminders
            )

            Spacer(Modifier.height(20.dp))
            SectionLabel("CE QUI MESURE")
            StatusLine(
                ok = healthOk,
                label = if (healthOk) "Health Connect lu : sommeil, pas, séances"
                else "Health Connect non autorisé : sommeil et pas resteront vides",
                actionLabel = if (healthOk) null else "Autoriser",
                onAction = onHealth
            )
            StatusLine(
                ok = usageOk,
                label = if (usageOk) "Temps d'écran lisible"
                else "Accès aux données d'utilisation refusé : le Pacte ne peut rien mesurer",
                actionLabel = if (usageOk) null else "Autoriser",
                onAction = onScreenTime
            )
            StatusLine(
                ok = !settings.pacteEnabled || BlockerService.running,
                label = when {
                    !settings.pacteEnabled -> "Pacte d'écran éteint — c'est un choix, pas une panne"
                    BlockerService.running -> "Surveillance du Pacte en marche"
                    else -> "Pacte activé mais surveillance arrêtée : l'économiseur de batterie du téléphone l'a probablement tuée"
                },
                actionLabel = if (settings.pacteEnabled && !BlockerService.running) "Relancer" else null,
                onAction = { vm.ensureBlockerRunning() }
            )
            StatusLine(
                ok = settings.calendarEnabled && settings.calendarId > 0,
                label = if (settings.calendarEnabled && settings.calendarId > 0)
                    "Créneaux envoyés vers ${settings.calendarName}"
                else "Agenda non relié : les créneaux restent dans l'application",
                actionLabel = if (settings.calendarEnabled && settings.calendarId > 0) null else "Relier",
                onAction = onCalendar
            )

            Spacer(Modifier.height(20.dp))
            SectionLabel("L'ASSISTANT")
            val aiOk = settings.aiEnabled && settings.aiApiKey.isNotBlank()
            StatusLine(
                ok = aiOk,
                label = if (aiOk) "Activé · ${com.notresemaine.app.ai.Ai.providerLabel(settings.aiApiKey)}"
                else "Éteint : aucun bouton ✨ n'apparaît, tout le reste fonctionne",
                actionLabel = if (aiOk) null else "Activer",
                onAction = onAssistant
            )
            StatusLine(
                ok = settings.mealPhotoEnabled,
                label = if (settings.mealPhotoEnabled) "Analyse photo des repas autorisée"
                else "Analyse photo éteinte : aucune image ne quitte le téléphone",
                actionLabel = if (settings.mealPhotoEnabled) null else "Activer",
                onAction = onAssistant
            )

            Spacer(Modifier.height(20.dp))
            SectionLabel("CE QUI NE SORT PAS DU TÉLÉPHONE")
            Text(
                text = buildString {
                    appendLine(
                        if (settings.weightShared) "· Vos pesées sont partagées avec votre binôme."
                        else "· Vos pesées restent sur ce téléphone — et ne sont donc pas sauvegardées."
                    )
                    appendLine(
                        if (settings.mealLogShared) "· Vos repas notés sont partagés."
                        else "· Vos repas notés restent sur ce téléphone — et ne sont donc pas sauvegardés."
                    )
                    append("· Un objectif marqué privé n'est jamais envoyé à l'assistant, ni visible par l'autre.")
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Une ligne d'état : le verdict, ce qu'il signifie, et où le corriger. */
@Composable
private fun StatusLine(
    ok: Boolean,
    label: String,
    actionLabel: String?,
    onAction: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(if (ok) "✅" else "⚠️", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (ok) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        if (actionLabel != null) {
            TextButton(onClick = onAction, modifier = Modifier.height(48.dp)) { Text(actionLabel) }
        }
    }
}

private fun canPostNotifications(context: Context): Boolean =
    if (android.os.Build.VERSION.SDK_INT < 33) true
    else context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED

/** « il y a 12 min », « hier », « le 3 septembre » — jamais un horodatage brut. */
private fun ago(timestamp: Long): String {
    val minutes = (System.currentTimeMillis() - timestamp) / 60_000
    return when {
        minutes < 1 -> "à l'instant"
        minutes < 60 -> "il y a $minutes min"
        minutes < 24 * 60 -> "il y a ${minutes / 60} h"
        minutes < 48 * 60 -> "hier"
        else -> "le " + Dates.shortLabel(
            java.time.Instant.ofEpochMilli(timestamp)
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate().format(Dates.ISO)
        )
    }
}
