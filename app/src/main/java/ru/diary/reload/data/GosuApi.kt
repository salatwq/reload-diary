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
      ['student_marks','student_homeworks_materials','student_events'].forEach(function(t){
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
        val events = mutableListOf<JSONObject>()
        for (i in 0 until arr.length()) {
            val e = arr.getJSONObject(i)
            val data = e.optJSONArray("data") ?: continue
            val list = (0 until data.length()).map { data.getJSONObject(it) }
            when (e.optString("obj_type")) {
                "student_marks" -> marks += list
                "student_homeworks_materials" -> homework += list
                "student_events" -> events += list
            }
        }
        return DiaryData(parseEvents(events), parseMarks(marks), parseHomework(homework))
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

    private fun parseHomework(list: List<JSONObject>): List<Homework> =
        list.distinctBy { it.optString("id") }
            .filter { it.optString("subject_name").isNotBlank() }
            .sortedByDescending { it.optString("plan_ready_date") }
            .take(40)
            .map {
                val d = it.optString("plan_ready_date")
                val due = if (d.length >= 10) d.substring(8, 10) + "." + d.substring(5, 7) else ""
                Homework(it.optString("subject_name"), due, it.optString("description").trim())
            }

    /** Best-effort: формат событий/расписания пока не подтверждён реальными данными. */
    private fun parseEvents(list: List<JSONObject>): List<Lesson> {
        val monday = LocalDate.now().with(DayOfWeek.MONDAY)
        val sunday = monday.plusDays(6)
        val items = list.mapNotNull { e ->
            val start = e.optString("start_datetime")
            val subject = e.optString("subject_name")
            if (start.length < 16 || subject.isBlank()) return@mapNotNull null
            val dt = runCatching { LocalDateTime.parse(start.take(16).replace(' ', 'T')) }.getOrNull() ?: return@mapNotNull null
            if (dt.toLocalDate() < monday || dt.toLocalDate() > sunday) return@mapNotNull null
            val end = e.optString("end_datetime").let { if (it.length >= 16) it.substring(11, 16) else "" }
            Triple(dt, subject, listOf(end, e.optString("room_name"), e.optString("teacher_name")))
        }
        return items.groupBy { it.first.dayOfWeek.value }.flatMap { (day, es) ->
            es.sortedBy { it.first }.mapIndexed { i, (dt, subject, x) ->
                val t = dt.toLocalTime().toString().take(5)
                Lesson(day.coerceAtMost(6), i + 1, if (x[0].isNotBlank()) "$t–${x[0]}" else t, subject, x[1], x[2])
            }
        }
    }
}
