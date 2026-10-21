package ru.diary.reload.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.IsoFields

class SyncJob(val ids: List<String>, val types: List<String>)

/**
 * Что запрашивать у «Моей школы». Интервал = номер недели ISO + учебный год (432026 = 43-я неделя, 2026/27).
 */
object Plan {
    const val MARKS = "student_marks"
    const val LESSONS = "student_lessons"
    const val HOMEWORK = "student_homeworks_materials"

    private fun weekIds(monday: LocalDate, acYear: Int): List<String> {
        val w = monday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        val ids = mutableListOf("$w$acYear")
        // после смены календарного года неизвестно, какой суффикс у портала — пробуем оба
        val y = monday.plusDays(3).year
        if (y > acYear) ids += "$w$y"
        return ids
    }

    fun jobs(today: LocalDate, cached: Set<String>, marksOnly: Boolean, full: Boolean): List<SyncJob> {
        val acYear = if (today.monthValue >= 7) today.year else today.year - 1
        val base = today.with(DayOfWeek.MONDAY)
        fun missing(ids: List<String>, type: String) = ids.none { "$it|$type" in cached }
        val out = mutableListOf<SyncJob>()

        val marksWeeks = generateSequence(LocalDate.of(acYear, 9, 1).with(DayOfWeek.MONDAY)) { it.plusWeeks(1) }
            .takeWhile { !it.isAfter(base) }.toList()
        val wantMarks = when {
            marksOnly -> marksWeeks.takeLast(2)
            full -> marksWeeks
            else -> marksWeeks.filterIndexed { i, w ->
                i >= marksWeeks.size - 3 || missing(weekIds(w, acYear), MARKS)
            }
        }
        wantMarks.flatMap { weekIds(it, acYear) }.chunked(5).forEach { out += SyncJob(it, listOf(MARKS)) }
        if (marksOnly) return out

        for (off in -5..6) {
            val ids = weekIds(base.plusWeeks(off.toLong()), acYear)
            if (full || off >= -1 || missing(ids, LESSONS)) out += SyncJob(ids, listOf(LESSONS, HOMEWORK))
        }
        return out
    }
}
