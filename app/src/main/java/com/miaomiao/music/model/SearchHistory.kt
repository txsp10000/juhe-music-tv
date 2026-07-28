package com.miaomiao.music.model

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class SearchHistoryItem(
    val keyword: String,
    val timestamp: Long = System.currentTimeMillis()
)

object SearchHistoryManager {
    private const val PREFS_NAME = "tvmusic_history"
    private const val KEY_HISTORY = "search_history"
    private const val MAX_SIZE = 50
    private val gson = Gson()

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(context: Context): List<SearchHistoryItem> {
        val json = getPrefs(context).getString(KEY_HISTORY, "[]") ?: "[]"
        val type = object : TypeToken<List<SearchHistoryItem>>() {}.type
        return try { gson.fromJson(json, type) } catch (_: Exception) { emptyList() }
    }

    fun save(context: Context, keyword: String) {
        val list = load(context).toMutableList()
        list.removeAll { it.keyword == keyword }
        list.add(0, SearchHistoryItem(keyword))
        val trimmed = if (list.size > MAX_SIZE) list.subList(0, MAX_SIZE) else list
        getPrefs(context).edit().putString(KEY_HISTORY, gson.toJson(trimmed)).apply()
    }

    fun clear(context: Context) {
        getPrefs(context).edit().remove(KEY_HISTORY).apply()
    }

    fun delete(context: Context, keyword: String) {
        val list = load(context).toMutableList()
        list.removeAll { it.keyword == keyword }
        getPrefs(context).edit().putString(KEY_HISTORY, gson.toJson(list)).apply()
    }
}
