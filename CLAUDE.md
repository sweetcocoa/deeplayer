# Deeplayer - 온디바이스 가사-오디오 동기화 음악 플레이어

## 프로젝트 구조

```
:app                              - 메인 앱 (Hilt, Compose)
:core:contracts                   - 공유 인터페이스/모델 (의존성 없음)
:core:player                      - 음악 플레이어 코어 (Media3, Room)
:feature:audio-preprocessor       - 오디오 전처리 (NDK/C++, FFmpeg)
:feature:inference-engine          - ML 추론 (LiteRT, ONNX Runtime)
:feature:lyrics-aligner            - CTC 강제 정렬 (G2P, Viterbi DP)
:feature:alignment-orchestrator    - 통합 오케스트레이션 (WorkManager, Room)
:feature:lyrics-ui                 - 가사 UI (Compose)
```

## 초기 설정

```bash
# Gradle wrapper jar가 없으면 먼저 생성 (시스템에 Gradle 8.12 필요)
gradle wrapper --gradle-version 8.12

# 또는 JAVA_HOME 설정
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home
```

## 빌드

```bash
# 전체 빌드
./gradlew assembleDebug

# 특정 모듈 빌드
./gradlew :feature:lyrics-aligner:assembleDebug
```

## 테스트

```bash
# 전체 단위 테스트
./gradlew test

# 특정 모듈 테스트
./gradlew :feature:lyrics-aligner:test

# 성능 테스트 포함
./gradlew test -PincludePerformanceTests=true
```

## 코드 품질

```bash
# 포맷팅 (ktfmt Google style, 2-space indent)
./gradlew ktfmtFormat

# 포맷 검증
./gradlew ktfmtCheck

# 린트 (detekt)
./gradlew detekt
```

## 코드 스타일 규칙

- **포매터**: ktfmt Google style (2-space indent, 100 column limit)
- **린터**: detekt (gradle/detekt/detekt.yml 참조)
- 모든 Kotlin 파일은 ktfmt Google style을 따른다
- Compose 함수는 PascalCase
- 패키지: `com.deeplayer.<module-path>`

## 의존성 관리

모든 라이브러리 버전은 `gradle/libs.versions.toml`에서 중앙 관리.
새 의존성 추가 시 반드시 version catalog를 통해 추가할 것.

## 공유 인터페이스 (core:contracts)

모든 모듈 간 인터페이스와 데이터 클래스는 `:core:contracts` 모듈에 정의.
구현 모듈은 계약(contract)에만 의존하고, 다른 구현 모듈에 직접 의존하지 않는다.
각 구현 모듈은 반드시 Fake 구현을 함께 제공해야 한다.

## 핵심 설계 결정

- **ML 모델**: Whisper tiny INT8 (whisper.cpp) - MVP
- **G2P**: 한국어는 규칙 기반 (Unicode 자모 분해), 영어는 CMU 사전
- **정렬**: CTC Forced Alignment (Viterbi/DP 기반)
- **전처리**: NDK/C++ (FFmpeg, NEON intrinsics)
- **타겟**: Android API 26+, arm64-v8a + armeabi-v7a
