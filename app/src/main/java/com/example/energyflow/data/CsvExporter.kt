package com.example.energyflow.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 抄表记录 CSV 导出器。
 *
 * - [buildCsv] 为纯函数（不依赖 Android），null 字段留空以保留 null ≠ 0.0 语义；
 * - [export] 写入 MediaStore Downloads/EnergyFlow，供 Excel 等外部工具打开。
 */
object CsvExporter {

    /** UTF-8 BOM — 保证 Excel 正确识别中文编码。 */
    private const val BOM = "\uFEFF"

    private const val HEADER = "时间戳,电表总读数,峰,谷,水表,燃气,备注"

    private val FILE_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

    /**
     * 构建 CSV 文本。
     *
     * 列：时间戳（ISO）、电表总读数、峰、谷、水表、燃气、备注。
     * null 字段输出为空（不是 0）；备注按 RFC 4180 转义。
     */
    fun buildCsv(records: List<MeterRecord>): String = buildString {
        append(BOM)
        appendLine(HEADER)
        for (record in records) {
            append(record.timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            append(',').append(record.electricTotal?.toString().orEmpty())
            append(',').append(record.electricPeak?.toString().orEmpty())
            append(',').append(record.electricValley?.toString().orEmpty())
            append(',').append(record.waterTotal?.toString().orEmpty())
            append(',').append(record.gasTotal?.toString().orEmpty())
            append(',').append(escape(record.note.orEmpty()))
            appendLine()
        }
    }

    /** RFC 4180：字段含逗号/引号/换行时用双引号包裹，内部引号翻倍。 */
    private fun escape(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }

    /**
     * 导出 CSV 到 MediaStore Downloads/EnergyFlow。
     *
     * @return 写入成功返回文件 Uri，失败返回 null
     */
    suspend fun export(context: Context, records: List<MeterRecord>): Uri? =
        withContext(Dispatchers.IO) {
            try {
                val fileName = "EnergyFlow_${LocalDateTime.now().format(FILE_NAME_FORMATTER)}.csv"
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                    put(
                        MediaStore.Downloads.RELATIVE_PATH,
                        "${Environment.DIRECTORY_DOWNLOADS}/EnergyFlow"
                    )
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext null
                resolver.openOutputStream(uri)?.use { out ->
                    out.write(buildCsv(records).toByteArray(Charsets.UTF_8))
                } ?: return@withContext null
                uri
            } catch (e: Exception) {
                null
            }
        }
}
