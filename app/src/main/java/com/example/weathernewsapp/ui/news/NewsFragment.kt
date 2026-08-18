package com.example.weathernewsapp.ui.news

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.weathernewsapp.NewsDetailActivity
import com.example.weathernewsapp.R
import com.example.weathernewsapp.adapter.NewsAdapter
import com.example.weathernewsapp.common.LifecycleLoggingFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class NewsFragment : LifecycleLoggingFragment(R.layout.fragment_news) {
    private val viewModel: NewsViewModel by viewModels()

    private var recyclerView: RecyclerView? = null
    private var progressBar: ProgressBar? = null
    private var errorContainer: View? = null
    private var tvError: TextView? = null
    private var btnRetry: Button? = null
    private var btnRetryEmpty: Button? = null // ⭐ Day 15 修复:空态的重试按钮(原文档遗漏)
    private var emptyContainer: View? = null // ⭐ Day 15 新增
    private var tvOfflineBanner: TextView? = null // ⭐ Day 15 新增:网络异常 fallback banner
    private var srlNews: SwipeRefreshLayout? = null // ⭐ Day 15 挑战 4:下拉刷新容器
    private var adapter: NewsAdapter? = null

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recyclerView)
        progressBar = view.findViewById(R.id.progressBar)
        errorContainer = view.findViewById(R.id.errorContainer)
        tvError = view.findViewById(R.id.tvError)
        btnRetry = view.findViewById(R.id.btnRetry)
        emptyContainer = view.findViewById(R.id.emptyContainer) // ⭐ Day 15 新增
        btnRetryEmpty = view.findViewById(R.id.btnRetryEmpty) // ⭐ Day 15 修复
        tvOfflineBanner = view.findViewById(R.id.tvOfflineBanner) // ⭐ Day 15 新增
        srlNews = view.findViewById(R.id.srlNews) // ⭐ 挑战 4

        recyclerView?.layoutManager = LinearLayoutManager(requireContext())
        adapter =
            NewsAdapter(emptyList()) { news ->
                startActivity(NewsDetailActivity.newIntent(requireContext(), news))
            }
        recyclerView?.adapter = adapter

        btnRetry?.setOnClickListener { viewModel.retry() }
        btnRetryEmpty?.setOnClickListener { viewModel.retry() } // ⭐ Day 15 修复:空态也调同一个 retry

        // ⭐ 挑战 4:下拉手势 → refresh() 通道(viewModel.refresh())
        //   ⭐ Bug 修复:与 btnRetry / btnRetryEmpty 区分——
        //     · 下拉 → refresh()  → 不让 _uiState 变 Loading(保留当前列表)
        //     · 按钮 → retry()    → 让 _uiState 变 Loading(整页重载)
        srlNews?.setOnRefreshListener { viewModel.refresh() }

        observeUiState()
        observeEvents() // ⭐ Day 15 新增
        observeRefreshing() // ⭐ 挑战 4
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    private fun render(state: NewsUiState) {
        when (state) {
            NewsUiState.Idle,
            NewsUiState.Loading,
            -> {
                // Idle 和 Loading 渲染一样:转圈
                recyclerView?.visibility = View.GONE
                errorContainer?.visibility = View.GONE
                emptyContainer?.visibility = View.GONE // ⭐ Day 15 新增
                progressBar?.visibility = View.VISIBLE
                // ⭐ Bug 修复:Loading 时清空 banner,避免视觉残留"已显示缓存数据"
                //   仅在 retry() 按钮触发的 Loading 才会进到这里(refresh() 不进)
                tvOfflineBanner?.visibility = View.GONE
            }
            is NewsUiState.Success -> {
                progressBar?.visibility = View.GONE
                errorContainer?.visibility = View.GONE
                emptyContainer?.visibility = View.GONE
                recyclerView?.visibility = View.VISIBLE
                adapter?.updateData(state.newsList)
                // ⭐ Day 15 新增:仿照 Weather.isFromCache 范式
                //   当列表中任意一条 News 的 isFromFallback = true 时,
                //   说明数据是 NewsRepository catch 分支 fallback 来的假数据
                //   显示 banner 告诉用户"已显示缓存数据"
                val showFallbackBanner = state.newsList.any { it.isFromFallback }
                tvOfflineBanner?.visibility = if (showFallbackBanner) View.VISIBLE else View.GONE
            }
            NewsUiState.Empty -> { // ⭐ Day 15 新增
                progressBar?.visibility = View.GONE
                recyclerView?.visibility = View.GONE
                errorContainer?.visibility = View.GONE
                emptyContainer?.visibility = View.VISIBLE
                // ⭐ Bug 修复:空态也清空 banner(空态说明服务器明确返回空,不是 fallback)
                tvOfflineBanner?.visibility = View.GONE
            }
            is NewsUiState.Error -> {
                progressBar?.visibility = View.GONE
                recyclerView?.visibility = View.GONE
                errorContainer?.visibility = View.VISIBLE
                emptyContainer?.visibility = View.GONE
                tvError?.text = state.type.userMessage
            }
        }
    }

    /**
     * ⭐ Day 15 新增:收集一次性事件(Toast)。
     * 范式与 WeatherFragment.observeEvents() 一致。
     * Channel 里的事件取走即消失,旋转屏幕不会重复弹出。
     */
    private fun observeEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    if (event is NewsEvent.ShowToast) {
                        Toast.makeText(requireContext(), event.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    /**
     * ⭐ Day 15 挑战 4:观察 isRefreshing 关闭下拉动画
     * 与 uiState 分离的副状态——下拉动画 vs 加载态进度条是两种 UI:
     *   · uiState.Loading → progressBar 居中转圈
     *   · isRefreshing    → srlNews 顶部下拉圆圈
     * 副状态分离让 uiState 保持"5 态"纯净
     */
    private fun observeRefreshing() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isRefreshing.collect { isRefreshing ->
                    srlNews?.isRefreshing = isRefreshing
                }
            }
        }
    }

    override fun onDestroyView() {
        recyclerView = null
        progressBar = null
        errorContainer = null
        tvError = null
        btnRetry = null
        btnRetryEmpty = null // ⭐ Day 15 修复
        emptyContainer = null // ⭐ Day 15 新增
        tvOfflineBanner = null // ⭐ Day 15 新增
        srlNews = null // ⭐ 挑战 4
        adapter = null
        super.onDestroyView()
    }
}
