package com.example.weathernewsapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.weathernewsapp.data.local.entity.WeatherEntity

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * WeatherDao —— 天气表的数据访问对象(Data Access Object)
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * 【DAO 是什么?】
 *   DAO 是 Room 定义的"数据访问层接口"——你只声明"我要对表做什么操作"
 *   (用注解描述 SQL),Room 在编译期通过 KSP 自动生成实现类
 *   `WeatherDao_Impl.java`,你从来不用自己写 SQL 执行逻辑。
 *
 * 【为什么是 interface 而不是 abstract class?】
 *   · interface:所有方法都是"抽象声明",没有默认实现 → Room 全部自动生成
 *   · abstract class:允许你写一些带 @Transaction 的默认组合方法
 *     (例如"先插日志再插数据"的事务封装),纯增删改查用 interface 就够
 *
 * 【所有方法都标了 suspend】
 *   Room 检测到 suspend 后会自动用自己的 IO Executor 在后台线程执行 SQL,
 *   主线程调用也安全,不需要手写 withContext(Dispatchers.IO)。
 *   (原理见 docs/day08.md 的 2.4 节 CoroutinesRoom.execute 伪代码)
 *
 * 【返回 WeatherEntity? (可空) 的含义】
 *   SQLite "SELECT ... WHERE ..." 查不到匹配行时返回空游标。
 *   Room 把"空游标"翻译成 null,所以返回类型写 WeatherEntity?
 *   让 Repository 可以用 `?:` 简单判断"有没有缓存"。
 * ═══════════════════════════════════════════════════════════════════════════
 */
@Dao
interface WeatherDao {
    // ═══════════════════════════════════════════════════════════════════════
    //  插入
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * 插入一条天气缓存。
     *
     * ── @Insert 注解 ──────────────────────────────────────────────────
     * 告诉 Room:"这个方法是 INSERT 操作"。
     * Room 会根据参数类型 WeatherEntity 的 @Entity / @ColumnInfo 自动拼出:
     *   INSERT OR REPLACE INTO weather (city_name, temperature, ...) VALUES (?, ?, ...)
     * 你完全不用写 SQL。
     *
     * ── onConflict = OnConflictStrategy.REPLACE ──────────────────────
     * 指定主键冲突时怎么办(这里主键是 city_name)。
     *
     *   · REPLACE:旧行整行 DELETE 再 INSERT 新行(等价于 SQLite 的 INSERT OR REPLACE)
     *     → 同一个城市再次调用 insert,旧数据被新数据覆盖,永远保持最新 ✓ 今天用这个
     *   · ABORT(默认):中断本次插入,抛 SQLiteConstraintException
     *   · IGNORE:保留旧行,忽略新行(有就不插)
     *   · ROLLBACK:事务回滚(已废弃)
     *
     * ⚠️ REPLACE 的陷阱:是 DELETE + INSERT,不是原地 UPDATE。
     *    如果新 Entity 缺字段(比如以后加了 NOT NULL 的 favorite 列但这里没带),
     *    旧值会被丢。今天 WeatherEntity 所有列都带,安全。
     *
     * ── 参数 weather:WeatherEntity ────────────────────────────────────
     * Room 会把这个对象的每个字段按 @ColumnInfo 指定的列名绑定到 SQL 参数。
     *
     * ── 返回值可选 ──────────────────────────────────────────────────────
     * 可以返回 Long(插入后的 rowId),也可以返 Unit(不关心)。
     * 这里用 Unit(默认),因为我们不需要 rowId。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(weather: WeatherEntity)

    // ═══════════════════════════════════════════════════════════════════════
    //  查询
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * 按城市名查询缓存。
     *
     * ── @Query 注解 ───────────────────────────────────────────────────
     * 与 @Insert 不同,@Query 必须手写 SQL —— 因为 SELECT 千变万化,Room 无法帮你推导。
     * 但 Room 会在**编译期**做三件事:
     *   1. 校验 SQL 语法(错了直接编译报错,而不是运行时崩)
     *   2. 校验 `:cityName` 这种占位符**是否真的存在同名的 Kotlin 参数**
     *   3. 校验返回类型 WeatherEntity 的字段是否与 SELECT 出来的列对得上
     *
     * ── :cityName 占位符 ──────────────────────────────────────────────
     * SQL 里以冒号开头的标识符代表"把 Kotlin 参数 cityName 绑定到这里"。
     * Room 会用 SQLite 的 prepared statement 安全绑定(防 SQL 注入),
     * 你不要自己用字符串拼,否则有注入风险且无法走预编译缓存。
     *
     * ── SELECT * ──────────────────────────────────────────────────────
     * 选出所有列(@Entity 里声明的 7 个字段),Room 自动把 cursor 每行映射成
     * WeatherEntity 对象。若只需要部分字段,可以 SELECT city_name, temperature
     * 并返回一个精简的 data class(性能优化用,今天不需要)。
     *
     * ── 返回 WeatherEntity? (可空) ────────────────────────────────────
     *   · 查到匹配行 → 返回一个 WeatherEntity 对象
     *   · 没查到 → 返回 null(空游标)
     * 调用方 Repository 用 `?: return NetworkResult.Error(...)` 可以直接兜底。
     */
    @Query("SELECT * FROM weather WHERE city_name = :cityName")
    suspend fun getByCity(cityName: String): WeatherEntity?

    /**
     * 查询所有城市的缓存,按写入时间倒序排列。
     *
     * 目前 UI 不用,主要用于:
     *   · Database Inspector 调试时手动验证
     *   · 将来做"缓存管理页"显示所有已缓存城市
     *   · 开发期 Debug 菜单"导出全部缓存"
     *
     * ORDER BY cached_at DESC: 最新写入的排最前面
     * 返回 List<WeatherEntity>: 永不为 null(没数据时是空 list)
     */
    @Query("SELECT * FROM weather ORDER BY cached_at DESC")
    suspend fun getAll(): List<WeatherEntity>

    // ═══════════════════════════════════════════════════════════════════════
    //  删除
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * 清空整张 weather 表(DELETE FROM weather)。
     *
     * 用途:设置页里"清除缓存"按钮,或 Debug 菜单"重置所有数据"。
     * 注意:这是 DML 操作(删行),不是 DROP TABLE(删表结构)。
     * 下次 insert 照样可用,不用重新建表。
     *
     * 返回 Unit。如果想知道删了几行,可以把返回类型改成 Int(被删行数)。
     */
    @Query("DELETE FROM weather")
    suspend fun clearAll()

    /**
     * 删除指定城市的缓存。
     *
     * 与 getByCity 对称:传 "北京" 就把北京那一行删掉。
     * 今天未用,预留:将来做"长按某个城市的缓存删除"时用。
     */
    @Query("DELETE FROM weather WHERE city_name = :cityName")
    suspend fun deleteByCity(cityName: String)
}
