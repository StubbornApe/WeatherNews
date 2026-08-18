package com.example.weathernewsapp.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.RadioGroup
import android.widget.Spinner
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.weathernewsapp.R
import com.example.weathernewsapp.data.datastore.SettingsDataStore
import com.example.weathernewsapp.data.model.CityCoordinates
import com.example.weathernewsapp.data.model.TempUnit
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * SettingsFragment —— 设置页
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * 【交互模式】
 *   设置页采用"即改即存"模式:用户一改 Spinner/Switch/RadioButton,
 *   立即写入 DataStore,不需要点"保存"按钮。
 *
 * 【防止"初始化触发监听器"的经典问题】
 *   给 Spinner/Switch 设置初始值时,会触发 onItemSelected / onCheckedChange,
 *   如果此时监听器已经注册,就会把"刚读出来的旧值"又写回去(虽然值一样,但浪费 IO)。
 *
 *   解法:用一个 Boolean 标志位 isInitialized,初始 false。
 *   在所有初始值设置完成后才设为 true。
 *   监听器内部检查 isInitialized,false 时直接 return。
 *
 * * 【lifecycleScope 的作用】
 *   Fragment 的 lifecycleScope 是一个绑定 Fragment 生命周期的协程作用域。
 *   Fragment 销毁时,这个 scope 自动 cancel,所有正在进行的 DataStore 写操作
 *   也会被取消(CancellationException),不会泄漏。
 * ═══════════════════════════════════════════════════════════════════════════
 */
@AndroidEntryPoint
class SettingsFragment : Fragment() {
    // ═══════════════════════════════════════════════════════════════════════
    //  视图引用
    // ═══════════════════════════════════════════════════════════════════════
    private var spinnerCity: Spinner? = null
    private var switchDarkMode: SwitchCompat? = null
    private var radioGroupTempUnit: RadioGroup? = null

    // ═══════════════════════════════════════════════════════════════════════
    //  DataStore + 初始化标志
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * SettingsDataStore 实例。由 Hilt 字段注入(@Inject + @AndroidEntryPoint):
     *   1. 编译期 Hilt 生成注入代码,运行时 Hilt 在 onAttach 之后才赋值
     *   2. 所以在 onAttach / onViewCreated / 更后面访问都安全
     *   3. ⚠️ 不要在构造器或属性初始化器里访问(@Inject 还没执行,会 NPE)
     *   4. 已经删掉原 by lazy { SettingsDataStore(context) } 的手动创建
     */
    @Inject lateinit var settingsDataStore: SettingsDataStore

    /**
     * 初始化标志位。
     * false = 正在用 DataStore 的值填充 UI,此时不应触发写操作
     * true  = UI 已就绪,用户操作才写 DataStore
     */
    private var isInitialized = false

    // ═══════════════════════════════════════════════════════════════════════
    //  生命周期
    // ═══════════════════════════════════════════════════════════════════════

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        // 1. findViewById 绑定视图
        spinnerCity = view.findViewById(R.id.spinnerCity)
        switchDarkMode = view.findViewById(R.id.switchDarkMode)
        radioGroupTempUnit = view.findViewById(R.id.radioGroupTempUnit)

        // 2. 配置城市 Spinner
        setupCitySpinner()

        // 3. 配置深色模式 Switch
        setupDarkModeSwitch()

        // 4. 配置温度单位 RadioGroup
        setupTempUnitRadioGroup()

        // 5. 从 DataStore 读取当前设置并填充 UI
        loadCurrentSettings()
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  城市 Spinner
    // ═══════════════════════════════════════════════════════════════════════

