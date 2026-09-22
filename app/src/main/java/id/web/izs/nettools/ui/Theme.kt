package id.web.izs.nettools.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

/** App theme (colorscheme) options. Stored as a plain string in settings. */
object AppTheme {
    const val AMOLED = "amoled"
    const val DARK = "dark"
    const val DARKER = "darker"
    const val SAND = "sand"
    const val LIGHT = "light"

    val presets = listOf(
        "AMOLED (pure black)" to AMOLED,
        "Darker" to DARKER,
        "Dark" to DARK,
        "Sand (warm light)" to SAND,
        "Light gray" to LIGHT
    )

    fun isDark(theme: String): Boolean = theme == AMOLED || theme == DARK || theme == DARKER
}

/** Full-black dark theme for AMOLED screens. */
fun amoledScheme() = darkColorScheme(
    primary = Color(0xFF64B5F6),
    onPrimary = Color(0xFF0F2038),
    primaryContainer = Color(0xFF01579B),
    onPrimaryContainer = Color(0xFFD8EBFD),
    secondary = Color(0xFF4DD0E1),
    tertiary = Color(0xFF9D7CD8),
    error = Color(0xFFE06C75),
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B0B0B),
    surfaceContainer = Color(0xFF121212),
    surfaceContainerHigh = Color(0xFF1A1A1A),
    surfaceContainerHighest = Color(0xFF242424)
)

/** Darker cool-gray theme - the previous "Dark". Deep background (#101216),
 *  for those who prefer a dimmer screen. */
fun darkerScheme() = darkColorScheme(
    primary = Color(0xFF64B5F6),
    onPrimary = Color(0xFF0F2038),
    primaryContainer = Color(0xFF01579B),
    onPrimaryContainer = Color(0xFFD8EBFD),
    secondary = Color(0xFF4DD0E1),
    tertiary = Color(0xFF9D7CD8),
    error = Color(0xFFE06C75),
    background = Color(0xFF101216),
    surface = Color(0xFF101216),
    surfaceContainerLowest = Color(0xFF0B0D10),
    surfaceContainerLow = Color(0xFF15181D),
    surfaceContainer = Color(0xFF1A1E24),
    surfaceContainerHigh = Color(0xFF21262E),
    surfaceContainerHighest = Color(0xFF292F38)
)

/** Dark theme (default) - cool slate, lifted a step above pure black so
 *  surfaces stay readable. Blue-shifted grays throughout, no warm/red tint. */
fun darkScheme() = darkColorScheme(
    primary = Color(0xFF64B5F6),
    onPrimary = Color(0xFF0F2038),
    primaryContainer = Color(0xFF01579B),
    onPrimaryContainer = Color(0xFFD8EBFD),
    secondary = Color(0xFF4DD0E1),
    tertiary = Color(0xFF9D7CD8),
    error = Color(0xFFE06C75),
    background = Color(0xFF1B2129),
    surface = Color(0xFF1B2129),
    onBackground = Color(0xFFDFE6ED),
    onSurface = Color(0xFFDFE6ED),
    onSurfaceVariant = Color(0xFF8E99A5),
    surfaceContainerLowest = Color(0xFF0F1319),
    surfaceContainerLow = Color(0xFF161B22),
    surfaceContainer = Color(0xFF1F252E),
    surfaceContainerHigh = Color(0xFF29313B),
    surfaceContainerHighest = Color(0xFF333D48),
    outline = Color(0xFF475162)
)

/** Warm, slightly yellow light theme - bright but not glaring. */
fun sandScheme() = lightColorScheme(
    primary = Color(0xFF6D5D00),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF3E48C),
    onPrimaryContainer = Color(0xFF221C00),
    background = Color(0xFFF7EFDA),
    surface = Color(0xFFF7EFDA),
    surfaceContainerLowest = Color(0xFFFFF8E7),
    surfaceContainerLow = Color(0xFFF5EACD),
    surfaceContainer = Color(0xFFEFE1BE),
    surfaceContainerHigh = Color(0xFFE8D6AE),
    surfaceContainerHighest = Color(0xFFE0CC9E),
    outline = Color(0xFF7C7667)
)

