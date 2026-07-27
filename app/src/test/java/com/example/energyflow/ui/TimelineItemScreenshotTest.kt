package com.example.energyflow.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.energyflow.data.MeterRecord
import com.example.energyflow.ui.theme.EnergyFlowTheme
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.LocalDateTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import android.app.Application
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * TimelineItem 截图基线测试。
 *
 * 录制基线: ./gradlew :app:recordRoborazziDebug
 * 对比验证: ./gradlew :app:verifyRoborazziDebug
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class TimelineItemScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val record = MeterRecord(
        id = 1,
        timestamp = LocalDateTime.of(2026, 7, 15, 8, 30),
        isElectricRecorded = true,
        electricTotal = 16543.0,
        electricPeak = 9876.5,
        electricValley = 6666.5,
        isWaterRecorded = true,
        waterTotal = 321.4,
        note = "空调清洗后首次记录"
    )

    @Test
    fun timelineItem_dark() {
        composeRule.setContent {
            EnergyFlowTheme(darkTheme = true) {
                TimelineItem(
                    record = record,
                    electricDelta = 12.5,
                    peakDelta = 8.0,
                    valleyDelta = 4.5,
                    waterDelta = 0.8
                )
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/timeline_item_dark.png")
    }

    @Test
    fun timelineItem_light() {
        composeRule.setContent {
            EnergyFlowTheme(darkTheme = false) {
                TimelineItem(
                    record = record,
                    electricDelta = 12.5,
                    waterDelta = 0.8
                )
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/timeline_item_light.png")
    }
}
