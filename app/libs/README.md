# Samsung Health Data SDK AAR

이 폴더에 Samsung Health Data SDK의 `.aar` 파일을 넣으면 `app/build.gradle.kts`가 자동으로 인식합니다
(`fileTree(dir = "libs", include = listOf("*.aar"))`).

## 받는 법

1. https://developer.samsung.com/health/data/overview.html 에서 Samsung 계정으로 로그인 후 SDK zip 다운로드
   (로그인/세션이 필요해서 자동 다운로드가 안 됨 — 직접 받아야 함)
2. zip 안의 `.aar` 파일(예: `health-data-api-x.x.x.aar` — 버전에 따라 파일명이 다를 수 있음, 압축 풀어서 실제 파일명 확인)을 이 폴더(`app/libs/`)에 복사
3. Android Studio에서 Gradle Sync 다시 실행

## 참고

- 2026-09-18 기준 공식 가이드 문서의 코드 예제는 파일명을 `health-data-api-1.0.0.aar`로 표기하고 있으나,
  릴리스 노트 최신 버전은 v1.1.0(2026-03-12)이라 실제 파일명이 다를 수 있음 — 다운로드한 실물 파일명을 기준으로 확인할 것.
- `app/build.gradle.kts`는 파일명을 하드코딩하지 않고 `*.aar` 패턴으로 잡기 때문에, 파일명이 달라도 문제 없음.
