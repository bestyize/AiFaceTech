package com.aifacemate.face.model

/**
 * 表情参数库：表情编号 -> 渲染参数。
 *
 * 新增一个表情只需要在这里补一条参数，渲染器与 UI 都不需要改动。
 */
object ExpressionLibrary {

    /** 取某个表情的参数。所有 [FaceExpression] 都必须有对应参数。 */
    fun paramsFor(expression: FaceExpression): FaceParams = when (expression) {
        // ---------- Alpha 1：精修并冻结的 5 个高频表情 ----------

        // 01 平静：圆环眼 + 猫嘴 ω，无腮红，完全待机状态
        FaceExpression.IDLE -> FaceParams(
            leftEye = EyeParams(shape = EyeShape.OPEN, radius = 1f),
            rightEye = EyeParams(shape = EyeShape.OPEN, radius = 1f),
            mouth = MouthParams(shape = MouthShape.CAT_W, scale = 1f),
            earGlow = 1f,
        )

        // 02 开心：眯眼 ^^ + 微笑弧 + 腮红
        FaceExpression.HAPPY -> FaceParams(
            scale = 1.01f,
            leftEye = EyeParams(shape = EyeShape.HAPPY_ARC, radius = 1f),
            rightEye = EyeParams(shape = EyeShape.HAPPY_ARC, radius = 1f),
            mouth = MouthParams(shape = MouthShape.SMILE_ARC, scale = 1.05f),
            blushAlpha = 0.55f,
            earGlow = 1.15f,
        )

        // 06 好奇：一大一小两只眼睛 + 右上角问号 + 微微歪头
        FaceExpression.CURIOUS -> FaceParams(
            tiltDegrees = -6f,
            leftEye = EyeParams(
                shape = EyeShape.OPEN,
                radius = 1.15f,
                pupilAlpha = 0.95f,
                pupilOffsetX = -0.25f,
                pupilOffsetY = 0.1f,
            ),
            rightEye = EyeParams(shape = EyeShape.OPEN, radius = 0.68f),
            mouth = MouthParams(shape = MouthShape.CAT_W, scale = 0.85f),
            blushAlpha = 0.1f,
            earGlow = 1.05f,
            marks = listOf(
                MarkParams(FaceMark.QUESTION, offsetX = 0.3f, offsetY = -0.28f, scale = 0.9f),
            ),
        )

        // 13 睡眠：闭眼弧 ︶ + 压暗屏幕 + 熄灭耳朵 + zzz
        FaceExpression.SLEEPING -> FaceParams(
            scale = 0.985f,
            offsetY = 0.01f,
            leftEye = EyeParams(shape = EyeShape.CALM_ARC, radius = 0.95f),
            rightEye = EyeParams(shape = EyeShape.CALM_ARC, radius = 0.95f),
            mouth = MouthParams(shape = MouthShape.CAT_W, scale = 0.8f),
            blushAlpha = 0.12f,
            panelDim = 0.45f,
            earGlow = 0.3f,
            marks = listOf(
                MarkParams(FaceMark.ZZZ, offsetX = 0.26f, offsetY = -0.26f, scale = 0.95f),
            ),
        )

        // 22 打招呼：笑脸 + 吐舌 + 挥手声波
        FaceExpression.GREETING -> FaceParams(
            scale = 1.02f,
            tiltDegrees = 2f,
            leftEye = EyeParams(shape = EyeShape.HAPPY_ARC, radius = 1f),
            rightEye = EyeParams(shape = EyeShape.HAPPY_ARC, radius = 1f),
            mouth = MouthParams(shape = MouthShape.OPEN_SMILE, scale = 1.1f, showTongue = true),
            blushAlpha = 0.6f,
            earGlow = 1.25f,
            marks = listOf(
                MarkParams(FaceMark.WAVE, offsetX = 0.36f, offsetY = -0.05f, scale = 0.9f),
            ),
        )

        // ---------- 其余 20 个表情位：结构已就绪，参数待按设计图逐个精修 ----------

        FaceExpression.EXCITED -> FaceParams(
            scale = 1.03f,
            leftEye = EyeParams(shape = EyeShape.HAPPY_ARC, radius = 0.95f, tiltDegrees = -18f),
            rightEye = EyeParams(shape = EyeShape.HAPPY_ARC, radius = 0.95f, tiltDegrees = 18f),
            mouth = MouthParams(shape = MouthShape.OPEN_SMILE, scale = 1.15f, showTongue = true),
            blushAlpha = 0.8f,
            earGlow = 1.4f,
            marks = listOf(
                MarkParams(FaceMark.SPARKLE, offsetX = 0.34f, offsetY = -0.3f, scale = 0.8f),
                MarkParams(FaceMark.SPARKLE, offsetX = -0.36f, offsetY = -0.24f, scale = 0.55f),
            ),
        )

        FaceExpression.LAUGHING -> FaceParams(
            scale = 1.04f,
            tiltDegrees = -3f,
            leftEye = EyeParams(shape = EyeShape.HAPPY_ARC, radius = 1f, tiltDegrees = -22f),
            rightEye = EyeParams(shape = EyeShape.HAPPY_ARC, radius = 1f, tiltDegrees = 22f),
            mouth = MouthParams(shape = MouthShape.OPEN_SMILE, scale = 1.3f, showTongue = true),
            blushAlpha = 0.85f,
            earGlow = 1.35f,
            marks = listOf(
                MarkParams(FaceMark.SPARKLE, offsetX = 0.3f, offsetY = -0.32f, scale = 0.7f),
            ),
        )

        FaceExpression.PROUD -> FaceParams(
            tiltDegrees = 4f,
            leftEye = EyeParams(shape = EyeShape.OPEN, radius = 1.05f),
            rightEye = EyeParams(shape = EyeShape.HAPPY_ARC, radius = 0.95f),
            mouth = MouthParams(shape = MouthShape.CAT_W, scale = 1f),
            blushAlpha = 0.2f,
            earGlow = 1.2f,
            marks = listOf(
                MarkParams(FaceMark.SPARKLE, offsetX = 0.34f, offsetY = -0.28f, scale = 0.85f),
            ),
        )

        FaceExpression.THINKING -> FaceParams(
            tiltDegrees = -4f,
            leftEye = EyeParams(shape = EyeShape.HALF_LID, radius = 1f),
            rightEye = EyeParams(shape = EyeShape.HALF_LID, radius = 1f),
            mouth = MouthParams(shape = MouthShape.FLAT, scale = 0.7f),
            blushAlpha = 0.08f,
            earGlow = 0.9f,
            marks = listOf(
                MarkParams(FaceMark.DOTS, offsetX = 0.3f, offsetY = -0.24f, scale = 0.9f),
            ),
        )

        FaceExpression.CONFUSED -> FaceParams(
            tiltDegrees = 5f,
            leftEye = EyeParams(
                shape = EyeShape.OPEN,
                radius = 1f,
                pupilAlpha = 0.9f,
                pupilOffsetX = -0.35f,
                pupilOffsetY = -0.3f,
            ),
            rightEye = EyeParams(
                shape = EyeShape.OPEN,
                radius = 0.85f,
                pupilAlpha = 0.9f,
                pupilOffsetX = 0.2f,
                pupilOffsetY = -0.2f,
            ),
            mouth = MouthParams(shape = MouthShape.FROWN, scale = 0.75f),
            blushAlpha = 0.15f,
            marks = listOf(
                MarkParams(FaceMark.QUESTION, offsetX = 0.32f, offsetY = -0.3f, scale = 0.8f),
            ),
        )

        FaceExpression.SURPRISED -> FaceParams(
            scale = 1.03f,
            leftEye = EyeParams(shape = EyeShape.WIDE, radius = 1.25f, pupilAlpha = 0.6f),
            rightEye = EyeParams(shape = EyeShape.WIDE, radius = 1.25f, pupilAlpha = 0.6f),
            mouth = MouthParams(shape = MouthShape.SMALL_O, scale = 0.9f),
            earGlow = 1.3f,
            marks = listOf(
                MarkParams(FaceMark.EXCLAMATION, offsetX = 0.34f, offsetY = -0.3f, scale = 1f),
            ),
        )

        FaceExpression.SHY -> FaceParams(
            tiltDegrees = 6f,
            leftEye = EyeParams(shape = EyeShape.HAPPY_ARC, radius = 0.9f),
            rightEye = EyeParams(shape = EyeShape.HAPPY_ARC, radius = 0.9f),
            mouth = MouthParams(shape = MouthShape.CAT_W, scale = 0.8f),
            blushAlpha = 1f,
            earGlow = 1.05f,
            marks = listOf(
                MarkParams(FaceMark.SWEAT, offsetX = 0.32f, offsetY = -0.26f, scale = 0.7f, tint = MarkTint.BLUE),
            ),
        )

        FaceExpression.LOVE -> FaceParams(
            scale = 1.02f,
            leftEye = EyeParams(shape = EyeShape.HEART, radius = 1.05f),
            rightEye = EyeParams(shape = EyeShape.HEART, radius = 1.05f),
            mouth = MouthParams(shape = MouthShape.CAT_W, scale = 0.95f),
            blushAlpha = 0.9f,
            earGlow = 1.3f,
            marks = listOf(
                MarkParams(FaceMark.HEART, offsetX = 0.34f, offsetY = -0.3f, scale = 0.7f, tint = MarkTint.PINK),
            ),
        )

        FaceExpression.SLEEPY -> FaceParams(
            tiltDegrees = 3f,
            scale = 0.99f,
            leftEye = EyeParams(shape = EyeShape.HALF_LID, radius = 1f),
            rightEye = EyeParams(shape = EyeShape.HALF_LID, radius = 1f),
            mouth = MouthParams(shape = MouthShape.CAT_W, scale = 0.85f),
            blushAlpha = 0.18f,
            panelDim = 0.2f,
            earGlow = 0.7f,
            marks = listOf(
                MarkParams(FaceMark.SWEAT, offsetX = 0.33f, offsetY = -0.25f, scale = 0.65f),
            ),
        )

        FaceExpression.SAD -> FaceParams(
            offsetY = 0.015f,
            leftEye = EyeParams(shape = EyeShape.CALM_ARC, radius = 0.95f, tiltDegrees = -14f),
            rightEye = EyeParams(shape = EyeShape.CALM_ARC, radius = 0.95f, tiltDegrees = 14f),
            mouth = MouthParams(shape = MouthShape.FROWN, scale = 0.85f),
            blushAlpha = 0.1f,
            earGlow = 0.6f,
        )

        FaceExpression.WORRIED -> FaceParams(
            tiltDegrees = -4f,
            leftEye = EyeParams(
                shape = EyeShape.OPEN,
                radius = 0.95f,
                pupilAlpha = 0.85f,
                pupilOffsetY = -0.35f,
            ),
            rightEye = EyeParams(
                shape = EyeShape.OPEN,
                radius = 0.95f,
                pupilAlpha = 0.85f,
                pupilOffsetY = -0.35f,
            ),
            mouth = MouthParams(shape = MouthShape.FROWN, scale = 0.7f),
            blushAlpha = 0.15f,
            earGlow = 0.8f,
            marks = listOf(
                MarkParams(FaceMark.SWEAT, offsetX = -0.34f, offsetY = -0.28f, scale = 0.75f, tint = MarkTint.BLUE),
            ),
        )

        FaceExpression.ANGRY -> FaceParams(
            leftEye = EyeParams(shape = EyeShape.HAPPY_ARC, radius = 0.95f, tiltDegrees = 26f),
            rightEye = EyeParams(shape = EyeShape.HAPPY_ARC, radius = 0.95f, tiltDegrees = -26f),
            mouth = MouthParams(shape = MouthShape.FROWN, scale = 0.95f),
            blushAlpha = 0.35f,
            earGlow = 1.1f,
            marks = listOf(
                MarkParams(FaceMark.ANGER, offsetX = 0.3f, offsetY = -0.3f, scale = 0.9f, tint = MarkTint.RED),
            ),
        )

        FaceExpression.ANNOYED -> FaceParams(
            tiltDegrees = -3f,
            leftEye = EyeParams(shape = EyeShape.HALF_LID, radius = 0.95f, tiltDegrees = 16f),
            rightEye = EyeParams(shape = EyeShape.HALF_LID, radius = 0.95f, tiltDegrees = -16f),
            mouth = MouthParams(shape = MouthShape.FLAT, scale = 0.8f),
            blushAlpha = 0.12f,
            earGlow = 0.85f,
            marks = listOf(
                MarkParams(FaceMark.ANGER, offsetX = 0.3f, offsetY = -0.26f, scale = 0.65f, tint = MarkTint.RED),
            ),
        )

        FaceExpression.CRYING -> FaceParams(
            leftEye = EyeParams(shape = EyeShape.CALM_ARC, radius = 0.95f, tiltDegrees = -16f),
            rightEye = EyeParams(shape = EyeShape.CALM_ARC, radius = 0.95f, tiltDegrees = 16f),
            mouth = MouthParams(shape = MouthShape.FROWN, scale = 0.9f),
            blushAlpha = 0.3f,
            earGlow = 0.7f,
            marks = listOf(
                MarkParams(FaceMark.TEAR, offsetX = -0.24f, offsetY = 0.1f, scale = 1f, tint = MarkTint.BLUE),
                MarkParams(FaceMark.TEAR, offsetX = 0.24f, offsetY = 0.18f, scale = 0.85f, tint = MarkTint.BLUE),
            ),
        )

        FaceExpression.EMBARRASSED -> FaceParams(
            tiltDegrees = 3f,
            leftEye = EyeParams(
                shape = EyeShape.OPEN,
                radius = 0.9f,
                pupilAlpha = 0.9f,
                pupilOffsetX = -0.25f,
            ),
            rightEye = EyeParams(
                shape = EyeShape.OPEN,
                radius = 0.9f,
                pupilAlpha = 0.9f,
                pupilOffsetX = 0.25f,
            ),
            mouth = MouthParams(shape = MouthShape.CAT_W, scale = 0.75f),
            blushAlpha = 1f,
            earGlow = 1f,
            marks = listOf(
                MarkParams(FaceMark.DOTS, offsetX = -0.34f, offsetY = 0.2f, scale = 0.7f),
            ),
        )

        FaceExpression.LISTENING -> FaceParams(
            leftEye = EyeParams(shape = EyeShape.WIDE, radius = 1.15f, pupilAlpha = 0.8f),
            rightEye = EyeParams(shape = EyeShape.WIDE, radius = 1.15f, pupilAlpha = 0.8f),
            mouth = MouthParams(shape = MouthShape.SMALL_O, scale = 0.7f),
            earGlow = 1.2f,
            marks = listOf(
                MarkParams(FaceMark.WAVE, offsetX = 0.36f, offsetY = -0.05f, scale = 0.85f),
            ),
        )

        FaceExpression.SPEAKING -> FaceParams(
            leftEye = EyeParams(shape = EyeShape.HAPPY_ARC, radius = 0.95f),
            rightEye = EyeParams(shape = EyeShape.HAPPY_ARC, radius = 0.95f),
            mouth = MouthParams(shape = MouthShape.OPEN_SMILE, scale = 1.05f, showTongue = true),
            blushAlpha = 0.4f,
            earGlow = 1.1f,
            marks = listOf(
                MarkParams(FaceMark.WAVE, offsetX = 0.36f, offsetY = -0.02f, scale = 0.8f),
            ),
        )

        FaceExpression.WAITING -> FaceParams(
            leftEye = EyeParams(shape = EyeShape.OPEN, radius = 0.95f, pupilAlpha = 0.75f),
            rightEye = EyeParams(shape = EyeShape.OPEN, radius = 0.95f, pupilAlpha = 0.75f),
            mouth = MouthParams(shape = MouthShape.SMALL_O, scale = 0.55f),
            blushAlpha = 0.08f,
            earGlow = 0.9f,
            marks = listOf(
                MarkParams(FaceMark.DOTS, offsetX = 0.3f, offsetY = 0.24f, scale = 0.8f),
            ),
        )

        FaceExpression.OFFLINE -> FaceParams(
            scale = 0.99f,
            leftEye = EyeParams(shape = EyeShape.CLOSED, radius = 0.85f, glow = 0.7f),
            rightEye = EyeParams(shape = EyeShape.CLOSED, radius = 0.85f, glow = 0.7f),
            mouth = MouthParams(shape = MouthShape.FLAT, scale = 0.6f),
            panelDim = 0.3f,
            earGlow = 0.12f,
            marks = listOf(
                MarkParams(
                    FaceMark.SLASH_CIRCLE,
                    offsetX = 0.32f,
                    offsetY = -0.28f,
                    scale = 0.8f,
                    tint = MarkTint.RED,
                ),
            ),
        )

        FaceExpression.CHARGING -> FaceParams(
            leftEye = EyeParams(shape = EyeShape.HAPPY_ARC, radius = 0.95f),
            rightEye = EyeParams(shape = EyeShape.HAPPY_ARC, radius = 0.95f),
            mouth = MouthParams(shape = MouthShape.CAT_W, scale = 0.9f),
            blushAlpha = 0.25f,
            panelDim = 0.15f,
            earGlow = 0.95f,
            marks = listOf(
                MarkParams(
                    FaceMark.BOLT,
                    offsetX = 0.32f,
                    offsetY = -0.28f,
                    scale = 0.85f,
                    tint = MarkTint.GREEN,
                ),
            ),
        )

        // ---------- 扩展表情 I：26-50 ----------

        // 26 轻松：垂眼放松 + 猫嘴 + 轻轻打个小呼
        FaceExpression.RELAXED -> FaceParams(
            leftEye = EyeParams(shape = EyeShape.CALM_ARC, radius = 0.95f),
            rightEye = EyeParams(shape = EyeShape.CALM_ARC, radius = 0.95f),
            mouth = MouthParams(shape = MouthShape.CAT_W, scale = 0.9f),
            blushAlpha = 0.15f,
            earGlow = 0.9f,
            marks = listOf(
                MarkParams(FaceMark.ZZZ, offsetX = 0.3f, offsetY = -0.28f, scale = 0.55f),
            ),
        )

        // 27 满足：眯眼笑 + 腮红
        FaceExpression.CONTENT -> FaceParams(
            leftEye = EyeParams(shape = EyeShape.HAPPY_ARC, radius = 0.95f),
            rightEye = EyeParams(shape = EyeShape.HAPPY_ARC, radius = 0.95f),
            mouth = MouthParams(shape = MouthShape.CAT_W, scale = 0.95f),
            blushAlpha = 0.6f,
            earGlow = 1.1f,
        )

        // 28 调皮：一只眼眯着一只圆睁 + 吐舌
        FaceExpression.PLAYFUL -> FaceParams(
            tiltDegrees = -3f,
            leftEye = EyeParams(shape = EyeShape.HAPPY_ARC, radius = 0.95f, tiltDegrees = -20f),
            rightEye = EyeParams(shape = EyeShape.OPEN, radius = 1f, pupilAlpha = 0.9f),
            mouth = MouthParams(shape = MouthShape.TONGUE, scale = 1f),
            blushAlpha = 0.35f,
            earGlow = 1.2f,
        )

        // 29 眨眼：一只圆眼一只闭眼 + 短促强调线
        FaceExpression.WINK -> FaceParams(
            tiltDegrees = 3f,
            leftEye = EyeParams(shape = EyeShape.OPEN, radius = 1f, pupilAlpha = 0.9f),
            rightEye = EyeParams(shape = EyeShape.CLOSED, radius = 0.9f, tiltDegrees = -8f),
            mouth = MouthParams(shape = MouthShape.SMILE_ARC, scale = 0.95f),
            blushAlpha = 0.35f,
            earGlow = 1.15f,
            marks = listOf(
                MarkParams(FaceMark.SPARK_LINES, offsetX = 0.32f, offsetY = -0.3f, scale = 0.8f),
            ),
        )

        // 30 期待：星星眼 + 两侧强调线
        FaceExpression.ANTICIPATING -> FaceParams(
            scale = 1.02f,
            leftEye = EyeParams(shape = EyeShape.STAR, radius = 1.05f),
            rightEye = EyeParams(shape = EyeShape.STAR, radius = 1.05f),
            mouth = MouthParams(shape = MouthShape.CAT_W, scale = 0.9f),
            blushAlpha = 0.4f,
            earGlow = 1.35f,
            marks = listOf(
                MarkParams(FaceMark.SPARK_LINES, offsetX = -0.32f, offsetY = -0.28f, scale = 0.7f, tiltDegrees = -30f),
                MarkParams(FaceMark.SPARK_LINES, offsetX = 0.32f, offsetY = -0.28f, scale = 0.7f, tiltDegrees = 30f),
            ),
        )

        // 31 平静：完全放松的闭眼
        FaceExpression.CALM -> FaceParams(
            leftEye = EyeParams(shape = EyeShape.CALM_ARC, radius = 0.98f),
            rightEye = EyeParams(shape = EyeShape.CALM_ARC, radius = 0.98f),
            mouth = MouthParams(shape = MouthShape.CAT_W, scale = 0.95f),
            earGlow = 0.95f,
        )

        // 32 专注：压低的眼角 + 抿嘴
        FaceExpression.FOCUSED -> FaceParams(
            leftEye = EyeParams(shape = EyeShape.HAPPY_ARC, radius = 0.9f, tiltDegrees = 20f),
            rightEye = EyeParams(shape = EyeShape.HAPPY_ARC, radius = 0.9f, tiltDegrees = -20f),
            mouth = MouthParams(shape = MouthShape.FLAT, scale = 0.55f),
            earGlow = 0.9f,
        )

        // 33 决心：眼角更用力
        FaceExpression.DETERMINED -> FaceParams(
            scale = 1.01f,
            leftEye = EyeParams(shape = EyeShape.HAPPY_ARC, radius = 0.95f, tiltDegrees = 24f),
            rightEye = EyeParams(shape = EyeShape.HAPPY_ARC, radius = 0.95f, tiltDegrees = -24f),
            mouth = MouthParams(shape = MouthShape.FLAT, scale = 0.6f),
            blushAlpha = 0.15f,
            earGlow = 1.15f,
        )

        // 34 灵感：圆眼 + 张嘴吐舌 + 灯泡
        FaceExpression.INSPIRED -> FaceParams(
            scale = 1.02f,
            leftEye = EyeParams(shape = EyeShape.OPEN, radius = 1f, pupilAlpha = 0.85f),
            rightEye = EyeParams(shape = EyeShape.OPEN, radius = 1f, pupilAlpha = 0.85f),
            mouth = MouthParams(shape = MouthShape.OPEN_SMILE, scale = 1f, showTongue = true),
            blushAlpha = 0.35f,
            earGlow = 1.3f,
            marks = listOf(
                MarkParams(
                    FaceMark.LIGHT_BULB,
                    offsetX = 0.3f,
                    offsetY = -0.3f,
                    scale = 0.95f,
                    tint = MarkTint.AMBER,
                ),
            ),
        )

        // 35 恍然：睁大眼 + 感叹号
        FaceExpression.REALIZED -> FaceParams(
            scale = 1.03f,
            leftEye = EyeParams(shape = EyeShape.WIDE, radius = 1.15f, pupilAlpha = 0.8f),
            rightEye = EyeParams(shape = EyeShape.WIDE, radius = 1.15f, pupilAlpha = 0.8f),
            mouth = MouthParams(shape = MouthShape.SMALL_O, scale = 0.8f),
            earGlow = 1.25f,
            marks = listOf(
                MarkParams(FaceMark.EXCLAMATION, offsetX = 0.32f, offsetY = -0.3f, scale = 1.1f),
            ),
        )

        // 36 偷笑：半眯眼 + 歪嘴笑 + 腮红
        FaceExpression.SMIRKING -> FaceParams(
            tiltDegrees = -3f,
            leftEye = EyeParams(shape = EyeShape.HALF_LID, radius = 1f),
            rightEye = EyeParams(shape = EyeShape.HALF_LID, radius = 1f),
            mouth = MouthParams(shape = MouthShape.SMILE_ARC, scale = 0.9f, offsetX = 0.07f),
            blushAlpha = 0.6f,
            earGlow = 1.05f,
        )

        // 37 撒娇：星星眼 + 爱心 + 腮红
        FaceExpression.AFFECTIONATE -> FaceParams(
            scale = 1.02f,
            tiltDegrees = -4f,
            leftEye = EyeParams(shape = EyeShape.STAR, radius = 1.05f),
            rightEye = EyeParams(shape = EyeShape.STAR, radius = 1.05f),
            mouth = MouthParams(shape = MouthShape.CAT_W, scale = 0.9f),
            blushAlpha = 0.65f,
            earGlow = 1.35f,
            marks = listOf(
                MarkParams(FaceMark.HEART, offsetX = -0.36f, offsetY = -0.2f, scale = 0.5f, tint = MarkTint.PINK),
                MarkParams(FaceMark.HEART, offsetX = 0.36f, offsetY = -0.05f, scale = 0.75f, tint = MarkTint.PINK),
            ),
        )

        // 38 求抱抱：眯眼 + 两只爪子举起来
        FaceExpression.HUG_ME -> FaceParams(
            scale = 1.02f,
            leftEye = EyeParams(shape = EyeShape.CALM_ARC, radius = 0.95f),
            rightEye = EyeParams(shape = EyeShape.CALM_ARC, radius = 0.95f),
            mouth = MouthParams(shape = MouthShape.CAT_W, scale = 0.95f),
            blushAlpha = 0.5f,
            earGlow = 1.2f,
            marks = listOf(
                MarkParams(FaceMark.PAW, offsetX = -0.42f, offsetY = 0.12f, scale = 0.8f),
                MarkParams(FaceMark.PAW, offsetX = 0.42f, offsetY = 0.12f, scale = 0.8f),
            ),
        )

        // 39 紧张：垂眼 + 冷汗 + 攥着的小爪子
        FaceExpression.NERVOUS -> FaceParams(
            tiltDegrees = -3f,
            leftEye = EyeParams(shape = EyeShape.CALM_ARC, radius = 0.9f, tiltDegrees = -10f),
            rightEye = EyeParams(shape = EyeShape.CALM_ARC, radius = 0.9f, tiltDegrees = 10f),
            mouth = MouthParams(shape = MouthShape.WAVY, scale = 0.75f),
            blushAlpha = 0.2f,
            earGlow = 0.75f,
            marks = listOf(
                MarkParams(FaceMark.SWEAT, offsetX = 0.32f, offsetY = -0.24f, scale = 0.7f, tint = MarkTint.BLUE),
                MarkParams(FaceMark.PAW, offsetX = -0.34f, offsetY = 0.26f, scale = 0.6f),
            ),
        )

        // 40 警觉：眼角压低 + 强调线
        FaceExpression.ALERT -> FaceParams(
            leftEye = EyeParams(shape = EyeShape.HAPPY_ARC, radius = 0.9f, tiltDegrees = 18f),
            rightEye = EyeParams(shape = EyeShape.HAPPY_ARC, radius = 0.9f, tiltDegrees = -18f),
            mouth = MouthParams(shape = MouthShape.FLAT, scale = 0.55f),
            earGlow = 1.2f,
            marks = listOf(
                MarkParams(FaceMark.SPARK_LINES, offsetX = 0.3f, offsetY = -0.3f, scale = 0.85f),
            ),
        )

        // 41 受惊：瞪大眼 + 张嘴 + 爆发线
        FaceExpression.ALARMED -> FaceParams(
            scale = 1.04f,
            leftEye = EyeParams(shape = EyeShape.WIDE, radius = 1.2f),
            rightEye = EyeParams(shape = EyeShape.WIDE, radius = 1.2f),
            mouth = MouthParams(shape = MouthShape.SMALL_O, scale = 1.35f),
            blushAlpha = 0.5f,
            earGlow = 1.4f,
            marks = listOf(
                MarkParams(FaceMark.ALARM, offsetX = 0.32f, offsetY = -0.3f, scale = 1f, tint = MarkTint.RED),
                MarkParams(FaceMark.ALARM, offsetX = -0.34f, offsetY = -0.26f, scale = 0.7f, tint = MarkTint.RED),
            ),
        )

        // 42 无聊：半眯眼 + 撑着下巴的爪子
        FaceExpression.BORED -> FaceParams(
            tiltDegrees = -4f,
            leftEye = EyeParams(shape = EyeShape.HALF_LID, radius = 1f),
            rightEye = EyeParams(shape = EyeShape.HALF_LID, radius = 1f),
            mouth = MouthParams(shape = MouthShape.FLAT, scale = 0.6f, offsetX = -0.05f),
            earGlow = 0.8f,
            marks = listOf(
                MarkParams(FaceMark.PAW, offsetX = 0.3f, offsetY = 0.28f, scale = 0.7f),
            ),
        )

        // 43 委屈：下垂眼 + 波浪嘴 + 眼泪
        FaceExpression.AGGRIEVED -> FaceParams(
            leftEye = EyeParams(shape = EyeShape.CALM_ARC, radius = 0.92f, tiltDegrees = -16f),
            rightEye = EyeParams(shape = EyeShape.CALM_ARC, radius = 0.92f, tiltDegrees = 16f),
            mouth = MouthParams(shape = MouthShape.WAVY, scale = 0.8f),
            blushAlpha = 0.3f,
            earGlow = 0.7f,
            marks = listOf(
                MarkParams(FaceMark.TEAR, offsetX = -0.24f, offsetY = 0.12f, scale = 0.8f, tint = MarkTint.BLUE),
                MarkParams(FaceMark.TEAR, offsetX = 0.24f, offsetY = 0.2f, scale = 0.65f, tint = MarkTint.BLUE),
            ),
        )

        // 44 无奈：闭眼 + 猫嘴 + 摊手
        FaceExpression.HELPLESS -> FaceParams(
            tiltDegrees = -3f,
            leftEye = EyeParams(shape = EyeShape.CALM_ARC, radius = 0.95f),
            rightEye = EyeParams(shape = EyeShape.CALM_ARC, radius = 0.95f),
            mouth = MouthParams(shape = MouthShape.CAT_W, scale = 0.85f),
            blushAlpha = 0.15f,
            earGlow = 0.8f,
            marks = listOf(
                MarkParams(FaceMark.PAW, offsetX = 0.24f, offsetY = 0.3f, scale = 0.7f),
            ),
        )

        // 45 感谢：眯眼笑 + 腮红 + 小爱心
        FaceExpression.GRATEFUL -> FaceParams(
            leftEye = EyeParams(shape = EyeShape.HAPPY_ARC, radius = 0.95f),
            rightEye = EyeParams(shape = EyeShape.HAPPY_ARC, radius = 0.95f),
            mouth = MouthParams(shape = MouthShape.SMILE_ARC, scale = 1f),
            blushAlpha = 0.75f,
            earGlow = 1.25f,
            marks = listOf(
                MarkParams(FaceMark.HEART, offsetX = 0.34f, offsetY = -0.28f, scale = 0.7f, tint = MarkTint.PINK),
            ),
        )

        // 46 鼓励：一只圆眼一只眯眼 + 吐舌 + 点赞
        FaceExpression.ENCOURAGING -> FaceParams(
            tiltDegrees = -2f,
            leftEye = EyeParams(shape = EyeShape.OPEN, radius = 1.05f, pupilAlpha = 0.85f),
            rightEye = EyeParams(shape = EyeShape.HAPPY_ARC, radius = 0.9f, tiltDegrees = 20f),
            mouth = MouthParams(shape = MouthShape.TONGUE, scale = 1f),
            blushAlpha = 0.4f,
            earGlow = 1.25f,
            marks = listOf(
                MarkParams(FaceMark.THUMB_UP, offsetX = 0.34f, offsetY = 0.06f, scale = 0.85f),
            ),
        )

        // 47 赞同：眯眼笑 + 强调线 + 点赞
        FaceExpression.APPROVING -> FaceParams(
            leftEye = EyeParams(shape = EyeShape.HAPPY_ARC, radius = 1f),
            rightEye = EyeParams(shape = EyeShape.HAPPY_ARC, radius = 1f),
            mouth = MouthParams(shape = MouthShape.SMILE_ARC, scale = 1f),
            blushAlpha = 0.45f,
            earGlow = 1.25f,
            marks = listOf(
                MarkParams(FaceMark.SPARK_LINES, offsetX = 0.3f, offsetY = -0.3f, scale = 0.75f),
                MarkParams(FaceMark.THUMB_UP, offsetX = 0.36f, offsetY = 0.12f, scale = 0.8f),
            ),
        )

        // 48 拒绝：叉叉眼 + 红叉
        FaceExpression.REFUSING -> FaceParams(
            leftEye = EyeParams(shape = EyeShape.CROSS, radius = 0.95f),
            rightEye = EyeParams(shape = EyeShape.CROSS, radius = 0.95f),
            mouth = MouthParams(shape = MouthShape.FLAT, scale = 0.6f),
            blushAlpha = 0.35f,
            earGlow = 1f,
            marks = listOf(
                MarkParams(FaceMark.CROSS_OUT, offsetX = 0.3f, offsetY = -0.3f, scale = 0.9f, tint = MarkTint.RED),
            ),
        )

        // 49 保密：半眯眼 + 锁 + 嘘的手势
        FaceExpression.SECRETIVE -> FaceParams(
            tiltDegrees = -2f,
            leftEye = EyeParams(shape = EyeShape.HALF_LID, radius = 1f),
            rightEye = EyeParams(shape = EyeShape.HALF_LID, radius = 1f),
            mouth = MouthParams(shape = MouthShape.FLAT, scale = 0.55f),
            earGlow = 0.95f,
            marks = listOf(
                MarkParams(FaceMark.LOCK, offsetX = 0.3f, offsetY = -0.28f, scale = 0.85f, tint = MarkTint.AMBER),
                MarkParams(FaceMark.PAW, offsetX = 0.06f, offsetY = 0.3f, scale = 0.65f),
            ),
        )

        // 50 发呆：圆眼 + 思绪云朵
        FaceExpression.DAYDREAMING -> FaceParams(
            tiltDegrees = 3f,
            leftEye = EyeParams(shape = EyeShape.OPEN, radius = 1f, pupilAlpha = 0.7f),
            rightEye = EyeParams(shape = EyeShape.OPEN, radius = 1f, pupilAlpha = 0.7f),
            mouth = MouthParams(shape = MouthShape.CAT_W, scale = 0.85f),
            blushAlpha = 0.2f,
            earGlow = 0.9f,
            marks = listOf(
                MarkParams(FaceMark.CLOUD, offsetX = 0.3f, offsetY = -0.3f, scale = 0.95f),
            ),
        )

        // ---------- 扩展表情 II：51-75 ----------

        // 51 庆祝：眯眼 + 张嘴吐舌 + 彩带
        FaceExpression.CELEBRATING -> FaceParams(
            scale = 1.04f,
            tiltDegrees = -3f,
            leftEye = EyeParams(shape = EyeShape.HAPPY_ARC, radius = 1f, tiltDegrees = -12f),
            rightEye = EyeParams(shape = EyeShape.HAPPY_ARC, radius = 1f, tiltDegrees = 12f),
            mouth = MouthParams(shape = MouthShape.OPEN_SMILE, scale = 1.2f, showTongue = true),
            blushAlpha = 0.6f,
            earGlow = 1.4f,
            marks = listOf(
                MarkParams(FaceMark.CONFETTI, offsetX = -0.34f, offsetY = 0.02f, scale = 1.5f),
            ),
        )

        // 52 凯旋：半眯眼 + 闪光
        FaceExpression.TRIUMPHANT -> FaceParams(
            scale = 1.02f,
            leftEye = EyeParams(shape = EyeShape.HALF_LID, radius = 1f, tiltDegrees = 12f),
            rightEye = EyeParams(shape = EyeShape.HALF_LID, radius = 1f, tiltDegrees = -12f),
            mouth = MouthParams(shape = MouthShape.SMILE_ARC, scale = 0.95f),
            blushAlpha = 0.35f,
            earGlow = 1.3f,
            marks = listOf(
                MarkParams(FaceMark.SPARKLE, offsetX = 0.32f, offsetY = -0.3f, scale = 0.9f),
            ),
        )

        // 53 冥想：完全放松的闭眼
        FaceExpression.MEDITATING -> FaceParams(
            leftEye = EyeParams(shape = EyeShape.CALM_ARC, radius = 0.95f),
            rightEye = EyeParams(shape = EyeShape.CALM_ARC, radius = 0.95f),
            mouth = MouthParams(shape = MouthShape.CAT_W, scale = 0.9f),
            blushAlpha = 0.15f,
            earGlow = 1.05f,
        )

        // 54 做梦：闭眼 + 梦里的云朵和星星
        FaceExpression.DREAMING -> FaceParams(
            leftEye = EyeParams(shape = EyeShape.CALM_ARC, radius = 0.95f),
            rightEye = EyeParams(shape = EyeShape.CALM_ARC, radius = 0.95f),
            mouth = MouthParams(shape = MouthShape.CAT_W, scale = 0.85f),
            blushAlpha = 0.25f,
            panelDim = 0.12f,
            earGlow = 0.75f,
            marks = listOf(
                MarkParams(FaceMark.CLOUD, offsetX = 0.3f, offsetY = -0.3f, scale = 1f),
                MarkParams(FaceMark.SPARKLE, offsetX = 0.16f, offsetY = -0.38f, scale = 0.5f, tint = MarkTint.AMBER),
            ),
        )

        // 55 哈欠：闭眼 + 张大嘴 + zzz
        FaceExpression.YAWNING -> FaceParams(
            scale = 1.01f,
            leftEye = EyeParams(shape = EyeShape.CALM_ARC, radius = 0.9f),
            rightEye = EyeParams(shape = EyeShape.CALM_ARC, radius = 0.9f),
            mouth = MouthParams(shape = MouthShape.SMALL_O, scale = 1.5f),
            blushAlpha = 0.3f,
            earGlow = 0.7f,
            marks = listOf(
                MarkParams(FaceMark.ZZZ, offsetX = 0.32f, offsetY = -0.26f, scale = 0.85f),
            ),
        )

        // 56 顽皮：半眯眼 + 侧吐舌 + 腮红
        FaceExpression.MISCHIEVOUS -> FaceParams(
            tiltDegrees = -4f,
            leftEye = EyeParams(shape = EyeShape.HALF_LID, radius = 1f, tiltDegrees = 10f),
            rightEye = EyeParams(shape = EyeShape.HALF_LID, radius = 1f, tiltDegrees = -10f),
            mouth = MouthParams(shape = MouthShape.TONGUE, scale = 0.95f, offsetX = -0.06f),
            blushAlpha = 0.5f,
            earGlow = 1.15f,
        )

        // 57 认真：压角眼 + 一条直线嘴
        FaceExpression.SERIOUS -> FaceParams(
            leftEye = EyeParams(shape = EyeShape.HALF_LID, radius = 0.95f, tiltDegrees = 16f),
            rightEye = EyeParams(shape = EyeShape.HALF_LID, radius = 0.95f, tiltDegrees = -16f),
            mouth = MouthParams(shape = MouthShape.FLAT, scale = 0.7f),
            earGlow = 0.95f,
        )

        // 58 守护：压角眼 + 盾牌
        FaceExpression.PROTECTIVE -> FaceParams(
            leftEye = EyeParams(shape = EyeShape.HAPPY_ARC, radius = 0.95f, tiltDegrees = 22f),
            rightEye = EyeParams(shape = EyeShape.HAPPY_ARC, radius = 0.95f, tiltDegrees = -22f),
            mouth = MouthParams(shape = MouthShape.FLAT, scale = 0.6f),
            blushAlpha = 0.1f,
            earGlow = 1.2f,
            marks = listOf(
                MarkParams(FaceMark.SHIELD, offsetX = 0.3f, offsetY = -0.3f, scale = 0.95f, tint = MarkTint.AMBER),
            ),
        )

        // 59 观察：睁大眼 + 强调线
        FaceExpression.OBSERVING -> FaceParams(
            leftEye = EyeParams(shape = EyeShape.WIDE, radius = 1.1f, pupilAlpha = 0.85f),
            rightEye = EyeParams(shape = EyeShape.WIDE, radius = 1.1f, pupilAlpha = 0.85f),
            mouth = MouthParams(shape = MouthShape.SMALL_O, scale = 0.5f),
            earGlow = 1.15f,
            marks = listOf(
                MarkParams(FaceMark.SPARK_LINES, offsetX = 0.34f, offsetY = -0.3f, scale = 0.8f),
            ),
        )

        // 60 倾心：眼里都是爱心
        FaceExpression.DEVOTED -> FaceParams(
            scale = 1.02f,
            leftEye = EyeParams(
                shape = EyeShape.OPEN,
                radius = 1.05f,
                pupilAlpha = 0.5f,
                pupilOffsetY = 0.1f,
            ),
            rightEye = EyeParams(
                shape = EyeShape.OPEN,
                radius = 1.05f,
                pupilAlpha = 0.5f,
                pupilOffsetY = 0.1f,
            ),
            mouth = MouthParams(shape = MouthShape.CAT_W, scale = 0.9f),
            blushAlpha = 0.8f,
            earGlow = 1.35f,
            marks = listOf(
                MarkParams(FaceMark.HEART, offsetX = -0.215f, offsetY = -0.06f, scale = 0.45f, tint = MarkTint.PINK),
                MarkParams(FaceMark.HEART, offsetX = 0.215f, offsetY = -0.06f, scale = 0.45f, tint = MarkTint.PINK),
                MarkParams(FaceMark.HEART, offsetX = 0.36f, offsetY = -0.3f, scale = 0.65f, tint = MarkTint.PINK),
            ),
        )

        // 61 道歉：下垂眼 + 腮红 + 小心心
        FaceExpression.APOLOGETIC -> FaceParams(
            leftEye = EyeParams(shape = EyeShape.CALM_ARC, radius = 0.9f, tiltDegrees = -14f),
            rightEye = EyeParams(shape = EyeShape.CALM_ARC, radius = 0.9f, tiltDegrees = 14f),
            mouth = MouthParams(shape = MouthShape.WAVY, scale = 0.7f),
            blushAlpha = 0.7f,
            earGlow = 0.8f,
            marks = listOf(
                MarkParams(FaceMark.HEART, offsetX = 0.32f, offsetY = -0.28f, scale = 0.5f, tint = MarkTint.PINK),
            ),
        )

        // 62 求助：一大一小的眼睛 + 举起爪子
        FaceExpression.ASKING_HELP -> FaceParams(
            tiltDegrees = -5f,
            leftEye = EyeParams(shape = EyeShape.OPEN, radius = 1.1f, pupilAlpha = 0.8f),
            rightEye = EyeParams(shape = EyeShape.OPEN, radius = 0.78f, pupilAlpha = 0.6f),
            mouth = MouthParams(shape = MouthShape.CAT_W, scale = 0.8f),
            blushAlpha = 0.3f,
            earGlow = 0.9f,
            marks = listOf(
                MarkParams(FaceMark.PAW, offsetX = 0.34f, offsetY = 0.06f, scale = 0.8f),
            ),
        )

        // 63 安慰：眯眼笑 + 腮红
        FaceExpression.COMFORTING -> FaceParams(
            leftEye = EyeParams(shape = EyeShape.HAPPY_ARC, radius = 0.95f),
            rightEye = EyeParams(shape = EyeShape.HAPPY_ARC, radius = 0.95f),
            mouth = MouthParams(shape = MouthShape.CAT_W, scale = 0.95f),
            blushAlpha = 0.8f,
            earGlow = 1.15f,
        )

        // 64 喝彩：一只闭眼一只眯眼 + 大笑 + 强调线
        FaceExpression.CHEERING -> FaceParams(
            scale = 1.03f,
            tiltDegrees = -3f,
            leftEye = EyeParams(shape = EyeShape.CLOSED, radius = 0.9f),
            rightEye = EyeParams(shape = EyeShape.HAPPY_ARC, radius = 1f, tiltDegrees = 20f),
            mouth = MouthParams(shape = MouthShape.OPEN_SMILE, scale = 1.2f, showTongue = true),
            blushAlpha = 0.55f,
            earGlow = 1.4f,
            marks = listOf(
                MarkParams(FaceMark.SPARK_LINES, offsetX = 0.32f, offsetY = -0.28f, scale = 0.9f),
            ),
        )

        // 65 偷看：一只眼半眯一只睁大 + 问号
        FaceExpression.PEEKING -> FaceParams(
            tiltDegrees = -4f,
            leftEye = EyeParams(shape = EyeShape.HALF_LID, radius = 0.9f),
            rightEye = EyeParams(shape = EyeShape.OPEN, radius = 1.1f, pupilAlpha = 0.65f),
            mouth = MouthParams(shape = MouthShape.CAT_W, scale = 0.7f),
            blushAlpha = 0.3f,
            earGlow = 1f,
            marks = listOf(
                MarkParams(FaceMark.QUESTION, offsetX = 0.32f, offsetY = -0.3f, scale = 0.85f),
            ),
        )

        // 66 坏笑：半眯眼 + 斜嘴笑 + 闪光
        FaceExpression.SLY -> FaceParams(
            tiltDegrees = -5f,
            leftEye = EyeParams(shape = EyeShape.HALF_LID, radius = 1f, tiltDegrees = 8f),
            rightEye = EyeParams(shape = EyeShape.HALF_LID, radius = 1f, tiltDegrees = -8f),
            mouth = MouthParams(shape = MouthShape.SMILE_ARC, scale = 0.85f, offsetX = 0.08f),
            blushAlpha = 0.25f,
            earGlow = 1.05f,
            marks = listOf(
                MarkParams(FaceMark.SPARKLE, offsetX = 0.32f, offsetY = -0.26f, scale = 0.6f),
            ),
        )

        // 67 石化：圆眼 + 头顶竖线
        FaceExpression.FROZEN -> FaceParams(
            leftEye = EyeParams(shape = EyeShape.OPEN, radius = 1f),
            rightEye = EyeParams(shape = EyeShape.OPEN, radius = 1f),
            mouth = MouthParams(shape = MouthShape.FLAT, scale = 0.65f),
            earGlow = 0.85f,
            marks = listOf(
                MarkParams(FaceMark.SPARK_LINES, offsetX = 0f, offsetY = -0.4f, scale = 1.1f, tiltDegrees = 90f),
            ),
        )

        // 68 晕乎：螺旋眼 + 波浪嘴 + 星星
        FaceExpression.DIZZY -> FaceParams(
            tiltDegrees = 4f,
            leftEye = EyeParams(shape = EyeShape.SPIRAL, radius = 0.95f),
            rightEye = EyeParams(shape = EyeShape.SPIRAL, radius = 0.95f),
            mouth = MouthParams(shape = MouthShape.WAVY, scale = 0.85f),
            blushAlpha = 0.35f,
            earGlow = 1f,
            marks = listOf(
                MarkParams(FaceMark.SPARKLE, offsetX = -0.3f, offsetY = -0.3f, scale = 0.7f, tint = MarkTint.AMBER),
            ),
        )

        // 69 生病：下垂眼 + 温度计 + 冷汗
        FaceExpression.UNWELL -> FaceParams(
            scale = 0.99f,
            leftEye = EyeParams(shape = EyeShape.CALM_ARC, radius = 0.9f, tiltDegrees = -14f),
            rightEye = EyeParams(shape = EyeShape.CALM_ARC, radius = 0.9f, tiltDegrees = 14f),
            mouth = MouthParams(shape = MouthShape.CAT_W, scale = 0.7f, offsetX = -0.06f),
            blushAlpha = 0.45f,
            panelDim = 0.15f,
            earGlow = 0.6f,
            marks = listOf(
                MarkParams(FaceMark.THERMOMETER, offsetX = 0.26f, offsetY = 0.16f, scale = 0.95f, tint = MarkTint.RED),
                MarkParams(FaceMark.SWEAT, offsetX = -0.32f, offsetY = -0.26f, scale = 0.65f, tint = MarkTint.BLUE),
            ),
        )

        // 70 迷糊：垂眼 + 波浪嘴 + z
        FaceExpression.GROGGY -> FaceParams(
            tiltDegrees = 5f,
            leftEye = EyeParams(shape = EyeShape.CALM_ARC, radius = 0.9f, tiltDegrees = -10f),
            rightEye = EyeParams(shape = EyeShape.CALM_ARC, radius = 0.9f, tiltDegrees = 10f),
            mouth = MouthParams(shape = MouthShape.WAVY, scale = 0.8f),
            blushAlpha = 0.3f,
            panelDim = 0.1f,
            earGlow = 0.7f,
            marks = listOf(
                MarkParams(FaceMark.ZZZ, offsetX = 0.3f, offsetY = -0.28f, scale = 0.6f),
            ),
        )

        // 71 侦测：圆眼 + 屏幕四角扫描框
        FaceExpression.SCANNING -> FaceParams(
            leftEye = EyeParams(shape = EyeShape.OPEN, radius = 1f, pupilAlpha = 0.8f),
            rightEye = EyeParams(shape = EyeShape.OPEN, radius = 1f, pupilAlpha = 0.8f),
            mouth = MouthParams(shape = MouthShape.FLAT, scale = 0.5f),
            earGlow = 1.15f,
            marks = listOf(
                MarkParams(FaceMark.SCAN_FRAME, scale = 1f),
            ),
        )

        // 72 处理中：小圆眼 + 省略号 + 转圈
        FaceExpression.PROCESSING -> FaceParams(
            leftEye = EyeParams(shape = EyeShape.OPEN, radius = 0.85f),
            rightEye = EyeParams(shape = EyeShape.OPEN, radius = 0.85f),
            mouth = MouthParams(shape = MouthShape.FLAT, scale = 0.5f),
            earGlow = 0.95f,
            marks = listOf(
                MarkParams(FaceMark.DOTS, offsetX = -0.24f, offsetY = 0.28f, scale = 0.85f),
                MarkParams(FaceMark.SPINNER, offsetX = 0.28f, offsetY = 0.28f, scale = 0.9f),
            ),
        )

        // 73 记录：一只大眼一只小眼 + 录制红点
        FaceExpression.RECORDING -> FaceParams(
            leftEye = EyeParams(shape = EyeShape.OPEN, radius = 1.15f, pupilAlpha = 0.9f),
            rightEye = EyeParams(shape = EyeShape.OPEN, radius = 0.72f),
            mouth = MouthParams(shape = MouthShape.FLAT, scale = 0.5f),
            earGlow = 1.05f,
            marks = listOf(
                MarkParams(FaceMark.REC_DOT, offsetX = 0.32f, offsetY = -0.3f, scale = 0.9f, tint = MarkTint.RED),
            ),
        )

        // 74 同步：眯眼 + 循环箭头
        FaceExpression.SYNCING -> FaceParams(
            leftEye = EyeParams(shape = EyeShape.HAPPY_ARC, radius = 0.95f),
            rightEye = EyeParams(shape = EyeShape.HAPPY_ARC, radius = 0.95f),
            mouth = MouthParams(shape = MouthShape.FLAT, scale = 0.55f),
            earGlow = 1f,
            marks = listOf(
                MarkParams(FaceMark.SYNC_ARROW, offsetX = 0.3f, offsetY = -0.3f, scale = 0.9f, tint = MarkTint.GREEN),
            ),
        )

        // 75 升级：眯眼 + 上升箭头 + 进度条
        FaceExpression.UPDATING -> FaceParams(
            leftEye = EyeParams(shape = EyeShape.HAPPY_ARC, radius = 0.95f),
            rightEye = EyeParams(shape = EyeShape.HAPPY_ARC, radius = 0.95f),
            mouth = MouthParams(shape = MouthShape.FLAT, scale = 0.4f),
            earGlow = 1.1f,
            marks = listOf(
                MarkParams(FaceMark.ARROW_UP, offsetX = 0.3f, offsetY = -0.3f, scale = 0.9f, tint = MarkTint.GREEN),
                MarkParams(FaceMark.PROGRESS_BAR, offsetX = 0f, offsetY = 0.3f, scale = 1f, tint = MarkTint.BLUE),
            ),
        )
    }
}
