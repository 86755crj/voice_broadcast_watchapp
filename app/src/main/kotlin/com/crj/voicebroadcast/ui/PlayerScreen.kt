package com.crj.voicebroadcast.ui

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.graphics.Path
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
import com.crj.voicebroadcast.data.Categories
import com.crj.voicebroadcast.playback.PlayerViewModel
import com.crj.voicebroadcast.ui.theme.DarkCoffee
import com.crj.voicebroadcast.ui.theme.MutedCoffee
import com.crj.voicebroadcast.ui.theme.ParchmentBeige
import com.crj.voicebroadcast.ui.theme.WarmOrange
import com.crj.voicebroadcast.ui.theme.WineRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

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

    // V3.1 修复：用 BoxWithConstraints 取真实屏宽 dp（466x466 / density 2.0 ≈ 233dp），
    // 所有尺寸用相对比例（占 w 的百分比），保证 Galaxy Watch Ultra 等真机不被推出屏幕。
    // 标题日期改用 "M月d日" 短格式，避免 11 字符被截。
    val categoryName = remember(categoryId) { Categories.byId(categoryId)?.name ?: "" }
    val titleText = remember(current?.pubDate, categoryName) {
        val cur = current
        if (cur != null) {
            val day = SimpleDateFormat("M月d日", Locale.CHINA).format(Date(cur.pubDate))
            if (categoryName.isNotEmpty()) "$day $categoryName" else day
        } else {
            categoryName.ifEmpty { "暂无节目" }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ParchmentBeige),
        contentAlignment = Alignment.Center
    ) {
        // ---- 相对单位（基于真实屏宽 dp） ----
        val w = maxWidth                      // 圆屏宽 ≈ 233dp
        val ringStroke = w * 0.038f           // 进度环 stroke ≈ 9dp
        val centerBtnDiameter = w * 0.26f     // 中央按钮直径 ≈ 60dp（r ≈ 30dp，占 w 的 0.13）
        val iconBtnDiameter = w * 0.134f      // 4 icon 圆框直径 ≈ 31dp（r 占 w 的 0.067）
        val iconCenterDist = w * 0.36f        // 4 icon 距圆心 ≈ 84dp
        val titleSp = (w.value * 0.075f).sp   // 标题字号 ≈ 17sp
        val timeSp = (w.value * 0.052f).sp    // 时间字号 ≈ 12sp
        val titleOffsetY = -(w * 0.30f)       // 标题距中心向上 ≈ 70dp
        val timeOffsetY = w * 0.22f           // 时间距中心向下 ≈ 51dp
        val hintOffsetY = w * 0.30f           // 倍速/定时器提示位置
        // 4 icon 三角函数硬编码
        val sin210 = -0.5f; val cos210 = -0.866f
        val sin150 =  0.5f; val cos150 = -0.866f

        // ===== 1) 进度环（外圈）+ 进度小圆点 =====
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = ringStroke.toPx()
            val cx = size.width / 2f
            val cy = size.height / 2f
            // 半径：从外缘内缩，给小圆点留空间（外缘 - stroke - 4dp 边距）
            val ringR = size.minDimension / 2f - stroke - 4.dp.toPx()
            val topLeft = Offset(cx - ringR, cy - ringR)
            val sz = Size(ringR * 2f, ringR * 2f)
            // 底环（深咖 18% alpha）
            drawArc(
                color = DarkCoffee.copy(alpha = 0.18f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = sz,
                style = Stroke(width = stroke, cap = StrokeCap.Butt)
            )
            // 进度弧（暖橙）
            if (progress > 0f) {
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
            // 进度小圆点
            val angleDeg = -90f + 360f * progress
            val rad = angleDeg * (Math.PI / 180f).toFloat()
            val dotX = cx + ringR * cos(rad)
            val dotY = cy + ringR * sin(rad)
            val dotR = (w.toPx() * 0.027f)  // ≈ 6.3dp
            drawCircle(
                color = WarmOrange,
                radius = dotR,
                center = Offset(dotX, dotY)
            )
            drawCircle(
                color = ParchmentBeige,
                radius = dotR,
                center = Offset(dotX, dotY),
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // ===== 2) 标题（顶部）=====
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = titleOffsetY)
                .padding(horizontal = w * 0.12f),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = titleText,
                color = DarkCoffee,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = titleSp,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }

        // ===== 3) 中央播放按钮 =====
        val dl = downloadState
        if (dl is PlayerViewModel.DownloadState.Downloading) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "下载中",
                    color = WineRed,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = timeSp
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = formatBytes(dl.received, dl.total),
                    color = DarkCoffee,
                    fontFamily = FontFamily.Default,
                    fontSize = (w.value * 0.047f).sp
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(centerBtnDiameter)
                    .clip(CircleShape)
                    .background(WineRed)
                    .clickable(enabled = current != null && ready) { vm.togglePlayPause() },
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(centerBtnDiameter * 0.65f)) {
                    if (isPlaying) {
                        // 两条白色矩形 ||
                        val barW = size.width * 0.13f
                        val barH = size.height * 0.62f
                        val gap = size.width * 0.22f
                        val cx2 = size.width / 2f
                        val cy2 = size.height / 2f
                        val r = 2.dp.toPx()
                        drawRoundRect(
                            color = ParchmentBeige,
                            topLeft = Offset(cx2 - gap / 2f - barW, cy2 - barH / 2f),
                            size = Size(barW, barH),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r)
                        )
                        drawRoundRect(
                            color = ParchmentBeige,
                            topLeft = Offset(cx2 + gap / 2f, cy2 - barH / 2f),
                            size = Size(barW, barH),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r)
                        )
                    } else {
                        // 播放三角 ▶
                        val cx2 = size.width / 2f
                        val cy2 = size.height / 2f
                        val triW = size.width * 0.55f
                        val triH = size.height * 0.62f
                        val path = Path().apply {
                            moveTo(cx2 - triW / 2f + 3.dp.toPx(), cy2 - triH / 2f)
                            lineTo(cx2 + triW / 2f, cy2)
                            lineTo(cx2 - triW / 2f + 3.dp.toPx(), cy2 + triH / 2f)
                            close()
                        }
                        drawPath(path, color = ParchmentBeige)
                    }
                }
            }
        }

        // ===== 4) 时间显示（按钮下方）=====
        if (dl !is PlayerViewModel.DownloadState.Downloading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = timeOffsetY),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = formatTime(positionMs) + " / " + formatTime(durationMs),
                    color = DarkCoffee.copy(alpha = 0.7f),
                    fontFamily = FontFamily.Serif,
                    fontSize = timeSp
                )
            }
        }

        // 倍速 / 定时器小提示
        if (playbackSpeed != 1.0f || (sleepRemain != null && (sleepRemain ?: 0) > 0)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = hintOffsetY),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (playbackSpeed != 1.0f) {
                        Text(
                            text = "${playbackSpeed}x",
                            color = WarmOrange,
                            fontFamily = FontFamily.Default,
                            fontSize = (w.value * 0.039f).sp
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
                            fontSize = (w.value * 0.039f).sp
                        )
                    }
                }
            }
        }

        // ===== 5) 4 个功能按钮（相对单位定位）=====
        // dist = w * 0.36，4 个角度：270°(List)、90°(Next)、210°(音量)、150°(菜单)
        IconButton(
            offsetX = -iconCenterDist, offsetY = 0.dp,
            diameter = iconBtnDiameter,
            onClick = onListClick
        ) { drawListIcon() }

        IconButton(
            offsetX = iconCenterDist, offsetY = 0.dp,
            diameter = iconBtnDiameter,
            enabled = canNext,
            onClick = { vm.next() }
        ) { drawNextIcon() }

        IconButton(
            offsetX = iconCenterDist * sin210, offsetY = -iconCenterDist * cos210,
            diameter = iconBtnDiameter,
            onClick = { showVolume = true }
        ) { drawVolumeIcon() }

        IconButton(
            offsetX = iconCenterDist * sin150, offsetY = -iconCenterDist * cos150,
            diameter = iconBtnDiameter,
            onClick = { showMenu = true }
        ) { drawMenuIcon() }
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

