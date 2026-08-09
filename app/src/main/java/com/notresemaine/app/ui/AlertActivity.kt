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
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        val emoji = intent.getStringExtra(Alarms.EXTRA_EMOJI) ?: "⏰"
        val title = intent.getStringExtra(Alarms.EXTRA_TITLE) ?: "Rappel"
        val text = intent.getStringExtra(Alarms.EXTRA_TEXT).orEmpty()
        val sound = intent.getBooleanExtra(Alarms.EXTRA_SOUND, true)

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
                            text = "C'est parti",
                            onClick = { finishAndOpenApp() }
                        )
                        OutlinedButton(
                            onClick = {
                                Alarms.snooze(this@AlertActivity, 10, emoji, title, text, sound)
                                finishAlert()
                            },
                            modifier = Modifier
                                .padding(top = 12.dp)
                                .height(56.dp)
                        ) { Text("Dans 10 minutes") }
                    }
                }
            }
        }
    }

    private fun finishAndOpenApp() {
        startActivity(
            android.content.Intent(this, com.notresemaine.app.MainActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
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
