package com.example.weathernewsapp.common

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * NetworkResult —— 把"异常"作为"值"返回的通用容器
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * 【为什么用 sealed class 而不是直接 throw?】
 *   1. 显式:调用方一眼看出这个函数会失败,必须处理 Error 分支
 *   2. 类型安全:when(result) { Success -> ..., Error -> ... } 编译器强制穷举
 *   3. 便于链式转换:.map { } / .onSuccess { } / .onError { }
 *   4. 与 StateFlow 天然契合(Day 12):val state = MutableStateFlow<NetworkResult<Weather>>(Loading)
 *
 * 【三个子类型的含义】
 *   · Loading:请求进行中(暂时不发射,预留给 Flow 时使用)
 *   · Success:请求成功,携带数据
 *   · Error:请求失败,携带 ErrorType(分类的错误)+ 可选原始异常
 *
 * 【sealed class 是什么?】
 *   · out T:声明协变(covariant)—— 允许 NetworkResult<Weather> 赋给 NetworkResult<Any>
 *   · 编译器保证:所有子类都在同一文件内,when 分支能强制穷举
 *   · 相比 enum 更强大:每个子类可以有自己的属性和构造函数
 * ═══════════════════════════════════════════════════════════════════════════
 */
sealed class NetworkResult<out T> {
    // object 单例子类:表示"没有额外数据"的状态
    //   · Loading 不需要携带数据,用 object 只有一个实例,内存最省
    //   · NetworkResult<Nothing>:Nothing 是所有类型的子类,
    //     所以 Loading 可以被赋值给任何 NetworkResult<T> 变量
    object Loading : NetworkResult<Nothing>()

    // data class 携带数据的子类:成功时装着实际结果
    //   · <T> 是类型参数,可以是 Weather / News / 任意类型
    //   · data class 自动生成 equals / hashCode / toString / copy,
    //     方便调试、日志输出和状态比较
    data class Success<T>(val data: T) : NetworkResult<T>()

    // Error 也用 data class:携带错误分类 + 可选原始异常
    //   · type: ErrorType    → 分类错误,让 UI 层根据类型显示不同文案
    //   · throwable: Throwable? = null → 保留原始异常引用,方便调试和日志上报
    //   · NetworkResult<Nothing>:同 Loading,让 Error 可以出现在任何 T 的容器中
    data class Error(val type: ErrorType, val throwable: Throwable? = null) : NetworkResult<Nothing>()
}

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * ErrorType —— 网络错误的分类枚举
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * 把"技术层的异常类型"翻译成"业务层能看懂的错误类别",
 * UI 层根据类型显示不同提示 / 图标 / 重试策略。
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * 【sealed class 里可以带一个"抽象属性"(userMessage)—— 每个子类必须提供】
 *   · 这样所有 ErrorType 都保证有"用户可读文案",避免遗漏
 *   · UI 层只需 errorType.userMessage 就能拿到显示内容
 * ═══════════════════════════════════════════════════════════════════════════
 */
sealed class ErrorType(val userMessage: String) {
    /** 无网络 / DNS 失败 / 连接失败 */
    object NoNetwork : ErrorType("网络不可用,请检查连接")

    /** 请求超时 */
    object Timeout : ErrorType("请求超时,请稍后重试")

    /** 服务端 5xx */
    data class Server(val code: Int) : ErrorType("服务器异常($code)")

    /** 客户端 4xx */
    data class Client(val code: Int) : ErrorType("请求错误($code)")

    /** JSON 解析失败 */
    object Parse : ErrorType("数据格式异常")

    /** 未知(兜底) */
    data class Unknown(val message: String?) : ErrorType(message ?: "未知错误")
}

/*
 * 【为什么每个子类有的用 object 有的用 data class?】
 *   · object:该错误类型永远长一样,无需额外参数(NoNetwork / Timeout / Parse)
 *              → 单例,节省内存
 *   · data class:该错误类型需要携带信息(Server(500) / Client(404))
 *              → 每次不同数据,需要单独实例
 */

