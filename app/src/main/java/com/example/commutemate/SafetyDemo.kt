package com.example.commutemate

import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.util.Log
import ai.pleos.playground.vehicle.Vehicle
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout

/** Read-only diagnostic. A generic DRIVER_ATTENTION telltale is not a HUD message. */
fun inspectSafetyAccess(context: Context, vehicle: Vehicle, report: (String) -> Unit) {
    val permission = "pleos.car.permission.PRIVATE_CAR_SAFETY"
    if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
        report("특별 권한 없음 · 실제 경고 미연결 · 시연 입력 사용")
        Log.i("CommuteMateSafetyProbe", "PRIVATE_CAR_SAFETY denied; no dedicated lead departure or eye-gaze API in Vehicle 2.3.2")
        return
    }
    try {
        vehicle.getSafety().checkTelltalesCapabilities({ config ->
            Log.i("CommuteMateSafetyProbe", "telltales capability=$config")
            vehicle.getSafety().getTelltalesInfo({ info ->
                Log.i("CommuteMateSafetyProbe", "read-only telltales=$info; specific HUD event mapping unverified; demo only")
                report("경고등 조회 가능 · 구체 경고 구분 미확인 · 시연 입력 사용")
            }, { error -> report("경고 조회 실패 · 시연 입력 사용"); Log.w("CommuteMateSafetyProbe", "read failed", error) })
        }, { error -> report("경고 지원 확인 실패 · 시연 입력 사용"); Log.w("CommuteMateSafetyProbe", "capability failed", error) })
    } catch (e: Exception) {
        report("차량 경고 접근 불가 · 시연 입력 사용")
        Log.w("CommuteMateSafetyProbe", "probe failed", e)
    }
}

data class SafetyDemoSnapshot(val cursor: String, val kind: SafetyWarning?)
fun fetchSafetyDemo(): SafetyDemoSnapshot {
    val connection = URL("http://127.0.0.1:8091/api/safety-state").openConnection() as HttpURLConnection
    connection.connectTimeout = 500; connection.readTimeout = 500
    try {
        check(connection.responseCode == 200)
        val root = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        check(root.getString("source") == "demo")
        return SafetyDemoSnapshot(root.optString("cursor"), root.optJSONObject("event")?.let {
            SafetyWarning.fromWire(it.getString("kind"))
        })
    } finally { connection.disconnect() }
}

/** Only one short local warning plays at a time; no network TTS latency. */
class SafetyCuePlayer(private val context: Context) {
    private var player: MediaPlayer? = null
    private var completed: CompletableDeferred<Unit>? = null
    fun stop() {
        completed?.complete(Unit); completed = null
        player?.let { p -> runCatching { p.stop() }; p.release() }; player = null
    }
    suspend fun play(warning: SafetyWarning) {
        stop()
        val done = CompletableDeferred<Unit>(); completed = done
        val p = MediaPlayer(); player = p
        try {
            context.assets.openFd(warning.asset).use { p.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            p.setOnCompletionListener { done.complete(Unit) }
            p.setOnErrorListener { _, what, extra -> done.completeExceptionally(IllegalStateException("$what/$extra")); true }
            p.prepare(); p.start()
            withTimeout(12_000) { done.await() }
        } finally { if (player === p) stop() }
    }
}
