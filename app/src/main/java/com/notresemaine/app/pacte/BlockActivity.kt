package com.notresemaine.app.pacte

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.notresemaine.app.data.Dates
import com.notresemaine.app.data.Repository
import com.notresemaine.app.sync.SyncManager
import com.notresemaine.app.ui.BigButton
import com.notresemaine.app.ui.theme.AppTheme
import kotlinx.coroutines.launch

/** Écran affiché quand la limite du Pacte est dépassée. Sortie : le partenaire, ou l'écran d'accueil. */
class BlockActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Doit s'afficher même téléphone verrouillé, comme un réveil.
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        val minutes = intent.getIntExtra("minutes", 0)
        val isCurfew = intent.getBooleanExtra("curfew", false)
        val curfewLabel = intent.getStringExtra("curfewLabel") ?: ""
        val repo = Repository.get(applicationContext)
        val sync = SyncManager(repo)

        setContent {
            AppTheme(mode = "sombre") {
                var requested by remember { mutableStateOf(false) }
                val settings by repo.settings.flow.collectAsState(initial = null)
                val s = settings

                // Le binôme a accordé la pause : l'écran s'efface tout seul.
                androidx.compose.runtime.LaunchedEffect(s?.graceUntil) {
                    if (s != null && s.graceUntil > System.currentTimeMillis()) finish()
                }

                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = if (isCurfew) "🌙 Couvre-feu" else "Pacte d'écran",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = if (isCurfew) "C'est l'heure de dormir.\nCouvre-feu $curfewLabel."
                            else "Limite atteinte : $minutes min sur les applications choisies aujourd'hui.",
                            style = MaterialTheme.typography.displaySmall
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = if (requested)
                                "Demande envoyée. Elle sera reçue à la prochaine synchronisation " +
                                    "du téléphone de ton binôme (à l'ouverture de son application)."
                            else
                                "C'était le pacte : seule l'autre moitié peut accorder une pause.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.weight(1f))
                        if (!requested && s != null) {
                            BigButton(
                                text = "Demander 15 min à mon binôme",
                                onClick = {
                                    requested = true
                                    lifecycleScope.launch {
                                        val partner = repo.db.profiles().partnerOf(s.myUserId)
                                        if (partner != null) {
                                            repo.requestGrace(s.myUserId, partner.id, 15)
                                            sync.syncNow()
                                        }
                                    }
                                }
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        TextButton(onClick = { goHome() }) {
                            Text(if (isCurfew) "Bonne nuit" else "Fermer et poser le téléphone")
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        visible = true
    }

    override fun onStop() {
        visible = false
        super.onStop()
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }

    companion object {
        /** Évite de relancer l'écran toutes les deux secondes s'il est déjà là. */
        @Volatile
        var visible: Boolean = false
            private set

        fun intent(context: android.content.Context, minutes: Int, curfew: Boolean, curfewLabel: String): Intent =
            Intent(context, BlockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("minutes", minutes)
                .putExtra("curfew", curfew)
                .putExtra("curfewLabel", curfewLabel)
    }
}
