package com.example.commutemate

/** 사전 준비한 시연 데이터이며 실제 캘린더 연동 결과가 아닙니다. */
data class SampleScheduleItem(val time: String, val title: String)

val sampleSchedule = listOf(
    SampleScheduleItem("09:30", "팀 주간 회의"),
    SampleScheduleItem("13:00", "해외 파트너 화상 통화(영어)"),
    SampleScheduleItem("17:00", "주간 보고서 마감")
)
