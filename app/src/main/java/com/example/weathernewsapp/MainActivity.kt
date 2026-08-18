package com.example.weathernewsapp

// ============ import 分区(按 Android 官方约定:android → androidx → 第三方 → 项目) ============
import android.os.Bundle                                            // Activity 状态保存/恢复入参
import androidx.activity.enableEdgeToEdge                           // Android 15+ 边到边模式(3.8 节)
import androidx.core.view.ViewCompat                                // WindowInsets 兼容 API
import androidx.core.view.WindowInsetsCompat                        // 系统栏 Inset 类型枚举
import androidx.navigation.fragment.NavHostFragment                 // Navigation:承载 Fragment 的宿主
import androidx.navigation.ui.setupWithNavController                // NavigationUI 扩展函数:一行绑定 BottomNav
import com.example.weathernewsapp.common.LifecycleLoggingActivity   // 生命周期日志基类(day05 抽取)
import com.google.android.material.bottomnavigation.BottomNavigationView  // Material 底部导航栏
import androidx.appcompat.app.AppCompatDelegate
import com.example.weathernewsapp.data.datastore.SettingsDataStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
/**
 * ═══════════════════════════════════════════════════════════════════════════
 * MainActivity —— App 的宿主 Activity
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * 【今日角色变化(vs Day04)】
 *   Day04:自己 findViewById RecyclerView + 装 NewsAdapter + 处理点击跳转
 *   Day05:所有列表逻辑迁到 NewsFragment,本类只负责两件事——
 *          ① 装载 NavHostFragment
 *          ② 把 BottomNavigationView 与 NavController 绑定
 *
 * 【继承 LifecycleLoggingActivity 的意义】
 *   - 免写 7 个 onXxx 回调,日志自动打印(Logcat 过滤 "LC" 可见)
 *   - 类名由基类通过 `this::class.java.simpleName` 自动获取,
 *     日志会显示 "MainActivity -> onCreate"
 *
 * 【为什么 MainActivity 只做宿主?】
 *   遵循单一职责原则:Activity 负责"应用外壳"(状态栏、底部导航、
 *   NavHost),Fragment 负责"每个业务页面"。这样后续新增业务页时,
 *   只需要建新 Fragment + 在 nav_graph 加一个 destination,
 *   不用改 MainActivity。
 * ═══════════════════════════════════════════════════════════════════════════
 */
@AndroidEntryPoint
class MainActivity : LifecycleLoggingActivity() {