/* -------- V3 icon 圆框 + 矢量绘制 -------- */

/**
 * V3.1 修复：icon 圆框直径由调用方按相对屏宽传入（占 w * 0.134，约 31dp@233dp 屏宽）。
 * offsetX/Y 是相对于父 Box 中心的位移（dp，已经是相对单位算好的）。
 *
 * 4 个按钮统一规格：
 * - 圆框 stroke 1.8dp WineRed alpha=0.4
 * - 内部 icon 用 Canvas drawScope 自绘（按 size 比例缩放）
 * - 全部酒红 #8B2635
 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.IconButton(
    offsetX: androidx.compose.ui.unit.Dp,
    offsetY: androidx.compose.ui.unit.Dp,
    diameter: androidx.compose.ui.unit.Dp,
    enabled: Boolean = true,
    onClick: () -> Unit,
    drawIcon: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .offset(x = offsetX, y = offsetY)
            .size(diameter)
            .clip(CircleShape)
            .border(1.8.dp, WineRed.copy(alpha = if (enabled) 0.4f else 0.15f), CircleShape)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(diameter)) {
            if (enabled) drawIcon() else drawIcon()
        }
    }
}

/** List icon：3 条水平线。按 Canvas 实际尺寸相对绘制（icon 自适应圆框直径）。 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawListIcon() {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val halfLen = size.width * 0.34f
    val gap = size.height * 0.22f
    val sw = size.width * 0.075f
    for (dy in listOf(-gap, 0f, gap)) {
        drawLine(
            color = WineRed,
            start = Offset(cx - halfLen, cy + dy),
            end = Offset(cx + halfLen, cy + dy),
            strokeWidth = sw,
            cap = StrokeCap.Round
        )
    }
}

/** Next icon：实心三角 ▶ + 右侧竖线 | */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawNextIcon() {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val triLeft = -size.width * 0.31f
    val triRight = size.width * 0.18f
    val triHalfH = size.height * 0.31f
    val triPath = Path().apply {
        moveTo(cx + triLeft, cy - triHalfH)
        lineTo(cx + triRight, cy)
        lineTo(cx + triLeft, cy + triHalfH)
        close()
    }
    drawPath(triPath, color = WineRed)
    val barX = size.width * 0.28f
    val barHalfH = size.height * 0.34f
    drawLine(
        color = WineRed,
        start = Offset(cx + barX, cy - barHalfH),
        end = Offset(cx + barX, cy + barHalfH),
        strokeWidth = size.width * 0.08f,
        cap = StrokeCap.Round
    )
}

