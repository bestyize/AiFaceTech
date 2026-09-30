package com.aifacemate.face.tools

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import com.aifacemate.face.feature.lifecenter.*
import com.aifacemate.face.model.FaceExpression
import com.aifacemate.face.theme.AppColors
import java.io.File
import kotlin.math.abs
import org.jetbrains.skia.EncodedImageFormat

private data class GridViewport(val name: String, val width: Int, val height: Int, val columns: Int,
    val density: Float = 1f, val fontScale: Float = 1f)

/** Renders the real grid and exercises scrolling plus every navigation entry without platform services. */
@OptIn(ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    val output = File(args.firstOrNull() ?: "build/life-center").apply { mkdirs() }
    val viewports = listOf(
        GridViewport("320x240", 320, 240, 2),
        GridViewport("480x320", 480, 320, 3),
        GridViewport("800x480", 800, 480, 4),
        GridViewport("wide", 1440, 900, 4),
        GridViewport("phone", 390, 844, 2),
        GridViewport("narrow", 240, 320, 1),
        GridViewport("font130", 320, 240, 2, fontScale = 1.3f),
        GridViewport("font200", 320, 240, 1, fontScale = 2f),
        GridViewport("density150", 320, 240, 1, density = 1.5f),
    )
    val routes = defaultFeatures.map { it.route } + LifeRoute.Living
    for (viewport in viewports) {
        var navigation: LifeRoute? = null
        val scene = ImageComposeScene(viewport.width, viewport.height, Density(viewport.density, viewport.fontScale)) {
            MaterialTheme(colorScheme = darkColorScheme(primary = AppColors.Amber)) {
                LifeCenterScreen(LifeCenterUiState(), FaceExpression.HAPPY, 1f) { navigation = it }
            }
        }
        try {
            var time = 0L
            fun render() { time += 20_000_000L; scene.render(time).close() }
            fun nodes(): List<SemanticsNode> {
                fun visit(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap { visit(it) }
                return scene.semanticsOwners.flatMap { visit(it.rootSemanticsNode) }
            }
            fun tagged(tag: String) = nodes().firstOrNull { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
            fun await(condition: () -> Boolean) {
                val deadline = System.nanoTime() + 3_000_000_000L
                while (!condition() && System.nanoTime() < deadline) { render(); Thread.sleep(10) }
                check(condition()) { "${viewport.name}: grid did not settle" }
            }
            fun shot(suffix: String) {
                scene.render(time).encodeToData(EncodedImageFormat.PNG)!!.use { File(output, "${viewport.name}-$suffix.png").writeBytes(it.bytes) }
            }
            render()
            val firstTiles = nodes().filter { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("life.feature.") == true && it.boundsInRoot.height > 0 }
            val firstY = firstTiles.minOf { it.boundsInRoot.top }
            val columns = firstTiles.count { abs(it.boundsInRoot.top - firstY) < 1f }
            check(columns == viewport.columns && columns <= 4) { "${viewport.name}: expected ${viewport.columns} columns, got $columns" }
            shot("top")
            for ((index, route) in routes.withIndex()) {
                val grid = checkNotNull(tagged("life.grid"))
                check(grid.config[SemanticsActions.ScrollToIndex].action?.invoke(index) == true)
                await {
                    tagged("life.feature.${route.name}")?.boundsInRoot?.let { bounds ->
                        bounds.height > 0 && bounds.top >= -1f && bounds.bottom <= viewport.height + 1f
                    } == true
                }
                val tile = checkNotNull(tagged("life.feature.${route.name}"))
                check(tile.boundsInRoot.width >= 48 * viewport.density && tile.boundsInRoot.height >= 48 * viewport.density)
                navigation = null
                check(tile.config[SemanticsActions.OnClick].action?.invoke() == true)
                check(navigation == route) { "${viewport.name}: incorrect navigation for $route" }
            }
            shot("bottom")
            println("PASS ${viewport.name}: $columns columns, vertical scroll, all ${routes.size} entries reachable and routed")
        } finally { scene.close() }
    }
    println("Exported life-center grid screenshots to ${output.absolutePath}")
}
