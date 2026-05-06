package com.crj.voicebroadcast.playback

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.crj.voicebroadcast.data.Episode
import com.crj.voicebroadcast.data.EpisodeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 播放屏 ViewModel。
 *
 * 职责：
 *   1. 连接 PlayerService 拿到 ExoPlayer 实例（bindService + LocalBinder）
 *   2. 维护当前 categoryId 下的 episode 列表 + 当前在播 episode
 *   3. 在 ExoPlayer 上挂 Listener，把 isPlaying / position / duration 桥接成 StateFlow 供 Compose 订阅
 *   4. Next 跳下一未听集；播完（STATE_ENDED）自动 mark played + advance
 *
 * 状态机：
 *   - episodes: 当前 categoryId 下所有 episode（按 pubDate desc）
 *   - currentEpisode: 当前在播
 *   - isPlaying / positionMs / durationMs: 跟 ExoPlayer 实时同步
 *   - hasNext: 是否还有"早于当前 + 未听"的集（决定 Next 按钮灰不灰）
 */
class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = EpisodeRepository(app)

    // -------- 公开状态 --------
    private val _episodes = MutableStateFlow<List<Episode>>(emptyList())
    val episodes: StateFlow<List<Episode>> = _episodes.asStateFlow()

    private val _currentEpisode = MutableStateFlow<Episode?>(null)
    val currentEpisode: StateFlow<Episode?> = _currentEpisode.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    // 播放倍速：0.8 / 1.0 / 1.25 / 1.5；UI 通过 setSpeed 改
    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    // 当前 episode 是否被加心；持久化在 SharedPreferences 里（避免动 Room schema 引发迁移）
    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    // 定时关闭剩余毫秒；null 表示未启用，0 表示已到期
    private val _sleepTimerRemainingMs = MutableStateFlow<Long?>(null)
    val sleepTimerRemainingMs: StateFlow<Long?> = _sleepTimerRemainingMs.asStateFlow()

    // -------- 内部 --------
    private var player: ExoPlayer? = null
    private var bound = false
    private var positionPollJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var categoryId: String? = null

    // 加心持久化：SharedPreferences key = guid, value = bool
    private val favPrefs by lazy {
        getApplication<Application>().getSharedPreferences(
            "voicebroadcast_favorites", Context.MODE_PRIVATE
        )
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            _isPlaying.value = playing
            if (playing) startPositionPolling() else stopPositionPolling()
        }

        override fun onPlaybackStateChanged(state: Int) {
            // 拿到 duration（prepare 完成后才有）
            val p = player ?: return
            if (state == Player.STATE_READY) {
                _durationMs.value = if (p.duration > 0) p.duration else 0L
            }
            if (state == Player.STATE_ENDED) {
                // 播完：mark played 并自动跳下一集
                onPlaybackEnded()
            }
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val local = service as? PlayerService.LocalBinder ?: return
            val p = local.getPlayer() ?: return
            player = p
            p.addListener(playerListener)
            // 反向同步一次当前状态
            _isPlaying.value = p.isPlaying
            if (p.duration > 0) _durationMs.value = p.duration
            _positionMs.value = p.currentPosition
            _ready.value = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            player?.removeListener(playerListener)
            player = null
            _ready.value = false
        }
    }

    /** Activity onStart 调；幂等 */
    fun bind(ctx: Context) {
        if (bound) return
        val intent = Intent(ctx, PlayerService::class.java)
        // startService 让服务能在 unbind 后存活继续后台播
        ctx.startService(intent)
        ctx.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        bound = true
    }

    /** Activity onStop 调 */
    fun unbind(ctx: Context) {
        if (!bound) return
        // 关键：unbind 前强制把当前位置写库一次，确保下次冷启能续播
        // （poll 里只在差值 > 30s 时存，最后一次差值不够的进度不能丢）
        flushPosition()
        try {
            player?.removeListener(playerListener)
            ctx.unbindService(serviceConnection)
        } catch (_: IllegalArgumentException) {
            // 已 unbind，忽略
        }
        bound = false
        stopPositionPolling()
    }

    /**
     * 把 ExoPlayer 当前 position 立即写入 DB（不等 30s 阈值）。
     * 用于 unbind / onStop 等可能马上被系统杀进程的关键时刻。
     */
    private fun flushPosition() {
        val p = player ?: return
        val cur = _currentEpisode.value ?: return
        val pos = p.currentPosition
        if (pos <= 0) return
        Log.i(TAG, "flushPosition: saving ${pos}ms for ${cur.title.take(40)}")
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.savePosition(cur.guid, pos) }
        }
    }

    /**
     * PlayerScreen 进入时调，加载该分类下的 episodes 并选中起始集。
     *
     * @param catId       分类 id
     * @param startGuid   优先匹配此 guid 作为起始集（用户从 CategoryScreen 点进来的那一集）；
     *                    匹配不到才回退到 nextUnplayed → firstOrNull
     *
     * 起始集选定后会从 DB 里读出 lastPositionMs 让 ExoPlayer seekTo，实现断点续播。
     */
    fun loadCategory(catId: String, startGuid: String? = null) {
        // 同一分类、同一起始集已加载过：幂等返回
        if (categoryId == catId && _episodes.value.isNotEmpty() &&
            (startGuid == null || _currentEpisode.value?.guid == startGuid)
        ) return
        categoryId = catId
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { repo.list(catId) }
            _episodes.value = list
            // 起始集优先级：startGuid 命中 > 未听最早集 > 最新一集
            val initial = startGuid?.let { g -> list.firstOrNull { it.guid == g } }
                ?: withContext(Dispatchers.IO) { repo.nextUnplayed(catId) }
                ?: list.firstOrNull()
            initial?.let { selectEpisode(it, autoplay = false) }
        }
    }

    /** 切到指定 episode；autoplay=true 时立即播 */
    private fun selectEpisode(ep: Episode, autoplay: Boolean) {
        _currentEpisode.value = ep
        _positionMs.value = ep.lastPositionMs
        _durationMs.value = (ep.durationSec * 1000L).coerceAtLeast(0L)
        // 同步加心状态（每集独立）
        _isFavorite.value = favPrefs.getBoolean(ep.guid, false)
        // 诊断日志：让用户能在 logcat 里看到断点续播是否生效
        Log.i(TAG, "resuming at ${ep.lastPositionMs}ms for ${ep.title.take(40)}")
        val p = player ?: return
        // 优先用本地缓存路径，没有则走网络
        val uri = ep.localPath?.takeIf { it.isNotEmpty() } ?: ep.enclosureUrl
        p.setMediaItem(MediaItem.fromUri(uri))
        p.prepare()
        if (ep.lastPositionMs > 0) p.seekTo(ep.lastPositionMs)
        // 应用当前倍速（切集会丢失上一首的 PlaybackParameters）
        p.setPlaybackSpeed(_playbackSpeed.value)
        p.playWhenReady = autoplay
    }

    /** 设置倍速：0.8 / 1.0 / 1.25 / 1.5 */
    fun setSpeed(speed: Float) {
        _playbackSpeed.value = speed
        player?.setPlaybackSpeed(speed)
    }

    /** 切换当前集的加心状态，写 SharedPreferences 持久化 */
    fun toggleFavorite() {
        val ep = _currentEpisode.value ?: return
        val now = !_isFavorite.value
        _isFavorite.value = now
        favPrefs.edit().putBoolean(ep.guid, now).apply()
    }

    /**
     * 启动定时关闭：minutes 分钟后暂停播放。
     * 重复调用会取消上一个 timer，重新计时。
     */
    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            _sleepTimerRemainingMs.value = null
            return
        }
        val totalMs = minutes * 60_000L
        _sleepTimerRemainingMs.value = totalMs
        sleepTimerJob = viewModelScope.launch {
            val deadline = System.currentTimeMillis() + totalMs
            while (true) {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) {
                    _sleepTimerRemainingMs.value = 0
                    player?.playWhenReady = false
                    flushPosition()
                    break
                }
                _sleepTimerRemainingMs.value = remaining
                delay(1000)
            }
        }
    }

    /** 取消定时关闭 */
    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimerRemainingMs.value = null
    }

    /** 中央按钮 toggle */
    fun togglePlayPause() {
        val p = player ?: return
        val ep = _currentEpisode.value ?: return
        // 没 mediaItem（player 在新 bind 后还没 setMediaItem）的话，先装载
        if (p.currentMediaItem == null) {
            val uri = ep.localPath?.takeIf { it.isNotEmpty() } ?: ep.enclosureUrl
            p.setMediaItem(MediaItem.fromUri(uri))
            p.prepare()
            if (ep.lastPositionMs > 0) p.seekTo(ep.lastPositionMs)
        }
        p.playWhenReady = !p.playWhenReady
    }

    /**
     * Next：跳到下一未听集。
     * 规则：在所有 episodes 中，pubDate 比当前更早的、未听的，取 pubDate 最大的（即"再早一点"的下一集）。
     */
    fun next() {
        val cur = _currentEpisode.value ?: return
        val candidate = _episodes.value
            .filter { !it.isPlayed && it.pubDate < cur.pubDate }
            .maxByOrNull { it.pubDate }
            ?: return
        selectEpisode(candidate, autoplay = true)
    }

    /** Next 按钮是否可用 */
    fun hasNext(): Boolean {
        val cur = _currentEpisode.value ?: return false
        return _episodes.value.any { !it.isPlayed && it.pubDate < cur.pubDate }
    }

    /** STATE_ENDED 触发：mark played + advance */
    private fun onPlaybackEnded() {
        val cur = _currentEpisode.value ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.markPlayed(cur.guid) }
            // 刷新本地列表以便 hasNext 重新计算
            categoryId?.let { _episodes.value = withContext(Dispatchers.IO) { repo.list(it) } }
            // 自动跳下一集（如果有）
            val candidate = _episodes.value
                .filter { !it.isPlayed && it.pubDate < cur.pubDate }
                .maxByOrNull { it.pubDate }
            if (candidate != null) {
                selectEpisode(candidate, autoplay = true)
            } else {
                _isPlaying.value = false
            }
        }
    }

    /**
     * 进度轮询：ExoPlayer 不会主动回调每秒进度，需自己 poll。
     * 仅在 isPlaying=true 时启动，避免空转耗电。
     */
    private fun startPositionPolling() {
        if (positionPollJob?.isActive == true) return
        positionPollJob = viewModelScope.launch {
            while (true) {
                val p = player ?: break
                _positionMs.value = p.currentPosition
                if (p.duration > 0) _durationMs.value = p.duration
                // 每 30s 持久化断点
                val cur = _currentEpisode.value
                if (cur != null && p.currentPosition - cur.lastPositionMs > 30_000) {
                    withContext(Dispatchers.IO) { repo.savePosition(cur.guid, p.currentPosition) }
                }
                // 80% 阈值 mark played（兜底，避免 STATE_ENDED 没触发）
                if (cur != null && !cur.isPlayed && p.duration > 0 &&
                    p.currentPosition >= p.duration * 0.8
                ) {
                    withContext(Dispatchers.IO) { repo.markPlayed(cur.guid) }
                }
                delay(500)
            }
        }
    }

    private fun stopPositionPolling() {
        positionPollJob?.cancel()
        positionPollJob = null
    }

    override fun onCleared() {
        // ViewModel 销毁前再保险一次：进程被杀前写库
        flushPosition()
        stopPositionPolling()
        // 注意：不主动 release player，因为 PlayerService 拥有它；ViewModel 只是引用持有者
        super.onCleared()
    }

    companion object {
        private const val TAG = "PlayerVM"
    }
}
