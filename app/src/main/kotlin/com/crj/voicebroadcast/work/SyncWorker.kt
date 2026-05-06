package com.crj.voicebroadcast.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.crj.voicebroadcast.data.Categories
import com.crj.voicebroadcast.data.EpisodeRepository
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * 每天 06:30 CST 后台同步：
 * 1. 拉每个 enabled Category 的 RSS 写 DB
 * 2. 把缺本地缓存的 episode 下载到 cache 目录
 *
 * Note: PeriodicWorkRequest 不能定时刻，只能按周期。
 * 用 initialDelay = next 06:30 - now 触发首次，之后每 24h 一次。
 */
class SyncWorker(
    appCtx: Context,
    params: WorkerParameters
) : CoroutineWorker(appCtx, params) {

    override suspend fun doWork(): Result = try {
        val repo = EpisodeRepository(applicationContext)
        for (cat in Categories.ALL.filter { it.enabled }) {
            repo.refresh(cat)
            // 仅下载最近 3 集（节省手表存储）
            val recent = repo.list(cat.id).take(3)
            for (ep in recent) {
                runCatching { repo.downloadIfNeeded(ep) }
            }
        }
        Result.success()
    } catch (t: Throwable) {
        Result.retry()
    }

    companion object {
        private const val NAME = "voice_broadcast_daily_sync"

        fun schedule(ctx: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<SyncWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setInitialDelay(initialDelayToNext0630Ms(), TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        private fun initialDelayToNext0630Ms(): Long {
            val now = Calendar.getInstance()
            val target = (now.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 6)
                set(Calendar.MINUTE, 30)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (target.timeInMillis <= now.timeInMillis) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }
            return target.timeInMillis - now.timeInMillis
        }
    }
}
