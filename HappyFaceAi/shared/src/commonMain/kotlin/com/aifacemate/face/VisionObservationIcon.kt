package com.aifacemate.face

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

/** Quiet monitoring indicator, with a full touch target and no background or activity animation. */
@Composable
internal fun VisionObservationIcon(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(48.dp).semantics {
            contentDescription = "视觉观测，打开摄像头"
            stateDescription = "视觉监测中"
        },
    ) {
        Canvas(Modifier.size(18.dp)) {
            val ink = Color.White.copy(alpha = .32f)
            val stroke = size.width * .075f
            drawRoundRect(ink, Offset(size.width * .08f, size.height * .28f),
                Size(size.width * .84f, size.height * .58f), CornerRadius(size.width * .10f),
                style = Stroke(stroke))
            drawLine(ink, Offset(size.width * .30f, size.height * .27f),
                Offset(size.width * .36f, size.height * .14f), stroke, StrokeCap.Round)
            drawLine(ink, Offset(size.width * .36f, size.height * .14f),
                Offset(size.width * .64f, size.height * .14f), stroke, StrokeCap.Round)
            drawLine(ink, Offset(size.width * .64f, size.height * .14f),
                Offset(size.width * .70f, size.height * .27f), stroke, StrokeCap.Round)
            drawCircle(ink, size.width * .16f, Offset(size.width * .5f, size.height * .57f),
                style = Stroke(stroke))
        }
    }
}
