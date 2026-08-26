package com.notresemaine.app.ui

import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.notresemaine.app.notif.Alarms
import com.notresemaine.app.ui.theme.AppTheme
import kotlinx.coroutines.launch

/**
 * L'écran de rappel : il s'allume par-dessus tout, même téléphone verrouillé.
 *
 * Une notification se balaie sans la lire ; une alarme, non. D'où le plein écran,
 * le son d'alarme du système, et deux boutons seulement — parce qu'à ce moment-là
 * on n'a pas envie de réfléchir à une troisième option.
 */
class AlertActivity : ComponentActivity() {

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val emoji = intent.getStringExtra(Alarms.EXTRA_EMOJI) ?: "⏰"
        val title = intent.getStringExtra(Alarms.EXTRA_TITLE) ?: "Rappel"
        val text = intent.getStringExtra(Alarms.EXTRA_TEXT).orEmpty()
        val nudge = intent.getBooleanExtra(Alarms.EXTRA_NUDGE, false)
        val route = intent.getStringExtra(Alarms.EXTRA_ROUTE).orEmpty()
        val actionLabel = intent.getStringExtra(Alarms.EXTRA_ACTION).orEmpty()
        // Une demande de pause se tranche ici même : l'envoyer chercher un menu
        // ferait attendre quelqu'un qui est bloqué à l'instant.
        val graceId = intent.getStringExtra(Alarms.EXTRA_GRACE_ID).orEmpty()
        val graceMinutes = intent.getIntExtra(Alarms.EXTRA_GRACE_MINUTES, 15)
        val sound = intent.getBooleanExtra(Alarms.EXTRA_SOUND, true) && !nudge

        // Une habitude s'affiche par-dessus tout, mais n'allume pas l'écran :
        // on ne réveille personne pour lui rappeler de se tenir droit.
        setShowWhenLocked(!nudge)
        setTurnScreenOn(!nudge)

        Alarms.dismissNotification(this)
        if (sound) startAlarm()

        setContent {
            AppTheme(mode = "sombre") {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 28.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(text = emoji, style = MaterialTheme.typography.displaySmall)
                        Text(
                            text = title,
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp)
                        )

                        Spacer(Modifier.height(40.dp))
                        BigButton(
                            text = when {
                                actionLabel.isNotBlank() -> actionLabel
                                nudge -> "Compris"
                                else -> "C'est parti"
                            },
                            onClick = {
                                when {
                                    graceId.isNotBlank() -> answerGrace(graceId, true, graceMinutes)
                                    // Un bouton qui mène quelque part ouvre l'application
                                    // à l'endroit exact où l'on peut agir.
                                    route.isNotBlank() -> finishAndOpenApp(route)
                                    nudge -> finishAlert()
                                    else -> finishAndOpenApp()
                                }
                            }
                        )
                        OutlinedButton(
                            onClick = {
                                when {
                                    graceId.isNotBlank() -> answerGrace(graceId, false, graceMinutes)
                                    nudge -> finishAndOpenApp()
                                    else -> {
                                        Alarms.snooze(this@AlertActivity, 10, emoji, title, text, sound)
                                        finishAlert()
                                    }
                                }
                            },
                            modifier = Modifier
                                .padding(top = 12.dp)
                                .height(56.dp)
                        ) {
                            Text(
                                when {
                                    // « C'était le pacte » : refuser doit être aussi
                                    // simple qu'accorder, sinon ce n'est plus un choix.
                                    graceId.isNotBlank() -> "Non, c'était le pacte"
                                    nudge -> "Ouvrir l'application"
                                    else -> "Dans 10 minutes"
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Accorde ou refuse la pause, puis pousse la réponse tout de suite.
     *
     * La synchronisation immédiate n'est pas un luxe : l'autre est devant un
     * écran de blocage en ce moment même, et sa délivrance ne peut pas attendre
     * le prochain réveil du travail de fond.
     */
    private fun answerGrace(requestId: String, granted: Boolean, minutes: Int) {
        val repo = com.notresemaine.app.data.Repository.get(applicationContext)
        // Le traitement continue au-delà de cet écran : on l'attache donc au
        // processus, pas à l'activité qu'on est en train de fermer.
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            repo.answerGrace(requestId, granted)
            com.notresemaine.app.sync.SyncManager(repo).syncNow()
        }
        android.widget.Toast.makeText(
            this,
            if (granted) "$minutes minutes accordées ✓" else "Demande refusée",
            android.widget.Toast.LENGTH_SHORT
        ).show()
        finishAlert()
    }

    private fun finishAndOpenApp(route: String = "") {
        startActivity(
            android.content.Intent(this, com.notresemaine.app.MainActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(com.notresemaine.app.MainActivity.EXTRA_ROUTE, route)
        )
        finishAlert()
    }

    private fun finishAlert() {
        stopAlarm()
        finish()
    }

    private fun startAlarm() {
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ringtone = RingtoneManager.getRingtone(this, uri)?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                play()
            }
        }
        runCatching {
            vibrator = getSystemService(Vibrator::class.java)
            vibrator?.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, 600, 900), 0)
            )
        }
    }

    private fun stopAlarm() {
        runCatching { ringtone?.stop() }
        runCatching { vibrator?.cancel() }
        ringtone = null
        vibrator = null
    }

    override fun onDestroy() {
        stopAlarm()
        super.onDestroy()
    }
}
