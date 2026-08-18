package com.example.weathernewsapp.data.remote.dto

import com.google.gson.annotations.SerializedName

data class NewsResponseDto(
    @SerializedName("code") val code: Int,
    @SerializedName("msg") val msg: String? = null,
    @SerializedName("newslist") val newsList: List<NewsItemDto>? = null
)

data class NewsItemDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("ctime") val ctime: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("source") val source: String? = null,
    @SerializedName("picUrl") val picUrl: String? = null,
    @SerializedName("url") val url: String? = null
)