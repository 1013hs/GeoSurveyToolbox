package com.geosurvey.toolbox.data.track

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(point: TrackPointEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(points: List<TrackPointEntity>)

    @Query("SELECT * FROM track_points WHERE trackId = :trackId ORDER BY timestamp ASC")
    fun getPointsByTrackId(trackId: String): Flow<List<TrackPointEntity>>

    @Query("SELECT DISTINCT trackId FROM track_points ORDER BY MAX(timestamp) DESC")
    fun getAllTrackIds(): Flow<List<String>>

    @Query("SELECT * FROM track_points ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestPoint(): TrackPointEntity?

    @Query("SELECT COUNT(*) FROM track_points WHERE trackId = :trackId")
    suspend fun getPointCount(trackId: String): Int

    @Query("DELETE FROM track_points WHERE trackId = :trackId")
    suspend fun deleteTrack(trackId: String)

    @Query("DELETE FROM track_points")
    suspend fun deleteAll()
}
