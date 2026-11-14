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
fun TurnPauseScreen(
    vehicleSignalActive: Boolean,
    willResume: Boolean,
    modifier: Modifier = Modifier,
    resumeMessage: String? = null,
    safetyPause: Boolean = false
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
                    text = if (safetyPause) {
                        "안전 경고 시연 입력"
                    } else if (vehicleSignalActive) {
                        "차량 방향지시등 신호 수신"
                    } else {
                        "시연 입력"
                    },
                    color = Color(0xFF9A9FA5),
                    fontSize = 24.sp
                )
            }

            Column(
                modifier = Modifier.padding(vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Text(
                    text = "일시정지됨",
                    color = Color(0xFFFFA726),
                    fontSize = 52.sp,
                    lineHeight = 64.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (safetyPause) {
                        "안전 안내를 위해\n학습을 멈춥니다."
                    } else if (vehicleSignalActive) {
                        "방향지시등이 켜져\n콘텐츠를 잠시 멈춥니다."
                    } else {
                        "시연 입력으로\n콘텐츠를 잠시 멈춥니다."
                    },
                    fontSize = 30.sp,
                    lineHeight = 42.sp
                )
                Text(
                    text = resumeMessage ?: if (willResume) {
                        "중단 조건이 해제되면\n멈춘 지점부터 이어 재생합니다."
                    } else {
                        "중단 조건이 해제되어도\n재생 대기 상태를 유지합니다."
                    },
                    color = Color(0xFF9A9FA5),
                    fontSize = 24.sp,
                    lineHeight = 34.sp
                )
            }

            Text(
                text = "운전에 집중해주세요.",
                color = Color(0xFF9A9FA5),
                fontSize = 24.sp
            )
        }
    }
}