# Compose 实现说明

## 页面

建议：

```
AiBrainRoute
AiBrainScreen
```

---

## 组件

```
feature/aibrain/

AiBrainScreen.kt

components/

CurrentBrainCard.kt
ProviderCarousel.kt
ModelCarousel.kt
CapabilityPanel.kt
SecretConfigCard.kt
ThinkingModeSelector.kt
```

---

## 数据模型

```kotlin
data class AIProvider(
    val id:String,
    val name:String,
    val logo:String,
)

data class AIModel(
    val id:String,
    val name:String,
    val description:String,
    val recommended:Boolean
)

data class BrainConfig(
    val provider:String,
    val model:String,
    val baseUrl:String,
    val apiKey:String
)
```

---

## 横屏布局

推荐：

左：

40%

右：

60%

使用：

- Row
- Weight
- WindowSizeClass

不要固定像素。

---

## 横向列表

Provider:

LazyRow

Model:

LazyRow

未来支持：

- 更多厂商
- 动态模型数量
- 服务端配置

---

## 状态管理

建议：

ViewModel

管理：

- 当前 Provider
- 当前 Model
- 输入配置
- 连接状态

UI 不直接修改数据。

---

## 路由

从：

lifeCenter

进入：

brain

返回：

lifeCenter
