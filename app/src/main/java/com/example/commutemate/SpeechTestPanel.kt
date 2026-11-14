package com.example.commutemate

import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** A short in-session noise estimate and hysteresis gate; never restarts the mic. */
internal class SpeakingActivity(knownNoise: Double? = null) {
    private var startedAt: Long? = null
    private val baseline = mutableListOf<Double>()
    private var previousSampleAt: Long? = null
    private var lastVoiceAt: Long? = null
    private var lastSampleAt: Long? = null
    private var aboveThresholdMillis = 0L
    private var voicedMillis = 0L
    private var gateOpen = false
    var noiseFloor = knownNoise?.coerceAtLeast(0.0) ?: 0.0
        private set
    var calibrated = knownNoise != null
        private set
    val hasVoice: Boolean get() = voicedMillis >= 600L

    fun sample(rms: Double, now: Long): Float {
        if (!rms.isFinite()) return 0f
        val level = rms.coerceAtLeast(0.0)
        val start = startedAt ?: now.also { startedAt = it }
        val previous = previousSampleAt
        val delta = if (previous == null) 0L else (now - previous).coerceIn(0L, 120L)
        previousSampleAt = now
        lastSampleAt = now
        if (!calibrated) {
            // Ignore the first 100ms of microphone startup/remaining speaker echo.
            if (now - start >= 100L) baseline.add(level)
            if (now - start < 350L || baseline.size < 2) return 0f
            val sorted = baseline.sorted()
            noiseFloor = sorted[sorted.size / 5]
            baseline.clear()
            calibrated = true
        } else if (!gateOpen && level < noiseFloor) {
            // Learn quieter background without raising the floor during a phrase.
            noiseFloor += (level - noiseFloor) * 0.15
        }
        val openAt = maxOf(300.0, noiseFloor * 2.0 + 100.0)
        val closeAt = maxOf(200.0, noiseFloor * 1.35 + 80.0)
        if (!gateOpen) {
            if (level >= openAt) {
                aboveThresholdMillis += delta
                if (aboveThresholdMillis >= 120L) gateOpen = true
            } else {
                aboveThresholdMillis = 0L
            }
        }
        if (gateOpen) {
            if (level >= closeAt) {
                voicedMillis += delta
                lastVoiceAt = now
            } else {
                // Visual release is smoothed by the waveform, not by fake input.
                gateOpen = false
                aboveThresholdMillis = 0L
            }
        }
        if (!gateOpen) return 0f
        return ((level - closeAt).coerceAtLeast(0.0) / maxOf(1_800.0, noiseFloor * 4.0))
            .toFloat().coerceIn(0f, 1f)
    }

    fun endedWithSilence(now: Long): Boolean {
        val voiceAt = lastVoiceAt ?: return false
        val sampleAt = lastSampleAt ?: return false
        return hasVoice && now - voiceAt >= 1_200L && now - sampleAt <= 400L
    }
}


/** New samples enter on the right; stored samples move left without being reanimated. */
internal class SpeakingWaveHistory(val size: Int = 120) {
    private val samples = FloatArray(size)
    private var head = 0
    init { require(size >= 2) }
    fun push(sample: Float) {
        samples[head] = if (sample.isFinite()) sample.coerceIn(-1f, 1f) else 0f
        head = (head + 1) % size
    }
    fun valueAt(index: Int): Float = samples[(head + index) % size]
    fun clear() { samples.fill(0f); head = 0 }
}


private enum class SpeakingMicMode { IDLE, LISTENING }

