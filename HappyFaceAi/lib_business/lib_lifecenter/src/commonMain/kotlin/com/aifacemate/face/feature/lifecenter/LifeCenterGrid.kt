package com.aifacemate.face.feature.lifecenter

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val GridGold = Color(0xFFE5C7A1)
private val GridText = Color(0xFFF2EFEB)

@Composable
internal fun LifeCenterGrid(features: List<LifeCenterFeature>, onNavigate: (LifeRoute) -> Unit) {
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val entries = if (features.any { it.route == LifeRoute.Living }) features else features +
        LifeCenterFeature(LifeRoute.Living, "返回陪伴", "回到全屏表情页", "‹")
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF090B0B)).safeDrawingPadding()) {
        val padding = when { maxWidth < 400.dp -> 12.dp; maxWidth < 700.dp -> 16.dp; else -> 24.dp }
        val gap = if (maxWidth < 400.dp) 10.dp else if (maxWidth < 700.dp) 12.dp else 16.dp
        val available = (maxWidth - padding * 2).coerceAtLeast(0.dp)
        val minimumTile = 128.dp * maxOf(1f, fontScale / 1.3f)
        val columns = ((available + gap) / (minimumTile + gap)).toInt().coerceIn(1, 4)
        val tileWidth = ((available - gap * (columns - 1)) / columns).coerceAtLeast(0.dp)
        val minimumHeight = 88.dp * maxOf(1f, fontScale / 1.3f)
        val twoRows = ((maxHeight - padding * 2 - gap) / 2).coerceAtLeast(minimumHeight)
        val tileHeight = if (maxHeight < 360.dp) minOf(tileWidth, twoRows).coerceAtLeast(minimumHeight)
            else tileWidth.coerceIn(minimumHeight, maxOf(minimumHeight, 200.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns), state = rememberLazyGridState(),
            contentPadding = PaddingValues(padding),
            horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(gap),
            modifier = Modifier.fillMaxSize().testTag("life.grid").semantics { contentDescription = "生命中心" },
        ) {
            items(entries, key = { it.route.name }) { feature ->
                LifeGridTile(feature, tileHeight, tileWidth, onClick = { onNavigate(feature.route) })
            }
        }
    }
}

