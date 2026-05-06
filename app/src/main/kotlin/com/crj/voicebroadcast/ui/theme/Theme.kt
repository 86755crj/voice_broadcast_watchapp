package com.crj.voicebroadcast.ui.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Wear OS 用 androidx.wear.compose.material（非 Material3），
 * Wear Material 用的是旧的 Colors 类，没有 dynamic theming。
 */
private val RetroColors = Colors(
    primary = WineRed,
    primaryVariant = PlumPurple,
    secondary = WarmOrange,
    secondaryVariant = CaramelOrange,
    background = ParchmentBeige,
    surface = ParchmentBeige,
    error = WineRed,
    onPrimary = ParchmentBeige,
    onSecondary = DarkCoffee,
    onBackground = DarkCoffee,
    onSurface = DarkCoffee,
    onSurfaceVariant = MutedCoffee,
    onError = ParchmentBeige
)

/**
 * 杂志感字体：用 FontFamily.Serif 让标题有衬线。
 * Wear OS 圆屏视野有限，正文必须放大一点（>14sp 才看得清）。
 */
private val RetroTypography = Typography(
    display1 = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 32.sp),
    display2 = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 26.sp),
    display3 = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 22.sp),
    title1 = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    title2 = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    title3 = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    body1 = TextStyle(fontFamily = FontFamily.Default, fontSize = 16.sp),
    body2 = TextStyle(fontFamily = FontFamily.Default, fontSize = 14.sp),
    button = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    caption1 = TextStyle(fontFamily = FontFamily.Default, fontSize = 13.sp),
    caption2 = TextStyle(fontFamily = FontFamily.Default, fontSize = 12.sp)
)

@Composable
fun VoiceBroadcastTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = RetroColors,
        typography = RetroTypography,
        content = content
    )
}
