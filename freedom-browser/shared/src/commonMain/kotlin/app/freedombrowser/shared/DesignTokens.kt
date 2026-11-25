package app.freedombrowser.shared

/**
 * Single source of truth for Freedom Browser's "Option A" visual language: black/anthracite
 * surfaces with a restrained turquoise accent. Android maps these to a Material 3 colour scheme;
 * the Windows UI receives them as CSS custom properties via [toCss].
 */
object DesignTokens {
    data class Palette(
        val background: Long,
        val surface: Long,
        /** Toolbar / tab strip background. */
        val toolbar: Long,
        val surfaceRaised: Long,
        val surfaceHover: Long,
        val outline: Long,
        val textPrimary: Long,
        val textSecondary: Long,
        val accent: Long,
        val onAccent: Long,
        val accentSoft: Long,
        val danger: Long,
        val warning: Long,
        val success: Long,
        val privateAccent: Long,
    )

    val Dark = Palette(
        background = 0xFF0B0C0E,
        surface = 0xFF15171B,
        toolbar = 0xFF191C21,
        surfaceRaised = 0xFF1F2328,
        surfaceHover = 0xFF292D33,
        outline = 0xFF292D33,
        textPrimary = 0xFFF5F5F5,
        textSecondary = 0xFFA0A5AD,
        accent = 0xFF2EC4B6,
        onAccent = 0xFF04201D,
        accentSoft = 0xFF12393A,
        danger = 0xFFFF6B6B,
        warning = 0xFFF2B84B,
        success = 0xFF4CD08A,
        privateAccent = 0xFFB39DDB,
    )

    val Light = Palette(
        background = 0xFFF6F7F8,
        surface = 0xFFFFFFFF,
        toolbar = 0xFFECEEF1,
        surfaceRaised = 0xFFECEEF0,
        surfaceHover = 0xFFE2E5E8,
        outline = 0xFFCDD2D7,
        textPrimary = 0xFF15171A,
        textSecondary = 0xFF5A6068,
        accent = 0xFF0F8F84,
        onAccent = 0xFFFFFFFF,
        accentSoft = 0xFFD4F1EE,
        danger = 0xFFC62828,
        warning = 0xFF9A6400,
        success = 0xFF1E7D4A,
        privateAccent = 0xFF6A4FB0,
    )

    const val RADIUS_SMALL = 6
    const val RADIUS_MEDIUM = 10
    const val RADIUS_LARGE = 16
    const val SPACE_XS = 4
    const val SPACE_S = 8
    const val SPACE_M = 12
    const val SPACE_L = 16
    const val SPACE_XL = 24
    const val TOUCH_TARGET_DP = 48
    const val TEXT_SMALL = 12
    const val TEXT_BODY = 14
    const val TEXT_TITLE = 18
    const val TEXT_DISPLAY = 28
    const val FONT_STACK = "\"Segoe UI\", \"Roboto\", system-ui, sans-serif"

    fun hex(argb: Long): String {
        val rgb = (argb and 0xFFFFFF).toString(16).padStart(6, '0')
        return "#$rgb"
    }

    fun toCss(palette: Palette): String = buildString {
        fun v(name: String, value: Long) = append("--$name:${hex(value)};")
        v("bg", palette.background); v("surface", palette.surface); v("toolbar", palette.toolbar); v("surface-raised", palette.surfaceRaised)
        v("surface-hover", palette.surfaceHover); v("outline", palette.outline); v("text", palette.textPrimary)
        v("text-2", palette.textSecondary); v("accent", palette.accent); v("on-accent", palette.onAccent)
        v("accent-soft", palette.accentSoft); v("danger", palette.danger); v("warning", palette.warning)
        v("success", palette.success); v("private", palette.privateAccent)
        append("--r-s:${RADIUS_SMALL}px;--r-m:${RADIUS_MEDIUM}px;--r-l:${RADIUS_LARGE}px;")
        append("--s-xs:${SPACE_XS}px;--s-s:${SPACE_S}px;--s-m:${SPACE_M}px;--s-l:${SPACE_L}px;--s-xl:${SPACE_XL}px;")
        append("--t-small:${TEXT_SMALL}px;--t-body:${TEXT_BODY}px;--t-title:${TEXT_TITLE}px;--t-display:${TEXT_DISPLAY}px;")
        append("--font:$FONT_STACK;")
    }
}
