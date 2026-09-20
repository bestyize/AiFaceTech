# Compose 页面结构与组件说明

## 1. 建议目录

如果现有工程已有架构，优先融入，不要另起一套。

```text
feature/lifecenter/
├── LifeCenterRoute.kt
├── LifeCenterScreen.kt
├── LifeCenterViewModel.kt
├── model/
│   ├── CompanionProfile.kt
│   ├── LifeCenterFeature.kt
│   └── RecentChange.kt
└── components/
    ├── LifeCenterTopBar.kt
    ├── CompanionProfileCard.kt
    ├── StatusBadge.kt
    ├── FeatureCard.kt
    ├── FeatureGrid.kt
    ├── RecentChangesTimeline.kt
    └── PrimaryActionCard.kt
```

KMP 项目优先将 UI 与纯模型放在 `commonMain`。

## 2. Route
```kotlin
@Composable
fun LifeCenterRoute(
    onBackToLiving: () -> Unit,
    onOpenBrain: () -> Unit,
    onOpenVoice: () -> Unit,
    onOpenPersonality: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenSkills: () -> Unit,
    onOpenRhythm: () -> Unit,
    onOpenDeviceSystem: () -> Unit,
)
```

`Route` 负责状态与导航；`Screen` 负责视觉。

## 3. UI State
```kotlin
data class LifeCenterUiState(
    val profile: CompanionProfile,
    val features: List<LifeCenterFeature>,
    val recentChanges: List<RecentChange>,
    val loading: Boolean = false
)
```

## 4. 横屏宽屏布局
```text
┌──────────────────────────────────────────────────┐
│ TopBar                                           │
├──────────────────┬───────────────────────────────┤
│                  │  AI 大脑   声音               │
│ Companion        │  人格      记忆               │
│ Profile Card     │  表情      能力               │
│                  │  节律      设备               │
│                  ├───────────────────────────────┤
│ 进入陪伴  设置   │ 最近变化                      │
└──────────────────┴───────────────────────────────┘
```

建议左侧约 40%～43%，右侧约 57%～60%。

## 5. 窄屏布局
```text
TopBar
↓
CompanionProfileCard
↓
FeatureGrid
↓
RecentChanges
↓
Actions
```

## 6. 宽度判断
```kotlin
BoxWithConstraints {
    val wide = maxWidth >= 840.dp
    if (wide) LifeCenterWideLayout(...) else LifeCenterCompactLayout(...)
}
```

## 7. Feature 数据模型
```kotlin
enum class LifeFeatureType {
    Brain, Voice, Personality, Memory,
    Appearance, Skills, Rhythm, DeviceSystem
}

data class LifeCenterFeature(
    val type: LifeFeatureType,
    val title: String,
    val subtitle: String,
    val accent: FeatureAccent
)
```

卡片必须由列表数据驱动，方便未来增加摄像头、运动、充电底座等模块。

## 8. 角色卡
建议展示：
- 角色形象
- 名称
- 陪伴天数
- 状态
- 人格
- 陪伴语

优先复用现有 Face Engine / 表情渲染组件，不维护第二份静态角色图。

## 9. 最近变化
统一模型：
```text
Icon
Title
Description
Timestamp
```

未来可由 Memory / Growth / Face Pack / Personality 提供数据。

## 10. 动效
- 页面进入：150～250ms 淡入
- 卡片：轻微 scale / alpha
- 角色脸：保持 Idle 微动作
- 避免大面积飞入、粒子特效和复杂 3D

## 11. 样式 Token
统一管理：
```text
PageBackground
CardBackground
CardBorder
PrimaryAmber
AccentPink
AccentPurple
AccentBlue
TextPrimary
TextSecondary
```

## 12. 可访问性
- 可点击区域 >= 48dp
- contentDescription
- 字号放大不应严重错位
- 主文本对比度足够
