package com.example.energyflow.ui.utils

import java.util.Locale

/**
 * 格式化工具函数
 */
object Formatters {
    private val locale = Locale.getDefault()

    /**
     * 格式化数值为整数
     */
    fun formatInt(value: Double): String {
        return String.format(locale, "%.0f", value)
    }

    /**
     * 格式化数值为一位小数
     */
    fun formatDecimal1(value: Double): String {
        return String.format(locale, "%.1f", value)
    }

    /**
     * 格式化数值为两位小数
     */
    fun formatDecimal2(value: Double): String {
        return String.format(locale, "%.2f", value)
    }

    /**
     * 格式化电量（保留两位小数，补0）
     */
    fun formatElectric(value: Double?): String {
        return if (value != null) formatDecimal2(value) else "0.00"
    }

    /**
     * 格式化水量（保留两位小数，补0）
     */
    fun formatWater(value: Double?): String {
        return if (value != null) formatDecimal2(value) else "0.00"
    }

    /**
     * 格式燃气（保留两位小数，补0）
     */
    fun formatGas(value: Double?): String {
        return if (value != null) formatDecimal2(value) else "0.00"
    }

    /**
     * 格式化日均消耗
     */
    fun formatDailyConsumption(value: Double): String {
        return String.format(locale, "%.1f 度/天", value)
    }

    /**
     * 格式化误差
     */
    fun formatError(value: Double): String {
        return String.format(locale, "%.2f", value)
    }
}
