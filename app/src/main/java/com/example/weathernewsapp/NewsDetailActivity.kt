package com.example.weathernewsapp

// ============ import 分区(遵循 Android 官方约定的分组顺序) ============
// android.*        —— Android SDK 内置类
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
// androidx.*       —— AndroidX 兼容库(Jetpack)
import androidx.activity.enableEdgeToEdge
import androidx.activity.OnBackPressedCallback           // 现代化的返回处理
import androidx.appcompat.app.AppCompatActivity         // Activity 基类(带兼容 ActionBar)
import androidx.core.content.ContextCompat              // 跨版本的资源获取
import androidx.core.content.IntentCompat               // 跨版本的 Parcelable 取值(关键)
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.weathernewsapp.common.LifecycleLoggingActivity

// com.google.*     —— 第三方(Material Components)
import com.google.android.material.appbar.MaterialToolbar
// 项目内部
import com.example.weathernewsapp.model.News



/**
 * NewsDetailActivity —— 新闻详情页
 * =====================================================================
 * 学习要点:
 *   ① 用 `companion object` 提供"启动器函数" newIntent(),
 *      把"Intent 的 key 和封装逻辑"收敛在 Activity 内部,调用方更清爽
 *   ② 用 Android 13+ 的 Intent.getParcelableExtra(name, Class) 新签名
 *      并用 IntentCompat 保证 <33 版本的兼容(避免旧签名的 Deprecated 警告)
 *   ③ 用 OnBackPressedDispatcher 处理返回按键(Android 13 起支持"预测性返回")
 *   ④ 用 setSupportActionBar + onSupportNavigateUp 统一处理顶部返回箭头
 *
 * 输入:上游(Adapter)通过 Intent 传入一个 Parcelable 的 News 对象
 * 输出:全屏展示这条新闻的标题 / 元信息 / 正文
 */
class NewsDetailActivity : LifecycleLoggingActivity() {

    // ═════════════════════════════════════════════════════════════════
    // ① 静态区:Extra key 常量 & 启动器工厂方法
    //    Kotlin 里没有 static,通过 `companion object` 表达"类级别"成员
    // ═════════════════════════════════════════════════════════════════
    companion object {
        /**
         * Intent extra 的 key。
         * 命名建议加**包名前缀**,避免与系统或其他库的 key 冲突。
         * `const val` 是 Kotlin 的"编译期常量",相当于 Java 的 `public static final`。
         */
        private const val EXTRA_NEWS = "com.example.weathernewsapp.EXTRA_NEWS"

        /**
         * 启动器(工厂方法):构造并返回启动 NewsDetailActivity 所需的 Intent。
         *
         * 优点:
         *   - 调用方(Adapter)只需 `startActivity(NewsDetailActivity.newIntent(ctx, news))`,
         *     不用关心 key 是什么、需要传什么参数
         *   - 未来给详情页加"从深链启动 / 从通知启动"等场景,只要多写几个 newXxxIntent(),
         *     所有 Intent 构造集中在这里,不散落各处
         *
         * `.apply { ... }` 是 Kotlin 作用域函数:配置对象后返回对象自身,
         *   让代码更紧凑(避免 val i = Intent(); i.putExtra(...); return i 的三行写法)。
         */
        fun newIntent(context: Context, news: News): Intent =
            Intent(context, NewsDetailActivity::class.java).apply {
                // 因为 News 已经用 @Parcelize 实现了 Parcelable,
                // Intent.putExtra 里可以直接塞进去
                putExtra(EXTRA_NEWS, news)
            }
    }

