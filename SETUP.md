# Deeplayer 개발 환경 설정 & 빌드 가이드

안드로이드 개발 경험이 없는 분을 위한 단계별 가이드입니다.
macOS 기준으로 작성되었습니다.

---

## 전체 흐름

```
1. JDK 설치        → Gradle/Kotlin 컴파일러를 실행하는 데 필요
2. Android Studio  → 안드로이드 SDK, 에뮬레이터, NDK를 한 번에 설치
3. Git 초기화      → 버전 관리 시작
4. Gradle Wrapper  → 프로젝트 전용 Gradle 버전 고정
5. 빌드 & 테스트    → 코드가 정상인지 확인
```

---

## Step 1: JDK 17 설치

### 왜 필요한가?
Kotlin/Java 코드를 컴파일하고 Gradle 빌드 도구를 실행하려면 JDK(Java Development Kit)가 필요합니다.
이 프로젝트는 JDK 17을 사용합니다.

### 설치

```bash
# Homebrew로 설치 (가장 간단)
brew install openjdk@17
```

설치 후 쉘 설정 파일(`~/.zshrc`)에 JAVA_HOME을 추가합니다:

```bash
# ~/.zshrc 맨 아래에 추가
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/$(ls /opt/homebrew/Cellar/openjdk@17/)/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
```

터미널을 새로 열거나 `source ~/.zshrc`를 실행한 뒤 확인:

```bash
java -version
# → openjdk version "17.x.x" 가 출력되면 성공
```

---

## Step 2: Android Studio 설치

### 왜 필요한가?
안드로이드 앱을 빌드하려면 **Android SDK**(안드로이드 API 라이브러리)가 필요합니다.
Android Studio를 설치하면 SDK, 에뮬레이터, NDK(C++ 빌드 도구)를 GUI로 편하게 관리할 수 있습니다.

### 설치

1. https://developer.android.com/studio 에서 다운로드 → 설치
2. 첫 실행 시 **Standard** 설정으로 진행 (기본 SDK 자동 다운로드)
3. 설치 완료되면 Welcome 화면이 뜹니다

### SDK 경로 확인 & 환경 변수 설정

Android Studio → Settings (⌘,) → Languages & Frameworks → Android SDK에서 경로를 확인합니다.
보통 `~/Library/Android/sdk`입니다.

```bash
# ~/.zshrc에 추가
export ANDROID_HOME=~/Library/Android/sdk
export PATH="$ANDROID_HOME/platform-tools:$PATH"
```

### NDK 설치 (오디오 전처리 C++ 코드에 필요)

이 프로젝트의 `:feature:audio-preprocessor` 모듈은 C++로 작성된 네이티브 코드를 포함합니다.
이를 컴파일하려면 NDK(Native Development Kit)가 필요합니다.

Android Studio → Settings → Languages & Frameworks → Android SDK → **SDK Tools** 탭:
- ✅ **NDK (Side by side)** 체크 → Apply
- ✅ **CMake** 체크 → Apply

### local.properties 파일 생성

프로젝트 루트에 `local.properties` 파일을 만들어 SDK 경로를 알려줘야 합니다:

```bash
cd /Users/jonghochoi/workspace/deeplayer

# 자동 생성 (Android Studio로 프로젝트를 열면 자동으로 만들어지지만, 수동으로도 가능)
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
```

> **참고**: `local.properties`는 `.gitignore`에 포함되어 있어 git에 커밋되지 않습니다.
> 각 개발자의 SDK 경로가 다를 수 있기 때문입니다.

---

## Step 3: Git 초기화

### 왜 필요한가?
코드 변경 이력을 추적하고, 나중에 GitHub 등에 올리기 위해 git 저장소를 초기화합니다.

```bash
cd /Users/jonghochoi/workspace/deeplayer
git init
```

첫 커밋을 만들어둡니다:

```bash
git add -A
git commit -m "Initial project scaffolding with 8 modules"
```

---

## Step 4: Gradle Wrapper 생성

### 왜 필요한가?
Gradle은 프로젝트 빌드 도구입니다 (npm 같은 역할).
**Gradle Wrapper**(`gradlew`)는 프로젝트에 필요한 정확한 Gradle 버전을 자동으로 다운로드해서 사용합니다.
팀원 모두가 동일한 Gradle 버전을 쓰도록 보장하는 장치입니다.

현재 `gradlew` 스크립트와 `gradle-wrapper.properties`는 있지만,
실제 실행에 필요한 `gradle-wrapper.jar` 파일이 없습니다. 이를 생성합니다.

### 방법 A: 시스템 Gradle로 생성 (권장)

```bash
# Gradle 설치 (아직 없다면)
brew install gradle

# wrapper 생성 — 프로젝트 루트에서 실행
cd /Users/jonghochoi/workspace/deeplayer
gradle wrapper --gradle-version 8.12
```

이 명령이 하는 일:
- `gradle/wrapper/gradle-wrapper.jar` 생성 (Gradle 부트스트랩 실행 파일)
- `gradlew`, `gradlew.bat` 스크립트 업데이트

### 방법 B: Android Studio로 자동 생성

