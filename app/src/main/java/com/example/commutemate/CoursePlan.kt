package com.example.commutemate

enum class CourseType(val label: String) {
    ENGLISH("영어 중심 코스"), NEWS("뉴스 포함 코스")
}

data class CoursePlan(
    val commuteMinutes: Int,
    val briefingMinutes: Int,
    val listeningMinutes: Int,
    val speakingMinutes: Int,
    val reviewMinutes: Int,
    val bufferMinutes: Int,
    val newsMinutes: Int = 0,
    val courseType: CourseType = CourseType.ENGLISH
) {
    val title: String get() = courseType.label
    val totalMinutes: Int get() = briefingMinutes + listeningMinutes + speakingMinutes + reviewMinutes + bufferMinutes + newsMinutes
    val lessonCount: Int get() = if (courseType == CourseType.NEWS) 4 else 8
}

fun createCoursePlan(commuteMinutes: Int, type: CourseType = CourseType.ENGLISH): CoursePlan {
    require(commuteMinutes in 5..90) { "출근시간은 5~90분이어야 합니다." }
    if (commuteMinutes == 23) return when (type) {
        CourseType.ENGLISH -> CoursePlan(23, 2, 6, 8, 4, 3)
        CourseType.NEWS -> CoursePlan(23, 2, 4, 6, 4, 3, 4, type)
    }
    val buffer = maxOf(1, (commuteMinutes + 9) / 10)
    val learning = commuteMinutes - buffer
    // Largest-remainder allocation keeps every duration within the selected total.
    val weights = if (type == CourseType.NEWS) listOf(2, 4, 6, 4, 4) else listOf(2, 6, 8, 4, 0)
    val parts = weights.map { learning * it / 20 }.toMutableList()
    val order = weights.indices.sortedByDescending { learning * weights[it] % 20 }
    repeat(learning - parts.sum()) { parts[order[it]] += 1 }
    return CoursePlan(commuteMinutes, parts[0], parts[1], parts[2], parts[3], buffer, parts[4], type)
}
