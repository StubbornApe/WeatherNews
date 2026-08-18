package com.example.weathernewsapp

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * 自定义测试运行器，用于在 Hilt 测试中使用 HiltTestApplication。
 * 这样可以避免在 debug/AndroidManifest.xml 中硬编码导致正常运行崩溃。
 */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, name: String?, context: Context?): Application {
        return super.newApplication(cl, HiltTestApplication::class.java.name, context)
    }
}
