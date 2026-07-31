package com.geosurvey.toolbox.gnss.filter

import com.geosurvey.toolbox.gnss.model.GnssFix
import kotlin.math.*

/**
 * 异常跳点过滤 + 漂移检测
 */
class JumpFilter(
    private val maxJumpDistanceMeters: Double = 35.0,   // 单点最大允许跳跃距离
    private val maxSpeedMps: Double = 45.0               // 最大合理速度（车辆）
) {
    private var lastValid: GnssFix? = null

    fun filter(fix: GnssFix): GnssFix? {
        val last = lastValid
        if (last == null) {
            lastValid = fix
            return fix
        }

        val dist = haversine(last.latitude, last.longitude, fix.latitude, fix.longitude)
        val timeDeltaSec = (fix.utcTimeMillis - last.utcTimeMillis) / 1000.0
        if (timeDeltaSec <= 0) return null

        val speed = dist / timeDeltaSec

        // 跳点或超速直接丢弃
        if (dist > maxJumpDistanceMeters || speed > maxSpeedMps) {
            return null
        }

        // 质量太差也丢弃
        if (fix.quality == com.geosurvey.toolbox.gnss.model.LocationQuality.BAD) {
            return null
        }

        lastValid = fix
        return fix
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
        lastValid = null
    }
}
