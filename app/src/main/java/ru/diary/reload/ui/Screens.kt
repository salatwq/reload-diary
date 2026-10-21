package ru.diary.reload.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.painterResource
import ru.diary.reload.R
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.diary.reload.AppViewModel
import ru.diary.reload.Screen
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

// ───────────────────────── Главное меню ─────────────────────────

@Composable
fun MenuScreen(vm: AppViewModel, onOpen: (Screen) -> Unit) {
    val items = Screen.entries.filter { it != Screen.MENU && it != Screen.BROWSER && it != Screen.SPLASH }
    var sel by rememberSaveable { mutableIntStateOf(0) }
    val haptic = LocalHapticFeedback.current
    val now = LocalDate.now()
    val locale = Locale("ru")
    val todayLessons = vm.data.lessons.count { lessonOn(it, now) }

    P3Background {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(20.dp)) {
            // «календарь» в шапке
            Row(verticalAlignment = Alignment.Bottom) {
                BigText(now.dayOfMonth.toString(), 92)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.padding(bottom = 14.dp)) {
                    BigText(now.month.getDisplayName(TextStyle.FULL, locale).uppercase(), 22, P3.Cyan)
                    BigText(now.dayOfWeek.getDisplayName(TextStyle.FULL, locale).uppercase(), 16)
                }
                Spacer(Modifier.weight(1f))
                SlantCard { BigText("УРОКОВ: $todayLessons", 16, P3.Cyan) }
            }
            Spacer(Modifier.weight(1f))
            // наклонный список пунктов
            Column(
                Modifier.graphicsLayer {
                    rotationZ = -5f
                    transformOrigin = TransformOrigin(0f, 0.5f)
                },
            ) {
                items.forEachIndexed { i, s ->
                    MenuItem(i, s.title, i == sel) {
                        if (i == sel) onOpen(s) else {
                            sel = i
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuItem(index: Int, label: String, selected: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (selected) 1.08f else 1f, label = "scale")
    Box(
        Modifier.fillMaxWidth().padding(vertical = 5.dp).appear(index)
            .graphicsLayer {
                scaleX = scale; scaleY = scale
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
            .clip(SlantShape(20.dp))
            .background(if (selected) P3.Cyan else P3.Navy)
            .clickable(onClick = onClick)
            .padding(horizontal = 36.dp, vertical = 16.dp),
    ) {
        BigText(label.uppercase(), 26, if (selected) P3.Navy else P3.White)
    }
}

// ───────────────────────── Расписание ─────────────────────────

@Composable
fun ScheduleScreen(vm: AppViewModel, onBack: () -> Unit) {
    val base = remember { baseMonday() }
    val monday = base.plusWeeks(vm.weekOffset.toLong())
    val date = monday.plusDays((vm.selectedDay - 1).toLong())
    val dots = (1..6).filter { d -> vm.data.lessons.any { lessonOn(it, monday.plusDays((d - 1).toLong())) } }.toSet()
    ScreenFrame("Расписание", onBack) {
        WeekBar(monday, vm.selectedDay, vm.weekOffset, dots, { vm.selectedDay = it }, { vm.weekOffset = it })
        Spacer(Modifier.height(8.dp))
        DateHeader(date, !vm.atToday) { vm.goToday() }
        Spacer(Modifier.height(6.dp))
        val list = vm.data.lessons.filter { lessonOn(it, date) }.sortedBy { it.number }
        if (list.isEmpty()) EmptyState(vm, "В этот день уроков нет")
        else LazyColumn(Modifier.weight(1f), verticalArrangement = SpacedList) {
            itemsIndexed(list, key = { _, l -> "${l.dateIso}-${l.day}-${l.number}-${l.subject}" }) { _, l ->
                SlantCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BigText(l.number.toString(), 40, P3.Cyan)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            BigText(l.subject, 18)
                            Text(
                                listOf(l.time, if (l.room.isNotBlank()) "каб. ${l.room}" else "", l.teacher)
                                    .filter { it.isNotBlank() }.joinToString("  ·  "),
                                color = P3.White.copy(alpha = 0.75f), fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ───────────────────────── Оценки ─────────────────────────

private fun markColor(m: String) = when (m.firstOrNull()) {
    '5' -> P3.Cyan
    '4' -> P3.White
    '3' -> P3.Yellow
    '2' -> P3.Red
    else -> P3.White.copy(alpha = 0.6f)
}

@Composable
fun GradesScreen(vm: AppViewModel, onBack: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    ScreenFrame("Оценки", onBack) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("ТЕКУЩИЕ", "ИТОГОВЫЕ").forEachIndexed { i, t ->
                val on = tab == i
                Box(
                    Modifier.weight(1f).clip(SlantShape(8.dp))
                        .background(if (on) P3.Cyan else P3.Navy)
                        .clickable { tab = i }.padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) { BigText(t, 15, if (on) P3.Navy else P3.White) }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (tab == 0) {
            if (vm.data.grades.isEmpty()) EmptyState(vm, "Оценок пока нет")
            else LazyColumn(Modifier.weight(1f), verticalArrangement = SpacedList) {
                itemsIndexed(vm.data.grades) { i, g ->
                    SlantCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                BigText(g.subject, 17)
                                Spacer(Modifier.height(6.dp))
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    g.marks.forEach { BigText(it, 22, markColor(it)) }
                                }
                            }
                            if (g.average.isNotBlank()) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("СР. БАЛЛ", color = P3.White.copy(alpha = 0.6f), fontSize = 10.sp)
                                    BigText(g.average, 24, P3.Cyan)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            val bySubject = vm.data.finals.groupBy { it.subject }
            if (bySubject.isEmpty()) EmptyState(vm, "Итоговых оценок нет")
            else LazyColumn(Modifier.weight(1f), verticalArrangement = SpacedList) {
                items(bySubject.entries.toList()) { (subject, list) ->
                    SlantCard(Modifier.fillMaxWidth()) {
                        BigText(subject, 17)
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            list.forEach { f ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(f.period, color = P3.White.copy(alpha = 0.6f), fontSize = 10.sp)
                                    BigText(f.value, 24, markColor(f.value))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ───────────────────────── Путь медали ─────────────────────────

private data class MedalRow(val subject: String, val avg: Double, val n: Int, val need: Int)

@Composable
fun MedalScreen(vm: AppViewModel, onBack: () -> Unit) {
    val first = LocalDate.now().monthValue >= 7
    val target = 4.6
    val rows = vm.data.grades.mapNotNull { g ->
        val nums = g.marks.mapNotNull { m -> m.toIntOrNull()?.takeIf { it in 2..5 } }
        if (nums.isEmpty()) null else {
            val n = nums.size
            val sum = nums.sum()
            val avg = sum.toDouble() / n
            val need = if (avg >= target - 1e-9) 0 else Math.ceil((target * n - sum) / (5 - target) - 1e-9).toInt()
            MedalRow(g.subject, avg, n, need)
        }
    }.sortedWith(compareByDescending<MedalRow> { it.need }.thenBy { it.subject })
    ScreenFrame("Путь медали", onBack) {
        SlantCard(Modifier.fillMaxWidth()) {
            BigText(if (first) "1 ПОЛУГОДИЕ" else "2 ПОЛУГОДИЕ", 16, P3.Cyan)
            Text(
                "Цель: средний балл 4.60 и выше. Показано, сколько пятёрок подряд нужно получить.",
                color = P3.White.copy(alpha = 0.8f), fontSize = 12.sp,
            )
        }
        Spacer(Modifier.height(10.dp))
        if (rows.isEmpty()) EmptyState(vm, "Оценок в полугодии пока нет")
        else LazyColumn(Modifier.weight(1f), verticalArrangement = SpacedList) {
            itemsIndexed(rows) { i, r ->
                SlantCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            BigText(r.subject, 16)
                            Text(
                                "средний " + String.format(Locale.US, "%.2f", r.avg) + "  ·  оценок " + r.n,
                                color = P3.White.copy(alpha = 0.75f), fontSize = 12.sp,
                            )
                        }
                        if (r.need == 0) {
                            Column(horizontalAlignment = Alignment.End) {
                                BigText("✓", 28, P3.Cyan)
                                Text("цель взята", color = P3.White.copy(alpha = 0.6f), fontSize = 10.sp)
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.End) {
                                BigText("×" + r.need, 28, P3.Cyan)
                                Text("пятёрок", color = P3.White.copy(alpha = 0.6f), fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ───────────────────────── Домашние задания ─────────────────────────

@Composable
fun HomeworkScreen(vm: AppViewModel, onBack: () -> Unit) {
    val base = remember { baseMonday() }
    val monday = base.plusWeeks(vm.weekOffset.toLong())
    val date = monday.plusDays((vm.selectedDay - 1).toLong())
    val iso = date.toString()
    val dots = (1..6).filter { d ->
        val di = monday.plusDays((d - 1).toLong()).toString()
        vm.data.homework.any { it.dueIso == di }
    }.toSet()
    // предметы дня по расписанию + то, что задано, но в расписании не нашлось
    val hwDay = vm.data.homework.filter { it.dueIso == iso }
    val subjects = (vm.data.lessons.filter { lessonOn(it, date) }.sortedBy { it.number }.map { it.subject } +
        hwDay.map { it.subject }).distinct()
    ScreenFrame("Домашка", onBack) {
        WeekBar(monday, vm.selectedDay, vm.weekOffset, dots, { vm.selectedDay = it }, { vm.weekOffset = it })
        Spacer(Modifier.height(8.dp))
        DateHeader(date, !vm.atToday) { vm.goToday() }
        Spacer(Modifier.height(6.dp))
        if (subjects.isEmpty()) EmptyState(vm, "На этот день ничего не задано")
        else LazyColumn(Modifier.weight(1f), verticalArrangement = SpacedList) {
            itemsIndexed(subjects, key = { _, s -> "$iso-$s" }) { _, subject ->
                val items = hwDay.filter { it.subject == subject }
                SlantCard(Modifier.fillMaxWidth()) {
                    BigText(subject, 16)
                    if (items.isEmpty()) {
                        Text("—", color = P3.White.copy(alpha = 0.5f), fontSize = 16.sp)
                    } else items.forEach { h ->
                        Text(
                            linkified(h.text.ifBlank { "—" }),
                            color = P3.White, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

// ───────────────────────── Аккаунт ─────────────────────────

@Composable
fun AccountScreen(vm: AppViewModel, onBack: () -> Unit, onLogin: () -> Unit) {
    ScreenFrame("Аккаунт", onBack) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SlantCard(Modifier.fillMaxWidth()) {
                BigText(if (vm.demo) "РЕЖИМ: ДЕМО" else "РЕЖИМ: de.edu.orb.ru", 18, P3.Cyan)
                Spacer(Modifier.height(4.dp))
                Text(vm.status.ifBlank { " " }, color = P3.White, fontSize = 13.sp)
                if (!vm.demo) {
                    Spacer(Modifier.height(4.dp))
                    Text(vm.sessionInfo, color = P3.White.copy(alpha = 0.65f), fontSize = 11.sp)
                }
            }
            SlantButton("ВОЙТИ ЧЕРЕЗ ГОСУСЛУГИ", Modifier.fillMaxWidth(), onClick = onLogin)
            SlantButton("ОБНОВИТЬ ДАННЫЕ", Modifier.fillMaxWidth(), filled = false) { vm.refresh() }
            SlantButton("ТЕСТ УВЕДОМЛЕНИЯ", Modifier.fillMaxWidth(), filled = false) { vm.testNotification() }
            SlantButton(
                if (vm.demo) "ВЫЙТИ ИЗ ДЕМО (реальные данные)" else "ВКЛЮЧИТЬ ДЕМО",
                Modifier.fillMaxWidth(), filled = false,
            ) { vm.switchDemo(!vm.demo) }
            SlantButton("ВЫЙТИ ИЗ АККАУНТА", Modifier.fillMaxWidth(), filled = false) { vm.logout() }
            if (vm.loading) BigText("ЗАГРУЗКА…", 14, P3.Cyan)
            SlantCard(Modifier.fillMaxWidth()) {
                BigText("АВТОР", 14, P3.Cyan)
                Spacer(Modifier.height(2.dp))
                Text(authorLink(), color = P3.White, fontSize = 15.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "О багах и пожеланиях пишите туда же.",
                    color = P3.White.copy(alpha = 0.75f), fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun EmptyState(vm: AppViewModel, fallback: String) {
    SlantCard(Modifier.fillMaxWidth()) {
        BigText(fallback.uppercase(), 15, P3.Cyan)
        if (vm.status.isNotBlank()) Text(vm.status, color = P3.White, fontSize = 12.sp)
    }
}
