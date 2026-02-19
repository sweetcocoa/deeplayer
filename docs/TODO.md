# TODO

## 1. 라이브러리 Fast Scroller (네비게이션 바)

음악 목록을 스크롤할 때 오른쪽에 알파벳/가나다 네비게이션 바를 추가한다.

### 요구사항
- LazyColumn 오른쪽에 세로 네비게이션 바 표시
- 한글(ㄱ-ㅎ) + 영문(A-Z) + 숫자(#) 인덱스
- 인덱스 탭/드래그 시 해당 섹션 첫 곡으로 점프
- 드래그 중 현재 선택된 문자를 크게 표시 (팝업 인디케이터)
- 현재 정렬 기준(제목/아티스트/앨범)에 맞는 인덱스 사용

### 관련 파일
- `app/src/main/java/com/deeplayer/ui/library/LibraryScreen.kt`
- `docs/ui-spec.md` 섹션 3-2 (우측 알파벳/가나다 Fast Scroller)

---

## 2. 폴더 기반 음악 필터링

선택된 폴더의 음악 파일만 라이브러리에 보이게 하는 기능을 추가한다.

### 요구사항
- 설정에서 스캔 대상 폴더 선택 UI
- 선택된 폴더(및 하위 폴더)의 음악만 라이브러리에 표시
- 기본값: 전체 MediaStore 음악 (기존 동작 유지)
- 폴더 선택 변경 시 라이브러리 자동 갱신
- SharedPreferences 또는 DataStore로 선택 폴더 경로 저장

### 관련 파일
- `core/player/src/main/java/com/deeplayer/core/player/MediaStoreScanner.kt`
- `core/player/src/main/java/com/deeplayer/core/player/PlayerServiceImpl.kt`
- `docs/ui-spec.md` 섹션 3-5 (폴더 탭), 섹션 6 (설정 > 스캔 폴더 관리)
