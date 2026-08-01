package com.geosurvey.toolbox

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.geosurvey.toolbox.service.LocationTrackingService
import com.geosurvey.toolbox.ui.GnssViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: GnssViewModel by viewModels()
    private lateinit var statusText: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private var isTracking = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            viewModel.onPermissionGranted()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 80, 40, 40)
        }

        statusText = TextView(this).apply {
            textSize = 15f
            setTextColor(0xFF00695C.toInt())
        }

        btnStart = Button(this).apply {
            text = "开始记录轨迹"
            setOnClickListener { startTracking() }
        }

        btnStop = Button(this).apply {
            text = "停止记录"
            isEnabled = false
            setOnClickListener { stopTracking() }
        }

        layout.addView(statusText)
        layout.addView(btnStart)
        layout.addView(btnStop)
        scroll.addView(layout)
        setContentView(scroll)

        requestPermissionsIfNeeded()

        lifecycleScope.launch {
            viewModel.fix.collect { fix ->
                val trackStatus = if (isTracking) "【正在记录】" else "【未记录】"
                if (fix == null) {
                    statusText.text = "$trackStatus\n正在搜索卫星…\n请到空旷处"
                } else {
                    statusText.text = """
                        $trackStatus
                        阶段3 · 后台轨迹记录
                        
                        纬度: ${"%.8f".format(fix.latitude)}
                        经度: ${"%.8f".format(fix.longitude)}
                        海拔: ${"%.1f".format(fix.altitudeEllipsoid)} m
                        精度: ${"%.1f".format(fix.accuracyHorizontal)} m
                        HDOP: ${"%.2f".format(fix.hdop)}  PDOP: ${"%.2f".format(fix.pdop)}
                        卫星: \( {fix.usedSatelliteCount}/ \){fix.satelliteCount}
                        质量: ${fix.quality.label}
                        速度: ${"%.2f".format(fix.speed)} m/s
                    """.trimIndent()
                }
            }
        }
    }

    private fun requestPermissionsIfNeeded() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // 后台定位会在用户点击开始后再引导
        }

        val needRequest = permissions.any {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needRequest) {
            permissionLauncher.launch(permissions.toTypedArray())
        } else {
            viewModel.onPermissionGranted()
        }
    }

    private fun startTracking() {
        val intent = Intent(this, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        isTracking = true
        btnStart.isEnabled = false
        btnStop.isEnabled = true
    }

    private fun stopTracking() {
        val intent = Intent(this, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_STOP
        }
        startService(intent)
        isTracking = false
        btnStart.isEnabled = true
        btnStop.isEnabled = false
    }
}
