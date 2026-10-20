package com.blackchapel.recipai.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.blackchapel.recipai.core.TagColor
import com.blackchapel.recipai.core.Tags

/** The design tokens from docs/DESIGN.md ("Tokens"). */
@Immutable
data class RecipaiColors(
    val isDark: Boolean,
    val canvas: Color,
    val surface: Color,
    val surface2: Color,
    val surface3: Color,
    val ink: Color,
    val body: Color,
    val mute: Color,
    val faint: Color,
    val line: Color,
    val lineStrong: Color,
    val saffron: Color,
    val paprika: Color,
    val basil: Color,
    val ube: Color,
    val sumac: Color,
    val ocean: Color,
    val danger: Color,
    /** Saffron-family text that stays readable on the canvas. */
    val accentInk: Color,
    /** The neutral primary button (and toasts). */
    val cta: Color,
    val onCta: Color,
    /** Translucent bars: top bar when scrolled, tab bar, bottom bars. */
    val bar: Color,
    /** Buttons over photos. */
    val glass: Color,
    val glassLine: Color,
    val scrim: Color,
    private val tagMix: Color,
    private val tagMixAmount: Float,
) {
    fun of(tag: TagColor): Color = when (tag) {
        TagColor.SAFFRON -> saffron
        TagColor.PAPRIKA -> paprika
        TagColor.BASIL -> basil
        TagColor.UBE -> ube
        TagColor.SUMAC -> sumac
        TagColor.OCEAN -> ocean
    }

    fun tag(tag: String): Color = of(Tags.colorOf(tag))

    /** Tag text: the tag color mixed toward ink for contrast. */
    fun tagText(c: Color): Color = lerp(c, tagMix, tagMixAmount)
}

val DarkColors = RecipaiColors(
    isDark = true,
    canvas = Color(0xFF0D0C0B),
    surface = Color(0xFF151311),
    surface2 = Color(0xFF1C1A17),
    surface3 = Color(0xFF27241F),
    ink = Color(0xFFF7F2EB),
    body = Color(0xFFD3CBBF),
    mute = Color(0xFFA39A8D),
    faint = Color(0xFF857D72),
    line = Color(0x14FFECD6),
    lineStrong = Color(0x29FFECD6),
    saffron = Color(0xFFFFB547),
    paprika = Color(0xFFFF6B3D),
    basil = Color(0xFF57D39A),
    ube = Color(0xFFB49CFF),
    sumac = Color(0xFFFF5C8D),
    ocean = Color(0xFF5BC4FF),
    danger = Color(0xFFFF5A4E),
    accentInk = Color(0xFFFFC56E),
    cta = Color(0xFFF7F2EB),
    onCta = Color(0xFF17120D),
    bar = Color(0xF2151311),
    glass = Color(0x8C0E0B09),
    glassLine = Color(0x24FFFFFF),
    scrim = Color(0x8C040302),
    tagMix = Color.White,
    tagMixAmount = 0.22f,
)

val LightColors = RecipaiColors(
    isDark = false,
    canvas = Color(0xFFF4F2EE),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFEFECE7),
    surface3 = Color(0xFFE4E0D9),
    ink = Color(0xFF18140F),
    body = Color(0xFF3E382F),
    mute = Color(0xFF655E53),
    faint = Color(0xFF7A7267),
    line = Color(0x17281C10),
    lineStrong = Color(0x2B281C10),
    saffron = Color(0xFFE3961A),
    paprika = Color(0xFFEC5427),
    basil = Color(0xFF1C9E66),
    ube = Color(0xFF7A5AF0),
    sumac = Color(0xFFE0396F),
    ocean = Color(0xFF1C92D3),
    danger = Color(0xFFD9342B),
    accentInk = Color(0xFFA35C00),
    cta = Color(0xFF18140F),
    onCta = Color(0xFFFBF8F3),
    bar = Color(0xF2FAF8F5),
    glass = Color(0x6B140F0A),
    glassLine = Color(0x3DFFFFFF),
    scrim = Color(0x52221A12),
    tagMix = Color.Black,
    tagMixAmount = 0.38f,
)

/** Text and icons on the flame gradient. */
val OnFlame = Color(0xFF1C0F06)

/** Text on the check/step "done" basil fill. */
val OnBasil = Color(0xFF062615)

/** The one accent: saffron -> paprika (55%) -> pink, used sparingly. */
val FlameBrush: Brush = Brush.linearGradient(
    0f to Color(0xFFFFB547),
    0.55f to Color(0xFFFF6B3D),
    1f to Color(0xFFFF5C8D),
    start = Offset.Zero,
    end = Offset.Infinite,
)
