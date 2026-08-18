package com.example.weathernewsapp.ui.news

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weathernewsapp.common.NetworkResult
import com.example.weathernewsapp.data.repository.NewsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NewsViewModel
    @Inject
    constructor(
        private val repository: NewsRepository,
    ) : ViewModel() {
        companion object {
            private const val TAG = "NewsVM"
        }

        private val _uiState = MutableStateFlow<NewsUiState>(NewsUiState.Idle)
        val uiState: StateFlow<NewsUiState> = _uiState.asStateFlow()

        // ⭐ Day 15 挑战 4 新增:下拉刷新副状态(与 uiState 分离,避免污染主状态机)
        //   下拉动画 vs Loading 进度条是两种 UI:
        //     - uiState.Loading → 居中转圈(整页重载,通常由 retry() 按钮触发)
        //     - isRefreshing    → 顶部下拉圆圈(用户下拉手势,保留当前列表内容)
        //   副状态分离让 uiState 保持"5 态"纯净
        private val _isRefreshing = MutableStateFlow(false)
        val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

        // ⭐ Day 15 新增:重试/刷新触发器
        //   值本身无意义,只要自增就会触发一次重新加载。
        //   与 WeatherViewModel 的 reloadTrigger 是同一范式。
        private val reloadTrigger = MutableStateFlow(0)

        // ⭐ Bug 修复:区分"下拉刷新"和"重试按钮"两种触发源
        //   下拉刷新 → 不应让 _uiState 变 Loading(否则列表消失 + banner 残留)
        //   重试按钮 → 应当让 _uiState 变 Loading(整页重新加载)
        //   标志在主线程读写,collectLatest 串行处理,无 race condition
        private var pendingIsPullRefresh: Boolean = false

        // ⭐ Day 15 新增:一次性事件通道
        //   BUFFERED 队列(默认 64 容量):send 不挂起,collect 取走即消失。
        //   旋转屏幕不会把已经消费过的 Toast 再弹一遍。
        private val eventChannel = Channel<NewsEvent>(Channel.BUFFERED)
        val events = eventChannel.receiveAsFlow()

        init {
            Log.d(TAG, "ViewModel created @ ${System.identityHashCode(this)}")

            // ⭐ Day 15 改造:与 WeatherViewModel 相同范式
            //   reloadTrigger 任意自增 → collectLatest 自动取消上一次的 loadNewsInternal
            //   → 启动新的 loadNewsInternal。避免连点"重试"产生多个并行协程。
            viewModelScope.launch {
                reloadTrigger
                    .collectLatest { trigger ->
                        val isPullRefresh = pendingIsPullRefresh
                        Log.d(TAG, "trigger load, value=$trigger, isPullRefresh=$isPullRefresh")
                        loadNewsInternal(isPullRefresh)
                    }
            }
        }

        /** Fragment 点"重试"按钮时调用:自增触发器,由 collectLatest 响应。
         *  ⭐ Bug 修复:这是按钮触发的"整页重载",会进入 Loading 态 */
        fun retry() {
            Log.d(TAG, "retry() called (from button)")
            pendingIsPullRefresh = false
            reloadTrigger.update { it + 1 }
        }

        /** ⭐ 挑战 4 修复:下拉刷新专入口。
         *  下拉手势 → 保留当前列表内容(不进入 Loading 态),只显示顶部下拉圆圈。
         *  这样:
         *    1. 下拉时列表不消失,用户能看到"下拉只是刷新,不是重新加载整页"
         *    2. Loading 状态时 banner 残留问题被绕过(banner 在 Loading 不渲染)
         *    3. UX 更自然:与今日头条/知乎一致 */
        fun refresh() {
            Log.d(TAG, "refresh() called (from pull-to-refresh)")
            pendingIsPullRefresh = true
            reloadTrigger.update { it + 1 }
        }

        private suspend fun loadNewsInternal(isPullRefresh: Boolean) {
            // ⭐ Bug 修复:下拉圆圈**只**在 isPullRefresh=true 时显示
            //   · 冷启动 / retry() 按钮 → isPullRefresh=false → 不显示下拉圆圈(走 Loading 态)
            //   · 下拉手势 → isPullRefresh=true → 只显示下拉圆圈(保留当前列表)
            // 之前无条件 _isRefreshing=true 会导致冷启动时 progressBar + 下拉圆圈同时出现
            if (isPullRefresh) {
                _isRefreshing.value = true
            }
            try {
                // ⭐ Bug 修复:下拉刷新时**不**改 _uiState
                //   · retry() 进入时 → _uiState = Loading(显示 progressBar)
                //   · refresh() 进入时 → _uiState 保持(列表可见,下拉圆圈在顶部)
                //   关键:无论哪种入口,网络请求都会真的发(repository.getNews() 无缓存)
                if (!isPullRefresh) {
                    _uiState.value = NewsUiState.Loading
                }

                when (val result = repository.getNews()) {
                    is NetworkResult.Success -> {
                        val list = result.data
                        val hasFallback = list.any { it.isFromFallback }
                        Log.d(TAG, "loadNews: -> Success(${list.size} items, hasFallback=$hasFallback)")
                        if (list.isEmpty()) {
                            _uiState.value = NewsUiState.Empty
                        } else {
                            _uiState.value = NewsUiState.Success(list)
                        }
                    }
                    is NetworkResult.Error -> {
                        Log.d(TAG, "loadNews: -> Error(${result.type.userMessage})")
                        _uiState.value = NewsUiState.Error(result.type)
                        eventChannel.send(NewsEvent.ShowToast(result.type.userMessage))
                    }
                    NetworkResult.Loading -> {
                        if (!isPullRefresh) {
                            _uiState.value = NewsUiState.Loading
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
    }

sealed interface NewsEvent {
    data class ShowToast(val message: String) : NewsEvent
}
