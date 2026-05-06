package com.crj.voicebroadcast.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
 * Player 屏：
 * - 背景米白
 * - 圆形进度环：暖橙激活，浅咖底（跟 ExoPlayer 真实 position 同步）
 * - 中央大圆按钮（酒红），白色 PlayArrow，toggle 真生效
 * - 左下 "List" 文字按钮 → 回 CategoryScreen
 * - 右下 "Next" 文字按钮 → 跳下一未听集（无下一集时灰掉）
 *
 * 数据流：
 *   Compose ←(StateFlow)— PlayerViewModel ←(Player.Listener)— ExoPlayer ←(Binder)— PlayerService
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

    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val canNext = current?.let { cur ->
        episodes.any { !it.isPlayed && it.pubDate < cur.pubDate }
    } ?: false

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ParchmentBeige),
        contentAlignment = Alignment.Center
    ) {
        // 进度环
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 36.dp)
            )
            Spacer(Modifier.height(8.dp))
            // 大圆酒红按钮（toggle 真接 ExoPlayer）
            Box(
                modifier = Modifier
                    .size(56.dp)
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
                    fontSize = 22.sp
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = formatTime(positionMs) + " / " + formatTime(durationMs),
                color = MutedCoffee,
                fontFamily = FontFamily.Default,
                fontSize = 10.sp
            )
        }

        // 左下 List
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 14.dp, bottom = 14.dp),
            contentAlignment = Alignment.BottomStart
        ) {
            Text(
                text = "List",
                color = WineRed,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                modifier = Modifier.clickable { onListClick() }
            )
        }
        // 右下 Next（无下一集时灰）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 14.dp, bottom = 14.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            Text(
                text = "Next",
                color = if (canNext) WineRed else MutedCoffee,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                modifier = Modifier.clickable(enabled = canNext) { vm.next() }
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val s = ms / 1000
    val mm = s / 60
    val ss = s % 60
    return "%02d:%02d".format(mm, ss)
}
