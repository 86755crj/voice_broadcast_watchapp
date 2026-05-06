package com.crj.voicebroadcast.ui

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.Text
import com.crj.voicebroadcast.playback.PlayerViewModel
import com.crj.voicebroadcast.ui.theme.DarkCoffee
import com.crj.voicebroadcast.ui.theme.MutedCoffee
import com.crj.voicebroadcast.ui.theme.ParchmentBeige
import com.crj.voicebroadcast.ui.theme.WarmOrange
import com.crj.voicebroadcast.ui.theme.WineRed

/**
 * Player 屏（圆形 Wear 466x466 布局）。
 *
 * 控件位置（中心对称、不挡进度环）：
 *   - 中央：标题 + 大圆酒红播放按钮 + 时间
 *   - 左中：文字 "List" → 回 CategoryScreen
 *   - 右中：文字 "Next" → 跳下一未听集（无下一集时灰）
 *   - 左下：音量按钮 → 弹滑动条 0-100%
 *   - 中下：三点菜单 → 弹倍速 / 加心 / 定时关闭
 *
 * 数据流：Compose ←(StateFlow)— PlayerViewModel ←(Player.Listener)— ExoPlayer ←(Binder)— PlayerService
 */
@Composable
fun PlayerScreen(
    categoryId: String,
    startGuid: String? = null,
    onListClick: () -> Unit,
    vm: PlayerViewModel = viewModel()
) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 生命周期感知地 bind/unbind PlayerService
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> vm.bind(ctx)
                Lifecycle.Event.ON_STOP -> vm.unbind(ctx)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 服务连上后再加载分类（loadCategory 内幂等）
    // 把 startGuid 传下去，让 ViewModel 优先选用户点中的那一集，从断点续播
    val ready by vm.ready.collectAsState()
    LaunchedEffect(categoryId, startGuid, ready) {
        if (ready) vm.loadCategory(categoryId, startGuid)
    }

    val current by vm.currentEpisode.collectAsState()
    val isPlaying by vm.isPlaying.collectAsState()
    val positionMs by vm.positionMs.collectAsState()
    val durationMs by vm.durationMs.collectAsState()
    val episodes by vm.episodes.collectAsState()
    val playbackSpeed by vm.playbackSpeed.collectAsState()
    val isFavorite by vm.isFavorite.collectAsState()
    val sleepRemain by vm.sleepTimerRemainingMs.collectAsState()
    val downloadState by vm.downloadState.collectAsState()

    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val canNext = current?.let { cur ->
        episodes.any { !it.isPlayed && it.pubDate < cur.pubDate }
    } ?: false

    // 弹窗状态
    var showVolume by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ParchmentBeige),
        contentAlignment = Alignment.Center
    ) {
        // 进度环（留出 8dp 安全边距，给四角控件让位）
        Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            val stroke = 6.dp.toPx()
            val sz = Size(size.minDimension - stroke, size.minDimension - stroke)
            val topLeft = Offset((size.width - sz.width) / 2, (size.height - sz.height) / 2)
            // 底环
            drawArc(
                color = MutedCoffee.copy(alpha = 0.3f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = sz,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            // 进度
            drawArc(
                color = WarmOrange,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = topLeft,
                size = sz,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }

        // 中央：标题 + 大圆按钮 + 时间
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = current?.title?.take(18) ?: "暂无节目",
                color = DarkCoffee,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 70.dp)
            )
            Spacer(Modifier.height(6.dp))
            // 下载中：显示进度文字代替播放按钮；下载完成或失败 fallback 后回到正常按钮
            val dl = downloadState
            if (dl is PlayerViewModel.DownloadState.Downloading) {
                Text(
                    text = "下载中",
                    color = WineRed,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = formatBytes(dl.received, dl.total),
                    color = DarkCoffee,
                    fontFamily = FontFamily.Default,
                    fontSize = 10.sp
                )
            } else {
                // 大圆酒红按钮（toggle 真接 ExoPlayer）
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(WineRed)
                        .clickable(enabled = current != null && ready) { vm.togglePlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isPlaying) "II" else "▶",
                        color = ParchmentBeige,
                        fontFamily = FontFamily.Default,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = formatTime(positionMs) + " / " + formatTime(durationMs),
                    color = MutedCoffee,
                    fontFamily = FontFamily.Default,
                    fontSize = 9.sp
                )
            }
            // 倍速 / 定时器小提示（如果非 1.0x 或 sleep 在跑）
            if (playbackSpeed != 1.0f || (sleepRemain != null && (sleepRemain ?: 0) > 0)) {
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (playbackSpeed != 1.0f) {
                        Text(
                            text = "${playbackSpeed}x",
                            color = WarmOrange,
                            fontFamily = FontFamily.Default,
                            fontSize = 8.sp
                        )
                    }
                    if (playbackSpeed != 1.0f && sleepRemain != null && (sleepRemain ?: 0) > 0) {
                        Spacer(Modifier.width(6.dp))
                    }
                    sleepRemain?.takeIf { it > 0 }?.let {
                        Text(
                            text = "⏰" + formatMinutes(it),
                            color = WineRed,
                            fontFamily = FontFamily.Default,
                            fontSize = 8.sp
                        )
                    }
                }
            }
        }

        // 左中 List —— 圆屏左中点空间最大，文字按钮垂直居中
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = "List",
                color = WineRed,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onListClick() }
                    .padding(4.dp)
            )
        }
        // 右中 Next（无下一集时灰）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 10.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Text(
                text = "Next",
                color = if (canNext) WineRed else MutedCoffee,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(enabled = canNext) { vm.next() }
                    .padding(4.dp)
            )
        }

        // 左下：音量按钮（喇叭符号）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 36.dp, bottom = 14.dp),
            contentAlignment = Alignment.BottomStart
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .border(1.dp, WineRed, CircleShape)
                    .clickable { showVolume = true },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "♪",
                    color = WineRed,
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }

        // 中下：三点菜单
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 14.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .border(1.dp, WineRed, CircleShape)
                    .clickable { showMenu = true },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "⋯",
                    color = WineRed,
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }

    // ---- 弹窗层 ----
    if (showVolume) {
        VolumeDialog(onDismiss = { showVolume = false })
    }
    if (showMenu) {
        ActionMenuDialog(
            isFavorite = isFavorite,
            currentSpeed = playbackSpeed,
            onDismiss = { showMenu = false },
            onPickSpeed = { showMenu = false; showSpeed = true },
            onToggleFavorite = { vm.toggleFavorite(); showMenu = false },
            onPickSleep = { showMenu = false; showSleep = true }
        )
    }
    if (showSpeed) {
        SpeedDialog(
            current = playbackSpeed,
            onPick = { sp -> vm.setSpeed(sp); showSpeed = false },
            onDismiss = { showSpeed = false }
        )
    }
    if (showSleep) {
        SleepTimerDialog(
            running = (sleepRemain ?: 0) > 0,
            onPick = { mins ->
                if (mins == 0) vm.cancelSleepTimer() else vm.setSleepTimer(mins)
                showSleep = false
            },
            onDismiss = { showSleep = false }
        )
    }
}

