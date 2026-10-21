package ru.diary.reload.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Локальная копия ответов «Моей школы» по ключу «интервал|тип».
 * Нужна, чтобы сбой одного запроса не стирал уже загруженные оценки, расписание и ДЗ.
 * Хранится только в приватной папке приложения.
 */
object DiaryCache {
    private const val FILE = "diary_cache.json"

    private fun file(ctx: Context) = File(ctx.filesDir, FILE)

    @Synchronized
    fun load(ctx: Context): MutableMap<String, JSONArray> {
        val out = linkedMapOf<String, JSONArray>()
        runCatching {
            val f = file(ctx)
            if (f.exists()) {
                val o = JSONObject(f.readText())
                val it = o.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    out[k] = o.getJSONArray(k)
                }
            }
        }
        return out
    }

    /** Добавляет свежие ответы. Пустой ответ не затирает уже сохранённые данные. */
    @Synchronized
    fun merge(ctx: Context, fresh: JSONArray): MutableMap<String, JSONArray> {
        val cache = load(ctx)
        for (i in 0 until fresh.length()) {
            val e = fresh.optJSONObject(i) ?: continue
            val data = e.optJSONArray("data") ?: continue
            val key = e.optString("interval_id") + "|" + e.optString("obj_type")
            if (data.length() == 0 && (cache[key]?.length() ?: 0) > 0) continue
            cache[key] = data
        }
        runCatching {
            val o = JSONObject()
            for ((k, v) in cache) o.put(k, v)
            val tmp = File(ctx.filesDir, "$FILE.tmp")
            tmp.writeText(o.toString())
            tmp.renameTo(file(ctx))
        }
        return cache
    }

    @Synchronized
    fun clear(ctx: Context) {
        runCatching { file(ctx).delete() }
    }
}
