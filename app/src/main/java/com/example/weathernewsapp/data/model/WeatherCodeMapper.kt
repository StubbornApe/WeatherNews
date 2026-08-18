package com.example.weathernewsapp.data.model

/**
 * WMO weather_code → 中文描述 的简化映射。
 * 参考:https://open-meteo.com/en/docs
 */
object WeatherCodeMapper {

    fun desc(code: Int): String = when (code) {
        0 -> "晴"
        1 -> "少云"
        2 -> "多云"
        3 -> "阴"
        45, 48 -> "雾"
        in 51..57 -> "毛毛雨"
        in 61..67 -> "雨"
        in 71..77 -> "雪"
        in 80..82 -> "阵雨"
        in 85..86 -> "阵雪"
        in 95..99 -> "雷暴"
        else -> "未知($code)"
    }
}