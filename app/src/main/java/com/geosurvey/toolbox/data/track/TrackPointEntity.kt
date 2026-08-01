package com.geosurvey.toolbox.data.track

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "track_points")
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: String,               // 一次记录的唯一ID（UUID）
    val timestamp: Long,               // UTC 毫秒
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val speed: Float,
    val bearing: Float,
    val accuracy: Float,
    val satelliteCount: Int,
    val hdop: Float,
    val pdop: Float,
    val qualityLabel: String
)
