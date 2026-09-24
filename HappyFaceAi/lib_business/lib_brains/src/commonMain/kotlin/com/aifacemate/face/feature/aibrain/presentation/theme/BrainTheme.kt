package com.aifacemate.face.feature.aibrain.presentation.theme
import com.aifacemate.face.feature.aibrain.presentation.theme.*

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.dp

internal object BrainStyle {
    val Background = Color(0xFF0C1011)
    val Surface = Color(0xFF191C1C)
    val Border = Color(0xFF48443A)
    val Gold = Color(0xFFF1C779)
    val LightGold = Color(0xFFFFE5AA)
    val Text = Color(0xFFF4F3EF)
    val Muted = Color(0xFFA7AAA8)
    val Green = Color(0xFF36D6AB)
    val Shape = RoundedCornerShape(14.dp)
}

/** Layout is driven by available dp, never by the screen's physical inch label. */
data class BrainLayout(val compact: Boolean, val labelledRail: Boolean, val artwork: Boolean)
fun brainLayout(width: Float, height: Float) = BrainLayout(
    compact = height < 400f || width < 600f,
    labelledRail = width >= 720f,
    artwork = width >= 1000f || (width >= 780f && width / height > 2f),
)

internal fun Modifier.brainPanel(selected: Boolean = false): Modifier = this
    .drawBehind {
        if (selected) for (i in 1..4) {
            val spread = i.dp.toPx()
            drawRoundRect(BrainStyle.Gold.copy(alpha = .035f), Offset(-spread, -spread),
                Size(size.width + spread * 2, size.height + spread * 2), CornerRadius(14.dp.toPx() + spread))
        }
    }
    .clip(BrainStyle.Shape)
    .background(Brush.linearGradient(if (selected) listOf(Color(0xFF494031), Color(0xFF1D2020))
        else listOf(Color(0xFF222525), Color(0xFF131617))))
    .border(1.dp, if (selected) BrainStyle.Gold else BrainStyle.Border, BrainStyle.Shape)
