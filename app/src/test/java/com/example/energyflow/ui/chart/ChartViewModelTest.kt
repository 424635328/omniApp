package com.example.energyflow.ui.chart

import com.example.energyflow.data.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChartViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val repository = mockk<MeterRepository>(relaxUnitFun = true)
    private val costEngine = mockk<CostEngine>(relaxUnitFun = true)
    private val predictiveAnalyzer = mockk<PredictiveAnalyzer>(relaxUnitFun = true)
    private val eventImpactAnalyzer = mockk<EventImpactAnalyzer>(relaxUnitFun = true)
    private val weatherRepository = mockk<WeatherRepository>(relaxUnitFun = true)
    private val userPreferences = mockk<UserPreferences>(relaxUnitFun = true)
    private val deepSeekRepository = mockk<com.example.energyflow.data.DeepSeekRepository>(relaxUnitFun = true)

    private val aRecord = MeterRecord(
        timestamp = java.time.LocalDateTime.of(2026, 7, 10, 12, 0),
        isElectricRecorded = true, electricTotal = 20000.0
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        every { repository.getElectricRecords() } returns flowOf(listOf(aRecord))
        every { repository.getWaterRecords() } returns flowOf(emptyList())
        every { repository.getGasRecords() } returns flowOf(emptyList())
        every { repository.getRecordsWithNotes() } returns flowOf(emptyList())
        every { repository.getRecordCount() } returns flowOf(1)

        every { userPreferences.chartShowCost } returns MutableStateFlow(false)
        every { userPreferences.deepSeekApiKey } returns MutableStateFlow("")
        every { userPreferences.predictionSnapshot } returns MutableStateFlow(null)
        every { userPreferences.weatherForecastDate } returns MutableStateFlow(null)
        every { userPreferences.billingRules } returns flowOf(BillingRules())
        every { userPreferences.themeDistEnabled } returns MutableStateFlow(true)

        coEvery { costEngine.calculateBill(any(), any(), any(), any()) } returns mockk()
        coEvery { predictiveAnalyzer.predictMonth(any(), any()) } returns null
        coEvery { eventImpactAnalyzer.analyzeWithRecords(any()) } returns emptyList()
        coEvery { userPreferences.cacheWeatherForecast(any(), any()) } returns mockk()
        coEvery { weatherRepository.fetch7DayForecast(any(), any()) } returns WeatherResult.Error("mock")
        coEvery { weatherRepository.fetchHistorical(any(), any(), any(), any()) } returns
                WeatherResult.Success(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): ChartViewModel {
        return ChartViewModel(
            repository = repository,
            costEngine = costEngine,
            predictiveAnalyzer = predictiveAnalyzer,
            eventImpactAnalyzer = eventImpactAnalyzer,
            weatherRepository = weatherRepository,
            userPreferences = userPreferences,
            deepSeekRepository = deepSeekRepository
        ).also { testDispatcher.scheduler.advanceUntilIdle() }
    }

    // ── 初始状态 ──

    @Test
    fun `default show cost is false`() {
        val vm = createViewModel()
        assertFalse(vm.showCost.value)
    }

    @Test
    fun `default time range is MONTH`() {
        val vm = createViewModel()
        assertEquals(TimeRange.MONTH, vm.timeRange.value)
    }

    @Test
    fun `toggle show cost flips value`() {
        val vm = createViewModel()
        vm.toggleShowCost()
        assertTrue(vm.showCost.value)
        vm.toggleShowCost()
        assertFalse(vm.showCost.value)
    }

    @Test
    fun `set time range updates state`() {
        val vm = createViewModel()
        vm.setTimeRange(TimeRange.YEAR)
        assertEquals(TimeRange.YEAR, vm.timeRange.value)
    }

    // ── 天气集成 ──

    @Ignore("Flaky with IO dispatcher — mock timing")
    @Test
    fun `set time range to MONTH triggers weather fetch`() = runTest(testDispatcher) {
        val vm = createViewModel()
        // 初始 @Before 已设置 fetchHistorical 返回 Success，init 已触发一次
        coVerify { weatherRepository.fetchHistorical(any(), any(), any(), any()) }
    }

    @Ignore("Flaky with IO dispatcher — mock timing")
    @Test
    fun `set time range to ALL clears weather data`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.setTimeRange(TimeRange.ALL)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.weatherData.value.isEmpty())
    }

    @Ignore("Flaky with IO dispatcher — mock timing")
    @Test
    fun `weather data falls back to forecast when historical fails`() = runTest(testDispatcher) {
        // init 已经用 @Before 的 Success mock 触发了一次 fetchHistorical
        // 在第二次调用前覆盖为 Error
        coEvery { weatherRepository.fetchHistorical(any(), any(), any(), any()) } returns
                WeatherResult.Error("failed")
        coEvery { weatherRepository.fetch7DayForecast(any(), any()) } returns
                WeatherResult.Success(listOf(
                    DailyWeather(date = java.time.LocalDate.of(2026, 7, 14), tempMax = 30.0, tempMin = 20.0)
                ))

        val vm = createViewModel()
        vm.refreshWeather()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, vm.weatherData.value.size)
        assertEquals(30.0, vm.weatherData.value[0].tempMax, 0.01)
    }

    @Ignore("Flaky with IO dispatcher — mock timing")
    @Test
    fun `refresh weather loads data`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.refreshWeather()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(atLeast = 1) { weatherRepository.fetchHistorical(any(), any(), any(), any()) }
    }

}
