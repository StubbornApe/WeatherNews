package com.example.weathernewsapp.ui.news

import com.example.weathernewsapp.common.ErrorType
import com.example.weathernewsapp.model.News

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * NewsUiState —— 新闻页 UI 状态的统一表达(Day 15 升级到 5 态)
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * 【与 Day 12 的 4 态版本对比】
 *   新增 data object Empty —— "服务器响应 200 但 newsList 为空"的业务空态。
 *
 *   为什么 Success(emptyList()) 不够用?
 *   · 渲染:Success 永远走 adapter.updateData(list),空 list 渲染出"空白列表"
 *     用户分不清"加载中 / 没数据 / 出错了"
 *   · 测试:单元测试需要单独的"空态被触发"断言,挤进 Success 里表达力差
 *   · 扩展性:Empty 将来可以加"换个频道"按钮,需要单独 UI 元素,跟 Success 解耦
 *
 * 【5 态完整含义】
 *   · Idle     未发起过请求(打开 App 瞬间)
 *   · Loading  请求中(转圈)
 *   · Success  拿到非空列表(渲染 RecyclerView)
 *   · Empty    拿到空列表(显示"暂无新闻"占位)
 *   · Error    请求失败(显示错误页 + 重试按钮)
 *
 * 【sealed interface 优势】
 *   · 编译器强制 when 穷举,新增态时漏处理会立刻报错
 *   · 与 StateFlow 天然搭配(单一 state 字段,render(state) 一个函数搞定)
 *   · 5 个子类型:3 个 data object(无数据) + 2 个 data class(携带数据)
 *
 * 【data object vs object(Kotlin 1.9+)】
 *   · data object 自动生成 toString() 为 "NewsUiState.Empty"
 *   · 普通 object 的 toString() 是 "NewsUiState$Empty@3a4b5c" 不利于日志调试
 *   · 本项目 Kotlin 2.0.21 完整支持 data object
 * ═══════════════════════════════════════════════════════════════════════════
 */
sealed interface NewsUiState {
    data object Idle : NewsUiState

    data object Loading : NewsUiState

    data class Success(val newsList: List<News>) : NewsUiState

    data object Empty : NewsUiState // ⭐ Day 15 新增

    data class Error(val type: ErrorType) : NewsUiState
}
