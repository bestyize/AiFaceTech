package com.aifacemate.face.effects

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.*
import kotlin.random.Random

private val Gold = Color(0xFFFFD479)
private val Aqua = Color(0xFF8AE3F0)
private val Pink = Color(0xFFFF96B7)
private val Palette = listOf(Gold, Aqua, Pink, Color(0xFFBFA5FF))

/** Transparent decoration only: no pointer handlers or clickable semantics. Stops ticking when absent/expired. */
@Composable
fun EffectLayer(scene: EffectScene?, nowMillis: () -> Long, modifier: Modifier = Modifier,
    strength: Float = .75f, useEmoji: Boolean = true, quiet: Boolean = false,
    renderAtMillis: Long? = null, faceOffsetY: Dp = 0.dp) {
    if (scene == null) return
    var frameMillis by remember(scene.instanceId) { mutableStateOf(nowMillis()) }
    LaunchedEffect(scene.instanceId, renderAtMillis) {
        if (renderAtMillis != null) return@LaunchedEffect
        var lastFrame = 0L
        while (nowMillis() - scene.startedAtMillis < scene.effect.durationMillis) {
            val frame = withFrameNanos { it }
            if (frame - lastFrame >= 33_000_000) { frameMillis = nowMillis(); lastFrame = frame }
        }
        frameMillis = scene.startedAtMillis + scene.effect.durationMillis
    }
    // Random descriptors and paths are created once per scene, not once per frame.
    val particles = remember(scene.instanceId, scene.seed) {
        val random = Random(scene.seed)
        FloatArray(96 * 4) { random.nextFloat() }
    }
    val heart = remember {
        Path().apply {
            moveTo(.5f, .92f); cubicTo(-.3f, .35f, .05f, -.18f, .5f, .22f)
            cubicTo(.95f, -.18f, 1.3f, .35f, .5f, .92f); close()
        }
    }
    val measurer = rememberTextMeasurer()
    val glyphs = remember(measurer) {
        listOf("💛", "✨", "☁️", "☀️").associateWith {
            measurer.measure(AnnotatedString(it), style = TextStyle(fontSize = 26.sp))
        }
    }
    Canvas(modifier.fillMaxSize().clipToBounds()) {
        val p = scene.progress(renderAtMillis ?: frameMillis)
        if (p >= 1f) return@Canvas
        val intensity = (if (strength.isFinite()) strength.coerceIn(0f, 1f) else 0f) * if (quiet) .32f else 1f
        if (intensity <= 0f) return@Canvas
        val fade = min(p / .10f, (1f - p) / .18f).coerceIn(0f, 1f) * intensity
        fun protection(point: Offset): Float {
            val x = point.x / size.width; val y = point.y / size.height
            val faceY = (point.y - faceOffsetY.toPx()) / size.height
            return when {
                x > .78f && y < .25f -> 0f // camera shortcut, including small-screen touch target
                y > .82f -> .15f // voice/HUD area
                x in .12f.. .88f && faceY in .22f.. .79f -> .12f // eyes and mouth, including voice-mode offset
                else -> 1f
            }
        }
        fun symbol(text: String, point: Offset, alpha: Float, turn: Float = 0f) {
            val layout = glyphs.getValue(text)
            val edge = (size.minDimension * .075f).coerceIn(10.dp.toPx(), 26.dp.toPx())
            val factor = edge / layout.size.height.coerceAtLeast(1)
            withTransform({ translate(point.x, point.y); rotate(turn, Offset.Zero); scale(factor, factor, Offset.Zero) }) {
                drawText(layout, topLeft = -Offset(layout.size.width / 2f, layout.size.height / 2f), alpha = alpha.coerceIn(0f, 1f))
            }
        }
        when (scene.effect) {
            EffectId.Fireworks -> {
                // Two rockets, staggered blooms, then drifting golden paper.
                repeat(2) { burst ->
                    val t = (p * 1.5f - burst * .22f).coerceIn(0f, 1f)
                    val center = Offset(size.width * if (burst == 0) .18f else .70f, size.height * .17f)
                    if (t < .24f) {
                        val tip = Offset(center.x, size.height * (.88f - .71f * t / .24f))
                        drawLine(Gold.copy(alpha = fade * .5f), tip + Offset(0f, size.height * .07f), tip, 2f, StrokeCap.Round)
                    } else {
                        val age = (t - .24f) / .76f
                        val count = if (quiet) 24 else 42
                        repeat(count) { i ->
                            val speed = .55f + particles[i * 4] * .45f
                            val angle = (i * 2 * PI / count + burst * .2).toFloat()
                            val radius = size.minDimension * .40f * (1f - (1f - age).pow(2)) * speed
                            val position = center + Offset(cos(angle) * radius, sin(angle) * radius + size.height * age * age * .22f)
                            val alpha = fade * (1f - age) * protection(position)
                            val direction = Offset(cos(angle), sin(angle)) * (size.minDimension * .024f)
                            drawLine(Palette[(i + burst) % Palette.size].copy(alpha = alpha), position - direction, position,
                                (size.minDimension * .008f).coerceIn(1.2f, 3f), StrokeCap.Round)
                            drawCircle(Gold.copy(alpha = alpha * .35f), size.minDimension * .015f, position)
                        }
                    }
                }
                if (p > .45f) repeat(if (quiet) 8 else 18) { i ->
                    val point = Offset(size.width * particles[i * 4], size.height * ((p - .45f) * .9f + particles[i * 4 + 1] * .2f))
                    drawCircle(Palette[i % 4].copy(alpha = fade * protection(point)), size.minDimension * .006f, point)
                }
                if (useEmoji && p > .45f) symbol("✨", Offset(size.width * .48f, size.height * .13f), fade)
            }
            EffectId.Hearts -> {
                repeat(if (quiet) 4 else 7) { i ->
                    val age = ((p - i * .055f) / .65f).coerceIn(0f, 1f)
                    if (age <= 0f || age >= 1f) return@repeat
                    val side = if (i % 2 == 0) .09f else .91f
                    val point = Offset(size.width * (side + sin(age * PI.toFloat() * 2 + i) * .028f),
                        size.height * (.76f - age * .65f))
                    val alpha = fade * min(age * 5, (1f - age) * 4).coerceIn(0f, 1f) * protection(point)
                    if (useEmoji && i < 4) symbol("💛", point, alpha, sin(age * PI.toFloat()) * 14f)
                    else {
                        val edge = (size.minDimension * .055f).coerceIn(12.dp.toPx(), 28.dp.toPx())
                        withTransform({ translate(point.x - edge / 2, point.y - edge / 2); scale(edge, edge, Offset.Zero) }) {
                            drawPath(heart, (if (i % 2 == 0) Gold else Pink).copy(alpha = alpha))
                        }
                    }
                }
                if (useEmoji) symbol("✨", Offset(size.width * .12f, size.height * .12f), fade * .7f)
            }
            EffectId.RainToSun -> {
                val rain = (1f - ((p - .48f) / .25f).coerceIn(0f, 1f)) * fade
                repeat(if (quiet) 32 else 64) { i ->
                    val x = particles[i * 4] * size.width
                    val y = ((particles[i * 4 + 1] + p * (1.5f + particles[i * 4 + 2])) % 1f) * size.height
                    val point = Offset(x, y)
                    drawLine(Aqua.copy(alpha = rain * .55f * protection(point)), point,
                        point + Offset(-size.height * .012f, size.height * .035f), 1.4f, StrokeCap.Round)
                }
                val cloud = Offset(size.width * .16f, size.height * .14f)
                if (useEmoji) symbol("☁️", cloud, rain)
                else {
                    drawCircle(Aqua.copy(alpha = rain * .5f), size.minDimension * .05f, cloud)
                    drawCircle(Aqua.copy(alpha = rain * .5f), size.minDimension * .04f, cloud + Offset(18f, 4f))
                }
                val sunshine = ((p - .52f) / .3f).coerceIn(0f, 1f) * fade
                val sun = Offset(size.width * .72f, size.height * .15f)
                if (useEmoji) symbol("☀️", sun, sunshine)
                else drawStar(sun, size.minDimension * .07f, sunshine, p * 20, Gold)
                repeat(6) { i -> drawStar(Offset(size.width * (.1f + i * .12f), size.height * (.12f + particles[i] * .08f)),
                    size.minDimension * .016f, sunshine, 0f, Gold) }
            }
            EffectId.StarReward -> {
                val center = Offset(size.width * .50f, size.height * .14f)
                drawCircle(Gold.copy(alpha = fade * (1f - p) * .35f), size.minDimension * (.03f + p * .18f), center,
                    style = Stroke(1.5f))
                drawStar(center, size.minDimension * (.07f + sin(p * PI.toFloat()) * .02f), fade, p * 36f, Gold)
                repeat(if (quiet) 4 else 8) { i ->
                    val angle = i * PI.toFloat() / 4
                    val radius = size.minDimension * (.09f + p * .17f)
                    val point = center + Offset(cos(angle), sin(angle)) * radius
                    drawStar(point, size.minDimension * .015f, fade * (1f - p) * protection(point), i * 20f, Aqua)
                }
                if (useEmoji) symbol("✨", Offset(size.width * .82f, size.height * .27f), fade * .65f)
            }
        }
    }
}

private fun DrawScope.drawStar(center: Offset, radius: Float, alpha: Float, angle: Float, color: Color) {
    rotate(angle, center) {
        drawLine(color.copy(alpha = alpha.coerceIn(0f, 1f)), center - Offset(radius, 0f), center + Offset(radius, 0f), 2f, StrokeCap.Round)
        drawLine(color.copy(alpha = alpha.coerceIn(0f, 1f)), center - Offset(0f, radius), center + Offset(0f, radius), 2f, StrokeCap.Round)
    }
}
