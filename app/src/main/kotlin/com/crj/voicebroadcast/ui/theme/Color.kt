package com.crj.voicebroadcast.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 复古杂志风配色 —— CRJ 已批准
 *
 * 设计思路：
 * - ParchmentBeige 米白做背景，避免 AMOLED 烧屏可切 DeepBrown
 * - WineRed/PlumPurple 做主交互色（按钮 / 顶栏）
 * - WarmOrange 做"激活态"高亮（进度环、当前播放）
 * - DarkCoffee 做正文，对比度好但不刺眼
 */
val ParchmentBeige = Color(0xFFF5F1E8)   // 米白背景
val DeepBrown = Color(0xFF2A1F1A)        // 深棕（暗模式背景）
val WineRed = Color(0xFF8B2635)          // 酒红（标题/主按钮）
val PlumPurple = Color(0xFF6B2C5C)       // 深紫红（备用主色）
val MossGreen = Color(0xFF3D5A3D)        // 墨绿（辅助）
val CaramelOrange = Color(0xFFC77B3C)    // 焦糖橙（辅助/图标）
val DarkCoffee = Color(0xFF3D2817)       // 深咖（正文）
val WarmOrange = Color(0xFFE8833A)       // 暖橙（数据高亮 / 进度环 active）
val MutedCoffee = Color(0xFF8B7355)      // 浅咖（已听 / 次要文字）
