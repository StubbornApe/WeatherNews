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
                        }
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
    fun provideWeatherApi(@WeatherRetrofit retrofit: Retrofit): WeatherApi =
        retrofit.create(WeatherApi::class.java)

    @Provides
    @Singleton
    fun provideNewsApi(@NewsRetrofit retrofit: Retrofit): NewsApi =
        retrofit.create(NewsApi::class.java)

    // ── 本地存储层 ─────────────────────────────────────────

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        AppDatabase.getInstance(context)

    @Provides
    @Singleton
    fun provideWeatherDao(db: AppDatabase): WeatherDao = db.weatherDao()

    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context
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
