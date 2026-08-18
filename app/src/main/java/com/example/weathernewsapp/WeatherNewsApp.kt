package com.example.weathernewsapp

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application 入口,Hilt 依赖注入的根容器。
 *
 * ## 作用
 * - `@HiltAndroidApp` 触发 Hilt 代码生成,在编译期生成 `Hilt_WeatherNewsApp` 基类
 * - 所有 `@AndroidEntryPoint` 注解的 Activity / Fragment 都通过这个基类获取依赖
 * - 替代旧的 `DaggerApplication` + `HasAndroidInjector` 样板代码
 *
 * ## 启动流程
 * 1. 系统创建本 Application 实例
 * 2. Hilt 初始化 SingletonComponent
 * 3. AppModule 里的 @Provides 方法被调用,生成单例绑定(OkHttp / Retrofit / Room / DataStore)
 * 4. MainActivity 启动时,HiltAndroidEntryPoint 解析依赖,本类不需要任何额外代码
 *
 * ## 何时需要修改本类
 * - 需要在 App 启动时初始化第三方 SDK(比如 Timber / Firebase / Bugly)时
 *   可在 `onCreate()` 里添加,但**不要**做耗时操作(会拖慢冷启动)
 * - 需要注册全局生命周期回调时,实现 `registerActivityLifecycleCallbacks()`
 *
 * @see AppModule 提供所有 Singleton 绑定
 * @see MainActivity 单 Activity 容器入口
 */
@HiltAndroidApp
class WeatherNewsApp : Application()
