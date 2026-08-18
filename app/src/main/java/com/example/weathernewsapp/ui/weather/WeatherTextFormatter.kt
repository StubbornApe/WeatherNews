package com.example.weathernewsapp.ui.weather

import com.example.weathernewsapp.data.model.TempUnit
import kotlin.math.round

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * WeatherTextFormatter —— 天气数据格式化工具
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * 【为什么用 object 而不是 class?】
 *   这个类没有状态(不需要保存成员变量),只用一堆工具方法。
 *   Kotlin 的 object 是单例,直接用 WeatherTextFormatter.formatXxx() 调用。
 *
 * 【为什么单独建一个 Formatter?】
 *   1. 温度换算逻辑不应该写在 Fragment 里(UI 只管展示)
 *   2. Day 11 引入 ViewModel 后这个类直接被 ViewModel 使用
 *   3. 方便单元测试——纯 Kotlin 类,不需要 Android 依赖
 */
object WeatherTextFormatter {

    /**
     * 摄氏度 → 华氏度.
     * 公式: F = C × 9/5 + 32
     *
     * 这是 Double 的【扩展函数】:在 object 内部定义,
     * 调用方式是 "25.0".toFahrenheit() 但需要先 import 这个方法。
     */
    private fun Double.toFahrenheit(): Double = this * 9.0 / 5.0 + 32.0

    /**
     * 保留 1 位小数.
     * round(23.456 * 10) / 10 = round(234.56) / 10 = 235 / 10 = 23.5
     */
    private fun Double.roundTo1(): Double = round(this * 10) / 10.0

    /**
     * 根据温度单位格式化温度文本.
     *
     * @param celsius 摄氏度原始值
     * @param unit 用户选择的温度单位(CELSIUS / FAHRENHEIT)
     * @return 如 "23.5°C" 或 "74.3°F"
     */
    fun formatTemperature(celsius: Double, unit: TempUnit): String = when (unit) {
        // when 表达式穷举枚举,编译器检查是否漏了分支
        TempUnit.CELSIUS    -> "${celsius.roundTo1()}°C"
        TempUnit.FAHRENHEIT -> "${celsius.toFahrenheit().roundTo1()}°F"
    }
}