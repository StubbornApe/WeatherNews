package com.example.weathernewsapp.ui.weather

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weathernewsapp.common.NetworkResult
import com.example.weathernewsapp.data.model.TempUnit
import com.example.weathernewsapp.data.repository.WeatherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WeatherViewModel
    @Inject
    constructor(
        // ⭐ Day 14 改动:由"自己 new"改为"Hilt 注入"
        //   删除原来的:private val repository: WeatherRepository by lazy { WeatherRepository(app.applicationContext) }
        private val repository: WeatherRepository,
    ) : ViewModel() { // ⭐ Day 14 改动:基类 AndroidViewModel → ViewModel

        companion object {
            private const val TAG = "WeatherVM"
        }

        // 天气加载状态(不含温度格式化)。这是"上游原始状态",只随网络请求变化。
        private val _weatherState = MutableStateFlow<WeatherUiState>(WeatherUiState.Idle)
        val weatherState: StateFlow<WeatherUiState> = _weatherState.asStateFlow()

        // ⭐ Day 15 挑战 4 新增:下拉刷新副状态(与 uiState 分离)
        //   参见 NewsViewModel._isRefreshing 的注释,范式完全相同
        private val _isRefreshing = MutableStateFlow(false)
        val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

        // 重试/刷新触发器:值本身无意义,只要自增就会触发一次重新加载。
        private val reloadTrigger = MutableStateFlow(0)

        // ⭐ Bug 修复:与 NewsViewModel 同款——区分下拉刷新与重试按钮
        //   主线程串行读写,与 collectLatest 协作无 race condition
        private var pendingIsPullRefresh: Boolean = false

        /**
         * 对 Fragment 暴露的最终 UI 状态。
         *
         * 把"天气加载状态"和"温度单位"combine 到一起:
         *   - 网络加载完成     -> weatherState 变 Success -> 重新算温度文本
         *   - 用户切换 °C/°F  -> tempUnitFlow 发新值     -> 重新算温度文本(不发网络请求)
         *
         * stateIn 把冷的 combine 结果转成热的 StateFlow,
         * WhileSubscribed(5000) 让旋转屏幕(通常 < 5 秒)不会重启上游、不会重新请求网络。
         */
        val uiState: StateFlow<WeatherUiState> =
            combine(
                _weatherState,
                repository.tempUnitFlow,
            ) { state, unit ->
                if (state is WeatherUiState.Success) {
                    state.withTemperatureUnit(unit)
                } else {
                    state
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = WeatherUiState.Idle,
            )

        // 一次性事件通道(Toast 等)。BUFFERED 队列:send 入队,collect 取走即消失,
        // 旋转屏幕不会重复弹出已经消费过的 Toast。
        private val eventChannel = Channel<WeatherEvent>(Channel.BUFFERED)
        val events = eventChannel.receiveAsFlow()

        init {
            Log.d(TAG, "ViewModel created @ ${System.identityHashCode(this)}")

            // 合并"默认城市变化"和"重试触发"两个信号。
            // 任意一个发新值,collectLatest 都会取消上一次未完成的加载、开始新的,
            // 因此不再需要手写 loadJob?.cancel()。
            viewModelScope.launch {
                combine(
                    repository.defaultCityFlow,
                    reloadTrigger,
                ) { city, _ -> city }
                    .collectLatest { city ->
                        val isPullRefresh = pendingIsPullRefresh
                        Log.d(TAG, "trigger load for city=$city, isPullRefresh=$isPullRefresh")
                        loadWeatherInternal(city, isPullRefresh)
                    }
            }
        }

        /** Fragment 点"重试"按钮时调用:自增触发器,由 collectLatest 响应。
         *  ⭐ Bug 修复:按钮触发的"整页重载",会进入 Loading 态 */
        fun retry() {
            Log.d(TAG, "retry() called (from button)")
            pendingIsPullRefresh = false
            reloadTrigger.update { it + 1 }
        }

        /** ⭐ 挑战 4 修复:下拉刷新专入口。
         *  下拉手势 → 保留当前天气数据(不进入 Loading 态),只显示顶部下拉圆圈。
         *  详见 NewsViewModel.refresh() 注释,范式完全相同 */
        fun refresh() {
            Log.d(TAG, "refresh() called (from pull-to-refresh)")
            pendingIsPullRefresh = true
            reloadTrigger.update { it + 1 }
        }

        private suspend fun loadWeatherInternal(
            city: String,
            isPullRefresh: Boolean,
        ) {
            // ⭐ Bug 修复:下拉圆圈**只**在 isPullRefresh=true 时显示
            //   · 冷启动 / retry() 按钮 → isPullRefresh=false → 不显示下拉圆圈(走 Loading 态)
            //   · 下拉手势 → isPullRefresh=true → 只显示下拉圆圈(保留当前天气数据)
            // 之前无条件 _isRefreshing=true 会导致冷启动时 progressBar + 下拉圆圈同时出现
            if (isPullRefresh) {
                _isRefreshing.value = true
            }
            try {
                // ⭐ Bug 修复:下拉刷新时**不**改 _weatherState
                if (!isPullRefresh) {
                    _weatherState.value = WeatherUiState.Loading
                }

                when (val result = repository.getDefaultWeather()) {
                    is NetworkResult.Success -> {
                        val weather = result.data
                        // ⭐ Bug 修复:移除 Day 11 的"缓存保护"逻辑
                        //   旧逻辑意图:避免"晚到的旧缓存覆盖新数据"
                        //   实际问题:在"网络异常 → 走 Room 缓存"场景下,旧逻辑反而不更新 UI
                        //            导致用户看到的是"上次的真实数据",没看到离线标签
                        //   修复:让所有 Success 都更新 _weatherState(包括 isFromCache=true 的)
                        //        isFromCache 标志由 UI 层(WeatherFragment.render)决定是否显示离线标签
                        //   设计理由:collectLatest 已经处理了"取消上一次未完成的请求",
                        //            "晚到的旧缓存覆盖新数据"的场景在 collectLatest 范式下不存在
                        Log.d(
                            TAG,
                            "loadWeather: -> Success(city=${weather.cityName}, isFromCache=${weather.isFromCache})",
                        )
                        _weatherState.value = WeatherUiState.Success(weather)
                        if (weather.isFromCache) {
                            eventChannel.send(WeatherEvent.ShowToast("网络不给力,已显示缓存数据"))
                        }
                    }
                    is NetworkResult.Error -> {
                        Log.d(TAG, "loadWeather: -> Error(${result.type.userMessage})")
                        _weatherState.value = WeatherUiState.Error(result.type)
                        eventChannel.send(WeatherEvent.ShowToast(result.type.userMessage))
                    }
                    NetworkResult.Loading -> {
                        if (!isPullRefresh) {
                            _weatherState.value = WeatherUiState.Loading
                        }
                    }
                }
            } finally {
                // ⭐ Bug 修复:同样只在下拉时关闭
                if (isPullRefresh) {
                    _isRefreshing.value = false
                }
            }
        }

        /**
         * 按温度单位复制出一个带新 temperatureText 的 Success。
         * 这里调用 Day 10 的纯函数 WeatherTextFormatter,逻辑从 Fragment 上移。
         */
        private fun WeatherUiState.Success.withTemperatureUnit(unit: TempUnit): WeatherUiState.Success {
            val text =
                WeatherTextFormatter.formatTemperature(
                    celsius = weather.temperatureCelsius,
                    unit = unit,
                )
            return if (text == temperatureText) this else copy(temperatureText = text)
        }
    }

/**
 * 天气页的一次性事件。
 * 用 sealed interface 便于将来扩展(导航、震动、Snackbar 等)。
 */
sealed interface WeatherEvent {
    data class ShowToast(val message: String) : WeatherEvent
}
