package app.sleepwell.health

import android.app.Activity
import com.samsung.android.sdk.health.data.HealthDataService
import com.samsung.android.sdk.health.data.HealthDataStore
import com.samsung.android.sdk.health.data.data.HealthDataPoint
import com.samsung.android.sdk.health.data.device.DeviceGroup
import com.samsung.android.sdk.health.data.error.AuthorizationException
import com.samsung.android.sdk.health.data.error.ErrorCode
import com.samsung.android.sdk.health.data.error.HealthDataException
import com.samsung.android.sdk.health.data.permission.AccessType
import com.samsung.android.sdk.health.data.permission.Permission
import com.samsung.android.sdk.health.data.request.AggregateSourceFilter
import com.samsung.android.sdk.health.data.request.DataType
import com.samsung.android.sdk.health.data.request.DataTypes
import com.samsung.android.sdk.health.data.request.LocalTimeFilter
import com.samsung.android.sdk.health.data.request.LocalTimeGroup
import com.samsung.android.sdk.health.data.request.LocalTimeGroupUnit
import com.samsung.android.sdk.health.data.request.ReadSourceFilter
import java.time.LocalDateTime
import java.time.ZoneId
import com.samsung.android.sdk.health.data.data.DataSource as SdkDataSource
import com.samsung.android.sdk.health.data.data.entries.HeartRate as SdkHeartRate
import com.samsung.android.sdk.health.data.data.entries.SleepSession as SdkSleepSession

/**
 * 채윤 담당: 실제 Samsung Health Data SDK를 [HealthDataSource] 공통 타입으로 번역하는 어댑터.
 *
 * 권한 요청 팝업을 띄우려면 SDK가 Activity를 요구하므로(Context만으로는 불가),
 * 이 클래스는 Context가 아니라 Activity를 받는다.
 */
class SamsungHealthSource(private val activity: Activity) : HealthDataSource {

    override val name: String = "SamsungHealth"

    private val store: HealthDataStore by lazy { HealthDataService.getStore(activity) }

    private val requiredPermissions: Set<Permission> = setOf(
        Permission.of(DataTypes.SLEEP, AccessType.READ),
        Permission.of(DataTypes.HEART_RATE, AccessType.READ),
        Permission.of(DataTypes.STEPS, AccessType.READ)
    )

    override suspend fun isAvailable(): Boolean = try {
        // 권한 없이도 응답이 오는지로 "Samsung Health 연결 가능한가"만 확인 (ping 용도)
        store.getGrantedPermissions(emptySet())
        true
    } catch (e: HealthDataException) {
        e.errorCode !in UNAVAILABLE_ERROR_CODES
    }

    override suspend fun hasPermissions(): Boolean = try {
        store.getGrantedPermissions(requiredPermissions).containsAll(requiredPermissions)
    } catch (e: HealthDataException) {
        false
    }

    override suspend fun requestPermissions(): HealthResult<Unit> = try {
        val granted = store.requestPermissions(requiredPermissions, activity)
        if (granted.containsAll(requiredPermissions)) {
            HealthResult.Success(Unit)
        } else {
            HealthResult.PermissionDenied
        }
    } catch (e: AuthorizationException) {
        HealthResult.PermissionDenied
    } catch (e: HealthDataException) {
        e.toUnavailableOrFailure()
    }

    override suspend fun readHeartRate(q: HealthQuery): HealthResult<List<HeartRateSample>> = readGuarded {
        val builder = DataTypes.HEART_RATE.readDataRequestBuilder
            .setLocalTimeFilter(q.toLocalTimeFilter())
        if (q.watchOnly) builder.setSourceFilter(ReadSourceFilter.fromDeviceType(DeviceGroup.WATCH))

        store.readData(builder.build()).dataList.flatMap { point ->
            val device = point.dataSource.toDeviceInfo(q.watchOnly)
            point.getValue(DataType.HeartRateType.SERIES_DATA).orEmpty().map { sample: SdkHeartRate ->
                HeartRateSample(
                    timeMs = sample.startTime.toEpochMilli(),
                    bpm = sample.heartRate,
                    device = device
                )
            }
        }
    }

