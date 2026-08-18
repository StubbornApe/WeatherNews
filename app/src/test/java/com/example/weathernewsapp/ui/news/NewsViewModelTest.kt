package com.example.weathernewsapp.ui.news

import androidx.lifecycle.viewModelScope
import com.example.weathernewsapp.common.ErrorType
import com.example.weathernewsapp.common.NetworkResult
import com.example.weathernewsapp.data.repository.NewsRepository
import com.example.weathernewsapp.model.News
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel    // ⭐ CoroutineScope.cancel() 顶级扩展,vm.viewModelScope.cancel() 需要它
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NewsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: NewsRepository

    private val sampleNews = News(
        id = 1,
        title = "测试新闻标题",
        summary = "测试摘要",
        author = "测试作者",
        time = "刚刚",
        category = "科技"
    )
    private val sampleNewsList = listOf(sampleNews)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** 测试 1：刚构造时,uiState 应该是 Idle */
    @Test
    fun `initial state is Idle`() = runTest(testDispatcher.scheduler) {
        coEvery { repository.getNews() } returns NetworkResult.Success(sampleNewsList)
        val vm = NewsViewModel(repository)
        assertEquals(NewsUiState.Idle, vm.uiState.value)
        vm.viewModelScope.cancel()
    }

    /** 测试 2：init 触发后,uiState 应当从 Idle 变 Success(非空列表) */
    @Test
    fun `init triggers load and state becomes Success`() = runTest(testDispatcher.scheduler) {
        coEvery { repository.getNews() } returns NetworkResult.Success(sampleNewsList)
        val vm = NewsViewModel(repository)
        advanceUntilIdle()

        assertEquals(NewsUiState.Success(sampleNewsList), vm.uiState.value)
        coVerify(exactly = 1) { repository.getNews() }
        vm.viewModelScope.cancel()
    }

    /** 测试 3：业务空态(Success(emptyList()))→ uiState = Empty */
    @Test
    fun `empty response becomes Empty state`() = runTest(testDispatcher.scheduler) {
        coEvery { repository.getNews() } returns NetworkResult.Success(emptyList())
        val vm = NewsViewModel(repository)
        advanceUntilIdle()

        assertEquals(NewsUiState.Empty, vm.uiState.value)
        vm.viewModelScope.cancel()
    }

    /** 测试 4：网络 Error → uiState = Error 且 events 收到 Toast */
    @Test
    fun `error response becomes Error state and sends toast event`() = runTest(testDispatcher.scheduler) {
        coEvery { repository.getNews() } returns NetworkResult.Error(ErrorType.NoNetwork)
        val vm = NewsViewModel(repository)
        advanceUntilIdle()

        assertEquals(NewsUiState.Error(ErrorType.NoNetwork), vm.uiState.value)
        assertEquals(NewsEvent.ShowToast("网络不可用,请检查连接"), vm.events.first())
        vm.viewModelScope.cancel()
    }

    /** 测试 5：retry() 触发后,getNews 应当被调用第二次 */
    @Test
    fun `retry triggers reload`() = runTest(testDispatcher.scheduler) {
        coEvery { repository.getNews() } returns NetworkResult.Success(sampleNewsList)
        val vm = NewsViewModel(repository)
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.getNews() }

        vm.retry()
        advanceUntilIdle()
        coVerify(exactly = 2) { repository.getNews() }
        vm.viewModelScope.cancel()
    }
}
