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

/** Синий градиент с медленно «едущими» диагональными полосами. */
@Composable
fun P3Background(content: @Composable BoxScope.() -> Unit) {
    val t = rememberInfiniteTransition(label = "bg")
    val shift by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Restart),
        label = "shift",
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(P3.Blue, P3.DeepBlue)))
            .drawBehind {
                val step = 96.dp.toPx()
                var x = -size.height + shift * step
                while (x < size.width) {
                    drawLine(
                        Color(0x1842F0FF), Offset(x, size.height), Offset(x + size.height, 0f),
                        strokeWidth = 20.dp.toPx(),
                    )
                    x += step
                }
            },
        content = content,
    )
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