@Composable
fun SpeechTestPanel(
    blocked: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    targetText: String = "I usually drive to work.",
    targetAudioResId: Int = R.raw.lesson_commute_answer,
    targetTranslation: String? = null,
    title: String = "두 번 따라 말하기 테스트",
    courseMinutes: Int? = null,
    courseMode: Boolean = false,
    vehicleSignalActive: Boolean = false,
    safetyPause: Boolean = false,
    onPracticeComplete: (() -> Unit)? = null,
    canContinue: () -> Boolean = { true }
) {
    val context = LocalContext.current
    var ready by remember { mutableStateOf(false) }
    var wantsSession by remember { mutableStateOf(true) }
    var introDone by remember { mutableStateOf(false) }
    var praisePending by remember { mutableStateOf(false) }
    var completed by remember { mutableStateOf(0) }
    var status by remember { mutableStateOf("마이크 연결 준비 중") }
    var micMode by remember { mutableStateOf(SpeakingMicMode.IDLE) }
    var detector by remember { mutableStateOf(SpeakingActivity()) }
    var attempt by remember { mutableStateOf<CompletableDeferred<Boolean>?>(null) }
    val targetLevel = remember { mutableFloatStateOf(0f) }
    val lastInputAt = remember { mutableLongStateOf(0L) }
    var inputReceived by remember { mutableStateOf(false) }
    var heardSpeech by remember { mutableStateOf(false) }
    val latestBlocked = rememberUpdatedState(blocked)
    val latestCompletion = rememberUpdatedState(onPracticeComplete)
    val latestCanContinue = rememberUpdatedState(canContinue)
    val speech = remember(context) { SpeechToText(context.applicationContext, Mode.ON_DEVICE) }
    val prompts = remember(context) { SpeakingPromptPlayer(context.applicationContext) }
    val alive = remember(speech) { AtomicBoolean(true) }
    val active = micMode == SpeakingMicMode.LISTENING && !blocked

    fun isBlockedNow(): Boolean = latestBlocked.value || !latestCanContinue.value()

    fun closeMicrophone() {
        micMode = SpeakingMicMode.IDLE
        targetLevel.floatValue = 0f
        runCatching { speech.stop() }
    }

    DisposableEffect(speech) {
        fun update(action: () -> Unit) {
            context.mainExecutor.execute { if (alive.get()) action() }
        }
        val listener = object : ResultListener {
            override fun onReady() { Log.d("CommuteMateSpeaking", "microphone ready") }
            override fun onStartedRecognition() { Log.d("CommuteMateSpeaking", "recognition started") }
            override fun onUpdated(stt: String, completed: Boolean) = update {
                if (micMode == SpeakingMicMode.LISTENING && !isBlockedNow() && stt.isNotBlank()) {
                    // Any detected speech is sufficient; words are never scored or displayed.
                    heardSpeech = true
                    if (completed) attempt?.complete(true)
                }
            }
            override fun onUpdatedEpdData(on: Long, off: Long) = Unit
            override fun onUpdatedRms(rms: Double) = update {
                if (micMode != SpeakingMicMode.IDLE && !isBlockedNow()) {
                    val now = SystemClock.elapsedRealtime()
                    if (rms.isFinite()) {
                        val wasCalibrated = detector.calibrated
                        val hadVoice = detector.hasVoice
                        val visibleLevel = detector.sample(rms, now)
                        if (!wasCalibrated && detector.calibrated) {
                            Log.d("CommuteMateSpeaking", "noise floor: ${detector.noiseFloor}")
                        }
                        if (!hadVoice && detector.hasVoice) {
                            Log.d("CommuteMateSpeaking", "sustained voice level: $rms")
                        }
                        lastInputAt.longValue = now
                        if (!inputReceived) Log.d("CommuteMateSpeaking", "first RMS received: $rms")
                        inputReceived = true
                        // Raw SDK RMS can be hundreds/thousands. Use the noise gate's
                        // scaled signal instead of saturating every reading at rms/16.
                        targetLevel.floatValue = visibleLevel
                    }
                }
            }
            override fun onEndedRecognition() = update {
                if (!isBlockedNow() && micMode == SpeakingMicMode.LISTENING) {
                    Log.d("CommuteMateSpeaking", "recognition ended; activity=${detector.hasVoice}; speech=$heardSpeech")
                    attempt?.complete(detector.hasVoice || heardSpeech)
                }
            }
            override fun onError(errorType: ErrorType) = update {
                Log.w("CommuteMateSpeaking", "microphone error: ${errorType.name}")
                val error = IllegalStateException("마이크 입력 오류: ${errorType.name}")
                when (micMode) {
                    SpeakingMicMode.LISTENING -> attempt?.completeExceptionally(error)
                    SpeakingMicMode.IDLE -> Unit
                }
            }
        }
        try {
            speech.initialize(object : OnInitializeListener {
                override fun onSuccess() = update {
                    try {
                        speech.setLocale(Locale.US)
                        speech.addListener(listener)
                        ready = true
                    } catch (e: Exception) {
                        status = "마이크 설정 실패: ${e.message}"
                    }
                }
                override fun onFailure(errorType: ErrorType) = update {
                    status = "초기화 실패: ${errorType.name}"
                }
            })
        } catch (e: Exception) {
            status = "초기화 오류: ${e.message}"
        }
        onDispose {
            alive.set(false)
            prompts.release()
            runCatching { speech.stop() }
            runCatching { speech.removeListener(listener) }
            runCatching { speech.release() }
        }
    }

    // One coroutine owns prompts and microphone sessions. Turning cancels both;
    // OFF restarts the interrupted turn without counting it as a completed attempt.
    LaunchedEffect(ready, blocked, wantsSession) {
        if (!ready || !wantsSession) return@LaunchedEffect
        if (blocked) {
            status = if (safetyPause) "안전 안내로 학습을 멈췄어요." else "방향지시등으로 잠시 멈췄어요."
            return@LaunchedEffect
        }
        suspend fun listen(): Boolean {
            detector = SpeakingActivity()
            heardSpeech = false
            inputReceived = false
            targetLevel.floatValue = 0f
            lastInputAt.longValue = 0L
            val result = CompletableDeferred<Boolean>()
            attempt = result
            micMode = SpeakingMicMode.LISTENING
            status = "듣고 있어요"
            val began = SystemClock.elapsedRealtime()
            return try {
                Log.d("CommuteMateSpeaking", "request attempt ${completed + 1}")
                speech.request()
                while (!result.isCompleted) {
                    delay(80)
                    val now = SystemClock.elapsedRealtime()
                    if (isBlockedNow()) throw CancellationException("turn signal")
                    if (!inputReceived && !heardSpeech && now - began >= 5_000) {
                        throw IllegalStateException("마이크 입력 신호가 오지 않았어요. 연결 상태 확인이 필요합니다.")
                    }
                    if (detector.endedWithSilence(now)) result.complete(true)
                    else if (now - began >= 20_000) result.complete(false)
                }
                result.await()
            } finally {
                closeMicrophone()
                attempt = null
            }
        }
        suspend fun praise() {
            status = "잘했어요"
            prompts.play("praise")
            praisePending = false
            // Countdown includes 500ms of silent output warmup; total audible pause is 1s.
            if (completed < 2) delay(500)
        }
        try {
            val required = listOf("intro", "praise", "retry", "countdown")
            check(required.all { name ->
                context.assets.list("speaking")?.contains("$name.wav") == true
            }) { "안내 음성 파일이 없습니다. 음성 생성 후 앱을 다시 실행해주세요." }
            if (!introDone) {
                status = "따라 말하기 안내"
                prompts.play("intro")
                if (isBlockedNow()) throw CancellationException("turn signal")
                status = "따라 말할 문장을 들어보세요."
                prompts.playResource(targetAudioResId)
                introDone = true
            }
            if (praisePending) praise()
            while (completed < 2) {
                status = "준비하세요"
                // All four numbers share one audio stream; no per-number player churn.
                prompts.play("countdown")
                // Prompt playback has finished; only the user's input is counted.
                if (listen()) {
                    if (isBlockedNow()) throw CancellationException("turn signal")
                    completed += 1
                    praisePending = true
                    praise()
                } else {
                    status = "다시 해볼까요?"
                    prompts.play("retry")
                    delay(500)
                }
            }
            if (isBlockedNow()) throw CancellationException("turn signal")
            status = "두 번 따라 말하기 완료!"
            if (courseMode) {
                // Let the completed phrase settle before starting the next question.
                delay(2_000)
                if (isBlockedNow()) throw CancellationException("turn signal")
            }
            wantsSession = false
            latestCompletion.value?.invoke()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            status = e.message ?: "따라 말하기를 시작하지 못했어요."
            wantsSession = false
        } finally {
            closeMicrophone()
            prompts.stop()
        }
    }

    if (blocked && courseMode) {
        TurnPauseScreen(
            vehicleSignalActive = vehicleSignalActive,
            willResume = wantsSession && !safetyPause,
            safetyPause = safetyPause,
            resumeMessage = if (safetyPause) "전방 확인 후 경고 창에서 직접 재개해주세요." else if (wantsSession) "신호가 꺼지면 이번 따라 말하기를 다시 시작합니다." else null,
            modifier = modifier
        )
        return
    }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(title, fontSize = 30.sp)
        courseMinutes?.let {
            Text("출근 코스 · ${it}분 계획", fontSize = 24.sp, color = Color(0xFF9A9FA5))
        }
        Text(targetText, fontSize = 36.sp, lineHeight = 48.sp)
        targetTranslation?.let { Text(it, fontSize = 26.sp, lineHeight = 36.sp) }
        Text("따라 말하기 $completed / 2", fontSize = 28.sp, color = Color(0xFFFF9138))
        SpeakingWaveform(targetLevel, active, lastInputAt)
        if (active && !inputReceived) {
            Text("마이크 연결 확인 중", fontSize = 18.sp, color = Color(0xFFB2B5B8))
        }
        Text(status, fontSize = 24.sp, lineHeight = 34.sp)
        if (!wantsSession) {
            Button(enabled = ready && !blocked, onClick = {
                if (completed >= 2) {
                    completed = 0
                    introDone = false
                }
                wantsSession = true
            }) { Text(if (completed == 1) "두 번째 이어하기" else "다시 시작", fontSize = 24.sp) }
        }
        if (!courseMode) {
            OutlinedButton(enabled = wantsSession, onClick = { wantsSession = false; status = "중지했어요." }) {
                Text("중지", fontSize = 24.sp)
            }
            TextButton(onClick = onClose) { Text("준비 화면으로 돌아가기", fontSize = 24.sp) }
        }
    }
}

