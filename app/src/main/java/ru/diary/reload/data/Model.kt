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

data class Homework(val subject: String, val due: String, val text: String)

data class DiaryData(
    val lessons: List<Lesson>,
    val grades: List<GradeRow>,
    val homework: List<Homework>,
) {
    val isEmpty get() = lessons.isEmpty() && grades.isEmpty() && homework.isEmpty()
}
