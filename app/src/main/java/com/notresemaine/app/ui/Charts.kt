package com.notresemaine.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp

/**
 * Courbe d'évolution : le poids, les calories, le temps d'écran.
 *
 * Un histogramme répond à « combien ce jour-là », une courbe à « ça monte ou
 * ça descend » — c'est la seule question qui compte pour une pesée. Pas de
 * rouge, pas de flèche de honte : la ligne dit ce qu'elle a à dire.
 */
@Composable
fun LineChart(
    values: List<Float>,
    labels: List<String>,
    accent: Color,
    valueLabel: (Float) -> String,
    target: Float? = null,
    modifier: Modifier = Modifier
) {
    val grid = MaterialTheme.colorScheme.surfaceVariant
    val real = values.filter { it > 0f }
    if (real.size < 2) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Deux mesures suffisent à tracer la courbe.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val dataLo = real.minOrNull() ?: 0f
    val dataHi = real.maxOrNull() ?: 0f
    val lo = minOf(dataLo, target ?: dataLo)
    val hi = maxOf(dataHi, target ?: dataHi)
    val span = (hi - lo).takeIf { it > 0.01f } ?: 1f
    val pad = span * 0.15f
    val bottom = lo - pad
    val top = hi + pad

    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = valueLabel(hi),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = valueLabel(lo),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .padding(vertical = 8.dp)
        ) {
            fun y(v: Float) = size.height * (1f - (v - bottom) / (top - bottom))
            fun x(i: Int) = if (values.size == 1) size.width / 2f
            else size.width * i / (values.size - 1).toFloat()

            // Trois lignes de repère, très discrètes.
            listOf(0f, 0.5f, 1f).forEach { f ->
                drawLine(
                    color = grid,
                    start = Offset(0f, size.height * f),
                    end = Offset(size.width, size.height * f),
                    strokeWidth = 1.5f
                )
            }

            if (target != null && target > 0f) {
                drawLine(
                    color = accent.copy(alpha = 0.45f),
                    start = Offset(0f, y(target)),
                    end = Offset(size.width, y(target)),
                    strokeWidth = 3f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 12f))
                )
            }

            // La ligne saute les trous plutôt que de plonger à zéro.
            val path = Path()
            var started = false
            values.forEachIndexed { i, v ->
                if (v <= 0f) { started = false; return@forEachIndexed }
                if (!started) { path.moveTo(x(i), y(v)); started = true }
                else path.lineTo(x(i), y(v))
            }
            drawPath(path, color = accent, style = Stroke(width = 6f))

            values.forEachIndexed { i, v ->
                if (v > 0f) drawCircle(accent, radius = 7f, center = Offset(x(i), y(v)))
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = labels.firstOrNull().orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = labels.lastOrNull().orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Anneau de progression : un chiffre au centre, l'avancement autour. */
@Composable
fun ProgressRing(
    progress: Float,
    center: String,
    label: String,
    accent: Color,
    size: androidx.compose.ui.unit.Dp = 96.dp,
    modifier: Modifier = Modifier
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val clamped = progress.coerceIn(0f, 1f)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 14f
                val inset = stroke / 2f
                val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
                drawArc(
                    color = track, startAngle = 0f, sweepAngle = 360f, useCenter = false,
                    topLeft = Offset(inset, inset), size = arcSize,
                    style = Stroke(width = stroke)
                )
                drawArc(
                    color = accent, startAngle = -90f, sweepAngle = 360f * clamped, useCenter = false,
                    topLeft = Offset(inset, inset), size = arcSize,
                    style = Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                )
            }
            Text(text = center, style = MaterialTheme.typography.titleLarge)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

/**
 * Jauge horizontale avec un repère : « 1 450 kcal sur 2 000 ».
 * Dépasser n'allume rien en rouge — la barre continue simplement au-delà du trait.
 */
@Composable
fun Gauge(
    value: Float,
    max: Float,
    accent: Color,
    caption: String,
    modifier: Modifier = Modifier
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val over = MaterialTheme.colorScheme.onSurfaceVariant
    val ratio = if (max > 0f) (value / max).coerceIn(0f, 1.4f) else 0f
    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
        ) {
            val r = 9f
            drawRoundRect(
                color = track, size = size,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r)
            )
            val w = size.width * (ratio / 1.4f)
            if (w > 0f) {
                drawRoundRect(
                    color = if (ratio > 1f) over else accent,
                    size = Size(w, size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r)
                )
            }
            // Le repère « objectif » se lit à 1/1,4 de la largeur.
            val markX = size.width / 1.4f
            drawLine(
                color = over,
                start = Offset(markX, 0f),
                end = Offset(markX, size.height),
                strokeWidth = 4f
            )
        }
        Text(
            text = caption,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/**
 * La répartition d'une journée entre protéines, glucides et lipides.
 *
 * Une seule barre en trois segments plutôt que trois jauges : la question n'est
 * pas « combien de protéines » dans l'absolu, mais quelle place elles prennent
 * par rapport au reste. Les trois nuances viennent de la couleur de la personne,
 * et chaque segment est doublé de son intitulé écrit — une couleur seule ne se
 * lit pas quand on distingue mal les teintes.
 *
 * Aucun seuil, aucune zone rouge : la barre décrit, elle ne corrige pas.
 */
@Composable
fun MacroBar(
    summary: com.notresemaine.app.data.Nutrition.Summary,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val (p, c, f) = summary.split
    if (p + c + f <= 0) return
    val track = MaterialTheme.colorScheme.surfaceVariant

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
        ) {
            val r = 9f
            drawRoundRect(
                color = track, size = size,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r)
            )
            var x = 0f
            listOf(p to 1f, c to 0.62f, f to 0.34f).forEach { (percent, alpha) ->
                val w = size.width * percent / 100f
                if (w > 0f) {
                    drawRect(
                        color = accent.copy(alpha = alpha),
                        topLeft = Offset(x, 0f),
                        size = Size(w, size.height)
                    )
                    x += w
                }
            }
        }
        Text(
            text = "$p % protéines · $c % glucides · $f % lipides",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
        Text(
            text = "${summary.protein} g · ${summary.carbs} g · ${summary.fat} g · " +
                "${summary.fiber} g de fibres, par jour noté",
            style = MaterialTheme.typography.bodyLarge
        )
        val ignored = summary.mealsIgnored
        Text(
            text = com.notresemaine.app.data.Nutrition.observation(summary) +
                if (ignored > 0) " ($ignored repas sans détail nutritionnel, non comptés.)" else "",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/** Un grand chiffre et son intitulé : le chiffre d'abord, le mot ensuite. */
@Composable
fun BigStat(
    value: String,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(text = value, style = MaterialTheme.typography.displaySmall, color = accent)
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
