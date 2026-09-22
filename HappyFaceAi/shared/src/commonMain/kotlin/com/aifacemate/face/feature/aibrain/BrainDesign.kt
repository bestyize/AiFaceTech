package com.aifacemate.face.feature.aibrain

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
internal data class BrainLayout(val compact: Boolean, val labelledRail: Boolean, val artwork: Boolean)
internal fun brainLayout(width: Float, height: Float) = BrainLayout(
    compact = height < 400f || width < 600f,
    labelledRail = width >= 720f,
    artwork = width >= 1000f || (width >= 780f && width / height > 2f),
)

internal fun Modifier.brainPanel(selected: Boolean = false): Modifier = this
    .drawBehind {
        if (selected) {
            for (i in 1..4) {
                val spread = i.dp.toPx()
                drawRoundRect(BrainStyle.Gold.copy(alpha = .035f), Offset(-spread, -spread),
                    Size(size.width + spread * 2, size.height + spread * 2), CornerRadius(14.dp.toPx() + spread))
            }
        }
    }
    .clip(BrainStyle.Shape)
    .background(Brush.linearGradient(if (selected) listOf(Color(0xFF494031), Color(0xFF1D2020))
        else listOf(Color(0xFF222525), Color(0xFF131617))))
    .border(1.dp, if (selected) BrainStyle.Gold else BrainStyle.Border, BrainStyle.Shape)

@Composable
internal fun BrainButton(text: String, modifier: Modifier = Modifier, primary: Boolean = false,
                         enabled: Boolean = true, onClick: () -> Unit) {
    Box(modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp))
        .background(if (primary && enabled) Brush.linearGradient(listOf(BrainStyle.LightGold, Color(0xFFC69850)))
            else Brush.linearGradient(listOf(BrainStyle.Surface, BrainStyle.Background)))
        .border(1.dp, BrainStyle.Gold.copy(alpha = if (enabled) .65f else .18f), RoundedCornerShape(10.dp))
        .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center) {
        Text(text, color = if (!enabled) BrainStyle.Muted else if (primary) Color(0xFF221D13) else BrainStyle.Text,
            fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** Monochrome line icons instead of platform-dependent emoji. Paths use a 24-unit viewport. */
@Composable
internal fun BrainIcon(kind: String, modifier: Modifier = Modifier, color: Color = BrainStyle.Gold) {
    Canvas(modifier.size(24.dp)) {
        scale(size.width / 24f, size.height / 24f, Offset.Zero) {
            val stroke = Stroke(1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            fun line(x: Float, y: Float, xx: Float, yy: Float) = drawLine(color, Offset(x,y), Offset(xx,yy), 1.6f, StrokeCap.Round)
            fun path(vararg points: Float) {
                val p = Path().apply { moveTo(points[0],points[1]); for(i in 2 until points.size step 2) lineTo(points[i],points[i+1]) }
                drawPath(p,color,style=stroke)
            }
            when(kind) {
                "brain", "deep" -> {
                    val p = Path().apply {
                        moveTo(11f,4f); cubicTo(5f,0f,3f,6f,5f,8f); cubicTo(0f,10f,2f,15f,5f,16f)
                        cubicTo(3f,21f,9f,24f,11f,20f); close()
                        moveTo(13f,4f); cubicTo(19f,0f,21f,6f,19f,8f); cubicTo(24f,10f,22f,15f,19f,16f)
                        cubicTo(21f,21f,15f,24f,13f,20f); close()
                    }; drawPath(p,color,style=stroke)
                    line(6f,7f,9f,9f); line(5f,14f,9f,13f); line(15f,8f,18f,6f); line(15f,14f,19f,15f)
                }
                "key" -> {
                    drawCircle(color,4.5f,Offset(16f,7f),style=stroke)
                    path(12f,10f,3f,19f,3f,22f,6f,22f,8f,20f,8f,18f,10f,18f,14f,13f)
                }
                "link" -> {
                    rotate(-40f,Offset(12f,12f)) {
                        drawRoundRect(color,Offset(2f,7f),Size(12f,9f),CornerRadius(4.5f),style=stroke)
                        drawRoundRect(color,Offset(10f,7f),Size(12f,9f),CornerRadius(4.5f),style=stroke)
                    }
                }
                "thinking" -> { for(y in listOf(5f,12f,19f)) line(2f,y,22f,y)
                    for((x,y) in listOf(7f to 5f,16f to 12f,9f to 19f)) { drawCircle(BrainStyle.Background,3f,Offset(x,y)); line(x,y-3,x,y+3) }
                }
                "fast" -> path(14f,2f,4f,14f,11f,14f,9f,23f,21f,10f,13f,10f,14f,2f)
                "balanced" -> {
                    line(12f,2f,12f,21f); line(4f,5f,20f,5f); line(7f,22f,17f,22f)
                    path(5f,6f,1f,15f,9f,15f,5f,6f); path(19f,6f,15f,15f,23f,15f,19f,6f)
                }
                "globe" -> {
                    drawCircle(color,10f,Offset(12f,12f),style=stroke)
                    drawOval(color,Offset(7f,2f),Size(10f,20f),style=stroke); line(2f,12f,22f,12f)
                }
                "memory" -> {
                    drawOval(color,Offset(4f,3f),Size(16f,6f),style=stroke)
                    line(4f,6f,4f,19f); line(20f,6f,20f,19f)
                    drawArc(color,0f,180f,false,Offset(4f,10f),Size(16f,6f),style=stroke)
                    drawArc(color,0f,180f,false,Offset(4f,16f),Size(16f,6f),style=stroke)
                }
                "calendar" -> {
                    drawRoundRect(color,Offset(3f,5f),Size(18f,17f),CornerRadius(3f),style=stroke)
                    line(3f,10f,21f,10f); line(8f,2f,8f,7f); line(16f,2f,16f,7f)
                    drawCircle(color,1f,Offset(8f,15f)); drawCircle(color,1f,Offset(15f,15f))
                }
                "camera" -> { path(3f,7f,7f,7f,9f,3f,15f,3f,17f,7f,21f,7f,21f,21f,3f,21f,3f,7f)
                    drawCircle(color,5f,Offset(12f,13f),style=stroke)
                }
                "voice" -> { drawRoundRect(color,Offset(9f,2f),Size(6f,13f),CornerRadius(3f),style=stroke)
                    drawArc(color,0f,180f,false,Offset(5f,7f),Size(14f,12f),style=stroke); line(12f,19f,12f,23f); line(8f,23f,16f,23f)
                }
                "home" -> { path(2f,11f,12f,2f,22f,11f); path(5f,9f,5f,22f,10f,22f,10f,15f,15f,15f,15f,22f,20f,22f,20f,9f) }
                "file" -> { path(5f,2f,15f,2f,20f,7f,20f,22f,5f,22f,5f,2f); path(14f,2f,14f,8f,20f,8f); line(9f,13f,16f,13f); line(9f,17f,16f,17f) }
                "back" -> { path(10f,5f,3f,12f,10f,19f); line(3f,12f,22f,12f) }
                "planet" -> { drawCircle(color,8f,Offset(12f,12f),style=stroke); rotate(-30f,Offset(12f,12f)) { drawOval(color,Offset(0f,7f),Size(24f,10f),style=stroke) } }
                else -> { path(12f,2f,22f,7f,22f,18f,12f,23f,2f,18f,2f,7f,12f,2f); path(2f,7f,12f,13f,22f,7f); line(12f,13f,12f,23f) }
            }
        }
    }
}
