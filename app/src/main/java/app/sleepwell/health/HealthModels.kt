package app.sleepwell.health

// 모든 시각은 Long = epoch milliseconds (1970-01-01 UTC 부터의 밀리초)
// 사람이 읽는 형식으로는 export 시점에만 변환한다.

// ──────────────────────────────────────────────
// 기기 정보 — "이 데이터가 Watch 5에서 나온 게 맞나" 판단 근거
// ──────────────────────────────────────────────
data class DeviceInfo(
    val sourceName: String,      // 예: "Galaxy Watch5", "SM-R900"
    val deviceType: DeviceType,
    val rawSourceId: String?     // SDK가 준 원본 식별자. 필터링 근거를 버리지 않기 위해 보존
)

enum class DeviceType { WATCH, PHONE, MANUAL_INPUT, UNKNOWN }

// ──────────────────────────────────────────────
// 센서 1건씩
// ──────────────────────────────────────────────
data class HeartRateSample(
    val timeMs: Long,
    val bpm: Float,
    val device: DeviceInfo? = null
)

data class StepSample(
    val startMs: Long,           // 걸음은 "구간 합계"로 오는 경우가 많아 start/end를 둘 다 둠
    val endMs: Long,
    val count: Int,
    val device: DeviceInfo? = null
)

// ──────────────────────────────────────────────
// 수면 — 팀 공통 코드 0~4 고정
// ──────────────────────────────────────────────
enum class SleepStage(val code: Int, val label: String) {
    WAKE(0, "W"),
    N1(1, "N1"),
    N2(2, "N2"),
    N3(3, "N3"),
    REM(4, "REM"),
    UNKNOWN(-1, "UNKNOWN");      // 매핑 실패 시. export 시 평가에서 자동 제외됨

    companion object {
        fun fromCode(c: Int) = entries.firstOrNull { it.code == c } ?: UNKNOWN
    }
}

/** 수면 단계가 유지된 한 구간 */
data class SleepStageSegment(
    val startMs: Long,
    val endMs: Long,
    val stage: SleepStage
)

/** 하룻밤 = 세션 1개 */
data class SleepSession(
    val sessionId: String,
    val startMs: Long,
    val endMs: Long,
    val stages: List<SleepStageSegment>,   // 단계 정보가 없으면 빈 리스트 (수면/각성만 있는 경우)
    val device: DeviceInfo? = null
)

// ──────────────────────────────────────────────
// 조회 조건
// ──────────────────────────────────────────────
data class HealthQuery(
    val startMs: Long,
    val endMs: Long,
    val watchOnly: Boolean = true   // Galaxy Watch 소스만. 폰 자체 기록/수동입력 제외
)

// ──────────────────────────────────────────────
// 결과 타입 — 실패 이유를 값으로 표현
// ──────────────────────────────────────────────
sealed interface HealthResult<out T> {
    data class Success<T>(val data: T) : HealthResult<T>
    data object PermissionDenied : HealthResult<Nothing>
    data class Unavailable(val reason: String) : HealthResult<Nothing>  // 앱 미설치 / 항목 미지원
    data class Failure(val error: Throwable) : HealthResult<Nothing>
}
