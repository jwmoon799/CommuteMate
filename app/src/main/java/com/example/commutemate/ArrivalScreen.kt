package com.example.commutemate

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ArrivalScreen(
    englishStarted: Boolean,
    activitySummary: String,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize().padding(24.dp)
    ) {
        val wide = maxWidth >= 720.dp
        val availableHeight = maxHeight

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .heightIn(min = availableHeight),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "커뮤트 메이트",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "샘플 출근 · 도착 요약",
                    color = Color(0xFF9A9FA5),
                    fontSize = 24.sp
                )
            }

            if (wide) {
                Row(
                    modifier = Modifier.padding(vertical = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(40.dp)
                ) {
                    ArrivalActivities(
                        activitySummary = activitySummary,
                        modifier = Modifier.weight(1f)
                    )
                    ArrivalExpressions(
                        englishStarted = englishStarted,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Column(
                    modifier = Modifier.padding(vertical = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    ArrivalActivities(activitySummary)
                    ArrivalExpressions(englishStarted)
                }
            }

            Text(
                text = "샘플 다음 일정 · ${sampleSchedule.first().time} ${sampleSchedule.first().title}",
                color = Color(0xFF9A9FA5),
                fontSize = 28.sp,
                lineHeight = 40.sp
            )
        }
    }
}

@Composable
private fun ArrivalActivities(
    activitySummary: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = "도착했어요",
            fontSize = 52.sp,
            lineHeight = 64.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "이번 코스 진행 결과",
            color = Color(0xFF9A9FA5),
            fontSize = 24.sp
        )
        Text(
            text = activitySummary,
            fontSize = 30.sp,
            lineHeight = 42.sp
        )
    }
}

@Composable
private fun ArrivalExpressions(
    englishStarted: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = if (englishStarted) {
                "오늘의 영어 표현 2개"
            } else {
                "다음 출근길에 연습할 표현"
            },
            color = Color(0xFF9A9FA5),
            fontSize = 28.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "I usually drive to work.\n저는 보통 운전해서 출근해요.",
            fontSize = 28.sp,
            lineHeight = 40.sp
        )
        Text(
            text = "It takes about twenty minutes.\n약 20분 걸려요.",
            fontSize = 28.sp,
            lineHeight = 40.sp
        )
    }
}