package ru.diary.reload

import android.app.Application
import android.webkit.CookieManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import ru.diary.reload.data.Builder
import ru.diary.reload.data.Demo
import ru.diary.reload.data.DiaryCache
import ru.diary.reload.data.DiaryData
import ru.diary.reload.data.Session
import ru.diary.reload.data.Sync
import ru.diary.reload.work.MarksWorker
import ru.diary.reload.work.Notifier
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

private val EMPTY = DiaryData(emptyList(), emptyList(), emptyList())

private fun todayDay() = LocalDate.now().dayOfWeek.value.let { if (it > 6) 1 else it }

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("diary", 0)

    var demo by mutableStateOf(prefs.getBoolean("demo", true)); private set
    var data by mutableStateOf(if (demo) Demo.data else EMPTY); private set
    var loading by mutableStateOf(false); private set
    var status by mutableStateOf(if (demo) "Демо-данные" else ""); private set
    var sessionInfo by mutableStateOf(Session.info(app)); private set

    /** Выбранная неделя (0 = текущая, от −5 до +5) и день (1 = ПН … 6 = СБ) — общие для расписания и домашки. */
    var weekOffset by mutableIntStateOf(0)
    var selectedDay by mutableIntStateOf(todayDay())

    val atToday: Boolean get() = weekOffset == 0 && selectedDay == todayDay()

    fun goToday() {
        weekOffset = 0
        selectedDay = todayDay()
    }

    init {
        if (!demo) loadCachedThenRefresh()
    }

    private fun loadCachedThenRefresh() {
        val app = getApplication<Application>()
        Session.restore(app)
        MarksWorker.schedule(app)
        viewModelScope.launch {
            val cached = withContext(Dispatchers.IO) { Builder.build(DiaryCache.load(app)) }
            if (!cached.isEmpty) {
                data = cached
                status = "Показаны сохранённые данные, обновляю…"
            }
            refresh()
        }
    }

    fun switchDemo(value: Boolean) {
        demo = value
        prefs.edit().putBoolean("demo", value).apply()
        if (value) {
            MarksWorker.cancel(getApplication<Application>())
            data = Demo.data
            status = "Демо-данные"
        } else {
            data = EMPTY
            status = ""
            loadCachedThenRefresh()
        }
    }

    /** Вызывается после входа через Госуслуги во встроенном браузере. */
    fun markLoggedIn() {
        val app = getApplication<Application>()
        CookieManager.getInstance().flush()
        Session.save(app)
        sessionInfo = Session.info(app)
        switchDemo(false)
    }

    fun logout() {
        val app = getApplication<Application>()
        prefs.edit().remove("known_marks").remove("last_full").apply()
        Session.clear(app)
        DiaryCache.clear(app)
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        sessionInfo = Session.info(app)
        switchDemo(true)
    }

    fun refresh() {
        if (demo) {
            data = Demo.data
            status = "Демо-данные"
            return
        }
        if (loading) return
        val app = getApplication<Application>()
        viewModelScope.launch {
            loading = true
            status = "Обновление…"
            try {
                val r = withTimeout(80_000) { Sync.run(app) }
                if (!r.data.isEmpty || data.isEmpty) data = r.data
                val known = (prefs.getStringSet("known_marks", null) ?: emptySet()).toMutableSet()
                known += data.marks.map { it.id }
                if (data.marks.isNotEmpty()) prefs.edit().putStringSet("known_marks", known).apply()
                val time = SimpleDateFormat("HH:mm", Locale("ru")).format(Date())
                val keep = if (!data.isEmpty) " Показаны сохранённые данные." else ""
                status = when {
                    r.error != null -> "Не удалось обновить: ${r.error}.$keep"
                    r.failed > 0 -> "Обновлено в $time, но часть данных не пришла (${r.failed} из ${r.total}). Остальное — из сохранённого."
                    else -> "Обновлено в $time · уроков ${data.lessons.size}, оценок ${data.marks.size}"
                }
            } catch (e: TimeoutCancellationException) {
                status = "Нет ответа от Госуслуг за 80 с. Проверьте интернет.${if (!data.isEmpty) " Показаны сохранённые данные." else ""}"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = "Ошибка: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                sessionInfo = Session.info(app)
                loading = false
            }
        }
    }

    fun testNotification() {
        Notifier.show(getApplication<Application>(), 1, "Новая оценка: 5", "Алгебра · Контрольная работа · тест")
    }
}
