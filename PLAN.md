# 온디바이스 가사-오디오 동기화 음악 플레이어: 병렬 에이전트 작업 분해

## 프로젝트 개요

사용자가 입력한 가사 텍스트를 재생 중인 오디오에 **온디바이스에서 자동 정렬**하는 안드로이드 음악 플레이어.
LRCLIB 등 외부 동기화 데이터 조회는 제외 — **모든 곡에 대해 ML 기반 정렬을 직접 수행**한다.

## 설계 원칙

- **한국어 전용 모델 불필요**: G2P(자소→발음 변환) 전처리만 있으면 다국어 CTC 모델로 정렬 가능
- **전처리 + 캐싱 > 실시간**: 첫 재생 시 백그라운드 처리 후 캐싱
- **UI 미려함 < 속도**: 성능 크리티컬, Kotlin Native 필수

---

## 의존성 그래프 (어떤 작업이 어떤 작업에 의존하는가)

```
[A] 음악 플레이어 코어 ──────────────────────────────────┐
                                                          │
[B] 오디오 전처리 파이프라인 ──┐                           │
                               ├──→ [E] 정렬 통합 + 캐싱 ─┼──→ [G] 통합 테스트 + 최적화
[C] ML 추론 엔진 통합 ────────┤                           │
                               │                           │
[D] CTC 강제 정렬 알고리즘 ───┘                           │
                                                          │
[F] 가사 표시 UI ─────────────────────────────────────────┘
```

- A, B, C, D, F는 **모두 독립적으로 병렬 실행 가능**
- E는 B, C, D가 완료되어야 시작 가능
- G는 A, E, F 모두가 완료되어야 시작 가능

---

## 작업 A: 음악 플레이어 코어

### 목적
로컬 오디오 파일을 탐색하고 재생하는 기본 음악 플레이어

### 범위
- MediaStore API로 기기의 음악 파일 스캔 (MP3, FLAC, OGG, WAV, AAC)
- Media3 ExoPlayer + FFmpeg 확장으로 오디오 재생
- Foreground Service + MediaSession (알림 컨트롤, 블루투스 등)
- Now Playing 화면: 앨범 아트, 재생/일시정지/이전/다음, 시크바
- 재생 큐 관리, 셔플, 반복
- Room DB에 곡 메타데이터 캐싱

### 기술 스택
- Kotlin, Jetpack Compose, Material 3
- Media3 (ExoPlayer + MediaSession + FFmpeg 확장)
- Hilt (DI), Room (DB)

### 입력
- 없음 (독립 작업)

### 출력 (다른 작업에 제공)
- `PlayerService` 인터페이스: 현재 재생 위치(ms), 재생 상태, 곡 메타데이터
- `AudioProcessor` 훅: PCM 오디오 데이터를 외부 모듈에 전달하는 콜백 포인트
- `StateFlow<PlaybackState>`: 재생 상태를 관찰 가능한 스트림

### 완료 기준
- 로컬 MP3/FLAC 파일 재생 가능
- 백그라운드 재생 + 알림 컨트롤 동작
- 시크바로 탐색 시 정확한 위치 이동
- 단위 테스트: MediaStore 쿼리, 재생 상태 전이

### 예상 소요: 2주

---

## 작업 B: 오디오 전처리 파이프라인 (NDK/C++)

### 목적
오디오 파일을 ML 모델이 소비 가능한 형태로 변환

### 범위
- 오디오 디코딩: 다양한 포맷 → Raw PCM (FFmpeg NDK 빌드)
- 리샘플링: 원본 샘플레이트 → 16kHz 모노 (ML 모델 표준 입력)
- Mel Spectrogram / Log-Mel Filterbank 특징 추출 (Whisper 호환 80-band)
- 청크 분할: 30초 단위 세그먼트 (Whisper 입력 규격)
- 메모리 매핑 I/O: mmap으로 대용량 오디오 제로카피 접근
- JNI 인터페이스: Kotlin에서 호출 가능한 native 함수 노출

### 기술 스택
- C++ (NDK r26+), CMake
- FFmpeg 라이브러리 (libavcodec, libavformat, libswresample) — NDK 크로스컴파일
- ARM NEON intrinsics로 FFT/Mel 연산 최적화

