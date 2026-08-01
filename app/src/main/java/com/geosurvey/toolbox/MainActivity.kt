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
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.geosurvey.toolbox.data.track.TrackRepository
import com.geosurvey.toolbox.service.LocationTrackingService
import com.geosurvey.toolbox.ui.GnssViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val viewModel: GnssViewModel by viewModels()
    private lateinit var repository: TrackRepository

    private lateinit var statusText: TextView
    private lateinit var historyText: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var btnRefreshHistory: Button

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
        repository = TrackRepository(applicationContext)

        val scroll = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 60, 36, 40)
        }

        statusText = TextView(this).apply {
            textSize = 14.5f
            setTextColor(0xFF00695C.toInt())
            setPadding(0, 0, 0, 24)
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

        btnRefreshHistory = Button(this).apply {
            text = "刷新历史轨迹"
            setOnClickListener { loadHistory() }
        }

        historyText = TextView(this).apply {
            textSize = 13f
            setTextColor(0xFF37474F.toInt())
            setPadding(0, 20, 0, 0)
        }

        layout.addView(statusText)
        layout.addView(btnStart)
        layout.addView(btnStop)
        layout.addView(btnRefreshHistory)
        layout.addView(historyText)
        scroll.addView(layout)
        setContentView(scroll)

        requestPermissionsIfNeeded()
        observeFix()
        loadHistory()
    }

    private fun observeFix() {
        lifecycleScope.launch {
            viewModel.fix.collect { fix ->
                val trackStatus = if (isTracking) "【正在记录】" else "【未记录】"
                if (fix == null) {
                    statusText.text = "$trackStatus\n正在搜索卫星…\n请到空旷处"
                } else {
                    statusText.text = """
                        $trackStatus
                        阶段3 · 后台轨迹 + 历史管理
                        
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

    private fun loadHistory() {
        lifecycleScope.launch {
            repository.getAllTrackIds().collectLatest { ids ->
                if (ids.isEmpty()) {
                    historyText.text = "\n暂无历史轨迹"
                    return@collectLatest
                }

                val sb = StringBuilder("\n===== 历史轨迹 =====\n")
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

                for (id in ids) {
                    val count = repository.getPointCount(id)
                    val points = repository.getPoints(id)
                    // 只取第一个点的时间作为开始时间（简化）
                    var startTime = "-"
                    // Flow 不能直接取，这里用最新点近似显示
                    val latest = repository.getLatestPoint()
                    if (latest != null && latest.trackId == id) {
                        startTime = sdf.format(Date(latest.timestamp))
                    }
                    sb.append("\n轨迹ID: ${id.take(8)}...\n")
                    sb.append("点数: $count\n")
                    sb.append("最近时间: $startTime\n")
                    sb.append("------------------\n")
                }
                historyText.text = sb.toString()
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
        Toast.makeText(this, "开始记录", Toast.LENGTH_SHORT).show()
    }

    private fun stopTracking() {
        val intent = Intent(this, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_STOP
        }
        startService(intent)
        isTracking = false
        btnStart.isEnabled = true
        btnStop.isEnabled = false
        Toast.makeText(this, "已停止记录", Toast.LENGTH_SHORT).show()
        loadHistory()
    }
}
