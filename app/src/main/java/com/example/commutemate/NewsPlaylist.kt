package com.example.commutemate

import android.content.Context
import android.media.MediaPlayer

/** Reuse the briefing player. A token prevents callbacks from advancing a cancelled run. */
class NewsPlaylist(private val context: Context, private val player: MediaPlayer,
    private val stories: () -> List<NewsStory>, private val active: () -> Boolean,
    private val canPlay: () -> Boolean, private val onClip: (Int) -> Unit,
    private val onWaiting: () -> Unit, private val onFinished: () -> Unit,
    private val onError: (String) -> Unit) {
    private var token = 0L
    private var currentIndex = -1
    fun cancel() { token++; player.setOnCompletionListener(null) }
    fun start() { cancel(); load(0, token) }
    private fun load(index: Int, run: Long) {
        if (run != token || !active()) return
        val current = stories()
        if (index == current.size) { token++; onFinished(); return }
        currentIndex = index
        try {
            player.reset()
            val story = current[index]
            if (story.audioFile != null) player.setDataSource(story.audioFile)
            else context.assets.openFd(story.assetPath).use {
                player.setDataSource(it.fileDescriptor, it.startOffset, it.length)
            }
            player.setOnCompletionListener {
                if (run == token && currentIndex == index && active()) load(index + 1, run)
            }
            player.setOnErrorListener { _, what, extra ->
                if (run == token && currentIndex == index && active()) { token++; onError("뉴스 음성 오류" + ": $what / $extra") }
                true
            }
            player.prepare()
            player.playbackParams = android.media.PlaybackParams().setSpeed(1f).setPitch(1f)
            onClip(index)
            if (canPlay()) player.start() else onWaiting()
        } catch (e: Exception) { if (run == token) { token++; onError(e.message ?: "뉴스 재생 오류") } }
    }
}
