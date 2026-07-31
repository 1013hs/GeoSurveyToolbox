package com.geosurvey.toolbox

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.geosurvey.toolbox.ui.GnssViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: GnssViewModel by viewModels()
    private lateinit var statusText: TextView

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
            textSize = 16f
            setTextColor(0xFF00695C.toInt())
        }
        layout.addView(statusText)
        scroll.addView(layout)
        setContentView(scroll)

        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        if (fine == PackageManager.PERMISSION_GRANTED) {
            viewModel.onPermissionGranted()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }

        lifecycleScope.launch {
            viewModel.fix.collect { fix ->
                if (fix == null) {
                    statusText.text = "正在搜索卫星...\n请到空旷处等待首次定位"
                } else {
                    statusText.text = """
                        GeoSurvey Toolbox - 阶段2
                        高精度GNSS定位核心
                        
                        纬度: ${"%.8f".format(fix.latitude)}
                        经度: ${"%.8f".format(fix.longitude)}
                        椭球高: ${"%.2f".format(fix.altitudeEllipsoid)} m
                        
                        水平精度: ${"%.1f".format(fix.accuracyHorizontal)} m
                        垂直精度: ${"%.1f".format(fix.accuracyVertical)} m
                        HDOP: ${"%.2f".format(fix.hdop)}
                        VDOP: ${"%.2f".format(fix.vdop)}
                        PDOP: ${"%.2f".format(fix.pdop)}
                        
                        速度: ${"%.2f".format(fix.speed)} m/s
                        方向: ${"%.1f".format(fix.bearing)}°
                        
                        使用卫星: ${fix.usedSatelliteCount} / ${fix.satelliteCount}
                        质量: ${fix.quality.label}
                        
                        (UI 将在后续阶段恢复为 Glassmorphism Compose)
                    """.trimIndent()
                }
            }
        }
    }
}
