package com.aifacemate.face.feature.aibrain.presentation
import com.aifacemate.face.feature.aibrain.presentation.theme.*

import com.aifacemate.face.feature.aibrain.presentation.theme.BrainStyle


import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.aifacemate.face.resources.*
import org.jetbrains.compose.resources.painterResource

/** Cropped artwork from the user-supplied v3 board, not approximated brand logos. */
@Composable
internal fun ProviderBrandIcon(providerId: String, size: Dp) {
    val resource = when (providerId) {
        "openai" -> Res.drawable.brain_provider_openai
        "claude" -> Res.drawable.brain_provider_claude
        "deepseek" -> Res.drawable.brain_provider_deepseek
        "qwen" -> Res.drawable.brain_provider_qwen
        "kimi" -> Res.drawable.brain_provider_kimi
        "zhipu" -> Res.drawable.brain_provider_zhipu
        "minimax" -> Res.drawable.brain_provider_minimax
        "volcengine" -> Res.drawable.brain_provider_volcengine
        "tencent" -> Res.drawable.brain_provider_tencent
        "bailian" -> Res.drawable.brain_provider_bailian
        else -> null
    }
    if (resource == null) Text("•••", color = BrainStyle.Gold, fontSize = 20.sp)
    else Image(painterResource(resource), null, Modifier.size(size).clip(RoundedCornerShape(size * .18f)))
}
