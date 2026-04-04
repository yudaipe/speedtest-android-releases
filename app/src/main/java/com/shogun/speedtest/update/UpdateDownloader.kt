package com.shogun.speedtest.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

class UpdateDownloader(
    private val context: Context,
    private val onNeedPermission: ((fileName: String) -> Unit)? = null
) {

    fun downloadApk(
        apkUrl: String,
        versionName: String,
        onError: (String) -> Unit = {}
    ): Long {
        val fileName = "speed_test_monitor-v$versionName.apk"

        // Fix 3: ダウンロード前に既存APKを削除（同名ファイルによる別名保存→FileProvider参照失敗を防ぐ）
        val existingFile = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            fileName
        )
        if (existingFile.exists()) {
            existingFile.delete()
            Log.d(TAG, "Deleted existing APK: ${existingFile.absolutePath}")
        }

        val request = DownloadManager.Request(Uri.parse(apkUrl)).apply {
            setTitle("speed_test_monitor アップデート v$versionName")
            setDescription("ダウンロード中...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            setMimeType("application/vnd.android.package-archive")
            setAllowedOverMetered(true)   // モバイル回線でのダウンロード許可
            setAllowedOverRoaming(true)   // ローミング中のダウンロード許可
        }
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = dm.enqueue(request)
        if (downloadId == -1L) {
            Log.e(TAG, "DownloadManager enqueue failed")
            onError("ダウンロード開始に失敗しました")
            return -1L
        }

        // ダウンロード完了をBroadcastReceiverで待機
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    ctx.unregisterReceiver(this)
                    val query = DownloadManager.Query().setFilterById(downloadId)
                    val cursor = dm.query(query)
                    cursor.use {
                        if (it.moveToFirst()) {
                            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                            val reason = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                            when (status) {
                                DownloadManager.STATUS_SUCCESSFUL -> installApk(fileName)
                                DownloadManager.STATUS_FAILED -> {
                                    val msg = when (reason) {
                                        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "ストレージ不足"
                                        DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "ファイルが既に存在します"
                                        DownloadManager.ERROR_CANNOT_RESUME -> "ダウンロードを再開できません"
                                        404 -> "ファイルが見つかりません（HTTP 404）"
                                        else -> "ダウンロード失敗（エラー: $reason）"
                                    }
                                    onError(msg)
                                }
                            }
                        }
                    }
                }
            }
        }
        context.registerReceiver(
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        )
        return downloadId
    }

    // Fix 1: BRスレッドからの呼び出し問題を解消するため ctx 引数を削除し this.context を使用
    private fun installApk(fileName: String) {
        try {
            val apkFile = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                fileName
            )
            if (!apkFile.exists()) {
                Log.w(TAG, "APK file not found: $fileName")
                return
            }

            // Android 8+ では canRequestPackageInstalls() チェックが必要
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!this.context.packageManager.canRequestPackageInstalls()) {
                    // Fix 1: メインスレッドでコールバック/startActivity を実行
                    Handler(Looper.getMainLooper()).post {
                        if (onNeedPermission != null) {
                            onNeedPermission.invoke(fileName)
                        } else {
                            this.context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:${this.context.packageName}")
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                    return
                }
            }

            // Fix 1: メインスレッドでインストールIntent起動
            Handler(Looper.getMainLooper()).post {
                launchInstallIntent(this.context, apkFile)
            }
        } catch (e: Exception) {
            Log.e(TAG, "installApk failed: ${e.message}", e)
        }
    }

    /** 権限許可後のリトライ用。呼び出し元から fileName を渡して再インストール。 */
    fun retryInstall(fileName: String) {
        try {
            val apkFile = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                fileName
            )
            if (!apkFile.exists()) {
                Log.w(TAG, "retryInstall: APK file not found: $fileName")
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !this.context.packageManager.canRequestPackageInstalls()
            ) {
                Log.w(TAG, "retryInstall: permission still not granted")
                return
            }
            // Fix 1: メインスレッドでインストールIntent起動
            Handler(Looper.getMainLooper()).post {
                launchInstallIntent(this.context, apkFile)
            }
        } catch (e: Exception) {
            Log.e(TAG, "retryInstall failed: ${e.message}", e)
        }
    }

    // Fix 2: Android 12+(API 31+) は ACTION_INSTALL_PACKAGE、それ以前は ACTION_VIEW
    private fun launchInstallIntent(context: Context, apkFile: File) {
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val installIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                data = apkUri
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(installIntent)
    }

    companion object {
        private const val TAG = "UpdateDownloader"
    }
}
