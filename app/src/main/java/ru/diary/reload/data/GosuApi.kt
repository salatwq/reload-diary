package ru.diary.reload.data

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.IsoFields
import java.util.Locale
import kotlin.coroutines.resume

/**
 * «Моя школа» на Госуслугах. Запросы выполняются JS-ом внутри скрытого WebView на origin www.gosuslugi.ru,
 * поэтому подставляются cookies текущего входа и не нужно ничего знать о токенах.
 */
object GosuApi {
    private val main = Handler(Looper.getMainLooper())

    private const val JS = """
(async function(){
 try {
  var h = {'Accept':'application/json'};
  var r1 = await fetch('/api/myschool/v2/auth/student?role=student', {credentials:'include', headers:h});
  if (!r1.ok) { Bridge.done(JSON.stringify({ok:false, err:'Вход не выполнен (код ' + r1.status + ')'})); return; }
  var st = await r1.json();
  if (!st.length) { Bridge.done(JSON.stringify({ok:false, err:'Нет доступа к дневнику ученика'})); return; }
  var sid = st[0].student_id;
  var ALL = __ALL__, NEAR = __NEAR__;
  var all = [], code = 0, tasks = [];
  function objs(ids, types) {
    var b = [];
    ids.forEach(function(i){ types.forEach(function(t){ b.push({interval_id: i, obj_type: t, student_id: sid}); }); });
    return b;
  }
  async function post(body) {
    // 202 = «данные готовятся» — как и сама «Моя школа», повторяем запрос
    for (var n = 0; n < 8; n++) {
      try {
        var r = await fetch('/api/myschool/v1/datamart', {method:'POST', credentials:'include',
          headers:{'Content-Type':'application/json','Accept':'application/json'}, body: JSON.stringify(body)});
        if (r.status === 200) { all = all.concat(await r.json()); return; }
        if (r.status !== 202) { code = r.status; return; }
        code = 202;
      } catch (e) {}
      await new Promise(function(res){ setTimeout(res, 2500); });
    }
  }
  for (var k = 0; k < ALL.length; k += 5) tasks.push(post(objs(ALL.slice(k, k + 5), ['student_marks'])));
  NEAR.forEach(function(i){ tasks.push(post(objs([i], ['student_lessons','student_homeworks_materials']))); });
  await Promise.all(tasks);
  if (!all.length) { Bridge.done(JSON.stringify({ok:false, err:'Данные не получены (код ' + code + ')'})); return; }
  Bridge.done(JSON.stringify({ok:true, data: all}));
 } catch (e) { Bridge.done(JSON.stringify({ok:false, err:String(e)})); }
})();
"""

    /** Интервалы в «Моей школе» = номер недели ISO + учебный год (432026 = 43-я неделя, учебный год 2026). */
    private fun buildJs(): String {
        val today = LocalDate.now()
        val acYear = if (today.monthValue >= 8) today.year else today.year - 1
        fun id(d: LocalDate, y: Int) = "${d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}$y"
        val all = linkedSetOf<String>()
        var d = LocalDate.of(acYear, 9, 1).with(DayOfWeek.MONDAY)
        while (!d.isAfter(today.plusWeeks(1))) {
            all += id(d, acYear)
            if (d.year > acYear) all += id(d, d.year)   // на случай, если суффикс — календарный год
            d = d.plusWeeks(1)
        }
        val near = linkedSetOf(id(today, acYear), id(today.plusWeeks(1), acYear))
        fun arr(c: Collection<String>) = c.joinToString(",", "[", "]") { "'$it'" }
        return JS.replace("__ALL__", arr(all)).replace("__NEAR__", arr(near))
    }

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
            main.post { wv.destroy() }
            if (cont.isActive) cont.resumeWith(r)
        }
        wv.addJavascriptInterface(object {
            @JavascriptInterface
            fun done(s: String) { finish(runCatching { parse(s) }) }
        }, "Bridge")
        var started = false
        wv.webViewClient = object : WebViewClient() {
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    finish(Result.failure(Exception("Не удалось открыть Госуслуги: ${error.description}")))
                }
            }
            override fun onPageFinished(view: WebView, url: String?) {
                if (started) return
                val host = Uri.parse(url ?: "").host.orEmpty()
                if (host == "www.gosuslugi.ru") {
                    started = true
                    main.postDelayed({ view.evaluateJavascript(buildJs(), null) }, 1500)
                } else if (host.isNotEmpty() && host.endsWith("gosuslugi.ru")) {
                    finish(Result.failure(Exception("Сессия закончилась — войдите заново (Аккаунт → Войти)")))
                }
            }
        }
        cont.invokeOnCancellation { main.post { wv.destroy() } }
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

    /** Начало текущего полугодия: до 31.12 — 1-е (с 1 сентября), с 1 января — 2-е. */
    private fun halfYearStart(): String {
        val t = LocalDate.now()
        return (if (t.monthValue >= 9) LocalDate.of(t.year, 9, 1) else LocalDate.of(t.year, 1, 1)).toString()
    }

    private fun parseMarks(list: List<JSONObject>): List<GradeRow> {
        val start = halfYearStart()
        return parseMarksIn(list.filter { it.str("mark_date").let { d -> d.isEmpty() || d >= start } })
    }

    private fun parseMarksIn(list: List<JSONObject>): List<GradeRow> =
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

    private fun parseHomework(list: List<JSONObject>): List<Homework> =
        list.distinctBy { it.optString("id") }
            .filter { it.str("subject_name").isNotBlank() && it.str("plan_ready_date").length >= 10 }
            .map {
                val d = it.str("plan_ready_date").take(10)
                Homework(it.str("subject_name"), d.substring(8, 10) + "." + d.substring(5, 7), it.str("description").trim(), d)
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
