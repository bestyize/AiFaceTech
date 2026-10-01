package com.aifacemate.face.tools

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import com.aifacemate.face.model.FaceExpression
import com.aifacemate.face.ui.FacePreviewScreen
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

@OptIn(ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    val output = File(args.firstOrNull() ?: "build/face-menus").apply { mkdirs() }
    for ((width, height) in listOf(320 to 240, 480 to 320, 800 to 480, 1180 to 820, 390 to 844)) {
        val expression = mutableStateOf(FaceExpression.IDLE)
        val glow = mutableStateOf(1f)
        val closeSignal = mutableStateOf(0)
        var open = false
        var returned = false
        val scene = ImageComposeScene(width, height, Density(1f)) {
            MaterialTheme(colorScheme = darkColorScheme()) {
                FacePreviewScreen(expression = expression.value, onExpressionChange = { expression.value = it },
                    diffuseGlow = glow.value, onDiffuseGlowChange = { glow.value = it },
                    onBack = { returned = true }, onMenuOpenChange = { open = it }, closeMenuSignal = closeSignal.value)
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
            fun click(tag: String) {
                if (tag.startsWith("face.expression.") || tag in listOf("face.life", "face.glow", "face.surface.DEVICE_SCREEN")) {
                    val container = tagged("face.controls.scroll")
                    val scroll = container.config[SemanticsActions.ScrollBy].action!!
                    val range = container.config[SemanticsProperties.VerticalScrollAxisRange]
                    scroll(0f, -range.maxValue()); repeat(8) { frame() }
                    var reached = false
                    for (step in 0..(range.maxValue() / 16).toInt() + 10) {
                        val bounds = tagged(tag).boundsInRoot
                        val area = tagged("face.controls.scroll").boundsInRoot
                        if (bounds.height >= 31 && bounds.top >= area.top && bounds.bottom <= area.bottom) { reached = true; break }
                        scroll(0f, 16f); repeat(8) { frame() }
                    }
                    check(reached) { "$width x $height cannot reach $tag" }
                }
                check(tagged(tag).boundsInRoot.height >= 31)
                check(tagged(tag).config[SemanticsActions.OnClick].action!!.invoke()); frame()
            }
            fun save(state: String) {
                scene.render(time).encodeToData(EncodedImageFormat.PNG)!!.use {
                    File(output, "$state-$width-$height.png").writeBytes(it.bytes)
                }
            }
            frame()
            check(!open)
            val preview = tagged("face.preview.pane").boundsInRoot
            check(preview.width == width.toFloat() && preview.height == height.toFloat())
            save("preview")
            click("face.menu"); check(open)
            val panel = tagged("face.menu.panel").boundsInRoot
            check(panel.left == 0f && panel.top == 0f && panel.width == width.toFloat() && panel.height == height.toFloat())
            check(nodes().count { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("face.expression.") == true } == 100)
            val area = tagged("face.controls.scroll").boundsInRoot
            val collapse = tagged("face.menu.collapse").boundsInRoot
            check(area.top == 12f && area.bottom == height - 12f) { "Close control reserved a header row: $area" }
            check(collapse.width == 32f && collapse.height == 32f && collapse.top == 12f)
            save("menu")
            click("face.tab.settings"); save("settings"); click("face.life"); click("face.glow"); check(glow.value == 0f)
            click("face.surface.DEVICE_SCREEN")
            click("face.tab.expressions"); click("face.expression.HAPPY"); check(expression.value == FaceExpression.HAPPY && !open)
            click("face.menu")
            click("face.expression.NEW_DAY"); check(expression.value == FaceExpression.NEW_DAY && !open)
            click("face.menu"); closeSignal.value++; repeat(3) { frame() }; check(!open)
            click("face.menu"); click("face.menu.collapse"); check(!open)
            click("face.menu"); click("face.back"); check(returned)
            println("PASS face menu $width x $height: full-screen, 100 expressions, scrolling, toggles and return")
        } finally { scene.close() }
    }
}