    // ═════════════════════════════════════════════════════════════════
    // ② onCreate —— Activity 创建入口,只干"一次性初始化"
    //    保持轻量:View 绑定 + 从 Intent 取数据 + 注册监听器
    //    耗时操作(网络/DB)放到协程里,不要挡在这里
    // ═════════════════════════════════════════════════════════════════
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // 加载布局:R.layout.activity_news_detail 由 res/layout/activity_news_detail.xml 编译生成
        setContentView(R.layout.activity_news_detail)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.detailRoot)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        Log.d("LC", "NewsDetail -> onCreate")   // 生命周期日志,与 MainActivity 用同一 TAG

        // ─────────── 2.1 从 Intent 里安全取出 News ───────────
        // 优先使用 Android 13(API 33)引入的**新签名**:显式传 Class,更类型安全,
        //   避免旧签名 `getParcelableExtra<T>(key)` 的"未受检泛型警告"。
        // 对 <33 版本,IntentCompat 内部会自动 fallback 到旧签名,一行搞定跨版本兼容。
        //
        // 返回值是可空(News?),因为:
        //   ① Intent 里可能没这个 key(调用方漏写);
        //   ② key 冲突导致取值类型不对;
        //   ③ 系统恢复时 Parcelable 反序列化失败(极少见)。
        val news: News? = IntentCompat.getParcelableExtra(
            intent,
            EXTRA_NEWS,
            News::class.java
        )

        // ─────────── 2.2 空值兜底 —— 用户体验 vs 直接崩溃 ───────────
        // Elvis + 提前 return:数据缺失时给一个"错误占位",避免直接 NPE 崩溃。
        // 这是"防御性编程":宁可提示"未收到数据",也不要让 App 挂掉。
        if (news == null) {
            findViewById<TextView>(R.id.tvDetailTitle).text =
                getString(R.string.detail_error_missing)
            return    // 直接结束 onCreate,后续绑定逻辑就不用跑了
        }

        // ─────────── 2.3 绑定顶部标题栏(MaterialToolbar) ───────────
        // 步骤三件套:
        //   ① findViewById 拿到 XML 里的 Toolbar 实例
        //   ② setSupportActionBar —— 让 Toolbar 充当 ActionBar,
        //                            这样才能启用 setDisplayHomeAsUpEnabled 显示返回箭头
        //   ③ 给箭头设点击回调(与物理返回归一化)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)   // 显示 ← 箭头
        supportActionBar?.setDisplayShowHomeEnabled(true)   // 让"Home 按钮区"可交互
        toolbar.setNavigationOnClickListener {
            // "点箭头 = 按物理返回",两条路径归一,行为一致
            onBackPressedDispatcher.onBackPressed()
        }

        // ─────────── 2.4 绑定 UI —— 把 News 数据填充到各 View ───────────
        // 抽取一个私有函数 bindNews(),让 onCreate 保持"编排"角色,不写业务细节
        bindNews(news)
        setupWeb(news)

        // ─────────── 2.5 注册返回回调(现代化的可预测返回) ───────────
        // 从 Android 13(API 33)起,`onBackPressed()` 覆写被标记 @Deprecated,
        // 官方推荐用 OnBackPressedDispatcher。好处:
        //   ① 支持"预测性返回"(Predictive Back)—— 手势返回时可预览目标页
        //   ② Fragment / Compose 也能注册自己的回调,统一路由
        //   ③ 可动态启用/禁用(如"有草稿时才拦截返回")
        //
        // enabled = true 表示"这个回调当前生效";若想暂停,把 isEnabled 设为 false 即可
        onBackPressedDispatcher.addCallback(
            this,   // LifecycleOwner:回调随 Activity 销毁自动移除,不需要手动 remove
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    Log.d("LC", "NewsDetail -> back pressed")
                    // 目前无自定义逻辑,直接 finish 返回。
                    // 未来可扩展:如"正文滚动到中段时,先滚回顶部再退出"
                    finish()
                }
            }
        )
    }

    // ═════════════════════════════════════════════════════════════════
    // ③ 顶部箭头的 Up navigation
    //    因为 Manifest 里声明了 android:parentActivityName=".MainActivity",
    //    系统在深链/多任务场景会走 Up navigation 逻辑,而不是简单 finish()。
    //    覆写此方法把 Up 事件也桥接到 OnBackPressedDispatcher,行为一致。
    // ═════════════════════════════════════════════════════════════════
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true    // 返回 true 表示"我已处理"
    }

    // ═════════════════════════════════════════════════════════════════
    // 挑战 2 新增:setupWeb —— 有 url 用 WebView 加载原文,无 url 显示摘要
    //    - WebView 在 Android 9+ 默认禁止明文 http 流量(见常见问题 cleartext)
    //    - webViewClient 让链接在当前 WebView 内打开,不进系统浏览器
    // ═════════════════════════════════════════════════════════════════
    private fun setupWeb(news: News) {
        val web = findViewById<WebView>(R.id.webDetail)
        val scroll = findViewById<View>(R.id.detailScroll)
        val url = news.url

        if (url.isNullOrBlank()) {
            scroll.visibility = View.VISIBLE
            web.visibility = View.GONE
        } else {
            scroll.visibility = View.GONE
            web.visibility = View.VISIBLE
            web.settings.javaScriptEnabled = true
            web.settings.useWideViewPort = true
            web.webViewClient = WebViewClient()
            web.loadUrl(url)
        }
    }

    // ═════════════════════════════════════════════════════════════════
    // ④ bindNews —— 把 News 数据映射到各个 View
    //    这里演示了几种 Kotlin 空安全惯用法:
    //      - `?: 兜底`        Elvis 运算符
    //      - `as? T`          安全类型转换(转失败返回 null,不崩)
    //      - `?.mutate()?.let { ... }`  链式判空 + 副作用
    // ═════════════════════════════════════════════════════════════════
    private fun bindNews(news: News) {
        // ─── 标题 & 正文 ───
        // findViewById<T>(id) 是"泛型版",省去了强转
        findViewById<TextView>(R.id.tvDetailTitle).text = news.title
        findViewById<TextView>(R.id.tvDetailBody).text  = news.summary

        // ─── 元信息:作者 · 时间 · 阅读量:1.2 万 ───
        // getString(resId, args...) 用占位符格式化,占位符定义在 strings.xml
        // 例:<string name="detail_read_count">阅读量:%1$s</string>
        val readCountText = getString(
            R.string.detail_read_count,
            formatReadCount(news.readCount)
        )
        // 字符串模板(string template):Kotlin 用 "$var" / "${expr}" 拼字符串
        findViewById<TextView>(R.id.tvDetailMeta).text =
            "${news.author} · ${news.time} · $readCountText"

        // ─── 置顶徽标:只在 isTop 时可见 ───
        // View.VISIBLE / GONE / INVISIBLE 三种状态:
        //   VISIBLE   —— 显示且占空间
        //   INVISIBLE —— 不显示但占空间(布局位置保留)
        //   GONE      —— 不显示且不占空间(布局收缩)
        findViewById<TextView>(R.id.tvDetailBadgeTop).visibility =
            if (news.isTop) View.VISIBLE else View.GONE

        // ─── 分类文字标签 ───
        findViewById<TextView>(R.id.tvDetailCategory).text = news.category

        // ─── 分类色块 —— 动态改颜色 ───
        val tvCover = findViewById<TextView>(R.id.tvDetailCover)
        // take(1) 取字符串前 1 位:"科技" → "科",作为色块内的分类首字
        tvCover.text = news.category.take(1)

        // when 表达式:根据分类映射到不同的颜色资源 id
        val colorRes = when (news.category) {
            "科技" -> R.color.cat_tech
            "汽车" -> R.color.cat_car
            "AI"  -> R.color.cat_ai
            else  -> R.color.cat_default   // else 分支必须有,when 才是"表达式"
        }
        // ContextCompat.getColor 用于跨版本安全获取颜色(考虑 API 23+ 的主题色)
        val color = ContextCompat.getColor(this, colorRes)

        // ⚠️ 关键:改 shape drawable 颜色前**必须 mutate()**,
        //    否则整个进程内所有引用这个 drawable 的 View 都会一起变色(状态共享)。
        //    `as?` 安全转换:如果 background 不是 GradientDrawable(如被主题 tint 换掉),
        //    会返回 null,链式 `?.` 后面的 mutate/let 就跳过,不会崩溃。
        (tvCover.background as? GradientDrawable)
            ?.mutate()                                        // 复制一份 ConstantState
            ?.let { (it as GradientDrawable).setColor(color) }// 只改这一份

        // ─── 顶部 AppBar 也染成分类色,让详情页有"归属感" ───
        // 这里 findViewById<View> 直接拿到 AppBarLayout(View 的父类可接收所有 View 子类)
        findViewById<View>(R.id.appBar).setBackgroundColor(color)
    }

    // ═════════════════════════════════════════════════════════════════
    // ⑤ formatReadCount —— 阅读量美化
    //    12500 → "1.2 万",8600 → "8.6k",800 → "800"
    //
    // Kotlin 语法点:
    //   - `when { 条件1 -> 值1; 条件2 -> 值2; else -> 值3 }` 无参 when,像 if/else if 链
    //   - `String.format` 用 Locale-independent 需要显式传 Locale.US,
    //     这里省略了 —— 如果国际化后发现小数点变逗号,改用 Locale.US
    // ═════════════════════════════════════════════════════════════════
    private fun formatReadCount(count: Int): String = when {
        count >= 10000 -> String.format("%.1f 万", count / 10000.0)
        count >= 1000  -> String.format("%.1fk", count / 1000.0)
        else           -> count.toString()
    }

    // ═════════════════════════════════════════════════════════════════
    // 挑战 2 新增:onDestroy —— 释放 WebView,防内存泄漏
    //    顺序:先 stopLoading() 再 destroy(),不能反
    // ═════════════════════════════════════════════════════════════════
    override fun onDestroy() {
        findViewById<WebView>(R.id.webDetail)?.let {
            it.stopLoading()
            it.destroy()
        }
        super.onDestroy()
    }
}