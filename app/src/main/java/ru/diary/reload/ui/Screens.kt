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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

private val DAYS = listOf("ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ")

// ───────────────────────── Главное меню ─────────────────────────

@Composable
fun MenuScreen(vm: AppViewModel, onOpen: (Screen) -> Unit) {
    val items = Screen.entries.filter { it != Screen.MENU && it != Screen.BROWSER }
    var sel by rememberSaveable { mutableIntStateOf(0) }
    val now = LocalDate.now()
    val locale = Locale("ru")
    val todayLessons = vm.data.lessons.count { it.day == now.dayOfWeek.value }

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
                    MenuItem(s.title, i == sel) { if (i == sel) onOpen(s) else sel = i }
                }
            }
            Spacer(Modifier.weight(1f))
            // подсказка внизу, как в игре
            Box(
                Modifier.fillMaxWidth().clip(SlantShape(16.dp)).background(P3.Navy)
                    .padding(horizontal = 28.dp, vertical = 12.dp),
            ) {
                Text(
                    items[sel].caption + "  ·  нажмите ещё раз, чтобы открыть",
                    color = P3.Cyan, fontSize = 13.sp, fontStyle = FontStyle.Italic,
                )
            }
        }
    }
}

@Composable
private fun MenuItem(label: String, selected: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (selected) 1.08f else 1f, label = "scale")
    Box(
        Modifier.fillMaxWidth().padding(vertical = 5.dp)
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
    var day by rememberSaveable { mutableIntStateOf(LocalDate.now().dayOfWeek.value.let { if (it > 6) 1 else it }) }
    ScreenFrame("Расписание", onBack) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DAYS.forEachIndexed { i, d ->
                val on = day == i + 1
                Box(
                    Modifier.weight(1f).clip(SlantShape(8.dp))
                        .background(if (on) P3.Cyan else P3.Navy)
                        .clickable { day = i + 1 }.padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) { BigText(d, 15, if (on) P3.Navy else P3.White) }
            }
        }
        Spacer(Modifier.height(12.dp))
        val list = vm.data.lessons.filter { it.day == day }.sortedBy { it.number }
        if (list.isEmpty()) EmptyState(vm, "В этот день уроков нет")
        else LazyColumn(Modifier.weight(1f), verticalArrangement = SpacedList) {
            items(list) { l ->
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
                items(vm.data.grades) { g ->
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

// ───────────────────────── Домашние задания ─────────────────────────

@Composable
fun HomeworkScreen(vm: AppViewModel, onBack: () -> Unit) {
    ScreenFrame("Домашка", onBack) {
        if (vm.data.homework.isEmpty()) EmptyState(vm, "Заданий нет")
        else LazyColumn(Modifier.weight(1f), verticalArrangement = SpacedList) {
            items(vm.data.homework) { h ->
                SlantCard(Modifier.fillMaxWidth()) {
                    Row {
                        BigText(h.subject, 17, P3.Cyan)
                        Spacer(Modifier.weight(1f))
                        if (h.due.isNotBlank()) BigText("до ${h.due}", 14)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(h.text, color = P3.White, fontSize = 14.sp)
                }
            }
        }
    }
}

// ───────────────────────── Аккаунт ─────────────────────────

@Composable
fun AccountScreen(vm: AppViewModel, onBack: () -> Unit, onLogin: () -> Unit) {
    ScreenFrame("Аккаунт", onBack) {
        SlantCard(Modifier.fillMaxWidth()) {
            BigText(if (vm.demo) "РЕЖИМ: ДЕМО" else "РЕЖИМ: de.edu.orb.ru", 18, P3.Cyan)
            Spacer(Modifier.height(4.dp))
            Text(vm.status.ifBlank { " " }, color = P3.White, fontSize = 13.sp)
        }
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SlantButton("ВОЙТИ ЧЕРЕЗ ГОСУСЛУГИ", Modifier.fillMaxWidth(), onClick = onLogin)
            SlantButton("ОБНОВИТЬ ДАННЫЕ", Modifier.fillMaxWidth(), filled = false) { vm.refresh() }
            SlantButton(
                if (vm.demo) "ВЫЙТИ ИЗ ДЕМО (реальные данные)" else "ВКЛЮЧИТЬ ДЕМО",
                Modifier.fillMaxWidth(), filled = false,
            ) { vm.switchDemo(!vm.demo) }
            SlantButton("ВЫЙТИ ИЗ АККАУНТА", Modifier.fillMaxWidth(), filled = false) { vm.logout() }
        }
        if (vm.loading) {
            Spacer(Modifier.height(10.dp))
            BigText("ЗАГРУЗКА…", 14, P3.Cyan)
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
