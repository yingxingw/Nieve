package com.yunx.app.tools.music

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

object MusicPlayer {
    private var player: ExoPlayer? = null
    val queue = mutableStateListOf<Song>()
    var index by mutableIntStateOf(-1)
        private set
    var current by mutableStateOf<Song?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var preparing by mutableStateOf(false)
        private set
    var error by mutableStateOf("")
        private set
    var endedTick by mutableIntStateOf(0)
        private set
    var durationMs by mutableStateOf(0L)
        private set

    fun ensure(context: Context): ExoPlayer {
        player?.let { return it }
        val exo = ExoPlayer.Builder(context.applicationContext).build().apply {
            playWhenReady = false
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    this@MusicPlayer.isPlaying = playing
                }

                override fun onPlaybackStateChanged(state: Int) {
                    preparing = state == Player.STATE_BUFFERING
                    if (state == Player.STATE_READY) {
                        durationMs = duration.coerceAtLeast(0)
                    }
                    if (state == Player.STATE_ENDED) {
                        endedTick++
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    this@MusicPlayer.error = error.message ?: "播放失败"
                    preparing = false
                }
            })
        }
        player = exo
        return exo
    }

    fun setQueue(songs: List<Song>, startIndex: Int) {
        queue.clear()
        queue.addAll(songs)
        index = startIndex.coerceIn(0, (songs.size - 1).coerceAtLeast(0))
        current = queue.getOrNull(index)
        error = ""
    }

    fun playUrl(context: Context, url: String, overrideSong: Song? = null) {
        val song = overrideSong ?: current ?: return
        if (overrideSong != null) current = overrideSong
        val exo = ensure(context)
        error = ""
        preparing = true
        durationMs = 0
        exo.setMediaItem(
            MediaItem.Builder()
                .setUri(url)
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(song.name)
                        .setArtist(song.artist)
                        .build()
                )
                .build()
        )
        exo.prepare()
        exo.playWhenReady = true
    }

    fun toggle() {
        val exo = player ?: return
        if (exo.isPlaying) exo.pause() else exo.play()
    }

    fun pause() {
        player?.pause()
    }

    fun seekTo(ms: Long) {
        player?.seekTo(ms)
    }

    fun position(): Long = player?.currentPosition ?: 0L

    fun stop() {
        player?.stop()
        player?.clearMediaItems()
        current = null
        index = -1
        queue.clear()
        isPlaying = false
        preparing = false
        error = ""
        durationMs = 0
    }

    fun select(index: Int): Song? {
        if (index !in queue.indices) return null
        this.index = index
        current = queue[index]
        return current
    }
}
