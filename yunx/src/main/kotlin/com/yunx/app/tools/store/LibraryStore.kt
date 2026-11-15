package com.yunx.app.tools.store

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.yunx.app.tools.movie.Movie

data class MovieFavoriteEntry(
    val movie: Movie,
    val siteIndex: Int,
    val savedAt: Long,
)

data class MovieHistoryEntry(
    val movie: Movie,
    val siteIndex: Int,
    val lineName: String,
    val episodeIndex: Int,
    val episodeName: String,
    val updatedAt: Long,
)

/** 收藏与观看记录，全部保存在本机，不依赖账号。 */
class LibraryStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("nieve_library_v1", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun movieFavorites(): List<MovieFavoriteEntry> =
        readList(KEY_MOVIE_FAV, MovieFavoriteEntry::class.java)

    fun isMovieFavorite(id: String): Boolean = movieFavorites().any { it.movie.id == id }

    fun toggleMovieFavorite(movie: Movie, siteIndex: Int): Boolean {
        val list = movieFavorites().toMutableList()
        val exists = list.any { it.movie.id == movie.id }
        if (exists) {
            list.removeAll { it.movie.id == movie.id }
        } else {
            list.add(0, MovieFavoriteEntry(movie, siteIndex, System.currentTimeMillis()))
        }
        writeList(KEY_MOVIE_FAV, list)
        return !exists
    }

    fun movieHistory(): List<MovieHistoryEntry> = readList(KEY_MOVIE_HISTORY, MovieHistoryEntry::class.java)

    fun recordMovie(
        movie: Movie,
        siteIndex: Int,
        lineName: String,
        episodeIndex: Int,
        episodeName: String,
    ) {
        val list = movieHistory().toMutableList()
        list.removeAll { it.movie.id == movie.id }
        list.add(
            0,
            MovieHistoryEntry(
                movie = movie,
                siteIndex = siteIndex,
                lineName = lineName,
                episodeIndex = episodeIndex,
                episodeName = episodeName,
                updatedAt = System.currentTimeMillis()
            )
        )
        writeList(KEY_MOVIE_HISTORY, list.take(MAX_HISTORY))
    }

    private fun <T> readList(key: String, clazz: Class<T>): List<T> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return runCatching {
            val type = TypeToken.getParameterized(List::class.java, clazz).type
            gson.fromJson<List<T>>(raw, type)
        }.getOrDefault(emptyList())
    }

    private fun writeList(key: String, value: List<*>) {
        prefs.edit().putString(key, gson.toJson(value)).apply()
    }

    private companion object {
        const val KEY_MOVIE_FAV = "movie_favorites"
        const val KEY_MOVIE_HISTORY = "movie_history"
        const val MAX_HISTORY = 50
    }
}
