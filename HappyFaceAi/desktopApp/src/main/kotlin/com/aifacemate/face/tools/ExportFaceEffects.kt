package com.aifacemate.face.tools

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import com.aifacemate.face.*
import com.aifacemate.face.effects.*
import com.aifacemate.face.model.FaceExpression
import com.aifacemate.face.ui.FaceDisplay
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

@OptIn(ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    val output = File(args.firstOrNull() ?: "build/face-effects").apply { mkdirs() }
    var shots = 0
    for ((width, height) in listOf(480 to 320, 800 to 480)) for (effect in EffectId.entries) {
        val time = mutableStateOf(0L)
        val emoji = mutableStateOf(true)
        val expression = when (effect) {
            EffectId.Fireworks -> FaceExpression.CELEBRATING
            EffectId.Hearts -> FaceExpression.BASHFUL_SMILE
            EffectId.RainToSun -> FaceExpression.COMFORTING
            EffectId.StarReward -> FaceExpression.APPROVING
        }
        val scene = ImageComposeScene(width, height, Density(1f)) {
            Box(Modifier.fillMaxSize()) {
                FaceDisplay(expression = expression, animate = false)
                EffectLayer(EffectScene("export", effect, 0, seed = 42), { time.value },
                    renderAtMillis = time.value, useEmoji = emoji.value)
            }
        }
        try {
            for (fraction in listOf(.12f, .32f, .60f, .82f)) {
                time.value = (effect.durationMillis * fraction).toLong()
                scene.render(time.value * 1_000_000).encodeToData(EncodedImageFormat.PNG)!!.use {
                    File(output, "$width-${effect.name}-${(fraction * 100).toInt()}.png").writeBytes(it.bytes)
                }; shots++
            }
            emoji.value = false; time.value = effect.durationMillis * 60 / 100
            scene.render(time.value * 1_000_000).encodeToData(EncodedImageFormat.PNG)!!.use {
                File(output, "$width-${effect.name}-vector.png").writeBytes(it.bytes)
            }; shots++
        } finally { scene.close() }
    }
    for ((width, height) in listOf(320 to 240, 480 to 320, 800 to 480, 390 to 844)) {
        val preferences = mutableStateOf(FaceEffectsPreferences())
        var closed = false
        var reportedMenuOpen = false
        val closeMenuSignal = mutableStateOf(0)
        val scene = ImageComposeScene(width, height, Density(1f)) {
            MaterialTheme(colorScheme = darkColorScheme()) {
                EffectsPreviewContent(preferences.value, { preferences.value = it }, { closed = true },
                    onMenuOpenChange = { reportedMenuOpen = it }, closeMenuSignal = closeMenuSignal.value)
            }
        }
        try {
            var time = 0L
            fun frame() { time += 100_000_000; scene.render(time).close() }
            fun nodes(): List<SemanticsNode> {
                fun visit(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::visit)
                return scene.semanticsOwners.flatMap { visit(it.rootSemanticsNode) }
            }
            fun tagged(tag: String) = nodes().first { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
            fun scrollTo(tag: String) {
                fun contains(n: SemanticsNode): Boolean = n.config.getOrNull(SemanticsProperties.TestTag) == tag || n.children.any(::contains)
                val container = nodes().firstOrNull { it.config.contains(SemanticsActions.ScrollBy) && contains(it) } ?: return
                val scroll = container.config[SemanticsActions.ScrollBy].action!!
                val range = container.config[SemanticsProperties.VerticalScrollAxisRange]
                scroll(0f, -range.maxValue()); repeat(8) { frame() }
                repeat((range.maxValue() / 16).toInt() + 8) {
                    val bounds = tagged(tag).boundsInRoot
                    val area = nodes().first { it.id == container.id }.boundsInRoot
                    if (bounds.height >= 47f && bounds.top >= area.top && bounds.bottom <= area.bottom) return
                    scroll(0f, 16f); repeat(8) { frame() }
                }
                error("$width x $height cannot reach $tag")
            }
            fun click(tag: String) {
                scrollTo(tag)
                check(tagged(tag).config[SemanticsActions.OnClick].action!!.invoke())
                frame()
            }
            frame()
            check(nodes().none { it.config.getOrNull(SemanticsProperties.TestTag) == "effects.controls.pane" })
            val menu = tagged("effects.menu").boundsInRoot
            check(menu.height >= 47 && menu.top >= 0 && menu.right <= width)
            val preview = tagged("effects.preview.pane").boundsInRoot
            val face = tagged("effects.preview.canvas").boundsInRoot
            check(preview.width == width.toFloat() && preview.height == height.toFloat())
            check(kotlin.math.abs(face.width / face.height - 16f / 9f) < .03f) { "Face preview was stretched: $face" }
            scene.render(time).encodeToData(EncodedImageFormat.PNG)!!.use {
                File(output, "preview-collapsed-$width-$height.png").writeBytes(it.bytes)
            }; shots++
            click("effects.menu")
            check(reportedMenuOpen)
            val close = tagged("effects.close").boundsInRoot
            check(close.height >= 47 && close.top >= 0 && close.bottom <= height)
            val panel = tagged("effects.menu.panel").boundsInRoot
            check(panel.left == 0f && panel.top == 0f && panel.width == width.toFloat() && panel.height == height.toFloat())
            scene.render(time).encodeToData(EncodedImageFormat.PNG)!!.use {
                File(output, "preview-menu-$width-$height.png").writeBytes(it.bytes)
            }; shots++
            val padding = Offset((panel.left + panel.right) / 2, panel.top + 6)
            scene.sendPointerEvent(PointerEventType.Press, padding); frame()
            scene.sendPointerEvent(PointerEventType.Release, padding); frame()
            check(reportedMenuOpen) { "Popup padding tap leaked through to the scrim" }
            click("effects.enabled"); check(!preferences.value.enabled)
            click("effects.enabled"); check(preferences.value.enabled)
            click("effects.emoji"); check(!preferences.value.emoji)
            for (effect in EffectId.entries) {
                click("effects.preview.${effect.name}")
                check(nodes().none { it.config.getOrNull(SemanticsProperties.TestTag) == "effects.controls.pane" })
                click("effects.menu")
            }
            click("effects.menu.collapse")
            click("effects.menu")
            closeMenuSignal.value++; repeat(3) { frame() }
            check(!reportedMenuOpen && nodes().none { it.config.getOrNull(SemanticsProperties.TestTag) == "effects.controls.pane" })
            click("effects.menu")
            click("effects.stop"); click("effects.close"); check(closed)
            scene.render(time).encodeToData(EncodedImageFormat.PNG)!!.use {
                File(output, "preview-$width-$height.png").writeBytes(it.bytes)
            }; shots++
            println("PASS effects preview $width x $height: toggle, glyphs, four scenes, stop and close")
        } finally { scene.close() }
    }
    println("Exported $shots effect images to ${output.absolutePath}")
}
