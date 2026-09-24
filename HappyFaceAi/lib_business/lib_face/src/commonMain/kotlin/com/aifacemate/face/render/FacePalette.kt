package com.aifacemate.face.render

import androidx.compose.ui.graphics.Color
import com.aifacemate.face.model.MarkTint

/** 屏幕配色。集中定义，后续做主题或不同 Face Pack 时只改这里。 */
object FacePalette {

    /** 眼睛与嘴部的主发光色。 */
    val Glow = Color(0xFFE9FCFF)

    /** 光晕颜色，比主发光更冷一点。 */
    val GlowHalo = Color(0xFF63E4FF)

    /** 黑玻璃屏的渐变两端。 */
    val PanelTop = Color(0xFF131317)
    val PanelBottom = Color(0xFF030305)

    /** 机身暖白外壳。 */
    val BezelLight = Color(0xFFFBF1E4)
    val BezelMid = Color(0xFFD8C5B0)
    val BezelShade = Color(0xFF9F8A74)

    val Blush = Color(0xFFFF7597)
    val Tongue = Color(0xFFFF8FA8)
    val EarGlow = Color(0xFFFFC46B)
    val EarCore = Color(0xFFFFE7BC)
    val Tear = Color(0xFF6FD0FF)
    val Danger = Color(0xFFFF5A6E)
    val Charge = Color(0xFF7CFF9E)
}

/** 情绪符号配色。 */
internal fun MarkTint.toColor(): Color = when (this) {
    MarkTint.GLOW -> FacePalette.Glow
    MarkTint.PINK -> FacePalette.Blush
    MarkTint.BLUE -> FacePalette.Tear
    MarkTint.RED -> FacePalette.Danger
    MarkTint.AMBER -> FacePalette.EarGlow
    MarkTint.GREEN -> FacePalette.Charge
}
