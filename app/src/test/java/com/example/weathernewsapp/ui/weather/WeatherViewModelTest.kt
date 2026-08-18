package com.example.weathernewsapp.ui.weather

import androidx.lifecycle.viewModelScope
import com.example.weathernewsapp.common.ErrorType
import com.example.weathernewsapp.common.NetworkResult
import com.example.weathernewsapp.data.model.TempUnit
import com.example.weathernewsapp.data.model.Weather
import com.example.weathernewsapp.data.repository.WeatherRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel    // ⭐ CoroutineScope.cancel() 顶级扩展,vm.viewModelScope.cancel() 需要它
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WeatherViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: WeatherRepository

    // 复用测试数据：网络新数据（isFromCache = false）
    private val sampleWeather = Weather(
        cityName = "北京",
        temperatureText = "20.0°C",
        temperatureCelsius = 20.0,
        weatherCode = 0,
        weatherDesc = "晴",
        windSpeedText = "10 km/h",
        updateTime = "2026-08-12T10:00",
        isFromCache = false
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** 测试 1：刚构造时,weatherState 应该是 Idle（init 协程还没跑） */
    @Test
    fun `initial state is Idle`() = runTest(testDispatcher.scheduler) {
        stubRepositorySuccess()
        val vm = WeatherViewModel(repository)
        assertEquals(WeatherUiState.Idle, vm.weatherState.value)
        vm.viewModelScope.cancel()
    }

    /** 测试 2：init 触发后,weatherState 应当从 Idle 变 Success */
    @Test
    fun `init triggers load and state becomes Success`() = runTest(testDispatcher.scheduler) {
        stubRepositorySuccess()
        val vm = WeatherViewModel(repository)
        advanceUntilIdle()

        assertEquals(WeatherUiState.Success(sampleWeather), vm.weatherState.value)
        coVerify(exactly = 1) { repository.getDefaultWeather() }
        vm.viewModelScope.cancel()
    }

    /** 测试 3：网络返回 Error 时,weatherState 变 Error,events 收到 Toast */
    @Test
    fun `error response becomes Error state and sends toast event`() = runTest(testDispatcher.scheduler) {
        every { repository.defaultCityFlow } returns flowOf("北京")
        every { repository.tempUnitFlow } returns flowOf(TempUnit.CELSIUS)
        coEvery { repository.getDefaultWeather() } returns NetworkResult.Error(ErrorType.NoNetwork)

        val vm = WeatherViewModel(repository)
        advanceUntilIdle()

        assertEquals(WeatherUiState.Error(ErrorType.NoNetwork), vm.weatherState.value)
        assertEquals(
            WeatherEvent.ShowToast("网络不可用,请检查连接"),
            vm.events.first()
        )
        vm.viewModelScope.cancel()
    }

    /** 测试 4：缓存命中(isFromCache=true)时,events 收到"网络不给力"Toast */
    @Test
    fun `cache response sends offline toast event`() = runTest(testDispatcher.scheduler) {
        every { repository.defaultCityFlow } returns flowOf("北京")
        every { repository.tempUnitFlow } returns flowOf(TempUnit.CELSIUS)
        coEvery { repository.getDefaultWeather() } returns
            NetworkResult.Success(sampleWeather.copy(isFromCache = true))

        val vm = WeatherViewModel(repository)
        advanceUntilIdle()

        assertTrue(vm.weatherState.value is WeatherUiState.Success)
        assertEquals(
            WeatherEvent.ShowToast("网络不给力,已显示缓存数据"),
            vm.events.first()
        )
        vm.viewModelScope.cancel()
    }

    /** 测试 5：retry() 触发后,getDefaultWeather 应当被调用第二次 */
    @Test
    fun `retry triggers reload`() = runTest(testDispatcher.scheduler) {
        stubRepositorySuccess()
        val vm = WeatherViewModel(repository)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.getDefaultWeather() }

        vm.retry()
        advanceUntilIdle()

        coVerify(exactly = 2) { repository.getDefaultWeather() }
        vm.viewModelScope.cancel()
    }

    // 提取一个"成功路径"的桩,避免每个测试重复写 3 行
    private fun stubRepositorySuccess() {
        every { repository.defaultCityFlow } returns flowOf("北京")
        every { repository.tempUnitFlow } returns flowOf(TempUnit.CELSIUS)
        coEvery { repository.getDefaultWeather() } returns NetworkResult.Success(sampleWeather)
    }
}
