package com.example.commutemate

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BriefingScreen(
    courseMinutes: Int,
    isPlaying: Boolean,
    onTogglePlayback: () -> Unit,
    modifier: Modifier = Modifier,
    activityTitle: String = "오늘 일정 브리핑",
    activityText: String = "샘플 일정\n" + sampleSchedule.joinToString("\n") { "${it.time}  ${it.title}" },
    footer: String = "업무 브리핑 → 영어 듣기 → 따라 말하기 → 도착 요약",
    errorMessage: String? = null
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize().padding(24.dp)
    ) {
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
                    lineHeight = 44.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "출근 코스 · ${courseMinutes}분 계획",
                    color = Color(0xFF9A9FA5),
                    fontSize = 24.sp
                )
            }

            Column(
                modifier = Modifier.padding(vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Text(
                    text = "현재 활동",
                    color = Color(0xFF9A9FA5),
                    fontSize = 24.sp
                )
                Text(
                    text = activityTitle,
                    fontSize = 40.sp,
                    lineHeight = 54.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = activityText,
                    modifier = Modifier.heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState()),
                    fontSize = 30.sp,
                    lineHeight = 42.sp
                )
                Button(
                    onClick = onTogglePlayback,
                    modifier = Modifier
                        .widthIn(min = 280.dp)
                        .heightIn(min = 88.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF3B82F6),
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = if (errorMessage != null) "다시 시도" else if (isPlaying) "일시정지" else "재생",
                        fontSize = 32.sp
                    )
                }
                Text(
                    text = errorMessage ?: if (isPlaying) "● 재생 중" else "재생 대기 · 일시정지",
                    color = Color(0xFF9A9FA5),
                    fontSize = 24.sp
                )
            }

            Text(
                text = footer,
                color = Color(0xFF9A9FA5),
                fontSize = 24.sp
            )
        }
    }
}