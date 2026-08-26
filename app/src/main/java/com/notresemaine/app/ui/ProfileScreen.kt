package com.notresemaine.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.notresemaine.app.data.AppSettings
import com.notresemaine.app.ui.theme.accentFor
import com.notresemaine.app.ui.theme.accentLabel

/** Qui je suis dans l'application, et à quoi elle ressemble. */
@Composable
fun ProfileScreen(vm: AppViewModel, settings: AppSettings, onBack: () -> Unit) {
    var name by remember(settings.myName) { mutableStateOf(settings.myName) }
    var color by remember(settings.myColor) { mutableStateOf(settings.myColor) }

    val context = LocalContext.current
    val appVersion = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
    }

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
                title = "👤 Profil",
                subtitle = "Notre Semaine · version $appVersion",
                onBack = onBack
            )

            Spacer(Modifier.height(12.dp))
            SectionLabel("MON PRÉNOM")
            VoiceField(
                value = name,
                onValueChange = { name = it },
                label = "Prénom",
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(20.dp))
            SectionLabel("MA COULEUR — prenez-en chacun une différente")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("A", "B").forEach { role ->
                    FilterChip(
                        selected = color == role,
                        onClick = { color = role },
                        label = {
                            Text(
                                "● ${accentLabel(role)}",
                                style = MaterialTheme.typography.labelLarge,
                                color = accentFor(role)
                            )
                        },
                        modifier = Modifier.height(48.dp)
                    )
                }
            }
            Text(
                text = "La couleur ne sert jamais seule : elle va toujours avec un prénom ou " +
                    "une initiale, pour rester lisible en cas de daltonisme.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(Modifier.height(24.dp))
            SectionLabel("APPARENCE")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("sombre" to "Sombre", "auto" to "Auto", "clair" to "Clair").forEach { (mode, label) ->
                    FilterChip(
                        selected = settings.themeMode == mode,
                        onClick = { vm.setThemeMode(mode) },
                        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
                        modifier = Modifier.height(48.dp)
                    )
                }
            }
            Text(
                text = "« Auto » suit le réglage du téléphone. Le mode sombre est le défaut : " +
                    "l'application sert surtout tôt le matin et tard le soir.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(Modifier.height(20.dp))
        }

        BigButton(
            text = "Enregistrer",
            enabled = name.isNotBlank(),
            onClick = {
                vm.saveProfile(name, color)
                onBack()
            },
            modifier = Modifier.padding(bottom = 16.dp)
        )
    }
}
