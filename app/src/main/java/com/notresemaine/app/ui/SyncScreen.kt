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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.notresemaine.app.data.AppSettings

/**
 * La synchronisation, en trois étapes qui s'enchaînent : configurer le service,
 * créer son compte, relier les deux téléphones. On ne voit que l'étape en cours —
 * afficher les trois d'un coup donnerait l'impression d'une montagne.
 */
@Composable
fun SyncScreen(vm: AppViewModel, settings: AppSettings, onBack: () -> Unit) {
    val status by vm.syncStatus.collectAsState()

    val stepLabel = when {
        settings.supabaseUrl.isBlank() || settings.supabaseKey.isBlank() -> "Étape 1 sur 3"
        settings.refreshToken.isBlank() -> "Étape 2 sur 3"
        settings.coupleCode.isBlank() -> "Étape 3 sur 3"
        else -> "Reliée ✓"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        ScreenHeader(
            title = "☁️ Synchronisation",
            subtitle = stepLabel,
            onBack = onBack
        )

        Spacer(Modifier.height(12.dp))
        when {
            settings.supabaseUrl.isBlank() || settings.supabaseKey.isBlank() -> {
                Text(
                    text = "Facultatif : l'application fonctionne très bien sans. C'est ce qui " +
                        "permet de voir la semaine de l'autre et de partager les menus.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Suivez le guide SETUP-SUPABASE.md du projet, puis collez ici " +
                        "l'adresse et la clé de votre projet Supabase.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Spacer(Modifier.height(12.dp))
                var url by remember { mutableStateOf("") }
                var key by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    label = { Text("Adresse du projet (https://…supabase.co)") },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = key, onValueChange = { key = it },
                    label = { Text("Clé « anon public »") },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                TextButton(
                    onClick = { vm.saveSupabaseConfig(url, key) },
                    enabled = url.startsWith("https://") && key.length > 20
                ) { Text("Enregistrer la configuration") }
            }

            settings.refreshToken.isBlank() -> {
                Text(
                    text = "Chacun crée son propre compte, sur son téléphone.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(12.dp))
                var email by remember { mutableStateOf("") }
                var password by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = email, onValueChange = { email = it },
                    label = { Text("E-mail") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text("Mot de passe (8 caractères minimum)") },
                    visualTransformation = PasswordVisualTransformation(),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    OutlinedButton(
                        onClick = { vm.signUp(email.trim(), password) },
                        enabled = email.contains("@") && password.length >= 8,
                        modifier = Modifier.height(48.dp)
                    ) { Text("Créer le compte") }
                    TextButton(
                        onClick = { vm.signIn(email.trim(), password) },
                        enabled = email.contains("@") && password.isNotBlank(),
                        modifier = Modifier.height(48.dp)
                    ) { Text("Se connecter") }
                }
            }

            else -> {
                Text(
                    text = "Connecté : ${settings.authEmail}",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(12.dp))
                if (settings.coupleCode.isBlank()) {
                    Text(
                        text = "Dernière étape : créez votre espace couple sur UN téléphone, " +
                            "puis saisissez le code obtenu sur l'autre.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { vm.createCouple() },
                        modifier = Modifier.height(48.dp)
                    ) { Text("Créer notre espace couple") }
                    Spacer(Modifier.height(12.dp))
                    var code by remember { mutableStateOf("") }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = code, onValueChange = { code = it },
                            label = { Text("Code reçu") },
                            textStyle = MaterialTheme.typography.bodyLarge,
                            singleLine = true, modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { vm.joinCouple(code) },
                            enabled = code.isNotBlank()
                        ) { Text("Rejoindre") }
                    }
                } else {
                    Text(
                        text = "Code de votre espace : ${settings.coupleCode}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { vm.syncNowManual() },
                        modifier = Modifier.height(48.dp)
                    ) { Text("Synchroniser maintenant") }
                }
                TextButton(onClick = { vm.signOut() }) { Text("Se déconnecter") }
            }
        }

        if (status.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = status,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}
