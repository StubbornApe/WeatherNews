package com.example.weathernewsapp.di

import android.content.Context
import androidx.room.Room
import com.example.weathernewsapp.data.datastore.SettingsDataStore
import com.example.weathernewsapp.data.local.AppDatabase
import com.example.weathernewsapp.data.local.dao.WeatherDao
import com.example.weathernewsapp.data.remote.NewsApi
import com.example.weathernewsapp.data.remote.WeatherApi
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/**
 * ⭐ Day 18 核心:用测试 Module 替换生产 AppModule。
 * 生产里从真实网络/磁盘取数据,测试里全部换成假实现 + 内存 Room。
 */
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [AppModule::class]
)
@Module
object FakeTestModule {

    @Provides
    @Singleton
    fun provideWeatherApi(): WeatherApi = FakeWeatherApi

    @Provides
    @Singleton
    fun provideNewsApi(): NewsApi = FakeNewsApi

    // @TianApiKey 必须非空,绕过 NewsRepository 里 apiKey.isBlank() 的校验
    @Provides
    @Singleton
    @TianApiKey
    fun provideTianApiKey(): String = "test-key"

    @Provides
    @Singleton
    @NewsChannel
    fun provideNewsChannel(): String = "keji"

    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context
    ): SettingsDataStore = SettingsDataStore(context)

    // 内存 Room:测试用,不落盘,隔离且快
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    @Provides
    @Singleton
    fun provideWeatherDao(db: AppDatabase): WeatherDao = db.weatherDao()
}