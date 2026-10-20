package ru.diary.reload.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.delay
import ru.diary.reload.R

/** Диагональная «шторка», пробегающая по экрану при смене раздела. */
@Composable
fun WipeOverlay(trigger: Any) {
    val p = remember { Animatable(1f) }
    LaunchedEffect(trigger) {
        p.snapTo(0f)
        p.animateTo(1f, tween(560, easing = FastOutSlowInEasing))
    }
    val v = p.value
    if (v > 0.001f && v < 0.999f) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val x = -0.7f * w + 1.8f * w * v
            band(x - 0.35f * w, 0.35f * w, P3.Navy.copy(alpha = 0.95f))
            band(x, 0.14f * w, P3.Cyan)
            band(x + 0.16f * w, 0.04f * w, P3.White)
        }
    }
}

private fun DrawScope.band(left: Float, width: Float, color: Color) {
    val h = size.height
    val sk = h * 0.3f
    val path = Path().apply {
        moveTo(left + sk, 0f); lineTo(left + width + sk, 0f)
        lineTo(left + width, h); lineTo(left, h); close()
    }
    drawPath(path, color)
}

/** Заставка с логотипом; по тапу пропускается. */
@Composable
fun Splash(onDone: () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        a.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
        delay(900)
        onDone()
    }
    Box(
        Modifier.fillMaxSize().background(Color.Black).clickable { onDone() },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painterResource(R.drawable.logo), contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth(0.88f).graphicsLayer {
                alpha = a.value
                val sc = 0.8f + 0.2f * a.value
                scaleX = sc; scaleY = sc
                translationY = (1f - a.value) * 60f
            },
        )
    }
}
