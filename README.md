# Deeplayer

온디바이스 가사-오디오 동기화 음악 플레이어

기기 내에서 ML 추론과 CTC Forced Alignment를 수행하여, 네트워크 없이도 가사를 오디오에 자동 동기화합니다.

## 주요 기능

- 오디오-가사 자동 동기화 (Whisper tiny + CTC Forced Alignment)
- 실시간 가사 하이라이트 재생
- 완전한 온디바이스 처리 (네트워크 불필요)
- 한국어/영어 가사 지원

## 아키텍처

```
:app                           메인 앱 (Hilt, Compose)
:core:contracts                공유 인터페이스/모델
:core:player                   음악 플레이어 (Media3, Room)
:feature:audio-preprocessor    오디오 전처리 (NDK/C++, FFmpeg)
:feature:inference-engine      ML 추론 (LiteRT, ONNX Runtime)
:feature:lyrics-aligner        CTC 강제 정렬 (G2P, Viterbi DP)
:feature:alignment-orchestrator 통합 오케스트레이션 (WorkManager)
:feature:lyrics-ui             가사 UI (Compose)
```

## 요구 사항

- Android API 26+ (arm64-v8a, armeabi-v7a)
- JDK 17
- Gradle 8.7+

## 빌드

```bash
./gradlew assembleDebug
```

## 테스트

```bash
# 단위 테스트
./gradlew test

# 성능 테스트 포함
./gradlew test -PincludePerformanceTests=true
```

## 코드 품질

```bash
# 포맷팅 (ktfmt Google style)
./gradlew ktfmtFormat

# 린트 (detekt)
./gradlew detekt
```

## 기술 스택

- **UI**: Jetpack Compose, Material 3
- **DI**: Hilt
- **Player**: Media3 (ExoPlayer)
- **DB**: Room
- **ML**: Whisper tiny INT8 (whisper.cpp), LiteRT, ONNX Runtime
- **전처리**: NDK/C++ (FFmpeg, NEON intrinsics)
- **정렬**: CTC Forced Alignment (Viterbi DP)
- **G2P**: 한국어 규칙 기반 (Unicode 자모 분해), 영어 CMU 사전
- **백그라운드**: WorkManager

## 라이선스

이 프로젝트는 Apache License 2.0에 따라 배포됩니다. 자세한 내용은 [LICENSE](LICENSE) 파일을 참조하세요.
