package com.example.energyflow.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.example.energyflow.data.InsertResult
import com.example.energyflow.data.MeterRecord
import com.example.energyflow.data.MeterRepository
import dagger.hilt.android.AndroidEntryPoint
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * 接收其它 App 分享（ACTION_SEND）或长按选中文本（ACTION_PROCESS_TEXT）的读数文本，
 * 后台静默解析并保存，Toast 反馈结果。透明主题，不渲染任何 UI。
 */
@AndroidEntryPoint
class ShareReceiverActivity : ComponentActivity() {

    @Inject lateinit var meterRepository: MeterRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val text = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_PROCESS_TEXT ->
                intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            else -> null
        }?.trim()

        if (text.isNullOrEmpty()) {
            Toast.makeText(this, "未收到文本", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        lifecycleScope.launch {
            when (val result = meterRepository.smartInsert(text)) {
                is InsertResult.Success -> {
                    val time = result.record.timestamp.format(TIME_FORMATTER)
                    Toast.makeText(
                        this@ShareReceiverActivity,
                        "⚡ 已记录 $time ${buildSummary(result.record)}",
                        Toast.LENGTH_LONG
                    ).show()
                }
                is InsertResult.Warning -> {
                    Toast.makeText(
                        this@ShareReceiverActivity,
                        "${result.message}，请在应用内确认后手动录入",
                        Toast.LENGTH_LONG
                    ).show()
                }
                is InsertResult.Error -> {
                    Toast.makeText(
                        this@ShareReceiverActivity,
                        "解析失败：${result.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            finish()
        }
    }

    private fun buildSummary(record: MeterRecord): String {
        val parts = mutableListOf<String>()
        record.electricTotal?.let { parts.add("电 $it") }
        record.waterTotal?.let { parts.add("水 $it") }
        record.gasTotal?.let { parts.add("燃气 $it") }
        if (parts.isEmpty() && record.note != null) {
            parts.add("备注")
        }
        return parts.joinToString(" / ")
    }

    companion object {
        private val TIME_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm")
    }
}
