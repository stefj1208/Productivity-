package com.notresemaine.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private data class Book(val title: String, val idea: String, val inApp: List<String>)

private val books = listOf(
    Book(
        "Miracle Morning — Hal Elrod",
        "La première heure de la journée, répétée chaque jour, change tout.",
        listOf(
            "Le rituel S.A.V.E.R.S. avec minuteur (Silence, Affirmations, Visualisation, Exercice, Lecture, Écriture)",
            "Le bouton « Régler le réveil » qui programme l'alarme du téléphone",
            "La série de jours consécutifs, visible aussi par l'autre"
        )
    ),
    Book(
        "S'organiser pour réussir (GTD) — David Allen",
        "Vider sa tête : tout capturer, puis trier, pour ne plus rien porter mentalement.",
        listOf(
            "Le bouton + présent partout : noter en 3 secondes",
            "« Plombier mardi » devient automatiquement une tâche datée mardi",
            "Le tri de la boîte de réception intégré à la revue du dimanche"
        )
    ),
    Book(
        "Deep Work — Cal Newport",
        "La valeur vient des blocs de concentration longs et protégés, pas des miettes de temps.",
        listOf(
            "Le bloc de concentration décidé la veille, affiché le matin",
            "Les séances d'objectifs générées comme des blocs dédiés",
            "Le Pacte d'écran qui coupe les distractions au-delà de la limite"
        )
    ),
    Book(
        "The One Thing — Gary Keller",
        "UNE seule chose, qui rend le reste plus simple ou inutile.",
        listOf(
            "Une seule priorité par jour, affichée en très gros",
            "Une seule priorité par semaine, obligatoire pour valider",
            "La régularité avant le volume dans les objectifs (20 min/jour)"
        )
    ),
    Book(
        "L'essentialisme — Greg McKeown",
        "Moins mais mieux : éliminer le non-essentiel est une discipline.",
        listOf(
            "Maximum 3 tâches par jour — l'application refuse la 4ᵉ",
            "Maximum 3 objectifs actifs à la fois",
            "L'étape « qu'est-ce que j'abandonne cette semaine ? » chaque dimanche"
        )
    ),
    Book(
        "La semaine de 4 heures — Tim Ferriss",
        "Éliminer, automatiser, déléguer : 20 % des actions produisent 80 % des résultats.",
        listOf(
            "La mesure automatique du temps d'écran et des déverrouillages",
            "La limite quotidienne sur les applications choisies, tenue à deux",
            "La question du bilan : quelles actions ont vraiment compté ?"
        )
    )
)

/** Les 6 livres, leur idée clé, et ce que l'application en applique concrètement. */
@Composable
fun MethodScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(20.dp))
        Text("La méthode", style = MaterialTheme.typography.titleLarge)
        Text(
            text = "Six livres, une application. Voici ce qu'elle applique pour vous.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
        books.forEach { book ->
            Spacer(Modifier.height(20.dp))
            Text(book.title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = book.idea,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            book.inApp.forEach { line ->
                Text(
                    text = "• $line",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onBack) { Text("Retour") }
        Spacer(Modifier.height(24.dp))
    }
}
