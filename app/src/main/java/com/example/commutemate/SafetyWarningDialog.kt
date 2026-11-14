package com.example.commutemate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SafetyWarningDialog(warning: SafetyWarning, speaking: Boolean, error: Boolean,
    turning: Boolean, resumesActivity: Boolean, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        containerColor = Color(0xFF20252B),
        title = { Text(warning.message, fontSize = 32.sp, color = Color(0xFFFFB74D)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("안전 경고 시연 · 실제 센서 감지 아님", color = Color.White, fontSize = 20.sp)
                Text("학습 음성과 마이크를 멈췄습니다.\n전방을 확인한 뒤 직접 재개해주세요.", color = Color.White, fontSize = 24.sp)
                Text(when {
                    error -> "음성 안내를 재생하지 못했습니다. 화면의 경고를 확인해주세요."
                    speaking -> "음성 안내 중"
                    turning -> "방향지시등이 켜져 있습니다. 신호가 꺼져도 학습은 자동 재개되지 않습니다."
                    !resumesActivity -> "기존 일시정지 상태를 유지합니다."
                    else -> "안내가 끝나도 확인 전까지 학습은 계속 정지합니다."
                }, color = Color(0xFFB8BEC5), fontSize = 20.sp)
            }
        },
        confirmButton = {
            Button(enabled = !speaking && !turning, onClick = onConfirm) {
                Text(if (resumesActivity) "확인 · 학습 재개" else "확인 · 정지 상태 유지", fontSize = 22.sp)
            }
        }
    )
}
