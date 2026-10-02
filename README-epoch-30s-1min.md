# 학술제 이번 주 작업 — 정채윤

**일자:** 2026-09-27 | **주제:** Two-Process 모델 입력 epoch 30초 → 1분

---

## 이번 주에 한 것

Two-Process 모델에 들어가는 epoch 타임라인을 30초에서 **1분으로 바꾸는 버전**을 새로 만들었다.
**30초 버전은 한 글자도 안 건드리고 그대로 동결**해 뒀다. 두 버전 다 실제로 빌드해서
같은 데이터로 돌려보고 숫자가 맞는 것을 확인했다.

---

## 폴더 안내

```
학술제 이번주 작업/
├── README.md                                   ← 이 파일
├── docs/
│   ├── 01_epoch-30s-1min-변경사항.md            무엇을 왜 바꿨는지
│   ├── 02_검증결과-30s-vs-1min.md              실제 숫자로 확인한 내용 + 한계
│   └── 03_다음주-확인필요사항-정채윤.md          소빈·서윤·혜지한테 물어볼 것
├── src-epoch-30s/app/sleepwell/health/          30초 버전 (동결, 현행과 동일)
├── src-epoch-1min/app/sleepwell/health/         1분 버전 (신규, EpochInterval.kt 추가)
└── tools/verify/                                컴파일·비교 검증 하네스 (앱 빌드 제외)
```

**읽는 순서:** `docs/01` → `docs/02` → 필요하면 `docs/03`.

---

## 결론부터

| | 30초 | 1분 |
|---|---|---|
| 7일 epoch 수 | 20,160 | 10,080 (정확히 1/2) |
| 현재 S | 0.289993 | 0.289342 (0.22% 차이) |
| 현재 propensity | 0.410746 | 0.411904 |
| S 최댓값 | 0.999755 | **0.999755 (완전히 동일)** |
| 세션 수 | 37 | 37 (동일) |
| 그래프 표시 | — | **사실상 동일** (전달 간격 10~15분이라) |

**"1분으로 바꿔도 결과가 같은데 계산량은 절반"** 이 확인됐다.
epoch 간격만 바뀌고 물리는 안 바뀌는 게 S 최댓값이 소수점 6자리까지 같다는 것으로 확인된다.

그래프는 눈에 띄게 달라지지 않는다. 눈에 띄는 차이는 epoch 수(전송량)와 계산 시간이고,
1분이면 충분하다. 단점은 **1분 이하 짧은 각성 구간이 1분으로 뭉개진다**는 것.
자세한 내용은 `docs/01` 2절.

---

## 코드에서 실제로 바뀐 것 (4곳)

1. **`EpochInterval.kt` 신규** — `SEC30` / `MIN1` enum. 하드코딩 `30_000L` 을 한 곳에 모았다.
2. **`SleepEpochConverter.toEpochTimeline()`** — epoch 간격을 인자로 받게 변경했다. 기본값 30초라
   기존 호출은 그대로 동작한다. epoch 경계 정렬 옵션 추가(기본 on).
3. **`SleepAnalysisPipeline.applyDirectInput()`** — 하드코딩 `30_000L` 제거. **이게 핵심.**
   이걸 안 고치면 1분 모델에서 사용자가 입력한 취침·기상 시각이 절반 크기로만 반영된다.
   겉보기엔 멀쩡하게 돌아가서 조용히 틀린다.
4. **`AnalysisResult`** — `epochIntervalMs` / `epochIntervalLabel` / `inputPeriodStartMs` /
   `inputPeriodEndMs` 4개 필드 추가. **모두 기본값 있음** → 소빈 화면 코드 수정 불필요.

`TwoProcessModel.kt` 와 `CsvFileSource.kt` 는 **한 줄도 안 바꿨다.**
둘 다 epoch 간격과 무관하게 동작한다. (S 최댓값이 동일한 이유가 이것이다.)

---

## 바로 확인해야 할 것

**1. 모델 입력만 1분, 앱 export 격자는 30초 유지로 뒀다.**
export 격자(`EPOCH_MS`)는 소빈 파일이고 `epochIdx` 가 시각 좌표라서 30초(Sleep-EDF 표준)와
묶여 있다. 여기까지 바꾸려면 소빈이 `HealthModels.kt` / `EpochAggregator` 를 함께 손봐야 한다.
근거는 `docs/01` 6절.

**2. 합의 문서를 아직 안 고쳤다.** `interface-schema-전처리-프론트.md` 에 "30초 epoch"로
합의돼 있다. 소빈이 1분 확정을 해야 문서와 구현이 한 방향을 본다. → `docs/03` 1-1

**3. 1분 모델 성능을 아직 못 잤다.** Sleep-EDF 정답이 30초라 **1분 → 30초 역집계** 규칙이
먼저 정해져야 한다. 이건 소빈 #2 파이프라인 작업과 겹친다. → `docs/03` 1-3

**4. Android 빌드는 확인 못 했다.** 이 폴더에 앱 프로젝트가 없어서 kotlinc 개별 컴파일만 했다.
앱에 붙일 때 Gradle 빌드로 한 번 더 확인이 필요하다. → `docs/02` 5절

---

## 검증 재현

```powershell
powershell -ExecutionPolicy Bypass -File tools\verify\verify-epoch.ps1
```

kotlinc 를 컴파일러 jar 로 직접 실행한다(Android Studio bundled). 결과는
`tools/verify/out/result-*.txt` 에 쌓인다. 산출물은 커밋 대상이 아니다.

> `verify-epoch.ps1` 는 **UTF-8(BOM)** 으로 저장돼 있어야 한다. 메모장 기본 UTF-8 저장은
> 깨진다. 이미 BOM 으로 맞춰 뒀다.

---

## GitHub

https://github.com/iuniion3287-web/SleepWell

| 저장소 경로 | 내용 |
|---|---|
| `src/app/sleepwell/health/` | 30초 버전 (기존 경로, 그대로 — 소빈 화면이 이쪽을 본다) |
| `src-epoch-1min/app/sleepwell/health/` | 1분 버전 (신규) |
| `docs/epoch-30s-1min/` | 이번 주 문서 3종 |

1분으로 실제 전환하려면 `src/` 를 `src-epoch-1min/` 내용으로 교체하면 된다.
되돌리기는 git 커밋으로 충분하다(30초 버전이 그대로 남아 있다).