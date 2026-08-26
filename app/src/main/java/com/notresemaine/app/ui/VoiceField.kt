package com.notresemaine.app.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import java.util.Locale

/**
 * Un champ de texte avec un micro au bout.
 *
 * Partout où l'application demande d'écrire, elle doit accepter qu'on parle :
 * taper « rappeler le garage pour le contrôle technique » au feu rouge, une main
 * sur le volant, ne se fait pas. Le micro est donc dans le champ lui-même, et
 * non relégué à un bouton flottant qu'il faudrait aller chercher.
 *
 * La dictée passe par celle du système — la même que le clavier. **La voix ne
 * quitte jamais le téléphone** : ce qui revient ici, c'est du texte, exactement
 * comme si on l'avait tapé. Aucun enregistrement n'est envoyé nulle part, et
 * cela vaut même quand l'assistant est éteint.
 *
 * Le texte dicté s'ajoute à la suite de ce qui est déjà écrit, il ne l'efface
 * pas : on peut commencer à taper, finir en parlant, ou dicter en deux fois.
 */
@Composable
fun VoiceField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    prompt: String = "Dictez votre texte",
    maxLines: Int = 3,
    singleLine: Boolean = false,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    val context = LocalContext.current

    val listen = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            .orEmpty()
            .trim()
        if (spoken.isBlank()) return@rememberLauncherForActivityResult
        onValueChange(if (value.isBlank()) spoken else "${value.trimEnd()} $spoken")
    }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        textStyle = MaterialTheme.typography.bodyLarge,
        maxLines = maxLines,
        singleLine = singleLine,
        enabled = enabled,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        trailingIcon = {
            // Une zone de 48 dp, comme tous les autres points touchables : le
            // micro est petit à l'œil, il ne doit pas l'être sous le doigt.
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .clickable(enabled = enabled) {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(
                                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                            )
                            putExtra(
                                RecognizerIntent.EXTRA_LANGUAGE,
                                Locale.FRANCE.toLanguageTag()
                            )
                            putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
                        }
                        runCatching { listen.launch(intent) }.onFailure {
                            Toast.makeText(
                                context,
                                "Aucune dictée vocale disponible sur ce téléphone.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                contentAlignment = Alignment.Center
            ) { Text("🎤", style = MaterialTheme.typography.titleMedium) }
        },
        modifier = modifier
    )
}
