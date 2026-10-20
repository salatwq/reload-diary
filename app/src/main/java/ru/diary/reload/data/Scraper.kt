package ru.diary.reload.data

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONTokener
import kotlin.coroutines.resume

typealias Tables = List<List<List<String>>>   // таблица → строка → ячейка

object SiteConfig {
    const val BASE = "https://de.edu.orb.ru"
}

/**
 * Загружает страницу в скрытом WebView (с теми же cookies, что и после входа через Госуслуги)
 * и вытаскивает из неё все <table>. Разбор — best-effort: структуру сайта подгоняем под реальные страницы.
 */
object Scraper {
    private const val EXTRACT_JS =
        "JSON.stringify(Array.from(document.querySelectorAll('table')).map(function(t){" +
            "return Array.from(t.rows).map(function(r){" +
            "return Array.from(r.cells).map(function(c){return (c.innerText||'').trim();});});}))"

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun fetchTables(context: Context, url: String): Tables =
        suspendCancellableCoroutine { cont ->
            val wv = WebView(context)
            wv.settings.javaScriptEnabled = true
            wv.settings.domStorageEnabled = true
            CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
            var done = false
            wv.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, u: String?) {
                    // даём SPA время дорисовать таблицы
                    view.postDelayed({
                        if (done) return@postDelayed
                        view.evaluateJavascript(EXTRACT_JS) { raw ->
                            if (!done) {
                                done = true
                                cont.resume(parseTables(raw))
                                view.destroy()
                            }
                        }
                    }, 2500)
                }
            }
            cont.invokeOnCancellation { wv.post { wv.destroy() } }
            wv.loadUrl(url)
        }

    private fun parseTables(raw: String?): Tables {
        if (raw == null) return emptyList()
        val s = JSONTokener(raw).nextValue() as? String ?: return emptyList()
        val arr = JSONArray(s)
        return List(arr.length()) { i ->
            val t = arr.getJSONArray(i)
            List(t.length()) { r ->
                val row = t.getJSONArray(r)
                List(row.length()) { c -> row.getString(c) }
            }
        }
    }

    private val TIME = Regex("""\d{1,2}[:.]\d{2}(\s*[–-]\s*\d{1,2}[:.]\d{2})?""")
    private val MARK = Regex("""^[2-5][+-]?$|^[нНбБ]$""")
    private val AVG = Regex("""^\d[.,]\d{1,2}$""")
    private val DATE = Regex("""\d{1,2}\.\d{2}(\.\d{2,4})?""")

    fun parseSchedule(t: Tables): List<Lesson> {
        val out = mutableListOf<Lesson>()
        val perTable = t.size in 2..7           // «одна таблица = один день»
        t.forEachIndexed { ti, rows ->
            val day = if (perTable) ti + 1 else 1
            rows.forEach { row ->
                val cells = row.filter { it.isNotBlank() }
                if (cells.size < 2) return@forEach
                val num = cells.first().trim().toIntOrNull()
                val time = cells.firstNotNullOfOrNull { TIME.find(it)?.value }.orEmpty()
                if (num == null && time.isEmpty()) return@forEach
                val rest = cells.filter { it.trim().toIntOrNull() == null && TIME.find(it)?.value != it.trim() }
                out += Lesson(
                    day, num ?: (out.count { it.day == day } + 1), time,
                    rest.getOrNull(0).orEmpty().lines().first(),
                    rest.getOrNull(1).orEmpty(), rest.getOrNull(2).orEmpty(),
                )
            }
        }
        return out
    }

    fun parseGrades(t: Tables): List<GradeRow> = t.flatten().mapNotNull { row ->
        val cells = row.filter { it.isNotBlank() }
        if (cells.size < 2) return@mapNotNull null
        val subject = cells.first().trim()
        if (MARK.matches(subject) || AVG.matches(subject)) return@mapNotNull null
        val marks = cells.drop(1).flatMap { it.split(Regex("[\\s,;]+")) }.filter { MARK.matches(it) }
        val avg = cells.drop(1).lastOrNull { AVG.matches(it.trim()) }.orEmpty()
        if (marks.isEmpty() && avg.isEmpty()) null else GradeRow(subject, marks, avg)
    }

    fun parseHomework(t: Tables): List<Homework> = t.flatten().mapNotNull { row ->
        val cells = row.filter { it.isNotBlank() }
        if (cells.size < 2) return@mapNotNull null
        val due = cells.firstNotNullOfOrNull { DATE.find(it)?.value }.orEmpty()
        val subject = cells.firstOrNull { !DATE.matches(it.trim()) }?.trim() ?: return@mapNotNull null
        val text = cells.maxByOrNull { it.length }.orEmpty().trim()
        if (text == subject) null else Homework(subject, due, text)
    }
}
