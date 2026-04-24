package com.shogun.speedtest.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.shogun.speedtest.data.SpeedtestResult
import com.shogun.speedtest.shizuku.ShizukuAccessState
import com.shogun.speedtest.update.UpdateChecker
import com.shogun.speedtest.update.UpdateDownloader
import com.shogun.speedtest.update.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val state by viewModel.uiState.collectAsState()
    val shizukuState by viewModel.shizukuState.collectAsState()
    val context = LocalContext.current
    val writePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onLegacyStoragePermissionResult(granted)
    }

    // エクスポート結果をToastで表示
    state.exportResult?.let { msg ->
        LaunchedEffect(msg) {
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            viewModel.clearExportResult()
        }
    }

    // デバッグログ共有インテント発行
    state.pendingShareUri?.let { uri ->
        LaunchedEffect(uri) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "ログを共有"))
            viewModel.clearPendingShareUri()
        }
    }

    if (state.requestLegacyStoragePermission) {
        LaunchedEffect(state.requestLegacyStoragePermission) {
            viewModel.consumeLegacyStoragePermissionRequest()
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                writePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

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

        ShizukuStatusRow(shizukuState)
        BatteryOptimizationStatus()
        LastMeasurementCard(state.lastResult)
        SyncStatusCard(state.unsyncedCount)

        Divider(modifier = Modifier.padding(vertical = 16.dp))
        Text("データ管理", style = MaterialTheme.typography.titleMedium)
        Button(
            onClick = { viewModel.exportCsv() },
            enabled = !state.isExporting,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text(if (state.isExporting) "エクスポート中..." else "データエクスポート")
        }

        Divider(modifier = Modifier.padding(vertical = 16.dp))
        Text("デバッグログ", style = MaterialTheme.typography.titleMedium)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("詳細ログ収集", style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = state.debugLogEnabled,
                onCheckedChange = viewModel::setDebugLogEnabled
            )
        }

        Button(
            onClick = { viewModel.exportDebugLog() },
            enabled = state.debugLogEnabled,
            modifier = Modifier.padding(top = 4.dp)
        ) {
            Text("ログをエクスポート")
        }

        Divider(modifier = Modifier.padding(vertical = 16.dp))
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
        var downloadError by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()

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
                dismissButton = {}
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
                        scope.launch {
                            UpdateDownloader(context).downloadApk(
                                apkUrl = info.apkUrl,
                                versionName = info.versionName,
                                sha256Expected = info.sha256,
                                onProgress = { progress ->
                                    downloadProgress = progress
                                },
                                onInstalling = {
                                    isDownloading = false
                                    Toast.makeText(context, "インストールを開始します", Toast.LENGTH_SHORT).show()
                                },
                                onComplete = {
                                    isDownloading = false
                                    downloadProgress = 1f
                                },
                                onError = { msg ->
                                    isDownloading = false
                                    downloadError = msg
                                }
                            )
                        }
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
fun ShizukuStatusRow(state: ShizukuAccessState) {
    val (label, color) = when (state) {
        ShizukuAccessState.Granted -> "接続済み" to Color.Green
        ShizukuAccessState.PermissionDenied -> "権限待ち" to Color.Yellow
        ShizukuAccessState.Unavailable -> "未接続" to Color.Gray
    }
    Row {
        Text("Shizuku: ", style = MaterialTheme.typography.bodySmall)
        Text(label, style = MaterialTheme.typography.bodySmall, color = color)
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
