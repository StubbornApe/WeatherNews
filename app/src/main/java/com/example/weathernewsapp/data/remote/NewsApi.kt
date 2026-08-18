package com.example.weathernewsapp.data.remote

import com.example.weathernewsapp.data.remote.dto.NewsResponseDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface NewsApi {
    @GET("{channel}/index")
    suspend fun getNews(
        @Path("channel") channel: String = "keji",
        @Query("key") key: String,
        @Query("num") num: Int = 20,
    ): NewsResponseDto
}
