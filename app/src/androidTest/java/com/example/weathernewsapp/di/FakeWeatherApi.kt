package com.example.weathernewsapp.di

import com.example.weathernewsapp.data.remote.WeatherApi
import com.example.weathernewsapp.data.remote.dto.CurrentDto
import com.example.weathernewsapp.data.remote.dto.CurrentUnitsDto
import com.example.weathernewsapp.data.remote.dto.WeatherResponseDto

/**
 * 假天气接口:替代真实 Retrofit 生成的 WeatherApi 实现。
 * 提供 shouldFail 开关,让同一种 fake 既能测成功态也能测失败态。
 */
object FakeWeatherApi : WeatherApi {
    // @Volatile 保证跨线程可见(测试线程设置,协程线程读取)
    @Volatile
    var shouldFail: Boolean = false

    override suspend fun getCurrentWeather(
        lat: Double,
        lng: Double,
        current: String,
        timezone: String,
    ): WeatherResponseDto {
        // ⭐ 关键:抛非 IOException(RuntimeException),绕过 retryOnNetworkError 的 delay 重试
        if (shouldFail) throw RuntimeException("FakeWeatherApi: network down")

        // 固定返回一条"北京"的天气:28.5°C、多云、12.3 km/h
        return WeatherResponseDto(
            latitude = lat,
            longitude = lng,
            timezone = timezone,
            elevation = 0.0,
            currentUnits =
                CurrentUnitsDto(
                    time = "s",
                    temperature = "°C",
                    weatherCode = "wmo code",
                    windSpeed = "km/h",
                ),
            current =
                CurrentDto(
                    time = "2026-07-28T10:00",
                    interval = 900,
                    temperature = 28.5,
                    // 2 → WeatherCodeMapper.desc → "多云"
                    weatherCode = 2,
                    windSpeed = 12.3,
                ),
        )
    }
}
