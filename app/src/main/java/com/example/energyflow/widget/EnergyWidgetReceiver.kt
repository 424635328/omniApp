package com.example.energyflow.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.state.updateAppWidgetState
import com.example.energyflow.data.CostEngine
import com.example.energyflow.data.MeterRecordDao
import com.example.energyflow.data.UserPreferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

@AndroidEntryPoint
class EnergyWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = EnergyFlowWidget()

    @Inject lateinit var meterRecordDao: MeterRecordDao
    @Inject lateinit var userPreferences: UserPreferences
    @Inject lateinit var costEngine: CostEngine

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onUpdate(
        context: Context,
        appWidgetManager: android.appwidget.AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        scope.launch { refreshWidget(context) }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        scope.launch { refreshWidget(context) }
    }

    private suspend fun refreshWidget(context: Context) {
        try {
            val records = meterRecordDao.getElectricRecords().first()
            val now = YearMonth.now()
            val thisMonth = records.filter {
                it.electricTotal != null && YearMonth.from(it.timestamp) == now
            }.sortedBy { it.timestamp }

            val kwh = if (thisMonth.size >= 2) {
                val last = thisMonth.last().electricTotal
                val first = thisMonth.first().electricTotal
                if (last != null && first != null) last - first else 0.0
            } else 0.0

            val peakRecords = thisMonth.filter { it.electricPeak != null }
            val valleyRecords = thisMonth.filter { it.electricValley != null }
            val peakKwh = if (peakRecords.size >= 2) {
                val last = peakRecords.last().electricPeak
                val first = peakRecords.first().electricPeak
                if (last != null && first != null) (last - first).coerceAtLeast(0.0) else 0.0
            } else 0.0
            val valleyKwh = if (valleyRecords.size >= 2) {
                val last = valleyRecords.last().electricValley
                val first = valleyRecords.first().electricValley
                if (last != null && first != null) (last - first).coerceAtLeast(0.0) else 0.0
            } else 0.0

            val bill = costEngine.calculateBill(kwh, peakKwh, valleyKwh)
            val monthLabel = "${now.year}年${now.monthValue}月"

            val manager = GlanceAppWidgetManager(context)
            val glanceIds = manager.getGlanceIds(EnergyFlowWidget::class.java)
            glanceIds.forEach { glanceId ->
                updateAppWidgetState(context, glanceId) { prefs ->
                    prefs[EnergyFlowWidget.KEY_COST] = bill.totalCost
                    prefs[EnergyFlowWidget.KEY_KWH] = kwh
                    prefs[EnergyFlowWidget.KEY_MONTH] = monthLabel
                }
                glanceAppWidget.update(context, glanceId)
            }
        } catch (e: Exception) {
            android.util.Log.w("EnergyWidgetReceiver", "refreshWidget failed", e)
        }
    }
}
