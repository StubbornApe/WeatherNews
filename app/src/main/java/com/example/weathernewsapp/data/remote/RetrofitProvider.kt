package com.example.weathernewsapp.data.remote

// ══════════════════════════════════════════════════════════════════════════════
//  import 分区(按 Android 官方约定:项目内 → okhttp3 → retrofit2 → java 标准库)
// ══════════════════════════════════════════════════════════════════════════════

// 项目自身生成的 BuildConfig 类。Gradle 编译时会根据构建变体(debug/release)
// 生成一个 Java 类,里面有 DEBUG / APPLICATION_ID / VERSION_NAME 等静态字段。
// 用它可以在运行时判断"当前是不是 Debug 构建",决定要不要装日志拦截器等敏感行为。
// ⚠️ AGP 8.0+ 默认关闭生成,需要在 app/build.gradle.kts 加 buildFeatures { buildConfig = true }
import com.example.weathernewsapp.BuildConfig

// OkHttp 的入口类。Retrofit 底层的 HTTP 请求全都交给它执行。
// 一个 App 建议只维护一个 OkHttpClient 单例(内部有连接池、线程池,重复创建极其浪费)。
import okhttp3.OkHttpClient

// OkHttp 官方提供的"日志拦截器"。装到 OkHttpClient 后,每次请求 / 响应
// 都会被它拦截并按指定 level 打到 Logcat,是网络调试第一利器。
// 它其实是单独的 artifact:com.squareup.okhttp3:logging-interceptor,别忘了加依赖。
import okhttp3.logging.HttpLoggingInterceptor

// Retrofit 主类。它提供 Builder 让你配置 baseUrl / OkHttpClient / Converter,
// 然后通过 .create(接口::class.java) 用动态代理生成接口的实现。
import retrofit2.Retrofit

// Retrofit 与 Gson 的桥接器。把它加进 Retrofit Builder 后,
// 接口方法返回的数据类会自动被 Gson 反序列化。
// 若换 Moshi / kotlinx.serialization,这里换成对应的 ConverterFactory 即可。
import retrofit2.converter.gson.GsonConverterFactory

// Java 标准库里表示"时间单位"的枚举(SECONDS / MILLISECONDS / MINUTES...)。
// OkHttp 的超时配置需要传"数值 + 单位"两个参数,单位就用这个。
import java.util.concurrent.TimeUnit


/**
 * ═══════════════════════════════════════════════════════════════════════════
 * RetrofitProvider —— Retrofit 实例的手工装配点
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * 【为什么单独抽一个类?】
 *   Retrofit 实例的创建涉及三件"应用级"配置:
 *     · baseUrl 常量(整个 App 只一个域名)
 *     · OkHttpClient 配置(超时 / 拦截器 / 连接池)
 *     · Converter 选择(Gson / Moshi / kotlinx.serialization)
 *   这些应集中在一处,避免散落到各个 Repository / ViewModel 里重复创建。
 *
 * 【为什么用 object(Kotlin 单例)?】
 *   1. Retrofit 内部维护大量缓存(方法元数据、动态代理实例),
 *      每次 new 一个都会重新解析注解,浪费 CPU 和内存
 *   2. OkHttpClient 内部有连接池 + 线程池,重复创建等于新开一套线程,
 *      单例才能真正复用 TCP 连接
 *   3. Day 14 会用 Hilt 依赖注入替代 object,由 @Module + @Provides 提供
 *      同样的单例(作用域改为 SingletonComponent)。现阶段先用 object 学原理。
 *
 * 【HttpLoggingInterceptor 的 4 个 level】
 *   · NONE    什么都不打(⭐ 生产环境用,防止敏感数据泄露)
 *   · BASIC   打请求行 + 响应行(URL / method / status / 耗时)
 *   · HEADERS 打请求 / 响应头
 *   · BODY    打完整请求体 + 响应体(⭐ Debug 用,能看到 JSON 全文)
 * ═══════════════════════════════════════════════════════════════════════════
 */
object RetrofitProvider {
    // ↑ Kotlin 的 object 关键字 = "线程安全的懒加载单例"。
    //   等价于 Java 里 public class RetrofitProvider {
    //                     private RetrofitProvider() {}
    //                     public static final RetrofitProvider INSTANCE = new RetrofitProvider();
    //                 }
    //   但代码量只需一个 `object 类名`。

    /**
     * Open-Meteo API 的根地址(baseUrl)。
     *
     * 【为什么必须以 "/" 结尾?】
     *   Retrofit 内部用 HttpUrl 拼接 URL:baseUrl + @GET("path") 的 path。
     *   若 baseUrl 不以 "/" 结尾,会被认为是"文件路径"而非"目录路径",
     *   与相对路径 path 拼接的规则会异常,所以 Retrofit 在构建期直接校验并抛异常:
     *   IllegalArgumentException("baseUrl must end in /")
     *
     * 【const val 的作用】
     *   编译期常量,会被编译进字节码的常量池,比 val 略优。
     *   要求:必须写在 companion object 或 object / top-level;类型只能是基本类型或 String。
     */
    private const val BASE_URL = "https://api.open-meteo.com/"

