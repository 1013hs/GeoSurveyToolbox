package com.geosurvey.toolbox.gnss

import com.geosurvey.toolbox.gnss.model.GnssFix
import com.geosurvey.toolbox.gnss.model.LocationQuality
import com.geosurvey.toolbox.gnss.model.SatelliteInfo
import kotlin.math.sqrt

object GnssQualityEvaluator {

    /**
     * 专业级定位质量评价
     * 综合考虑：HDOP / PDOP / 使用卫星数 / 平均CN0 / 水平精度
     */
    fun evaluate(
        hdop: Float,
        pdop: Float,
        usedSatCount: Int,
        avgCn0: Float,
        horizontalAccuracy: Float
    ): LocationQuality {
        var score = 100f

        // HDOP 权重最高
        score -= when {
            hdop <= 1.0f -> 0f
            hdop <= 2.0f -> 10f
            hdop <= 3.0f -> 25f
            hdop <= 5.0f -> 45f
            else -> 70f
        }

        // PDOP
        score -= when {
            pdop <= 2.0f -> 0f
            pdop <= 4.0f -> 10f
            pdop <= 6.0f -> 20f
            else -> 35f
        }

        // 使用卫星数量
        score -= when {
            usedSatCount >= 12 -> 0f
            usedSatCount >= 8 -> 8f
            usedSatCount >= 6 -> 20f
            usedSatCount >= 4 -> 40f
            else -> 60f
        }

        // 平均信噪比 CN0
        score -= when {
            avgCn0 >= 35f -> 0f
            avgCn0 >= 30f -> 8f
            avgCn0 >= 25f -> 18f
            avgCn0 >= 20f -> 30f
            else -> 45f
        }

        // 水平精度
        score -= when {
            horizontalAccuracy <= 3f -> 0f
            horizontalAccuracy <= 5f -> 8f
            horizontalAccuracy <= 10f -> 18f
            horizontalAccuracy <= 20f -> 30f
            else -> 45f
        }

        return when {
            score >= 80f -> LocationQuality.EXCELLENT
            score >= 60f -> LocationQuality.GOOD
            score >= 40f -> LocationQuality.FAIR
            score >= 20f -> LocationQuality.POOR
            else -> LocationQuality.BAD
        }
    }

    fun calculateAverageCn0(sats: List<SatelliteInfo>): Float {
        val used = sats.filter { it.usedInFix && it.cn0DbHz > 0 }
        if (used.isEmpty()) return 0f
        return used.map { it.cn0DbHz }.average().toFloat()
    }

    /**
     * 简易DOP估算（基于卫星数量与几何，后续可升级为完整协方差矩阵）
     */
    fun estimateDop(usedCount: Int, avgElevation: Float): Triple<Float, Float, Float> {
        // 经验公式（野外实测调优）
        val base = when {
            usedCount >= 12 -> 1.2f
            usedCount >= 9 -> 1.6f
            usedCount >= 7 -> 2.2f
            usedCount >= 5 -> 3.5f
            else -> 6.0f
        }
        val elevFactor = (90f - avgElevation.coerceIn(10f, 80f)) / 80f
        val hdop = (base * (1f + elevFactor * 0.6f)).coerceAtLeast(0.8f)
        val vdop = hdop * 1.5f
        val pdop = sqrt(hdop * hdop + vdop * vdop)
        return Triple(hdop, vdop, pdop)
    }
}
