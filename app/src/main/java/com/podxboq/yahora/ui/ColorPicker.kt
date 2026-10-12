/*
 * Copyright (C) 2026 podxboq
 *
 * This file is part of Yahora.
 *
 * Yahora is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.podxboq.yahora.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import com.podxboq.yahora.R
import com.podxboq.yahora.data.Tag

/**
 * A colour as hue, saturation and lightness — the three things a person
 * adjusts when they say "a bit darker" or "less loud". Opaque always: a
 * see-through tab would show whatever happens to be behind the cloud.
 */
data class Hsl(val hue: Float, val saturation: Float, val lightness: Float) {

    fun toArgb(): Int = ColorUtils.HSLToColor(floatArrayOf(hue, saturation, lightness))

    /** Six hex digits, upper case, without the `#`. */
    fun toHex(): String = "%06X".format(toArgb() and 0xFFFFFF)

    companion object {
        fun fromArgb(argb: Int): Hsl {
            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(argb, hsl)
            return Hsl(hsl[0], hsl[1], hsl[2])
        }

        /** Six hex digits, with or without a leading `#`; null for anything else. */
        fun fromHex(text: String): Hsl? {
            val digits = text.trim().removePrefix("#")
            if (digits.length != 6 || digits.any { Character.digit(it, 16) < 0 }) return null
            return fromArgb(0xFF000000.toInt() or digits.toInt(16))
        }
    }
}

/**
 * Quick picks, so the common case is one tap. They are a starting point, not
 * a palette: any of them can be pushed anywhere with the sliders.
 */
private val Swatches = listOf(
    0xFFE53935, 0xFFFB8C00, 0xFFFDD835, 0xFF43A047,
    0xFF00ACC1, 0xFF1E88E5, 0xFF8E24AA, 0xFF6D4C41,
).map { it.toInt() }

/**
 * Picks any colour for [tag]: swatches for speed, sliders for anything else,
 * and a hex field for a colour known by its code. The preview is the tag's own
 * chip, drawn exactly as the cloud will draw it.
 *
 * Nothing here judges the choice. Contrast is the user's call, and the tab
 * carries no meaning that the name does not already carry.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPickerDialog(tag: Tag, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    val initial = Hsl.fromArgb(Swatches.first())
    var hue by rememberSaveable { mutableFloatStateOf(initial.hue) }
    var saturation by rememberSaveable { mutableFloatStateOf(initial.saturation) }
    var lightness by rememberSaveable { mutableFloatStateOf(initial.lightness) }
    var hexText by rememberSaveable { mutableStateOf(initial.toHex()) }

    val current = Hsl(hue, saturation, lightness)

    // Every control but the hex field rewrites the field, so it always shows the
    // colour on screen. The field itself is only ever rewritten by the user.
    fun select(hsl: Hsl, fromHexField: Boolean = false) {
        hue = hsl.hue
        saturation = hsl.saturation
        lightness = hsl.lightness
        if (!fromHexField) hexText = hsl.toHex()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.color_dialog_title, tag.name)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TagChipFace(
                        name = tag.name,
                        isFavorite = tag.isFavorite,
                        color = Color(current.toArgb()),
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Swatches.forEach { argb ->
                        Swatch(argb = argb, onClick = { select(Hsl.fromArgb(argb)) })
                    }
                }

                GradientSlider(
                    label = stringResource(R.string.color_hue),
                    value = hue,
                    valueRange = 0f..360f,
                    // Hue at full strength, whatever the other two are: with the
                    // saturation at zero the bar would otherwise be one grey.
                    brush = Brush.horizontalGradient(
                        (0..360 step 30).map { Color.hsl(it.toFloat(), 1f, 0.5f) },
                    ),
                    onValueChange = { select(current.copy(hue = it)) },
                )
                GradientSlider(
                    label = stringResource(R.string.color_saturation),
                    value = saturation,
                    valueRange = 0f..1f,
                    brush = Brush.horizontalGradient(
                        listOf(Color.hsl(hue, 0f, lightness), Color.hsl(hue, 1f, lightness)),
                    ),
                    onValueChange = { select(current.copy(saturation = it)) },
                )
                GradientSlider(
                    label = stringResource(R.string.color_lightness),
                    value = lightness,
                    valueRange = 0f..1f,
                    brush = Brush.horizontalGradient(
                        listOf(Color.Black, Color.hsl(hue, saturation, 0.5f), Color.White),
                    ),
                    onValueChange = { select(current.copy(lightness = it)) },
                )

                OutlinedTextField(
                    value = hexText,
                    onValueChange = { text ->
                        val digits = text.removePrefix("#").take(6).uppercase()
                        hexText = digits
                        Hsl.fromHex(digits)?.let { select(it, fromHexField = true) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.color_hex)) },
                    prefix = { Text("#") },
                    singleLine = true,
                    isError = Hsl.fromHex(hexText) == null,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Ascii,
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(current.toArgb()) }) {
                Text(stringResource(R.string.action_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun Swatch(argb: Int, onClick: () -> Unit) {
    val description = stringResource(R.string.color_swatch, "#%06X".format(argb and 0xFFFFFF))
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Color(argb))
            // A hairline keeps the white-ish and black-ish picks visible on
            // either theme.
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
    )
}

/**
 * A slider whose track is the range it selects from, so moving it shows which
 * colour lies where instead of asking the user to imagine it.
 */
@Composable
private fun GradientSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    brush: Brush,
    onValueChange: (Float) -> Unit,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .height(12.dp)
                    .clip(CircleShape)
                    .background(brush),
            )
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = label },
                colors = SliderDefaults.colors(
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                ),
            )
        }
    }
}
