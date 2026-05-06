package com.crj.voicebroadcast.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Episode 实体。一条 RSS <item> 对应一条记录。
 * - guid 用 RSS 的 <guid> 或 enclosure URL 作为主键，保证幂等
 * - localPath 是 sync worker 下载到的本地 mp3 路径（null = 还没下/不缓存）
 * - playedAt 是上次"听完 80%"的时间戳；null 表示从未听过
 * - lastPositionMs 用于断点续播
 */
@Entity(tableName = "episodes")
data class Episode(
    @PrimaryKey val guid: String,
    val categoryId: String,
    val title: String,
    val pubDate: Long,           // epoch ms
    val enclosureUrl: String,
    val durationSec: Int = 0,
    val localPath: String? = null,
    val playedAt: Long? = null,
    val lastPositionMs: Long = 0L
) {
    val isPlayed: Boolean get() = playedAt != null
}
