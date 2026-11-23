package com.bloomcycle.app.data

import java.util.UUID

data class User(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val age: Int,
    val email: String,
    val role: String = "user"
)

data class PeriodRecord(
    val id: String = UUID.randomUUID().toString(),
    val startDate: String,
    val endDate: String? = null
)

data class DailyNote(
    val id: String = UUID.randomUUID().toString(),
    val date: String,
    val flow: String = "",
    val pain: String = "",
    val mood: String = "",
    val symptoms: List<String> = java.util.Collections.emptyList(),
    val text: String = ""
)

data class DiscussionAnswer(
    val id: String = UUID.randomUUID().toString(),
    val authorName: String,
    val authorEmail: String,
    val date: String,
    val text: String
)

data class DiscussionPost(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val category: String,
    val authorName: String,
    val authorEmail: String,
    val date: String,
    val text: String,
    val answers: List<DiscussionAnswer> = java.util.Collections.emptyList()
)

data class Article(
    val id: Int,
    val title: String,
    val tag: String,
    val body: List<String>
)

data class ArticleComment(
    val id: String = UUID.randomUUID().toString(),
    val articleId: Int,
    val name: String,
    val email: String,
    val role: String = "user",
    val date: String,
    val text: String
)

data class Report(
    val id: String = UUID.randomUUID().toString(),
    val targetType: String,
    val targetTitle: String,
    val targetId: String? = null,
    val category: String,
    val description: String,
    val date: String,
    val reporterEmail: String
)

data class AppSettings(
    var announcement: String = "",
    var communityEnabled: Boolean = true
)

data class CycleStats(
    val averageCycleLength: Int?,
    val averagePeriodDuration: Int?,
    val nextPeriodStart: String?,
    val isEstimated: Boolean,
    val cycleVariation: Int?
)
