package com.crj.voicebroadcast.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaStyleNotificationHelper
import com.crj.voicebroadcast.R

/**
 * MediaSessionService 形态的播放服务。
 *
 * 双重身份：
 *   1. MediaSessionService 让 Wear OS 的 "正在播放" 卡片识别得到（onGetSession）。
 *   2. 同进程通过 LocalBinder 暴露 ExoPlayer 实例给 UI 层（ViewModel 直接拿到 player 控制 + 监听事件）。
 *
 * 选用 bindService + LocalBinder 而不是 MediaController：
 *   - 单进程同包，无需 IPC，简化代码
 *   - ViewModel 能直接 attach Player.Listener 拿到 onIsPlayingChanged / onPositionDiscontinuity / STATE_ENDED
 *   - 服务生命周期由 startService（让它常驻播放）+ bindService（控制信道）共同管理
 *
 * Foreground 化（关键）：
 *   Wear OS 6 在 app idle 检测时会 "Stopping service due to app idle" 把后台 service kill 掉，
 *   表现为播 1 分钟后突然 stall。startForeground + MediaStyle 通知能让系统知道这是用户感知的播放任务，
 *   不会被 idle kill。同时也是 FOREGROUND_SERVICE_MEDIA_PLAYBACK 权限要求的合规调用。
 */
class PlayerService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var session: MediaSession? = null
    private val binder = LocalBinder()

    /** 同进程 Binder：UI 层拿到这个就拿到 ExoPlayer 控制权 */
    inner class LocalBinder : Binder() {
        fun getPlayer(): ExoPlayer? = player
    }

    override fun onCreate() {
        super.onCreate()
        // Wear OS 6 在 LTE 下 ExoPlayer 默认 buffer 偏小（50s min），LTE 抖动 → buffer 用尽 →
        // 进入 BUFFERING 状态，过短 stall 还可能被框架升级为 STATE_ENDED 误判。
        // 把 min/max 拉到 3min/5min，让长 mp3（~40min）在 LTE 上有足够缓冲。
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs */                  180_000,
                /* maxBufferMs */                  300_000,
                /* bufferForPlaybackMs */            2_500,
                /* bufferForPlaybackAfterRebufferMs */ 5_000
            )
            .build()
        val p = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .build()
        player = p
        val s = MediaSession.Builder(this, p).build()
        session = s

        // 必须在 service 创建后立刻 startForeground，避免 Wear OS idle kill
        ensureChannel()
        startForeground(NOTIFICATION_ID, buildNotification(s))
    }

    /**
     * onBind 优先级：
     *   - MediaSessionService 框架在某些 action（androidx.media3.session.MediaSessionService）下需要框架默认实现
     *   - 我们自定义 action 时返回 LocalBinder
     * 实际上同进程 bindService(Intent(this, PlayerService::class.java), conn, ...) 会走这里
     */
    override fun onBind(intent: Intent?): IBinder? {
        // 走系统媒体会话流程时让父类处理
        if (intent?.action == "androidx.media3.session.MediaSessionService") {
            return super.onBind(intent)
        }
        return binder
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 任务移除时如果没在播就关掉，避免后台空跑
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

    // -------- 通知 --------

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    "播放",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "晨练简报正在播放"
                    setShowBadge(false)
                }
                nm.createNotificationChannel(ch)
            }
        }
    }

    private fun buildNotification(session: MediaSession): android.app.Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("晨练简报")
            .setContentText("正在播放")
            .setSmallIcon(R.drawable.ic_launcher)
            .setStyle(MediaStyleNotificationHelper.MediaStyle(session))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "voice_broadcast_playback"
        private const val NOTIFICATION_ID = 1
    }
}
