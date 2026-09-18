package app.sleepwell.health

interface HealthDataSource {
    /** 화면에 "지금 어느 소스를 보고 있는지" 표시용. "SamsungHealth" 또는 "Fake" */
    val name: String

    /** 이 소스를 쓸 수 있는 환경인가 (Samsung Health 앱 설치·버전 등). Fake는 항상 true */
    suspend fun isAvailable(): Boolean

    suspend fun hasPermissions(): Boolean
    suspend fun requestPermissions(): HealthResult<Unit>

    suspend fun readHeartRate(q: HealthQuery): HealthResult<List<HeartRateSample>>
    suspend fun readSteps(q: HealthQuery): HealthResult<List<StepSample>>
    suspend fun readSleepSessions(q: HealthQuery): HealthResult<List<SleepSession>>
}
