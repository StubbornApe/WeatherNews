package com.example.weathernewsapp.common

// ============ import 分区(按 Android 官方约定:android → androidx → 项目) ============
import android.content.Context // Fragment onAttach 的入参类型
import android.os.Bundle // 状态保存/恢复
import android.util.Log // 日志 API
import android.view.View // Fragment 的 View 树
import androidx.annotation.LayoutRes // 编译期校验参数必须是 @layout 资源
import androidx.fragment.app.Fragment // Jetpack Fragment 基类

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * LifecycleLoggingFragment —— Fragment 生命周期日志学习专用基类
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * 【目标】
 *   让所有子类 Fragment 都能自动打印 12 个生命周期回调的日志,
 *   把 Day05 引入的 3 个 Fragment(News/Weather/Mine) 里重复的日志代码
 *   收敛到基类,子类只需专注业务逻辑。
 *
 * 【用法示例】
 *   class NewsFragment : LifecycleLoggingFragment(R.layout.fragment_news) {
 *       override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
 *           super.onViewCreated(view, savedInstanceState)   // ⚠️ 必须调 super
 *           // ...业务:findViewById / 装 Adapter / 注册监听...
 *       }
 *   }
 *
 * 【设计要点】
 *   1. 主构造参数 @LayoutRes contentLayoutId
 *      → 转发给父类 Fragment(contentLayoutId) 的次构造函数,
 *        Jetpack Fragment 1.2+ 会自动在 onCreateView 里完成 inflate,
 *        免去手写 onCreateView 里的 inflater.inflate(...) 样板代码。
 *
 *   2. open class + protected open val logTag
 *      → 子类可以在特殊场景覆写 logTag 起自定义名字(默认取类的 simpleName),
 *        `open` 让本类可被继承,`protected` 让 logTag 仅子类可见,不污染外部。
 *        ⚠️ 注意不叫 tag,因为 Fragment.getTag() 已存在(FragmentManager 用),
 *           Kotlin 属性 `val tag` 会与 `getTag()` 签名冲突(Accidental override)。
 *
 *   3. companion object 里的 LC_TAG = "LC"
 *      → 统一用 "LC" 作为 Logcat TAG,过滤时只需一个过滤器,
 *        Activity 版基类(LifecycleLoggingActivity)也用同名 TAG,
 *        Logcat 里过滤 "LC" 就能看到 Activity + Fragment 全部生命周期。
 *        (为什么不把类名放 TAG?见 Day04 笔记 Q2:TAG 长度上限 23 字符 + 过滤便利性)
 *
 * 【为什么不覆写 onCreateView?】
 *   因为父类 Fragment(contentLayoutId) 已接管 onCreateView 的 inflate 逻辑,
 *   我们再覆写反而会破坏这个机制。如果确实需要在 onCreateView 里做事,
 *   建议放到 onViewCreated —— 那时 View 已经 inflate 完成且更安全。
 *
 * 【为什么 Activity 有 7 个回调,Fragment 有 12 个?】
 *   Fragment 多出 5 个与 View 相关的回调(onAttach/onCreateView/onViewCreated/
 *   onDestroyView/onDetach),用于区分"Fragment 实例生命周期"和"Fragment View
 *   生命周期"——见 Day05 笔记 Q2:两条生命周期是 Fragment 相比 Activity 的最大差异。
 *
 * 【生产项目替代方案】
 *   本基类通过继承实现日志共享,吃掉了唯一的继承槽。生产项目更推荐用
 *   androidx.lifecycle.DefaultLifecycleObserver + lifecycle.addObserver(),
 *   走"组合"路线,可挂多个 observer(日志 + 埋点 + 崩溃上报各挂一个)。
 *   学习期用继承是"教具",让每一次子类实例化都能感受到"生命周期回调
 *   是从父类继承来的"。第 3 周(Day 11+)会重构成 Observer 方案作为对比。
 * ═══════════════════════════════════════════════════════════════════════════
 */
