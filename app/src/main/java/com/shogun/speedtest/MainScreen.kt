package com.shogun.speedtest

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.core.*
import com.shogun.speedtest.update.UpdateDownloader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shogun.speedtest.ui.HiddenRadioDebugPanel
import com.shogun.speedtest.data.HiddenRadioSnapshot
import com.shogun.speedtest.data.SpeedtestResult
import com.shogun.speedtest.shizuku.ShizukuAccessState
import com.shogun.speedtest.ui.SettingsActivity
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

private val BgColor = Color(0xFF0D0D0D)
private val CardColor = Color(0xFF1A1A1A)
private val AccentBlue = Color(0xFF2196F3)
private val AccentGreen = Color(0xFF4CAF50)
private val AccentYellow = Color(0xFFFFEB3B)
private val AccentOrange = Color(0xFFFF9800)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFF9E9E9E)

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isMeasuring by viewModel.isMeasuring.collectAsState()
    val latestResult by viewModel.latestResult.collectAsState()
    val history by viewModel.history.collectAsState()
    val realtimePoints by viewModel.realtimePoints.collectAsState()
    val gaugeValue by viewModel.gaugeValue.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val updateInfo by viewModel.updateInfo.collectAsState()
    val locationMissing by viewModel.locationPermissionMissing.collectAsState()
    val notificationMissing by viewModel.notificationPermissionMissing.collectAsState()
    val shizukuState by viewModel.shizukuState.collectAsState()
    val hiddenRadio by viewModel.hiddenRadioFlow.collectAsState()

    val animatedGauge by animateFloatAsState(
        targetValue = gaugeValue,
        animationSpec = tween(durationMillis = 400),
        label = "gauge"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top bar: device info + settings button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = viewModel.deviceName,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                val infoText = latestResult?.let { r ->
                    buildString {
                        r.isp?.let { append(it) }
                        r.serverName?.let { if (isNotEmpty()) append(" / "); append(it) }
                    }.takeIf { it.isNotEmpty() } ?: "計測待機中"
                } ?: "計測待機中"
                Text(text = infoText, color = TextSecondary, fontSize = 11.sp)
            }
            IconButton(onClick = {
                context.startActivity(Intent(context, SettingsActivity::class.java))
            }) {
                Text("⚙", fontSize = 20.sp, color = TextSecondary)
            }
        }

        // Permission warning banners
        if (locationMissing || notificationMissing) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF4A1A1A))
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    if (locationMissing) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "⚠ 位置情報の権限がないためSSIDを取得できません",
                                color = Color(0xFFFF8A80),
                                fontSize = 11.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    if (notificationMissing) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "⚠ 通知の権限がないため自動計測が停止する場合があります",
                                color = Color(0xFFFF8A80),
                                fontSize = 11.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    TextButton(onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }) {
                        Text("権限を設定する", color = AccentYellow, fontSize = 11.sp)
                    }
                }
            }
        }

        ShizukuStatusCard(
            state = shizukuState,
            onInstallClick = {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/thedjchi/Shizuku/releases/latest")
                    )
                )
            },
            onGrantClick = { viewModel.requestShizukuPermission() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )

        // Speedometer gauge or measuring animation
        if (isMeasuring) {
            MeasuringAnimation(modifier = Modifier.size(220.dp))
        } else {
            SpeedometerGauge(
                value = animatedGauge,
                maxValue = 300f,
                isMeasuring = false,
                modifier = Modifier.size(220.dp)
            )
        }

        // Status text
        if (isMeasuring) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .padding(bottom = 4.dp),
                color = AccentBlue,
                trackColor = Color.DarkGray
            )
            Text(
                text = "計測中...",
                color = AccentBlue,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        } else if (errorMessage != null) {
            Text(
                text = errorMessage ?: "",
                color = Color.Red,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        // Real-time line graph (only show after measurement completes)
        if (!isMeasuring && realtimePoints.isNotEmpty()) {
            RealtimeGraph(
                points = realtimePoints,
                maxValue = 300f,
                color = AccentBlue,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .padding(bottom = 8.dp)
            )
        }

        // Result metrics (show "--" during measurement)
        val displayResult = if (isMeasuring) null else latestResult
        ResultMetrics(result = displayResult, modifier = Modifier.fillMaxWidth())

        if (shizukuState == ShizukuAccessState.Granted) {
            Spacer(modifier = Modifier.height(8.dp))
            HiddenRadioInfoCard(
                snapshot = hiddenRadio,
                onRefresh = { viewModel.refreshHiddenRadio() },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            HiddenRadioDebugPanel(modifier = Modifier.fillMaxWidth())
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Manual run button
        Button(
            onClick = { viewModel.startMeasurement(context) },
            enabled = !isMeasuring,
            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (isMeasuring) "計測中..." else "手動実行",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // History list
        if (history.isNotEmpty()) {
            Text(
                text = "計測履歴",
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth()
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(history) { result ->
                    HistoryCard(result = result)
                }
            }
        }
    }

    // アップデートダイアログ
    updateInfo?.let { info ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissUpdate() },
            title = { Text("アップデートあり v${info.versionName}") },
            text = { Text(info.releaseNotes.ifEmpty { "新しいバージョンが利用可能です" }) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        UpdateDownloader(context).downloadApk(
                            apkUrl = info.apkUrl,
                            versionName = info.versionName,
                            sha256Expected = info.sha256,
                            onProgress = {},
                            onInstalling = {},
                            onComplete = { viewModel.dismissUpdate() },
                            onError = { message ->
                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            }
                        )
                    }
                }) { Text("ダウンロード") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissUpdate() }) { Text("後で") }
            }
        )
    }
}

