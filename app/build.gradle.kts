import java.util.Properties
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-parcelize")
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt) // ⭐ Day 14 新增
    alias(libs.plugins.ktlint) // ⭐ Day 19 新增
}

// 读取 local.properties（用于注入 TianAPI Key）
val localProps =
    Properties().apply {
        val f = rootProject.file("local.properties")
        if (f.exists()) load(f.inputStream())
    }

android {
    namespace = "com.example.weathernewsapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.weathernewsapp"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "com.example.weathernewsapp.HiltTestRunner"

        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }

        // ⭐ Day 13 新增：把 TianAPI Key 注入 BuildConfig
        buildConfigField(
            "String",
            "TIANAPI_KEY",
            "\"${localProps.getProperty("TIANAPI_KEY", "")}\"",
        )
    }

    buildFeatures {
        buildConfig = true // ← 显式启用 BuildConfig 生成
        viewBinding = true // 顺便把 Day05 用过的也保留(如果已有)
    }

    // ⭐ Day 17 新增:JaCoCo 覆盖率配置
    //   AGP 8.0+ 已内置 JaCoCo 集成,只需指定版本即可
    //   跑 ./gradlew.bat :app:createDebugUnitTestCoverageReport 生成 HTML 报告
    testCoverage {
        jacocoVersion = libs.versions.jacoco.get()
    }

    buildTypes {
        debug {
            // ⭐ Day 17 修复:真正让 debug 变体单元测试开启代码覆盖率(仅 testCoverage{ jacocoVersion } 不会生成覆盖率任务)
            enableUnitTestCoverage = true
            // ⭐ Day 20 新增:开启设备端(androidTest / connectedDebugAndroidTest)代码覆盖率
            //   会让 AGP 注册 createDebugAndroidTestCoverageReport 任务,单独产出 UI 测试的报告中
            enableAndroidTestCoverage = true
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    // ⭐ Day 16 新增:JVM 单测里未 mock 的 Android stub 方法(如 Log.d)返回默认值而非抛异常
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

// ⭐ Day 19 新增:ktlint 配置
ktlint {
    // 本项目包名没下划线,关闭这条规则作为教学示范
    disabledRules.set(setOf("package-name"))

    // Android 项目的 R 类 / BuildConfig 不需要走 ktlint
    filter {
        exclude { element -> element.name.contains("BuildConfig") }
        exclude { element -> element.name.contains("/R.kt") }
        // 允许 KSP 生成的代码(如 Hilt_*, *_Factory)跳过
        exclude { element -> element.name.contains("/generated/") }
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    // ⭐ Day 18 新增:UI 测试(Espresso + Hilt 假数据注入)
    //   espresso-core 已在 Day 01 引入,这里补 contrib(RecyclerViewActions)
    androidTestImplementation(libs.androidx.espresso.contrib)
    androidTestImplementation(libs.androidx.test.core) // ActivityScenario
    androidTestImplementation(libs.androidx.test.runner) // 测试运行器
    androidTestImplementation(libs.androidx.test.rules) // 测试规则
    androidTestImplementation(libs.hilt.android.testing) // @HiltAndroidTest / HiltAndroidRule
    kspAndroidTest(libs.hilt.compiler) // Hilt 测试注解处理(必须)
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.coroutines.android)

    // Room 本地缓存
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)

    // ⭐ Day 11 新增:ViewModel + StateFlow + by viewModels()
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.fragment.ktx)

    // ⭐ Day 13 挑战 3 新增:Coil 图片加载
    implementation(libs.coil)

    // ⭐ Day 14 新增：Hilt 依赖注入
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // ⭐ Day 15 挑战 4 新增:下拉刷新
    implementation(libs.androidx.swiperefreshlayout)

    // ⭐ Day 16 新增:单测框架
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}

// ⭐ Day 20:合并 "debug 单测 + connected(UI)测试" 覆盖率为一份聚合报告
//   用法:.\gradlew.bat :app:jacocoMergedDebugReport
//   输出:app/build/reports/coverage/jacocoMergedDebug/index.html
//   说明:AGP 内置 createDebugCoverageReport 只分别出单测/UI 两份报告,并不合并;
//        这里自定义一个 JacocoReport,把 unit 的 .exec 和 androidTest 的 .ec 一起统计。
tasks.register(
    "jacocoMergedDebugReport",
    org.gradle.testing.jacoco.tasks.JacocoReport::class.java,
) {
    group = "verification"
    description = "合并 debug 单测 + connected(UI)测试的 JaCoCo 覆盖率为一份报告"

    // 依赖两个数据源,保证数据最新:
    //   单测 exec 由 testDebugUnitTest 生成,设备端 .ec 由 createDebugAndroidTestCoverageReport 生成
    dependsOn("testDebugUnitTest", "createDebugAndroidTestCoverageReport")

    // 1) 执行数据:单测 exec + 设备端 coverage.ec(两者会由 JaCoCo 合并统计)
    val unitExec = file("build/outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec")
    val androidEc = fileTree("build/outputs/code_coverage/debugAndroidTest/connected") {
        include("**/*.ec")
    }
    executionData(files(unitExec, androidEc))

    // 2) class 产物(要统计的字节码)
    classDirectories.setFrom(
        files("build/tmp/kotlin-classes/debug"),
        files("build/intermediates/javac/debug/compileDebugJavaWithJavac/classes"),
    )

    // 3) 源码路径
    sourceDirectories.setFrom(files("src/main/java"))

    // 4) 报告输出到独立目录,避免和 AGP 内置两份混淆
    reports {
        html.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("reports/coverage/jacocoMergedDebug"))
        xml.required.set(false)
        csv.required.set(false)
    }
}
