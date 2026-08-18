package com.example.weathernewsapp.ui

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.weathernewsapp.MainActivity
import com.example.weathernewsapp.R
import com.example.weathernewsapp.di.FakeNewsApi
import com.example.weathernewsapp.di.FakeWeatherApi
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class BottomNavUiTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Before
    fun setUp() {
        hiltRule.inject()
        FakeNewsApi.shouldFail = false
        FakeWeatherApi.shouldFail = false
    }

    @Test
    fun clickWeatherTab_showsWeather() {
        // 底部导航的 menu item id 就是 nav_graph 的 destination id(weatherFragment)
        onView(withId(R.id.weatherFragment))
            .perform(click())
        // 天气页内容(成功态):城市名 + 温度
        onView(withId(R.id.tvCityName))
            .check(matches(isDisplayed()))
            .check(matches(withText("北京")))
        onView(withId(R.id.tvTemperature))
            .check(matches(withText("28.5°C")))
    }

    @Test
    fun clickWeatherThenNews_switchBack() {
        onView(withId(R.id.weatherFragment)).perform(click())
        onView(withId(R.id.newsFragment)).perform(click())
        // 切回新闻页,列表重新可见
        onView(withId(R.id.recyclerView))
            .check(matches(isDisplayed()))
    }
}
