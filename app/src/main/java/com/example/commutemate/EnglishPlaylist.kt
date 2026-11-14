package com.example.commutemate

import android.content.Context
import android.media.MediaPlayer

class EnglishPlaylist(
    private val context: Context,
    private val player: MediaPlayer,
    lessons: List<EnglishLesson>,
    private val isSessionActive: () -> Boolean,
    private val canAutoPlay: () -> Boolean,
    private val onStarted: () -> Unit,
    private val onClipChanged: (Int) -> Unit,
    private val onWaiting: () -> Unit,
    private val onFinished: () -> Unit,
    private val onError: (String) -> Unit,
    private val onPracticeRequested: ((SpeakingLessonRequest) -> Unit)? = null,
    private val answerOnly: Boolean = false,
    private val lessonLimit: () -> Int = { lessons.size }
) {
    private val clips = lessons.flatMap { lesson ->
        if (answerOnly) listOf(lesson.audio.answerResId)
        else listOf(lesson.audio.questionResId, lesson.audio.answerResId)
    }
    private val progress = EnglishCourseProgress(lessons.size, onPracticeRequested != null && !answerOnly, clips.size)
    val currentClipIndex: Int get() = progress.currentClipIndex

    fun startFromBeginning() {
        if (!isSessionActive()) return
        onStarted()
        val count = lessonLimit()
        require(count > 0 && count * (if (answerOnly) 1 else 2) <= clips.size)
        applyStep(progress.start(count * if (answerOnly) 1 else 2))
    }

    fun completePractice(request: SpeakingLessonRequest) {
        if (!isSessionActive()) return
        applyStep(progress.practiceCompleted(request))
    }

    /** Invalidate queued completions when arriving, exiting, or restarting. */
    fun cancel() {
        progress.cancel()
        player.setOnCompletionListener(null)
    }

    private fun applyStep(step: EnglishCourseStep) {
        when (step) {
            is EnglishCourseStep.Clip -> loadAndPlayCurrent()
            is EnglishCourseStep.Practice -> onPracticeRequested?.invoke(step.request)
            EnglishCourseStep.Complete -> onFinished()
            EnglishCourseStep.Ignore -> Unit
        }
    }

    private fun loadAndPlayCurrent() {
        if (!isSessionActive()) return
        val loadedIndex = progress.currentClipIndex
        val loadedSession = progress.sessionId
        try {
            player.reset()
            context.resources.openRawResourceFd(clips[loadedIndex]).use { descriptor ->
                player.setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
            }
            player.setOnErrorListener { _, what, extra ->
                progress.cancel()
                onError("영어 음성 재생 오류: $what / $extra")
                true
            }
            player.setOnCompletionListener {
                if (isSessionActive()) applyStep(progress.clipEnded(loadedIndex, loadedSession))
            }
            player.prepare()
            onClipChanged(loadedIndex)
            if (canAutoPlay()) player.start() else onWaiting()
        } catch (e: Exception) {
            progress.cancel()
            onError("영어 음성을 불러오지 못했습니다: ${e.message}")
        }
    }
}
