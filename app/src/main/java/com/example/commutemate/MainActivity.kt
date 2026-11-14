package com.example.commutemate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.commutemate.ui.theme.CommuteMateTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Button
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.dp
import android.media.MediaPlayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.DisposableEffect
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CommuteMateTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = androidx.compose.ui.graphics.Color(0xFF111315),
                    contentColor = androidx.compose.ui.graphics.Color.White
                ) { innerPadding ->
                    Greeting(
                        name = "Commute Mate",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    val started = remember { mutableStateOf(false) }
    val showSpeechTest = remember { mutableStateOf(false) }
    val turning = remember { mutableStateOf(false) }
    val resumeAfterTurn = remember { mutableStateOf(false) }
    val englishMode = remember { mutableStateOf(false) }
    val arrived = remember { mutableStateOf(false) }
    val safetyWarning = remember { mutableStateOf<SafetyWarning?>(null) }
    val safetyMayResume = remember { mutableStateOf(false) }
    val safetyCueBusy = remember { mutableStateOf(false) }
    val safetyCueError = remember { mutableStateOf(false) }
    val safetyGate = remember { SafetyEventGate() }
    val safetyAccess = remember { mutableStateOf("실제 경고 미연결 · 시연 입력 사용") }
    val safetyMenu = remember { mutableStateOf(false) }
    val vehicleTurning = remember { mutableStateOf(false) }
    val briefingCompleted = remember { mutableStateOf(false) }
    val englishCompleted = remember { mutableStateOf(false) }
    val selectedCourse = androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(CourseType.ENGLISH) }
    val newsMode = remember { mutableStateOf(false) }
    val newsStarted = remember { mutableStateOf(false) }
    val newsCompleted = remember { mutableStateOf(false) }
    val newsIndex = remember { mutableStateOf(0) }
    val newsError = remember { mutableStateOf<String?>(null) }
    val reviewStarted = remember { mutableStateOf(false) }
    val reviewCompleted = remember { mutableStateOf(false) }
    val reviewClipIndex = remember { mutableStateOf(0) }
    val reviewError = remember { mutableStateOf<String?>(null) }
    val completedPracticeCount = remember { mutableStateOf(0) }
    val speakingRequest = remember { mutableStateOf<SpeakingLessonRequest?>(null) }
    val activeCoursePlan = remember {
        mutableStateOf(createCoursePlan(23))
    }
    val commuteMinutesText =
        androidx.compose.runtime.saveable.rememberSaveable {
            mutableStateOf("23")
        }

    val context = LocalContext.current
    val newsStories = remember { mutableStateOf<List<NewsStory>>(emptyList()) }
    val activeNewsStories = remember { mutableStateOf<List<NewsStory>>(emptyList()) }
    val newsFetchedAt = remember { mutableStateOf("") }
    val newsLoading = remember { mutableStateOf(false) }
    val newsLoadError = remember { mutableStateOf<String?>(null) }
    val newsRefresh = remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val newsAudioReady = newsStories.value.size == 2 && !newsLoading.value && newsLoadError.value == null
    LaunchedEffect(selectedCourse.value, newsRefresh.intValue) {
        if (selectedCourse.value != CourseType.NEWS) return@LaunchedEffect
        newsLoading.value = true
        newsLoadError.value = null
        try {
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                NewsRepository.fetch(context.applicationContext, newsRefresh.intValue > 0)
            }
            newsStories.value = result.stories
            newsFetchedAt.value = result.fetchedAt
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            newsStories.value = emptyList()
            newsLoadError.value = "최신 뉴스를 불러오지 못했어요. 뉴스 연결 실행기를 확인하고 다시 시도해주세요."
        } finally { newsLoading.value = false }
    }
    val lessonCount = activeCoursePlan.value.lessonCount

    val briefingPlayer = remember(context) {
        MediaPlayer.create(context, R.raw.briefing)
    }

    fun startBriefingPlayback() {
        briefingPlayer?.let { mediaPlayer ->
            mediaPlayer.playbackParams = android.media.PlaybackParams()
                .setSpeed(1f)
                .setPitch(1f)
            mediaPlayer.start()
        }
    }

    val englishPlayer = remember(context) {
        MediaPlayer.create(context, R.raw.english)
    }
    val safetyCue = remember(context) { SafetyCuePlayer(context.applicationContext) }
    fun receiveSafetyWarning(kind: SafetyWarning) {
        if (arrived.value || !safetyGate.accept(kind, android.os.SystemClock.elapsedRealtime())) return
        if (safetyWarning.value == null) {
            safetyMayResume.value = started.value &&
                (briefingPlayer?.isPlaying == true || englishPlayer?.isPlaying == true ||
                    resumeAfterTurn.value || speakingRequest.value != null)
        }
        // Pause synchronously before preparing any warning audio.
        if (briefingPlayer?.isPlaying == true) briefingPlayer.pause()
        if (englishPlayer?.isPlaying == true) englishPlayer.pause()
        resumeAfterTurn.value = false
        safetyCueBusy.value = true
        safetyCueError.value = false
        safetyWarning.value = kind
        android.util.Log.i("CommuteMateSafety", "warning=${kind.wireName}; source=demo; hold until explicit confirmation")
    }
    LaunchedEffect(safetyWarning.value) {
        val warning = safetyWarning.value ?: return@LaunchedEffect
        safetyCueBusy.value = true
        try {
            // Give the practice coroutine time to cancel its mic and prompt player.
            kotlinx.coroutines.delay(150)
            if ((context as? ComponentActivity)?.lifecycle?.currentState
                    ?.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) != true) return@LaunchedEffect
            safetyCue.play(warning)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) {
            safetyCueError.value = true
            android.util.Log.w("CommuteMateSafety", "warning audio failed; hold retained", e)
        } finally { safetyCueBusy.value = false }
    }
    DisposableEffect(safetyCue) {
        val lifecycle = (context as? ComponentActivity)?.lifecycle
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) safetyCue.stop()
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer); safetyCue.stop() }
    }
    LaunchedEffect(context) {
        var initialized = false
        var cursor = ""
        var wasForeground = false
        while (true) {
            if ((context as? ComponentActivity)?.lifecycle?.currentState
                    ?.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) != true) wasForeground = false
            try {
                val snapshot = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { fetchSafetyDemo() }
                val resumed = (context as? ComponentActivity)?.lifecycle?.currentState
                    ?.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) == true
                // Establish a baseline on every new app instance; never replay an old warning.
                if (initialized && resumed && wasForeground && snapshot.cursor != cursor) snapshot.kind?.let { receiveSafetyWarning(it) }
                cursor = snapshot.cursor; initialized = true; wasForeground = resumed
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { /* No bridge: in-app demo controls remain available. */ }
            kotlinx.coroutines.delay(350)
        }
    }
    val englishClipIndex = remember { mutableStateOf(0) }
    val englishAudioError = remember {
        mutableStateOf<String?>(null)
    }

    // Reuse the existing player and verified answer recordings; no extra audio device.
    val reviewLessons = remember { englishLessons.take(2) }
    val reviewPlaylist = remember(context, englishPlayer) {
        englishPlayer?.let { mediaPlayer ->
            EnglishPlaylist(
                context = context,
                player = mediaPlayer,
                lessons = reviewLessons,
                isSessionActive = { started.value && englishMode.value && !arrived.value },
                canAutoPlay = { !turning.value && !vehicleTurning.value && safetyWarning.value == null },
                onStarted = {
                    reviewStarted.value = true
                    reviewCompleted.value = false
                    reviewError.value = null
                    speakingRequest.value = null
                    resumeAfterTurn.value = false
                },
                onClipChanged = { reviewClipIndex.value = it },
                onWaiting = { resumeAfterTurn.value = true },
                onFinished = { reviewCompleted.value = true; resumeAfterTurn.value = false },
                onError = { reviewError.value = it; resumeAfterTurn.value = false },
                answerOnly = true
            )
        }
    }

    val englishPlaylist = remember(context, englishPlayer) {
        englishPlayer?.let { mediaPlayer ->
            EnglishPlaylist(
                context = context,
                player = mediaPlayer,
                lessons = englishLessons,
                lessonLimit = { activeCoursePlan.value.lessonCount },
                isSessionActive = {
                    started.value &&
                            !arrived.value &&
                            englishMode.value
                },
                canAutoPlay = {
                    !turning.value && !vehicleTurning.value && safetyWarning.value == null
                },
                onStarted = {
                    reviewPlaylist?.cancel()
                    reviewStarted.value = false
                    reviewCompleted.value = false
                    reviewError.value = null
                    speakingRequest.value = null
                    englishCompleted.value = false
                    completedPracticeCount.value = 0
                    englishAudioError.value = null
                    resumeAfterTurn.value = false
                },
                onClipChanged = { index ->
                    englishClipIndex.value = index
                },
                onWaiting = {
                    resumeAfterTurn.value = true
                },
                onFinished = {
                    speakingRequest.value = null
                    englishCompleted.value = true
                    resumeAfterTurn.value = false
                    reviewPlaylist?.startFromBeginning()
                },
                onError = { message ->
                    speakingRequest.value = null
                    englishAudioError.value = message
                    resumeAfterTurn.value = false
                },
                onPracticeRequested = { request ->
                    speakingRequest.value = request
                    resumeAfterTurn.value = false
                }
            )
        }
    }
    val newsPlaylist = remember(context, briefingPlayer, englishPlaylist) {
        briefingPlayer?.let { mediaPlayer ->
            NewsPlaylist(context, mediaPlayer, { activeNewsStories.value },
                active = { started.value && newsMode.value && !arrived.value },
                canPlay = { !turning.value && !vehicleTurning.value && safetyWarning.value == null },
                onClip = { newsIndex.value = it },
                onWaiting = { resumeAfterTurn.value = true },
                onFinished = {
                    newsCompleted.value = true
                    newsMode.value = false
                    englishMode.value = true
                    resumeAfterTurn.value = false
                    englishPlaylist?.startFromBeginning()
                },
                onError = { newsError.value = "뉴스 음성을 재생하지 못했어요. $it"; resumeAfterTurn.value = false }
            )
        }
    }
    fun prepareBriefing() {
        briefingPlayer?.let { mediaPlayer ->
            mediaPlayer.reset()
            context.resources.openRawResourceFd(R.raw.briefing).use {
                mediaPlayer.setDataSource(it.fileDescriptor, it.startOffset, it.length)
            }
            mediaPlayer.setOnErrorListener(null)
            mediaPlayer.setOnCompletionListener {
                if (started.value && !arrived.value && !englishMode.value && !newsMode.value) {
                    briefingCompleted.value = true
                    resumeAfterTurn.value = false
                    if (activeCoursePlan.value.courseType == CourseType.NEWS) {
                        newsMode.value = true
                        newsStarted.value = true
                        newsPlaylist?.start()
                    } else {
                        englishMode.value = true
                        englishPlaylist?.startFromBeginning()
                    }
                }
            }
            mediaPlayer.prepare()
        }
    }

    val sdkStatus = remember {
        mutableStateOf("초기화 대기")
    }

    val vehicle = remember(context) {
        Vehicle(context.applicationContext)
    }

    val signalListener = remember(vehicle) {
        object : TurnSignalStateListener {
            override fun onTurnSignalStateUpdated(state: TurnSignalValue?) {
                context.mainExecutor.execute {
                    android.util.Log.d("CommuteMateTurn", "Vehicle SDK signal=${state?.name}; source=Vehicle; auto-resume requires OFF")
                    sdkStatus.value =
                        "실시간 방향지시등: ${state?.name ?: "값 없음"}"

                    when (state) {
                        TurnSignalValue.LEFT,
                        TurnSignalValue.RIGHT -> {
                            vehicleTurning.value = true
                        }

                        TurnSignalValue.OFF -> {
                            vehicleTurning.value = false
                        }

                        null -> {
                            // 값이 없으면 이전 상태 유지
                        }
                    }
                }
            }

            override fun onFailed(e: Exception) {
                context.mainExecutor.execute {
                    sdkStatus.value = "감시 오류: ${e.message}"
                }
            }
        }
    }


    DisposableEffect(vehicle) {
        try {
            vehicle.initialize()
            inspectSafetyAccess(context, vehicle) { status ->
                context.mainExecutor.execute { safetyAccess.value = status }
            }

            sdkStatus.value = "자동 감시 등록 요청 · 신호 수신 대기"
            vehicle.getTurnSignal()
                .registerTurnSignalState(signalListener)
        } catch (e: Exception) {
            sdkStatus.value = "차량 연결·감시 등록 실패: ${e.message}"
        }

        onDispose {
            runCatching {
                vehicle.getTurnSignal()
                    .unregisterTurnSignalState(signalListener)
            }

            runCatching {
                vehicle.release()
            }
        }
    }



    val player = if (englishMode.value) {
        englishPlayer
    } else {
        briefingPlayer
    }

    val pauseForTurn = turning.value || vehicleTurning.value || safetyWarning.value != null

    LaunchedEffect(
        pauseForTurn,
        started.value,
        arrived.value,
        englishMode.value,
        speakingRequest.value
    ) {
        if (!started.value || arrived.value || speakingRequest.value != null) {
            resumeAfterTurn.value = false
            return@LaunchedEffect
        }

        if (safetyWarning.value != null) {
            if (player?.isPlaying == true) player.pause()
            resumeAfterTurn.value = false
            return@LaunchedEffect
        }
        if (pauseForTurn) {
            if (player?.isPlaying == true) {
                resumeAfterTurn.value = true
                player.pause()
            }
        } else if (resumeAfterTurn.value) {
            resumeAfterTurn.value = false
            player?.start()
        }
    }

    DisposableEffect(
        briefingPlayer,
        englishPlayer,
        englishPlaylist
    ) {
        onDispose {
            briefingPlayer?.setOnCompletionListener(null)
            englishPlayer?.setOnCompletionListener(null)
            briefingPlayer?.release()
            englishPlayer?.release()
        }
    }



    val briefingIsPlaying = remember { mutableStateOf(false) }
    val englishIsPlaying = remember { mutableStateOf(false) }

    LaunchedEffect(briefingPlayer, englishPlayer, started.value, showSpeechTest.value, speakingRequest.value) {
        if (!started.value || showSpeechTest.value || speakingRequest.value != null) return@LaunchedEffect
        while (true) {
            briefingIsPlaying.value = briefingPlayer?.isPlaying == true
            englishIsPlaying.value = englishPlayer?.isPlaying == true
            kotlinx.coroutines.delay(150)
        }
    }
    safetyWarning.value?.let { warning ->
        SafetyWarningDialog(
            warning = warning, speaking = safetyCueBusy.value, error = safetyCueError.value,
            turning = turning.value || vehicleTurning.value,
            resumesActivity = safetyMayResume.value,
            onConfirm = {
                val resume = safetyMayResume.value
                safetyCue.stop()
                safetyGate.clear(android.os.SystemClock.elapsedRealtime())
                safetyWarning.value = null
                safetyMayResume.value = false
                resumeAfterTurn.value = false
                if (resume && started.value && !arrived.value && speakingRequest.value == null) player?.start()
            }
        )
    }
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "차량 SDK: ${sdkStatus.value}")
        Text(text = "안전 경고: ${safetyAccess.value}", color = androidx.compose.ui.graphics.Color(0xFF9A9FA5))
        androidx.compose.foundation.layout.Box {
            Button(enabled = !arrived.value, onClick = { safetyMenu.value = true }) { Text("안전 경고 시연") }
            androidx.compose.material3.DropdownMenu(expanded = safetyMenu.value, onDismissRequest = { safetyMenu.value = false }) {
                androidx.compose.material3.DropdownMenuItem(text = { Text("시연: 앞차 출발") }, onClick = {
                    safetyMenu.value = false; receiveSafetyWarning(SafetyWarning.LEAD_DEPARTURE)
                })
                androidx.compose.material3.DropdownMenuItem(text = { Text("시연: 전방 주시") }, onClick = {
                    safetyMenu.value = false; receiveSafetyWarning(SafetyWarning.ATTENTION)
                })
            }
        }
        Button(
            enabled = !pauseForTurn && !started.value && !showSpeechTest.value,
            onClick = { showSpeechTest.value = true }
        ) { Text(text = "영어 한 문장 인식 테스트") }


        if (!started.value && showSpeechTest.value) {
            SpeechTestPanel(
                blocked = pauseForTurn,
                safetyPause = safetyWarning.value != null,
                onClose = {
                    showSpeechTest.value = false
                },
                modifier = Modifier.weight(1f)
            )
            return@Column
        }

        if (!started.value) {

            ReadyScreen(
                onStart = {
                    activeCoursePlan.value =
                        createCoursePlan(commuteMinutesText.value.toInt(), selectedCourse.value)
                    activeNewsStories.value = newsStories.value
                    englishPlaylist?.cancel()
                    newsPlaylist?.cancel()
                    newsMode.value = false
                    newsStarted.value = false
                    newsCompleted.value = false
                    newsError.value = null
                    reviewPlaylist?.cancel()
                    reviewStarted.value = false
                    reviewCompleted.value = false
                    reviewError.value = null
                    speakingRequest.value = null
                    briefingCompleted.value = false
                    englishCompleted.value = false
                    completedPracticeCount.value = 0
                    englishMode.value = false
                    turning.value = false
                    resumeAfterTurn.value = false
                    arrived.value = false

                    prepareBriefing()
                    startBriefingPlayback()
                    started.value = true
                },
                startEnabled = !pauseForTurn &&
                        ((commuteMinutesText.value.toIntOrNull() ?: 0) in 5..90) &&
                        (selectedCourse.value != CourseType.NEWS || newsAudioReady),
                minutesText = commuteMinutesText.value,
                onMinutesChange = { commuteMinutesText.value = it },
                courseType = selectedCourse.value,
                onCourseTypeChange = { selectedCourse.value = it },
                newsAudioReady = newsAudioReady,
                newsLoading = newsLoading.value,
                newsStatus = newsLoadError.value ?: if (newsFetchedAt.value.isNotEmpty()) "최신순 2편 · 불러온 시각 ${newsFetchedAt.value}" else null,
                onNewsRefresh = { newsRefresh.intValue++ }
            )
            return@Column
        }
        if (arrived.value) {
            ArrivalScreen(
                englishStarted = englishMode.value,
                activitySummary = buildString {
                    append("업무 브리핑 · ")
                    append(
                        if (briefingCompleted.value) {
                            "끝까지 들음"
                        } else {
                            "도중에 종료"
                        }
                    )

                    if (activeCoursePlan.value.courseType == CourseType.NEWS) {
                        append("\n경제·산업 뉴스 · ")
                        append(when {
                            newsCompleted.value -> "2개 모두 들음"
                            newsStarted.value -> "도중에 종료"
                            else -> "시작 전"
                        })
                    }
                    append("\n영어 듣기 · ")
                    append(
                        when {
                            englishCompleted.value -> "전체 완료"
                            englishMode.value -> "도중에 종료"
                            else -> "시작 전"
                        }
                    )
                    append("\n따라 말하기 · ${completedPracticeCount.value}/${lessonCount}문항 완료")
                    append("\n복습 · ")
                    append(when {
                        reviewCompleted.value -> "표현 2개 완료"
                        reviewStarted.value -> "도중에 종료"
                        else -> "시작 전"
                    })
                },
                modifier = Modifier.weight(1f)
            )
        } else if (newsMode.value && !pauseForTurn) {
            val story = activeNewsStories.value[newsIndex.value]
            BriefingScreen(
                courseMinutes = activeCoursePlan.value.commuteMinutes,
                isPlaying = briefingIsPlaying.value,
                activityTitle = "뉴스 ${newsIndex.value + 1}/${activeNewsStories.value.size} · ${story.title}",
                activityText = "${story.category}\n${story.source} · 기사 발행 ${story.publishedAt}\n불러온 시각 ${story.fetchedAt} · 기사 전체 본문\n\n${story.text}",
                footer = "업무 브리핑 → 뉴스 듣기 → 영어 학습 → 복습 → 도착 요약",
                errorMessage = newsError.value,
                onTogglePlayback = {
                    if (!turning.value && !vehicleTurning.value && safetyWarning.value == null && !arrived.value) {
                        resumeAfterTurn.value = false
                        if (newsError.value != null) { newsError.value = null; newsPlaylist?.start() }
                        else if (briefingPlayer?.isPlaying == true) briefingPlayer.pause()
                        else briefingPlayer?.start()
                        briefingIsPlaying.value = briefingPlayer?.isPlaying == true
                    }
                },
                modifier = Modifier.weight(1f)
            )
        } else if (!englishMode.value && !arrived.value && !pauseForTurn) {
            BriefingScreen(
                courseMinutes = activeCoursePlan.value.commuteMinutes,
                isPlaying = briefingIsPlaying.value,
                footer = if (activeCoursePlan.value.courseType == CourseType.NEWS)
                    "업무 브리핑 → 뉴스 듣기 → 영어 학습 → 복습 → 도착 요약"
                else "업무 브리핑 → 영어 듣기 → 따라 말하기 → 복습 → 도착 요약",
                onTogglePlayback = {
                    if (!pauseForTurn && !arrived.value) {
                        resumeAfterTurn.value = false

                        if (briefingPlayer?.isPlaying == true) {
                            briefingPlayer.pause()
                        } else {
                            startBriefingPlayback()
                        }

                        briefingIsPlaying.value =
                            briefingPlayer?.isPlaying == true
                    }
                },
                modifier = Modifier.weight(1f)
            )
        } else if (englishMode.value && speakingRequest.value != null) {
            val request = speakingRequest.value!!
            val lesson = englishLessons[request.lessonIndex]
            key(request.sessionId, request.lessonIndex) {
                SpeechTestPanel(
                    blocked = pauseForTurn,
                safetyPause = safetyWarning.value != null,
                    onClose = {},
                    targetText = lesson.answer,
                    targetAudioResId = lesson.audio.answerResId,
                    targetTranslation = lesson.answerTranslation,
                    title = "영어 따라 말하기 · ${request.lessonIndex + 1}/${lessonCount}문항",
                    courseMinutes = activeCoursePlan.value.commuteMinutes,
                    courseMode = true,
                    vehicleSignalActive = vehicleTurning.value,
                    canContinue = {
                        started.value && !arrived.value && englishMode.value &&
                            speakingRequest.value == request && !turning.value && !vehicleTurning.value && safetyWarning.value == null
                    },
                    onPracticeComplete = {
                        if (started.value && !arrived.value && englishMode.value &&
                            speakingRequest.value == request && !turning.value && !vehicleTurning.value && safetyWarning.value == null) {
                            completedPracticeCount.value = maxOf(completedPracticeCount.value, request.lessonIndex + 1)
                            speakingRequest.value = null
                            englishPlaylist?.completePractice(request)
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        } else if (pauseForTurn && !arrived.value) {
            TurnPauseScreen(
                vehicleSignalActive = vehicleTurning.value,
                willResume = resumeAfterTurn.value,
                safetyPause = safetyWarning.value != null,
                modifier = Modifier.weight(1f)
            )
        } else if (reviewStarted.value && !arrived.value) {
            EnglishScreen(
                courseMinutes = activeCoursePlan.value.commuteMinutes,
                lesson = reviewLessons[reviewClipIndex.value],
                lessonNumber = reviewClipIndex.value + 1,
                lessonCount = reviewLessons.size,
                isAnswer = true,
                isCompleted = reviewCompleted.value,
                isPlaying = englishIsPlaying.value,
                errorMessage = reviewError.value,
                reviewMode = true,
                onTogglePlayback = {
                    if (!pauseForTurn && !arrived.value) {
                        resumeAfterTurn.value = false
                        if (englishPlayer?.isPlaying == true) englishPlayer.pause()
                        else if (reviewCompleted.value || reviewError.value != null) reviewPlaylist?.startFromBeginning()
                        else englishPlayer?.start()
                        englishIsPlaying.value = englishPlayer?.isPlaying == true
                    }
                },
                modifier = Modifier.weight(1f)
            )
        } else if (englishMode.value && !arrived.value) {
            EnglishScreen(
                courseMinutes = activeCoursePlan.value.commuteMinutes,
                lesson = englishLessons[englishClipIndex.value / 2],
                lessonNumber = englishClipIndex.value / 2 + 1,
                lessonCount = lessonCount,
                isAnswer = englishClipIndex.value % 2 == 1,
                isCompleted = englishCompleted.value,
                errorMessage = englishAudioError.value,
                isPlaying = englishIsPlaying.value,
                onTogglePlayback = {
                    if (!pauseForTurn && !arrived.value) {
                        resumeAfterTurn.value = false

                        if (englishPlayer?.isPlaying == true) {
                            englishPlayer.pause()
                        } else if (
                            englishCompleted.value ||
                            englishAudioError.value != null
                        ) {
                            englishPlaylist?.startFromBeginning()
                        } else {
                            englishPlayer?.start()
                        }

                        englishIsPlaying.value =
                            englishPlayer?.isPlaying == true
                    }
                },
                modifier = Modifier.weight(1f)
            )
        } else {
            Text(
                text = "커뮤트 메이트",
                style = androidx.compose.material3.MaterialTheme.typography.headlineLarge
            )

            Text(
                text = when {
                    arrived.value ->
                        if (englishMode.value) {
                            "도착 요약\n시작한 활동: 업무 브리핑, 영어 듣기\n예문: I usually drive to work."
                        } else {
                            "도착 요약\n시작한 활동: 업무 브리핑\n영어 활동은 다음 출근길에 이어가세요."
                        }

                    pauseForTurn ->
                        "콘텐츠 일시정지\n방향지시등 또는 시연 입력으로 잠시 멈춥니다."

                    else ->
                        "영어 듣기 · 따라 말하기\nHow do you usually get to work?\n예시: I usually drive to work."
                },
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge
            )
        }

        Button(
            enabled = started.value || !pauseForTurn,
            onClick = {
                englishPlaylist?.cancel()
                newsPlaylist?.cancel()
                newsMode.value = false
                newsStarted.value = false
                newsCompleted.value = false
                newsError.value = null
                reviewPlaylist?.cancel()
                reviewStarted.value = false
                reviewCompleted.value = false
                reviewError.value = null
                speakingRequest.value = null
                if (started.value) {
                    if (briefingPlayer?.isPlaying == true) {
                        briefingPlayer.pause()
                    }
                    briefingPlayer?.seekTo(0)
                    if (englishPlayer?.isPlaying == true) {
                        englishPlayer.pause()
                    }
                    englishPlayer?.seekTo(0)
                    started.value = false
                } else {
                    prepareBriefing()
                    startBriefingPlayback()
                    started.value = true
                }

                englishMode.value = false
                turning.value = false
                resumeAfterTurn.value = false
                arrived.value = false
                briefingCompleted.value = false
                englishCompleted.value = false
                completedPracticeCount.value = 0
            }
        ) {
            Text(
                text = if (started.value) "처음으로" else "출근 시작"
            )
        }

        if (started.value && !arrived.value) {
            Button(
                onClick = {
                    turning.value = !turning.value
                }
            ) {
                Text(
                    text = if (turning.value) {
                        "시연: 회전 완료"
                    } else {
                        "시연: 회전 임박"
                    }
                )
            }


            Button(
                enabled = !pauseForTurn,
                onClick = {
                    if (briefingPlayer?.isPlaying == true) {
                        briefingPlayer.pause()
                    }

                    if (englishPlayer?.isPlaying == true) {
                        englishPlayer.pause()
                    }

                    newsPlaylist?.cancel()
                    newsMode.value = false
                    englishMode.value = true
                    resumeAfterTurn.value = false

                    englishPlaylist?.startFromBeginning()
                }
            ) {
                Text(
                    text = if (englishMode.value) {
                        "영어 처음부터 다시 듣기"
                    } else {
                        "시연: 정체 구간 → 영어 활동"
                    }
                )
            }

            if (englishMode.value && !reviewStarted.value) {
                Button(
                    enabled = !pauseForTurn,
                    onClick = {
                        englishPlaylist?.cancel()
                        speakingRequest.value = null
                        if (englishPlayer?.isPlaying == true) englishPlayer.pause()
                        reviewPlaylist?.startFromBeginning()
                    }
                ) { Text("시연: 복습으로 이동") }
            }

            Button(
                onClick = {
                    if (briefingPlayer?.isPlaying == true) {
                        briefingPlayer.pause()
                    }

                    if (englishPlayer?.isPlaying == true) {
                        englishPlayer.pause()
                    }

                    englishPlaylist?.cancel()
                    newsPlaylist?.cancel()
                    reviewPlaylist?.cancel()
                    speakingRequest.value = null
                    turning.value = false
                    resumeAfterTurn.value = false
                    arrived.value = true
                }
            ) {
                Text(text = "시연: 도착·주차")
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    CommuteMateTheme {
        Greeting("Android")
    }
}
