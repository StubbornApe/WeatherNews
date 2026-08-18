package com.example.weathernewsapp.data.repository

import android.util.Log
import com.example.weathernewsapp.common.ErrorType
import com.example.weathernewsapp.common.NetworkResult
import com.example.weathernewsapp.common.retryOnNetworkError
import com.example.weathernewsapp.data.FakeNewsData
import com.example.weathernewsapp.data.remote.NewsApi
import com.example.weathernewsapp.data.remote.dto.NewsItemDto
import com.example.weathernewsapp.di.NewsChannel
import com.example.weathernewsapp.di.TianApiKey
import com.example.weathernewsapp.model.News
import javax.inject.Inject

class NewsRepository @Inject constructor(
    private val api: NewsApi,
    @TianApiKey private val apiKey: String,
    @NewsChannel private val channel: String
) {

    suspend fun getNews(): NetworkResult<List<News>> {
        if (apiKey.isBlank()) {
            return NetworkResult.Error(
                ErrorType.Client(401),
                IllegalStateException("TIANAPI_KEY 未配置，请在 local.properties 中设置")
            )
        }

        return try {
            val response = retryOnNetworkError {
                api.getNews(channel = channel, key = apiKey, num = 20)
            }

            if (response.code != 200) {
                // 业务错误(API 业务码非 200):返回 Error,UI 显示错误页
                Log.w(TAG, "TianAPI business error: code=${response.code} msg=${response.msg}")
                NetworkResult.Error(
                    ErrorType.Client(response.code),
                    RuntimeException(response.msg ?: "业务错误 code=${response.code}")
                )
            } else {
                val list = response.newsList
                    ?.mapNotNull { it.toDomainOrNull() }
                    ?.sortedWith(
                        compareByDescending<News> { it.isTop }
                            .thenByDescending { it.readCount }
                    )
                    ?: emptyList()

                // ⭐ Day 15 改造:服务器成功响应但列表为空,返回 Success(emptyList())
                //   不要再用 Error(Parse) 把"业务空"当成"网络错"
                //   ViewModel 看到 Success(emptyList) 会翻译成 NewsUiState.Empty
                Log.d(TAG, "getNews: server returned ${list.size} items")
                NetworkResult.Success(list)   // 可能是 emptyList()
            }
        } catch (e: Exception) {
            // 真正的网络异常(IOException/HttpException/JsonSyntaxException 等)
            //   ⭐ Day 15 改造:仿照 WeatherRepository.isFromCache 范式
            //   fallback 到 fake data,但每条 News 用 copy(isFromFallback = true) 标记
            //   这样 UI 知道当前数据是"假"的,可以显示 banner 提示用户
            //   用户既能看内容(开发体验保留),又知道是缓存(知情权)
            Log.e(TAG, "getNews failed, fallback to fake data", e)
            NetworkResult.Success(
                FakeNewsData.newsList
                    .map { it.copy(isFromFallback = true) }   // ⭐ 标记为 fallback 数据
                    .sortedWith(
                        compareByDescending<News> { it.isTop }
                            .thenByDescending { it.readCount }
                    )
            )
        }
    }

    private fun NewsItemDto.toDomainOrNull(): News? {
        val safeTitle = title?.takeIf { it.isNotBlank() } ?: return null
        return News(
            id = id?.hashCode() ?: safeTitle.hashCode(),
            title = safeTitle,
            summary = description.orEmpty(),
            author = source.orEmpty().ifBlank { "天行数据" },
            time = ctime.orEmpty().ifBlank { "刚刚" },
            imageUrl = picUrl,
            url = url,
            category = "科技",
            isTop = false,
            readCount = 0
        )
    }

    companion object {
        private const val TAG = "NewsRepository"
    }
}