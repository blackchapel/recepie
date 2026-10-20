package com.blackchapel.recipai.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Text styles from the prototype. Colors come from [RecipaiColors] at use. */
object RecipaiType {
    val largeTitle = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, lineHeight = 36.sp, letterSpacing = (-0.035).em)
    val detailTitle = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.ExtraBold, fontSize = 31.sp, lineHeight = 33.sp, letterSpacing = (-0.035).em)
    val section = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 25.sp, letterSpacing = (-0.02).em)
    val sheetTitle = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 24.sp, letterSpacing = (-0.015).em)
    val secTitle = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 23.sp, letterSpacing = (-0.015).em)
    val cardTitle = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 18.sp, letterSpacing = (-0.01).em)
    val rowTitle = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.5.sp, lineHeight = 19.sp, letterSpacing = (-0.01).em)
    val statValue = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 21.sp, fontFeatureSettings = "tnum")
    val tileTitle = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 19.sp, letterSpacing = (-0.01).em)

    val navTitle = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.5.sp, lineHeight = 20.sp)
    val body = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Normal, fontSize = 15.5.sp, lineHeight = 24.sp)
    val bodySmall = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp)
    val ui = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp)
    val uiSmall = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, lineHeight = 18.sp)
    val cell = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Medium, fontSize = 15.5.sp, lineHeight = 20.sp)
    val tab = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp, lineHeight = 13.sp)
    val tag = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 15.sp)
    /** Uppercase group labels ("INGREDIENTS"). */
    val label = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 13.sp, letterSpacing = 0.09.em)
    val groupLabel = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 15.sp, letterSpacing = 0.06.em)

    val mono = TextStyle(fontFamily = MonoFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, fontFeatureSettings = "tnum")
    val monoSmall = TextStyle(fontFamily = MonoFamily, fontWeight = FontWeight.Medium, fontSize = 11.5.sp, lineHeight = 15.sp, fontFeatureSettings = "tnum")
    val amount = TextStyle(fontFamily = MonoFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, lineHeight = 19.sp, fontFeatureSettings = "tnum")
    val amountBig = TextStyle(fontFamily = MonoFamily, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp, fontFeatureSettings = "tnum")
    val eyebrow = TextStyle(fontFamily = MonoFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 13.sp, letterSpacing = 0.12.em)
    val textarea = TextStyle(fontFamily = MonoFamily, fontWeight = FontWeight.Normal, fontSize = 13.5.sp, lineHeight = 21.sp)
}
