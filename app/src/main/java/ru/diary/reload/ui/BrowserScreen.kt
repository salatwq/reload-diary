package ru.diary.reload.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
                        note = "Сессия сохранена. Откройте страницу расписания и нажмите «ЭТО РАСПИСАНИЕ»."
                    }
                    SlantButton("ЭТО РАСПИСАНИЕ", filled = false) { save(web, vm, "schedule", "Расписание") { note = it } }
                    SlantButton("ЭТО ОЦЕНКИ", filled = false) { save(web, vm, "grades", "Оценки") { note = it } }
                    SlantButton("ЭТО ДЗ", filled = false) { save(web, vm, "homework", "Домашка") { note = it } }
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