    /**
     * Activity 唯一生命周期入口。
     * 注意:由于继承了 LifecycleLoggingActivity,基类已经在 super.onCreate
     * 里打印过日志,这里就不用再写 Log.d 了。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        // ═══════════════════════════════════════════════════════════════
        // ① enableEdgeToEdge() —— 开启"沉浸式"边到边模式
        // ═══════════════════════════════════════════════════════════════
        //   等价于旧 API:
        //     window.setDecorFitsSystemWindows(false)
        //     WindowCompat.setDecorFitsSystemWindows(window, false)
        //   开启后:
        //     · 状态栏 / 导航栏变透明,内容延伸到全屏
        //     · Android 15 之后是**默认行为**,不主动 opt-in 也会强制开启
        //   ⚠️ 必须在 super.onCreate 之前调用(Activity 主题相关),否则不生效。
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)          // ⚠️ 必须先调 super,保证基类日志、
                                                    //     ViewModel、SavedState 正常初始化

        // ═══════════════════════════════════════════════════════════════════════
        //  ⭐ Day 09:在 setContentView 之前读 DataStore 的深色模式设置并应用
        // ═══════════════════════════════════════════════════════════════════════
        //
        // 【为什么用 runBlocking 而不是 lifecycleScope.launch?】
        //   如果用 launch,协程在后台异步读文件,主线程继续往下走 setContentView,
        //   此时还没读到深色设置 → 界面先用浅色渲染 → 几百毫秒后读到了再 recreate
        //   → 用户看到一次"白→黑"闪烁。
        //
        //   runBlocking 会阻塞主线程直到读到值(通常 10-50ms,因为 DataStore
        //   有内存缓存,首次安装读文件也极快),然后再 setContentView,
        //   保证第一次渲染就是正确的主题。
        //
        // 【runBlocking 会不会 ANR?】
        //   不会。DataStore 首次读文件是在 IO 线程执行的,
        //   runBlocking 只是让主线程挂起等结果,不是在主线程做磁盘 IO。
        //   而且 DataStore 文件极小(几行键值对),读取耗时 < 50ms。
        //   ⚠️ 但不要在 runBlocking 里做网络请求或大量计算,那才会卡。
        //
        // ── 逐行语法拆解 ──

        // runBlocking { ... }  ← Kotlin 标准库中的【协程构建器】
        //
        //   runBlocking         ← 函数名,意思是"运行一个协程并阻塞当前线程直到它完成"
        //                         ⚠️ 它是一个"桥接函数":把非协程世界(普通函数)和协程世界
        //                            连接起来。只有在 main() 函数、测试、启动时读偏好
        //                            这种"必须同步等待"的场景才用。
        //                         通常的协程构建器(launch/async)不会阻塞线程,只有
        //                         runBlocking 会阻塞。所以 Google 官方文档说"不要在生产
        //                         代码里用 runBlocking",但"App 启动时读一个小偏好文件"
        //                         是社区公认的可接受例外。
        //
        //   { ... }             ← Lambda 参数:runBlocking 接收一个 suspend Lambda,
        //                         在 Lambda 内部可以调用 suspend 函数(如 .first())
        //   runBlocking 的返回值类型就是 Lambda 最后一行的类型
        //   这里我们没接收返回值,只用了副作用(设置深色模式)
        runBlocking {
            // ── 第 1 行:创建 SettingsDataStore 实例 + 读 settingsFlow ──

            // val              ← 只读变量(赋值后不可改)
            // settings          ← 变量名,类型是 UserSettings(由 Kotlin 类型推断自动推导)
            // =                 ← 赋值运算符
            //
            // SettingsDataStore(...)  ← 调用构造函数创建实例
            //   this@MainActivity     ← 【带标签的 this】,因为我们在 runBlocking 的
            //                            Lambda 里,this 指向 Lambda 的接收者(runBlocking
            //                            的 CoroutineScope),要访问 MainActivity 的
            //                            applicationContext 就必须用 this@MainActivity
            //   .applicationContext   ← Activity 的成员属性,返回应用级 Context(单例,
            //                           不会被 Activity 销毁影响,避免内存泄漏)
            //
            // .settingsFlow         ← 访问 SettingsDataStore 的成员属性:
            //                          类型是 Flow<UserSettings>
            //                          每次 DataStore 值变化它会自动发射新值
            //
            // .first()              ← Flow 的【终止操作符】(terminal operator)
            //                         它是一个 suspend 函数:
            //                           1. 挂起当前协程,直到 Flow 发射第一个值
            //                           2. 收到第一个值后,自动取消 collect,返回这个值
            //                           3. 如果 Flow 为空(永远不会发生,因为 DataStore
            //                              至少会发射一个值),抛出 NoSuchElementException
            //                         这里我们只读"当前值"初始化 UI,不需要持续监听
            val settings = SettingsDataStore(this@MainActivity.applicationContext)
                .settingsFlow
                .first()

            // ── 第 2 行:if 表达式决定夜间模式常量 ──

            // val nightMode = if (...) { ... } else { ... }
            //   Kotlin 中 if 是【表达式】,不是语句!
            //   这意味着 if 可以返回值,赋值给变量。
            //   对比 Java:Java 的 if 是语句,不能赋值,必须写三元运算符 ? :
            //
            //   if (settings.darkMode)  ← 条件:settings.darkMode 是 Boolean 类型
            //                              darkMode 是 UserSettings data class 的字段
            //
            //   AppCompatDelegate.MODE_NIGHT_YES  ← 伴生对象常量,值是 2
            //   AppCompatDelegate.MODE_NIGHT_NO   ← 伴生对象常量,值是 1
            //
            //   整句效果:settings.darkMode=true 时 nightMode = MODE_NIGHT_YES,
            //           否则 nightMode = MODE_NIGHT_NO
            val nightMode = if (settings.darkMode) {
                AppCompatDelegate.MODE_NIGHT_YES
            } else {
                AppCompatDelegate.MODE_NIGHT_NO
            }

            // ── 第 3 行:应用夜间模式 ──

            // AppCompatDelegate          ← AndroidX 的委托类,负责 AppCompat 主题兼容
            // .setDefaultNightMode(...)  ← 【伴生对象方法】(静态方法)
            //                             设置全局夜间模式,影响所有 Activity
            //                               MODE_NIGHT_FOLLOW_SYSTEM = -1
            //                               MODE_NIGHT_NO            = 1
            //                               MODE_NIGHT_YES           = 2
            //                             调用后:
            //                               1. 如果当前 Activity 在前台,系统会
            //                                  recreate Activity(走 onDestroy→onCreate)
            //                               2. 新主题立即生效(不用等重启)
            //                               3. 这个值会被持久化到 AppCompat 内部配置,
            //                                  下次启动不用重新设置(但我们的 DataStore
            //                                  才是唯一真相源,所以每次启动还是要读
            //                                  DataStore 再设一次)
            AppCompatDelegate.setDefaultNightMode(nightMode)
        }
        // ↑ runBlocking 执行完毕:主线程被阻塞了约 10-50ms,
        //   此时深色/浅色主题已设置好,后续 setContentView 渲染的
        //   就是正确的主题,不会出现"白→黑"闪屏。

        setContentView(R.layout.activity_main)      // 加载 3.3 节创建的宿主布局

        // ═══════════════════════════════════════════════════════════════
        // ② 给 BottomNav 加 padding,避免被系统导航条遮挡
        // ═══════════════════════════════════════════════════════════════
        //
        // 【WindowInsets 是什么?】
        //   系统告诉 App "你的 View 上下左右分别有多少 dp 被系统 UI(状态栏、
        //   导航栏、输入法)占用"的元数据。
        //
        // 【setOnApplyWindowInsetsListener 的作用】
        //   注册一个回调,系统在 dispatch WindowInsets 时会传给我们,
        //   由我们决定如何"消费"这些 inset(通常是加 padding 或 margin)。
        //
        // 【Type.systemBars()】
        //   同时包含状态栏 + 导航栏。三指手势条也算在导航栏里。
        //
        // 【为什么只加左右 + 底部 padding,不加顶部?】
        //   BottomNav 在屏幕底部,只有下方会被导航栏遮挡,左右可能被曲面屏
        //   或折叠屏侧边遮挡,而顶部不需要给状态栏让位。
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.bottomNav)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // 保留 View 原有的 top padding,只覆盖 left/right/bottom
            v.setPadding(bars.left, v.paddingTop, bars.right, bars.bottom)
            insets   // 返回原 insets(不 consume,允许子 View 继续接收)
        }

        // ═══════════════════════════════════════════════════════════════
        // ③ 给 NavHost 加顶部 padding,避免 Fragment 顶部内容被状态栏挡住
        // ═══════════════════════════════════════════════════════════════
        //   可选步骤:如果 Fragment 顶部就是列表且不介意穿透到状态栏(比如
        //   希望滚动内容从状态栏后面滑过),就不需要这段。
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.navHostFragment)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, bars.top, v.paddingRight, v.paddingBottom)
            insets
        }

        // ═══════════════════════════════════════════════════════════════
        // ④ 拿到 NavHostFragment 实例 → 再拿 NavController
        // ═══════════════════════════════════════════════════════════════
        //
        // 为什么不能直接 findViewById<FragmentContainerView>(id).navController?
        //   FragmentContainerView 只是"外壳 View",真正的 Fragment 实例由
        //   FragmentManager 管理。想拿 NavController,必须先从 FragmentManager
        //   拿到里面的 NavHostFragment,再取它的 navController。
        //
        // as NavHostFragment:
        //   findFragmentById 返回的是 Fragment?(可空、且是父类型),
        //   我们已经知道它就是 NavHostFragment,所以用非空强转 as。
        //   ⚠️ 如果 activity_main.xml 里 android:name 写错(不是
        //      androidx.navigation.fragment.NavHostFragment),这行会
        //      抛 ClassCastException,是个常见坑。
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment

        // 每个 NavHost 对应一个 NavController(导航控制器)。
        // NavController 提供:
        //   - navigate(destId)  跳转
        //   - popBackStack()    退栈
        //   - currentDestination 观察当前所在页面
        val navController = navHostFragment.navController

        // ═══════════════════════════════════════════════════════════════
        // ⑤ 一行绑定 BottomNav ↔ NavController
        // ═══════════════════════════════════════════════════════════════
        //
        // setupWithNavController 内部做的三件事(读源码 NavigationUI.kt):
        //   1. bottomNav.setOnItemSelectedListener { item ->
        //        onNavDestinationSelected(item, navController)  // 按 item id 跳转
        //      }
        //   2. navController.addOnDestinationChangedListener { _, dest, _ ->
        //        bottomNav.menu.findItem(dest.id)?.isChecked = true  // 同步选中态
        //      }
        //   3. bottomNav.setOnItemReselectedListener { /* 默认拦截,防止重复切换 */ }
        //
        // 换句话说:点 Tab → 切 Fragment;切 Fragment(比如深链/代码 navigate) →
        // 更新 Tab 高亮。⭐ 全靠 menu item id 与 nav_graph destination id 相同来匹配。
        findViewById<BottomNavigationView>(R.id.bottomNav)
            .setupWithNavController(navController)
    }
}