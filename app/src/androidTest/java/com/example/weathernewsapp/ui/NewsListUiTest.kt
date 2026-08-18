package com.example.weathernewsapp.ui

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.swipeDown
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.weathernewsapp.MainActivity
import com.example.weathernewsapp.R
import com.example.weathernewsapp.adapter.NewsAdapter
import com.example.weathernewsapp.di.FakeNewsApi
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class NewsListUiTest {
    // order:先让 Hilt 就绪,再启动 Activity
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Before
    fun setUp() {
        hiltRule.inject()
        // 每个用例前重置 fake 状态,保证用例相互独立
        FakeNewsApi.shouldFail = false
    }

    @Test
    fun launch_newsList_renderFirstItem() {
        // MainActivity 已被 ActivityScenarioRule 启动,startDestination 就是新闻页
        onView(withId(R.id.recyclerView))
            .check(matches(isDisplayed()))
        onView(withText("Android 15 正式发布"))
            .check(matches(isDisplayed()))
    }

    @Test
    fun clickFirstItem_openDetail_showTitle() {
        onView(withId(R.id.recyclerView))
            .perform(
                RecyclerViewActions.actionOnItemAtPosition<NewsAdapter.NewsViewHolder>(0, click()),
            )
        // 跳转到 NewsDetailActivity,详情标题 = 第一条新闻的 title
        onView(withId(R.id.tvDetailTitle))
            .check(matches(withText("Android 15 正式发布")))
    }

    @Test
    fun newsError_fallbackShowsListAndBanner() {
        // 新闻页是 startDestination,ActivityScenarioRule 在 setUp() 前就已用
        // shouldFail=false 完成加载。此处设 shouldFail=true 之后必须重新触发一次
        // 加载,Repository 才会走 catch fallback。利用下拉刷新(srlNews → refresh())
        // 重新发起请求 → fallback 到假数据 → 列表可见 + 顶部 banner 可见。
        FakeNewsApi.shouldFail = true
        onView(withId(R.id.srlNews)).perform(swipeDown())
        onView(withId(R.id.recyclerView))
            .check(matches(isDisplayed()))
        onView(withId(R.id.tvOfflineBanner))
            .check(matches(isDisplayed()))
    }
}
