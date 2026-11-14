package com.yunx.app.tools.music

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.yunx.app.tools.toolsPrefs

enum class MusicSource(val key: String, val label: String) {
    LOCAL("local", "本地"),
    KUGOU("kg", "酷狗"),
    NETEASE("wy", "网易云"),
    QQ("tx", "QQ 音乐"),
    KUWO("kw", "免费音源"),
}

data class Song(
    val id: String,
    val name: String,
    val artist: String = "",
    val album: String = "",
    val source: String = MusicSource.LOCAL.key,
    val extra: String = "",
    val cover: String = "",
)

object MusicStore {
    private val gson = Gson()
    private val listType = object : TypeToken<MutableList<Song>>() {}.type

    fun favorites(context: Context): MutableList<Song> {
        val raw = toolsPrefs(context).getString("music_favorites", null) ?: return mutableListOf()
        return runCatching { gson.fromJson<MutableList<Song>>(raw, listType) }.getOrNull() ?: mutableListOf()
    }

    fun saveFavorites(context: Context, songs: List<Song>) {
        toolsPrefs(context).edit().putString("music_favorites", gson.toJson(songs)).apply()
    }

    fun history(context: Context): MutableList<Song> {
        val raw = toolsPrefs(context).getString("music_history", null) ?: return mutableListOf()
        return runCatching { gson.fromJson<MutableList<Song>>(raw, listType) }.getOrNull() ?: mutableListOf()
    }

    fun pushHistory(context: Context, song: Song) {
        val list = history(context)
        list.removeAll { it.id == song.id && it.source == song.source }
        list.add(0, song)
        while (list.size > 100) list.removeAt(list.lastIndex)
        toolsPrefs(context).edit().putString("music_history", gson.toJson(list)).apply()
    }
}

object MusicLibrary {
    fun scan(context: Context): List<Song> {
        val result = mutableListOf<Song>()
        val projection = arrayOf(
            android.provider.MediaStore.Audio.Media._ID,
            android.provider.MediaStore.Audio.Media.TITLE,
            android.provider.MediaStore.Audio.Media.ARTIST,
            android.provider.MediaStore.Audio.Media.ALBUM,
            android.provider.MediaStore.Audio.Media.DURATION,
        )
        runCatching {
            context.contentResolver.query(
                android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                "${android.provider.MediaStore.Audio.Media.IS_MUSIC} != 0",
                null,
                "${android.provider.MediaStore.Audio.Media.DATE_ADDED} DESC",
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(projection[0])
                val titleCol = cursor.getColumnIndexOrThrow(projection[1])
                val artistCol = cursor.getColumnIndexOrThrow(projection[2])
                val albumCol = cursor.getColumnIndexOrThrow(projection[3])
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(titleCol) ?: continue
                    result.add(
                        Song(
                            id = id.toString(),
                            name = name,
                            artist = cursor.getString(artistCol).orEmpty(),
                            album = cursor.getString(albumCol).orEmpty(),
                            source = MusicSource.LOCAL.key,
                            extra = android.content.ContentUris.withAppendedId(
                                android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                id
                            ).toString(),
                        )
                    )
                }
            }
        }
        return result
    }
}
