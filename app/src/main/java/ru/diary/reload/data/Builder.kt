package ru.diary.reload.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

/** Собирает данные для экранов из сохранённых ответов «Моей школы». */
object Builder {
    private fun JSONObject.str(k: String): String = optString(k).takeIf { it.isNotBlank() && it != "null" } ?: ""

    fun build(cache: Map<String, JSONArray>): DiaryData {
        val marks = ArrayList<JSONObject>()
        val homework = ArrayList<JSONObject>()
        val lessons = ArrayList<JSONObject>()
        for ((key, arr) in cache) {
            val bucket = when (key.substringAfter('|')) {
                Plan.MARKS -> marks
                Plan.HOMEWORK -> homework
                Plan.LESSONS -> lessons
                else -> continue
            }
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { bucket += it }
        }
        val valid = marks.distinctBy { it.str("id") }.filter {
            it.str("subject_name").isNotBlank() && it.str("mark_value1").isNotBlank()
        }
        val (fin, cur) = valid.partition { isFinal(it) }
        val infos = valid.map {
            MarkInfo(it.str("id"), it.str("subject_name"), it.str("mark_value1"), it.str("mark_date"), it.str("work_type_description"))
        }
        return DiaryData(parseLessons(lessons), parseGrades(cur), parseHomework(homework), parseFinals(fin), infos)
    }

    private val FINAL = Regex("(?i)итог|четверт|полугод|годов|final|period|quarter|semester|annual")

    /** Итоговые определяем по типу работы; точный признак уточним по реальным данным. */
    private fun isFinal(o: JSONObject) =
        listOf("work_type_code", "work_type_description", "work_kind", "work_name").any { FINAL.containsMatchIn(o.str(it)) }

    private fun parseFinals(list: List<JSONObject>): List<FinalMark> =
        list.sortedWith(compareBy({ it.str("subject_name") }, { it.str("mark_date") })).map {
            val label = listOf("work_type_description", "work_name", "work_kind").map { k -> it.str(k) }.firstOrNull { v -> v.isNotBlank() }.orEmpty()
            FinalMark(it.str("subject_name"), label, it.str("mark_value1"))
        }

    /** Полугодия сбрасываются с 1 июля: июль–декабрь — 1-е (с 1 сентября), январь–июнь — 2-е (с 1 января). */
    private fun halfYearStart(): String {
        val t = LocalDate.now()
        return (if (t.monthValue >= 7) LocalDate.of(t.year, 9, 1) else LocalDate.of(t.year, 1, 1)).toString()
    }

    private fun parseGrades(list: List<JSONObject>): List<GradeRow> {
        val start = halfYearStart()
        return list.filter { it.str("mark_date").let { d -> d.isEmpty() || d >= start } }
            .groupBy { it.str("subject_name") }
            .map { (subject, ms) ->
                val values = ms.sortedBy { it.str("mark_date") }.map { it.str("mark_value1") }
                val nums = values.mapNotNull { v -> v.toIntOrNull()?.takeIf { it in 2..5 } }
                val avg = if (nums.isEmpty()) "" else String.format(Locale.US, "%.2f", nums.average())
                GradeRow(subject, values, avg)
            }
            .sortedBy { it.subject }
    }

    private fun parseHomework(list: List<JSONObject>): List<Homework> =
        list.distinctBy { it.str("id") }
            .filter { it.str("subject_name").isNotBlank() && it.str("plan_ready_date").length >= 10 }
            .map {
                val d = it.str("plan_ready_date").take(10)
                Homework(it.str("subject_name"), d.substring(8, 10) + "." + d.substring(5, 7), it.str("description").trim(), d)
            }

    private fun parseLessons(list: List<JSONObject>): List<Lesson> {
        val items = list
            .distinctBy { it.str("id").ifEmpty { it.str("lesson_id") + it.str("start_datetime") } }
            .filter { it.optBoolean("active", true) && it.str("subject_name").isNotBlank() }
            .mapNotNull { e ->
                val start = e.str("start_datetime")
                if (start.length < 16) return@mapNotNull null
                val dt = runCatching { LocalDateTime.parse(start.take(16).replace(' ', 'T')) }.getOrNull()
                    ?: return@mapNotNull null
                if (dt.dayOfWeek == DayOfWeek.SUNDAY) return@mapNotNull null
                Pair(dt, e)
            }
        return items.groupBy { it.first.toLocalDate() }.flatMap { (date, es) ->
            es.sortedBy { it.first }.mapIndexed { i, (dt, e) ->
                val end = e.str("end_datetime").let { if (it.length >= 16) it.substring(11, 16) else "" }
                val t = dt.toLocalTime().toString().take(5)
                val teacher = listOf(
                    e.str("lastname"),
                    e.str("firstname").take(1).let { if (it.isEmpty()) "" else "$it." },
                    e.str("patronymic").take(1).let { if (it.isEmpty()) "" else "$it." },
                ).filter { it.isNotEmpty() }.joinToString(" ")
                Lesson(
                    date.dayOfWeek.value, i + 1, if (end.isNotEmpty()) "$t–$end" else t,
                    e.str("subject_name"), e.str("room"), teacher, date.toString(),
                )
            }
        }
    }
}
