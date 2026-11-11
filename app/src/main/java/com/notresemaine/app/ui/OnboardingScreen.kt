package com.notresemaine.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.notresemaine.app.ui.theme.accentFor
import com.notresemaine.app.ui.theme.accentLabel

@Composable
fun OnboardingScreen(vm: AppViewModel) {
    var name by remember { mutableStateOf("") }
    var color by remember { mutableStateOf("A") }
    var mode by remember { mutableStateOf("duo") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.weight(1f))
        Text(text = "Notre Semaine", style = MaterialTheme.typography.displaySmall)
        Text(
            text = if (mode == "solo") "Planifier sa semaine, sans se surcharger."
            else "Planifier à deux, sans se surcharger.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )

        Spacer(Modifier.height(32.dp))
        SectionLabel("TON PRÉNOM")
        VoiceField(
            value = name,
            onValueChange = { name = it },
            placeholder = "Prénom",
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(20.dp))
        SectionLabel("SEUL OU À DEUX ?")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("solo" to "🧍 Solo", "duo" to "💞 À deux").forEach { (value, label) ->
                FilterChip(
                    selected = mode == value,
                    onClick = { mode = value },
                    label = { Text(label, style = MaterialTheme.typography.labelLarge) },
                    modifier = Modifier.height(48.dp)
                )
            }
        }
        Text(
            text = if (mode == "solo")
                "Tout pour soi : planning, défis, repas, pacte d'écran. Ça se change plus tard dans Profil."
            else "Vos deux téléphones se relient dans « Moi → Synchronisation ». Ça se change plus tard dans Profil.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )

        Spacer(Modifier.height(20.dp))
        SectionLabel(if (mode == "solo") "TA COULEUR" else "TA COULEUR (l'autre prendra la seconde)")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("A", "B").forEach { role ->
                FilterChip(
                    selected = color == role,
                    onClick = { color = role },
                    label = {
                        Text(
                            text = "● ${accentLabel(role)}",
                            style = MaterialTheme.typography.labelLarge,
                            color = accentFor(role)
                        )
                    },
                    modifier = Modifier.height(48.dp)
                )
            }
        }

        Spacer(Modifier.weight(1f))
        BigButton(
            text = "Commencer",
            enabled = name.isNotBlank(),
            onClick = {
                if (Build.VERSION.SDK_INT >= 33) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                vm.completeOnboarding(name, color, mode)
            },
            modifier = Modifier.padding(bottom = 24.dp)
        )
    }
}
