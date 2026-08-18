package com.example.weathernewsapp.data.model

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * Weather —— UI 层的天气领域模型(Domain Model)
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * 【与 DTO / Entity 的关系】
 *   · WeatherResponseDto (remote.dto):描述"服务器返回的形状",字段名随 API 走
 *   · WeatherEntity      (local.entity):描述"SQLite 表结构",字段名随列名走
 *   · Weather            (data.model,本类):描述"UI 想展示的形状",不掺任何网络/存储细节
 *
 *   Repository 负责在三者之间做映射,UI 永远只接触 Weather。
 *
 * 【字段设计】
 *   · cityName:城市名("北京")
 *   · temperatureText:已拼好单位的温度字符串,UI 直接 setText(如 "28.5°C")
 *   · weatherCode:WMO 原始整数 code,保留给未来"天气图标"用
 *   · weatherDesc:根据 weather_code 映射的人类可读文字("晴"/"多云")
 *   · windSpeedText:已拼好单位的风速字符串
 *   · updateTime:响应里 current.time 的原始 ISO8601 字符串
 *   · isFromCache:Day 08 新增。true 表示这份数据是从 Room 读出来的缓存
 *                 (断网兜底场景),UI 可以显示"离线数据"标签。
 *                 新拉到的网络数据默认 false。
 * ═══════════════════════════════════════════════════════════════════════════
 */
data class Weather(
    val cityName: String,
    // 保留:DTO 直接拼好的文本(兼容旧代码)
    val temperatureText: String,
    // ⭐ Day 10 新增:摄氏度数值,用于单位换算
    val temperatureCelsius: Double = 0.0,
    val weatherCode: Int,
    val weatherDesc: String,
    val windSpeedText: String,
    val updateTime: String,
    val isFromCache: Boolean = false,
)
