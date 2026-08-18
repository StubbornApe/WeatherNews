package com.example.weathernewsapp.data.model

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * CityCoordinates —— 城市名 ↔ 经纬度映射表
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * 【为什么需要这个表?】
 *   Open-Meteo API 调用时需要传 latitude / longitude,而不是城市名。
 *   用户在设置页看到的是"北京"这样的中文名,需要转换成 (39.9042, 116.4074)。
 *
 * 【为什么不做地理编码 API?】
 *   Open-Meteo 有一个免费的 geocoding API 可以把城市名转经纬度,
 *   但那需要额外一次网络请求,且会增加 Day 09 的复杂度。
 *   今天用本地硬编码表覆盖 6 个常用中国城市,简单直接。
 *   Day 10+ 可以接入 geocoding API 做"任意城市搜索"。
 *
 * * 【lat / lng 字段说明】
 *   · lat = latitude  = 纬度(北纬为正,如北京 39.9°)
 *   · lng = longitude = 经度(东经为正,如北京 116.4°)
 *   · 注意不要搞反!Open-Meteo 的参数顺序是 latitude 在前,longitude 在后
 * ═══════════════════════════════════════════════════════════════════════════
 */
data class CityCoordinates(
    val cityName: String,
    val lat: Double,
    val lng: Double
) {
    companion object {
        /**
         * 预置城市表。key 是城市中文名,value 是经纬度。
         *
         * 这些经纬度来自 Open-Meteo 的 geocoding API 公开数据,精度到小数点后 4 位
         * (约 11 米精度,天气查询完全够用)。
         */
        private val CITY_MAP: Map<String, CityCoordinates> = listOf(
            CityCoordinates("北京", 39.9042, 116.4074),
            CityCoordinates("上海", 31.2304, 121.4737),
            CityCoordinates("广州", 23.1291, 113.2644),
            CityCoordinates("深圳", 22.5431, 114.0579),
            CityCoordinates("成都", 30.5728, 104.0668),
            CityCoordinates("杭州", 30.2741, 120.1551)
        ).associateBy { it.cityName }

        /** 支持的城市名列表,设置页的城市选择器可以直接用 */
        val SUPPORTED_CITIES: List<String> = CITY_MAP.keys.toList()

        /**
         * 根据城市名查经纬度。
         *
         * @return 找到的 CityCoordinates;找不到返回 null(比如用户手动输入了不支持的城市)
         */
        fun fromCityName(cityName: String): CityCoordinates? = CITY_MAP[cityName]

        /**
         * 根据城市名查经纬度,找不到时返回北京的坐标(兜底默认值)。
         * 这样即使用户输入了不支持的城市,也不会崩,只是查的是北京天气。
         */
        fun fromCityNameOrDefault(cityName: String): CityCoordinates =
            CITY_MAP[cityName] ?: CITY_MAP.getValue("北京")
    }
}