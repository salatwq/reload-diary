package ru.diary.reload.data

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale
import kotlin.coroutines.resume

/**
 * «Моя школа» на Госуслугах. Запросы выполняются JS-ом внутри скрытого WebView на origin www.gosuslugi.ru,
 * поэтому подставляются cookies текущего входа и не нужно ничего знать о токенах.
 */
object GosuApi {
    private const val JS = """
(async function(){
 try {
  var h = {'Accept':'application/json'};
  var r1 = await fetch('/api/myschool/v2/auth/student?role=student', {credentials:'include', headers:h});
  if (!r1.ok) { Bridge.done(JSON.stringify({ok:false, err:'Вход не выполнен (код ' + r1.status + ')'})); return; }
  var st = await r1.json();
  if (!st.length) { Bridge.done(JSON.stringify({ok:false, err:'Нет доступа к дневнику ученика'})); return; }
  var sid = st[0].student_id;
  function mk(from, to) {
    var body = [];
    for (var i = from; i <= to; i++) {
      ['student_marks','student_homeworks_materials','student_lessons'].forEach(function(t){
        body.push({interval_id: String(i) + '2026', obj_type: t, student_id: sid});
      });
    }
    return body;
  }
  async function req(body) {
    return fetch('/api/myschool/v1/datamart', {method:'POST', credentials:'include',
      headers:{'Content-Type':'application/json','Accept':'application/json'}, body: JSON.stringify(body)});
  }
  var r2 = await req(mk(41, 50));
  if (!r2.ok) r2 = await req(mk(42, 44));
  if (!r2.ok) { Bridge.done(JSON.stringify({ok:false, err:'Ошибка данных (код ' + r2.status + ')'})); return; }
  Bridge.done(JSON.stringify({ok:true, data: await r2.json()}));
 } catch (e) { Bridge.done(JSON.stringify({ok:false, err:String(e)})); }
})();
"""

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    suspend fun load(ctx: Context): DiaryData = suspendCancellableCoroutine { cont ->
        val wv = WebView(ctx)
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
        var finished = false
        fun finish(r: Result<DiaryData>) {
            if (finished) return
            finished = true
            wv.post { wv.destroy() }
            if (cont.isActive) cont.resumeWith(r)
        }
        wv.addJavascriptInterface(object {
            @JavascriptInterface
            fun done(s: String) { finish(runCatching { parse(s) }) }
        }, "Bridge")
        var started = false
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                if (started) return
                val host = Uri.parse(url ?: "").host.orEmpty()
                if (host == "www.gosuslugi.ru") {
                    started = true
                    view.postDelayed({ view.evaluateJavascript(JS, null) }, 1500)
                } else if (host.isNotEmpty() && host.endsWith("gosuslugi.ru")) {
                    finish(Result.failure(Exception("Сессия закончилась — войдите заново (Аккаунт → Войти)")))
                }
            }
        }
        cont.invokeOnCancellation { wv.post { wv.destroy() } }
        wv.loadUrl("https://www.gosuslugi.ru/school")
    }

    private fun parse(s: String): DiaryData {
        val o = JSONObject(s)
        if (!o.getBoolean("ok")) throw Exception(o.optString("err", "Ошибка"))
        val arr = o.getJSONArray("data")
        val marks = mutableListOf<JSONObject>()
        val homework = mutableListOf<JSONObject>()
        val lessons = mutableListOf<JSONObject>()
        for (i in 0 until arr.length()) {
            val e = arr.getJSONObject(i)
            val data = e.optJSONArray("data") ?: continue
            val list = (0 until data.length()).map { data.getJSONObject(it) }
            when (e.optString("obj_type")) {
                "student_marks" -> marks += list
                "student_homeworks_materials" -> homework += list
                "student_lessons" -> lessons += list
            }
        }
        val valid = marks.distinctBy { it.optString("id") }.filter {
            it.optString("subject_name").isNotBlank() &&
                it.optString("mark_value1").isNotBlank() && it.optString("mark_value1") != "null"
        }
        val (fin, cur) = valid.partition { isFinal(it) }
        return DiaryData(parseLessons(lessons), parseMarks(cur), parseHomework(homework), parseFinals(fin))
    }

    private val FINAL = Regex("(?i)итог|четверт|полугод|годов|final|period|quarter|semester|annual")

    /** Итоговые определяем по типу работы; точный признак уточним по реальным данным. */
    private fun isFinal(o: JSONObject) =
        listOf("work_type_code", "work_type_description", "work_kind", "work_name")
            .any { FINAL.containsMatchIn(o.optString(it)) }

    private fun parseFinals(list: List<JSONObject>): List<FinalMark> =
        list.sortedWith(compareBy({ it.optString("subject_name") }, { it.optString("mark_date") })).map {
            val label = listOf("work_type_description", "work_name", "work_kind")
                .map { k -> it.optString(k) }.firstOrNull { v -> v.isNotBlank() && v != "null" }.orEmpty()
            FinalMark(it.optString("subject_name"), label, it.optString("mark_value1"))
        }

    private fun parseMarks(list: List<JSONObject>): List<GradeRow> =
        list.distinctBy { it.optString("id") }
            .filter { it.optString("subject_name").isNotBlank() && it.optString("mark_value1").isNotBlank() && it.optString("mark_value1") != "null" }
            .groupBy { it.optString("subject_name") }
            .map { (subject, ms) ->
                val values = ms.sortedBy { it.optString("mark_date") }.map { it.optString("mark_value1") }
                val nums = values.mapNotNull { v -> v.toIntOrNull()?.takeIf { it in 2..5 } }
                val avg = if (nums.isEmpty()) "" else String.format(Locale.US, "%.2f", nums.average())
                GradeRow(subject, values, avg)
            }
            .sortedBy { it.subject }

    private fun JSONObject.str(k: String): String = optString(k).takeIf { it.isNotBlank() && it != "null" } ?: ""

    private fun parseHomework(list: List<JSONObject>): List<Homework> {
        val today = LocalDate.now().toString()
        val all = list.distinctBy { it.optString("id") }.filter { it.str("subject_name").isNotBlank() }
        val upcoming = all.filter { it.str("plan_ready_date") >= today }.sortedBy { it.str("plan_ready_date") }
        val past = all.filter { it.str("plan_ready_date") < today }.sortedByDescending { it.str("plan_ready_date") }.take(10)
        return (upcoming + past).map {
            val d = it.str("plan_ready_date")
            val due = if (d.length >= 10) d.substring(8, 10) + "." + d.substring(5, 7) else ""
            Homework(it.str("subject_name"), due, it.str("description").trim())
        }
    }

    /** Уроки текущей недели (в воскресенье — следующей) из student_lessons. */
    private fun parseLessons(list: List<JSONObject>): List<Lesson> {
        val today = LocalDate.now()
        val monday = (if (today.dayOfWeek == DayOfWeek.SUNDAY) today.plusDays(1) else today).with(DayOfWeek.MONDAY)
        val sunday = monday.plusDays(6)
        val items = list
            .distinctBy { it.str("lesson_id").ifEmpty { it.str("id") } }
            .filter { it.optBoolean("active", true) && it.str("subject_name").isNotBlank() }
            .mapNotNull { e ->
                val start = e.str("start_datetime")
                if (start.length < 16) return@mapNotNull null
                val dt = runCatching { LocalDateTime.parse(start.take(16).replace(' ', 'T')) }.getOrNull()
                    ?: return@mapNotNull null
                if (dt.toLocalDate() < monday || dt.toLocalDate() > sunday || dt.dayOfWeek == DayOfWeek.SUNDAY) return@mapNotNull null
                Pair(dt, e)
            }
        return items.groupBy { it.first.dayOfWeek.value }.flatMap { (day, es) ->
            es.sortedBy { it.first }.mapIndexed { i, (dt, e) ->
                val end = e.str("end_datetime").let { if (it.length >= 16) it.substring(11, 16) else "" }
                val t = dt.toLocalTime().toString().take(5)
                val last = e.str("lastname")
                val teacher = listOf(last, e.str("firstname").take(1).let { if (it.isEmpty()) "" else "$it." },
                    e.str("patronymic").take(1).let { if (it.isEmpty()) "" else "$it." })
                    .filter { it.isNotEmpty() }.joinToString(" ")
                Lesson(day, i + 1, if (end.isNotEmpty()) "$t–$end" else t, e.str("subject_name"), e.str("room"), teacher)
            }
        }
    }
}
