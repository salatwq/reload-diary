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
import ru.diary.reload.data.Scraper

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("diary", 0)

    var demo by mutableStateOf(prefs.getBoolean("demo", true)); private set
    var data by mutableStateOf(if (demo) Demo.data else DiaryData(emptyList(), emptyList(), emptyList())); private set
    var loading by mutableStateOf(false); private set
    var status by mutableStateOf(if (demo) "Демо-данные" else ""); private set

    init { if (!demo) refresh() }

    fun url(key: String): String? = prefs.getString("url_$key", null)
    fun saveUrl(key: String, url: String) = prefs.edit().putString("url_$key", url).apply()

    fun setDemo(value: Boolean) {
        demo = value
        prefs.edit().putBoolean("demo", value).apply()
        refresh()
    }

    /** Вызывается после того, как пользователь вошёл через Госуслуги во встроенном браузере. */
    fun markLoggedIn() {
        CookieManager.getInstance().flush()
        setDemo(false)
    }

    fun logout() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        setDemo(true)
    }

    fun refresh() {
        if (demo) {
            data = Demo.data
            status = "Демо-данные"
            return
        }
        if (url("schedule") == null && url("grades") == null && url("homework") == null) {
            status = "Страницы не указаны: Аккаунт → Войти → откройте нужную страницу → «Это расписание / оценки / ДЗ»."
            return
        }
        viewModelScope.launch {
            loading = true
            status = "Загрузка…"
            try {
                val ctx = getApplication<Application>()
                withTimeout(60_000) {
                    val lessons = url("schedule")?.let { Scraper.parseSchedule(Scraper.fetchTables(ctx, it)) }.orEmpty()
                    val grades = url("grades")?.let { Scraper.parseGrades(Scraper.fetchTables(ctx, it)) }.orEmpty()
                    val hw = url("homework")?.let { Scraper.parseHomework(Scraper.fetchTables(ctx, it)) }.orEmpty()
                    data = DiaryData(lessons, grades, hw)
                }
                status = if (data.isEmpty) {
                    "Страницы загружены, но таблицы не распознаны (возможно, сессия истекла — войдите снова)."
                } else "Обновлено"
            } catch (e: Exception) {
                status = "Ошибка: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                loading = false
            }
        }
    }
}
