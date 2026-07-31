package com.geosurvey.toolbox

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.geosurvey.toolbox.gnss.model.GnssFix
import com.geosurvey.toolbox.gnss.model.LocationQuality
import com.geosurvey.toolbox.ui.GnssViewModel
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private val viewModel: GnssViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            viewModel.onPermissionGranted()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 请求权限
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

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
                    GnssScreen(viewModel)
                }
            }
        }
    }
}

@Composable
fun GnssScreen(viewModel: GnssViewModel) {
    val fix by viewModel.fix.collectAsState()
    val isStationary by viewModel.isStationary.collectAsState()
    val permissionGranted by viewModel.permissionGranted.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFFE0F7FA),
                        Color(0xFFB2EBF2),
                        Color(0xFF80DEEA)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // 标题
            GlassCard {
                Text(
                    text = "GeoSurvey Toolbox",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00695C)
                )
                Text(
                    text = "阶段2 · 高精度GNSS定位核心",
                    fontSize = 14.sp,
                    color = Color(0xFF00897B)
                )
            }

            Spacer(Modifier = Modifier.height(12.dp))

            if (!permissionGranted) {
                GlassCard {
                    Text("请授予定位权限以启动高精度GNSS", color = Color(0xFFD32F2F))
                }
            } else if (fix == null) {
                GlassCard {
                    Text("正在搜索卫星…", color = Color(0xFF00695C))
                    Text("请到空旷处等待首次定位", fontSize = 13.sp)
                }
            } else {
                // 质量指示
                QualityCard(fix!!.quality, isStationary)

                Spacer(modifier = Modifier.height(10.dp))

                // 坐标与时间
                GlassCard {
                    DataRow("纬度", "%.8f°".format(fix!!.latitude))
                    DataRow("经度", "%.8f°".format(fix!!.longitude))
                    DataRow("椭球高", "%.2f m".format(fix!!.altitudeEllipsoid))
                    DataRow("UTC时间", formatUtc(fix!!.utcTimeMillis))
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 精度与DOP
                GlassCard {
                    DataRow("水平精度", "%.1f m".format(fix!!.accuracyHorizontal))
                    DataRow("垂直精度", "%.1f m".format(fix!!.accuracyVertical))
                    DataRow("HDOP", "%.2f".format(fix!!.hdop))
                    DataRow("VDOP", "%.2f".format(fix!!.vdop))
                    DataRow("PDOP", "%.2f".format(fix!!.pdop))
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 运动信息
                GlassCard {
                    DataRow("速度", "%.2f m/s  (%.1f km/h)".format(fix!!.speed, fix!!.speed * 3.6f))
                    DataRow("行进方向", "%.1f°".format(fix!!.bearing))
                    DataRow("使用卫星", "${fix!!.usedSatelliteCount} / ${fix!!.satelliteCount}")
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 卫星列表（前12颗）
                GlassCard {
                    Text("卫星详情 (CN0)", fontWeight = FontWeight.Bold, color = Color(0xFF00695C))
                    Spacer(modifier = Modifier.height(6.dp))
                    fix!!.satellites
                        .sortedByDescending { it.cn0DbHz }
                        .take(12)
                        .forEach { sat ->
                            Text(
                                text = "\( {sat.constellationName} # \){sat.svid}  " +
                                        "CN0=${"%.1f".format(sat.cn0DbHz)} dB-Hz  " +
                                        "El=${"%.0f".format(sat.elevationDegrees)}°  " +
                                        if (sat.usedInFix) "✓" else "",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (sat.usedInFix) Color(0xFF00695C) else Color.Gray
                            )
                        }
                }
            }
        }
    }
}

@Composable
fun QualityCard(quality: LocationQuality, isStationary: Boolean) {
    val bg = Color(quality.colorHex).copy(alpha = 0.25f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(1.dp, Color(quality.colorHex).copy(alpha = 0.6f), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Column {
            Text(
                text = "定位质量：${quality.label}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(quality.colorHex)
            )
            Text(
                text = if (isStationary) "状态：静止（已过滤无效点）" else "状态：运动中",
                fontSize = 13.sp,
                color = Color(0xFF00695C)
            )
        }
    }
}

@Composable
fun GlassCard(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.38f))
            .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Column(content = content)
    }
}

@Composable
fun DataRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color(0xFF455A64), fontSize = 14.sp)
        Text(
            text = value,
            color = Color(0xFF00695C),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace
        )
    }
}

private fun formatUtc(millis: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    return sdf.format(Date(millis))
}
