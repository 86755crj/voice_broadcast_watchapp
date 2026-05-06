package com.crj.voicebroadcast.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 业务封装：把 RSS、Room、本地缓存串起来。
 * - refresh: 拉 RSS 写 DB
 * - downloadMissing: 把没本地缓存的 episode 下载到 cache 目录
 */
class EpisodeRepository(private val ctx: Context) {

    private val dao = AppDatabase.get(ctx).episodes()
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun observe(catId: String): Flow<List<Episode>> = dao.observeByCategory(catId)

    suspend fun refresh(category: Category) {
        val fresh = RssClient.fetch(category)
        if (fresh.isNotEmpty()) dao.insertAll(fresh)
    }

    suspend fun nextUnplayed(catId: String): Episode? = dao.nextUnplayed(catId)

    suspend fun list(catId: String): List<Episode> = dao.listByCategory(catId)

    suspend fun markPlayed(guid: String) = dao.markPlayed(guid)

    suspend fun savePosition(guid: String, posMs: Long) = dao.updatePosition(guid, posMs)

    suspend fun byGuid(guid: String): Episode? = dao.byGuid(guid)

    /** 下载到 /sdcard/Android/data/<pkg>/files/cache/<catId>/<safeName>.mp3 */
    suspend fun downloadIfNeeded(ep: Episode): String? = withContext(Dispatchers.IO) {
        if (!ep.localPath.isNullOrEmpty() && File(ep.localPath).exists()) return@withContext ep.localPath
        val baseDir = File(ctx.getExternalFilesDir(null), "cache/${ep.categoryId}").apply { mkdirs() }
        val safeName = ep.guid.hashCode().toString().replace("-", "n") + ".mp3"
        val target = File(baseDir, safeName)

        if (!target.exists()) {
            val req = Request.Builder().url(ep.enclosureUrl).build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body ?: return@withContext null
                target.outputStream().use { os -> body.byteStream().copyTo(os) }
            }
        }
        dao.setLocalPath(ep.guid, target.absolutePath)
        target.absolutePath
    }

    /**
     * 带进度回调的下载（PlayerScreen 进入时用）。
     * - 已存在直接返回路径，progress(total,total) 回调一次表示就绪
     * - 边下边写入临时文件 .part，完成后 rename，断电不会留半截
     * - progress 回调单位字节；total<=0 表示服务器没给 Content-Length
     * - 失败返回 null（PlayerScreen 会 fallback 到流式 url）
     */
    suspend fun downloadWithProgress(
        ep: Episode,
        progress: (received: Long, total: Long) -> Unit
    ): String? = withContext(Dispatchers.IO) {
        if (!ep.localPath.isNullOrEmpty() && File(ep.localPath).exists()) {
            val len = File(ep.localPath).length()
            progress(len, len)
            return@withContext ep.localPath
        }
        val baseDir = File(ctx.getExternalFilesDir(null), "cache/${ep.categoryId}").apply { mkdirs() }
        val safeName = ep.guid.hashCode().toString().replace("-", "n") + ".mp3"
        val target = File(baseDir, safeName)
        val tmp = File(baseDir, "$safeName.part")

        if (target.exists()) {
            val len = target.length()
            progress(len, len)
            dao.setLocalPath(ep.guid, target.absolutePath)
            return@withContext target.absolutePath
        }

        try {
            val req = Request.Builder().url(ep.enclosureUrl).build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body ?: return@withContext null
                val total = body.contentLength()
                tmp.outputStream().use { os ->
                    body.byteStream().use { input ->
                        val buf = ByteArray(64 * 1024)
                        var received = 0L
                        var lastReport = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            os.write(buf, 0, n)
                            received += n
                            // 节流：每 256KB 才回调一次，避免 UI 抖
                            if (received - lastReport > 256 * 1024) {
                                progress(received, total)
                                lastReport = received
                            }
                        }
                        progress(received, total)
                    }
                }
            }
            if (!tmp.renameTo(target)) {
                tmp.delete()
                return@withContext null
            }
            dao.setLocalPath(ep.guid, target.absolutePath)
            target.absolutePath
        } catch (e: Exception) {
            tmp.delete()
            null
        }
    }
}
