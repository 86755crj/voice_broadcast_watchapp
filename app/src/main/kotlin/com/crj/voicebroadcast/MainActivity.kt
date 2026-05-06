package com.crj.voicebroadcast

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.crj.voicebroadcast.data.Categories
import com.crj.voicebroadcast.data.EpisodeRepository
import com.crj.voicebroadcast.ui.CategoryScreen
import com.crj.voicebroadcast.ui.HomeScreen
import com.crj.voicebroadcast.ui.PlayerScreen
import com.crj.voicebroadcast.ui.theme.VoiceBroadcastTheme
import com.crj.voicebroadcast.work.SyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 单 Activity，三屏切换用 sealed class state。
 * 不引 navigation library 减少 APK 体积。
 *
 * 启动加载策略：
 *   - SyncWorker 后台每日同步注册（轻）
 *   - 首启 RSS 拉取放到 Dispatchers.IO，不阻塞 UI
 *   - 拉取期间 Home 屏显示 loading（米白底 + 酒红 ProgressIndicator）
 *   - 单 cat 抛错只打日志，不让整个 UI crash
 */
class MainActivity : ComponentActivity() {

    private sealed class Screen {
        object Home : Screen()
        data class Category(val id: String) : Screen()
        /**
         * Player 屏。
         * @param categoryId 当前分类
         * @param startGuid  从 CategoryScreen 点进来的具体集 guid；null 表示由 ViewModel
         *                   走 nextUnplayed/firstOrNull 自选起始集
         */
        data class Player(val categoryId: String, val startGuid: String? = null) : Screen()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 注册每日同步 worker（轻量，UI 线程 ok）
        SyncWorker.schedule(applicationContext)

        setContent {
            VoiceBroadcastTheme {
                var screen by remember { mutableStateOf<Screen>(Screen.Home) }
                var loading by remember { mutableStateOf(true) }

                // 首启异步拉一次 RSS（IO 线程，不阻塞 UI）
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            val repo = EpisodeRepository(applicationContext)
                            for (c in Categories.ALL.filter { it.enabled }) {
                                runCatching { repo.refresh(c) }
                                    .onFailure { Log.w(TAG, "refresh ${c.id} failed", it) }
                            }
                        }
                    }.onFailure { Log.e(TAG, "initial sync failed", it) }
                    loading = false
                }

                when (val s = screen) {
                    is Screen.Home -> {
                        // Home 屏返回 → 系统默认（退到桌面），不拦截
                        HomeScreen(
                            loading = loading,
                            onCategoryClick = { cat -> screen = Screen.Category(cat.id) }
                        )
                    }
                    is Screen.Category -> {
                        // Category 屏返回 → Home
                        BackHandler(enabled = true) { screen = Screen.Home }
                        CategoryScreen(
                            categoryId = s.id,
                            // 把点中的 episode guid 传给 Player，便于按断点续播
                            onEpisodeClick = { ep ->
                                screen = Screen.Player(s.id, startGuid = ep.guid)
                            }
                        )
                    }
                    is Screen.Player -> {
                        // Player 屏返回 → Category（保留 categoryId 以便回到同一列表）
                        BackHandler(enabled = true) {
                            screen = Screen.Category(s.categoryId)
                        }
                        PlayerScreen(
                            categoryId = s.categoryId,
                            startGuid = s.startGuid,
                            onListClick = { screen = Screen.Category(s.categoryId) }
                        )
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