    private fun setupCitySpinner() {
        val cities = CityCoordinates.SUPPORTED_CITIES

        // ArrayAdapter:Spinner 的标准适配器,把 List<String> 渲染成下拉项
        val adapter =
            ArrayAdapter(
                requireContext(),
                // 选中时的布局
                android.R.layout.simple_spinner_item,
                cities,
            ).apply {
                // 下拉列表里每一行的布局
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }

        spinnerCity?.adapter = adapter

        // 注册选择监听器
        spinnerCity?.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long,
                ) {
                    // isInitialized = false 时忽略(这是初始设值触发的回调)
                    if (!isInitialized) return

                    val selectedCity = cities[position]
                    saveDefaultCity(selectedCity)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    // 用户没选任何东西时不处理(Spinner 始终有一个选中项)
                }
            }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  深色模式 Switch
    // ═══════════════════════════════════════════════════════════════════════

    private fun setupDarkModeSwitch() {
        switchDarkMode?.setOnCheckedChangeListener { _, isChecked ->
            if (!isInitialized) return@setOnCheckedChangeListener
            saveDarkMode(isChecked)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  温度单位 RadioGroup
    // ═══════════════════════════════════════════════════════════════════════

    private fun setupTempUnitRadioGroup() {
        radioGroupTempUnit?.setOnCheckedChangeListener { _, checkedId ->
            if (!isInitialized) return@setOnCheckedChangeListener

            val unit =
                when (checkedId) {
                    R.id.radioCelsius -> TempUnit.CELSIUS
                    R.id.radioFahrenheit -> TempUnit.FAHRENHEIT
                    else -> TempUnit.CELSIUS // 兜底
                }
            saveTempUnit(unit)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  从 DataStore 加载当前设置 → 填充 UI
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * 读取当前保存的设置并刷新 UI 控件。
     *
     * 【为什么用 .first() 而不是 .collect?】
     *   · Flow.first():挂起直到 Flow 发射第一个值,然后取消 collect,返回这个值
     *   · 我们在这里只需要"读一次当前值"来初始化 UI,不需要持续监听
     *   · 用户改了设置后,UI 控件自己会变(Spinner 选了什么、Switch 开没开),
     *     不需要 DataStore 反向推回来
     *   · 如果用 collect,设置页自己写 DataStore 会触发 collect 回调,
     *     又反过来 setSelection/setChecked,可能造成死循环
     *
     * 【为什么放在 lifecycleScope.launch 里?】
     *   first() 是 suspend 函数,必须在协程里调。
     *   lifecycleScope 绑定 Fragment 生命周期,Fragment 销毁自动取消。
     */
    private fun loadCurrentSettings() {
        viewLifecycleOwner.lifecycleScope.launch {
            val settings = settingsDataStore.settingsFlow.first()

            // ── 1. 填充城市 Spinner ──
            val cityIndex = CityCoordinates.SUPPORTED_CITIES.indexOf(settings.defaultCity)
            if (cityIndex >= 0) {
                spinnerCity?.setSelection(cityIndex)
            }

            // ── 2. 填充深色模式 Switch ──
            switchDarkMode?.isChecked = settings.darkMode

            // ── 3. 填充温度单位 RadioGroup ──
            val radioId =
                when (settings.tempUnit) {
                    TempUnit.CELSIUS -> R.id.radioCelsius
                    TempUnit.FAHRENHEIT -> R.id.radioFahrenheit
                }
            radioGroupTempUnit?.check(radioId)

            // ⭐ 所有 UI 初始化完成后,才把标志位设为 true
            //    之后用户操作才会真正写 DataStore
            isInitialized = true
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  写 DataStore 的封装
    // ═══════════════════════════════════════════════════════════════════════

    private fun saveDefaultCity(city: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            settingsDataStore.setDefaultCity(city)
        }
    }

    /**
     * 保存深色模式并立即应用。
     *
     * 为什么用 runBlocking 先写 DataStore 再切换主题?
     *   setDefaultNightMode 会立即 recreate Activity → Fragment 重建 →
     *   loadCurrentSettings() 用 first() 读 DataStore。
     *   如果 DataStore 还没写完(异步 lifecycleScope.launch 还没执行到),
     *   就会读到旧值 → Switch 被重置回旧状态 → 用户再点 → 又闪。
     *
     *   runBlocking 保证写盘完成后再切主题,重建后读到的一定是新值。
     *   因为 DataStore 文件极小(几行键值对),runBlocking 阻塞 < 10ms。
     */
    private fun saveDarkMode(enabled: Boolean) {
        // 先同步写 DataStore(确保重建后读到的是新值)
        runBlocking {
            settingsDataStore.setDarkMode(enabled)
        }

        // 再切换主题(这行不需要在协程里,它是同步 API)
        val mode =
            if (enabled) {
                AppCompatDelegate.MODE_NIGHT_YES
            } else {
                AppCompatDelegate.MODE_NIGHT_NO
            }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    private fun saveTempUnit(unit: TempUnit) {
        viewLifecycleOwner.lifecycleScope.launch {
            settingsDataStore.setTempUnit(unit)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  视图销毁
    // ═══════════════════════════════════════════════════════════════════════

    override fun onDestroyView() {
        super.onDestroyView()
        // 置空所有视图引用,防止内存泄漏
        spinnerCity = null
        switchDarkMode = null
        radioGroupTempUnit = null
        isInitialized = false
    }
}
