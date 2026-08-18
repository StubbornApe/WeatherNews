package com.example.weathernewsapp.ui.weather

import com.example.weathernewsapp.common.ErrorType
import com.example.weathernewsapp.data.model.Weather

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * WeatherUiState —— 天气页 UI 状态的统一表达
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * 【为什么用 sealed class?】
 *   · UI 只需要 render(state) 一个函数,when 分支完全覆盖所有情况
 *   · 新增一个状态时(如 Empty / Refreshing),漏处理编译器会报错
 *   · 与 StateFlow 天然搭配(Day 12 会看到)
 *
 * 【本次先设计 4 态】
 *   · Loading  加载中(转圈)
 *   · Success  加载成功,携带 Weather
 *   · Error    加载失败,携带 ErrorType(带用户友好文案)
 *   · Idle     初始态,尚未发起过请求(可选,用于覆盖首次进入还没 loadWeather 的一瞬间)
 *
 * Day 12 变化:Success 增加 temperatureText 字段。
 *   · 之前:Fragment 拿到 Weather 后自己用 WeatherTextFormatter 算温度文本
 *   · 现在:ViewModel 通过 combine(天气状态, 温度单位) 算好文本放进 UiState
 *   · Fragment 渲染时直接 state.temperatureText,不再持有温度单位
 *
 * temperatureText 给默认值是为了"构造 Success 时可以不立即传文本"
 * (combine 块里再 copy 填上),保持向后兼容。
 * ═══════════════════════════════════════════════════════════════════════════
 */
sealed class WeatherUiState {
    object Idle : WeatherUiState()

    object Loading : WeatherUiState()

    // data class Success(val weather: Weather) : WeatherUiState()
    data class Success(
        val weather: Weather,
        // ⭐ Day 12:已按当前温度单位格式化好的温度文本,如 "23.5°C"
        val temperatureText: String = weather.temperatureText,
    ) : WeatherUiState()

    data class Error(val type: ErrorType) : WeatherUiState()
}
