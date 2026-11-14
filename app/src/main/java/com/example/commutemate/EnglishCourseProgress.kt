package com.example.commutemate

/** A completion belongs to one lesson in one particular run of the course. */
data class SpeakingLessonRequest(val lessonIndex: Int, val sessionId: Long)

internal sealed interface EnglishCourseStep {
    data class Clip(val index: Int) : EnglishCourseStep
    data class Practice(val request: SpeakingLessonRequest) : EnglishCourseStep
    data object Complete : EnglishCourseStep
    data object Ignore : EnglishCourseStep
}

/** Keep playback waiting after every answer until its speaking practice completes. */
internal class EnglishCourseProgress(
    private val lessonCount: Int,
    private val speakingEnabled: Boolean = true,
    private val clipCount: Int = lessonCount * 2
) {
    init {
        require(lessonCount > 0 && clipCount > 0)
        require(!speakingEnabled || clipCount == lessonCount * 2)
    }
    var currentClipIndex = 0
        private set
    var sessionId = 0L
        private set
    private var activeClipCount = clipCount
    private var active = false
    private var waiting: SpeakingLessonRequest? = null

    fun start(clipsToPlay: Int = clipCount): EnglishCourseStep {
        require(clipsToPlay in 1..clipCount && (!speakingEnabled || clipsToPlay % 2 == 0))
        activeClipCount = clipsToPlay
        sessionId += 1
        currentClipIndex = 0
        waiting = null
        active = true
        return EnglishCourseStep.Clip(0)
    }

    fun cancel() {
        sessionId += 1
        active = false
        waiting = null
    }

    fun clipEnded(playedIndex: Int, playedSession: Long): EnglishCourseStep {
        if (!active || waiting != null || playedIndex != currentClipIndex || playedSession != sessionId) {
            return EnglishCourseStep.Ignore
        }
        if (speakingEnabled && currentClipIndex % 2 == 1) {
            val request = SpeakingLessonRequest(currentClipIndex / 2, sessionId)
            waiting = request
            return EnglishCourseStep.Practice(request)
        }
        return advance()
    }

    fun practiceCompleted(request: SpeakingLessonRequest): EnglishCourseStep {
        if (!active || waiting != request) return EnglishCourseStep.Ignore
        waiting = null
        return advance()
    }

    private fun advance(): EnglishCourseStep {
        if (currentClipIndex == activeClipCount - 1) {
            active = false
            return EnglishCourseStep.Complete
        }
        currentClipIndex += 1
        return EnglishCourseStep.Clip(currentClipIndex)
    }
}
