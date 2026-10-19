package com.blackchapel.recipai.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.blackchapel.recipai.R

// Bundled variable fonts (res/font, SIL OFL; licenses in third_party/fonts).
// Each weight is one instance of the variable font.

@OptIn(ExperimentalTextApi::class)
private fun bricolage(weight: Int) = Font(
    R.font.bricolage_grotesque,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        // Semi-condensed, as in the design (font-stretch 92%).
        FontVariation.width(92f),
        FontVariation.Setting("opsz", 36f),
    ),
)

@OptIn(ExperimentalTextApi::class)
private fun geist(weight: Int) = Font(
    R.font.geist,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

@OptIn(ExperimentalTextApi::class)
private fun geistMono(weight: Int) = Font(
    R.font.geist_mono,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Titles, large titles, recipe names. */
val DisplayFamily = FontFamily(listOf(500, 600, 700, 800).map(::bricolage))

/** Body and UI text. */
val BodyFamily = FontFamily(listOf(400, 500, 600, 700).map(::geist))

/** Amounts, times, counts, eyebrows. */
val MonoFamily = FontFamily(listOf(400, 500, 600).map(::geistMono))
