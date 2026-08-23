package com.notresemaine.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.notresemaine.app.data.Dates
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Un champ de date qui ouvre un calendrier.
 *
 * Une date tapée à la main, c'est trois façons de se tromper : le format, le
 * jour de la semaine qu'on ne voit pas, et la faute de frappe qui passe
 * inaperçue. Le calendrier supprime les trois d'un coup — et il n'y a plus
 * moyen d'enregistrer une date qui n'existe pas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    val parsed = Dates.parseOrNull(value)

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                .clickable { open = true }
                .defaultMinSize(minHeight = 56.dp)
                .padding(horizontal = 14.dp)
        ) {
            Text("📅", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = parsed?.let { Dates.longLabel(value) } ?: "Choisir une date",
                style = MaterialTheme.typography.bodyLarge,
                color = if (parsed != null) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            )
            if (parsed != null) {
                TextButton(onClick = { onChange("") }) { Text("Retirer") }
            }
        }
    }

    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (parsed ?: LocalDate.now())
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val picked = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.systemDefault()).toLocalDate()
                        onChange(picked.toString())
                    }
                    open = false
                }) { Text("Choisir") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Annuler") } }
        ) {
            DatePicker(state = state)
        }
    }
}

/**
 * Un champ d'heure qui ouvre une horloge. Même raison que la date : taper
 * « 9h30 » au lieu de « 09:30 » ne devrait pas être une erreur possible.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    val valid = Dates.isValidTime(value)

    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                .clickable { open = true }
                .defaultMinSize(minHeight = 56.dp)
                .padding(horizontal = 14.dp)
        ) {
            Text("🕐", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = if (valid) value else "Choisir une heure",
                style = MaterialTheme.typography.bodyLarge,
                color = if (valid) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            )
        }
    }

    if (open) {
        val start = runCatching { LocalTime.parse(value) }.getOrNull() ?: LocalTime.of(9, 0)
        val state = rememberTimePickerState(
            initialHour = start.hour,
            initialMinute = start.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(label) },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) { TimePicker(state = state) }
            },
            confirmButton = {
                TextButton(onClick = {
                    onChange("%02d:%02d".format(state.hour, state.minute))
                    open = false
                }) { Text("Choisir") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Annuler") } }
        )
    }
}
