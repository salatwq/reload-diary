package ru.diary.reload.data

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

class FetchResult(
    val items: JSONArray,
    val failed: Int,
    val total: Int,
    val error: String?,
    val needLogin: Boolean,
)

/**
 * «Моя школа» на Госуслугах. Запросы выполняются JS-ом внутри скрытого WebView на origin www.gosuslugi.ru,
 * поэтому подставляются cookies текущего входа. Запросы идут напрямую с телефона, без посредников.
 */
object GosuApi {
    private val main = Handler(Looper.getMainLooper())

    private const val JS = """
(async function(){
 var DEADLINE = Date.now() + 52000;
 function done(o){ Bridge.done(JSON.stringify(o)); }
 function sleep(ms){ return new Promise(function(r){ setTimeout(r, ms); }); }
 try {
  var H = {'Accept':'application/json'};
  var st = null, code = 0;
  for (var a = 0; a < 3 && !st; a++) {
    try {
      var r1 = await fetch('/api/myschool/v2/auth/student?role=student', {credentials:'include', headers:H});
      code = r1.status;
      if (r1.ok) { st = await r1.json(); break; }
      if (code === 401 || code === 403) break;
    } catch (e) { code = -1; }
    await sleep(1500);
  }
  if (!st) {
    if (code === 401 || code === 403) done({ok:false, login:true, err:'Сессия закончилась — войдите заново (код ' + code + ')'});
    else done({ok:false, err:'Нет ответа от Госуслуг (код ' + code + ')'});
    return;
  }
  if (!st.length) { done({ok:false, err:'Нет доступа к дневнику ученика'}); return; }
  var sid = st[0].student_id;
  var JOBS = __JOBS__;
  var KEEP = {
    student_marks: ['id','subject_name','mark_value1','mark_date','work_type_code','work_type_description','work_kind','work_name'],
    student_lessons: ['id','lesson_id','subject_name','start_datetime','end_datetime','active','room','firstname','lastname','patronymic'],
    student_homeworks_materials: ['id','subject_name','description','plan_ready_date','issue_date']
  };
  function slim(list){
    return list.map(function(e){
      var keep = KEEP[e.obj_type];
      if (!keep || !Array.isArray(e.data)) return null;
      return {obj_type:e.obj_type, interval_id:e.interval_id, data: e.data.map(function(x){
        var o = {};
        keep.forEach(function(k){ if (x[k] !== undefined && x[k] !== null) o[k] = x[k]; });
        return o;
      })};
    }).filter(function(x){ return x; });
  }
  var results = [], failed = 0, authFail = false, idx = 0;
  async function run(job){
    var body = [];
    job.ids.forEach(function(i){ job.types.forEach(function(t){ body.push({interval_id:i, obj_type:t, student_id:sid}); }); });
    for (var n = 0; n < 12; n++) {
      if (Date.now() > DEADLINE) break;
      try {
        var r = await fetch('/api/myschool/v1/datamart', {method:'POST', credentials:'include',
          headers:{'Content-Type':'application/json','Accept':'application/json'}, body: JSON.stringify(body)});
        if (r.status === 200) { results.push(slim(await r.json())); return; }
        if (r.status === 401 || r.status === 403) { authFail = true; failed++; return; }
      } catch (e) {}
      // 202 («данные готовятся»), 429, 5xx — как и сама «Моя школа», повторяем с паузой
      await sleep(1200 + n * 600);
    }
    failed++;
  }
  async function worker(){ while (idx < JOBS.length && !authFail) { var j = JOBS[idx++]; await run(j); } }
  await Promise.all([worker(), worker(), worker()]);
  failed += JOBS.length - idx;
  done({ok:true, login:authFail, failed:failed, total:JOBS.length, data:[].concat.apply([], results)});
 } catch (e) { done({ok:false, err:String(e)}); }
})();
"""

    private fun buildJs(jobs: List<SyncJob>): String {
        val arr = JSONArray()
        for (j in jobs) {
            arr.put(JSONObject().put("ids", JSONArray(j.ids)).put("types", JSONArray(j.types)))
        }
        return JS.replace("__JOBS__", arr.toString())
    }

    private fun failure(msg: String, login: Boolean = false) = FetchResult(JSONArray(), 0, 0, msg, login)

    private fun parse(s: String): FetchResult {
        val o = JSONObject(s)
        if (!o.optBoolean("ok")) return failure(o.optString("err", "Ошибка"), o.optBoolean("login"))
        return FetchResult(o.getJSONArray("data"), o.optInt("failed"), o.optInt("total"), null, o.optBoolean("login"))
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    suspend fun fetch(ctx: Context, jobs: List<SyncJob>): FetchResult = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine<FetchResult> { cont ->
            val wv = WebView(ctx.applicationContext)
            wv.settings.javaScriptEnabled = true
            wv.settings.domStorageEnabled = true
            CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
            wv.onResume()
            wv.resumeTimers()
            val finished = AtomicBoolean(false)
            fun finish(r: FetchResult) {
                if (!finished.compareAndSet(false, true)) return
                main.post { wv.destroy() }
                if (cont.isActive) cont.resume(r)
            }
            val js = buildJs(jobs)
            wv.addJavascriptInterface(object {
                @JavascriptInterface
                fun done(s: String) {
                    finish(runCatching { parse(s) }.getOrElse { failure("Не удалось разобрать ответ") })
                }
            }, "Bridge")
            val started = AtomicBoolean(false)
            wv.webViewClient = object : WebViewClient() {
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) finish(failure("Не удалось открыть Госуслуги: ${error.description}"))
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    val host = Uri.parse(url ?: "").host.orEmpty()
                    if (host == "www.gosuslugi.ru") {
                        if (started.compareAndSet(false, true)) {
                            main.postDelayed({ view.evaluateJavascript(js, null) }, 1500)
                        }
                    } else if (host.endsWith("gosuslugi.ru")) {
                        finish(failure("Сессия закончилась — войдите заново", true))
                    }
                }
            }
            cont.invokeOnCancellation { main.post { wv.destroy() } }
            wv.loadUrl("https://www.gosuslugi.ru/school")
        }
    }
}
