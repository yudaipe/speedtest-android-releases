package com.shogun.speedtest.ui

import android.app.DownloadManager
import android.content.Context
import android.os.PowerManager
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.shogun.speedtest.data.SpeedtestResult
import com.shogun.speedtest.update.UpdateChecker
import com.shogun.speedtest.update.UpdateDownloader
import com.shogun.speedtest.update.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("端末設定", style = MaterialTheme.typography.titleMedium)

        OutlinedTextField(
            value = state.deviceName,
            onValueChange = viewModel::setDeviceName,
            label = { Text("端末名") },
            placeholder = { Text("例: 端末1-docomo") }
        )

        Text("端末ID: ${state.deviceId}", style = MaterialTheme.typography.bodySmall)

        Divider(modifier = Modifier.padding(vertical = 16.dp))
        Text("計測設定（v1固定）", style = MaterialTheme.typography.titleMedium)

        Text("測定間隔: 30分毎（毎時00分/30分）")
        Text("サーバー: IPA CyberLab 400G (ID: 48463)")

        Divider(modifier = Modifier.padding(vertical = 16.dp))
        Text("ステータス", style = MaterialTheme.typography.titleMedium)

        BatteryOptimizationStatus()
        LastMeasurementCard(state.lastResult)
        SyncStatusCard(state.unsyncedCount)

        Divider(modifier = Modifier.padding(vertical = 16.dp))
        val context = LocalContext.current
        val versionName = remember {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }
        Text(
            text = "バージョン: $versionName",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )

        var isChecking by remember { mutableStateOf(false) }
        var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
        var isDownloading by remember { mutableStateOf(false) }
        var downloadProgress by remember { mutableFloatStateOf(0f) }
        var activeDownloadId by remember { mutableLongStateOf(-1L) }
        var downloadError by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        // ダウンロード進捗ポーリング
        LaunchedEffect(activeDownloadId) {
            if (activeDownloadId < 0) return@LaunchedEffect
            while (isDownloading) {
                val query = DownloadManager.Query().setFilterById(activeDownloadId)
                val cursor = dm.query(query)
                cursor.use {
                    if (it.moveToFirst()) {
                        val downloaded = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        if (total > 0) {
                            downloadProgress = downloaded.toFloat() / total.toFloat()
                        }
                        val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        if (status == DownloadManager.STATUS_SUCCESSFUL ||
                            status == DownloadManager.STATUS_FAILED) {
                            isDownloading = false
                        }
                    }
                }
                delay(500)
            }
        }

        // ダウンロード進捗ダイアログ
        if (isDownloading) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("ダウンロード中...") },
                text = {
                    Column {
                        Text("${(downloadProgress * 100).toInt()}%")
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        )
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = {
                        dm.remove(activeDownloadId)
                        isDownloading = false
                        activeDownloadId = -1L
                        downloadProgress = 0f
                    }) { Text("キャンセル") }
                }
            )
        }

        // エラーダイアログ
        downloadError?.let { errMsg ->
            AlertDialog(
                onDismissRequest = { downloadError = null },
                title = { Text("ダウンロードエラー") },
                text = { Text(errMsg) },
                confirmButton = {
                    TextButton(onClick = { downloadError = null }) { Text("OK") }
                }
            )
        }

        if (updateInfo != null) {
            AlertDialog(
                onDismissRequest = { updateInfo = null },
                title = { Text("アップデートあり") },
                text = { Text("v${updateInfo!!.versionName} が利用可能です") },
                confirmButton = {
                    TextButton(onClick = {
                        val info = updateInfo!!
                        updateInfo = null
                        isDownloading = true
                        downloadProgress = 0f
                        val id = UpdateDownloader(context).downloadApk(
                            apkUrl = info.apkUrl,
                            versionName = info.versionName,
                            onError = { msg ->
                                isDownloading = false
                                downloadError = msg
                            }
                        )
                        activeDownloadId = id
                    }) { Text("ダウンロード") }
                },
                dismissButton = {
                    TextButton(onClick = { updateInfo = null }) { Text("後で") }
                }
            )
        }

        Button(
            onClick = {
                isChecking = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) { UpdateChecker(context).checkForUpdate() }
                    isChecking = false
                    val currentVersionCode = UpdateChecker(context).getCurrentVersionCode()
                    if (result != null && result.versionCode > currentVersionCode) updateInfo = result
                    else Toast.makeText(context, "最新版です", Toast.LENGTH_SHORT).show()
                }
            },
            enabled = !isChecking,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text(if (isChecking) "確認中..." else "アップデート確認")
        }
    }
}

@Composable
fun BatteryOptimizationStatus() {
    val context = LocalContext.current
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    val isExempted = pm.isIgnoringBatteryOptimizations(context.packageName)

    Row {
        Text("バッテリー最適化: ")
        if (isExempted) {
            Text("✓ 除外済み", color = Color.Green)
        } else {
            Text("✗ 未除外（精度低下の可能性）", color = Color.Yellow)
        }
    }
}

@Composable
fun LastMeasurementCard(lastResult: SpeedtestResult?) {
    if (lastResult == null) {
        Text("直近計測: なし", style = MaterialTheme.typography.bodySmall)
    } else {
        Column {
            Text("直近計測:", style = MaterialTheme.typography.bodySmall)
            Text(
                "↓ ${String.format("%.1f", lastResult.downloadMbps)} Mbps  " +
                "↑ ${String.format("%.1f", lastResult.uploadMbps)} Mbps  " +
                "Ping: ${String.format("%.0f", lastResult.pingMs)} ms",
                style = MaterialTheme.typography.bodySmall
            )
            Text(lastResult.timestampIso, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun SyncStatusCard(unsyncedCount: Int) {
    val color = if (unsyncedCount == 0) Color.Green else Color.Yellow
    Text(
        "未同期件数: $unsyncedCount 件",
        style = MaterialTheme.typography.bodySmall,
        color = color
    )
}
