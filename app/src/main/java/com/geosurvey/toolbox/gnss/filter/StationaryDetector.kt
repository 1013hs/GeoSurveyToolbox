package com.geosurvey.toolbox.gnss.filter

import com.geosurvey.toolbox.gnss.model.GnssFix
import kotlin.math.*

/**
 * 静止状态识别
 * 静止时禁止产生无效轨迹点（后续轨迹模块会用）
 */
class StationaryDetector(
    private val windowSize: Int = 8,
    private val maxMovementMeters: Double = 2.8
) {
    private val recent = ArrayDeque<GnssFix>(windowSize)

    fun isStationary(fix: GnssFix): Boolean {
        recent.addLast(fix)
        if (recent.size > windowSize) recent.removeFirst()
        if (recent.size < 4) return false

        val first = recent.first()
        val last = recent.last()
        val dist = haversine(first.latitude, first.longitude, last.latitude, last.longitude)
        return dist < maxMovementMeters
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        return 2 * R * asin(sqrt(a))
    }

    fun reset() {
        recent.clear()
    }
}
