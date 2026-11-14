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
fun EnglishScreen(
    courseMinutes: Int,
    lesson: EnglishLesson,
    lessonNumber: Int,
    lessonCount: Int,
    isAnswer: Boolean,
    isCompleted: Boolean,
    isPlaying: Boolean,
    onTogglePlayback: () -> Unit,
    errorMessage: String? = null,
    modifier: Modifier = Modifier,
    reviewMode: Boolean = false
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
                    text = if (reviewMode) "오늘의 표현 복습 · ${lessonNumber}/${lessonCount}" else "영어 듣기 · ${lessonNumber}/${lessonCount}문항",
                    color = Color(0xFF9A9FA5),
                    fontSize = 24.sp
                )
                Text(
                    text = if (reviewMode) "다시 들어보세요" else if (isAnswer) "예시 답변" else "질문",
                    color = Color(0xFF64A5FF),
                    fontSize = 28.sp
                )
                Text(
                    text = if (isAnswer) {
                        lesson.answer
                    } else {
                        lesson.question
                    },
                    fontSize = 48.sp,
                    lineHeight = 60.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isAnswer) {
                        lesson.answerTranslation
                    } else {
                        lesson.questionTranslation
                    },
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
                        text = when {
                            isPlaying -> "일시정지"
                            isCompleted -> if (reviewMode) "복습 다시 듣기" else "전체 다시 하기"
                            errorMessage != null -> "다시 시도"
                            else -> "재생"
                        },
                        fontSize = 32.sp
                    )
                }
                Text(
                    text = errorMessage ?: when {
                        isCompleted ->
                            if (reviewMode) "오늘의 표현 ${lessonCount}개 복습 완료" else "전체 ${lessonCount}문항 듣기·따라 말하기 완료"
                        isPlaying ->
                            "● 재생 중"
                        else ->
                            "재생 대기 · 일시정지"
                    },
                    color = if (errorMessage != null) {
                        Color(0xFFFFB4AB)
                    } else {
                        Color(0xFF9A9FA5)
                    },
                    fontSize = 24.sp,
                    lineHeight = 34.sp
                )
            }

            Text(
                text = if (reviewMode) "현재 활동 · 복습" else "현재 활동 · 영어 듣기",
                color = Color(0xFF9A9FA5),
                fontSize = 24.sp
            )
        }
    }
}