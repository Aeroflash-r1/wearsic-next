package com.wearsic.next.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Wearsic Next palette — a faithful port of the original Wearsic
 * "Vibrant / Midnight Lavender" production theme.
 *
 * OLED black stays black: the panel switches black pixels off, so the
 * pure-black backdrop is measurably kinder to battery and thermals than any
 * tinted background. Surfaces deepen one step for separation; the lavender
 * primary is tuned for small-size legibility.
 */

// ── Backdrop / surfaces ────────────────────────────────────────────────────
val WearsicBlack: Color = Color(0xFF000000)
val WearsicAppBackground: Color = WearsicBlack
val WearsicCanvasDark = Color(0xFF0A0A0A)
val WearsicSurface = Color(0xFF141318)
val WearsicSurfaceActive = Color(0xFF232027)
val WearsicSurfaceBorder = Color(0x1AFFFFFF) // white/10
val WearsicSurfaceBorderSubtle = Color(0x0DFFFFFF) // white/5
val WearsicSurfaceRaised = Color(0xFF1E1C22)

// ── Signature accents ──────────────────────────────────────────────────────
val WearsicVibrantLavender = Color(0xFFC4B0FF)
val WearsicViolet = Color(0xFF8A5CF6)
val WearsicLavenderTertiary = Color(0xFFB8A1FF)

// ── Glassmorphism ──────────────────────────────────────────────────────────
val WearsicGlassFill = Color(0x14FFFFFF) // white 8%
val WearsicGlassBorder = Color(0x2EFFFFFF) // white 18%

// ── Text tokens ────────────────────────────────────────────────────────────
val WearsicTextPrimary = Color(0xFFE6E1E5)
val WearsicTextPrimaryDark = Color(0xFF000000)
val WearsicTextSecondary = Color(0xFFC4B0FF)
val WearsicTextWhite80 = Color(0xCCFFFFFF)
val WearsicTextWhite60 = Color(0x99FFFFFF)
val WearsicTextWhite40 = Color(0x66FFFFFF)
val WearsicTextMuted = Color(0xFF938F99)

// ── Status ─────────────────────────────────────────────────────────────────
val WearsicError = Color(0xFFF2B8C6)
val WearsicSuccess = Color(0xFF9DB89A)
val WearsicWarning = Color(0xFFE8C547)

/**
 * The app-colour catalog shown in Settings — the same pleasant, OLED-friendly
 * shades the original Wearsic ships. ACCENT_AUTO makes the app accent follow
 * the current artwork (the dynamic-colour feature).
 */
val WearsicAppColorChoices: List<Pair<String, Color>> = listOf(
    "Lavender" to WearsicVibrantLavender,
    "Violet" to WearsicViolet,
    "Sky" to Color(0xFF8FD3FE),
    "Mint" to Color(0xFF7FD8B4),
    "Peach" to Color(0xFFF0A87E),
    "Rose" to Color(0xFFFF8FA3),
    "Amber" to Color(0xFFF2C879),
    "Coral" to Color(0xFFFF9E80),
    "Teal" to Color(0xFF5FD4C8),
    "Cyan" to Color(0xFF4DD0E1),
    "Blue" to Color(0xFF82B1FF),
    "Indigo" to Color(0xFF9FA8DA),
    "Green" to Color(0xFF8BC98B),
    "Pink" to Color(0xFFFF8A9B),
)

/** Name persisted in prefs for the dynamic (artwork) accent. */
const val ACCENT_AUTO = "Auto (artwork)"

/**
 * The app-wide accent. Screens read this through [LocalWearsicAccent]; the
 * root app composable resolves the persisted choice (and, for ACCENT_AUTO,
 * overlays the current artwork colour hoisted up from the player).
 */
val LocalWearsicAccent = staticCompositionLocalOf { WearsicVibrantLavender }

/** Container tone derived from the current accent (used behind accent icons). */
@Composable
fun wearsicAccentContainer(): Color {
    return androidx.compose.ui.graphics.lerp(LocalWearsicAccent.current, Color.Black, 0.55f)
}

/** Secondary text tone: lavender with the static palette. */
@Composable
fun wearsicSecondaryText(): Color = WearsicTextSecondary

/**
 * Live user preferences surfaced as Compose state, backed by
 * SharedPreferences so choices survive process death. Initialised once
 * from [WearsicPrefs.init] before the first composition.
 */
object WearsicPrefs {
    private var prefs: SharedPreferences? = null

    /** App accent colour name ([ACCENT_AUTO] or a name from WearsicAppColorChoices). */
    var accentName: String by mutableStateOf("Lavender")
        private set

    /** Slow disc rotation while a song plays (the vinyl feel). */
    var rotateArtwork: Boolean by mutableStateOf(true)
        private set

    /** Haptic ticks on press/click targets. */
    var haptics: Boolean by mutableStateOf(true)
        private set

    /** Invert the bezel/crown scroll direction (Settings > Bezel scroll). */
    var rotaryInverted: Boolean by mutableStateOf(false)
        private set

    /** Must be called before the first composition. */
    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.getSharedPreferences("wearsic_next_prefs", Context.MODE_PRIVATE)
            .also { p ->
                // v3 (0.3.2+): the accent is ALWAYS the signature lavender
                // on first launch. The artwork tint ("Auto") stays available
                // in Settings, but the app must OPEN looking exactly like
                // the original instead of inheriting whatever tint was
                // active before.
                val version = p.getInt(KEY_VERSION, 0)
                if (version < 3) {
                    accentName = "Lavender"
                    p.edit()
                        .putInt(KEY_VERSION, 3)
                        .putString(KEY_ACCENT, "Lavender")
                        .apply()
                } else {
                    accentName = p.getString(KEY_ACCENT, "Lavender") ?: "Lavender"
                }
                rotateArtwork = p.getBoolean(KEY_ROTATE, true)
                haptics = p.getBoolean(KEY_HAPTICS, true)
                rotaryInverted = p.getBoolean(KEY_ROTARY, false)
            }
    }

    fun chooseAccent(name: String) {
        accentName = name
        prefs?.edit()?.putString(KEY_ACCENT, name)?.apply()
    }

    fun setRotateEnabled(enabled: Boolean) {
        rotateArtwork = enabled
        prefs?.edit()?.putBoolean(KEY_ROTATE, enabled)?.apply()
    }

    fun setHapticsEnabled(enabled: Boolean) {
        haptics = enabled
        prefs?.edit()?.putBoolean(KEY_HAPTICS, enabled)?.apply()
    }

    fun setRotaryDirection(enabled: Boolean) {
        rotaryInverted = enabled
        prefs?.edit()?.putBoolean(KEY_ROTARY, enabled)?.apply()
    }

    /** Resolves the persisted accent name to a colour; null resolves to artwork accent. */
    fun accentColorOrNull(): Color? = when {
        accentName == ACCENT_AUTO -> null
        else -> WearsicAppColorChoices.firstOrNull { it.first == accentName }?.second
    }

    private const val KEY_ACCENT = "accentName"
    private const val KEY_ROTATE = "rotateArtwork"
    private const val KEY_HAPTICS = "haptics"
    private const val KEY_ROTARY = "rotaryInverted"
    private const val KEY_VERSION = "prefs_version"
}
