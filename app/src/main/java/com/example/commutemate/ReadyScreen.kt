package com.example.commutemate

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ReadyScreen(
    onStart: () -> Unit,
    startEnabled: Boolean,
    minutesText: String,
    onMinutesChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    courseType: CourseType = CourseType.ENGLISH,
    onCourseTypeChange: (CourseType) -> Unit = {},
    newsAudioReady: Boolean = true,
    newsLoading: Boolean = false,
    newsStatus: String? = null,
    onNewsRefresh: () -> Unit = {}
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var courseChosen by rememberSaveable { mutableStateOf(false) }
    var timeChosen by rememberSaveable { mutableStateOf(false) }
    val validMinutes = minutesText.toIntOrNull()?.takeIf { it in 5..90 }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val canAdvance = when (step) {
        0 -> courseChosen
        1 -> timeChosen && validMinutes != null
        else -> false
    }
    Column(modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("커뮤트 메이트", fontSize = 24.sp, color = Color(0xFF9A9FA5))
            Text("${step + 1} / 3", fontSize = 22.sp, color = Color(0xFF9A9FA5))
        }
        AnimatedContent(
            targetState = step,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                val direction = if (targetState > initialState) 1 else -1
                (fadeIn(tween(260)) + slideInHorizontally(tween(260)) { direction * it / 12 }) togetherWith
                    (fadeOut(tween(180)) + slideOutHorizontally(tween(260)) { -direction * it / 12 })
            },
            label = "준비 화면 전환"
        ) { page ->
            when (page) {
                0 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        Modifier.widthIn(max = 760.dp).verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(28.dp)
                    ) {
                        Text("환영합니다", fontSize = 52.sp, fontWeight = FontWeight.Bold)
                        Text("오늘 함께할 코스를 선택해주세요.", fontSize = 24.sp, color = Color(0xFF9A9FA5))
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            if (maxWidth >= 600.dp) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                    CourseType.entries.forEach { type ->
                                        CourseChoice(type, courseChosen && courseType == type,
                                            onClick = { onCourseTypeChange(type); courseChosen = true },
                                            modifier = Modifier.weight(1f))
                                    }
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    CourseType.entries.forEach { type ->
                                        CourseChoice(type, courseChosen && courseType == type,
                                            onClick = { onCourseTypeChange(type); courseChosen = true },
                                            modifier = Modifier.fillMaxWidth())
                                    }
                                }
                            }
                        }
                        if (courseChosen) Text(
                            if (courseType == CourseType.ENGLISH) "영어 8문항 · 두 번 따라 말하기 · 표현 복습"
                            else "최신 경제 뉴스 2편 · 영어 4문항 · 표현 복습",
                            fontSize = 22.sp, color = Color(0xFF9A9FA5)
                        )
                    }
                }
                1 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CommuteTimeSelector(
                        minutesText = minutesText,
                        onMinutesChange = { timeChosen = true; onMinutesChange(it) },
                        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()
                            .verticalScroll(rememberScrollState()).padding(vertical = 24.dp)
                    )
                }
                else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                    val wide = maxWidth >= 720.dp
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(28.dp)
                    ) {
                        val plan = validMinutes?.let { createCoursePlan(it, courseType) }
                        if (wide) {
                            Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                                ReadyIntro(onStart, startEnabled, courseType, Modifier.weight(1f))
                                ReadyCourse(plan, Modifier.weight(1f))
                            }
                        } else {
                            ReadyIntro(onStart, startEnabled, courseType)
                            ReadyCourse(plan)
                        }
                        if (courseType == CourseType.NEWS) {
                            Text(if (newsLoading) "최신 경제 뉴스와 음성을 준비하고 있어요…" else newsStatus ?: "최신 뉴스 연결이 필요합니다.",
                                color = if (!newsAudioReady && !newsLoading) Color(0xFFFFB4AB) else Color(0xFF9A9FA5), fontSize = 22.sp)
                            Button(
                                enabled = !newsLoading,
                                onClick = onNewsRefresh,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFDC2626),
                                    contentColor = Color.White,
                                    disabledContainerColor = Color(0xFF7F1D1D),
                                    disabledContentColor = Color.White
                                )
                            ) {
                                Text(if (newsLoading) "뉴스 불러오는 중…" else "최신 뉴스 새로고침", fontSize = 22.sp)
                            }
                        }
                        Text(if (courseType == CourseType.NEWS) "뉴스는 RSS 조회 시점 기준 · 샘플 일정 · 시연용 경로" else "AI로 사전 생성한 콘텐츠 · 샘플 일정 · 시연용 경로",
                            color = Color(0xFF9A9FA5), fontSize = 22.sp)
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 72.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (step > 0) {
                TextButton(onClick = { focus.clearFocus(); keyboard?.hide(); step-- }) {
                    Text("이전", color = Color.White, fontSize = 24.sp)
                }
            } else Spacer(Modifier.width(1.dp))
            if (canAdvance) Button(
                onClick = {
                    focus.clearFocus(); keyboard?.hide()
                    if (step == 0 && !timeChosen) onMinutesChange("")
                    step++
                },
                modifier = Modifier.widthIn(min = 160.dp).heightIn(min = 64.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6), contentColor = Color.White)
            ) { Text("Next", fontSize = 26.sp) }
        }
    }
}

