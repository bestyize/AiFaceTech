package com.aifacemate.face.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aifacemate.face.feature.lifecenter.LifeColors

/**
 * 轻提示：贴在屏幕底部的一条深色胶囊，几秒后由调用方清掉。
 *
 * 用 [imePadding] 让它在键盘弹起时浮到键盘上方，不会被挡住。
 */
@Composable
fun AppToast(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxSize().imePadding().padding(bottom = 26.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Text(
            message,
            Modifier.widthIn(max = 460.dp)
                .shadow(18.dp, RoundedCornerShape(50), clip = false)
                .clip(RoundedCornerShape(50))
                .background(Color(0xF2141820))
                .border(1.dp, LifeColors.Amber.copy(alpha = .35f), RoundedCornerShape(50))
                .padding(horizontal = 18.dp, vertical = 10.dp),
            color = Color.White,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