/** Only the drawing phase observes the history tick, keeping parent UI idle. */
@Composable
private fun SpeakingWaveform(level: State<Float>, active: Boolean, lastInputAt: State<Long>) {
    val history = remember { SpeakingWaveHistory() }
    val revision = remember { mutableIntStateOf(0) }
    val path = remember { Path() }
    LaunchedEffect(active) {
        history.clear()
        revision.intValue += 1
        var amplitude = 0f
        var phase = 0f
        var previous = SystemClock.elapsedRealtime()
        while (active) {
            delay(33)
            val now = SystemClock.elapsedRealtime()
            val dt = ((now - previous) / 1000f).coerceIn(0f, 0.1f)
            previous = now
            val fresh = now - lastInputAt.value < 1_000L
            val target = if (fresh) level.value else 0f
            val tau = if (target > amplitude) 0.1f else 0.18f
            amplitude += (target - amplitude) * (1f - exp(-dt / tau))
            phase = (phase + dt * 2f * PI.toFloat() * 3.5f) % (2f * PI.toFloat())
            history.push(if (amplitude < 0.002f) 0f else amplitude * sin(phase))
            revision.intValue += 1
        }
    }
    Canvas(Modifier.fillMaxWidth().height(110.dp)
        .background(Color(0xFF1C2429), RoundedCornerShape(16.dp))) {
        // Subscribing here invalidates drawing, not composition/layout.
        @Suppress("UNUSED_VARIABLE") val observedRevision = revision.intValue
        val center = size.height / 2f
        val scale = size.height * 0.40f
        val step = size.width / (history.size - 1)
        path.reset()
        path.moveTo(0f, center - history.valueAt(0) * scale)
        // Midpoint quadratic curves round off the sampled trace.
        for (i in 0 until history.size - 1) {
            val x = i * step
            val y = center - history.valueAt(i) * scale
            val nextY = center - history.valueAt(i + 1) * scale
            path.quadraticTo(x, y, x + step / 2f, (y + nextY) / 2f)
        }
        path.lineTo(size.width, center - history.valueAt(history.size - 1) * scale)
        drawPath(path, Color(0xFFFF9138), style = Stroke(width = 2.8.dp.toPx()))
    }
}
