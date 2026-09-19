package com.aifacemate.face.model

/** 眼睛造型。以“发光描边”为基本语言，闭眼用弧形而不是图片资源。 */
enum class EyeShape {
    /** 圆环眼，标准睁眼（设计图 01 Idle）。 */
    OPEN,

    /** 更大的圆环眼，用于惊讶 / 倾听。 */
    WIDE,

    /** 上弧 ^ ^，开心眯眼（设计图 02 Happy）。 */
    HAPPY_ARC,

    /** 下弧 ︶ ︶，平静闭眼（设计图 13 Sleeping）。 */
    CALM_ARC,

    /** 半睁眼，下弧 + 上眼睑横线（设计图 12 Sleepy）。 */
    HALF_LID,

    /** 一条横线，完全闭合。 */
    CLOSED,

    /** 爱心眼（设计图 11 Love）。 */
    HEART,

    /** 星星眼，期待 / 撒娇（设计图 30、37）。 */
    STAR,

    /** 螺旋眼，晕乎（设计图 68）。 */
    SPIRAL,

    /** 叉叉眼，拒绝（设计图 48）。 */
    CROSS,
}

/** 猫嘴造型。 */
enum class MouthShape {
    /** 经典猫嘴 ω。 */
    CAT_W,

    /** 微笑弧 ‿。 */
    SMILE_ARC,

    /** 张嘴笑（露出腮红/舌头）。 */
    OPEN_SMILE,

    /** 小圆嘴。 */
    SMALL_O,

    /** 一条平线。 */
    FLAT,

    /** 撇嘴 / 难过。 */
    FROWN,

    /** 吐舌。 */
    TONGUE,

    /** 波浪嘴，委屈 / 迷糊（设计图 43、70）。 */
    WAVY,
}

/** 屏幕上的情绪符号，对齐设计图里的 ? ! zzz 声波 水滴 星星 爱心 怒气 省略号。 */
enum class FaceMark {
    QUESTION,
    EXCLAMATION,
    ZZZ,
    WAVE,
    TEAR,
    SWEAT,
    SPARKLE,
    HEART,
    ANGER,
    DOTS,
    /** 充电闪电。 */
    BOLT,
    /** 离线禁用标记（圆圈加斜杠）。 */
    SLASH_CIRCLE,
    /** 灵光一现的灯泡（设计图 34）。 */
    LIGHT_BULB,
    /** 守护盾牌（设计图 58）。 */
    SHIELD,
    /** 保密锁（设计图 49）。 */
    LOCK,
    /** 点赞（设计图 46、47）。 */
    THUMB_UP,
    /** 猫爪（设计图 38、42、44、62）。 */
    PAW,
    /** 思绪云朵（设计图 50、54）。 */
    CLOUD,
    /** 温度计，生病（设计图 69）。 */
    THERMOMETER,
    /** 屏幕四角扫描框（设计图 71）。 */
    SCAN_FRAME,
    /** 转圈圈，处理中（设计图 72）。 */
    SPINNER,
    /** 进度条，升级（设计图 75）。 */
    PROGRESS_BAR,
    /** 彩带，庆祝（设计图 51）。 */
    CONFETTI,
    /** 红色叉，拒绝（设计图 48）。 */
    CROSS_OUT,
    /** 惊叹爆发线，受惊（设计图 41）。 */
    ALARM,
    /** 短促的强调线（设计图 29、40、47、64）。 */
    SPARK_LINES,
    /** 上升箭头，升级（设计图 75）。 */
    ARROW_UP,
    /** 循环箭头，同步（设计图 74）。 */
    SYNC_ARROW,
    /** 录制红点（设计图 73）。 */
    REC_DOT,
    /** 时钟，守候（设计图 82）。 */
    CLOCK,
    /** 音符，哼唱 / 舒适（设计图 89、97）。 */
    MUSIC_NOTE,
    /** 满电电池，充电完成（设计图 98）。 */
    BATTERY_FULL,
    /** 太阳，迎接新的一天（设计图 100）。 */
    SUN,
    /** 玫瑰花，烦恼（设计图 92）。 */
    ROSE,
}

/** 符号配色，避免在模型层引入 Compose 颜色类型。 */
enum class MarkTint {
    /** 与眼睛一致的冷白光。 */
    GLOW,
    PINK,
    BLUE,
    RED,
    AMBER,
    GREEN,
}

/**
 * 单只眼睛的参数。
 *
 * 坐标类参数以屏幕面板尺寸为单位（x 为面板宽、y 为面板高的比例），
 * 这样同一套参数可以在任意分辨率的屏幕上等比渲染。
 */
