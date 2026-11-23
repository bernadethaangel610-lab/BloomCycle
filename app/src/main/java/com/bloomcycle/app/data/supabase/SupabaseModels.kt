package com.bloomcycle.app.data.supabase

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SupabaseUser(
    val id: String,
    val name: String,
    val age: Int,
    val email: String,
    val role: String = "user"
)

@Serializable
data class SupabasePeriod(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String? = null
)

@Serializable
data class SupabaseNote(
    val id: String,
    @SerialName("user_id") val userId: String,
    val date: String,
    val flow: String = "",
    val pain: String = "",
    val mood: String = "",
    val symptoms: List<String> = emptyList(),
    val text: String = ""
)

@Serializable
data class SupabaseDiscussion(
    val id: String,
    @SerialName("user_id") val userId: String? = null,
    val title: String,
    val category: String,
    @SerialName("author_name") val authorName: String,
    @SerialName("author_email") val authorEmail: String,
    val date: String,
    val text: String
)

@Serializable
data class SupabaseAnswer(
    val id: String,
    @SerialName("discussion_id") val discussionId: String,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("author_name") val authorName: String,
    @SerialName("author_email") val authorEmail: String,
    val date: String,
    val text: String
)

@Serializable
data class SupabaseComment(
    val id: String,
    @SerialName("article_id") val articleId: Int,
    @SerialName("user_id") val userId: String? = null,
    val name: String,
    val email: String,
    val role: String = "user",
    val date: String,
    val text: String
)

@Serializable
data class SupabaseReport(
    val id: String,
    @SerialName("reporter_email") val reporterEmail: String,
    @SerialName("target_type") val targetType: String,
    @SerialName("target_title") val targetTitle: String,
    @SerialName("target_id") val targetId: String? = null,
    val category: String,
    val description: String,
    @SerialName("created_at") val createdAt: String? = null
)
