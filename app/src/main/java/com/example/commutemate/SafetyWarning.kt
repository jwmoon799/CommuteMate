package com.example.commutemate

/** These are explicit demo events, not classifications of driver camera data. */
enum class SafetyWarning(val wireName: String, val message: String, val asset: String, val priority: Int) {
    LEAD_DEPARTURE("lead_departure", "앞차가 출발했습니다. 전방을 확인하세요.", "safety/lead_departure.wav", 1),
    ATTENTION("attention", "전방을 주시하세요.", "safety/attention.wav", 2);
    companion object { fun fromWire(value: String) = entries.find { it.wireName == value } }
}

/** Repeated signals cannot repeatedly interrupt speech; attention takes precedence. */
class SafetyEventGate {
    private var active: SafetyWarning? = null
    private var lastKind: SafetyWarning? = null
    private var lastAt = Long.MIN_VALUE
    fun accept(kind: SafetyWarning, now: Long): Boolean {
        if (active != null && kind.priority <= active!!.priority) return false
        if (active == null && kind == lastKind && lastAt != Long.MIN_VALUE && now - lastAt < 5_000) return false
        active = kind; lastKind = kind; lastAt = now
        return true
    }
    fun clear(now: Long) { active = null; lastAt = now }
}