### 입력
- 오디오 파일 경로 (String)

### 출력 (다른 작업에 제공)
- `AudioFeatures` 객체: float 배열 (Mel spectrogram frames)
- `PcmSegments`: 16kHz 모노 PCM 청크 리스트
- 각 청크의 원본 오디오 내 시간 오프셋 매핑

### 인터페이스 계약 (Contract)
```kotlin
// JNI를 통해 Kotlin에서 호출
interface AudioPreprocessor {
    /** 오디오 파일을 16kHz 모노 PCM으로 디코딩 */
    fun decodeToPcm(filePath: String): FloatArray

    /** PCM을 30초 청크로 분할, 각 청크의 시작 시간(ms) 포함 */
    fun segmentPcm(pcm: FloatArray, chunkDurationMs: Int = 30000): List<PcmChunk>

    /** PCM을 80-band Log-Mel Spectrogram으로 변환 (Whisper 호환) */
    fun extractMelSpectrogram(pcm: FloatArray): FloatArray
}

data class PcmChunk(
    val data: FloatArray,
    val offsetMs: Long,      // 원본 오디오 내 시작 위치
    val durationMs: Long
)
```

### 완료 기준
- MP3, FLAC, OGG, WAV 파일 → 16kHz PCM 변환 성공
- Mel spectrogram 출력이 Python librosa 결과와 ±1% 오차 이내
- 3.5분 곡 전처리 시간: 플래그십 <1초, 중급 <3초
- 메모리 누수 없음 (ASan 통과)

### 예상 소요: 2주

---

## 작업 C: ML 추론 엔진 통합

### 목적
TFLite/ONNX 모델을 안드로이드에서 효율적으로 로드하고 추론 실행

### 범위
- LiteRT (TFLite) 런타임 통합 + XNNPack CPU 델리게이트
- ONNX Runtime Mobile 통합 (대안/비교용)
- GPU 델리게이트 (OpenCL) 설정
- NPU 델리게이트 (Qualcomm QNN) 조건부 활성화
- 모델 로딩: mmap 기반 메모리 매핑
- 적응형 델리게이트 선택: 기기 하드웨어 탐지 → 최적 백엔드 자동 선택
- 추론 성능 벤치마크 유틸리티

### 기술 스택
- LiteRT (com.google.ai.edge.litert)
- ONNX Runtime (com.microsoft.onnxruntime:onnxruntime-android)
- Qualcomm QNN 델리게이트 (com.qualcomm.qti:qnn-litert-delegate)

### 입력
- ML 모델 파일 (.tflite 또는 .onnx)
- 입력 텐서 데이터 (FloatArray — Mel spectrogram 또는 PCM)

### 출력 (다른 작업에 제공)
- `InferenceEngine` 인터페이스: 모델 로드, 추론 실행, 결과 반환
- 추론 결과 텐서 (FloatArray — 음소 확률, 토큰 로짓 등)
- 성능 메트릭: 추론 시간, 메모리 사용량

### 인터페이스 계약
```kotlin
interface InferenceEngine {
    /** 모델 로드. 기기에 맞는 최적 델리게이트 자동 선택 */
    fun loadModel(modelPath: String, config: InferenceConfig = InferenceConfig.AUTO): Boolean

    /** 단일 추론 실행 */
    fun run(input: Map<String, Any>): Map<String, Any>

    /** 리소스 해제 */
    fun close()

    /** 현재 사용 중인 백엔드 정보 */
    fun getBackendInfo(): BackendInfo
}

data class InferenceConfig(
    val preferredBackend: Backend = Backend.AUTO,  // AUTO, CPU, GPU, NPU
    val numThreads: Int = 4,
    val enableFp16: Boolean = true
) {
    companion object {
        val AUTO = InferenceConfig()
    }
}

data class BackendInfo(
    val backend: Backend,
    val delegateName: String,
    val estimatedSpeedup: Float  // CPU 대비 배율
)
```

