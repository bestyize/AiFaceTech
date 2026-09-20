package com.aifacemate.face.feature.lifecenter

import androidx.compose.ui.graphics.Color

enum class LifeRoute { Living, LifeCenter, Brain, Voice, Personality, Memory, Appearance, Skills, Rhythm, DeviceSystem }
data class CompanionProfile(val name: String = "小星", val status: String = "本地陪伴", val personality: String = "等待认识你", val message: String = "有我在，每天都是好日子")
data class LifeCenterFeature(val route: LifeRoute, val title: String, val subtitle: String, val symbol: String, val accent: Color = LifeColors.Amber)
data class RecentChange(val title: String, val description: String)
data class LifeCenterUiState(val profile: CompanionProfile = CompanionProfile(), val features: List<LifeCenterFeature> = defaultFeatures, val recentChanges: List<RecentChange> = emptyList())
object LifeColors {
    val Background = Color(0xFF0D1017)
    val Card = Color(0xFF191C24)
    val Border = Color(0xFF30323A)
    val Amber = Color(0xFFFFC46B)
    val Pink = Color(0xFFF278A6)
    val Purple = Color(0xFFBB91ED)
    val Secondary = Color(0xFFA7A9B4)
}
val defaultFeatures = listOf(
    LifeCenterFeature(LifeRoute.Brain, "AI 大脑", "模型、思考与能力配置", "◎"),
    LifeCenterFeature(LifeRoute.Voice, "声音", "音色与语音 · 待接入", "♫"),
    LifeCenterFeature(LifeRoute.Personality, "人格", "探索它的性格 · 待开放", "♡", LifeColors.Pink),
    LifeCenterFeature(LifeRoute.Memory, "记忆", "记录相处的点滴 · 待接入", "▤"),
    LifeCenterFeature(LifeRoute.Appearance, "表情与外观", "100 个表情 / 生命感 Alpha", "✧"),
    LifeCenterFeature(LifeRoute.Skills, "能力", "更多生活本领 · 待接入", "☆"),
    LifeCenterFeature(LifeRoute.Rhythm, "生活节律", "作息与安静时光 · 待开放", "☾", LifeColors.Purple),
    LifeCenterFeature(LifeRoute.DeviceSystem, "设备与系统", "账号 / 网络 / 硬件 · 待接入", "⚙︎"),
)
