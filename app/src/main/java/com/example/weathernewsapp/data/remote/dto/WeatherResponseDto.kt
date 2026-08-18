package com.example.weathernewsapp.data.remote.dto

// ============ import ============
import com.google.gson.annotations.SerializedName // Gson 字段映射注解

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * WeatherResponseDto —— Open-Meteo /v1/forecast 接口的响应模型
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * 【DTO 是什么?】
 *   Data Transfer Object —— 数据传输对象。专门描述"网络层收到的原始数据形状",
 *   与 UI 层展示用的领域模型(Domain Model)分开,避免 API 变更时上层大量改动。
 *
 * 【为什么不直接把 API 响应当 UI 模型用?】
 *   1. API 字段可能很多,UI 只用其中一小部分
 *   2. API 字段名可能不符合业务语义(如 temperature_2m,业务层想叫 temperature)
 *   3. 未来换 API 供应商时,只需要改 Repository 里的映射,UI 层零改动
 *
 * 【本文件包含 4 个 DTO】(严格贴合 JSON 结构)
 *   · WeatherResponseDto:顶层响应
 *   · CurrentUnitsDto:各字段的单位
 *   · CurrentDto:实况数据
 *   · (未来 hourly / daily 也可以类似定义)
 * ═══════════════════════════════════════════════════════════════════════════
 */
data class WeatherResponseDto(
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("timezone") val timezone: String,
    @SerializedName("elevation") val elevation: Double,
    @SerializedName("current_units") val currentUnits: CurrentUnitsDto,
    @SerializedName("current") val current: CurrentDto,
)

/** 各字段的单位(温度=°C,风速=km/h,等) */
data class CurrentUnitsDto(
    @SerializedName("time") val time: String,
    @SerializedName("temperature_2m") val temperature: String,
    @SerializedName("weather_code") val weatherCode: String,
    @SerializedName("wind_speed_10m") val windSpeed: String,
)

/**
 * 实况数据主体。
 * 注意:JSON 里 key 是 snake_case + 尾部带 "_2m" / "_10m" 后缀
 * (2m = 距地面 2 米高度处的气温;10m = 10 米高度处的风速),
 * Kotlin 侧统一简化为 temperature / windSpeed。
 */
data class CurrentDto(
    // ISO8601:"2026-07-28T10:00"
    @SerializedName("time") val time: String,
    // 数据刷新间隔(秒)
    @SerializedName("interval") val interval: Int,
    @SerializedName("temperature_2m") val temperature: Double,
    @SerializedName("weather_code") val weatherCode: Int,
    @SerializedName("wind_speed_10m") val windSpeed: Double,
)
