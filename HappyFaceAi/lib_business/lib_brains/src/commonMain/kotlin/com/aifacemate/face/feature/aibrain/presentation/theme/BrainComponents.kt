package com.aifacemate.face.feature.aibrain.presentation.theme
import com.aifacemate.face.feature.aibrain.presentation.theme.*

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
