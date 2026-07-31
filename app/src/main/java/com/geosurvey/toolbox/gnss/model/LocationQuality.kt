package com.geosurvey.toolbox.gnss.model

enum class LocationQuality(val label: String, val colorHex: Long) {
    EXCELLENT("优秀", 0xFF00C853),
    GOOD("良好", 0xFF64DD17),
    FAIR("一般", 0xFFFFAB00),
    POOR("较差", 0xFFFF6D00),
    BAD("差/不可用", 0xFFD50000)
}
