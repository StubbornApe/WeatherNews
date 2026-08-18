package com.example.weathernewsapp.di

import com.example.weathernewsapp.data.remote.NewsApi
import com.example.weathernewsapp.data.remote.dto.NewsItemDto
import com.example.weathernewsapp.data.remote.dto.NewsResponseDto

/**
 * 假新闻接口:替代真实 Retrofit 生成的 NewsApi 实现。
 * 成功返回 3 条固定新闻;失败抛 RuntimeException 触发 NewsRepository 的 fallback。
 */
object FakeNewsApi : NewsApi {
    @Volatile
    var shouldFail: Boolean = false

    override suspend fun getNews(
        channel: String,
        key: String,
        num: Int,
    ): NewsResponseDto {
        if (shouldFail) throw RuntimeException("FakeNewsApi: network down")

        return NewsResponseDto(
            code = 200,
            msg = "success",
            newsList =
                listOf(
                    NewsItemDto(
                        id = "1",
                        ctime = "2 小时前",
                        title = "Android 15 正式发布",
                        description = "Google 推出 Android 15,新增预测性返回手势与照片权限。",
                        source = "新华社",
                        picUrl = null,
                        url = null,
                    ),
                    NewsItemDto(
                        id = "2",
                        ctime = "5 小时前",
                        title = "Kotlin 2.0 稳定版发布",
                        description = "K2 编译器带来大幅编译性能提升。",
                        source = "InfoQ",
                        picUrl = null,
                        url = null,
                    ),
                    NewsItemDto(
                        id = "3",
                        ctime = "1 天前",
                        title = "Jetpack Compose 里程碑",
                        description = "Compose 现已支持全部主流 View 组件。",
                        source = "掘金",
                        picUrl = null,
                        url = null,
                    ),
                ),
        )
    }
}