data class EyeParams(
    val shape: EyeShape = EyeShape.OPEN,
    /** 相对标准眼半径的缩放，1.0 为标准大小。 */
    val radius: Float = 1f,
    /** 睁眼程度，1.0 全睁、0.0 闭合，用于眨眼动画。 */
    val openness: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    /** 瞳孔/高光的水平偏移，用于“看某个方向”。 */
    val pupilOffsetX: Float = 0f,
    val pupilOffsetY: Float = 0f,
    /** 瞳孔可见度，0 表示纯圆环眼（设计图默认风格）。 */
    val pupilAlpha: Float = 0f,
    /** 发光强度，1.0 为标准。 */
    val glow: Float = 1f,
    /** 眼型倾斜角度，用于害羞 / 困惑等不对称表情。 */
    val tiltDegrees: Float = 0f,
)

/** 猫嘴参数。 */
data class MouthParams(
    val shape: MouthShape = MouthShape.CAT_W,
    val scale: Float = 1f,
    /** 张嘴程度，用于说话口型联动。 */
    val openness: Float = 0.35f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    /** 是否显示舌头（张嘴笑 / 吐舌）。 */
    val showTongue: Boolean = false,
)

/**
 * 屏幕上的情绪符号。
 *
 * [offsetX] / [offsetY] 以面板中心为原点，单位为面板宽高的比例。
 */
data class MarkParams(
    val mark: FaceMark,
    val alpha: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1f,
    val tint: MarkTint = MarkTint.GLOW,
    val tiltDegrees: Float = 0f,
)

/**
 * 一帧猫脸的完整参数。渲染器只读这个结构，表情库只写这个结构，
 * 两者解耦后就能做到“新增表情不改渲染代码”。
 */
data class FaceParams(
    /** 头部倾斜，单位度。 */
    val tiltDegrees: Float = 0f,
    /** 整体缩放，>1 表示凑近，<1 表示后退（用于呼吸与亲昵动作）。 */
    val scale: Float = 1f,
    /** 整体垂直偏移，单位为面板高的比例。 */
    val offsetY: Float = 0f,
    val leftEye: EyeParams = EyeParams(),
    val rightEye: EyeParams = EyeParams(),
    val mouth: MouthParams = MouthParams(),
    /** 腮红强度。 */
    val blushAlpha: Float = 0f,
    /** 屏幕压暗程度，1.0 为最暗（睡眠 / 离线）。 */
    val panelDim: Float = 0f,
    /**
     * 耳朵发光强度，0 表示熄灭。
     *
     * 耳朵是机身上的硬件灯效，只有 [com.aifacemate.face.render.FaceSurface.PRODUCT_MOCKUP]
     * 概念图会画出来；实机屏幕不画耳朵，这个参数在那里不生效。
     */
    val earGlow: Float = 1f,
    val marks: List<MarkParams> = emptyList(),
) {
    /** 返回参数中所有越界的项，空列表表示合法。表情库与 Face Pack 导出都靠它做校验。 */
    fun validate(): List<String> {
        val problems = mutableListOf<String>()

        fun check(name: String, value: Float, range: ClosedFloatingPointRange<Float>) {
            if (value.isNaN() || value !in range) {
                problems += "$name=$value 超出 $range"
            }
        }

        fun checkEye(name: String, eye: EyeParams) {
            check("$name.radius", eye.radius, 0.2f..1.8f)
            check("$name.openness", eye.openness, 0f..1f)
            check("$name.offsetX", eye.offsetX, -0.6f..0.6f)
            check("$name.offsetY", eye.offsetY, -0.6f..0.6f)
            check("$name.pupilOffsetX", eye.pupilOffsetX, -1f..1f)
            check("$name.pupilOffsetY", eye.pupilOffsetY, -1f..1f)
            check("$name.pupilAlpha", eye.pupilAlpha, 0f..1f)
            check("$name.glow", eye.glow, 0f..2f)
            check("$name.tiltDegrees", eye.tiltDegrees, -60f..60f)
        }

        check("tiltDegrees", tiltDegrees, -60f..60f)
        check("scale", scale, 0.5f..1.5f)
        check("offsetY", offsetY, -0.5f..0.5f)
        checkEye("leftEye", leftEye)
        checkEye("rightEye", rightEye)

        check("mouth.scale", mouth.scale, 0.2f..2.5f)
        check("mouth.openness", mouth.openness, 0f..1f)
        check("mouth.offsetX", mouth.offsetX, -0.6f..0.6f)
        check("mouth.offsetY", mouth.offsetY, -0.6f..0.6f)

        check("blushAlpha", blushAlpha, 0f..1f)
        check("panelDim", panelDim, 0f..1f)
        check("earGlow", earGlow, 0f..2f)

        marks.forEachIndexed { index, mark ->
            check("marks[$index].alpha", mark.alpha, 0f..1f)
            check("marks[$index].offsetX", mark.offsetX, -1f..1f)
            check("marks[$index].offsetY", mark.offsetY, -1f..1f)
            check("marks[$index].scale", mark.scale, 0.2f..3f)
            check("marks[$index].tiltDegrees", mark.tiltDegrees, -90f..90f)
        }

        return problems
    }
}
