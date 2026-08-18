package com.example.weathernewsapp.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * News —— 新闻数据模型
 *
 * @property id         新闻唯一 ID
 * @property title      标题
 * @property summary    摘要
 * @property author     作者/来源
 * @property time       发布时间描述（"2 小时前"）
 * @property imageUrl   封面图 URL（可空，后续接入 Retrofit 后使用）
 * @property category   分类（如 "科技" / "汽车" / "AI"）—— 用于分组、过滤
 * @property isTop      是否置顶（true = 顶部显示）
 * @property readCount  阅读量 —— 用于热度排序
 */
/**
 * @Parcelize 由 kotlin-parcelize 插件在编译时自动生成:
 *   1) writeToParcel(dest, flags)
 *   2) CREATOR(Parcelable.Creator<News>) 的静态字段
 * 相当于原来要写 30+ 行样板,现在一行注解搞定。
 *
 * 前提:主构造参数里所有字段都是 Parcelable 支持的类型:
 *   - 基本类型 / String / 已实现 Parcelable 的类 / List/Map(元素也需可 parcel)
 *   - null 也 OK,自动处理
 */
@Parcelize
data class News(
    val id: Int,
    val title: String,
    val summary: String,
    val author: String,
    val time: String,
    val imageUrl: String? = null,
    val url: String? = null,
    val category: String = "综合",
    val isTop: Boolean = false,
    val readCount: Int = 0,
    // ⭐ Day 15 新增:仿照 Weather.isFromCache 范式
    //   默认 false,NewsRepository 在 catch 分支用 copy(isFromFallback = true) 标记
    val isFromFallback: Boolean = false
): Parcelable