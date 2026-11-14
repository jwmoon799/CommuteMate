package com.example.commutemate

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class LatestNews(val stories: List<NewsStory>, val fetchedAt: String)

/** Local emulator helper fetches the RSS and creates audio; no API secrets in the APK. */
object NewsRepository {
    private const val BASE = "http://127.0.0.1:8092"
    private fun request(path: String): ByteArray {
        val connection = URL(BASE + path).openConnection() as HttpURLConnection
        connection.connectTimeout = 8000
        connection.readTimeout = 600000
        return try {
            if (connection.responseCode != 200) error("뉴스 서버가 최신 자료를 준비하지 못했습니다.")
            connection.inputStream.use { stream ->
                val data = stream.readBytes()
                check(data.size <= 160_000_000) { "뉴스 응답 크기 초과" }
                data
            }
        } finally { connection.disconnect() }
    }
    fun fetch(context: Context, force: Boolean): LatestNews {
        val metadata = JSONObject(String(request("/news/latest" + if (force) "?force=1" else ""), Charsets.UTF_8))
        check(metadata.getString("sort") == "latest")
        val fetchedAt = metadata.getString("fetchedAt")
        val items = metadata.getJSONArray("stories")
        check(items.length() == 2) { "뉴스 2편이 필요합니다." }
        val folder = File(context.filesDir, "latest-news").apply { mkdirs() }
        val stories = (0 until items.length()).map { index ->
            val item = items.getJSONObject(index)
            check(item.getString("contentMode") == "full") { "뉴스 서버를 전체 본문 버전으로 갱신해주세요." }
            val id = item.getString("id")
            val audioPath = item.getString("audioPath")
            check(id.matches(Regex("[a-f0-9]{24}")) && audioPath == "/audio/$id.wav")
            val target = File(folder, "$id.wav")
            val audio = request(audioPath)
            val hash = MessageDigest.getInstance("SHA-256").digest(audio).joinToString("") { "%02x".format(it) }
            check(hash == item.getString("audioSha256")) { "뉴스 음성 다운로드 검증 실패" }
            check(audio.size > 44 && String(audio, 0, 4) == "RIFF" && String(audio, 8, 4) == "WAVE")
            val temp = File.createTempFile(id, ".tmp", folder)
            temp.writeBytes(audio)
            check(temp.renameTo(target)) { "뉴스 음성 저장 실패" }
            NewsStory(id, item.getString("category"), item.getString("title"), item.getString("source"),
                item.getString("publishedAt"), item.getString("url"), item.getString("text"), target.absolutePath, fetchedAt)
        }
        return LatestNews(stories, fetchedAt)
    }
}