@Composable
private fun CourseChoice(type: CourseType, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 88.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) Color(0xFFFFC107) else Color(0xFF20252B),
            contentColor = Color.White
        )
    ) { Text(type.label, fontSize = 28.sp, fontWeight = FontWeight.Medium) }
}

@Composable
private fun ReadyIntro(
    onStart: () -> Unit,
    startEnabled: Boolean,
    courseType: CourseType,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = "출근 준비",
            color = Color(0xFF9A9FA5),
            fontSize = 22.sp
        )
        Text(
            text = "우리 집 → 회사",
            fontSize = 52.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = if (courseType == CourseType.NEWS) "브리핑, 최신 경제 뉴스, 영어 학습" else "브리핑, 영어 듣기, 따라 말하기",
            fontSize = 32.sp
        )
        Text(
            text = "샘플 일정 · 08:30 출발\n" + sampleSchedule.joinToString("\n") {
                "${it.time}  ${it.title}"
            },
            fontSize = 22.sp,
            lineHeight = 32.sp,
            color = Color(0xFF9A9FA5)
        )
        Button(
            onClick = onStart,
            enabled = startEnabled,
            modifier = Modifier
                .widthIn(min = 280.dp)
                .heightIn(min = 88.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF3B82F6),
                contentColor = Color.White
            )
        ) {
            Text(text = "시작", fontSize = 32.sp)
        }
    }
}

@Composable
private fun ReadyCourse(
    plan: CoursePlan?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = plan?.title ?: "코스 구성",
            color = Color(0xFF9A9FA5),
            fontSize = 30.sp,
            lineHeight = 42.sp,
            fontWeight = FontWeight.Bold
        )

        if (plan == null) {
            Text(
                text = "5~90분 사이의 출근시간을 선택해주세요.",
                fontSize = 24.sp,
                lineHeight = 34.sp
            )
        } else {
            Text(
                text = "활동 계획 · 실제 음성은 시연용으로 단축",
                color = Color(0xFF9A9FA5),
                fontSize = 24.sp
            )
            Text(
                text = "업무 브리핑  ${plan.briefingMinutes}분",
                fontSize = 30.sp
            )
            if (plan.newsMinutes > 0) Text(text = "최신 경제 뉴스  ${plan.newsMinutes}분", fontSize = 30.sp)
            Text(
                text = "영어 듣기  ${plan.listeningMinutes}분",
                fontSize = 30.sp
            )
            Text(
                text = "따라 말하기  ${plan.speakingMinutes}분",
                fontSize = 30.sp
            )
            Text(
                text = "복습  ${plan.reviewMinutes}분",
                fontSize = 30.sp
            )
            Text(
                text = "여유  ${plan.bufferMinutes}분",
                fontSize = 30.sp
            )
            Text(
                text = "총 ${plan.totalMinutes}분",
                color = Color(0xFF64A5FF),
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}