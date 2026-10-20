package ru.diary.reload.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONTokener
import ru.diary.reload.AppViewModel
import ru.diary.reload.data.SiteConfig


/** Перехватывает fetch/XHR и запоминает ответы (для разбора API «Моя школа»). */
private const val CAPTURE_JS = """
(function(){
 if (window.__capInstalled) return; window.__capInstalled = true; window.__cap = [];
 function skip(u){ return u.indexOf('/api/myschool/')<0 || /regions|disclaimers|auth\/student/.test(u); }
 function summ(u,t){ if(u.indexOf('datamart')<0) return t.slice(0,3000);
  try{ var j=JSON.parse(t); if(!Array.isArray(j)) return t.slice(0,3000);
   return JSON.stringify(j.map(function(o){ var d=o.data||[]; return {t:o.obj_type,i:o.interval_id,n:d.length,
    r:d.slice(0,120).map(function(x){ return [x.subject_name,x.start_datetime,x.end_datetime,x.active,x.lesson_type,x.mark_value1,x.mark_date,x.work_type_code,x.work_type_description,x.work_kind,x.plan_ready_date,x.skipping_description]; })}; }));
  }catch(e){ return t.slice(0,3000); } }
 function push(m,u,b,s,t){ if(skip(String(u))) return;
   window.__cap.push({m:m,u:String(u),b:b?String(b).slice(0,1500):null,s:s,t:summ(String(u),t||'')});
   if (window.__cap.length>40) window.__cap.shift(); }
 var of = window.fetch;
 if (of) window.fetch = function(i,o){
   var u=(typeof i==='string')?i:(i&&i.url); var m=(o&&o.method)||(i&&i.method)||'GET'; var b=o&&o.body;
   return of.apply(this,arguments).then(function(r){
     try{ r.clone().text().then(function(t){push(m,u,b,r.status,t);}); }catch(e){} return r; }); };
 var ox=XMLHttpRequest.prototype.open, os=XMLHttpRequest.prototype.send;
 XMLHttpRequest.prototype.open=function(m,u){ this.__m=m; this.__u=u; return ox.apply(this,arguments); };
 XMLHttpRequest.prototype.send=function(b){ var x=this;
   x.addEventListener('load',function(){ try{
     var t=(x.responseType===''||x.responseType==='text')?x.responseText:'';
     push(x.__m,x.__u,b,x.status,t);}catch(e){} });
   return os.apply(this,arguments); };
})();
"""

/**
 * Встроенный браузер: здесь выполняется вход через Госуслуги, а также указываются
 * страницы расписания/оценок/ДЗ (открыли страницу → нажали кнопку).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(vm: AppViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var web by remember { mutableStateOf<WebView?>(null) }
    var note by remember { mutableStateOf("Войдите через Госуслуги, затем нажмите «Я ВОШЁЛ».") }

    BackHandler {
        val w = web
        if (w != null && w.canGoBack()) w.goBack() else onBack()
    }
    DisposableEffect(Unit) { onDispose { web?.destroy() } }

    P3Background {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            AndroidView(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                factory = { c ->
                    WebView(c).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                                // не даём уйти в deeplink приложения Госуслуг
                                val scheme = r.url.scheme ?: ""
                                return scheme != "http" && scheme != "https"
                            }
                            override fun onPageStarted(v: WebView, url: String?, f: android.graphics.Bitmap?) {
                                v.evaluateJavascript(CAPTURE_JS, null)
                            }
                            override fun onPageFinished(v: WebView, url: String?) {
                                v.evaluateJavascript(CAPTURE_JS, null)
                            }
                        }
                        web = this
                        loadUrl(SiteConfig.BASE)
                    }
                },
            )
            Column(Modifier.fillMaxWidth().background(P3.Navy).padding(8.dp)) {
                Text(note, color = P3.Cyan, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SlantButton("Я ВОШЁЛ") {
                        vm.markLoggedIn()
                        note = "Готово. Нажмите «ЗАКРЫТЬ» и «ОБНОВИТЬ ДАННЫЕ» в Аккаунте."
                    }
                    SlantButton("МОЯ ШКОЛА", filled = false) {
                        web?.loadUrl("https://www.gosuslugi.ru/school")
                    }
                    SlantButton("ПОДЕЛИТЬСЯ ЗАПРОСАМИ") {
                        web?.evaluateJavascript("JSON.stringify(window.__cap||[])") { raw ->
                            val txt = (JSONTokener(raw).nextValue() as? String).orEmpty().take(150_000)
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, txt)
                            }
                            try {
                                // сначала пробуем сразу открыть приложение Claude
                                ctx.startActivity(Intent(send).setPackage("com.anthropic.claude"))
                            } catch (e: Exception) {
                                ctx.startActivity(Intent.createChooser(send, "Поделиться запросами"))
                            }
                            note = "Отправлено запросов: ${txt.count { it == '{' }}"
                        }
                    }
                    SlantButton("КОПИРОВАТЬ ЗАПРОСЫ", filled = false) {
                        web?.evaluateJavascript("JSON.stringify(window.__cap||[])") { raw ->
                            val txt = (JSONTokener(raw).nextValue() as? String).orEmpty()
                            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("capture", txt))
                            note = "Скопировано запросов: ${txt.count { it == '{' }}. Размер ${txt.length} симв. Внутри могут быть личные данные."
                        }
                    }
                    SlantButton("КОПИРОВАТЬ HTML", filled = false) {
                        web?.evaluateJavascript("document.documentElement.outerHTML") { raw ->
                            val html = (JSONTokener(raw).nextValue() as? String).orEmpty()
                            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("page", html))
                            note = "HTML скопирован (${html.length} символов). В нём могут быть личные данные."
                        }
                    }
                    SlantButton("ЗАКРЫТЬ", filled = false, onClick = onBack)
                }
            }
        }
    }
}

private fun save(web: WebView?, vm: AppViewModel, key: String, name: String, setNote: (String) -> Unit) {
    val url = web?.url
    if (url == null) { setNote("Страница ещё не загружена"); return }
    vm.saveUrl(key, url)
    setNote("$name: сохранено\n$url")
}
