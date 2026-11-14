package com.example.commutemate

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.AudioAttributes
import android.media.MediaPlayer
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Reuse one player; cancel promptly and release once when leaving the screen. */
internal class SpeakingPromptPlayer(private val context: Context) {
    private var player: MediaPlayer? = null
    private var generation = 0L

    fun stop() {
        generation += 1
        player?.let {
            it.setOnCompletionListener(null)
            it.setOnPreparedListener(null)
            it.setOnErrorListener(null)
            runCatching { it.reset() }
        }
    }

    fun release() {
        stop()
        player?.release()
        player = null
    }

    suspend fun play(name: String) = playSource { context.assets.openFd("speaking/$name.wav") }

    suspend fun playResource(resourceId: Int) = playSource {
        context.resources.openRawResourceFd(resourceId)
            ?: error("예시 문장 음성을 찾지 못했습니다.")
    }

    private suspend fun playSource(openSource: () -> AssetFileDescriptor) =
        suspendCancellableCoroutine<Unit> { continuation ->
        stop()
        val token = generation
        val current = player ?: MediaPlayer().also { player = it }
        continuation.invokeOnCancellation { if (generation == token) stop() }
        try {
            current.setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            openSource().use { descriptor ->
                current.setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
            }
            current.setOnPreparedListener {
                if (continuation.isActive && generation == token) {
                    try {
                        it.start()
                    } catch (error: Exception) {
                        stop()
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                }
            }
            current.setOnCompletionListener {
                if (generation == token) {
                    stop()
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }
            current.setOnErrorListener { _, what, extra ->
                if (generation == token) {
                    stop()
                    if (continuation.isActive) {
                        continuation.resumeWithException(IllegalStateException("안내 음성 오류: $what / $extra"))
                    }
                }
                true
            }
            current.prepareAsync()
        } catch (error: Exception) {
            stop()
            if (continuation.isActive) continuation.resumeWithException(error)
        }
    }
}
