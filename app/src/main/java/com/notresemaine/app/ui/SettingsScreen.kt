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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import com.notresemaine.app.data.Dates
import com.notresemaine.app.ui.theme.accentFor
import com.notresemaine.app.ui.theme.accentLabel

@Composable
fun SettingsScreen(
    vm: AppViewModel,
    settings: AppSettings,
    onMethod: () -> Unit,
    onScreenTime: () -> Unit,
    onHealth: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(20.dp))
        Text(text = "Réglages", style = MaterialTheme.typography.titleLarge)

        // Permet de vérifier d'un coup d'œil quelle version est réellement installée.
        val context = androidx.compose.ui.platform.LocalContext.current
        val appVersion = remember {
            runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull() ?: "?"
        }
        Text(
            text = "Version $appVersion",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onScreenTime, modifier = Modifier.height(48.dp)) {
            Text("📵 Temps d'écran & Pacte →")
        }
        TextButton(onClick = onHealth, modifier = Modifier.height(48.dp)) {
            Text("😴 Sommeil & sport →")
        }
        TextButton(onClick = onMethod, modifier = Modifier.height(48.dp)) {
            Text("📖 La méthode (les 6 livres) →")
        }

        HorizontalDivider(Modifier.padding(vertical = 16.dp))

        // ----- Assistant : en tête de page, parce que tant qu'il est éteint,
        // aucun bouton ✨ n'apparaît ailleurs dans l'application. -----
        var aiEnabled by remember(settings.aiEnabled) { mutableStateOf(settings.aiEnabled) }
        var aiKey by remember(settings.aiApiKey) { mutableStateOf(settings.aiApiKey) }
        SectionLabel(
            if (settings.aiEnabled && settings.aiApiKey.isNotBlank()) "✨ ASSISTANT — ACTIVÉ"
            else "✨ ASSISTANT — ÉTEINT"
        )
        Text(
            text = if (settings.aiEnabled && settings.aiApiKey.isNotBlank()) {
                "Les boutons ✨ sont visibles dans Objectifs, la revue du dimanche, " +
                    "Préparer demain, le bouton +, Menus, Courses, Temps d'écran et Sommeil."
            } else {
                "Tant qu'il est éteint, aucun bouton ✨ n'apparaît dans l'application. " +
                    "Activez-le ci-dessous et collez votre clé : les boutons apparaîtront alors " +
                    "dans Objectifs, la revue du dimanche, Préparer demain, le bouton +, " +
                    "Menus, Courses, Temps d'écran et Sommeil."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "L'application reste complète sans lui : idées de menus, premiers pas, " +
                "répartition des séances et classement des courses existent déjà hors ligne.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
        Text(
            text = "⚠️ Ce qui sort du téléphone, et rien d'autre : vos contraintes de menus, " +
                "l'intitulé d'un objectif, la note que vous venez d'écrire, les titres de vos " +
                "tâches en attente, vos moyennes d'écran et de sommeil. " +
                "Jamais un objectif marqué privé, jamais quoi que ce soit du partenaire, " +
                "jamais le détail jour par jour de votre santé.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
        Text(
            text = "Deux clés possibles, l'application reconnaît laquelle toute seule : " +
                "une clé Google (aistudio.google.com) ou une clé Anthropic (sk-ant-…). " +
                "Chaque appel vous est facturé par le fournisseur choisi.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Activer l'assistant",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Switch(checked = aiEnabled, onCheckedChange = { aiEnabled = it })
        }
        if (aiEnabled) {
            OutlinedTextField(
                value = aiKey,
                onValueChange = { aiKey = it },
                label = { Text("Clé API (Google ou Anthropic)") },
                visualTransformation = PasswordVisualTransformation(),
                textStyle = MaterialTheme.typography.bodyLarge,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Détecté : ${com.notresemaine.app.ai.Ai.providerLabel(aiKey)}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        TextButton(
            onClick = { vm.saveAiSettings(aiEnabled, aiKey) },
            enabled = !aiEnabled || aiKey.isNotBlank()
        ) { Text("Enregistrer l'assistant") }

        HorizontalDivider(Modifier.padding(vertical = 16.dp))

        // ----- Profil -----
        Spacer(Modifier.height(20.dp))
        SectionLabel("MON PROFIL")
        var name by remember(settings.myName) { mutableStateOf(settings.myName) }
        var color by remember(settings.myColor) { mutableStateOf(settings.myColor) }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Prénom") },
            textStyle = MaterialTheme.typography.bodyLarge,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
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
        TextButton(onClick = { vm.saveProfile(name, color) }, enabled = name.isNotBlank()) {
            Text("Enregistrer le profil")
        }

        HorizontalDivider(Modifier.padding(vertical = 16.dp))

        // ----- Rappels -----
        SectionLabel("RAPPELS (jamais plus de 2 par jour)")
        var evening by remember(settings.eveningReminder) { mutableStateOf(settings.eveningReminder) }
        var eveningOn by remember(settings.eveningEnabled) { mutableStateOf(settings.eveningEnabled) }
        var sunday by remember(settings.sundayReminder) { mutableStateOf(settings.sundayReminder) }
        var sundayOn by remember(settings.sundayEnabled) { mutableStateOf(settings.sundayEnabled) }

        ReminderRow("Préparer demain (chaque soir)", evening, eveningOn,
            onTime = { evening = it }, onToggle = { eveningOn = it })
        ReminderRow("Revue du dimanche", sunday, sundayOn,
            onTime = { sunday = it }, onToggle = { sundayOn = it })

        val timesOk = Dates.isValidTime(evening) && Dates.isValidTime(sunday)
        if (!timesOk) {
            Text(
                "Format d'heure attendu : HH:MM",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(
            onClick = { vm.saveReminders(evening, eveningOn, sunday, sundayOn) },
            enabled = timesOk
        ) { Text("Enregistrer les rappels") }

        HorizontalDivider(Modifier.padding(vertical = 16.dp))

        // ----- Thème -----
        SectionLabel("THÈME")
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

        HorizontalDivider(Modifier.padding(vertical = 16.dp))

        // ----- Synchronisation -----
        SectionLabel("SYNCHRONISATION ENTRE VOS DEUX TÉLÉPHONES")
        val status by vm.syncStatus.collectAsState()

        when {
            settings.supabaseUrl.isBlank() || settings.supabaseKey.isBlank() -> {
                Text(
                    text = "Facultatif : l'application fonctionne sans. Pour partager votre semaine, " +
                        "suivez le guide SETUP-SUPABASE.md (dans le projet), puis collez ici " +
                        "l'adresse et la clé de votre projet Supabase.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    text = "Chacun crée son propre compte (e-mail + mot de passe), sur son téléphone.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { vm.syncNowManual() },
                        modifier = Modifier.height(48.dp)
                    ) { Text("Synchroniser maintenant") }
                }
                TextButton(onClick = { vm.signOut() }) { Text("Se déconnecter") }
            }
        }
        if (status.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(text = status, style = MaterialTheme.typography.bodyLarge)
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun ReminderRow(
    label: String,
    time: String,
    enabled: Boolean,
    onTime: (String) -> Unit,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = time,
            onValueChange = onTime,
            textStyle = MaterialTheme.typography.bodyLarge,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .fillMaxWidth(0.32f)
        )
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}
