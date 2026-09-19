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
    }
}
