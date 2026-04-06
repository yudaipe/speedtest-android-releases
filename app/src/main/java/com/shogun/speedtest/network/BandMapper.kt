package com.shogun.speedtest.network

import android.content.Context
import org.json.JSONObject

object BandMapper {

    private data class BandRange(val band: Int, val min: Int, val max: Int)

    private var lteBands: List<BandRange>? = null
    private var nrBands: List<BandRange>? = null

    private fun ensureLoaded(context: Context) {
        if (lteBands != null) return
        val json = context.assets.open("band_mapping.json").bufferedReader().use { it.readText() }
        val root = JSONObject(json)

        val lteArray = root.getJSONArray("lte")
        lteBands = (0 until lteArray.length()).map { i ->
            val obj = lteArray.getJSONObject(i)
            BandRange(obj.getInt("band"), obj.getInt("earfcn_min"), obj.getInt("earfcn_max"))
        }

        val nrArray = root.getJSONArray("nr")
        nrBands = (0 until nrArray.length()).map { i ->
            val obj = nrArray.getJSONObject(i)
            BandRange(obj.getInt("band"), obj.getInt("nrarfcn_min"), obj.getInt("nrarfcn_max"))
        }
    }

    fun getBandFromEarfcn(context: Context, earfcn: Int?): Int? {
        if (earfcn == null || earfcn <= 0 || earfcn == Int.MAX_VALUE) return null
        ensureLoaded(context)
        return lteBands?.firstOrNull { earfcn in it.min..it.max }?.band
    }

    fun getBandFromNrArfcn(context: Context, nrarfcn: Int?): Int? {
        if (nrarfcn == null || nrarfcn <= 0 || nrarfcn == Int.MAX_VALUE) return null
        ensureLoaded(context)
        return nrBands?.firstOrNull { nrarfcn in it.min..it.max }?.band
    }
}
