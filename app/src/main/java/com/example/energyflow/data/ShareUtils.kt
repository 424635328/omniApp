package com.example.energyflow.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast

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
            clipData = ClipData.newPlainText("能耗手记账单", text)
        }
        context.startActivity(Intent.createChooser(intent, "分享能耗报告"))
    }

    /**
     * Share HTML content via ACTION_SEND.
     * Many apps (Notion, email clients) support text/html.
     */
    fun shareHtml(context: Context, html: String, title: String = "能耗手记账单") {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/html"
            putExtra(Intent.EXTRA_TEXT, html)
            putExtra(Intent.EXTRA_HTML_TEXT, html)
            putExtra(Intent.EXTRA_SUBJECT, title)
            clipData = ClipData.newHtmlText(title, html, html)
        }
        context.startActivity(Intent.createChooser(intent, "分享 $title"))
    }

    /**
     * Share an image to WeChat specifically.
     * Falls back to generic share if WeChat is not installed.
     */
    fun shareImageToWeChat(context: Context, uri: Uri) {
        val weChatPackage = "com.tencent.mm"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri("EnergyFlow Report", uri)
            `package` = weChatPackage
        }
        // Check if WeChat can handle this intent
        val pkgMgr = context.packageManager
        if (intent.resolveActivity(pkgMgr) != null) {
            context.startActivity(intent)
        } else {
            // Fallback: remove package restriction and show chooser
            intent.`package` = null
            context.startActivity(Intent.createChooser(intent, "分享到微信（未安装，请选择其他方式）"))
        }
    }

    /**
     * Copy formatted text to clipboard.
     * Also stores plain text for apps that don't support styled text.
     */
    fun copyToClipboard(context: Context, plainText: String, htmlText: String? = null, label: String = "能耗手记账单") {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = if (htmlText != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            ClipData.newHtmlText(label, plainText, htmlText)
        } else {
            ClipData.newPlainText(label, plainText)
        }
        clipboard.setPrimaryClip(clip)
    }
}
