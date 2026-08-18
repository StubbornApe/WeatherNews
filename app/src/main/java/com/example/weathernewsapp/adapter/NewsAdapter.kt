package com.example.weathernewsapp.adapter

// ============= 依赖导入 =============
import android.view.LayoutInflater // XML → View 的转换器
import android.view.View // View.VISIBLE / View.GONE 常量
import android.view.ViewGroup // Adapter 的 parent 类型
import android.widget.ImageView // 封面图
import android.widget.TextView // ViewHolder 里持有的控件
import androidx.recyclerview.widget.RecyclerView // Adapter 基类
import coil.load // Coil 图片加载扩展
import com.example.weathernewsapp.R // 资源引用(R.layout / R.id / R.color)
import com.example.weathernewsapp.model.News // 数据模型

/**
 * NewsAdapter —— 新闻列表适配器（第 3 天升级版）
 *
 * 职责:告诉 RecyclerView 每一条 News 数据应该怎么渲染成 View。
 *
 * 三大回调:
 *   ① onCreateViewHolder  只在需要新 View 时调一次(RecyclerView 会尽量复用旧的)
 *   ② onBindViewHolder    每次滚动进入屏幕都要调,把数据"绑"到 View 上
 *   ③ getItemCount        告诉 RecyclerView 一共多少条数据
 *
 * 升级点：
 *   1) 支持置顶徽标显示/隐藏
 *   2) 显示分类首字 + 分类色作为封面占位
 *   3) 阅读量格式化（>1000 显示"1.2 万"）
 */
class NewsAdapter(
    // Day 11: 改成 var,以支持 updateData() 全量刷新(MVVM 后由 collect 驱动)
    private var newsList: List<News>,
    // 点击回调,由外部注入。Kotlin 高阶函数类型 (News) -> Unit
    private val onItemClick: (News) -> Unit,
) : RecyclerView.Adapter<NewsAdapter.NewsViewHolder>() {
    // ↑ 继承 RecyclerView.Adapter,泛型参数指定自己的 ViewHolder 类型

    /**
     * ViewHolder —— 缓存一个 item 的所有子 View 引用
     * ================================================
     * 为什么需要 ViewHolder?
     *   findViewById 是耗时操作,每次滚动都调会卡顿。
     *   ViewHolder 模式:创建时 findViewById 一次,后续复用直接读字段。
     */
    class NewsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // 每个 val 对应 item_news.xml 里的一个 TextView
        val ivCover: ImageView = itemView.findViewById(R.id.ivCover) // 封面图(Coil 加载)
        val tvBadgeTop: TextView = itemView.findViewById(R.id.tvBadgeTop) // 置顶徽标
        val tvTitle: TextView = itemView.findViewById(R.id.tvTitle) // 标题
        val tvSummary: TextView = itemView.findViewById(R.id.tvSummary) // 摘要
        val tvMeta: TextView = itemView.findViewById(R.id.tvMeta) // 元信息:作者·时间·阅读量
    }

    /**
     * 创建 ViewHolder(通常一次滚动只调几次,复用后就不再调)
     * @param parent   即将放置该 item 的父容器(RecyclerView 本身)
     * @param viewType 条目类型,本例只有一种类型,忽略
     */
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): NewsViewHolder {
        // ① 用 LayoutInflater 把 XML 转成 View 对象
        //    参数 3 = false 意味着"先不 attach 到 parent",RecyclerView 会自己处理挂载
        val view =
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_news, parent, false)
        // ② 用这个 View 造一个 ViewHolder 返回
        return NewsViewHolder(view)
    }

    /**
     * 每次 item 滚入屏幕(或数据变化后回填)都会调用
     * @param holder   要绑定的 ViewHolder(可能是复用的)
     * @param position 当前是列表第几条(从 0 开始)
     */
    override fun onBindViewHolder(
        holder: NewsViewHolder,
        position: Int,
    ) {
        val news = newsList[position] // 取当前位置的数据

        // ============ 1) 标题、摘要 —— 最基础的绑定 ============
        holder.tvTitle.text = news.title
        holder.tvSummary.text = news.summary

        // ============ 2) 元信息:作者 · 时间 · 阅读量 ============
        //    Kotlin 字符串模板 "${...}" 拼接;调用 formatReadCount 把数字美化
        holder.tvMeta.text = "${news.author} · ${news.time} · ${formatReadCount(news.readCount)}阅读"

        // ============ 3) 置顶徽标显示/隐藏 ============
        //    if 是"表达式",可以直接赋值给 visibility;比 Java 的三元少一层大括号
        holder.tvBadgeTop.visibility = if (news.isTop) View.VISIBLE else View.GONE

        // ============ 4) 封面图:Coil 异步加载(无图时保持 background 占位色块) ============
        holder.ivCover.load(news.imageUrl)

        // ============ 5) 点击事件(预留) ============
        //    Kotlin 的"Lambda 作为最后一个参数可以移到括号外"语法糖:
        //      setOnClickListener { ... }  ≡  setOnClickListener({ ... })
        holder.itemView.setOnClickListener {
            onItemClick(news)
        }
    }

    /**
     * 单表达式函数(=)的简写形式,等价于:
     *   override fun getItemCount(): Int { return newsList.size }
     */
    override fun getItemCount(): Int = newsList.size

    /**
     * Day 11 新增:全量替换数据并刷新列表。
     * 由 NewsFragment 在 collect 到 viewModel.newsList 新值时调用。
     *
     * 为什么用 notifyDataSetChanged 而不是 DiffUtil?
     *   今天是 MVVM 迁移第一步,最简单的全量刷新足够用。
     *   Day 12 之后可换成 ListAdapter + DiffUtil 做增量更新(只刷新变化的 item,动画更顺)。
     */
    fun updateData(newList: List<News>) {
        newsList = newList
        notifyDataSetChanged()
    }

    // ============= 私有工具函数区 =============

    /**
     * 阅读量美化:12500 → "1.2 万",8600 → "8.6k",520 → "520"
     * when {} 无参数形式:每个分支写完整布尔条件,从上到下第一个 true 生效。
     * String.format("%.1f", ...) 保留 1 位小数。
     * ⚠️ 生产代码建议指定 Locale.US,避免部分地区把小数点变成逗号。
     */
    private fun formatReadCount(count: Int): String =
        when {
            count >= 10000 -> String.format("%.1f 万 ", count / 10000.0)
            count >= 1000 -> String.format("%.1fk ", count / 1000.0)
            else -> "$count "
        }
}
