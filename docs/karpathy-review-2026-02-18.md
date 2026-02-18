# Karpathy Guidelines 코드 리뷰 결과

**날짜**: 2026-02-18
**범위**: 전체 코드베이스 (8개 모듈)
**기준**: Karpathy Guidelines (Simplicity First, Surgical Changes, Think Before Coding, Goal-Driven)

---

## 🔴 Critical (즉시 수정 필요)

### 1. C++ 메모리 안전성 - `audio_decoder.cpp`

**버퍼 오버플로우 위험** (라인 113-122): `swr_convert` 호출 시 `&out_buf` (포인터의 주소) 대신 배열 포인터를 전달해야 함

```cpp
// 현재 (위험)
int out_samples = swr_convert(swr_ctx, &out_buf, max_out_samples, ...);

// 수정 필요
uint8_t* out_buffers[] = {out_buf};
int out_samples = swr_convert(swr_ctx, out_buffers, max_out_samples, ...);
```

**RAII 미적용** (라인 23-48): FFmpeg 리소스(`AVFormatContext`, `AVCodecContext`, `SwrContext`)가 예외 발생 시 해제되지 않음

**JNI 리소스 누수** (`audio_preprocessor_jni.cpp` 라인 35-43): `GetStringUTFChars` 후 `std::string` 생성 중 예외 시 `ReleaseStringUTFChars` 미호출

### 2. InferenceEngine 타입 안전성 부재

```kotlin
// InferenceEngine.kt
fun run(input: Map<String, Any>): Map<String, Any>  // 완전히 타입 없음
```

- 런타임 `ClassCastException` 위험. Whisper 전용 프로젝트인데 과도하게 generic한 인터페이스
- `LiteRtInferenceEngine.kt` 라인 63: `input.values.toList()[index]` → `IndexOutOfBoundsException` 위험
- `OnnxInferenceEngine.kt` 라인 113: `@Suppress("UNCHECKED_CAST")` → 캐스트 실패 시 크래시

### 3. MappedByteBuffer 누수 - `LiteRtInferenceEngine.kt` (라인 31-46)

모델 로드 실패 시 mmap된 ByteBuffer가 해제되지 않음. try-finally 필요.

### 4. CTC 신뢰도 계산 오류 - `CtcForcedAligner.kt` (라인 179, 205)

```kotlin
val avgConf = kotlin.math.exp(sumLogProb / frameCount)
```

- `sumLogProb`은 큰 음수 → `exp(큰 음수)` ≈ 0 → 모든 confidence가 0에 수렴
- 수치적으로 의미 있는 결과를 내지 못함

---

## 🟠 High (설계 결함) — ✅ 수정 완료

> #5, #6, #7은 Critical 수정 단계에서 이미 해결. #8, #9 추가 수정 완료.

### 5. PlaybackState 중복 상태 — ✅ Critical에서 수정

`enum class PlaybackStatus { STOPPED, PLAYING, PAUSED }` 도입, `isPlaying`/`isPaused`는 computed property로 변경.

### 6. `currentPositionMs` 중복 — ✅ Critical에서 수정

`PlayerService` 인터페이스에서 `currentPositionMs: StateFlow<Long>` 제거. `playbackState.positionMs`로 단일화.

### 7. `resolveTrack()` 항상 null — ✅ Critical에서 수정

`TrackDao`를 Hilt 생성자 주입하고, `resolveTrack()`이 실제 DB 조회하도록 구현.

### 8. alignment-orchestrator 의존성 위반 — ✅ 수정

`build.gradle.kts`에서 구현 모듈 의존성(`audio-preprocessor`, `inference-engine`, `lyrics-aligner`) 제거. `:core:contracts`만 의존하도록 변경.

### 9. LyricsAlignerImpl DI 미사용 — ✅ 수정

`KoreanG2P`, `EnglishG2P`, `CodeSwitchDetector`를 생성자 주입으로 변경. 각 클래스에 `@Inject constructor()` 추가. 순수 연산 유틸(CTC, TimestampConverter 등)은 내부 생성 유지 (교체 필요 없음). 미사용 `WordInfo.phonemeSymbols` 필드 제거.

---

## 🟡 Medium (개선 권장) — ✅ 수정 완료

> Medium 이슈 #10, #12, #15, #16, #17 수정 완료. #11(FloatArray 차원), #13(KoreanG2P 매직넘버), #14(EnglishG2P 사전 크기)는 현 단계에서 과도한 추상화로 판단하여 보류.