@Composable
private fun LifeGridTile(feature: LifeCenterFeature, height: Dp, width: Dp, onClick: () -> Unit) {
    val compact = height < 120.dp
    val shape = RoundedCornerShape(if (compact) 12.dp else 16.dp)
    Surface(onClick = onClick, color = Color.Transparent, shape = shape,
        border = BorderStroke(1.dp, GridGold.copy(alpha = .43f)),
        modifier = Modifier.fillMaxWidth().height(height).testTag("life.feature.${feature.route.name}")
            .semantics { contentDescription = "${feature.title}，${feature.subtitle}" },
    ) {
        Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF252524), Color(0xFF111313))))
            .padding(horizontal = 8.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp, Alignment.CenterVertically)) {
            Canvas(Modifier.size(if (compact) 30.dp else 42.dp)) { drawLifeIcon(feature.route) }
            Text(feature.title, color = GridText, fontWeight = FontWeight.Medium,
                fontSize = if (compact || width < 150.dp) 15.sp else 18.sp,
                lineHeight = if (compact || width < 150.dp) 19.sp else 23.sp,
                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Platform-independent outline icons; every tile has the same visual weight and warm accent. */
private fun DrawScope.drawLifeIcon(route: LifeRoute) {
    val unit = size.minDimension
    val stroke = Stroke(unit * .055f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun point(x: Float, y: Float) = Offset(x * unit, y * unit)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(GridGold, point(x1, y1), point(x2, y2), stroke.width, StrokeCap.Round)
    fun outline(path: Path) = drawPath(path, GridGold, style = stroke)
    fun Path.move(x: Float, y: Float) = moveTo(x * unit, y * unit)
    fun Path.line(x: Float, y: Float) = lineTo(x * unit, y * unit)
    fun Path.curve(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) =
        cubicTo(x1 * unit, y1 * unit, x2 * unit, y2 * unit, x3 * unit, y3 * unit)
    when (route) {
        LifeRoute.Brain -> {
            outline(Path().apply {
                move(.49f, .19f); curve(.4f, .07f, .24f, .13f, .26f, .27f)
                curve(.12f, .26f, .1f, .4f, .18f, .48f); curve(.08f, .59f, .16f, .73f, .29f, .73f)
                curve(.24f, .88f, .44f, .93f, .49f, .78f); line(.49f, .19f)
                move(.51f, .19f); curve(.6f, .07f, .76f, .13f, .74f, .27f)
                curve(.88f, .26f, .9f, .4f, .82f, .48f); curve(.92f, .59f, .84f, .73f, .71f, .73f)
                curve(.76f, .88f, .56f, .93f, .51f, .78f); line(.51f, .19f)
            })
            line(.28f, .36f, .36f, .4f); line(.27f, .57f, .36f, .55f)
            line(.72f, .36f, .64f, .4f); line(.73f, .57f, .64f, .55f)
        }
        LifeRoute.Voice -> listOf(.24f, .48f, .74f, .94f, .62f, .4f, .2f).forEachIndexed { index, length ->
            val x = .14f + index * .12f
            line(x, .5f - length * .4f, x, .5f + length * .4f)
        }
        LifeRoute.Vision, LifeRoute.Camera -> {
            outline(Path().apply {
                move(.08f, .5f); curve(.3f, .14f, .7f, .14f, .92f, .5f)
                curve(.7f, .86f, .3f, .86f, .08f, .5f); close()
            })
            drawCircle(GridGold, unit * .14f, point(.5f, .5f), style = stroke)
        }
        LifeRoute.Memory -> {
            outline(Path().apply {
                move(.5f, .23f); curve(.36f, .12f, .2f, .14f, .12f, .18f); line(.12f, .8f)
                curve(.27f, .75f, .38f, .75f, .5f, .86f); curve(.62f, .75f, .73f, .75f, .88f, .8f)
                line(.88f, .18f); curve(.8f, .14f, .64f, .12f, .5f, .23f); line(.5f, .86f)
            })
        }
        LifeRoute.Appearance -> {
            drawCircle(GridGold, unit * .38f, point(.5f, .5f), style = stroke)
            drawCircle(GridGold, unit * .025f, point(.36f, .4f)); drawCircle(GridGold, unit * .025f, point(.64f, .4f))
            drawArc(GridGold, 20f, 140f, false, point(.31f, .4f), Size(unit * .38f, unit * .3f), style = stroke)
        }
        LifeRoute.Skills -> {
            outline(Path().apply {
                move(.5f, .1f); line(.86f, .3f); line(.86f, .7f); line(.5f, .9f); line(.14f, .7f)
                line(.14f, .3f); close(); move(.14f, .3f); line(.5f, .5f); line(.86f, .3f); move(.5f, .5f); line(.5f, .9f)
            })
        }
        LifeRoute.Personality -> {
            drawCircle(GridGold, unit * .16f, point(.5f, .27f), style = stroke)
            outline(Path().apply {
                move(.18f, .86f); line(.18f, .78f); curve(.18f, .5f, .82f, .5f, .82f, .78f); line(.82f, .86f); close()
            })
        }
        LifeRoute.DeviceSystem -> {
            outline(Path().apply {
                for (index in 0 until 32) {
                    val angle = index * 2 * PI / 32
                    val radius = if (index % 4 == 0 || index % 4 == 3) .41f else .33f
                    val x = .5f + cos(angle).toFloat() * radius
                    val y = .5f + sin(angle).toFloat() * radius
                    if (index == 0) move(x, y) else line(x, y)
                }
                close()
            })
            drawCircle(GridGold, unit * .13f, point(.5f, .5f), style = stroke)
        }
        LifeRoute.Rhythm -> outline(Path().apply {
            move(.64f, .13f); curve(.06f, .13f, .06f, .87f, .62f, .87f)
            curve(.79f, .87f, .87f, .75f, .9f, .63f); curve(.46f, .82f, .31f, .38f, .64f, .13f); close()
        })
        LifeRoute.Living -> {
            outline(Path().apply { move(.43f, .16f); line(.15f, .43f); line(.43f, .7f) })
            outline(Path().apply { move(.15f, .43f); line(.63f, .43f); curve(.89f, .43f, .94f, .83f, .63f, .83f) })
        }
        else -> drawRoundRect(GridGold, point(.16f, .16f), Size(unit * .68f, unit * .68f), CornerRadius(unit * .12f), style = stroke)
    }
}
