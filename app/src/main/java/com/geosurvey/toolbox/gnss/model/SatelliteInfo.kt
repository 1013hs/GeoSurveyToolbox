package com.geosurvey.toolbox.gnss.model

data class SatelliteInfo(
    val svid: Int,
    val constellationType: Int,          // GnssStatus.CONSTELLATION_*
    val cn0DbHz: Float,                   // 信噪比
    val elevationDegrees: Float,
    val azimuthDegrees: Float,
    val usedInFix: Boolean,
    val hasEphemeris: Boolean,
    val hasAlmanac: Boolean
) {
    val constellationName: String
        get() = when (constellationType) {
            1 -> "GPS"
            3 -> "GLONASS"
            6 -> "Galileo"
            5 -> "BeiDou"
            4 -> "QZSS"
            else -> "Other"
        }
}
