package ru.diary.reload.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import ru.diary.reload.data.Sync
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Раз в ~15 минут смотрит оценки и присылает уведомление о каждой новой.
 * Заодно «трогает» Госуслуги, чтобы сессия входа не простаивала.
 */
class MarksWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val prefs = ctx.getSharedPreferences("diary", 0)
        if (prefs.getBoolean("demo", true)) return Result.success()
        try {
            val r = withTimeout(80_000) { Sync.run(ctx, marksOnly = true) }
            if (r.needLogin) {
                if (!prefs.getBoolean("session_warned", false)) {
                    Notifier.show(ctx, 2, "Нужно войти заново", "Сессия Госуслуг закончилась. Откройте приложение → Аккаунт → Войти через Госуслуги.")
                    prefs.edit().putBoolean("session_warned", true).apply()
                }
            } else {
                prefs.edit().putBoolean("session_warned", false).apply()
                val marks = r.data.marks
                val known = prefs.getStringSet("known_marks", null)?.let { HashSet(it) }
                if (marks.isNotEmpty()) {
                    if (known == null) {
                        prefs.edit().putStringSet("known_marks", marks.map { it.id }.toSet()).apply()
                    } else {
                        val fresh = marks.filter { it.id !in known }
                        // старые оценки, которые просто впервые подгрузились, не объявляем
                        val cutoff = LocalDate.now().minusDays(7).toString()
                        fresh.filter { it.date >= cutoff }.forEach {
                            val d = if (it.date.length >= 10) it.date.substring(8, 10) + "." + it.date.substring(5, 7) else ""
                            Notifier.show(
                                ctx, it.id.hashCode(), "Новая оценка: ${it.value}",
                                listOf(it.subject, it.kind, d).filter { s -> s.isNotBlank() }.joinToString(" · "),
                            )
                        }
                        if (fresh.isNotEmpty()) {
                            known += fresh.map { it.id }
                            prefs.edit().putStringSet("known_marks", known).apply()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException && e !is TimeoutCancellationException) throw e
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "marks-check"

        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<MarksWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun cancel(ctx: Context) {
            WorkManager.getInstance(ctx).cancelUniqueWork(NAME)
        }
    }
}