open class LifecycleLoggingFragment(
    @LayoutRes contentLayoutId: Int,
) : Fragment(contentLayoutId) {
    /**
     * Logcat 消息里显示的类名。
     * `open` 允许子类覆写(比如给同一个类的不同实例起不同名字方便区分)。
     * `protected` 限制访问范围,外部代码不应关心这个字段。
     * 默认取类的 simpleName(如 "NewsFragment"),而不是 qualifiedName,
     * 是为了消息更简洁。
     *
     * ⚠️ 命名注意:不能叫 `tag` —— Fragment 基类已经有一个 `getTag(): String?`
     * (通过 FragmentManager add/replace 时设置的 tag),Kotlin 属性 `val tag`
     * 会生成 `getTag(): String`,与父类方法 JVM 签名冲突,构成"意外覆盖",
     * 编译报错:Accidental override。所以这里改名为 `logTag`。
     */
    protected open val logTag: String = this::class.java.simpleName

    // ═══════════════════════════════════════════════════════════════════
    // ① onAttach —— Fragment 附着到宿主 Activity
    //   此时可拿到 Context/Activity 引用;但 View 还没创建,
    //   不能访问 view 相关的东西。
    // ═══════════════════════════════════════════════════════════════════
    override fun onAttach(context: Context) {
        super.onAttach(context) // ⚠️ 必须先调 super
        Log.d(LC_TAG, "$logTag -> onAttach")
    }

    // ═══════════════════════════════════════════════════════════════════
    // ② onCreate —— Fragment 实例初始化
    //   适合做:初始化 ViewModel、解析 arguments。
    //   不要:inflate 布局(那是父类 Fragment(contentLayoutId) 在 onCreateView 做的事)。
    // ═══════════════════════════════════════════════════════════════════
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(LC_TAG, "$logTag -> onCreate")
    }

    // ═══════════════════════════════════════════════════════════════════
    // ③ onViewCreated —— ⭐ View 已经 inflate 完成
    //   适合做:findViewById / 装 Adapter / 注册监听 / 用 viewLifecycleOwner
    //          去 collect Flow 或 observe LiveData。
    //   注意:View 生命周期从这一刻起,到 onDestroyView 结束,
    //        与 Fragment 实例的 onCreate ~ onDestroy 是两条不同的生命周期
    //        (见 Day05 笔记 Q2)。
    // ═══════════════════════════════════════════════════════════════════
    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(LC_TAG, "$logTag -> onViewCreated")
    }

    // ═══════════════════════════════════════════════════════════════════
    // ④ onStart —— 与宿主 Activity 联动
    //   Activity onStart 后 Fragment 才 onStart,反过来也一样。
    //   适合做:注册广播、启动动画。
    // ═══════════════════════════════════════════════════════════════════
    override fun onStart() {
        super.onStart()
        Log.d(LC_TAG, "$logTag -> onStart")
    }

    // ═══════════════════════════════════════════════════════════════════
    // ⑤ onResume —— 完全可见且可交互
    //   适合做:恢复摄像头预览、传感器订阅。
    //   不要:做耗时操作,会卡帧。
    // ═══════════════════════════════════════════════════════════════════
    override fun onResume() {
        super.onResume()
        Log.d(LC_TAG, "$logTag -> onResume")
    }

    // ═══════════════════════════════════════════════════════════════════
    // ⑥ onPause —— 失去焦点(半透明弹窗/被覆盖)
    //   适合做:暂停动画、保存草稿。
    //   ⚠️ 保存操作必须**快速**,否则会挡住下一个 Fragment 的显示。
    // ═══════════════════════════════════════════════════════════════════
    override fun onPause() {
        super.onPause()
        Log.d(LC_TAG, "$logTag -> onPause")
    }

    // ═══════════════════════════════════════════════════════════════════
    // ⑦ onStop —— 完全不可见(被覆盖或 Home)
    //   适合做:释放大对象、注销监听。
    //   不要:更新 UI(此时 View 可能已不再可见)。
    // ═══════════════════════════════════════════════════════════════════
    override fun onStop() {
        super.onStop()
        Log.d(LC_TAG, "$logTag -> onStop")
    }

    // ═══════════════════════════════════════════════════════════════════
    // ⑧ onDestroyView —— ⭐ Fragment View 生命周期结束
    //   适合做:释放 ViewBinding / 置空 View 引用,避免内存泄漏。
    //   注意:此时 Fragment 实例可能还活着(如加入了回退栈),
    //        再次 onCreateView 时 View 会重建。所以 collect Flow /
    //        observe LiveData 必须用 viewLifecycleOwner,让协程/观察者
    //        随 View 生命周期一起被取消。
    //   记忆:View 走完 = 从 STARTED → DESTROYED,但 Fragment 实例仍在 CREATED。
    // ═══════════════════════════════════════════════════════════════════
    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(LC_TAG, "$logTag -> onDestroyView")
    }

    // ═══════════════════════════════════════════════════════════════════
    // ⑨ onDestroy —— Fragment 实例被销毁
    //   适合做:释放最终资源(数据库连接、Job.cancel())。
    //   注意:此回调**不保证一定被调用**(内存不足场景可能跳过),
    //        真正必须清理的资源应放到 onStop / onDestroyView 里更保险。
    // ═══════════════════════════════════════════════════════════════════
    override fun onDestroy() {
        super.onDestroy()
        Log.d(LC_TAG, "$logTag -> onDestroy")
    }

    // ═══════════════════════════════════════════════════════════════════
    // ⑩ onDetach —— 与宿主 Activity 解绑
    //   与 onAttach 对称,此时不再能拿到宿主 Context。
    //   通常无需处理,极少数场景需要清理"回调接口"引用避免泄漏。
    // ═══════════════════════════════════════════════════════════════════
    override fun onDetach() {
        super.onDetach()
        Log.d(LC_TAG, "$logTag -> onDetach")
    }

    companion object {
        /**
         * Logcat 过滤器统一用 "LC"(Lifecycle 缩写)。
         * ⚠️ Android TAG 最大 23 个字符,不要放长类名进来。
         * 类名放消息里(见每个 Log.d 的第二个参数)更清晰。
         */
        const val LC_TAG = "LC"
    }
}