/* -------- 弹窗 Composables -------- */

/** 音量弹窗：直接读写系统媒体音量。竖向滑动小条 + 当前百分比文字。 */
@Composable
private fun VolumeDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val am = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVol = remember { am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var vol by remember { mutableStateOf(am.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    val pct = (vol * 100 / maxVol)

    DialogScrim(onDismiss = onDismiss) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "音量",
                color = DarkCoffee,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "$pct%",
                color = WarmOrange,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
            )
            Spacer(Modifier.height(8.dp))
            // 自绘横向滑动条：tap / drag 任意点设音量为该位置对应比例
            Box(
                modifier = Modifier
                    .width(140.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MutedCoffee.copy(alpha = 0.25f))
                    .pointerInput(maxVol) {
                        // awaitPointerEventScope 收任意指针变化，按 x / width 算比例
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val pos = event.changes.firstOrNull()?.position ?: continue
                                val ratio = (pos.x / size.width).coerceIn(0f, 1f)
                                val target = (ratio * maxVol).toInt().coerceIn(0, maxVol)
                                if (target != vol) {
                                    vol = target
                                    am.setStreamVolume(
                                        AudioManager.STREAM_MUSIC, target, 0
                                    )
                                }
                            }
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width * (vol.toFloat() / maxVol)
                    drawRect(
                        color = WarmOrange,
                        size = Size(w, size.height)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            // -/+ 微调（drag 在小屏可能不准，给点击补偿）
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniButton("−") {
                    vol = (vol - 1).coerceAtLeast(0)
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0)
                }
                MiniButton("✓") { onDismiss() }
                MiniButton("+") {
                    vol = (vol + 1).coerceAtMost(maxVol)
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0)
                }
            }
        }
    }
}

