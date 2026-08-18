package com.example.weathernewsapp.data.repository

import android.util.Log
import com.example.weathernewsapp.common.NetworkResult
import com.example.weathernewsapp.common.retryOnNetworkError
import com.example.weathernewsapp.common.toErrorType
import com.example.weathernewsapp.data.datastore.SettingsDataStore
import com.example.weathernewsapp.data.local.dao.WeatherDao
import com.example.weathernewsapp.data.local.entity.toDomain
import com.example.weathernewsapp.data.local.entity.toEntity
import com.example.weathernewsapp.data.model.CityCoordinates
import com.example.weathernewsapp.data.model.TempUnit
import com.example.weathernewsapp.data.model.Weather
import com.example.weathernewsapp.data.model.WeatherCodeMapper
import com.example.weathernewsapp.data.remote.WeatherApi
import com.example.weathernewsapp.data.remote.dto.WeatherResponseDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * WeatherRepository —— 天气数据层门面(Day 08 版本:Room 双层缓存)
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * 【Repository 是干嘛的?】
 *   Repository 是"数据层的统一门面":UI/ViewModel 不直接调网络 API 或 DAO,
 *   而是调 Repository。Repository 负责决定"这份数据从哪来":
 *
 *   ┌───────────┐  success ──► write Room ──► return Success
 *   │  Network  │
 *   └─────┬─────┘  failure ──► read Room
 *         │                          ├─ 有缓存 ──► return Success(cache, isFromCache=true)
 *         │                          └─ 无缓存 ──► return Error
 *         ▼
 *   ┌───────────┐
 *   │   Room    │  ← 离线兜底
 *   └───────────┘
 *
 * 【和 Day 07 版本的区别】
 *   · 构造函数新增 Context 参数,用来拿 AppDatabase 实例
 *   · 网络成功后立即把数据 insert 到 Room(覆盖式 upsert)
 *   · 网络失败(catch 分支)不直接返错,先读 Room 兜底:
 *       - 读得到 → 返回 Success(离线数据)
 *       - 读不到 → 返回原网络错误
 *
 * 【UI 层零改动】
 *   接口签名 `suspend fun getCurrentWeather(...): NetworkResult<Weather>` 没变,
 *   Weather 只是多了个 isFromCache 字段(默认 false,旧代码不读这个字段也不崩)。
 *   这就是分层的好处——数据来源变化不影响上层。
 * ═══════════════════════════════════════════════════════════════════════════
 */
