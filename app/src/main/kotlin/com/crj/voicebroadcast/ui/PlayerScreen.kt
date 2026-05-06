package com.crj.voicebroadcast.ui

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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.wear.compose.material.Text
import com.crj.voicebroadcast.data.Episode
import com.crj.voicebroadcast.data.EpisodeRepository
import com.crj.voicebroadcast.ui.theme.DarkCoffee
import com.crj.voicebroadcast.ui.theme.MutedCoffee
import com.crj.voicebroadcast.ui.theme.ParchmentBeige
import com.crj.voicebroadcast.ui.theme.WarmOrange
import com.crj.voicebroadcast.ui.theme.WineRed
import kotlinx.coroutines.delay

/**
 * Player 屏：
 * - 背景米白
 * - 圆形进度环：暖橙激活，浅咖底
 * - 中央大圆按钮（酒红），白色 PlayArrow
 * - 左下 "List" 文字按钮 → 回 CategoryScreen
 * - 右下 "Next" 文字按钮 → 跳下一集
 */
@Composable
fun PlayerScreen(
    categoryId: String,
    onListClick: () -> Unit
) {
    val ctx = LocalContext.current
    val repo = remember { EpisodeRepository(ctx) }

    var current by remember { mutableStateOf<Episode?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }

    // 启动时取下一集未听
    LaunchedEffect(categoryId) {
        current = repo.nextUnplayed(categoryId) ?: repo.list(categoryId).firstOrNull()
        durationMs = (current?.durationSec ?: 0) * 1000L
        positionMs = current?.lastPositionMs ?: 0L
    }

    // 模拟播放进度推进（实际应该用 ExoPlayer 回调；这里 UI 层 demo）
    LaunchedEffect(isPlaying, current?.guid) {
        while (isPlaying && current != null) {
            delay(1000)
            if (durationMs > 0) {
                positionMs = (positionMs + 1000).coerceAtMost(durationMs)
                if (positionMs >= durationMs * 0.8 && current?.isPlayed == false) {
                    current?.guid?.let { repo.markPlayed(it) }
                }
            }
        }
    }

    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

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

        // 中央：标题 + 大圆按钮
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
            // 大圆酒红按钮
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(WineRed)
                    .clickable(enabled = current != null) { isPlaying = !isPlaying },
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
        // 右下 Next
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 14.dp, bottom = 14.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            Text(
                text = "Next",
                color = WineRed,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                modifier = Modifier.clickable {
                    // 标记已听 + 切下一集
                    current?.guid?.let {
                        // 在 Composable 里不能直接 suspend，简单方案：reset state，
                        // 真实跳过逻辑在 PlayerService 或 ViewModel
                    }
                }
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
