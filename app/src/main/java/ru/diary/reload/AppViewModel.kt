package ru.diary.reload

import android.app.Application
import android.webkit.CookieManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import ru.diary.reload.data.Demo
import ru.diary.reload.data.DiaryData
import ru.diary.reload.data.GosuApi

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("diary", 0)

    var demo by mutableStateOf(prefs.getBoolean("demo", true)); private set
    var data by mutableStateOf(if (demo) Demo.data else DiaryData(emptyList(), emptyList(), emptyList())); private set
    var loading by mutableStateOf(false); private set
    var status by mutableStateOf(if (demo) "Демо-данные" else ""); private set

    init { if (!demo) refresh() }

    fun url(key: String): String? = prefs.getString("url_$key", null)
    fun saveUrl(key: String, url: String) = prefs.edit().putString("url_$key", url).apply()

    fun switchDemo(value: Boolean) {
        demo = value
        prefs.edit().putBoolean("demo", value).apply()
        refresh()
    }

    /** Вызывается после входа через Госуслуги во встроенном браузере. */
    fun markLoggedIn() {
        CookieManager.getInstance().flush()
        switchDemo(false)
    }

    fun logout() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        switchDemo(true)
    }

    fun refresh() {
        if (demo) {
            data = Demo.data
            status = "Демо-данные"
            return
        }
        viewModelScope.launch {
            loading = true
            status = "Загрузка…"
            try {
                data = withTimeout(45_000) { GosuApi.load(getApplication<Application>()) }
                CookieManager.getInstance().flush()
                status = when {
                    data.isEmpty -> "Загружено, но данных нет."
                    data.lessons.isEmpty() -> "Обновлено. Расписание пока не подключено."
                    else -> "Обновлено: уроков ${data.lessons.size}, предметов с оценками ${data.grades.size}"
                }
            } catch (e: Exception) {
                status = if (e is kotlinx.coroutines.TimeoutCancellationException)
                    "Нет ответа от Госуслуг за 45 с. Проверьте интернет и войдите заново: Аккаунт → Войти → «Я ВОШЁЛ»."
                else "Ошибка: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                loading = false
            }
        }
    }
}
