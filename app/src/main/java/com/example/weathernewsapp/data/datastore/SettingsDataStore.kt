package com.example.weathernewsapp.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.weathernewsapp.data.model.TempUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * SettingsDataStore —— 用户偏好设置的数据访问层
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * 【这个类在做什么?一句话总结】
 *   封装 Jetpack Preferences DataStore 的所有读写操作,对外暴露:
 *     · 类型安全的 Flow<T> 用于"响应式读取"
 *     · suspend 写方法用于"修改偏好"
 *   UI/Repository 不直接碰 DataStore<Preferences> 和 Key 常量,全走这个类。
 *
 * 【为什么要封装而不直接用 DataStore?】
 *   1. Key 定义散落在各处容易拼错/重复;集中在这里一目了然
 *   2. 默认值("北京"、CELSIUS、false)集中管理,不会出现"UI 一个默认值,
 *      Repository 另一个默认值"的不一致
 *   3. 类型转换逻辑(Int→TempUnit)放在这一层,外层拿到的就是强类型
 *   4. 将来从 Preferences 迁到 Proto 或其他存储,外层不用改
 *
 * 【为什么用顶层扩展属性 + by lazy?】
 *   Google 官方推荐的 DataStore 创建方式:
 *     private val Context.dataStore by preferencesDataStore("settings")
 *   这个顶层委托保证:
 *     · 全 App 只有一个 DataStore 实例(单例)
 *     · 文件名固定为 settings.preferences_pb
 *     · 多进程/多实例同时访问同一个文件会被 DataStore 内部检测到并抛异常
 *   ⚠️ 千万不要在每个 Activity/Fragment 里都调 preferencesDataStore(),
 *      那样会创建多个实例访问同一个文件,DataStore 会抛
 *      "There are multiple DataStores active for the same file" 异常。
 *
 * 【数据流图】
 *
 *   UI(SettingsFragment)
 *     │ collect settingsFlow
 *     ▼
 *   SettingsDataStore.settingsFlow  ← Flow<Settings>
 *     │ map { 组合 3 个 Flow }
 *     ▼
 *   Context.dataStore.data          ← Flow<Preferences>(DataStore 内部)
 *     │
 *     ▼
 *   settings.preferences_pb 文件    ← 磁盘持久化
 *
 * 【线程模型】
 *   · 读:Flow 在 DataStore 内部的单线程 IO Dispatcher 上读文件
 *   · 写:edit{} 在同一个单线程 IO Dispatcher 上,持有 Mutex 串行执行
 *   · 主线程只负责 collect,不阻塞
 * ═══════════════════════════════════════════════════════════════════════════
 */

// ⭐ 顶层扩展属性:全 App 唯一的 DataStore<Preferences> 实例
//
// 【为什么定义在文件顶层而不是类里面?】
//   preferencesDataStore 是一个属性委托工厂,它必须作为
//   Context 的扩展属性存在(Kotlin 委托属性的要求)。
//   定义在顶层让整个 App 共享同一个实例,避免多实例冲突。
//
// 【为什么是 private?】
//   只在本文件内使用,外部通过 SettingsDataStore 类间接访问,
//   不允许外部直接操作 dataStore.edit { }(否则绕过了我们的类型安全封装)。
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings"   // 文件名 → /files/datastore/settings.preferences_pb
)

/**
 * 设置项的领域模型(Domain Model)。
 *
 * 把 3 个偏好组合成一个 data class,UI 一次 collect 就能拿到全部设置,
 * 不用分别 collect 三个 Flow。
 *
 * 这也是"响应式 UI"的常见模式:把一屏需要的数据打包成一个 UiState。
 */
data class UserSettings(
    /** 默认城市名,如 "北京" */
    val defaultCity: String,

    /** 是否深色模式 */
    val darkMode: Boolean,

    /** 温度单位 */
    val tempUnit: TempUnit
)

