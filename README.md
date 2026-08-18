# Weather News App

> 一款基于 MVVM + Jetpack 的天气 / 新闻 Android App,第 1 阶段学习项目(Day 1-19)。

## ✨ 特性

- 🌤️ 实时天气:基于 Open-Meteo API,支持城市切换、°C/°F 单位切换
- 📰 中文新闻:基于天行数据 API,下拉刷新、错误 fallback 到缓存
- 💾 离线可用:Room 本地缓存天气数据,断网仍可查看
- ⚙️ 个性化设置:DataStore 保存默认城市 / 温度单位 / 新闻频道
- 🧪 测试完备:3 层测试金字塔(ViewModel 单测 + Repository 单测 + Espresso UI 测试 + Jacoco 覆盖率)
- 🏗️ 架构清晰:MVVM + Repository + Hilt + Navigation

## 📸 截图 / GIF

<!-- TODO(Day 20): 插入主界面/新闻列表/天气详情/设置页/下拉刷新动图 -->

## 🏗️ 架构图

```
┌──────────────────────────────────────────────────────────────────┐
│ UI Layer (Fragment)                                              │
│ NewsFragment WeatherFragment SettingsFragment Mine               │
└───────────┬──────────────────┬──────────────────┬────────────────┘
            │ collect uiState   │ collect uiState
            ▼                   ▼                   ▼
┌──────────────────────────────────────────────────────────────────┐
│ ViewModel Layer (Hilt 注入 Repository)                           │
│ NewsViewModel WeatherViewModel                                   │
└───────────┬──────────────────┬──────────────────┬────────────────┘
            │ suspend / Flow    │ suspend / Flow
            ▼                   ▼                   ▼
┌──────────────────────────────────────────────────────────────────┐
│ Repository Layer (单一数据源)                                     │
│ NewsRepository WeatherRepository                                 │
└─────┬───────────────────────┬──────────────────────┬─────────────┘
      │                       │                      │
      ▼                       ▼                      ▼
┌──────────┐          ┌──────────────┐          ┌──────────────┐
│ TianAPI  │          │ Open-Meteo   │          │ Room (DAO)   │
│ (新闻)    │          │ (天气)        │          │ (本地缓存)    │
└──────────┘          └──────────────┘          └──────────────┘
                                                      ▲
                                                      │
┌──────────────────────────────────────┐
│ DataStore                            │
│ (用户设置)                            │
└──────────────────────────────────────┘
```

## 🧱 技术栈

| 层级     | 技术                                | 选型理由                          |
| -------- | ----------------------------------- | --------------------------------- |
| 异步     | Kotlin Coroutines + Flow            | 官方推荐、Structured Concurrency  |
| 网络     | Retrofit + OkHttp                   | 行业标准、拦截器机制成熟          |
| 解析     | Gson                                | 与 Retrofit 无缝集成              |
| 本地缓存 | Room                                | 编译期 SQL 校验、协程支持         |
| 偏好设置 | DataStore                           | 替代 SharedPreferences、支持 Flow |
| 依赖注入 | Hilt                                | Google 官方推荐、编译期校验       |
| 导航     | Jetpack Navigation                  | 单 Activity 多 Fragment 标配      |
| 图片     | Coil                                | 协程原生、API 简洁                |
| 测试     | JUnit 4 + MockK + Espresso + Jacoco | 单测 + UI 测 + 覆盖率             |
| 静态检查 | ktlint 12.1.0                       | 编译期拦截格式违规                |

## 📁 模块结构

```
app/src/main/java/com/example/weathernewsapp/
├── MainActivity.kt # 单 Activity 容器,持有 NavHost + BottomNav
├── NewsDetailActivity.kt # 新闻详情页
├── WeatherNewsApp.kt # Application 子类,@HiltAndroidApp 入口
├── adapter/ # RecyclerView Adapter
│ └── NewsAdapter.kt
├── common/ # 跨层共享工具
│ ├── NetworkResult.kt # 密封类:Loading / Success / Error
│ ├── RetryHelper.kt # 网络错误重试
│ └── LifecycleLogging*.kt # 生命周期日志基类
├── data/ # 数据层
│ ├── datastore/ # DataStore(用户设置)
│ ├── local/ # Room(数据库 + DAO + Entity)
│ ├── model/ # 领域模型(Weather / News / CityCoordinates)
│ ├── remote/ # Retrofit API + DTO
│ └── repository/ # Repository(数据源聚合)
├── di/ # Hilt 模块
│ ├── AppModule.kt # 网络 / 数据库 / DataStore 绑定
│ └── Qualifiers.kt # @WeatherRetrofit / @NewsRetrofit / @TianApiKey
└── ui/ # UI 层
├── news/ # 新闻 Fragment + ViewModel + UiState
├── weather/ # 天气 Fragment + ViewModel + UiState
├── settings/ # 设置 Fragment
└── mine/ # 我的 Fragment(占位)
```

## 🚀 运行方式

### 前置环境

- Android Studio Hedgehog (2023.1.1) 或更高
- JDK 11
- Android SDK 35
- 模拟器 API 28+ 或真机

### 步骤

1. **克隆仓库**

    ```bash
    git clone https://github.com/<your-account>/WeatherNewsApp.git
    cd WeatherNewsApp
    ```

2. **配置天行数据 API Key**(新闻功能需要)

    - 访问 [天行数据](https://www.tianapi.com/) 注册并申请「科技新闻」API Key
    - 在项目根目录新建 `local.properties`,添加:

        ```properties
        sdk.dir=/path/to/Android/Sdk
        TIANAPI_KEY=your_api_key_here
        ```

3. **用 Android Studio 打开项目**——File → Open → 选择 `WeatherNewsApp` 目录,等待 Gradle Sync 完成

4. **运行 App**——选择模拟器或真机,点击 Run ▶️

## 🧪 测试方式

### JVM 单元测试(毫秒级)

```powershell
# 跑全部 ViewModel + Repository 单测
.\gradlew.bat testDebugUnitTest

# 只跑某个类
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.weathernewsapp.ui.weather.WeatherViewModelTest"
```

### UI 测试(模拟器/真机,分钟级)

```powershell
# 跑全部 Espresso UI 测试
.\gradlew.bat :app:connectedDebugAndroidTest

# 只跑某个 UI 测试类
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.weathernewsapp.ui.NewsListUiTest
```

### 覆盖率报告(JaCoCo)

```powershell
.\gradlew.bat :app:createDebugUnitTestCoverageReport

# 报告位置:app/build/reports/coverage/test/debug/index.html
```

### 静态代码检查(ktlint)

```powershell
# 检查代码规范
.\gradlew.bat :app:ktlintCheck

# 一键自动修复
.\gradlew.bat :app:ktlintFormat
```

## 📝 学习笔记

完整学习笔记见 [`docs/`](docs/) 目录,共 19 篇(Day 01 - Day 19)。

## 🗺️ Roadmap

### ✅ 第 1 阶段(已完成,Day 1-19)

- [x] MVVM + Hilt + Navigation 架构搭建
- [x] Retrofit + Room + DataStore 数据层
- [x] 三层测试金字塔(ViewModel / Repository / UI)
- [x] Jacoco 覆盖率报告
- [x] Conventional Commits + ktlint

### 📅 第 2 阶段(计划)

- [ ] 车载 HMI 自定义 View
- [ ] 仪表盘 UI 与动画
- [ ] 空调面板 UI

## 📄 License

MIT License — 详见 [LICENSE](LICENSE) 文件

