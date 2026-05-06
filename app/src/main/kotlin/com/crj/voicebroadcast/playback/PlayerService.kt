package com.crj.voicebroadcast.playback

import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * MediaSessionService 形态的播放服务，让 Wear OS 的"正在播放"卡片识别得到。
 * Wear OS 6 标准做法。
 */
class PlayerService : MediaSessionService() {
    private var player: ExoPlayer? = null
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val p = ExoPlayer.Builder(this).build()
        player = p
        session = MediaSession.Builder(this, p).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 任务移除时停止播放，避免后台空跑
        if (player?.playWhenReady == false) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        session?.run { release() }
        player?.release()
        session = null
        player = null
        super.onDestroy()
    }

    /** 外部 API（同进程调用）：加载 url 并播 */
    fun playUrl(url: String) {
        val p = player ?: return
        p.setMediaItem(MediaItem.fromUri(url))
        p.prepare()
        p.playWhenReady = true
    }
}
