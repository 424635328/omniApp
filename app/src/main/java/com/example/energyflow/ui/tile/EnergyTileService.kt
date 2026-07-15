package com.example.energyflow.ui.tile

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.example.energyflow.MainActivity
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
@RequiresApi(Build.VERSION_CODES.N)
class EnergyTileService : TileService() {

    @Inject lateinit var meterRecordDao: MeterRecordDao
    @Inject lateinit var costEngine: CostEngine
    @Inject lateinit var userPreferences: UserPreferences

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onTileAdded() {
        super.onTileAdded()
        refreshTile()
    }

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        unlockAndRun {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivityAndCollapse(intent)
        }
    }

    private fun refreshTile() {
        scope.launch {
            try {
                val records = meterRecordDao.getElectricRecords().first()
                val now = YearMonth.now()
                val thisMonth = records.filter {
                    it.electricTotal != null && YearMonth.from(it.timestamp) == now
                }.sortedBy { it.timestamp }

                val kwh = if (thisMonth.size >= 2)
                    thisMonth.last().electricTotal!! - thisMonth.first().electricTotal!!
                else 0.0

                val bill = if (kwh > 0) costEngine.calculateSimple(kwh) else 0.0

                val tile = qsTile ?: return@launch
                tile.label = "Energy Flow"
                tile.subtitle = if (kwh > 0)
                    "${"%.0f".format(kwh)}度 / ¥${"%.1f".format(bill)}"
                else "暂无数据"
                tile.state = Tile.STATE_ACTIVE
                tile.updateTile()
            } catch (_: Exception) {}
        }
    }
}