### 완료 기준
- Whisper tiny INT8 모델 로드 + 30초 오디오 추론 성공
- CPU/GPU/NPU 델리게이트 전환이 런타임에 동작
- 30초 청크 추론 시간: 플래그십 CPU <2초, NPU <0.5초
- OOM 없이 연속 10회 추론 안정 실행

### 예상 소요: 2주

---

## 작업 D: CTC 강제 정렬 알고리즘

### 목적
알려진 가사 텍스트를 오디오 프레임 시퀀스에 정렬하는 핵심 알고리즘 구현

### 범위
**이것이 프로젝트의 핵심 기술이다.**

#### D-1: G2P 텍스트 전처리
- 한국어 가사 → 발음 음소 시퀀스 변환
  - 한글 자모 분해 (Unicode 연산, 외부 라이브러리 불필요)
  - 발음 규칙 적용: 연음, 비음화, 유음화, 경음화, 구개음화 등
  - 예: `맑은` → /ㅁㅏㄹㄱㅡㄴ/, `같이` → /ㄱㅏㅊㅣ/
- 영어 단어 → CMU 발음 사전 또는 규칙 기반 G2P
- 한영 혼합 가사 처리 (코드스위칭 감지)
- 숫자, 특수기호 정규화

#### D-2: CTC Forced Alignment 코어
- Viterbi 또는 CTC 세그멘테이션 알고리즘 구현
  - 입력: 프레임별 음소 확률 행렬 (ML 추론 엔진의 출력) + 기대 음소 시퀀스 (G2P 출력)
  - 출력: 각 단어/음절의 시작/종료 프레임 인덱스
- 동적 프로그래밍 기반 최적 정렬 경로 탐색
- 빈 토큰(CTC blank) 처리
- 청크 경계 연결: 30초 청크 간 타임스탬프 연속성 보장

#### D-3: 후처리
- 프레임 인덱스 → 밀리초 타임스탬프 변환
- 신뢰도 점수 계산 (정렬 경로의 평균 확률)
- 저신뢰 구간 보간 (음악 간주 등 보컬 없는 구간)
- Enhanced LRC 포맷 생성

### 기술 스택
- Kotlin (알고리즘 로직)
- 선택적으로 C++/NDK (Viterbi 루프 성능 최적화)

### 입력
- 가사 텍스트 (String)
- 음소 확률 행렬 (FloatArray — 작업 C의 추론 결과)

### 출력
- `AlignmentResult`: 단어별 (시작ms, 종료ms, 단어, 신뢰도) 리스트
- Enhanced LRC 문자열

### 인터페이스 계약
```kotlin
interface LyricsAligner {
    /**
     * 가사 텍스트를 음소 확률 행렬에 정렬
     * @param lyrics 줄 단위 가사 텍스트
     * @param phonemeProbabilities 프레임별 음소 확률 [frames × vocab_size]
     * @param frameDurationMs 한 프레임의 시간 길이 (보통 20ms)
     * @param language "ko", "en", "mixed"
     * @return 단어별 타임스탬프와 신뢰도
     */
    fun align(
        lyrics: List<String>,
        phonemeProbabilities: FloatArray,
        frameDurationMs: Float,
        language: String = "ko"
    ): AlignmentResult
}

data class AlignmentResult(
    val words: List<WordAlignment>,
    val lines: List<LineAlignment>,
    val overallConfidence: Float,  // 0.0~1.0
    val enhancedLrc: String
)

data class WordAlignment(
    val word: String,
    val startMs: Long,
    val endMs: Long,
    val confidence: Float,
    val lineIndex: Int
)

data class LineAlignment(
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val wordAlignments: List<WordAlignment>
)
```

### 테스트 전략 (Claude Code 피드백 루프)
- **합성 테스트 데이터**: TTS로 알려진 타임스탬프의 음성 생성 → 정렬 → 비교
- **CSD 데이터셋**: KAIST 한국어 노래 50곡 (음절 수준 정렬 ground truth)
- **Jamendo**: 80곡 다국어 (단어 수준 ground truth)
- **품질 게이트**:
  - MAE < 0.30초
  - 0.3초 허용오차 내 정확도 > 80%
  - 한국어 곡에서 MedAE < 0.15초