    /**
     * OkHttpClient 单例:
     *   · 连接超时 15s(建立 TCP + TLS 的最长等待时间)
     *   · 读取超时 15s(连接建立后,等服务端返回一次数据的最长时间)
     *   · 写入超时 15s(向服务端发送请求体的最长时间)
     *   · Debug 构建时加日志拦截器,Release 构建时不加
     *
     * 【by lazy 是什么?】
     *   Kotlin 属性委托,首次访问该属性时才求值,后续访问直接返回缓存结果。
     *   默认 LazyThreadSafetyMode.SYNCHRONIZED,保证多线程首次访问也只初始化一次。
     *   相比在类加载时直接创建,能让 App 启动更快 —— 只有真的用到网络时才装配 OkHttp。
     */
    private val okHttpClient: OkHttpClient by lazy {
        // Builder 模式:链式配置各项参数,最后 .build() 得到不可变的 OkHttpClient 实例
        OkHttpClient.Builder()
            // 三个超时配置。TimeUnit.SECONDS 表示"以秒为单位",15 = 15 秒。
            //   - connectTimeout: 建立 TCP + TLS 握手的最长时间。慢网络下适当调大。
            //   - readTimeout:    请求发出后,等一个响应包到来的最长时间。
            //   - writeTimeout:   往 Socket 写请求体的最长时间(GET 请求几乎用不到)。
            //   Retrofit 底层调用 OkHttp,任何一个超时都会抛 SocketTimeoutException,
            //   由业务层 try/catch 转换成 UI error 态。
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)

            // .apply { } 是 Kotlin 标准函数:在其接收者(此处是 Builder)上下文里执行 lambda,
            //   返回接收者本身。用在 Builder 链上是"条件配置"的常见写法 ——
            //   我们只在 Debug 构建时装日志拦截器,不打断链式调用。
            .apply {
                // BuildConfig.DEBUG 是 Gradle 生成的编译期常量。
                //   Debug 构建 = true;Release 构建 = false。
                //   编译器会根据这个值做"死代码消除",Release 包里连 if 都没有,
                //   HttpLoggingInterceptor 相关代码不会被打进 APK,零性能开销。
                if (BuildConfig.DEBUG) {
                    // HttpLoggingInterceptor 是 OkHttp 官方拦截器。
                    //   构造时不带参数 = 默认打到 java.util.logging.Logger(会转到 Logcat)。
                    //   .apply 配置它的 level(默认 NONE,必须显式设置才生效)。
                    val logging = HttpLoggingInterceptor().apply {
                        // Level.BODY = 打印请求头 + 请求体 + 响应头 + 响应体全部。
                        // Debug 期间用它最省事,能看到 JSON 完整内容。
                        // ⚠️ 生产环境改成 NONE,否则 access_token / 用户手机号
                        //    这类敏感数据会被打到日志里,存在隐私泄露风险。
                        level = HttpLoggingInterceptor.Level.BODY
                    }
                    // addInterceptor 把拦截器加进 OkHttp 的"拦截器链"。
                    //   链是有序的:先加的先执行。日志拦截器建议放最外层,
                    //   这样能看到最原始的请求 / 最终的响应。
                    addInterceptor(logging)
                }
            }

            // Builder 收尾:构建不可变的 OkHttpClient 实例。
            //   OkHttp 内部所有配置一旦 build 就冻结,想改配置只能用 newBuilder() 派生新实例。
            .build()
    }

    /**
     * Retrofit 单例。
     *
     * 【为什么也用 by lazy?】
     *   Retrofit 依赖 okHttpClient(见 .client(okHttpClient) 那行),
     *   而 okHttpClient 本身也是 lazy,首次访问才初始化。
     *   两者都 lazy,能保证"最晚需要时才装配整个网络栈",App 冷启动更快。
     */
    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            // .baseUrl(...) —— 全 App 的根地址。必须以 "/" 结尾(见上文说明)。
            .baseUrl(BASE_URL)

            // .client(...) —— 传入我们配置好的 OkHttpClient。
            //   如果不显式传,Retrofit 会 new 一个"裸奔"的 OkHttpClient(没超时、没日志)。
            //   在需要拦截器 / 自定义超时的项目里必须传自己的。
            .client(okHttpClient)

            // .addConverterFactory(...) —— 注册 JSON ↔ Kotlin 转换器。
            //   Gson 的 GsonConverterFactory.create() 会用 Gson 反射解析 JSON。
            //   若换 kotlinx.serialization,这里换成 asConverterFactory 即可(见进阶挑战 1)。
            //   ⚠️ Converter 可以注册多个,Retrofit 按注册顺序尝试。
            .addConverterFactory(GsonConverterFactory.create())

            // 与 OkHttp 一样,.build() 之后 Retrofit 实例不可变。
            .build()
    }

    /**
     * 暴露给外界的天气 API 实例(整个类唯一的 public 成员)。
     *
     * 【retrofit.create() 的原理】
     *   传入 WeatherApi::class.java(反射拿到 Class 对象),
     *   内部通过 Java 动态代理(java.lang.reflect.Proxy.newProxyInstance)
     *   生成一个"匿名实现类",这个类的每个方法调用都会转发到 Retrofit 的
     *   InvocationHandler,后者:
     *     1. 解析方法上的 @GET / @Query / @Path 等注解
     *     2. 拼出完整 URL
     *     3. 用 okHttpClient 发送请求
     *     4. 用 Converter 把响应 JSON 解析成返回类型
     *   → 所以你在 WeatherApi 里只写了 interface,运行时就能像调普通对象一样调用。
     *
     * 【为什么再套一层 by lazy?】
     *   与 retrofit / okHttpClient 保持一致的懒加载。第一次访问
     *   RetrofitProvider.weatherApi 时,才会真正触发 Retrofit + OkHttp 的初始化。
     */
    val weatherApi: WeatherApi by lazy {
        retrofit.create(WeatherApi::class.java)
    }

    // ⭐ Day 13 新增：天行数据 baseUrl（必须以 / 结尾）
    private const val NEWS_BASE_URL = "https://api.tianapi.com/"

    private val newsRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(NEWS_BASE_URL)
            .client(okHttpClient)          // 复用天气的 OkHttpClient
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val newsApi: NewsApi by lazy {
        newsRetrofit.create(NewsApi::class.java)
    }
}