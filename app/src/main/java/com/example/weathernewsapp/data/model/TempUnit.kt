package com.example.weathernewsapp.data.model

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * TempUnit —— 温度单位枚举
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * * 【为什么用枚举而不是 Boolean?】
 *   Boolean 只能存"是/否",无法扩展第三种单位。
 *   将来要加"开尔文(K)"时,只需加一个枚举项,不需要改存储结构。
 *
 * * 【怎么存到 DataStore?】
 *   DataStore 只支持基础类型(Int/String/Boolean 等)。
 *   我们存 ordinal(枚举序号):
 *     CELSIUS.ordinal   = 0
 *     FAHRENHEIT.ordinal = 1
 *   读取时用 TempUnit.entries[ordinal] 恢复成枚举。
 *
 * * 【symbol 字段的作用】
 *   UI 显示用的单位符号:
 *     CELSIUS.symbol   = "°C"
 *     FAHRENHEIT.symbol = "°F"
 *   设置页的 RadioButton 可以直接用 `unit.symbol` 作为标签。
 * ═══════════════════════════════════════════════════════════════════════════
 */
enum class TempUnit(val symbol: String) {
    /** 摄氏度(中国/欧洲默认) */
    CELSIUS("°C"),

    /** 华氏度(美国默认) */
    FAHRENHEIT("°F"),
    ;

    companion object {
        /**
         * 从 ordinal 安全恢复枚举。
         *
         * 为什么需要这个?因为 DataStore 里存的是 Int,如果将来枚举顺序变了
         * (比如加了 KELVIN 插在中间),老数据的 ordinal 可能对不上。
         * `entries.getOrNull(ordinal)` 越界时返回 null,我们再 fallback 到 CELSIUS。
         *
         * @param ordinal DataStore 里存的整数序号
         * @return 对应的 TempUnit,序号无效时返回 CELSIUS
         */
        fun fromOrdinal(ordinal: Int?): TempUnit = TempUnit.entries.getOrNull(ordinal ?: -1) ?: CELSIUS
    }
}
