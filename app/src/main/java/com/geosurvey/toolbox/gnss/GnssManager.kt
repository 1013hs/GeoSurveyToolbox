package com.geosurvey.toolbox.gnss

import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssMeasurementsEvent
import android.location.GnssStatus
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.geosurvey.toolbox.gnss.filter.JumpFilter
import com.geosurvey.toolbox.gnss.filter.StationaryDetector
import com.geosurvey.toolbox.gnss.model.GnssFix
import com.geosurvey.toolbox.gnss.model.LocationQuality
import com.geosurvey.toolbox.gnss.model.SatelliteInfo
import com.google.android.gms.location.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 专业级高精度GNSS定位管理器
 * 同时使用 FusedLocationProvider + GnssStatus + GnssMeasurements
 */
class GnssManager(private val context: Context) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)

    private val jumpFilter = JumpFilter()
    private val stationaryDetector = StationaryDetector()

    private var latestStatus: GnssStatus? = null
    private var latestMeasurements: GnssMeasurementsEvent? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    @SuppressLint("MissingPermission")
    fun observeFixes(): Flow<GnssFix> = callbackFlow {
        val statusCallback = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                latestStatus = status
            }
        }

        val measurementCallback = object : GnssMeasurementsEvent.Callback() {
            override fun onGnssMeasurementsReceived(eventArgs: GnssMeasurementsEvent) {
                latestMeasurements = eventArgs
            }
        }

        // 注册原生GNSS回调
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            locationManager.registerGnssStatusCallback(statusCallback, mainHandler)
            locationManager.registerGnssMeasurementsCallback(measurementCallback, mainHandler)
        }

        // Fused Location 高精度请求
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(500L)
            .setMaxUpdateDelayMillis(2000L)
            .setWaitForAccurateLocation(true)
            .build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                val fix = buildFix(loc)
                val filtered = jumpFilter.filter(fix)
                if (filtered != null) {
                    trySend(filtered)
                }
            }
        }

        fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())

        awaitClose {
            fusedClient.removeLocationUpdates(locationCallback)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                locationManager.unregisterGnssStatusCallback(statusCallback)
                locationManager.unregisterGnssMeasurementsCallback(measurementCallback)
            }
        }
    }.distinctUntilChanged()

    private fun buildFix(location: Location): GnssFix {
        val status = latestStatus
        val sats = mutableListOf<SatelliteInfo>()
        var usedCount = 0
        var elevSum = 0f

        if (status != null) {
            for (i in 0 until status.satelliteCount) {
                val cn0 = status.getCn0DbHz(i)
                val used = status.usedInFix(i)
                if (used) {
                    usedCount++
                    elevSum += status.getElevationDegrees(i)
                }
                sats.add(
                    SatelliteInfo(
                        svid = status.getSvid(i),
                        constellationType = status.getConstellationType(i),
                        cn0DbHz = cn0,
                        elevationDegrees = status.getElevationDegrees(i),
                        azimuthDegrees = status.getAzimuthDegrees(i),
                        usedInFix = used,
                        hasEphemeris = status.hasEphemerisData(i),
                        hasAlmanac = status.hasAlmanacData(i)
                    )
                )
            }
        }

        val avgElev = if (usedCount > 0) elevSum / usedCount else 30f
        val (hdop, vdop, pdop) = GnssQualityEvaluator.estimateDop(usedCount, avgElev)
        val avgCn0 = GnssQualityEvaluator.calculateAverageCn0(sats)

        val quality = GnssQualityEvaluator.evaluate(
            hdop = hdop,
            pdop = pdop,
            usedSatCount = usedCount,
            avgCn0 = avgCn0,
            horizontalAccuracy = location.accuracy
        )

        return GnssFix(
            latitude = location.latitude,
            longitude = location.longitude,
            altitudeEllipsoid = if (location.hasAltitude()) location.altitude else 0.0,
            accuracyHorizontal = location.accuracy,
            accuracyVertical = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && location.hasVerticalAccuracy()) {
                location.verticalAccuracyMeters
            } else {
                location.accuracy * 1.5f
            },
            speed = if (location.hasSpeed()) location.speed else 0f,
            bearing = if (location.hasBearing()) location.bearing else 0f,
            utcTimeMillis = location.time,
            satelliteCount = status?.satelliteCount ?: 0,
            usedSatelliteCount = usedCount,
            hdop = hdop,
            vdop = vdop,
            pdop = pdop,
            quality = quality,
            satellites = sats
        )
    }

    fun isStationary(fix: GnssFix): Boolean = stationaryDetector.isStationary(fix)

    fun resetFilters() {
        jumpFilter.reset()
        stationaryDetector.reset()
    }
}
