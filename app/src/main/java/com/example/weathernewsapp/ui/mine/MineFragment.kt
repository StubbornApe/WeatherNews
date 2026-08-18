package com.example.weathernewsapp.ui.mine

// ============ import 分区(按 Android 官方约定:android → androidx → 项目) ============
import android.os.Bundle                                           // View 状态保存/恢复入参
import android.view.View                                           // Fragment 根 View
import android.widget.TextView                                     // 文本控件(占位文字用)
import com.example.weathernewsapp.R                                // 资源 id 引用
import com.example.weathernewsapp.common.LifecycleLoggingFragment  // 生命周期日志基类

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * MineFragment —— "我的"页占位实现
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * 【当前职责】
 *   只显示一段"我的 - 开发中"的文字。
 *
 * 【未来演进(Day09)】
 *   - fragment_mine.xml:真实布局(设置项列表:主题、字号、缓存清理...)
 *   - MineFragment:用 DataStore (取代 SharedPreferences) 读写偏好设置
 *   - MineViewModel:把设置项以 StateFlow 暴露给 UI
 *
 * 【与 WeatherFragment 的唯一差别】
 *   只是占位文字不同,布局完全复用 fragment_placeholder.xml,
 *   代码结构也一模一样。这体现了"占位 Fragment 用统一模板即可"的思路。
 * ═══════════════════════════════════════════════════════════════════════════
 */
class MineFragment : LifecycleLoggingFragment(R.layout.fragment_placeholder) {

    /**
     * View 已 inflate,把占位 TextView 的文本换成"我的 - 开发中"。
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)   // ⚠️ 先调 super,让基类打日志

        // 与 WeatherFragment 唯一的差别:资源 id 换成 placeholder_mine。
        // 这就是"一份布局 + 两个 Fragment 差异化"的最小实现。
        view.findViewById<TextView>(R.id.tvPlaceholder)
            .setText(R.string.placeholder_mine)
    }
}