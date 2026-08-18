package com.example.weathernewsapp.data.repository

import com.example.weathernewsapp.common.ErrorType
import com.example.weathernewsapp.common.NetworkResult
import com.example.weathernewsapp.data.remote.NewsApi
import com.example.weathernewsapp.data.remote.dto.NewsItemDto
import com.example.weathernewsapp.data.remote.dto.NewsResponseDto
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.UnknownHostException

/**
 * NewsRepository 单测
 *
 * 测试范围:API key 空 / 业务码 200 + 有数据 / 业务码 200 + 空列表 / 业务码非 200 / 网络异常 fallback
 * Mock 边界:NewsApi(Retrofit 接口) + apiKey + channel
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NewsRepositoryTest {

    private lateinit var api: NewsApi

    // 业务码 200 + 两条数据的响应
    private val sampleResponseWithData = NewsResponseDto(
        code = 200,
        msg = "success",
        newsList = listOf(
            NewsItemDto(
                id = "1",
                title = "Android 15 发布",
                description = "新增预测性返回",
                source = "新华社",
                ctime = "2 小时前",
                picUrl = "https://example.com/img1.jpg",
                url = "https://example.com/news/1"
            ),
            NewsItemDto(
                id = "2",
                title = "Kotlin 2.0 稳定",
                description = "K2 编译器 GA",
                source = "InfoQ",
                ctime = "5 小时前",
                picUrl = null,
                url = null
            )
        )
    )

    // 业务码 200 但 newsList 为 null(当作空列表)
    private val emptyResponse = NewsResponseDto(
        code = 200,
        msg = "success",
        newsList = null
    )

    @Before
    fun setUp() {
        api = mockk()
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  1. API key 为空 → 直接返回 Error(Client(401)),不调网络
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `blank api key - returns Client 401 and skips network`() = runTest {
        // Given: apiKey 是空字符串
        val repository = NewsRepository(api, apiKey = "", channel = "keji")

        // When
        val result = repository.getNews()

        // Then: 返回 Client(401);即使 api 没桩,也不该被调用(校验在网络之前)
        assertTrue(result is NetworkResult.Error)
        val error = result as NetworkResult.Error
        assertEquals(ErrorType.Client(401), error.type)
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  2. 业务码 200 + 有数据 → Success(排序后的列表)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `business code 200 with data - returns Success list`() = runTest {
        // Given
        val repository = NewsRepository(api, apiKey = "valid-key", channel = "keji")
        coEvery { api.getNews(any(), any(), any()) } returns sampleResponseWithData

        // When
        val result = repository.getNews()

        // Then: 返回 Success,两条都被正确映射
        assertTrue(result is NetworkResult.Success)
        val list = (result as NetworkResult.Success).data
        assertEquals(2, list.size)
        assertEquals("Android 15 发布", list[0].title)
        assertEquals("新华社", list[0].author)
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  3. 业务码 200 + 空列表 → Success(emptyList())
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `business code 200 with empty list - returns Success empty`() = runTest {
        // Given
        val repository = NewsRepository(api, apiKey = "valid-key", channel = "keji")
        coEvery { api.getNews(any(), any(), any()) } returns emptyResponse

        // When
        val result = repository.getNews()

        // Then: 返回 Success(emptyList),不是 Error(业务空 ≠ 网络错)
        assertTrue(result is NetworkResult.Success)
        val list = (result as NetworkResult.Success).data
        assertTrue(list.isEmpty())
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  4. 业务码非 200 → Error(Client(code))
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `business code non 200 - returns Error Client`() = runTest {
        // Given: 业务码 500
        val repository = NewsRepository(api, apiKey = "valid-key", channel = "keji")
        coEvery { api.getNews(any(), any(), any()) } returns NewsResponseDto(code = 500, msg = "error")

        // When
        val result = repository.getNews()

        // Then: Error(type=Client(500))
        assertTrue(result is NetworkResult.Error)
        val error = result as NetworkResult.Error
        assertEquals(ErrorType.Client(500), error.type)
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  5. 网络异常 → fallback 到假数据,标记 isFromFallback=true
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `network error - falls back to fake data with isFromFallback`() = runTest {
        // Given: 网络失败
        val repository = NewsRepository(api, apiKey = "valid-key", channel = "keji")
        coEvery { api.getNews(any(), any(), any()) } throws UnknownHostException("dns")

        // When
        val result = repository.getNews()

        // Then: 返回 Success(fake data),且每条都标记 isFromFallback=true
        assertTrue(result is NetworkResult.Success)
        val list = (result as NetworkResult.Success).data
        assertTrue(list.isNotEmpty())
        assertTrue(list.all { it.isFromFallback })
    }
}