class WeatherRepository @Inject constructor(
    // ⭐ Day 08 旧写法已删:不再需要 Context 来初始化 AppDatabase
    //   现在 WeatherDao 由 Hilt 注入(见 di/AppModule.kt 的 provideWeatherDao)

    // 网络 API(由 Hilt 注入,见 di/AppModule.kt 的 provideWeatherApi)
    private val api: WeatherApi,

    // 用户设置存储(由 Hilt 注入,见 di/AppModule.kt 的 provideSettingsDataStore)
    private val settingsDataStore: SettingsDataStore,

    // ⭐ Day 14 新增:WeatherDao 由 Hilt 注入,不再手动从 db 取
    //   替代原来的 `private val db = ...; private val dao = db.weatherDao()` 两行样板代码
    private val dao: WeatherDao
) {

    /**
     * 获取指定城市的天气。
     *
     * 【策略】"在线取新数据,离线读缓存":
     *   1) 先用 retryOnNetworkError 最多重试 2 次发起网络请求
     *   2) 网络成功 → 把数据 insert 到 Room → 返回 Success(新数据)
     *   3) 网络失败 → 读 Room 缓存:
     *        · 有缓存 → 返回 Success(旧数据,isFromCache=true)
     *        · 没缓存 → 返回 Error(原网络错误类型)
     *
     * @param cityName 城市名("北京"),同时作为 Room 缓存的主键
     * @param lat      纬度
     * @param lng      经度
     * @return NetworkResult<Weather> 永远不会抛异常(除 CancellationException)
     */
    suspend fun getCurrentWeather(
        cityName: String,
        lat: Double,
        lng: Double
    ): NetworkResult<Weather> {

        return try {
            // ─────────────────────────────────────────────────────────────
            //  1) 走网络
            // ─────────────────────────────────────────────────────────────
            // retryOnNetworkError:Day 07 写的重试工具,遇到 IOException 重试 2 次
            // 成功返回 dto,失败抛异常(进入下面的 catch)
            val dto = retryOnNetworkError(times = 2) {
                api.getCurrentWeather(
                    lat = lat,
                    lng = lng,
                    current = "temperature_2m,weather_code,wind_speed_10m",
                    timezone = "Asia/Shanghai"
                )
            }

            // DTO → Domain(业务模型),标记 isFromCache = false(新数据)
            val weather = dto.toDomain(cityName, isFromCache = false)

            // ⭐ 网络成功:立刻写入 Room 缓存
            //   用 try/catch 包住是因为"缓存写挂了不能影响主流程"——
            //   网络数据已经拿到了,UI 应该展示,写库失败只记日志。
            //   (实际 Room 写挂几乎不可能,但防御性编程该做)
            try {
                // weather.toEntity():Domain → Entity(默认用 System.currentTimeMillis()
                //   作为 cachedAt 时间戳)
                dao.insert(weather.toEntity())
                Log.d(TAG, "cache written for city=$cityName")
            } catch (e: Exception) {
                Log.e(TAG, "cache write failed (proceeding without cache)", e)
            }

            // 返回新数据给 UI
            NetworkResult.Success(weather)

        } catch (e: CancellationException) {
            // ─────────────────────────────────────────────────────────────
            //  协程被取消(用户退出页面 / Fragment 销毁)→ 必须原样抛出
            // ─────────────────────────────────────────────────────────────
            // 为什么不能吞?因为协程取消是"协作式"的:如果你在这里把它包成
            // NetworkResult.Error,上层无法知道协程已取消,
            // 会导致协程继续占线程 / 内存泄漏。
            throw e

        } catch (e: Exception) {
            // ─────────────────────────────────────────────────────────────
            //  2) 网络失败:兜底读 Room 缓存
            // ─────────────────────────────────────────────────────────────
            Log.w(TAG, "network failed, trying to fallback to cache", e)

            // 读缓存也用 try/catch:极端情况下(数据库损坏 / 首次安装迁移失败)
            // DAO 操作也可能抛异常,这时和网络错误一起走到"无缓存"分支。
            val cachedWeather: Weather? = try {
                // dao.getByCity(cityName):返回 WeatherEntity? (可空)
                //   ?.toDomain():Entity → Domain(链式安全调用,null 就传不过去)
                //   ?.copy(isFromCache = true):data class 的 copy 方法,
                //     只改 isFromCache 一个字段,其他字段保持数据库里的值
                dao.getByCity(cityName)
                    ?.toDomain()
                    ?.copy(isFromCache = true)
            } catch (cacheReadException: Exception) {
                Log.e(TAG, "cache read also failed", cacheReadException)
                null
            }

            if (cachedWeather != null) {
                // ⭐ 有缓存 → 当成"成功"返回,UI 可以通过 isFromCache 显示离线标签
                Log.d(TAG, "returning cached data for city=$cityName")
                NetworkResult.Success(cachedWeather)
            } else {
                // 没缓存 → 返回网络层的错误(用户看到的错误页和 Day 07 一样)
                NetworkResult.Error(type = e.toErrorType(), throwable = e)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  DTO → Domain 转换(从 WeatherResponseDto 转 Weather)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * 把网络响应 DTO 转成业务层 Weather。
     *
     * @param isFromCache 这个参数由 Repository 决定:
     *                    · 网络新数据 → false
     *                    · 从 Room 读出来(本文件另一条路径) → true(但那条路径走的是
     *                      WeatherEntity.toDomain() + copy,不经过这个函数)
     */
    private fun WeatherResponseDto.toDomain(
        cityName: String,
        isFromCache: Boolean = false
    ): Weather {
        val cur = current
        return Weather(
            cityName        = cityName,
            // 把数字温度拼上单位字符串(DTO 的 currentUnits 里是 "°C"),UI 直接显示
            temperatureText = "${cur.temperature}${currentUnits.temperature}",
            temperatureCelsius  = cur.temperature,    // ⭐ Day 10 新增:保存数值温度
            weatherCode     = cur.weatherCode,
            // WMO 天气码 → 中文描述(0→"晴", 2→"多云" 等)
            weatherDesc     = WeatherCodeMapper.desc(cur.weatherCode),
            windSpeedText   = "${cur.windSpeed} ${currentUnits.windSpeed}",
            updateTime      = cur.time,
            isFromCache     = isFromCache
        )
    }

    // ⭐ Day 11 新增:暴露默认城市 Flow,供 ViewModel 观察城市变化并自动刷新
    val defaultCityFlow: Flow<String> = settingsDataStore.defaultCityFlow

    // ⭐ Day 12 新增:暴露温度单位 Flow,供 ViewModel combine 进 uiState
    val tempUnitFlow: Flow<TempUnit> = settingsDataStore.tempUnitFlow

    // ═══════════════════════════════════════════════════════════════════════
    //  ⭐ Day 09 新增:获取"用户设置的默认城市"的天气
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * ⭐ Day 09 新增:获取"用户设置的默认城市"的天气。
     *
     * 【流程】
     *   1. 从 DataStore 读 defaultCityFlow 的当前值(用 .first())
     *   2. 用 CityCoordinates 把城市名转成经纬度
     *   3. 调用已有的 getCurrentWeather(city, lat, lng)
     *
     * 【为什么是 suspend?】
     *   DataStore 的 first() 是挂起函数,需要在协程里调。
     *   调用方(Fragment)本来就在 lifecycleScope.launch 里,直接调即可。
     *
     * @return NetworkResult<Weather> 和 getCurrentWeather 完全相同的返回类型
     */
    suspend fun getDefaultWeather(): NetworkResult<Weather> {
        // 读用户设置的城市名,如果还没设置过,flow 会发射默认值 "北京"
        val cityName = settingsDataStore.defaultCityFlow.first()

        // 城市名 → 经纬度(找不到时兜底返回北京坐标)
        val coords = CityCoordinates.fromCityNameOrDefault(cityName)

        // 复用已有的网络+缓存逻辑
        return getCurrentWeather(
            cityName = coords.cityName,
            lat = coords.lat,
            lng = coords.lng
        )
    }

    companion object {
        private const val TAG = "WeatherRepository"
    }
}
