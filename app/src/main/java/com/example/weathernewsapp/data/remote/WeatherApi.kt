package com.example.weathernewsapp.data.remote

// ============ import ============
import com.example.weathernewsapp.data.remote.dto.WeatherResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * WeatherApi —— Open-Meteo 天气 API 的 Retrofit 接口定义
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * 【Retrofit 接口 = 声明式 HTTP 客户端】
 *   你只需要声明"这个方法对应哪个 HTTP 请求",Retrofit 会在运行时
 *   通过动态代理(Proxy.newProxyInstance)生成实现类,自动:
 *     1. 拼 URL(baseUrl + @GET 里的 path + @Query 参数)
 *     2. 发起 HTTP 请求(交给 OkHttp)
 *     3. 收到响应后用 Converter(Gson)把 JSON 解析成返回类型
 *     4. 返回给调用方
 *
 * 【为什么用 interface 不用 class?】
 *   Retrofit 通过 Java 动态代理机制拦截接口方法调用,只有 interface
 *   才能作为代理目标。这也意味着接口里不能写方法体、不能有字段。
 *
 * 【suspend 修饰的意义】
 *   告诉 Retrofit:请生成"协程适配"的实现,方法内部会挂起当前协程
 *   直到网络响应回来,再恢复执行。调用处必须在协程作用域里调用。
 *
 * 【baseUrl 在哪里?】
 *   不在这里 —— baseUrl 是构建 Retrofit 实例时指定(见 RetrofitProvider),
 *   接口里只写"相对路径"(如 v1/forecast),让接口本身可以复用不同环境
 *   的 baseUrl(开发/测试/生产)。
 * ═══════════════════════════════════════════════════════════════════════════
 */
interface WeatherApi {

    /**
     * 获取实况天气。
     *
     * @param lat       纬度(-90 ~ 90)
     * @param lng       经度(-180 ~ 180)
     * @param current   要查询的实况字段,逗号分隔,如 "temperature_2m,weather_code,wind_speed_10m"
     * @param timezone  时区,如 "Asia/Shanghai";传 "auto" 让服务端根据经纬度自动判断
     *
     * @return 完整响应模型 WeatherResponseDto
     *
     * 【异常语义】
     *   本方法在以下情况抛异常(由调用方负责 try/catch):
     *   · IOException           网络不通(无网、DNS 失败、连接超时)
     *   · HttpException         非 2xx 响应(404 / 500 等)
     *   · JsonSyntaxException   JSON 格式不符合数据类(字段缺失、类型不对)
     */
    @GET("v1/forecast")
    suspend fun getCurrentWeather(
        @Query("latitude")  lat: Double,
        @Query("longitude") lng: Double,
        @Query("current")   current: String,
        @Query("timezone")  timezone: String
    ): WeatherResponseDto
}