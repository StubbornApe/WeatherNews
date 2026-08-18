package com.example.weathernewsapp.di

import android.content.Context
import com.example.weathernewsapp.BuildConfig
import com.example.weathernewsapp.data.datastore.SettingsDataStore
import com.example.weathernewsapp.data.local.AppDatabase
import com.example.weathernewsapp.data.local.dao.WeatherDao
import com.example.weathernewsapp.data.remote.NewsApi
import com.example.weathernewsapp.data.remote.WeatherApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Hilt 主模块,提供所有 Singleton 绑定的"装配清单"。
 *
 * ## 本模块提供的绑定
 *
 * ### 网络层
 * - [OkHttpClient] — 配置了 15s 超时 + Debug 模式下的 HttpLoggingInterceptor
 * - [WeatherApi] / [NewsApi] — 通过 `@WeatherRetrofit` / `@NewsRetrofit` 区分不同 baseUrl
 *
 * ### 本地存储层
 * - [AppDatabase] — Room 单例
 * - [WeatherDao] — 从 AppDatabase 取
 * - [SettingsDataStore] — DataStore 包装,暴露 Flow
 *
 * ### 配置项(用 @Qualifier 区分同名类型)
 * - `@TianApiKey String` — 从 BuildConfig.TIANAPI_KEY 注入 API Key
 * - `@NewsChannel String` — 新闻频道默认值("keji" 科技)
 *
 * ## 何时需要修改本模块
 * - 新增网络库绑定(比如再加一个图片上传 API) → 新增 @Provides 方法
 * - 改 baseUrl → 改 WEATHER_BASE_URL / NEWS_BASE_URL 常量
 * - 改默认新闻频道 → 改 DEFAULT_NEWS_CHANNEL 常量
 *
 * ## 为什么用 @Qualifier 而不是 @Named
 * `@Qualifier` 是类型安全的自定义注解(见 [Qualifiers.kt]),
 * 编译期就能发现拼写错误;`@Named("xxx")` 是字符串,改名后 IDE 不会报错。
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    private const val WEATHER_BASE_URL = "https://api.open-meteo.com/"
    private const val NEWS_BASE_URL = "https://api.tianapi.com/"

    // Day 13 默认值:科技频道。未来 SettingsFragment 改这里(也可以保留为常量,改成 DataStore 暴露 Flow)
    private const val DEFAULT_NEWS_CHANNEL = "keji"

    // ── 网络层 ─────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BODY
                        },
                    )
                }
            }
            .build()

    @Provides
    @Singleton
    @WeatherRetrofit
    fun provideWeatherRetrofit(client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(WEATHER_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    @NewsRetrofit
    fun provideNewsRetrofit(client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(NEWS_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideWeatherApi(
        @WeatherRetrofit retrofit: Retrofit,
    ): WeatherApi = retrofit.create(WeatherApi::class.java)

    @Provides
    @Singleton
    fun provideNewsApi(
        @NewsRetrofit retrofit: Retrofit,
    ): NewsApi = retrofit.create(NewsApi::class.java)

    // ── 本地存储层 ─────────────────────────────────────────

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase = AppDatabase.getInstance(context)

    @Provides
    @Singleton
    fun provideWeatherDao(db: AppDatabase): WeatherDao = db.weatherDao()

    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context,
    ): SettingsDataStore = SettingsDataStore(context)

    // ── 配置项(用 @Qualifier 区分两个 String)──────────────

    @Provides
    @Singleton
    @TianApiKey
    fun provideTianApiKey(): String = BuildConfig.TIANAPI_KEY

    @Provides
    @Singleton
    @NewsChannel
    fun provideNewsChannel(): String = DEFAULT_NEWS_CHANNEL
}
