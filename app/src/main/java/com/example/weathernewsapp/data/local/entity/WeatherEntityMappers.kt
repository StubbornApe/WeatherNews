package com.example.weathernewsapp.data.local.entity

import com.example.weathernewsapp.data.model.Weather

/*
 * ═══════════════════════════════════════════════════════════════════════════
 * WeatherEntityMappers —— Domain (Weather) ↔ Entity (WeatherEntity) 转换扩展
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * 【为什么需要"转换层"?】
 *   三个层的模型长得相似但职责不同:
 *   ┌──────────────────┬─────────────────────────────────────────────────┐
 *   │ remote.dto       │ 描述网络返回的 JSON 形状(字段名随 API 变)       │
 *   │ data.model       │ UI 真正用的"业务领域模型"(不掺网络/存储细节)   │
 *   │ local.entity     │ 描述 SQLite 表结构(列名、主键、缓存时间)         │
 *   └──────────────────┴─────────────────────────────────────────────────┘
 *   三层分离的好处:
 *     · 换 API(字段名变了) → 只改 DTO 和 Repository 的映射,UI/缓存零改动
 *     · 改表结构(加列/加表) → 只改 Entity 和映射,UI/网络零改动
 *     · UI 想要的字段名(比如 temperatureText 带单位)可以独立演进
 *
 * 【为什么用扩展函数(extension function)?】
 *   Kotlin 的扩展函数可以让你"给别人的类加方法",而不用继承/包装。
 *   写 weather.toEntity() 看起来像 Weather 类自己就有这个方法,
 *   但实际这个函数定义在数据层文件里,Domain 层的 Weather 完全不知道
 *   Entity 的存在 —— 保持了"Domain 不依赖数据层"的单向依赖。
 *
 * 【文件位置】
 *   放在 `data/local/entity/` 包里,和 Entity 同目录。
 *   它属于"数据层内部的转换工具",UI 层不应该调用它。
 * ═══════════════════════════════════════════════════════════════════════════
 */

/**
 * 把业务层的 Weather 转成数据库表的 WeatherEntity。
 *
 * ── 字段名的差异 ──────────────────────────────────────────────────────
 *   Weather.temperatureText ("27.3°C")  →  WeatherEntity.temperature
 *   Weather.windSpeedText   ("12.5 km/h") →  WeatherEntity.windSpeed
 *   Weather.updateTime                  →  WeatherEntity.updateTime
 *   两边的字段名不完全一致:Domain 里带 `Text` 后缀(强调"给 UI 直接显示用"),
 *   Entity 里用简短列名(省 SQLite 空间,也符合数据库命名习惯)。
 *
 * ── cachedAt 参数为什么要单独传? ───────────────────────────────────────
 *   Weather(Domain) 是"业务模型",它不关心"我什么时候被写入数据库",
 *   所以它没有 cachedAt 字段。
 *   转 Entity 时由调用方传入写入时间。默认参数 = System.currentTimeMillis()
 *   表示"如果调用方不传,就用当前系统时间"。
 *
 *   System.currentTimeMillis():JDK 方法,返回 1970-01-01 00:00:00 UTC
 *   到现在的毫秒数(Long)。比如 2026-07-31 大约是 1785000000000。
 *
 * ── 怎么调用? ─────────────────────────────────────────────────────────
 *   val weather: Weather = dto.toDomain("北京", isFromCache = false)
 *   val entity = weather.toEntity()              // cachedAt 自动取当前时间
 *   // 或指定时间: weather.toEntity(cachedAt = yesterdayMs)
 *   dao.insert(entity)
 */
fun Weather.toEntity(cachedAt: Long = System.currentTimeMillis()): WeatherEntity =
    WeatherEntity(
        // this.xxx 里的 this 指"被扩展的对象",也就是 Weather 实例
        // 可以省略 this,直接写 cityName,但保留 this. 让初学者更清楚"这个字段从哪来"
        cityName = this.cityName,
        // ⭐ 名字不同:temperatureText → temperature
        temperature = this.temperatureText,
        // ⭐ Day 10 新增
        temperatureCelsius = this.temperatureCelsius,
        weatherCode = this.weatherCode,
        weatherDesc = this.weatherDesc,
        // ⭐ 名字不同:windSpeedText → windSpeed
        windSpeed = this.windSpeedText,
        updateTime = this.updateTime,
        // ⭐ Domain 没有这个字段,由参数传入
        cachedAt = cachedAt,
    )

// ═══════════════════════════════════════════════════════════════════════════
//  Entity (WeatherEntity) → Domain (Weather)
//  使用场景:从 Room 读出来缓存后,转成业务模型交回 Repository/UI
// ═══════════════════════════════════════════════════════════════════════════

/**
 * 把数据库行 WeatherEntity 转回业务层 Weather。
 *
 * ── 反向映射 ────────────────────────────────────────────────────────────
 *   Entity.temperature ("27.3°C")  →  Weather.temperatureText
 *   Entity.windSpeed  ("12.5 km/h") →  Weather.windSpeedText
 *   Entity.cachedAt                →  (不传给 Weather,Domain 不需要)
 *
 * ── 返回的 Weather 的 isFromCache 字段 ─────────────────────────────────
 *   注意:这个转换函数**不设置** isFromCache = true。
 *   原因:toDomain() 只负责"字段对字段"的纯数据转换,
 *   "这是不是离线缓存数据"是业务语义,由 Repository 层在调用处决定:
 *
 *   ```kotlin
 *   val cached: Weather? = dao.getByCity("北京")
 *       ?.toDomain()
 *       ?.copy(isFromCache = true)   // ← 在 Repository 里加标志
 *   ```
 *
 *   这样做的好处:转换函数无状态、无业务逻辑,便于测试和复用。
 *
 * ── 为什么返回 Weather 而不是 Weather? (可空) ─────────────────────────
 *   DAO 的 getByCity() 已经返回 WeatherEntity? (可空,查不到为 null),
 *   Kotlin 的安全调用 `?.` 会把 null 传不过来,所以这里参数是非空 Entity,
 *   返回非空 Weather。
 */
fun WeatherEntity.toDomain(): Weather =
    Weather(
        cityName = this.cityName,
        // ⭐ 反向:temperature → temperatureText
        temperatureText = this.temperature,
        // ⭐ Day 10 新增
        temperatureCelsius = this.temperatureCelsius,
        weatherCode = this.weatherCode,
        weatherDesc = this.weatherDesc,
        // ⭐ 反向:windSpeed → windSpeedText
        windSpeedText = this.windSpeed,
        updateTime = this.updateTime,
        // isFromCache 不传,用默认值 false(业务语义交给 Repository 层)
    )
