package com.example.weathernewsapp.common

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.io.IOException

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * retryOnNetworkError —— 网络异常自动重试(指数退避)
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * 【适用场景】
 *   短时网络抖动 / DNS 偶发失败 / 服务端瞬时 5xx —— 立即重试大概率能成功。
 *
 * 【不适用场景】
 *   · 客户端 4xx(请求本身有问题,重试没用)
 *   · JSON 解析失败(数据格式问题,重试没用)
 *   · 用户手动取消(CancellationException,必须放行)
 *
 * 【指数退避策略】
 *   第 1 次失败 → 等 initialDelayMs 后重试
 *   第 2 次失败 → 等 initialDelayMs * factor 后重试
 *   第 3 次失败 → 等 initialDelayMs * factor^2 后重试
 *   ...
 *   避免瞬间集中重试压垮服务端。
 *
 * @param times          最大重试次数(不含第一次)。times = 2 表示"最多总共尝试 3 次"
 * @param initialDelayMs 首次重试前的等待毫秒数
 * @param maxDelayMs     单次重试最大等待时间,防止指数增长过大
 * @param factor         每次退避的倍数
 * @param block          实际要执行的 suspend 逻辑
 * @return               成功时的 block 返回值;所有重试都失败则抛出最后一次的异常
 * ═══════════════════════════════════════════════════════════════════════════
 */
// suspend fun:表示这是一个"可挂起函数"
//   · 因为内部会调用 delay(...)(也是 suspend 函数),所以必须在协程作用域里调用
//   · 只能被另一个 suspend fun 或 launch/async 内部调用
//
// <T>:泛型类型参数
//   · block 返回什么类型,retryOnNetworkError 就返回什么类型
//   · 让本函数可以复用到任何返回值类型(Weather / News / Any)
//
// 参数说明:
//   · times          重试次数(不含第一次)。默认 2 → 最多总共尝试 3 次(1 次原始 + 2 次重试)
//   · initialDelayMs 首次重试前的等待毫秒数,默认 500ms
//   · maxDelayMs     单次等待的上限,防止指数增长后等太久,默认 4 秒
//   · factor         每次等待时间的倍数,默认 2.0(标准的指数退避)
//   · block          真正要执行的 suspend 函数(比如 api.getCurrentWeather(...))
//     · 类型 suspend () -> T:表示"一个可挂起的、无参、返回 T 的函数类型"
//     · 用 lambda 方式调用:retryOnNetworkError { api.getCurrentWeather(...) }
suspend fun <T> retryOnNetworkError(
    times: Int = 2,
    initialDelayMs: Long = 500,
    maxDelayMs: Long = 4_000,
    factor: Double = 2.0,
    block: suspend () -> T
): T {
    // var 可变变量:currentDelay 会在每次失败后被更新为"下次要等的时间"
    //   · Long 类型,与 delay(Long) 的参数类型一致
    var currentDelay = initialDelayMs

    // repeat(n) { block }:Kotlin 标准库函数,把 block 执行 n 次
    //   · 等价于 for (i in 0 until n) { ... }
    //   · lambda 参数 attempt 就是当前是第几次(从 0 开始)
    repeat(times) { attempt ->
        try {
            // ⭐ 尝试执行传入的 block(比如 api.getCurrentWeather(...))
            //   · 成功 → 立即 return block 的返回值,整个 retry 函数结束
            //   · 抛异常 → 进入下面的 catch 分支
            //   · 注意:return 在这里是"从 retryOnNetworkError 返回",不是从 repeat 返回
            return block()

        } catch (e: CancellationException) {
            // ⭐ 特例:CancellationException 是协程取消信号,必须原样抛出
            //   · 用户离开页面 / View 销毁 → scope 取消 → block 抛 CancellationException
            //   · 若把它当作错误 delay 重试,协程状态机会被破坏,导致资源无法释放
            //   · throw e 让异常继续向上冒泡,scope 层做正常的取消清理
            throw e

        } catch (e: IOException) {
            // ⭐ 只对网络类异常(IOException 及子类)重试
            //   · SocketTimeoutException / UnknownHostException 都是它的子类
            //   · 因为这些异常大概率是"短时抖动",立即重试很可能就成功了

            // android.util.Log.w:打 Warning 级别的 Logcat 日志
            //   · 第 1 个参数是 tag("retryOnNetworkError")
            //   · 第 2 个参数是消息内容,用字符串模板 ${...} 拼接变量
            //   · e.javaClass.simpleName:拿到异常类的简短名(如 "UnknownHostException")
            //   · attempt + 1:因为 attempt 从 0 开始,展示时 +1 让读日志更直观
            android.util.Log.w(
                "retryOnNetworkError",
                "attempt ${attempt + 1}/$times failed: ${e.javaClass.simpleName}, retry in ${currentDelay}ms"
            )

            // delay(Long):协程标准库的挂起函数,让当前协程"睡 currentDelay 毫秒"
            //   · ⚠️ 不是 Thread.sleep!delay 是挂起点,让出线程,不阻塞线程池
            //   · 期间线程可以去处理别的协程任务
            //   · 睡够时间后自动恢复,继续执行下一行
            delay(currentDelay)

            // 计算下一次的等待时间(指数退避):
            //   · currentDelay * factor:比如 500ms * 2.0 = 1000ms
            //   · .toLong():转成 Long,因为 delay 需要 Long
            //   · .coerceAtMost(maxDelayMs):取"当前值与上限的较小值",防止无限增长
            //     · 例如:1000 * 2 = 2000,2000 * 2 = 4000,4000 * 2 = 8000 → 但上限是 4000,所以取 4000
            //   · 效果:500ms → 1000ms → 2000ms → 4000ms → 4000ms → ...(每次翻倍,封顶 4 秒)
            currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelayMs)
        }
        // 其它异常(HttpException / JsonSyntaxException)不 catch → 直接向外冒泡
        //   · 这些错误重试也无意义:4xx 是请求本身错、JsonSyntaxException 是数据格式错
        //   · 交给外层调用方(WeatherRepository)处理
    }

    // 用完所有重试次数(times 次)后,再执行最后一次
    //   · 若成功 → 返回结果
    //   · 若失败 → 异常直接向上抛(这次没有 try/catch 包)
    //   · 这就是"最多总共尝试 times+1 次"的实现:repeat 里 times 次 + 这里最后 1 次
    return block()
}