/** 音量 icon：喇叭实心 path + 1 道波纹 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawVolumeIcon() {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val w = size.width
    val h = size.height
    val horn = Path().apply {
        moveTo(cx + (-0.34f) * w, cy + (-0.18f) * h)
        lineTo(cx + (-0.10f) * w, cy + (-0.18f) * h)
        lineTo(cx + 0.13f * w, cy + (-0.34f) * h)
        lineTo(cx + 0.13f * w, cy + 0.34f * h)
        lineTo(cx + (-0.10f) * w, cy + 0.18f * h)
        lineTo(cx + (-0.34f) * w, cy + 0.18f * h)
        close()
    }
    drawPath(horn, color = WineRed)
    val wave = Path().apply {
        moveTo(cx + 0.23f * w, cy + (-0.18f) * h)
        quadraticBezierTo(
            cx + 0.36f * w, cy,
            cx + 0.23f * w, cy + 0.18f * h
        )
    }
    drawPath(
        wave,
        color = WineRed,
        style = Stroke(width = w * 0.05f, cap = StrokeCap.Round)
    )
}

/** 菜单 icon：3 个实心圆点 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMenuIcon() {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val dotR = size.width * 0.09f
    val gap = size.width * 0.31f
    drawCircle(color = WineRed, radius = dotR, center = Offset(cx - gap, cy))
    drawCircle(color = WineRed, radius = dotR, center = Offset(cx, cy))
    drawCircle(color = WineRed, radius = dotR, center = Offset(cx + gap, cy))
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