/** Light gray theme - pure neutral gray, never pure white. */
fun lightGrayScheme() = lightColorScheme(
    background = Color(0xFFE7E7E7),
    surface = Color(0xFFE7E7E7),
    surfaceContainerLowest = Color(0xFFF2F2F2),
    surfaceContainerLow = Color(0xFFE1E1E1),
    surfaceContainer = Color(0xFFD9D9D9),
    surfaceContainerHigh = Color(0xFFCFCFCF),
    surfaceContainerHighest = Color(0xFFC6C6C6)
)

fun baseScheme(theme: String): ColorScheme = when (theme) {
    AppTheme.AMOLED -> amoledScheme()
    AppTheme.DARKER -> darkerScheme()
    AppTheme.SAND -> sandScheme()
    AppTheme.LIGHT -> lightGrayScheme()
    else -> darkScheme()
}

/** Theme sections the user may override, key to label. */
val CustomColorRoles = listOf(
    "background" to "Background",
    "surface" to "Surface",
    "surfaceContainer" to "Surface Container",
    "primary" to "Primary",
    "onPrimary" to "On Primary",
    "primaryContainer" to "Primary Container",
    "onPrimaryContainer" to "On Primary Container",
    "terminal" to "Terminal"
)

/** Default output-console background. Dark theme uses a panel one step darker
 *  than the app background (same cool hue family, not a near-black slab);
 *  other dark themes keep the classic #0D1117 terminal, light themes follow
 *  the theme surface. */
fun defaultTerminalBg(theme: String, scheme: ColorScheme): Color =
    if (theme == AppTheme.AMOLED) Color.Black
    else if (theme == AppTheme.DARK) scheme.surfaceContainerLow
    else if (AppTheme.isDark(theme)) Color(0xFF0D1117)
    else scheme.surfaceContainer

/** Normalize "#rgb", "#rrggbb" or "#aarrggbb" to "#AARRGGBB", else null. */
fun normalizeHex(input: String): String? {
    var h = input.trim().removePrefix("#")
    if (h.length == 3) h = h.map { "$it$it" }.joinToString("")
    if (h.length == 6) h = "FF$h"
    if (h.length != 8 || !h.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
    return "#" + h.uppercase()
}

fun hexToColor(hex: String): Color? {
    val n = normalizeHex(hex) ?: return null
    val digits = n.removePrefix("#")
    // Same pattern as izs-ssh schemeColorArgb: build an ARGB Int, then Color(Int).
    // NOTE: Color(ULong) is NOT ARGB — it expects a packed color and corrupts
    // the value (crashed Paint.setColor with "Invalid ID"). Never pass raw hex there.
    val argb: Int = when (digits.length) {
        6 -> (0xFF000000.toInt() or digits.toInt(16))
        8 -> digits.toUInt(16).toInt()
        else -> return null
    }
    return Color(argb)
}

fun colorToHex(color: Color): String {
    fun b(v: Float) = (v * 255).roundToInt().coerceIn(0, 255)
    val a = b(color.alpha)
    // Like izs-ssh argbToHex: keep alpha digits only when not opaque.
    return if (a == 255) "#%02X%02X%02X".format(b(color.red), b(color.green), b(color.blue))
    else "#%02X%02X%02X%02X".format(a, b(color.red), b(color.green), b(color.blue))
}

/** Apply user overrides on top of a base scheme. Invalid values are ignored. */
fun ColorScheme.withOverrides(o: Map<String, String>): ColorScheme {
    fun c(key: String, fallback: Color): Color = o[key]?.let { hexToColor(it) } ?: fallback
    return copy(
        background = c("background", background),
        surface = c("surface", surface),
        surfaceContainer = c("surfaceContainer", surfaceContainer),
        primary = c("primary", primary),
        onPrimary = c("onPrimary", onPrimary),
        primaryContainer = c("primaryContainer", primaryContainer),
        onPrimaryContainer = c("onPrimaryContainer", onPrimaryContainer)
    )
}