### 10. `language: String` 타입 불안전

`AlignmentOrchestrator`, `LyricsAligner` 모두 `language: String = "ko"` 사용. `enum class Language { KO, EN, MIXED }` 권장.

### 11. FloatArray 차원 불명확

`AudioPreprocessor.extractMelSpectrogram()`, `LyricsAligner.align()`의 `phonemeProbabilities` 모두 1D FloatArray로 선언되었지만 실제로는 2D 데이터. 래퍼 클래스로 차원 명시 필요.

### 12. segmentPcm 중복 코드

`NativeAudioPreprocessor`와 `FakeAudioPreprocessor`에 동일한 `segmentPcm` 로직이 복사됨. 공유 유틸리티로 추출 필요.

### 13. KoreanG2P 매직 숫자

유니코드 리터럴(`'\u3131'`, `'\u313A'` 등)이 170줄에 걸쳐 하드코딩. 명명된 상수 또는 lookup map 사용 권장.

### 14. EnglishG2P CMU 사전 부족

~60개 단어만 포함. CLAUDE.md에 "영어는 CMU 사전"이라 했으나 실질적으로 rule-based fallback에 의존.

### 15. SyncedLyricsView 문제들

- `onOffsetChange` 파라미터 선언 후 미사용 (죽은 코드)
- `itemsIndexed`에 key 미지정 → 리스트 변경 시 불필요한 recomposition
- 커스텀 `lerpColor` 구현 (Compose에 이미 `lerp` 존재)

### 16. ChunkBoundaryConnector DRY 위반

단어/라인 오프셋 적용 로직이 3회 반복. Helper 함수로 추출 가능.

### 17. PlayerServiceImplTest 파일명 오도

`PlayerServiceImplTest`인데 실제로는 `FakePlayerService`만 테스트. 실제 `PlayerServiceImpl` 통합 테스트 부재.

### 18. OnnxInferenceEngine Logger 비일관성

`LiteRtInferenceEngine`은 Android `Log`, `OnnxInferenceEngine`은 `java.util.logging.Logger`를 매번 새로 생성. 통일 필요.

---

## 🟢 Low (미세 개선) — ✅ 수정 완료

> 수정 가능한 항목 모두 처리 완료.

| 항목 | 위치 | 내용 | 상태 |
|------|------|------|------|
| InferenceConfig.AUTO | Models.kt | 불필요한 companion object 제거, `InferenceConfig()`로 대체 | ✅ |
| WordInfo.phonemeSymbols | LyricsAlignerImpl | 미사용 필드 제거 | ✅ High #9에서 수정 |
| LyricsEditor `text.split("\n")` | 라인 37, 69 | 서로 다른 실행 시점(렌더링 vs 콜백)이므로 공유 불필요 | ⏭ 보류 |
| OffsetAdjuster 포맷 | 라인 51-56 | 10ms 단위로 포맷 수정 (99ms → "+0.09s") | ✅ |
| LiteRtInferenceEngine 4D 버퍼 | createOutputBuffer | Whisper 불필요 4D 분기 제거 | ✅ |
| CoroutineScope 누수 | PlayerServiceImpl | `SupervisorJob()` 추가로 자식 실패 격리 | ✅ |
| jsize 캐스팅 | audio_preprocessor_jni.cpp | `size_t > INT_MAX` 검증 추가 | ✅ Critical에서 수정 |

---

## 종합 평가

| 원칙 | 위반 수 | 대표 사례 |
|------|---------|----------|
| **Simplicity First** | 8건 | `Map<String,Any>` 인터페이스, 불필요한 generic 설계 |
| **Surgical Changes** | 6건 | 죽은 코드(`resolveTrack`, `phonemeSymbols`, `onOffsetChange`) |
| **Think Before Coding** | 4건 | PlaybackState 상태 모델링, Float 차원 불명확 |
| **Goal-Driven** | 3건 | alignment-orchestrator 미구현, 테스트 커버리지 갭 |

## 수정 우선순위

1. C++ 메모리 안전성 수정 (버퍼 오버플로우, RAII, JNI 누수)
2. InferenceEngine을 Whisper-specific 타입으로 구체화
3. PlaybackState 상태 모델 + currentPositionMs 중복 제거
4. CTC 신뢰도 계산 수정
5. PlayerServiceImpl 죽은 코드 정리 및 TrackDao 연결
6. LyricsAlignerImpl DI 구조 개선