class SettingsDataStore(
    // 传入 Context(建议传 applicationContext,避免内存泄漏)
    private val context: Context
) {

    // ═══════════════════════════════════════════════════════════════════════
    //  Preferences Keys —— 类型安全的键定义
    // ═══════════════════════════════════════════════════════════════════════
    //
    // 【为什么每个类型有不同的 key 工厂方法?】
    //   stringPreferencesKey()   → 返回 Preferences.Key<String>
    //   intPreferencesKey()      → 返回 Preferences.Key<Int>
    //   booleanPreferencesKey()  → 返回 Preferences.Key<Boolean>
    //   用对应类型的 Key 去读,返回值自动是该类型(可空),不会出现
    //   SharedPreferences 时代"用 getInt 读 String 返回默认值"的运行时错误。
    //
    // 【Key 名字符串的作用】
    //   仅用于磁盘序列化标识(存到 .preferences_pb 文件里)。
    //   改这个字符串 = 老数据读不出来(相当于新 Key)。
    //   所以命名要稳定,加前缀避免冲突。

    private companion object Keys {
        val DEFAULT_CITY = stringPreferencesKey("default_city")
        val DARK_MODE    = booleanPreferencesKey("dark_mode")
        val TEMP_UNIT    = intPreferencesKey("temp_unit")
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  默认值集中管理
    // ═══════════════════════════════════════════════════════════════════════
    //
    // 所有默认值放这里,UI 和 Repository 都引用这些常量,
    // 不会出现"UI 默认北京、Repository 默认上海"的不一致。

    object Defaults {
        const val DEFAULT_CITY = "北京"
        const val DARK_MODE = false            // 默认浅色
        val TEMP_UNIT = TempUnit.CELSIUS      // 默认摄氏度
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  读:组合 3 个 Flow 成一个 SettingsFlow
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * 所有用户设置的响应式流。
     *
     * 【使用方式】
     * ```kotlin
     * settingsDataStore.settingsFlow.collect { settings ->
     *     // settings.defaultCity / settings.darkMode / settings.tempUnit
     * }
     * ```
     *
     * 【值变化自动推送】
     * 任何一个偏好被 setXxx() 修改后,这个 Flow 会重新发射一个新的 UserSettings 对象,
     * UI 通过 collect 自动收到最新值,无需手动刷新。
     *
     * 【错误处理】
     * .catch 捕获 IOException(磁盘损坏/文件格式错误),
     * 发射 emptyPreferences() 让上层拿到默认值而不是崩溃。
     * 非 IOException(如 CancellationException)继续抛出。
     */
    val settingsFlow: Flow<UserSettings> = context.dataStore.data
        .catch { e ->
            if (e is IOException) {
                // 文件读取异常时发出空 Preferences,后续 map 会用默认值填充
                emit(emptyPreferences())
            } else {
                throw e   // 其他异常(如协程取消)不能吞
            }
        }
        .map { prefs ->
            UserSettings(
                defaultCity = prefs[DEFAULT_CITY] ?: Defaults.DEFAULT_CITY,
                darkMode    = prefs[DARK_MODE] ?: Defaults.DARK_MODE,
                tempUnit    = TempUnit.fromOrdinal(prefs[TEMP_UNIT])
            )
        }

    // ─────────────────────────────────────────────────────────────────────
    //  单项 Flow(如果某处只关心一个偏好,可以用这些,不用 collect 全量)
    // ─────────────────────────────────────────────────────────────────────

    /** 默认城市的响应式流 */
    val defaultCityFlow: Flow<String> = context.dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { it[DEFAULT_CITY] ?: Defaults.DEFAULT_CITY }
        // ⭐ Day 12 修复:只在城市真正变化时才发射。
        //   dataStore.data 在"任意 key"变化时都会把整个 Preferences 重发一次,
        //   导致切温度单位/深色模式也会让 defaultCityFlow 发射相同的城市名,
        //   进而触发 ViewModel 的 combine → collectLatest 重新请求网络。
        //   distinctUntilChanged() 过滤连续相等的值,从源头杜绝无关刷新。
        .distinctUntilChanged()

    /** 深色模式的响应式流 */
    val darkModeFlow: Flow<Boolean> = context.dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { it[DARK_MODE] ?: Defaults.DARK_MODE }
        .distinctUntilChanged()

    /** 温度单位的响应式流 */
    val tempUnitFlow: Flow<TempUnit> = context.dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { TempUnit.fromOrdinal(it[TEMP_UNIT]) }
        .distinctUntilChanged()

    // ═══════════════════════════════════════════════════════════════════════
    //  写:suspend 方法,在协程里调用
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * 设置默认城市。
     *
     * @param city 城市名,如 "北京"、"上海"
     *
     * 【suspend + 协程】
     *   内部调用 dataStore.edit { },这是一个 suspend 函数。
     *   调用方必须在 lifecycleScope / viewLifecycleOwner.lifecycleScope 里调。
     *   写盘在 DataStore 内部 IO 线程完成,主线程只挂起不阻塞。
     *
     * 【原子性】
     *   edit {} 块内的所有修改一次性提交,不会出现"写了一半"的中间状态。
     *   即使只改一个 key,也走完整的"读当前值 → 改 → 写 tmp → rename"流程。
     */
    suspend fun setDefaultCity(city: String) {
        context.dataStore.edit { prefs ->
            prefs[DEFAULT_CITY] = city
        }
    }

    /** 设置深色模式开关。true=深色,false=浅色。 */
    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[DARK_MODE] = enabled
        }
    }

    /** 设置温度单位。传枚举,内部转成 Int 存储。 */
    suspend fun setTempUnit(unit: TempUnit) {
        context.dataStore.edit { prefs ->
            prefs[TEMP_UNIT] = unit.ordinal
        }
    }
}