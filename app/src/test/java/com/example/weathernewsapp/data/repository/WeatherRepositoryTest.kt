package com.example.weathernewsapp.data.repository

import com.example.weathernewsapp.common.ErrorType
import com.example.weathernewsapp.common.NetworkResult
import com.example.weathernewsapp.data.datastore.SettingsDataStore
import com.example.weathernewsapp.data.local.dao.WeatherDao
import com.example.weathernewsapp.data.local.entity.WeatherEntity
import com.example.weathernewsapp.data.remote.WeatherApi
import com.example.weathernewsapp.data.remote.dto.CurrentDto
import com.example.weathernewsapp.data.remote.dto.CurrentUnitsDto
import com.example.weathernewsapp.data.remote.dto.WeatherResponseDto
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.UnknownHostException

/**
 * WeatherRepository 单测
 *
 * 测试范围:网络成功写缓存 / getDefaultWeather 读 DataStore / 缓存命中 / 无缓存 Error / 5xx 映射
 * Mock 边界:WeatherApi(Retrofit 接口) + WeatherDao(Room DAO) + SettingsDataStore(DataStore)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WeatherRepositoryTest {
    private lateinit var api: WeatherApi
    private lateinit var dao: WeatherDao
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var repository: WeatherRepository

    // 网络成功时 API 返回的"新数据"DTO
    private val sampleDto =
        WeatherResponseDto(
            latitude = 39.9042,
            longitude = 116.4074,
            timezone = "Asia/Shanghai",
            elevation = 0.0,
            currentUnits =
                CurrentUnitsDto(
                    time = "2026-08-13T10:00",
                    temperature = "°C",
                    weatherCode = "wmo code",
                    windSpeed = "km/h",
                ),
            current =
                CurrentDto(
                    time = "2026-08-13T10:00",
                    interval = 900,
                    temperature = 28.5,
                    weatherCode = 0,
                    windSpeed = 12.0,
                ),
        )

    // Room 里已有的缓存 Entity(断网兜底用)
    private val cachedEntity =
        WeatherEntity(
            cityName = "北京",
            temperature = "26.0°C",
            temperatureCelsius = 26.0,
            weatherCode = 1,
            weatherDesc = "晴",
            windSpeed = "10.0 km/h",
            updateTime = "2026-08-12T10:00",
            cachedAt = 0L,
        )

    @Before
    fun setUp() {
        api = mockk()
        dao = mockk()
        // relaxed:未桩的 Flow / 方法返回默认值,避免构造时访问 defaultCityFlow/tempUnitFlow NPE
        settingsDataStore = mockk(relaxed = true)
        repository = WeatherRepository(api, settingsDataStore, dao)
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  1. 网络成功 → 写 Room → 返回 Success(isFromCache=false)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `success writes to Room`() =
        runTest {
            // Given: 网络成功返回 sampleDto;Room insert 成功(just Runs)
            coEvery { api.getCurrentWeather(any(), any(), any(), any()) } returns sampleDto
            coEvery { dao.insert(any()) } just Runs

            // When
            val result = repository.getCurrentWeather("北京", 39.9042, 116.4074)

            // Then: 返回 Success,isFromCache=false,且 Room 被写了一次
            assertTrue(result is NetworkResult.Success)
            val weather = (result as NetworkResult.Success).data
            assertEquals("北京", weather.cityName)
            assertEquals(false, weather.isFromCache)
            assertEquals(28.5, weather.temperatureCelsius, 0.0)
            coVerify(exactly = 1) { dao.insert(any()) }
        }

    // ═══════════════════════════════════════════════════════════════════════
    //  2. getDefaultWeather 从 DataStore 读默认城市后走网络
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `getDefaultWeather reads DataStore default city`() =
        runTest {
            // Given: DataStore 默认城市 = "北京";网络成功;Room insert 成功
            every { settingsDataStore.defaultCityFlow } returns flowOf("北京")
            coEvery { api.getCurrentWeather(any(), any(), any(), any()) } returns sampleDto
            coEvery { dao.insert(any()) } just Runs

            // When
            val result = repository.getDefaultWeather()

            // Then: 返回北京天气,且确实走了网络请求
            assertTrue(result is NetworkResult.Success)
            val weather = (result as NetworkResult.Success).data
            assertEquals("北京", weather.cityName)
            coVerify(exactly = 1) { api.getCurrentWeather(any(), any(), any(), any()) }
        }

    // ═══════════════════════════════════════════════════════════════════════
    //  3. 网络失败 + Room 有缓存 → 返回 Success(isFromCache=true)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `network fail with cache - returns Success isFromCache`() =
        runTest {
            // Given: 网络失败;Room 有缓存
            coEvery { api.getCurrentWeather(any(), any(), any(), any()) } throws UnknownHostException("dns")
            coEvery { dao.getByCity("北京") } returns cachedEntity

            // When
            val result = repository.getCurrentWeather("北京", 39.9042, 116.4074)

            // Then: 返回 Success,且 isFromCache=true(离线兜底)
            assertTrue(result is NetworkResult.Success)
            val weather = (result as NetworkResult.Success).data
            assertEquals(true, weather.isFromCache)
            assertEquals("北京", weather.cityName)
            // 验证兜底路径真的查了 Room
            coVerify(exactly = 1) { dao.getByCity("北京") }
        }

    // ═══════════════════════════════════════════════════════════════════════
    //  4. 网络失败 + Room 无缓存 → 返回 Error(NoNetwork)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `network fail with no cache - returns Error NoNetwork`() =
        runTest {
            // Given: 网络失败;Room 查不到缓存(返回 null)
            coEvery { api.getCurrentWeather(any(), any(), any(), any()) } throws UnknownHostException("dns")
            coEvery { dao.getByCity("北京") } returns null

            // When
            val result = repository.getCurrentWeather("北京", 39.9042, 116.4074)

            // Then: 返回 Error,类型是 NoNetwork;且 Room 确实被查过
            assertTrue(result is NetworkResult.Error)
            val error = result as NetworkResult.Error
            assertEquals(ErrorType.NoNetwork, error.type)
            coVerify(exactly = 1) { dao.getByCity("北京") }
        }

    // ═══════════════════════════════════════════════════════════════════════
    //  5. HTTP 5xx → ErrorType.Server(500)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `http 5xx - returns Server error type`() =
        runTest {
            // Given: Retrofit 抛 HttpException(code=500);无缓存
            val httpException =
                retrofit2.HttpException(
                    retrofit2.Response.error<Any>(500, okhttp3.ResponseBody.create(null, "")),
                )
            coEvery { api.getCurrentWeather(any(), any(), any(), any()) } throws httpException
            coEvery { dao.getByCity("北京") } returns null

            // When
            val result = repository.getCurrentWeather("北京", 39.9042, 116.4074)

            // Then: Error(type=Server(500))
            assertTrue(result is NetworkResult.Error)
            val error = result as NetworkResult.Error
            assertEquals(ErrorType.Server(500), error.type)
        }
}
