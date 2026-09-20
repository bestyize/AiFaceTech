package com.aifacemate.face.tools

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.aifacemate.face.feature.lifecenter.*
import com.aifacemate.face.model.FaceExpression
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

/** Render actual shared UI at desktop, compact landscape and phone sizes. */
@OptIn(ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    val output = File(args.firstOrNull() ?: "build/life-center").apply { mkdirs() }
    listOf(Triple("wide", 1440, 900), Triple("compact-landscape", 800, 450), Triple("phone", 390, 844)).forEach { (name, width, height) ->
        val scene = ImageComposeScene(width, height, Density(1f)) {
            MaterialTheme(colorScheme = darkColorScheme(primary = LifeColors.Amber)) {
                LifeCenterScreen(LifeCenterUiState(), FaceExpression.HAPPY, 1f) {}
            }
        }
        try {
            scene.render().encodeToData(EncodedImageFormat.PNG)!!.use { File(output, "$name.png").writeBytes(it.bytes) }
        } finally { scene.close() }
    }
}
