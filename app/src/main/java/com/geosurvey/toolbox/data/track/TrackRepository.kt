package com.geosurvey.toolbox.data.track

import android.content.Context
import kotlinx.coroutines.flow.Flow

class TrackRepository(context: Context) {
    private val dao = AppDatabase.getInstance(context).trackDao()

    suspend fun insertPoint(point: TrackPointEntity) {
        dao.insert(point)
    }

    fun getPoints(trackId: String): Flow<List<TrackPointEntity>> {
        return dao.getPointsByTrackId(trackId)
    }

    fun getAllTrackIds(): Flow<List<String>> {
        return dao.getAllTrackIds()
    }

    suspend fun getLatestPoint(): TrackPointEntity? {
        return dao.getLatestPoint()
    }

    suspend fun getPointCount(trackId: String): Int {
        return dao.getPointCount(trackId)
    }

    suspend fun deleteTrack(trackId: String) {
        dao.deleteTrack(trackId)
    }
}
