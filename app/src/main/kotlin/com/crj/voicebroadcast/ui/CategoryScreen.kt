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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import com.crj.voicebroadcast.data.Categories
import com.crj.voicebroadcast.data.Episode
import com.crj.voicebroadcast.data.EpisodeRepository
import com.crj.voicebroadcast.ui.theme.DarkCoffee
import com.crj.voicebroadcast.ui.theme.MutedCoffee
import com.crj.voicebroadcast.ui.theme.ParchmentBeige
import com.crj.voicebroadcast.ui.theme.WarmOrange
import com.crj.voicebroadcast.ui.theme.WineRed
import kotlinx.coroutines.flow.flowOf
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ParchmentBeige)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 杂志栏目头
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WineRed)
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = cat?.name ?: "?",
                    color = ParchmentBeige,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            if (episodes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "暂无节目，等待 06:30 同步",
                        color = MutedCoffee,
                        fontFamily = FontFamily.Default,
                        fontSize = 12.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = 6.dp)
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 6.dp)
    ) {
        Column {
            Text(
                text = df.format(Date(ep.pubDate)),
                color = accent,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
            Text(
                text = ep.title,
                color = titleColor,
                fontFamily = FontFamily.Serif,
                fontSize = 13.sp,
                maxLines = 2
            )
        }
    }
}
