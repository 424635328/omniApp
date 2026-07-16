package com.example.energyflow

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() {
        baselineProfileRule.collectBaselineProfile(
            packageName = "com.example.energyflow",
            profileBlock = {
                startActivityAndWait()
                device.waitForIdle()
                // Home screen loaded

                // Navigate to Chart tab
                startActivityAndWait(
                    intent = androidx.benchmark.macro.StartupHelper.createIntent(
                        packageName = "com.example.energyflow",
                        action = "android.intent.action.MAIN"
                    )
                )
                device.waitForIdle()
            }
        )
    }
}
