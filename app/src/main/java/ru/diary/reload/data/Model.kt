package ru.diary.reload.data

data class Lesson(
    val day: Int,          // 1 = понедельник … 6 = суббота
    val number: Int,
    val time: String,
    val subject: String,
    val room: String,
    val teacher: String,
)

data class GradeRow(val subject: String, val marks: List<String>, val average: String)

data class Homework(val subject: String, val due: String, val text: String, val dueIso: String = "")

data class MarkInfo(val id: String, val subject: String, val value: String, val date: String, val kind: String)

data class FinalMark(val subject: String, val period: String, val value: String)

data class DiaryData(
    val lessons: List<Lesson>,
    val grades: List<GradeRow>,
    val homework: List<Homework>,
    val finals: List<FinalMark> = emptyList(),
    val marks: List<MarkInfo> = emptyList(),
) {
    val isEmpty get() = lessons.isEmpty() && grades.isEmpty() && homework.isEmpty() && finals.isEmpty()
}
