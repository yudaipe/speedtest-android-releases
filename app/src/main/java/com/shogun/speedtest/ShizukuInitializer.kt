package com.shogun.speedtest

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

class ShizukuInitializer(
    private val onStateChanged: (available: Boolean, permissionGranted: Boolean) -> Unit
) {

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        refreshState(requestIfNeeded = true)
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        refreshState(requestIfNeeded = false)
    }

    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == REQUEST_CODE) {
                onStateChanged(
                    isShizukuAvailable(),
                    grantResult == PackageManager.PERMISSION_GRANTED
                )
            }
        }

    fun initialize() {
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionListener)
        refreshState(requestIfNeeded = true)
    }

    fun isShizukuAvailable(): Boolean = Shizuku.pingBinder()

    fun requestPermission() {
        if (isShizukuAvailable() && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(REQUEST_CODE)
        }
    }

    fun dispose() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionListener)
    }

    private fun refreshState(requestIfNeeded: Boolean) {
        val available = isShizukuAvailable()
        val permissionGranted = available &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        onStateChanged(available, permissionGranted)
        if (requestIfNeeded && available && !permissionGranted &&
            !Shizuku.shouldShowRequestPermissionRationale()
        ) {
            requestPermission()
        }
    }

    companion object {
        private const val REQUEST_CODE = 1001
    }
}