### 완료 기준
- 한국어 G2P가 표준 발음 규칙 20가지 이상 처리
- Jamendo 영어 곡에서 MAE < 0.30초
- CSD 한국어 곡에서 MAE < 0.35초
- 정렬 알고리즘 실행 시간: 3.5분 곡 프레임에 대해 <500ms (정렬 자체, 추론 제외)

### 예상 소요: 3주 (가장 기술적으로 도전적)

---

## 작업 E: 정렬 오케스트레이션 + 캐싱

### 목적
B, C, D를 연결하는 파이프라인 관리 + 결과 영구 캐싱

### 범위
- 전체 파이프라인 오케스트레이션:
  1. 오디오 파일 → [B] 전처리 → PCM/Mel 특징
  2. Mel 특징 → [C] ML 추론 → 음소 확률 행렬
  3. 가사 텍스트 + 음소 확률 → [D] 강제 정렬 → 타임스탬프
- WorkManager 기반 백그라운드 실행
  - 첫 재생 시 자동 트리거
  - 점진적 처리: 30초 청크 단위로 완료 즉시 UI에 전달
  - 실패 시 재시도 (최대 3회)
- Room DB에 정렬 결과 캐싱
  - 곡 fingerprint (파일 해시) 기반 캐시 키
  - 모델 버전 변경 시 캐시 무효화
- 사용자 수동 오프셋 조정 저장 (글로벌 오프셋 + 줄별 보정)
- 진행률 StateFlow 노출

### 기술 스택
- Kotlin Coroutines + Flow
- WorkManager
- Room DB

### 의존성
- 작업 B의 `AudioPreprocessor`
- 작업 C의 `InferenceEngine`
- 작업 D의 `LyricsAligner`

### 인터페이스 계약
```kotlin
interface AlignmentOrchestrator {
    /**
     * 가사 정렬 시작 (백그라운드)
     * 이미 캐시가 있으면 즉시 반환
     */
    fun requestAlignment(
        songId: String,
        audioPath: String,
        lyrics: List<String>,
        language: String = "ko"
    ): Flow<AlignmentProgress>

    /** 캐시된 정렬 결과 조회 */
    suspend fun getCachedAlignment(songId: String): AlignmentResult?

    /** 사용자 오프셋 조정 저장 */
    suspend fun saveUserOffset(songId: String, globalOffsetMs: Long)

    /** 캐시 무효화 (모델 업데이트 시) */
    suspend fun invalidateCache(modelVersion: String)
}

sealed class AlignmentProgress {
    data class Processing(val chunkIndex: Int, val totalChunks: Int) : AlignmentProgress()
    data class PartialResult(val lines: List<LineAlignment>, val upToMs: Long) : AlignmentProgress()
    data class Complete(val result: AlignmentResult) : AlignmentProgress()
    data class Failed(val error: Throwable, val retriesLeft: Int) : AlignmentProgress()
}
```

### 완료 기준
- 파이프라인 end-to-end 동작: 오디오 파일 + 가사 → 타임스탬프 캐싱
- WorkManager로 앱 종료 후에도 처리 계속
- 캐시 히트 시 정렬 결과 로딩 <50ms
- 점진적 결과 전달: 첫 30초 결과가 전체 처리 완료 전에 UI 도달

### 예상 소요: 1.5주

---

## 작업 F: 가사 표시 UI

### 목적
동기화된 가사를 실시간으로 표시하는 Compose UI 컴포넌트

### 범위
- 줄별 동기화 가사 스크롤 뷰
  - 현재 재생 줄 자동 센터링 (스무스 애니메이션)
  - 지나간 줄 / 현재 줄 / 다음 줄 시각적 구분
- 단어별 하이라이트 (Enhanced LRC 지원 시)
  - 현재 단어 진행 애니메이션 (색상 그라데이션 또는 볼드)
- 사용자 입력 가사 편집기
  - 멀티라인 텍스트 입력
  - 줄 번호 표시
  - 복사/붙여넣기 지원
- 수동 타이밍 조정 UI
  - 글로벌 오프셋 슬라이더 (±5초)
  - 가사 터치 → 해당 위치로 시크
