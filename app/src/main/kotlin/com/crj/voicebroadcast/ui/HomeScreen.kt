package com.crj.voicebroadcast.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.crj.voicebroadcast.data.Categories
import com.crj.voicebroadcast.data.Category
import com.crj.voicebroadcast.ui.theme.CaramelOrange
import com.crj.voicebroadcast.ui.theme.DarkCoffee
import com.crj.voicebroadcast.ui.theme.MutedCoffee
import com.crj.voicebroadcast.ui.theme.ParchmentBeige
import com.crj.voicebroadcast.ui.theme.WineRed

/**
 * Home 屏：2x2 网格（其中两格当下没用，留给未来扩展），
 * 每张卡片米白底 + 酒红边框 + 衬线大字标题。
 */
@Composable
fun HomeScreen(onCategoryClick: (Category) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ParchmentBeige),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 顶部刊头
            Text(
                text = "口播 · 第壹刊",
                color = WineRed,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "VOICE BROADCAST",
                color = CaramelOrange,
                fontFamily = FontFamily.Serif,
                fontSize = 9.sp,
                letterSpacing = 2.sp
            )

            Spacer(Modifier.height(10.dp))

            // 2x2 网格
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CategoryCard(
                    category = Categories.MORNING,
                    modifier = Modifier.weight(1f),
                    onClick = { onCategoryClick(Categories.MORNING) }
                )
                CategoryCard(
                    category = Categories.WORK_PLACEHOLDER,
                    modifier = Modifier.weight(1f),
                    onClick = {} // disabled
                )
            }
        }
    }
}

@Composable
private fun CategoryCard(
    category: Category,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val borderColor = if (category.enabled) WineRed else MutedCoffee
    val textColor = if (category.enabled) DarkCoffee else MutedCoffee

    Box(
        modifier = modifier
            .height(80.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(ParchmentBeige)
            .border(
                width = if (category.enabled) 2.dp else 1.dp,
                brush = SolidColor(borderColor),
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(enabled = category.enabled) { onClick() }
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = category.name,
                color = textColor,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            if (!category.enabled) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "即将上线",
                    color = MutedCoffee,
                    fontFamily = FontFamily.Default,
                    fontSize = 9.sp
                )
            }
        }
    }
}