/**
 * 便捷扩展:把 Throwable 映射成 ErrorType。
 * 供 Repository 层 catch 时统一转换。
 *
 * 【扩展函数的工作原理】
 *   · fun Throwable.xxx():"这是 Throwable 的扩展方法"
 *   · 调用方式:someException.toErrorType(),就像 Throwable 自带的方法一样
 *   · 实际底层:Kotlin 生成一个静态方法,把 Throwable 作为第一个参数
 */
fun Throwable.toErrorType(): ErrorType =
// = + when 表达式:整个函数体就是 when 表达式的结果
//   · when (this) 表示"根据 this(即当前 Throwable)的实际类型判断"
    //   · Kotlin 里 when 是表达式,可以直接作为函数返回值
    when (this) {
        // is 关键字:类型判断 + 智能转换
        //   · 匹配"这个 Throwable 是不是 SocketTimeoutException 的实例"
        //   · 匹配成功后,this 在这个分支里自动被视为 SocketTimeoutException 类型
        //   · SocketTimeoutException 是 IOException 的子类(专表示超时)
        is java.net.SocketTimeoutException -> ErrorType.Timeout

        // 匹配 DNS 解析失败(UnknownHostException 也是 IOException 子类)
        //   · 场景:飞行模式 / 无网 / DNS 服务器挂了
        is java.net.UnknownHostException -> ErrorType.NoNetwork

        // 匹配所有其它 IOException(网络断开 / 连接被重置等)
        //   · ⚠️ 这行必须放在 SocketTimeoutException 和 UnknownHostException 之后!
        //   · 因为它们是 IOException 的子类,若 IOException 放前面会"截胡"子类分支
        //   · when 从上到下匹配,命中就停 —— 更具体的类型必须放上面
        is java.io.IOException -> ErrorType.NoNetwork

        // 匹配 Retrofit 的 HttpException(非 2xx 响应)
        //   · 匹配后,进入一个嵌套 when 判断具体状态码
        //   · this.code() 是 HttpException 的方法,返回 HTTP 状态码
        is retrofit2.HttpException -> {
            val code = this.code() // 局部变量:保存状态码,避免重复调用
            when (code) {
                // in 400..499:区间匹配,表示 code 在 400~499(闭区间)
                //   · 常见:400 Bad Request / 401 Unauthorized / 404 Not Found / 429 Too Many Requests
                in 400..499 -> ErrorType.Client(code)

                // 500~599 是服务器错误
                //   · 常见:500 Internal Server Error / 503 Service Unavailable / 504 Gateway Timeout
                in 500..599 -> ErrorType.Server(code)

                // 其它状态码(如 1xx / 3xx,理论上 Retrofit 不会抛)兜底
                else -> ErrorType.Unknown("HTTP $code")
            }
        }

        // 匹配 Gson 的 JSON 语法错误
        //   · 场景:服务端返回不是 JSON(如 HTML 错误页)、字段类型不匹配
        //   · 需要写完整包名 com.google.gson.JsonSyntaxException,
        //     因为文件顶部没 import(避免污染主 import 区)
        is com.google.gson.JsonSyntaxException -> ErrorType.Parse

        // else 分支:所有上面都没匹配到的异常
        //   · this.message 拿异常的 message 字段(可空)
        //   · 传给 ErrorType.Unknown,它内部会做 null 兜底("未知错误")
        else -> ErrorType.Unknown(this.message)
    }

/*
 * 【三段代码整体协作图】
 *
 *   底层技术异常                         .toErrorType()                业务层 ErrorType
 *   ────────────────────────────────────────────────────────────────────────────
 *   SocketTimeoutException          →   Timeout             →   "请求超时..."
 *   UnknownHostException            →   NoNetwork           →   "网络不可用..."
 *   IOException(其它)              →   NoNetwork           →   "网络不可用..."
 *   HttpException 4xx               →   Client(code)        →   "请求错误(4xx)"
 *   HttpException 5xx               →   Server(code)        →   "服务器异常(5xx)"
 *   JsonSyntaxException             →   Parse               →   "数据格式异常"
 *   其它 Throwable                  →   Unknown             →   "未知错误..."
 *
 *   最终:Repository 把结果包成 NetworkResult<T>
 *     ├── Success(Weather)     UI 拿到就渲染
 *     ├── Error(ErrorType, ..) UI 显示 errorType.userMessage
 *     └── Loading              UI 显示转圈(暂时预留,Flow 时使用)
 */