    override suspend fun readSteps(q: HealthQuery): HealthResult<List<StepSample>> = readGuarded {
        // Steps는 SDK에서 집계(Aggregate) 전용이라 point-in-time 원본이 없음.
        // LocalTimeGroupUnit 최소 단위가 1분이라(30초 불가), 1분 버킷으로 받아온다.
        // watchOnly는 AggregateSourceFilter가 기기 종류 구분을 지원하지 않아 fromPlatform()(수동입력 제외)로만 근사한다.
        val builder = DataType.StepsType.TOTAL.requestBuilder
            .setLocalTimeFilterWithGroup(q.toLocalTimeFilter(), LocalTimeGroup.of(LocalTimeGroupUnit.MINUTELY, 1))
        if (q.watchOnly) builder.setSourceFilter(AggregateSourceFilter.fromPlatform())

        store.aggregateData(builder.build()).dataList.map { bucket ->
            StepSample(
                startMs = bucket.startTime.toEpochMilli(),
                endMs = bucket.endTime.toEpochMilli(),
                count = (bucket.value ?: 0L).toInt(),
                device = null // 집계 결과라 개별 device 정보가 없음
            )
        }
    }

    override suspend fun readSleepSessions(q: HealthQuery): HealthResult<List<SleepSession>> = readGuarded {
        val builder = DataTypes.SLEEP.readDataRequestBuilder
            .setLocalTimeFilter(q.toLocalTimeFilter())
        if (q.watchOnly) builder.setSourceFilter(ReadSourceFilter.fromDeviceType(DeviceGroup.WATCH))

        store.readData(builder.build()).dataList.flatMap { point ->
            point.toSleepSessions(q.watchOnly)
        }
    }

    private fun HealthDataPoint.toSleepSessions(isWatchFiltered: Boolean): List<SleepSession> {
        val device = dataSource.toDeviceInfo(isWatchFiltered)
        val sdkSessions = getValue(DataType.SleepType.SESSIONS).orEmpty()
        return sdkSessions.mapIndexed { index, sdkSession: SdkSleepSession ->
            SleepSession(
                sessionId = "${uid}_$index",
                startMs = sdkSession.startTime.toEpochMilli(),
                endMs = sdkSession.endTime.toEpochMilli(),
                stages = sdkSession.stages.orEmpty().map { stage ->
                    SleepStageSegment(
                        startMs = stage.startTime.toEpochMilli(),
                        endMs = stage.endTime.toEpochMilli(),
                        stage = stage.stage.toSleepStage()
                    )
                },
                device = device
            )
        }
    }

    private fun DataType.SleepType.StageType.toSleepStage(): SleepStage = when (this) {
        // Samsung Health SDK는 4단계(Wake/Light/Deep/REM)만 제공, N1/N2 구분 없음.
        // 팀 합의(HealthDataSource_인터페이스_합의안_v1 §5.1): LIGHT -> N2로 매핑.
        DataType.SleepType.StageType.AWAKE -> SleepStage.WAKE
        DataType.SleepType.StageType.LIGHT -> SleepStage.N2
        DataType.SleepType.StageType.DEEP -> SleepStage.N3
        DataType.SleepType.StageType.REM -> SleepStage.REM
        DataType.SleepType.StageType.UNDEFINED -> SleepStage.UNKNOWN
    }

    private fun SdkDataSource?.toDeviceInfo(isWatchFiltered: Boolean): DeviceInfo? {
        if (this == null) return null
        return DeviceInfo(
            sourceName = appId,
            // 소스 필터를 워치로 걸었을 때만 WATCH로 확정 지음. 개별 기기 모델명 조회는 2차 범위.
            deviceType = if (isWatchFiltered) DeviceType.WATCH else DeviceType.UNKNOWN,
            rawSourceId = deviceId
        )
    }

    private fun HealthQuery.toLocalTimeFilter(): LocalTimeFilter {
        val zone = ZoneId.systemDefault()
        val start = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(startMs), zone)
        val end = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(endMs), zone)
        return LocalTimeFilter.of(start, end)
    }

    private inline fun <T> readGuarded(block: () -> List<T>): HealthResult<List<T>> = try {
        HealthResult.Success(block())
    } catch (e: AuthorizationException) {
        HealthResult.PermissionDenied
    } catch (e: HealthDataException) {
        e.toUnavailableOrFailure()
    }

    private fun <T> HealthDataException.toUnavailableOrFailure(): HealthResult<T> =
        if (errorCode in UNAVAILABLE_ERROR_CODES) {
            HealthResult.Unavailable(errorMessage ?: "Samsung Health를 사용할 수 없습니다 (code=$errorCode)")
        } else {
            HealthResult.Failure(this)
        }

    companion object {
        private val UNAVAILABLE_ERROR_CODES = setOf(
            ErrorCode.ERR_PLATFORM_NOT_INSTALLED,
            ErrorCode.ERR_OLD_VERSION_PLATFORM,
            ErrorCode.ERR_PLATFORM_DISABLED,
            ErrorCode.ERR_PLATFORM_NOT_INITIALIZED,
            ErrorCode.ERR_PLATFORM_DISCONNECTED
        )
    }
}
