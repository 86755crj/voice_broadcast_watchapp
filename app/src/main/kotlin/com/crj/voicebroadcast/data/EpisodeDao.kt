package com.crj.voicebroadcast.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EpisodeDao {

    @Query("SELECT * FROM episodes WHERE categoryId = :catId ORDER BY pubDate DESC")
    fun observeByCategory(catId: String): Flow<List<Episode>>

    @Query("SELECT * FROM episodes WHERE categoryId = :catId ORDER BY pubDate DESC")
    suspend fun listByCategory(catId: String): List<Episode>

    @Query("SELECT * FROM episodes WHERE categoryId = :catId AND playedAt IS NULL ORDER BY pubDate DESC LIMIT 1")
    suspend fun nextUnplayed(catId: String): Episode?

    @Query("SELECT * FROM episodes WHERE guid = :guid")
    suspend fun byGuid(guid: String): Episode?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(eps: List<Episode>): List<Long>

    @Update
    suspend fun update(ep: Episode)

    @Query("UPDATE episodes SET playedAt = :ts WHERE guid = :guid")
    suspend fun markPlayed(guid: String, ts: Long = System.currentTimeMillis())

    @Query("UPDATE episodes SET lastPositionMs = :pos WHERE guid = :guid")
    suspend fun updatePosition(guid: String, pos: Long)

    @Query("UPDATE episodes SET localPath = :path WHERE guid = :guid")
    suspend fun setLocalPath(guid: String, path: String)
}
