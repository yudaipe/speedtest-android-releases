package com.shogun.speedtest.shizuku

import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import android.telephony.PhysicalChannelConfig
import com.shogun.speedtest.debug.HiddenRadioDebugLog

class PhysicalChannelConfigListenerBinder(
    private val onChanged: (List<PhysicalChannelConfig>) -> Unit
) : Binder(), IInterface {

    init {
        attachInterface(this, DESCRIPTOR)
    }

    override fun asBinder(): IBinder = this

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code in IBinder.FIRST_CALL_TRANSACTION..IBinder.LAST_CALL_TRANSACTION) {
            data.enforceInterface(DESCRIPTOR)
        }
        return when (code) {
            INTERFACE_TRANSACTION -> {
                reply?.writeString(DESCRIPTOR)
                true
            }

            TRANSACTION_ON_PHYSICAL_CHANNEL_CONFIG_CHANGED -> {
                val configs = data.createTypedArrayList(PhysicalChannelConfig.CREATOR).orEmpty()
                HiddenRadioDebugLog.add("listener_callback", "stub configs=${configs.size}")
                onChanged(configs)
                true
            }

            else -> super.onTransact(code, data, reply, flags)
        }
    }

    companion object {
        const val DESCRIPTOR = "com.android.internal.telephony.IPhoneStateListener"
        const val TRANSACTION_ON_PHYSICAL_CHANNEL_CONFIG_CHANGED =
            IBinder.FIRST_CALL_TRANSACTION + 32
    }
}
