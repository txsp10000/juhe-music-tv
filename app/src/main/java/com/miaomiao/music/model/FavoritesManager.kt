package com.miaomiao.music.model

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object FavoritesManager {
    private const val PREF_NAME = "tvmusic_favorites"
    private const val KEY_SONGS = "favorite_songs"
    private val gson = Gson()

    fun save(context: Context, song: Song) {
        val songs = getAll(context).toMutableList()
        // 避免重复
        val existing = songs.indexOfFirst { it.name == song.name && it.singer == song.singer }
        if (existing >= 0) songs[existing] = song
        else songs.add(0, song)
        saveAll(context, songs)
    }

    fun remove(context: Context, song: Song) {
        val songs = getAll(context).toMutableList()
        songs.removeAll { it.name == song.name && it.singer == song.singer }
        saveAll(context, songs)
    }

    fun isFavorite(context: Context, song: Song): Boolean {
        return getAll(context).any { it.name == song.name && it.singer == song.singer }
    }

    fun getAll(context: Context): List<Song> {
        val json = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SONGS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<Song>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveAll(context: Context, songs: List<Song>) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SONGS, gson.toJson(songs))
            .apply()
    }
}
