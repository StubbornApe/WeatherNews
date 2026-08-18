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
import com.example.weathernewsapp.di.FakeWeatherApi
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class WeatherUiTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Before
    fun setUp() {
        hiltRule.inject()
        FakeWeatherApi.shouldFail = false
    }

    @Test
    fun weatherSuccess_showsCity_andTemperature() {
        onView(withId(R.id.weatherFragment)).perform(click())

        onView(withId(R.id.tvCityName))
            .check(matches(isDisplayed()))
            .check(matches(withText("北京")))
        onView(withId(R.id.tvTemperature))
            .check(matches(withText("28.5°C")))
        onView(withId(R.id.tvWeatherDesc))
            .check(matches(withText("多云")))
    }

    @Test
    fun weatherError_showsError_andRetryButton() {
        // 先让 fake 失败,再切到天气页(天气 Fragment 是懒加载,切过去才创建)
        FakeWeatherApi.shouldFail = true
        onView(withId(R.id.weatherFragment)).perform(click())

        // RuntimeException → ErrorType.Unknown → userMessage = "FakeWeatherApi: network down"
        onView(withId(R.id.tvError))
            .check(matches(withText("FakeWeatherApi: network down")))
        onView(withId(R.id.btnRetry))
            .check(matches(isDisplayed()))
    }
}