package com.example.commutemate

data class EnglishLesson(
    val id: String,
    val question: String,
    val questionTranslation: String,
    val answer: String,
    val answerTranslation: String
)

val englishLessons = listOf(
    EnglishLesson(
        id = "commute",
        question = "How do you usually get to work?",
        questionTranslation = "보통 어떻게 출근하세요?",
        answer = "I usually drive to work.",
        answerTranslation = "저는 보통 운전해서 출근해요."
    ),
    EnglishLesson(
        id = "duration",
        question = "How long does your commute take?",
        questionTranslation = "출근하는 데 얼마나 걸리나요?",
        answer = "It takes about twenty minutes.",
        answerTranslation = "약 20분 걸려요."
    ),
    EnglishLesson(
        id = "start_time",
        question = "What time do you start work?",
        questionTranslation = "몇 시에 일을 시작하세요?",
        answer = "I start work at nine.",
        answerTranslation = "저는 9시에 일을 시작해요."
    ),
    EnglishLesson(
        id = "traffic",
        question = "How was the traffic this morning?",
        questionTranslation = "오늘 아침 교통 상황은 어땠나요?",
        answer = "The traffic was heavier than usual.",
        answerTranslation = "평소보다 차가 많이 막혔어요."
    ),
    EnglishLesson(
        id = "meeting",
        question = "What time is the team meeting?",
        questionTranslation = "팀 회의는 몇 시인가요?",
        answer = "The meeting starts at nine thirty.",
        answerTranslation = "회의는 9시 30분에 시작해요."
    ),
    EnglishLesson(
        id = "priority",
        question = "What is your main priority today?",
        questionTranslation = "오늘 가장 우선해서 할 일은 무엇인가요?",
        answer = "I need to finish the weekly report.",
        answerTranslation = "주간 보고서를 마무리해야 해요."
    ),
    EnglishLesson(
        id = "clarification",
        question = "What can you say when you need clarification?",
        questionTranslation = "설명이 더 필요할 때 어떻게 말할 수 있나요?",
        answer = "Could you explain that again, please?",
        answerTranslation = "다시 설명해 주시겠어요?"
    ),
    EnglishLesson(
        id = "follow_up",
        question = "How will you follow up after the meeting?",
        questionTranslation = "회의 후에 어떻게 후속 내용을 전달할 건가요?",
        answer = "I will send you a summary by email.",
        answerTranslation = "이메일로 요약 내용을 보내드릴게요."
    )
)