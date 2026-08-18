package com.example.weathernewsapp.ui.weather

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.Group
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.weathernewsapp.R
import com.example.weathernewsapp.common.LifecycleLoggingFragment
import com.google.android.material.button.MaterialButton
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class WeatherFragment : LifecycleLoggingFragment(R.layout.fragment_weather) {
    private val viewModel: WeatherViewModel by viewModels()

    private var progressBar: ProgressBar? = null
    private var contentGroup: Group? = null
    private var errorGroup: Group? = null
    private var tvCityName: TextView? = null
    private var tvOfflineTag: TextView? = null
    private var tvTemperature: TextView? = null
    private var tvWeatherDesc: TextView? = null
    private var tvWindSpeed: TextView? = null
    private var tvUpdateTime: TextView? = null
    private var tvError: TextView? = null
    private var btnRetry: MaterialButton? = null
    private var srlWeather: SwipeRefreshLayout? = null // ⭐ Day 15 挑战 4:下拉刷新容器

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        progressBar = view.findViewById(R.id.progressBar)
        contentGroup = view.findViewById(R.id.contentGroup)
        errorGroup = view.findViewById(R.id.errorGroup)
        tvCityName = view.findViewById(R.id.tvCityName)
        tvOfflineTag = view.findViewById(R.id.tvOfflineTag)
        tvTemperature = view.findViewById(R.id.tvTemperature)
        tvWeatherDesc = view.findViewById(R.id.tvWeatherDesc)
        tvWindSpeed = view.findViewById(R.id.tvWindSpeed)
        tvUpdateTime = view.findViewById(R.id.tvUpdateTime)
        tvError = view.findViewById(R.id.tvError)
        btnRetry = view.findViewById(R.id.btnRetry)
        srlWeather = view.findViewById(R.id.srlWeather) // ⭐ 挑战 4

        // 重试改为发意图给 ViewModel,由 reloadTrigger -> collectLatest 响应
        btnRetry?.setOnClickListener { viewModel.retry() }

        // ⭐ 挑战 4:下拉手势 → refresh() 通道(viewModel.refresh())
        //   ⭐ Bug 修复:与 btnRetry 区分——
        //     · 下拉 → refresh()  → 不让 _weatherState 变 Loading(保留当前数据)
        //     · 按钮 → retry()    → 让 _weatherState 变 Loading(整页重载)
        srlWeather?.setOnRefreshListener { viewModel.refresh() }

        observeUiState()
        observeEvents()
        observeRefreshing() // ⭐ 挑战 4
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    /**
     * 收集一次性事件(Toast)。
     * 用 repeatOnLifecycle 保证只在可见时收集;Channel 中的事件取走即消失,
     * 旋转屏幕不会把已经弹过的 Toast 再弹一遍。
     */
    private fun observeEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    if (event is WeatherEvent.ShowToast) {
                        Toast.makeText(requireContext(), event.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    /**
     * ⭐ Day 15 挑战 4:观察 isRefreshing 关闭下拉动画。
     * 范式与 NewsFragment.observeRefreshing() 一致:
     *   · isRefreshing=true  → srlWeather.isRefreshing=true(顶部转圈)
     *   · isRefreshing=false → srlWeather.isRefreshing=false(收起)
     * 副状态分离让 uiState 保持"3 态"纯净(Idle/Loading/Success/Error)
     */
    private fun observeRefreshing() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isRefreshing.collect { isRefreshing ->
                    srlWeather?.isRefreshing = isRefreshing
                }
            }
        }
    }

    private fun render(state: WeatherUiState) {
        when (state) {
            WeatherUiState.Idle -> {
                progressBar?.visibility = View.GONE
                contentGroup?.visibility = View.GONE
                errorGroup?.visibility = View.GONE
                // ⭐ Bug 修复:Idle 状态也隐藏离线标签
                tvOfflineTag?.visibility = View.GONE
            }
            WeatherUiState.Loading -> {
                progressBar?.visibility = View.VISIBLE
                contentGroup?.visibility = View.GONE
                errorGroup?.visibility = View.GONE
                // ⭐ Bug 修复:Loading 时清空 tvOfflineTag(避免"已显示缓存"残留)
                tvOfflineTag?.visibility = View.GONE
            }
            is WeatherUiState.Success -> {
                progressBar?.visibility = View.GONE
                contentGroup?.visibility = View.VISIBLE
                errorGroup?.visibility = View.GONE

                val w = state.weather
                tvCityName?.text = w.cityName
                // ⭐ Day 12:温度文本由 ViewModel 算好,Fragment 直接用
                tvTemperature?.text = state.temperatureText
                tvWeatherDesc?.text = w.weatherDesc
                tvWindSpeed?.text = "风速:${w.windSpeedText}"
                tvUpdateTime?.text = "更新时间:${w.updateTime}"
                tvOfflineTag?.visibility = if (w.isFromCache) View.VISIBLE else View.GONE
            }
            is WeatherUiState.Error -> {
                progressBar?.visibility = View.GONE
                contentGroup?.visibility = View.GONE
                errorGroup?.visibility = View.VISIBLE
                tvError?.text = state.type.userMessage
            }
        }
    }

    override fun onDestroyView() {
        progressBar = null
        contentGroup = null
        errorGroup = null
        tvCityName = null
        tvOfflineTag = null
        tvTemperature = null
        tvWeatherDesc = null
        tvWindSpeed = null
        tvUpdateTime = null
        tvError = null
        btnRetry = null
        srlWeather = null // ⭐ 挑战 4
        super.onDestroyView()
    }
}
