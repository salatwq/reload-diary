package ru.diary.reload.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Палитра в духе синего меню паузы (оригинальные значения, без ассетов игры). */
object P3 {
    val Blue = Color(0xFF0A2BD6)
    val DeepBlue = Color(0xFF020B4A)
    val Navy = Color(0xFF00081F)
    val Cyan = Color(0xFF42F0FF)
    val White = Color(0xFFF2FBFF)
    val Yellow = Color(0xFFFFD23F)
    val Red = Color(0xFFFF3B5C)
}

/** Параллелограмм — «рубленая» форма пунктов меню. */
class SlantShape(private val skew: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val s = with(density) { skew.toPx() }.coerceAtMost(size.width / 2)
        val p = Path().apply {
            moveTo(s, 0f); lineTo(size.width, 0f)
            lineTo(size.width - s, size.height); lineTo(0f, size.height); close()
        }
        return Outline.Generic(p)
    }
}

private class Diamond(val x: Float, val y: Float, val size: Float, val speed: Int, val alpha: Float, val cyan: Boolean)

/** Глубокий синий с мягким свечением, точечной сеткой, вращающимся «циферблатом» и плывущими ромбами. */
@Composable
fun P3Background(content: @Composable BoxScope.() -> Unit) {
    val t = rememberInfiniteTransition(label = "bg")
    val phase by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(30000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    val diamonds = remember {
        val r = Random(7)
        List(18) {
            Diamond(r.nextFloat(), r.nextFloat(), 14f + r.nextFloat() * 46f, 1 + r.nextInt(3), 0.05f + r.nextFloat() * 0.13f, r.nextBoolean())
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                val w = size.width
                val h = size.height
                drawRect(
                    Brush.radialGradient(
                        listOf(Color(0xFF2C66FF), Color(0xFF0A2BD6), Color(0xFF020B4A)),
                        center = Offset(w * 0.15f, h * 0.1f), radius = maxOf(w, h) * 1.15f,
                    ),
                )
                // точечная сетка
                val step = 34.dp.toPx()
                val dot = Color(0x1442F0FF)
                var gy = step / 2
                while (gy < h) {
                    var gx = step / 2
                    while (gx < w) { drawCircle(dot, 1.6.dp.toPx(), Offset(gx, gy)); gx += step }
                    gy += step
                }
                // «циферблат» в правом нижнем углу
                val c = Offset(w * 0.92f, h * 0.9f)
                rotate(phase * 360f, c) {
                    listOf(0.45f, 0.7f, 0.98f).forEachIndexed { i, rr ->
                        drawCircle(
                            Color(0x2B42F0FF), w * rr, c,
                            style = Stroke(width = (2 + i).dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(46f * (i + 1), 30f))),
                        )
                    }
                }
                rotate(-phase * 180f, c) {
                    drawCircle(
                        Color(0x1AFFFFFF), w * 0.3f, c,
                        style = Stroke(width = 6.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 36f))),
                    )
                }
                // плывущие вверх ромбы
                diamonds.forEach { d ->
                    val s = d.size.dp.toPx()
                    val fy = (((d.y - phase * d.speed) % 1f) + 1f) % 1f
                    val cx = d.x * w + sin(phase * 2f * PI.toFloat() * d.speed + d.x * 6f) * 12.dp.toPx()
                    val cy = fy * (h + 2 * s) - s
                    val p = Path().apply {
                        moveTo(cx, cy - s); lineTo(cx + s * 0.7f, cy); lineTo(cx, cy + s); lineTo(cx - s * 0.7f, cy); close()
                    }
                    drawPath(p, (if (d.cyan) Color(0xFF42F0FF) else Color.White).copy(alpha = d.alpha))
                }
                // тёмный угол для глубины
                val corner = Path().apply { moveTo(0f, h * 0.72f); lineTo(w * 0.45f, h); lineTo(0f, h); close() }
                drawPath(corner, Color(0x59000820))
            },
        content = content,
    )
}

private val URL_RE = Regex("""https?://[^\s]+""")

/** Делает ссылки в тексте кликабельными (открываются в браузере по умолчанию). */
fun linkified(text: String): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (m in URL_RE.findAll(text)) {
        val url = m.value.trimEnd('.', ',', ')', ';', '!', '?')
        append(text.substring(last, m.range.first))
        withLink(
            LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = P3.Cyan, textDecoration = TextDecoration.Underline))),
        ) { append(url) }
        last = m.range.first + url.length
    }
    append(text.substring(last))
}

@Composable
fun ScreenFrame(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    P3Background {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.clip(SlantShape(14.dp)).background(P3.Cyan)
                        .padding(horizontal = 30.dp, vertical = 10.dp),
                ) {
                    Text(
                        title.uppercase(), color = P3.Navy, fontSize = 24.sp,
                        fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic,
                    )
                }
                Spacer(Modifier.weight(1f))
                SlantButton("НАЗАД", onClick = onBack)
            }
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
fun SlantCard(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = SlantShape(12.dp)
    var m = modifier
        .background(if (selected) P3.Cyan else P3.Navy.copy(alpha = 0.88f), shape)
        .border(2.dp, P3.Cyan, shape)
        .clip(shape)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Column(m.padding(horizontal = 26.dp, vertical = 12.dp), content = content)
}

@Composable
fun SlantButton(text: String, modifier: Modifier = Modifier, filled: Boolean = true, onClick: () -> Unit) {
    val shape = SlantShape(8.dp)
    Box(
        modifier
            .background(if (filled) P3.Cyan else P3.Navy, shape)
            .border(2.dp, P3.Cyan, shape)
            .clip(shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, color = if (filled) P3.Navy else P3.Cyan, fontSize = 14.sp,
            fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic,
        )
    }
}

@Composable
fun BigText(text: String, size: Int, color: Color = P3.White) {
    Text(text, color = color, fontSize = size.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
}

val SpacedList = Arrangement.spacedBy(10.dp)

/** Плавное появление: карточка выезжает слева с задержкой по номеру. */
@Composable
fun Modifier.appear(index: Int): Modifier {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay((index * 50L).coerceAtMost(450L))
        a.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
    }
    return this.graphicsLayer {
        alpha = a.value
        translationX = (1f - a.value) * 140f
    }
}
