package com.example.weathernewsapp.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * WeatherEntity —— 天气缓存表(Room Entity)
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * 【表名】weather
 * 【主键】city_name(城市名)—— 一个城市只有一行缓存
 * 【冲突策略】REPLACE —— 同城市再次 insert 时覆盖旧行,始终保持最新
 *
 * 【为什么字段全是基础类型?】
 *   SQLite 只支持 5 种基础类型(INTEGER/TEXT/REAL/BLOB/NULL)。
 *   Weather 的字段全是文本/数字,不需要 TypeConverter。
 *   (如果以后加"24 小时预报 List<HourlyForecast>",再用 TypeConverter 存 JSON)
 *
 * 【为什么有 cachedAt 字段?】
 *   记录"这条缓存是什么时候写入的"。
 *   后续可以用来判断缓存是否过期(比如超过 30 分钟就强制走网络)。
 *   目前我们只在断网时兜底,暂不判断过期,先存着备用。
 * ═══════════════════════════════════════════════════════════════════════════
 */
@Entity(tableName = "weather")
data class WeatherEntity(
    /** 城市名,主键 —— 一个城市一行缓存 */
    @PrimaryKey
    @ColumnInfo(name = "city_name")
    val cityName: String,
    /** 温度文本,例如 "27.3°C" */
    val temperature: String,
    /** ⭐ Day 10 新增:摄氏度数值,用于单位换算 */
    @ColumnInfo(name = "temperature_celsius")
    val temperatureCelsius: Double = 0.0,
    /** WMO 天气码(整数)*/
    @ColumnInfo(name = "weather_code")
    val weatherCode: Int,
    /** 天气描述,例如 "晴" / "多云" */
    @ColumnInfo(name = "weather_desc")
    val weatherDesc: String,
    /** 风速文本,例如 "12.5 km/h" */
    @ColumnInfo(name = "wind_speed")
    val windSpeed: String,
    /** 数据更新时间(由 API 返回),例如 "2026-07-31T10:00" */
    @ColumnInfo(name = "update_time")
    val updateTime: String,
    /** 缓存写入时间(系统当前毫秒数),用于后续过期判断 */
    @ColumnInfo(name = "cached_at")
    val cachedAt: Long,
)
