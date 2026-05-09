package com.crj.voicebroadcast.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Text
import com.crj.voicebroadcast.data.Categories
import com.crj.voicebroadcast.data.Episode
import com.crj.voicebroadcast.data.EpisodeRepository
import com.crj.voicebroadcast.ui.theme.DarkCoffee
import com.crj.voicebroadcast.ui.theme.MutedCoffee
import com.crj.voicebroadcast.ui.theme.ParchmentBeige
import com.crj.voicebroadcast.ui.theme.WarmOrange
import com.crj.voicebroadcast.ui.theme.WineRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CategoryScreen(
    categoryId: String,
    onEpisodeClick: (Episode) -> Unit
) {
    val ctx = LocalContext.current
    val repo = remember { EpisodeRepository(ctx) }
    val cat = Categories.byId(categoryId)
    val episodes by (cat?.let { repo.observe(it.id) } ?: flowOf(emptyList()))
        .collectAsState(initial = emptyList())

    val df = remember { SimpleDateFormat("MM/dd", Locale.US) }
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ParchmentBeige)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 杂志栏目头：点击触发 RSS 重新拉取
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WineRed)
                    .clickable(enabled = !refreshing) {
                        if (cat != null && !refreshing) {
                            refreshing = true
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) { repo.refresh(cat) }
                                } catch (_: Exception) {
                                    // 静默失败：网络错误不打断 UI
                                }
                                // 至少展示 0.5s 的视觉反馈，避免一闪而过
                                delay(500)
                                refreshing = false
                            }
                        }
                    }
                    .semantics { role = Role.Button }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                if (refreshing) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            indicatorColor = WarmOrange,
                            trackColor = ParchmentBeige,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            text = "刷新中…",
                            color = ParchmentBeige,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    Text(
                        text = cat?.name ?: "?",
                        color = ParchmentBeige,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            if (episodes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "暂无节目，等待 06:30 同步",
                        color = MutedCoffee,
                        fontFamily = FontFamily.Default,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    items(episodes) { ep ->
                        EpisodeRow(ep, df) { onEpisodeClick(ep) }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    ep: Episode,
    df: SimpleDateFormat,
    onClick: () -> Unit
) {
    val titleColor = if (ep.isPlayed) MutedCoffee else DarkCoffee
    val accent = if (ep.isPlayed) MutedCoffee else WarmOrange

    // 圆屏适配：整个 Row 在水平方向居中，避免左侧文字被表盘弧度切掉
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = df.format(Date(ep.pubDate)),
                color = accent,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
            Text(
                text = ep.title,
                color = titleColor,
                fontFamily = FontFamily.Serif,
                fontSize = 13.sp,
                maxLines = 2,
                textAlign = TextAlign.Center
            )
        }
    }
}
