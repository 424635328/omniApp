package com.example.energyflow.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri

object ShareUtils {
    fun shareImage(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri("EnergyFlow Report", uri)
        }
        context.startActivity(Intent.createChooser(intent, "分享能耗报告"))
    }

    fun shareText(context: Context, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "分享能耗报告"))
    }
}
