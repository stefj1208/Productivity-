package com.notresemaine.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.notresemaine.app.data.TaskEntity
import com.notresemaine.app.ui.theme.DeepBlack
import com.notresemaine.app.ui.theme.NeutralGray
import com.notresemaine.app.ui.theme.accentFor

/** Pastille de personne : couleur + initiale, jamais la couleur seule. */
@Composable
fun PersonBadge(name: String, colorRole: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(accentFor(colorRole), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.take(1).uppercase().ifBlank { "?" },
                style = MaterialTheme.typography.titleMedium,
                color = DeepBlack
            )
        }
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 10.dp)
        )
    }
}

/** Ligne de tâche : un seul tap n'importe où pour cocher. Hauteur minimum 56 dp. */
@Composable
fun TaskRow(
    task: TaskEntity,
    accent: Color,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clickable(onClick = onToggle)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (task.done) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
            contentDescription = if (task.done) "Fait" else "À faire",
            tint = if (task.done) NeutralGray else accent,
            modifier = Modifier.size(28.dp)
        )
        Text(
            text = (if (task.isSport) "🏃 " else "") + task.title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (task.done) NeutralGray else MaterialTheme.colorScheme.onBackground,
            textDecoration = if (task.done) TextDecoration.LineThrough else null,
            modifier = Modifier.padding(start = 14.dp)
        )
    }
}

/** Bouton principal : pleine largeur, 56 dp, placé en bas d'écran (zone du pouce). */
@Composable
fun BigButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Étiquette de section discrète. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

/** Puces L M M J V S D pour affecter une tâche à un jour, en un tap. */
@Composable
fun DayChips(
    weekStart: String,
    selectedDate: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val days = com.notresemaine.app.data.Dates.daysOfWeek(weekStart)
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        days.forEach { iso ->
            val d = java.time.LocalDate.parse(iso)
            val selected = iso == selectedDate
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape
                    )
                    .clickable { onSelect(if (selected) null else iso) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = com.notresemaine.app.data.Dates.dayInitial(d.dayOfWeek),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
