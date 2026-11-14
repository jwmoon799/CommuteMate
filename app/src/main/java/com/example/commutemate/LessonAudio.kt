package com.example.commutemate

data class LessonAudio(
    val questionResId: Int,
    val answerResId: Int
)

val EnglishLesson.audio: LessonAudio
    get() = when (id) {
        "commute" -> LessonAudio(
            R.raw.lesson_commute_question,
            R.raw.lesson_commute_answer
        )

        "duration" -> LessonAudio(
            R.raw.lesson_duration_question,
            R.raw.lesson_duration_answer
        )

        "start_time" -> LessonAudio(
            R.raw.lesson_start_time_question,
            R.raw.lesson_start_time_answer
        )

        "traffic" -> LessonAudio(
            R.raw.lesson_traffic_question,
            R.raw.lesson_traffic_answer
        )

        "meeting" -> LessonAudio(
            R.raw.lesson_meeting_question,
            R.raw.lesson_meeting_answer
        )

        "priority" -> LessonAudio(
            R.raw.lesson_priority_question,
            R.raw.lesson_priority_answer
        )

        "clarification" -> LessonAudio(
            R.raw.lesson_clarification_question,
            R.raw.lesson_clarification_answer
        )

        "follow_up" -> LessonAudio(
            R.raw.lesson_follow_up_question,
            R.raw.lesson_follow_up_answer
        )

        else -> error("음성이 등록되지 않은 문항: $id")
    }