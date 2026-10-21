package ru.diary.reload.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Загрузка из «Моей школы» → кэш → данные для экранов. При любой ошибке остаются сохранённые данные. */
object Sync {
    class Result(
        val data: DiaryData,
        val error: String?,
        val failed: Int,
        val total: Int,
        val needLogin: Boolean,
    )

    suspend fun run(ctx: Context, marksOnly: Boolean = false): Result {
        val app = ctx.applicationContext
        val prefs = app.getSharedPreferences("diary", 0)
        Session.restore(app)
        val cache = withContext(Dispatchers.IO) { DiaryCache.load(app) }
        val now = System.currentTimeMillis()
        val full = !marksOnly && (
            now - prefs.getLong("last_full", 0L) > 6 * 3_600_000L ||
                cache.keys.none { it.endsWith("|" + Plan.MARKS) }
            )
        val jobs = Plan.jobs(LocalDate.now(), cache.keys, marksOnly, full)
        val fetched = GosuApi.fetch(app, jobs)
        if (fetched.error != null) {
            return Result(Builder.build(cache), fetched.error, 0, jobs.size, fetched.needLogin)
        }
        val merged = withContext(Dispatchers.IO) { DiaryCache.merge(app, fetched.items) }
        if (!fetched.needLogin) Session.save(app)
        if (full && fetched.failed == 0) prefs.edit().putLong("last_full", now).apply()
        val err = when {
            fetched.needLogin -> "Сессия закончилась — войдите заново"
            fetched.total > 0 && fetched.failed >= fetched.total -> "Госуслуги не отдали данные, повторите позже"
            else -> null
        }
        return Result(Builder.build(merged), err, fetched.failed, fetched.total, fetched.needLogin)
    }
}
