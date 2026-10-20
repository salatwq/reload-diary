package ru.diary.reload

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import ru.diary.reload.ui.AccountScreen
import ru.diary.reload.ui.BrowserScreen
import ru.diary.reload.ui.GradesScreen
import ru.diary.reload.ui.HomeworkScreen
import ru.diary.reload.ui.MenuScreen
import ru.diary.reload.ui.ScheduleScreen

enum class Screen(val title: String, val caption: String) {
    MENU("", ""),
    SCHEDULE("Расписание", "Уроки на неделю"),
    GRADES("Оценки", "Отметки и средний балл"),
    HOMEWORK("Домашка", "Что задали"),
    ACCOUNT("Аккаунт", "Вход через Госуслуги, данные"),
    BROWSER("", ""),
}

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { App(vm) }
    }
}

@Composable
fun App(vm: AppViewModel) {
    var screen by rememberSaveable { mutableStateOf(Screen.MENU) }
    val back = { screen = Screen.MENU }
    BackHandler(enabled = screen != Screen.MENU && screen != Screen.BROWSER) { back() }

    when (screen) {
        Screen.MENU -> MenuScreen(vm) { screen = it }
        Screen.SCHEDULE -> ScheduleScreen(vm, back)
        Screen.GRADES -> GradesScreen(vm, back)
        Screen.HOMEWORK -> HomeworkScreen(vm, back)
        Screen.ACCOUNT -> AccountScreen(vm, back) { screen = Screen.BROWSER }
        Screen.BROWSER -> BrowserScreen(vm) { screen = Screen.ACCOUNT }
    }
}
