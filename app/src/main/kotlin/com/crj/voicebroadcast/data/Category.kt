package com.crj.voicebroadcast.data

/**
 * 一个口播类别。Home 屏 hardcode 两个：晨练简报（可点）+ 工作播报（placeholder）
 */
data class Category(
    val id: String,
    val name: String,
    val feedUrl: String?,   // null 表示 placeholder
    val enabled: Boolean = feedUrl != null
)

object Categories {
    val MORNING = Category(
        id = "morning-briefing",
        name = "晨练简报",
        feedUrl = "http://47.115.56.9/podcast/a1heeTdZuYAVzxGYra5qUrT4wcIlSUU5/feed.xml"
    )

    val WORK_PLACEHOLDER = Category(
        id = "work-briefing",
        name = "工作播报",
        feedUrl = null,
        enabled = false
    )

    val ALL = listOf(MORNING, WORK_PLACEHOLDER)

    fun byId(id: String): Category? = ALL.firstOrNull { it.id == id }
}