- 정렬 진행률 표시 (처리 중일 때)
- 정렬 없는 상태: 일반 가사 텍스트만 표시

### 기술 스택
- Jetpack Compose
- Compose Animation API
- amlv 라이브러리 참조 (github.com/dokar3/amlv)

### 입력 (다른 작업으로부터)
- `StateFlow<PlaybackState>` — 현재 재생 위치 (작업 A)
- `AlignmentResult` — 단어별 타임스탬프 (작업 E, 또는 캐시)
- 가사 텍스트 (사용자 입력)

### 인터페이스 계약
```kotlin
@Composable
fun SyncedLyricsView(
    lyrics: List<LineAlignment>,
    currentPositionMs: Long,    // 재생 위치 (작업 A에서 관찰)
    globalOffsetMs: Long = 0,
    onLineClick: (lineIndex: Int) -> Unit = {},  // 줄 클릭 → 시크
    onOffsetChange: (Long) -> Unit = {},
    modifier: Modifier = Modifier
)

@Composable
fun LyricsEditor(
    initialText: String = "",
    onLyricsSubmit: (List<String>) -> Unit,
    modifier: Modifier = Modifier
)

@Composable
fun AlignmentProgressIndicator(
    progress: AlignmentProgress,
    modifier: Modifier = Modifier
)
```

### 완료 기준
- 현재 줄 자동 스크롤이 재생과 ±100ms 이내 동기화
- 단어별 하이라이트가 시각적으로 매끄러움 (60fps)
- 가사 편집기에서 한국어 입력 정상 동작
- 시크바 이동 시 가사 위치 즉시 업데이트

### 예상 소요: 2주

---

## 작업 G: 통합 테스트 + 최적화

### 목적
전체 모듈 통합, end-to-end 검증, 성능/배터리 최적화

### 범위
- 모듈 간 통합 및 DI 구성 (Hilt)
- End-to-end 테스트: 음악 재생 + 가사 입력 → 자동 정렬 → 동기화 표시
- 성능 프로파일링 및 최적화
  - Android Profiler로 CPU/메모리/배터리 프로파일링
  - 저사양 기기 테스트 (4GB RAM, 저가 칩셋)
  - 메모리 누수 탐지 (LeakCanary)
- 배터리 최적화
  - 정렬 처리 중 배터리 소모 측정
  - 열 쓰로틀링 감지 및 처리 속도 조절
- 적응형 모델 선택 로직
  - 기기 capability 감지 → 모델 크기/정밀도 자동 선택
- 앱 크기 최적화 (모델 파일 다운로드 vs 번들)
- CI/CD 파이프라인 구성

### 의존성
- 모든 작업 (A, B, C, D, E, F)

### 완료 기준
- 5종 이상 실기기에서 end-to-end 정상 동작
- 3.5분 곡 정렬 완료 시간: 플래그십 <15초, 중급 <30초
- 앱 크기 <80MB (모델 포함)
- 연속 1시간 재생 + 10곡 정렬 시 배터리 소모 <5%
- CI에서 단위 테스트 + ML 평가 자동 실행

### 예상 소요: 2주

---

## 병렬 실행 일정 요약

```
주차:  1    2    3    4    5    6    7    8    9   10   11
      ├────┼────┼────┼────┼────┼────┼────┼────┼────┼────┤
  [A] ████████████                                        음악 플레이어 코어
  [B] ████████████                                        오디오 전처리
  [C] ████████████                                        ML 추론 엔진
  [D] ██████████████████                                  CTC 강제 정렬 (핵심)
  [F] ████████████                                        가사 표시 UI
                         [E] ████████                     정렬 통합 + 캐싱
                                     [G] ████████████     통합 + 최적화
```

**Phase 1 (1~3주)**: A, B, C, D, F 동시 시작 → 5개 에이전트 병렬
**Phase 2 (4주)**: D 계속 진행, 나머지 완료 → 1~2개 에이전트
**Phase 3 (5~6주)**: E 통합 → 1개 에이전트
**Phase 4 (7~8주)**: G 통합 테스트 → 1~2개 에이전트

