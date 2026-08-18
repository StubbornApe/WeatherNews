package com.example.weathernewsapp.common

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

/** 生命周期学习专用基类:统一 TAG,方便 Logcat 过滤。 */
open class LifecycleLoggingActivity : AppCompatActivity() {
    protected open val tag: String = this::class.java.simpleName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(LC_TAG, "$tag -> onCreate")
    }

    override fun onStart() {
        super.onStart()
        Log.d(LC_TAG, "$tag -> onStart")
    }

    override fun onResume() {
        super.onResume()
        Log.d(LC_TAG, "$tag -> onResume")
    }

    override fun onPause() {
        super.onPause()
        Log.d(LC_TAG, "$tag -> onPause")
    }

    override fun onStop() {
        super.onStop()
        Log.d(LC_TAG, "$tag -> onStop")
    }

    override fun onRestart() {
        super.onRestart()
        Log.d(LC_TAG, "$tag -> onRestart")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(LC_TAG, "$tag -> onDestroy")
    }

    companion object {
        const val LC_TAG = "LC"
    }
}
