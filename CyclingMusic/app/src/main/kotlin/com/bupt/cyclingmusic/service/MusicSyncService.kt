package com.bupt.cyclingmusic.service

import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper

class MusicSyncService : Service(), MediaPlayer.OnCompletionListener, MediaPlayer.OnPreparedListener {
    private val binder = LocalBinder()
    private var mediaPlayer: MediaPlayer? = null
    private var currentTrack: String? = null
    private var isPlaying = false
    private var targetBpm = 0f

    // 防抖：候选档位与计时
    private var pendingTier: BpmTier? = null
    private val DEBOUNCE_MS = 5000L

    private val debounceHandler = Handler(Looper.getMainLooper())
    private var debounceRunnable: Runnable? = null

    private val userTracks = mutableMapOf<BpmTier, MutableList<String>>()

    enum class BpmTier { SLOW, MEDIUM, FAST }

    inner class LocalBinder : Binder() {
        fun getService(): MusicSyncService = this@MusicSyncService
        fun addTrack(tier: BpmTier, path: String) = this@MusicSyncService.addTrack(tier, path)
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            setOnCompletionListener(this@MusicSyncService)
            setOnPreparedListener(this@MusicSyncService)
        }

        BpmTier.values().forEach { userTracks[it] = mutableListOf() }
    }

    fun addTrack(tier: BpmTier, path: String) {
        userTracks[tier]?.add(path)
    }

    fun startPlayback(bpm: Float) {
        targetBpm = bpm
        val tier = bpmToTier(bpm)
        pendingTier = tier
        currentTrack = selectTrackByTier(tier)
        playCurrentTrack()
    }

    fun pausePlayback() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                isPlaying = false
            }
        }
    }

    fun resumePlayback() {
        val player = mediaPlayer ?: return
        if (currentTrack != null && !player.isPlaying) {
            try {
                player.start()
                isPlaying = true
            } catch (e: IllegalStateException) {
                playCurrentTrack()
            }
        } else if (currentTrack == null) {
            startPlayback(targetBpm)
        }
    }

    fun stopPlayback() {
        debounceRunnable?.let { debounceHandler.removeCallbacks(it) }
        mediaPlayer?.let {
            if (it.isPlaying || isPlaying) {
                it.stop()
            }
            it.reset()
        }
        isPlaying = false
    }

    fun updateBpm(bpm: Float) {
        targetBpm = bpm
        val newTier = bpmToTier(bpm)
        val currentTier = pendingTier

        if (newTier == currentTier) {
            // 档位没变，取消待切换
            debounceRunnable?.let { debounceHandler.removeCallbacks(it) }
            pendingTier = newTier
            return
        }

        // 档位变了，启动防抖计时
        if (pendingTier != newTier) {
            debounceRunnable?.let { debounceHandler.removeCallbacks(it) }
            pendingTier = newTier

            val runnable = Runnable {
                // 5秒后档位仍然是 newTier，才真正切歌
                if (pendingTier == newTier) {
                    val track = selectTrackByTier(newTier)
                    if (track != currentTrack) {
                        currentTrack = track
                        playCurrentTrack()
                    }
                }
            }
            debounceRunnable = runnable
            debounceHandler.postDelayed(runnable, DEBOUNCE_MS)
        }
    }

    fun getCurrentTier(): BpmTier = bpmToTier(targetBpm)

    private fun bpmToTier(bpm: Float): BpmTier = when {
        bpm < 60 -> BpmTier.SLOW
        bpm < 90 -> BpmTier.MEDIUM
        else -> BpmTier.FAST
    }

    private fun selectTrackByTier(tier: BpmTier): String {
        val tracks = userTracks[tier]
        if (!tracks.isNullOrEmpty()) {
            return tracks.random()
        }
        // 回退到硬编码路径（用户未配置时的占位）
        return when (tier) {
            BpmTier.SLOW -> "/storage/emulated/0/Music/slow_track.mp3"
            BpmTier.MEDIUM -> "/storage/emulated/0/Music/medium_track.mp3"
            BpmTier.FAST -> "/storage/emulated/0/Music/fast_track.mp3"
        }
    }

    private fun playCurrentTrack() {
        val player = mediaPlayer ?: return
        currentTrack?.let { track ->
            try {
                player.reset()
                player.setDataSource(track)
                player.prepareAsync()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onPrepared(mp: MediaPlayer?) {
        mp?.start()
        isPlaying = true
    }

    override fun onCompletion(mp: MediaPlayer?) {
        Handler(Looper.getMainLooper()).post { playCurrentTrack() }
    }

    override fun onDestroy() {
        super.onDestroy()
        debounceRunnable?.let { debounceHandler.removeCallbacks(it) }
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
