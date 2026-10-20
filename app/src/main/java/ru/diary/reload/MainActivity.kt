package ru.diary.reload

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.diary.reload.ui.Splash
import ru.diary.reload.ui.WipeOverlay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import ru.diary.reload.ui.AccountScreen
import ru.diary.reload.ui.BrowserScreen
import ru.diary.reload.ui.GradesScreen
import ru.diary.reload.ui.HomeworkScreen
import ru.diary.reload.ui.MedalScreen
import ru.diary.reload.ui.MenuScreen
import ru.diary.reload.ui.ScheduleScreen

enum class Screen(val title: String, val caption: String) {
    SPLASH("", ""),
    MENU("", ""),
    SCHEDULE("Расписание", "Уроки на неделю"),
    GRADES("Оценки", "Отметки и средний балл"),
    MEDAL("Путь медали", "Сколько пятёрок нужно до 4.6"),
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
    var screen by rememberSaveable { mutableStateOf(Screen.SPLASH) }
    val back = { screen = Screen.MENU }
    BackHandler(enabled = screen != Screen.MENU && screen != Screen.BROWSER && screen != Screen.SPLASH) { back() }

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                (slideInHorizontally(tween(380)) { it / 5 } + fadeIn(tween(380))) togetherWith
                    (slideOutHorizontally(tween(260)) { -it / 8 } + fadeOut(tween(260)))
            },
            label = "screen",
        ) { s ->
            when (s) {
                Screen.SPLASH -> Splash { screen = Screen.MENU }
                Screen.MENU -> MenuScreen(vm) { screen = it }
                Screen.SCHEDULE -> ScheduleScreen(vm, back)
                Screen.GRADES -> GradesScreen(vm, back)
                Screen.MEDAL -> MedalScreen(vm, back)
                Screen.HOMEWORK -> HomeworkScreen(vm, back)
                Screen.ACCOUNT -> AccountScreen(vm, back) { screen = Screen.BROWSER }
                Screen.BROWSER -> BrowserScreen(vm) { screen = Screen.ACCOUNT }
            }
        }
        if (screen != Screen.SPLASH) WipeOverlay(screen)
    }
}
