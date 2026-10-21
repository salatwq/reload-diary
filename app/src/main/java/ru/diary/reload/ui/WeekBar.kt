package ru.diary.reload.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import ru.diary.reload.data.Lesson
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

val WEEK_RANGE = -5..5
private val DAY_NAMES = listOf("ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ")

/** Понедельник «текущей» недели; в воскресенье — следующей. */
fun baseMonday(today: LocalDate = LocalDate.now()): LocalDate =
    (if (today.dayOfWeek == DayOfWeek.SUNDAY) today.plusDays(1) else today).with(DayOfWeek.MONDAY)

fun lessonOn(l: Lesson, d: LocalDate): Boolean =
    if (l.dateIso.isNotEmpty()) l.dateIso == d.toString() else l.day == d.dayOfWeek.value

@Composable
private fun ArrowButton(left: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = SlantShape(5.dp)
    Box(
        Modifier.width(34.dp).height(52.dp).alpha(if (enabled) 1f else 0.3f)
            .clip(shape).background(P3.Navy).border(2.dp, P3.Cyan, shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(12.dp, 20.dp)) {
            val w = size.width
            val h = size.height
            val p = Path().apply {
                if (left) { moveTo(w, 0f); lineTo(0f, h / 2); lineTo(w, h) }
                else { moveTo(0f, 0f); lineTo(w, h / 2); lineTo(0f, h) }
            }
            drawPath(p, P3.Cyan, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Square, join = StrokeJoin.Miter))
        }
    }
}

/** Выбор дня недели со стрелками переключения недель. dots — дни, в которых есть что показать. */
@Composable
fun WeekBar(
    monday: LocalDate,
    day: Int,
    weekOffset: Int,
    dots: Set<Int>,
    onDay: (Int) -> Unit,
    onWeek: (Int) -> Unit,
) {
    val today = LocalDate.now()
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArrowButton(true, weekOffset > WEEK_RANGE.first) { onWeek(weekOffset - 1) }
        DAY_NAMES.forEachIndexed { i, name ->
            val on = day == i + 1
            val date = monday.plusDays(i.toLong())
            val isToday = date == today
            Column(
                Modifier.weight(1f).height(52.dp).clip(SlantShape(7.dp))
                    .background(if (on) P3.Cyan else P3.Navy)
                    .clickable { onDay(i + 1) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                BigText(name, 10, if (on) P3.Navy else P3.Cyan)
                BigText(date.dayOfMonth.toString(), 18, if (on) P3.Navy else P3.White)
                Box(
                    Modifier.padding(top = 2.dp).size(width = if (isToday) 14.dp else 5.dp, height = 3.dp)
                        .background(
                            when {
                                isToday -> P3.Yellow
                                (i + 1) in dots -> if (on) P3.Navy else P3.Cyan
                                else -> Color.Transparent
                            },
                        ),
                )
            }
        }
        ArrowButton(false, weekOffset < WEEK_RANGE.last) { onWeek(weekOffset + 1) }
    }
}

/** Крупная дата выбранного дня; если смотрим не текущую неделю — кнопка «СЕГОДНЯ». */
@Composable
fun DateHeader(date: LocalDate, showToday: Boolean, onToday: () -> Unit) {
    val locale = Locale("ru")
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        BigText(date.dayOfMonth.toString(), 56)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.padding(bottom = 8.dp)) {
            BigText(date.month.getDisplayName(TextStyle.FULL, locale).uppercase(), 16, P3.Cyan)
            BigText(date.dayOfWeek.getDisplayName(TextStyle.FULL, locale).uppercase(), 13)
        }
        Spacer(Modifier.weight(1f))
        if (showToday) Box(Modifier.padding(bottom = 10.dp)) { SlantButton("СЕГОДНЯ", onClick = onToday) }
    }
}