@Composable
private fun ShizukuStatusCard(
    state: ShizukuAccessState,
    onInstallClick: () -> Unit,
    onGrantClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor = when (state) {
        ShizukuAccessState.Granted -> AccentGreen
        ShizukuAccessState.PermissionDenied -> AccentYellow
        ShizukuAccessState.Unavailable -> AccentOrange
    }
    val statusText = when (state) {
        ShizukuAccessState.Granted -> "接続済み"
        ShizukuAccessState.PermissionDenied -> "権限待ち"
        ShizukuAccessState.Unavailable -> "未導入 / 未起動"
    }
    val helperText = when (state) {
        ShizukuAccessState.Granted -> "Hidden Radio Info の取得中。データが取得できない場合は端末非対応の可能性があります。"
        ShizukuAccessState.PermissionDenied -> "Shizuku は見つかりました。永続化対応版(thedjchi fork)で権限を許可すると追加情報を有効化できます。"
        ShizukuAccessState.Unavailable -> "Shizuku が見つからないか、サービスに接続できません。永続化対応版(thedjchi fork)の導入を推奨します。"
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = CardColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Shizuku Status",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = statusText,
                    color = statusColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = helperText,
                color = TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
            when (state) {
                ShizukuAccessState.Unavailable -> {
                    TextButton(onClick = onInstallClick, modifier = Modifier.padding(top = 4.dp)) {
                        Text("Shizukuをインストール", color = AccentBlue)
                    }
                }
                ShizukuAccessState.PermissionDenied -> {
                    TextButton(onClick = onGrantClick, modifier = Modifier.padding(top = 4.dp)) {
                        Text("Shizuku権限を許可", color = AccentBlue)
                    }
                }
                ShizukuAccessState.Granted -> Unit
            }
        }
    }
}

