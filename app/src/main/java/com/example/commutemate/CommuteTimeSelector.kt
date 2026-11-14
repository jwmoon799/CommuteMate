package com.example.commutemate

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CommuteTimeSelector(
    minutesText: String,
    onMinutesChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val minutes = minutesText.toIntOrNull()
    val valid = minutes != null && minutes in 5..90

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "예상 출근시간",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "출발 전에 시간을 선택하거나 직접 입력하세요.",
            color = Color(0xFF9A9FA5),
            fontSize = 22.sp,
            lineHeight = 32.sp
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            listOf(10, 20, 30).forEach { option ->
                val selected = minutes == option

                Button(
                    onClick = {
                        onMinutesChange(option.toString())
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 64.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selected) {
                            Color(0xFF3B82F6)
                        } else {
                            Color(0xFF30363D)
                        },
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "${option}분",
                        fontSize = 24.sp
                    )
                }
            }
        }

        OutlinedTextField(
            value = minutesText,
            onValueChange = { input ->
                if (
                    input.length <= 2 &&
                    input.all { it in '0'..'9' }
                ) {
                    onMinutesChange(input)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text(text = "직접 입력 · 분")
            },
            singleLine = true,
            isError = minutesText.isNotEmpty() && !valid,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number
            ),
            textStyle = LocalTextStyle.current.copy(
                fontSize = 28.sp,
                color = Color.White
            )
        )

        Text(
            text = if (valid) {
                "선택한 출근시간: ${minutes}분"
            } else {
                "5~90분 사이의 시간을 입력해주세요."
            },
            color = if (valid || minutesText.isEmpty()) {
                Color(0xFF9A9FA5)
            } else {
                Color(0xFFFFB4AB)
            },
            fontSize = 22.sp,
            lineHeight = 32.sp
        )
    }
}