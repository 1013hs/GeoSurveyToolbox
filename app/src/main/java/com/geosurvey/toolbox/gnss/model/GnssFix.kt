package com.geosurvey.toolbox.gnss.model

data class GnssFix(
    val latitude: Double,
    val longitude: Double,
    val altitudeEllipsoid: Double,        // 椭球高
    val accuracyHorizontal: Float,        // 水平精度估计 (m)
    val accuracyVertical: Float,          // 垂直精度估计 (m)
    val speed: Float,                     // m/s
    val bearing: Float,                   // 行进方向 0-360
    val utcTimeMillis: Long,
    val satelliteCount: Int,
    val usedSatelliteCount: Int,
    val hdop: Float,
    val vdop: Float,
    val pdop: Float,
    val quality: LocationQuality,
    val satellites: List<SatelliteInfo> = emptyList()
)