@Composable
private fun HiddenRadioInfoCard(
    snapshot: HiddenRadioSnapshot?,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = CardColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Hidden Radio Info",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                if (snapshot != null && snapshot.isCarrierAggregation) {
                    Text(
                        text = "CA (${snapshot.ccCount}CC)",
                        color = AccentGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (snapshot == null) {
                Surface(
                    color = Color(0xFF121212),
                    shape = MaterialTheme.shapes.medium,
                    tonalElevation = 0.dp
                ) {
                    Text(
                        text = "データ取得中...",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                TextButton(onClick = onRefresh, modifier = Modifier.padding(top = 4.dp)) {
                    Text("再取得", color = AccentBlue, fontSize = 11.sp)
                }
            } else if (snapshot.componentCarriers.isEmpty()) {
                Surface(
                    color = Color(0xFF121212),
                    shape = MaterialTheme.shapes.medium,
                    tonalElevation = 0.dp
                ) {
                    Text(
                        text = "取得失敗または利用不可（端末非対応の可能性）",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                TextButton(onClick = onRefresh, modifier = Modifier.padding(top = 4.dp)) {
                    Text("再取得", color = AccentBlue, fontSize = 11.sp)
                }
            } else {
                Surface(
                    color = Color(0xFF121212),
                    shape = MaterialTheme.shapes.medium,
                    tonalElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(
                            text = "QAM",
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
                        Text(
                            text = "取得不可（機種非対応）",
                            color = AccentYellow,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                snapshot.componentCarriers.forEachIndexed { index, cc ->
                    if (index > 0) Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = Color(0xFF121212),
                        shape = MaterialTheme.shapes.medium,
                        tonalElevation = 0.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                val statusColor = if (cc.connectionStatus == "PCC") {
                                    AccentBlue
                                } else {
                                    AccentGreen
                                }
                                Text(
                                    text = "CC${index + 1} ${cc.connectionStatus}",
                                    color = statusColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = listOf(
                                        cc.band?.let { "Band $it" } ?: "Band -",
                                        cc.bandwidthDownlinkKhz?.let { "DL ${it / 1000} MHz" } ?: "DL -",
                                        cc.bandwidthUplinkKhz?.let { "UL ${it / 1000} MHz" } ?: "UL -"
                                    ).joinToString(" / "),
                                    color = TextPrimary,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = listOf(
                                        cc.physicalCellId?.let { "PCI $it" } ?: "PCI -",
                                        cc.downlinkChannelNumber?.let { "EARFCN/NRARFCN $it" } ?: "Ch -"
                                    ).joinToString(" / "),
                                    color = TextSecondary,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = listOf(
                                        cc.networkType ?: "Type -",
                                        cc.frequencyRange ?: "Range -",
                                        cc.downlinkFrequencyKhz?.let { "DLFreq ${it / 1000} MHz" } ?: "DLFreq -"
                                    ).joinToString(" / "),
                                    color = TextSecondary,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MeasuringAnimation(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size((120 * pulse).dp)
                .clip(CircleShape)
                .background(Color(0xFF1A9BE6).copy(alpha = 0.25f))
        )
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(Color(0xFF1A9BE6).copy(alpha = 0.5f))
        )
    }
}

@Composable
private fun SpeedometerGauge(
    value: Float,
    maxValue: Float,
    isMeasuring: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 18.dp.toPx()
            val inset = strokeWidth / 2f + 8.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)

            val startAngle = 135f
            val sweepTotal = 270f

            // Background arc
            drawArc(
                color = Color(0xFF2A2A2A),
                startAngle = startAngle,
                sweepAngle = sweepTotal,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Filled arc
            val ratio = (value / maxValue).coerceIn(0f, 1f)
            val sweepFilled = sweepTotal * ratio
            if (sweepFilled > 0f) {
                val arcColor = when {
                    value < 50f -> Color(0xFF4CAF50)
                    value < 150f -> Color(0xFF2196F3)
                    else -> Color(0xFF9C27B0)
                }
                drawArc(
                    color = arcColor,
                    startAngle = startAngle,
                    sweepAngle = sweepFilled,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            // Tick marks
            val cx = size.width / 2f
            val cy = size.height / 2f
            val outerR = (size.width / 2f) - inset + strokeWidth / 2 + 4.dp.toPx()
            val innerR = outerR - 10.dp.toPx()
            val labels = listOf(0, 50, 100, 200, 300)
            labels.forEach { v ->
                val angle = Math.toRadians((startAngle + sweepTotal * v / maxValue).toDouble())
                val sx = cx + outerR * cos(angle).toFloat()
                val sy = cy + outerR * sin(angle).toFloat()
                val ex = cx + innerR * cos(angle).toFloat()
                val ey = cy + innerR * sin(angle).toFloat()
                drawLine(Color(0xFF555555), Offset(sx, sy), Offset(ex, ey), 2.dp.toPx())

                // Label
                val labelR = outerR + 14.dp.toPx()
                val lx = cx + labelR * cos(angle).toFloat()
                val ly = cy + labelR * sin(angle).toFloat()
                drawContext.canvas.nativeCanvas.drawText(
                    "$v",
                    lx,
                    ly + 4.dp.toPx(),
                    android.graphics.Paint().apply {
                        color = 0xFF777777.toInt()
                        textSize = 9.sp.toPx()
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                )
            }
        }

        // Center value display
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (value < 0.5f && !isMeasuring) "--" else "%.1f".format(value),
                color = TextPrimary,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(text = "Mbps", color = TextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun RealtimeGraph(
    points: List<Float>,
    maxValue: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (points.size < 2) return@Canvas

        val path = Path()
        val stepX = size.width / (points.size - 1).toFloat().coerceAtLeast(1f)

        points.forEachIndexed { i, v ->
            val x = i * stepX
            val y = size.height - (v / maxValue).coerceIn(0f, 1f) * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )

        // Fill under curve
        val fillPath = Path().apply {
            addPath(path)
            val lastX = (points.size - 1) * stepX
            lineTo(lastX, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(fillPath, color.copy(alpha = 0.15f))
    }
}

@Composable
private fun ResultMetrics(result: SpeedtestResult?, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = CardColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            MetricItem("↓ DL", result?.downloadMbps?.let { "%.1f".format(it) } ?: "--", "Mbps", AccentBlue)
            MetricItem("↑ UL", result?.uploadMbps?.let { "%.1f".format(it) } ?: "--", "Mbps", AccentGreen)
            MetricItem("◎ Ping", result?.pingMs?.let { "%.1f".format(it) } ?: "--", "ms", AccentYellow)
            MetricItem("≈ Jitter", result?.jitterMs?.let { "%.1f".format(it) } ?: "--", "ms", AccentOrange)
        }
    }
}

@Composable
private fun MetricItem(label: String, value: String, unit: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = TextSecondary, fontSize = 10.sp)
        Text(
            text = value,
            color = color,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Text(text = unit, color = TextSecondary, fontSize = 10.sp)
    }
}

@Composable
private fun HistoryCard(result: SpeedtestResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = result.timestampIso.take(16),
                color = TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f),
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "↓%.1f".format(result.downloadMbps),
                color = AccentBlue,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 4.dp),
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "↑%.1f".format(result.uploadMbps),
                color = AccentGreen,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 4.dp),
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "%.0fms".format(result.pingMs),
                color = AccentYellow,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp),
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