Android Studio에서 프로젝트 폴더를 열면 (File → Open → deeplayer 폴더 선택),
Gradle sync가 자동으로 시작되면서 wrapper를 생성합니다.

### 확인

```bash
./gradlew --version
# → Gradle 8.12 가 출력되면 성공
```

---

## Step 5: 빌드

### 왜 필요한가?
코드에 문법 오류가 없는지, 의존성이 제대로 다운로드되는지, 모든 모듈이 정상 컴파일되는지 확인합니다.

```bash
cd /Users/jonghochoi/workspace/deeplayer
./gradlew assembleDebug
```

**첫 실행 시 시간이 오래 걸립니다** (5~15분).
Gradle이 모든 의존성 라이브러리를 Maven Central/Google 저장소에서 다운로드하기 때문입니다.
두 번째부터는 캐시되어 훨씬 빠릅니다.

### 자주 보는 오류와 해결법

| 오류 메시지 | 원인 | 해결 |
|------------|------|------|
| `SDK location not found` | `local.properties` 없음 | Step 2의 local.properties 생성 참고 |
| `NDK not installed` | NDK 미설치 | Android Studio SDK Tools에서 NDK 설치 |
| `Could not determine java version` | JAVA_HOME 미설정 | Step 1의 환경 변수 설정 참고 |
| `Failed to find CMake` | CMake 미설치 | Android Studio SDK Tools에서 CMake 설치 |

### NDK 없이 빌드하기 (audio-preprocessor 제외)

C++ 빌드 환경이 준비되지 않았다면, 해당 모듈만 제외하고 빌드할 수 있습니다:

```bash
# 특정 모듈만 빌드
./gradlew :core:contracts:assembleDebug
./gradlew :core:player:assembleDebug
./gradlew :feature:lyrics-aligner:assembleDebug
./gradlew :feature:inference-engine:assembleDebug
./gradlew :feature:lyrics-ui:assembleDebug
```

---

## Step 6: 단위 테스트 실행

### 왜 필요한가?
각 모듈의 로직이 기대대로 동작하는지 자동으로 검증합니다.
테스트는 에뮬레이터나 실기기 없이 PC에서 바로 실행됩니다 (JVM 기반 단위 테스트).

```bash
# 전체 테스트
./gradlew test

# 특정 모듈 테스트
./gradlew :feature:lyrics-aligner:test    # CTC 정렬 (가장 중요)
./gradlew :feature:inference-engine:test   # ML 추론
./gradlew :core:player:test                # 플레이어
./gradlew :feature:lyrics-ui:test          # 가사 UI
./gradlew :feature:audio-preprocessor:test # 오디오 전처리
```

### 테스트 결과 확인

테스트 실행 후 HTML 리포트가 생성됩니다:

```bash
# 브라우저에서 열기
open feature/lyrics-aligner/build/reports/tests/testDebugUnitTest/index.html
```

### 테스트가 실패하면?

터미널에 실패한 테스트 이름과 원인이 출력됩니다.
상세 로그를 보려면:

```bash
./gradlew :feature:lyrics-aligner:test --info
```

---

## Step 7: 코드 품질 검사

### 왜 필요한가?
코드 스타일이 일관되는지 (ktfmt), 잠재적 버그가 없는지 (detekt) 자동 검사합니다.

```bash
# 포맷 검사 (스타일 위반 찾기)
./gradlew ktfmtCheck

# 포맷 자동 수정
./gradlew ktfmtFormat

# 정적 분석 (잠재적 버그/코드스멜 탐지)
./gradlew detekt
```

---

## Step 8: Android Studio에서 열기 (선택)

터미널 대신 IDE를 사용하고 싶다면:

1. Android Studio 실행
2. **File → Open** → `/Users/jonghochoi/workspace/deeplayer` 폴더 선택
3. Gradle sync가 자동 시작됨 (하단 진행 바 확인)
4. sync 완료 후 좌측 Project 패널에서 모든 모듈을 탐색 가능

### IDE에서 테스트 실행
- 테스트 파일 열기 → 클래스명이나 함수명 옆의 ▶ 버튼 클릭
- 또는 Run → Run... → 원하는 테스트 선택

### IDE에서 빌드
- 상단 메뉴 Build → Make Project (⌘F9)

---

## 요약: 최소 실행 명령어

```bash
# 1. 환경 변수 (이미 ~/.zshrc에 추가했다면 생략)
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/$(ls /opt/homebrew/Cellar/openjdk@17/)/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=~/Library/Android/sdk

# 2. local.properties 생성
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties

# 3. Gradle wrapper 생성 (최초 1회)
gradle wrapper --gradle-version 8.12

# 4. 빌드
./gradlew assembleDebug

# 5. 테스트
./gradlew test

# 6. 코드 품질
./gradlew ktfmtCheck detekt
```

---

## 문제가 생기면

1. **터미널에서 에러 메시지 전체를 복사**합니다
2. 이 프로젝트의 Claude Code 세션에 붙여넣으면 디버깅을 도와드립니다
3. 흔한 문제 대부분은 SDK/NDK 경로 설정과 관련됩니다
