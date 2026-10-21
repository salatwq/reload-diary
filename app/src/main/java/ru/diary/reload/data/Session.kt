package ru.diary.reload.data

import android.content.Context
import android.webkit.CookieManager
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Резервная копия cookies входа через Госуслуги.
 * Сеансовые cookies WebView теряет при перезапуске приложения — поэтому сохраняем их сами
 * и при старте возвращаем с продлённым сроком (только те, которых в браузере сейчас нет).
 */
object Session {
    private val URLS = listOf(
        "https://www.gosuslugi.ru/", "https://esia.gosuslugi.ru/",
        "https://lk.gosuslugi.ru/", "https://gosuslugi.ru/",
    )
    private const val KEY = "session_cookies"
    private const val MAX_AGE = 60L * 60 * 24 * 90

    @Volatile private var restored = false

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("diary", 0)

    private fun names(header: String?): Set<String> =
        header.orEmpty().split(";").mapNotNull { it.trim().substringBefore('=', "").takeIf { n -> n.isNotEmpty() } }.toSet()

    /** Один раз за запуск процесса: вернуть пропавшие cookies. */
    fun restore(ctx: Context): Int {
        if (restored) return 0
        restored = true
        val raw = prefs(ctx).getString(KEY, null) ?: return 0
        var n = 0
        runCatching {
            val cm = CookieManager.getInstance()
            val o = JSONObject(raw)
            for (url in URLS) {
                val saved = o.optString(url)
                if (saved.isBlank()) continue
                val present = names(cm.getCookie(url))
                for (pair in saved.split(";").map { it.trim() }) {
                    val name = pair.substringBefore('=', "")
                    if (name.isEmpty() || name in present) continue
                    cm.setCookie(url, "$pair; Path=/; Max-Age=$MAX_AGE; Secure")
                    n++
                }
            }
            if (n > 0) cm.flush()
        }
        return n
    }

    /** Сохранить текущие cookies (вызывать только после удачного входа/загрузки). */
    fun save(ctx: Context): Int {
        restored = true
        var n = 0
        runCatching {
            val cm = CookieManager.getInstance()
            val o = JSONObject()
            for (url in URLS) {
                val c = cm.getCookie(url).orEmpty()
                if (c.isNotBlank()) {
                    o.put(url, c)
                    n += names(c).size
                }
            }
            if (n > 0) {
                prefs(ctx).edit().putString(KEY, o.toString())
                    .putLong("session_saved", System.currentTimeMillis()).putInt("session_count", n).apply()
            }
            cm.flush()
        }
        return n
    }

    fun clear(ctx: Context) {
        prefs(ctx).edit().remove(KEY).remove("session_saved").remove("session_count").apply()
    }

    fun info(ctx: Context): String {
        val p = prefs(ctx)
        val t = p.getLong("session_saved", 0L)
        if (t == 0L) return "Резервной копии входа пока нет"
        return "Вход сохранён (${p.getInt("session_count", 0)} cookie), обновлён " +
            SimpleDateFormat("dd.MM HH:mm", Locale("ru")).format(Date(t))
    }
}
