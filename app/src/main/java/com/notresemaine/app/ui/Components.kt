package com.notresemaine.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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

/** Conseil d'un livre : gros emoji, une phrase qui claque, la source en petit. */
@Composable
fun TipCard(tip: com.notresemaine.app.data.Tips.Tip, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = tip.emoji, style = MaterialTheme.typography.displaySmall)
        Column(modifier = Modifier.padding(start = 14.dp)) {
            Text(
                text = tip.punch,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = tip.book,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

/** La boussole : la seule chose à faire maintenant, en très gros. */
@Composable
fun CompassCard(
    step: com.notresemaine.app.data.Compass.Step,
    accent: Color,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(20.dp)
    ) {
        Text(text = step.emoji, style = MaterialTheme.typography.displaySmall)
        Text(
            text = step.title,
            style = MaterialTheme.typography.displaySmall,
            color = accent,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = step.why,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

/** Flèches ‹ › pour naviguer d'une semaine à l'autre, passé comme futur. */
@Composable
fun WeekNavigator(
    weekStartIso: String,
    onOffsetChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                .clickable { onOffsetChange(-1) },
            contentAlignment = Alignment.Center
        ) {
            Text("‹", style = MaterialTheme.typography.titleLarge)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(
                text = com.notresemaine.app.data.Dates.weekRelativeLabel(weekStartIso),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = com.notresemaine.app.data.Dates.weekRangeLabel(weekStartIso),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                .clickable { onOffsetChange(1) },
            contentAlignment = Alignment.Center
        ) {
            Text("›", style = MaterialTheme.typography.titleLarge)
        }
    }
}

/** Petit histogramme sur 4 semaines : lisible d'un coup d'œil, sans rouge d'alerte. */
@Composable
fun MiniBarChart(
    values: List<Float>,
    labels: List<String>,
    accent: Color,
    valueLabel: (Float) -> String,
    modifier: Modifier = Modifier
) {
    val maxValue = (values.maxOrNull() ?: 0f).coerceAtLeast(0.001f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(110.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        values.forEachIndexed { index, value ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (value > 0f) valueLabel(value) else "—",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .fillMaxWidth()
                        .height((6 + 62 * (value / maxValue)).dp)
                        .background(
                            if (value > 0f) accent else MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(6.dp)
                        )
                )
                Text(
                    text = labels.getOrElse(index) { "" },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
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

/**
 * Le bouton de l'assistant, identique partout dans l'application : on le reconnaît
 * du premier coup d'œil, et il dit lui-même quand il travaille.
 * Un seul geste, une seule touche — pas de menu caché.
 */
@Composable
fun AiButton(
    text: String,
    busy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        enabled = enabled && !busy,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        Text(if (busy) "L'assistant réfléchit…" else "✨ $text")
    }
}

/** Une proposition de l'assistant : on la lit, puis on l'accepte ou on l'ignore. */
@Composable
fun AiSuggestion(
    lines: List<String>,
    acceptLabel: String,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (lines.isEmpty()) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        lines.forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        Row(modifier = Modifier.padding(top = 6.dp)) {
            androidx.compose.material3.OutlinedButton(
                onClick = onAccept,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) { Text(acceptLabel) }
            androidx.compose.material3.TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .height(48.dp)
            ) { Text("Ignorer") }
        }
    }
}

/**
 * Une proposition qu'on adopte d'un seul tap.
 *
 * Remplace les puces Material pour tout texte un peu long : une puce garde ses
 * mots sur une seule ligne et coupe le reste, alors qu'une action peut faire
 * dix mots. Ici le texte passe à la ligne, et la zone tactile grandit avec lui.
 */
@Composable
fun ChoiceRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(
                if (selected) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.surface,
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (selected) "✓ $text" else text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Tuile d'accès à une partie de l'application.
 *
 * Elle dit toujours deux choses : où elle mène, et où on en est de ce côté-là.
 * Un réglage dont on ne voit pas l'état est un réglage qu'on n'ouvre jamais —
 * d'où le sous-titre vivant plutôt qu'une simple étiquette.
 */
@Composable
fun ShortcutTile(
    emoji: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlight: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 64.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = emoji, style = MaterialTheme.typography.titleLarge)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = if (highlight) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "›",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * L'en-tête d'un écran : où je suis, et comment je reviens.
 * Le même partout, pour qu'on n'ait jamais à chercher le chemin du retour.
 */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Text("‹", style = MaterialTheme.typography.titleLarge)
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = if (onBack != null) 14.dp else 0.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Un écran vide ne doit jamais être un cul-de-sac : il explique et il propose.
 */
@Composable
fun EmptyState(
    emoji: String,
    text: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(20.dp)
    ) {
        Text(text = emoji, style = MaterialTheme.typography.displaySmall)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp)
        )
        if (actionLabel != null && onAction != null) {
            androidx.compose.material3.OutlinedButton(
                onClick = onAction,
                modifier = Modifier
                    .padding(top = 14.dp)
                    .defaultMinSize(minHeight = 48.dp)
            ) { Text(actionLabel) }
        }
    }
}
