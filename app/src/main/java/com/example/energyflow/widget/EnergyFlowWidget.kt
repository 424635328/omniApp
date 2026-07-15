package com.example.energyflow.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.energyflow.MainActivity

class EnergyFlowWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            WidgetContent(context)
        }
    }

    @Composable
    private fun WidgetContent(context: Context) {
        val prefs = currentState<Preferences>()
        val cost = prefs[KEY_COST] ?: 0.0
        val kwh = prefs[KEY_KWH] ?: 0.0
        val month = prefs[KEY_MONTH] ?: ""

        val intent = Intent(context, MainActivity::class.java)

        GlanceTheme {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(Color(0xFF0D0F12))
                    .padding(12.dp)
                    .clickable(actionStartActivity(intent)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = GlanceModifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "⚡ Energy Flow",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFF00FFC4)),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )

                    Spacer(modifier = GlanceModifier.height(8.dp))

                    Text(
                        text = if (cost > 0) "¥${"%.1f".format(cost)}" else "--",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFF00FFC4)),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )

                    Spacer(modifier = GlanceModifier.height(4.dp))

                    Text(
                        text = if (kwh > 0) "${"%.1f".format(kwh)} 度" else "暂无数据",
                        style = TextStyle(
                            color = ColorProvider(if (kwh > 0) Color(0xFFE2E8F0) else Color(0xFF64748B)),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )

                    Spacer(modifier = GlanceModifier.height(6.dp))

                    if (month.isNotBlank()) {
                        Text(
                            text = month,
                            style = TextStyle(
                                color = ColorProvider(Color(0xFF64748B)),
                                fontSize = 10.sp
                            )
                        )
                    }
                }
            }
        }
    }

    companion object {
        val KEY_COST = doublePreferencesKey("widget_cost")
        val KEY_KWH = doublePreferencesKey("widget_kwh")
        val KEY_MONTH = stringPreferencesKey("widget_month")
    }
}
