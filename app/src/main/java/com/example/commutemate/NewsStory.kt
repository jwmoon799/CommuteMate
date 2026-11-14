package com.example.commutemate

import android.content.Context
import org.json.JSONArray

data class NewsStory(val id: String, val category: String, val title: String, val source: String,
    val publishedAt: String, val url: String, val text: String,
    val audioFile: String? = null, val fetchedAt: String = "") {
    val assetPath: String get() = "news/$id.wav"
}
fun loadNewsStories(context: Context): List<NewsStory> {
    val data = JSONArray(context.assets.open("news/stories.json").bufferedReader().use { it.readText() })
    return (0 until data.length()).map { i ->
        val item = data.getJSONObject(i)
        NewsStory(item.getString("id"), item.getString("category"), item.getString("title"),
            item.getString("source"), item.getString("publishedAt"), item.getString("url"), item.getString("text"))
    }
}