/** 三点菜单：列出三个动作 */
@Composable
private fun ActionMenuDialog(
    isFavorite: Boolean,
    currentSpeed: Float,
    onDismiss: () -> Unit,
    onPickSpeed: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPickSleep: () -> Unit
) {
    DialogScrim(onDismiss = onDismiss) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "操作",
                color = DarkCoffee,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(8.dp))
            MenuRow(label = "倍速 ${currentSpeed}x", onClick = onPickSpeed)
            Spacer(Modifier.height(4.dp))
            MenuRow(
                label = if (isFavorite) "已加心 ♥" else "加心 ♡",
                accent = if (isFavorite) WineRed else null,
                onClick = onToggleFavorite
            )
            Spacer(Modifier.height(4.dp))
            MenuRow(label = "定时关闭…", onClick = onPickSleep)
        }
    }
}

@Composable
private fun SpeedDialog(
    current: Float,
    onPick: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    DialogScrim(onDismiss = onDismiss) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "倍速",
                color = DarkCoffee,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(8.dp))
            for (sp in listOf(0.8f, 1.0f, 1.25f, 1.5f)) {
                MenuRow(
                    label = "${sp}x",
                    accent = if (sp == current) WarmOrange else null,
                    onClick = { onPick(sp) }
                )
                Spacer(Modifier.height(3.dp))
            }
        }
    }
}

@Composable
private fun SleepTimerDialog(
    running: Boolean,
    onPick: (Int) -> Unit,    // 0 = 取消
    onDismiss: () -> Unit
) {
    DialogScrim(onDismiss = onDismiss) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "定时关闭",
                color = DarkCoffee,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(6.dp))
            for (m in listOf(5, 15, 30)) {
                MenuRow(label = "${m} 分钟", onClick = { onPick(m) })
                Spacer(Modifier.height(3.dp))
            }
            if (running) {
                Spacer(Modifier.height(2.dp))
                MenuRow(label = "取消定时", accent = WineRed, onClick = { onPick(0) })
            }
        }
    }
}

/** 全屏半透明遮罩 + 中央内容；点遮罩外即关闭 */
@Composable
private fun DialogScrim(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkCoffee.copy(alpha = 0.55f))
            .clickable(enabled = true) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        // 内容卡片：米白底圆角，内部点击不冒泡到 scrim
        Box(
            modifier = Modifier
                .padding(24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ParchmentBeige)
                .border(1.5.dp, WineRed, RoundedCornerShape(12.dp))
                .clickable(enabled = false) {} // 拦截点击，让外部 scrim 不收到
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

@Composable
private fun MenuRow(
    label: String,
    accent: androidx.compose.ui.graphics.Color? = null,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = accent ?: DarkCoffee,
            fontFamily = FontFamily.Serif,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun MiniButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(WineRed)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = ParchmentBeige,
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}

/* -------- 工具函数 -------- */

private fun formatTime(ms: Long): String {
    val s = ms / 1000
    val mm = s / 60
    val ss = s % 60
    return "%02d:%02d".format(mm, ss)
}

private fun formatMinutes(ms: Long): String {
    val totalSec = ms / 1000
    val mm = totalSec / 60
    val ss = totalSec % 60
    return "%d:%02d".format(mm, ss)
}

/** 把字节数格式化成 "12.3 / 36.5 MB"，total<=0 时只显示 received */
private fun formatBytes(received: Long, total: Long): String {
    val mb = 1024.0 * 1024.0
    val r = received / mb
    return if (total > 0) {
        val t = total / mb
        "%.1f / %.1f MB".format(r, t)
    } else {
        "%.1f MB".format(r)
    }
}