총 **8주** (병렬 실행 시), 순차 실행 시 ~15주

---

## 에이전트 간 통신 규약

### 공유 인터페이스 파일
각 작업의 인터페이스 계약(Contract)을 **먼저 작성하고 공유 모듈에 배치**한 뒤 구현에 착수한다.
이렇게 하면 각 에이전트가 다른 모듈의 구현을 기다리지 않고 인터페이스에 대해 코딩할 수 있다.

```
:core:contracts/
  ├── AudioPreprocessor.kt    (작업 B의 인터페이스)
  ├── InferenceEngine.kt      (작업 C의 인터페이스)
  ├── LyricsAligner.kt        (작업 D의 인터페이스)
  ├── AlignmentOrchestrator.kt (작업 E의 인터페이스)
  └── Models.kt               (공유 데이터 클래스: PcmChunk, AlignmentResult 등)
```

### 테스트용 Mock/Fake
각 에이전트는 자기 모듈의 **Fake 구현**도 함께 제공한다.
다른 모듈이 통합 전에 테스트할 수 있도록.

```kotlin
// 작업 D가 제공하는 Fake
class FakeLyricsAligner : LyricsAligner {
    override fun align(...) = AlignmentResult(
        words = lyrics.flatMapIndexed { i, line ->
            line.split(" ").mapIndexed { j, word ->
                WordAlignment(word, startMs = (i * 5000L + j * 500L), ...)
            }
        }, ...
    )
}
```

---

## 모델 선택 결정 (사전에 확정 필요)

아래 결정은 **작업 시작 전에 확정**해야 B, C, D가 올바른 모델을 기준으로 작업할 수 있다.

### 1차 모델 (MVP)
| 용도 | 모델 | 크기 | 형식 |
|------|------|------|------|
| 음소 확률 생성 | wav2vec2-base (MMS) | ~360MB → INT8 ~95MB | ONNX |
| 대안: 더 경량 | Whisper tiny | ~75MB → INT8 ~40MB | GGML (whisper.cpp) |

### G2P
| 언어 | 방법 | 크기 |
|------|------|------|
| 한국어 | 규칙 기반 (Unicode 자모 분해 + 발음 규칙 테이블) | ~0 (코드만) |
| 영어 | CMU Pronouncing Dictionary 내장 | ~5MB |

### 권장: MVP에서는 **Whisper tiny INT8 (whisper.cpp)** 로 시작
- 이유: whisper.cpp에 안드로이드 예제가 이미 있고, 타임스탬프 출력을 기본 제공
- 단어 수준 정밀도가 부족하면 2차로 wav2vec2 CTC 정렬 추가 (작업 D의 확장)

---

## Claude Code 피드백 루프 설정

### CLAUDE.md 품질 게이트
```markdown
## 자동 검증 명령어
- 코드 빌드: ./gradlew assembleDebug
- 단위 테스트: ./gradlew test
- ML 정렬 평가: python evaluation/eval_alignment.py --dataset csd_korean
- 코드 린트: ./gradlew ktlintCheck

## 정렬 품질 기준
- MAE < 0.30초 (영어 Jamendo)
- MAE < 0.35초 (한국어 CSD)
- 0.3초 허용오차 내 정확도 > 80%

## 성능 기준
- 30초 오디오 전처리: <500ms (Pixel 7 기준)
- 30초 오디오 추론: <2초 (Pixel 7 CPU)
- CTC 정렬 알고리즘: <200ms (3.5분 곡 전체)

## 코드 변경 후 자동 실행
PostToolUse: ./gradlew test && python evaluation/quick_eval.py
```

### 평가 데이터
```
evaluation/
  ├── datasets/
  │   ├── csd_korean/       # KAIST CSD 한국어 50곡 (git-lfs)
  │   ├── jamendo/          # Jamendo 80곡 (git-lfs)
  │   └── synthetic/        # TTS 합성 테스트 데이터
  ├── eval_alignment.py     # mir_eval 기반 평가 스크립트
  ├── quick_eval.py         # 5곡 빠른 평가 (CI용)
  └── baseline.json         # 현재 최고 성능 기록
```
