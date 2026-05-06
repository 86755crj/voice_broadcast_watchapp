package com.crj.voicebroadcast

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.crj.voicebroadcast.data.Categories
import com.crj.voicebroadcast.data.EpisodeRepository
import com.crj.voicebroadcast.ui.CategoryScreen
import com.crj.voicebroadcast.ui.HomeScreen
import com.crj.voicebroadcast.ui.PlayerScreen
import com.crj.voicebroadcast.ui.theme.VoiceBroadcastTheme
import com.crj.voicebroadcast.work.SyncWorker
import kotlinx.coroutines.launch

/**
 * 单 Activity，三屏切换用 sealed class state。
 * 不引 navigation library 减少 APK 体积。
 */
class MainActivity : ComponentActivity() {

    private sealed class Screen {
        object Home : Screen()
        data class Category(val id: String) : Screen()
        data class Player(val categoryId: String) : Screen()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 注册每日同步 worker
        SyncWorker.schedule(applicationContext)

        // 进 Activity 立即拉一次 RSS（第一次启动让用户秒看到内容）
        lifecycleScope.launch {
            val repo = EpisodeRepository(applicationContext)
            for (c in Categories.ALL.filter { it.enabled }) {
                runCatching { repo.refresh(c) }
            }
        }

        setContent {
            VoiceBroadcastTheme {
                var screen by remember { mutableStateOf<Screen>(Screen.Home) }
                when (val s = screen) {
                    is Screen.Home -> HomeScreen(onCategoryClick = { cat ->
                        screen = Screen.Category(cat.id)
                    })
                    is Screen.Category -> CategoryScreen(
                        categoryId = s.id,
                        onEpisodeClick = { _ -> screen = Screen.Player(s.id) }
                    )
                    is Screen.Player -> PlayerScreen(
                        categoryId = s.categoryId,
                        onListClick = { screen = Screen.Category(s.categoryId) }
                    )
                }
            }
        }
    }
}
