# 손끝길 Kotlin Android 앱 · 0.2.3

이 브랜치의 기본 앱은 **Kotlin + Jetpack Compose + CameraX**다. 프론트 화면·음성 결과·주문 해석·주문 확인·버튼 계획·메뉴 검색을 Kotlin으로 실행한다. 빌드와 실행에 npm, Node, Metro, React Native가 필요하지 않다.

## Android Studio 열기

두 레포를 같은 부모 폴더 아래에 배치한다.

```text
workspace/
  sonkkeut-frontend/     ← Android Studio에서 이 폴더를 Open
  sonkkeut-ai/
```

프론트는 `develop_ai`, AI는 `codex/unified-ai-20261003` 브랜치가 필요하다. 프론트 루트의 `settings.gradle.kts`가 AI 레포의 `android/sonkkeut-native` 모듈을 직접 포함한다. SDK 35, JDK 17, Gradle 8.10.2, AGP 8.7.2, Kotlin 1.9.25 / Compose compiler 1.5.15를 사용한다. Android Studio가 SDK 경로를 지정한 `local.properties`를 만든 뒤 `app` 실행 구성을 선택한다.

이 APK에 사용한 AI 소스 커밋: `d9938b2f3e999b093942c4a882264a3fbc2ca8b8`.

```powershell
./gradlew.bat :app:assembleRelease :app:testDebugUnitTest
./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest
```

명령행에서는 `ANDROID_HOME`과 `JAVA_HOME`을 실제 설치 위치로 설정한다. 모델과 Android 의존성의 첫 준비에는 인터넷이 필요하다. 루트 `app/`가 Kotlin 앱이며, `android/`와 TypeScript 파일은 이전 구현의 비교 자료다. 이 앱의 빌드에 포함되지 않는다.

## 기능 연결

| 영역 | Kotlin 구현 |
|---|---|
| 메인·주문·설정 | `MainActivity.kt`, `NativeHomeControls.kt`, `NativeOrderConfirmation.kt`, Compose |
| 카메라 | CameraX `ImageAnalysis`, 1280×960 우선/미지원 시 대체 해상도, FIT_CENTER 전체 프레임, 분석 스레드 |
| 영상·OCR·손끝 | `sonkkeut-native` → 기존 실제 ONNX·ML Kit·MediaPipe 엔진 |
| 음성 | 자체 CT2 Whisper v3만 사용, 완료/취소·모델 다운로드·직접 입력 대체 |
| 메뉴 검색 | 매장별 SQLite DB → `MenuSpeechIndex`, 한글 이름·별칭·발음 유사도 |
| 메뉴 후보 | `MenuMatcher` → `NativeMenuRecommendations`, 품절 제외·최대 3개, 선택 후 재입력·정상 주문이면 자동 안내 |
| 상세 읽기 내부 코드 | `DetailScanner`, 6개 영역 OCR·취소·늦은 결과 폐기 유지. 현재 UX에는 실행 진입점 없음 |
| 주문 | `NativeOrderParser`, `NativeOrderFlow`, 정상 주문 자동 확인 후 안내. 오류·모호한 후보는 안내 차단 |
| 서버 | `NativeMenuClient`, 시작·재연결 시 자동 동기화, 매장별 오프라인 캐시 |
| 출력·설정 | 고대비 기본 화면·밝은 테마·글자 크기·음성·진동·속도·재안내 |
| 통계 | 기본 꺼짐, 동의한 경우 집계 결과만 전송, 동의 해제 시 대기 기록 삭제 |

메인에는 설정과 실행 중 노란색 종료, 비활성 음성 재인식, 재안내를 표시한다. 주문 인식 원문·보정 결과 창과 안내 시작 버튼을 없애고 정상 주문의 확인 내용과 안내를 함께 표시한다. 주문 확인은 카메라를 축소하고 스크롤 없이 배치하며 긴 주문을 이전·다음 페이지로 보여준다. 주문 입력·오류·후보 영역은 별도 스크롤을 사용할 수 있다.

환경 설정은 시작/안내 상태 모두 화면·카메라·음성 항목만 제공한다. 안내 중 설정 복귀는 새 프레임으로 재개한다. 백그라운드 복귀는 중지 상태를 유지하며 카메라 다시 시작을 제공한다. 페이지 이동·중지 이전 영상/음성 결과는 무효화한다. 서버·매장 변경, 화면 읽기·상세 OCR, 통계 동의 등의 기존 UI 진입점은 제거했고 내부 연결·추론·저장 코드는 유지했다. 서버 설정 변경 화면은 현재 사용자 UX에 없다.

미등록 메뉴·지원하지 않는 옵션·품절·불확실한 OCR은 자동 선택하지 않는다. 장바구니 수량·금액 확인과 중단된 담기 결과 검사를 유지한다. 모델 가중치는 그대로이며 이전 앱과 같은 application ID `com.sonkkeut`, 개발 서명, 더 높은 versionCode 13을 사용해 업데이트 설치한다. Android namespace와 Activity는 `com.sonkkeut.app`다.

## 범위와 검사 기록

0.2.2는 기획자의 다섯 가지 UX 요청을 반영했다. 0.2.1의 메뉴 후보·상세 OCR 등 내부 AI 기능과 가중치를 그대로 유지했다. 메뉴 추천은 제한된 음식 분류·유사 철자 검색이며 LLM·신경망 임베딩·재료/알레르기 추론은 포함하지 않는다. 후보 선택 뒤 수량·옵션을 다시 입력해야 정상 주문 안내를 시작한다. 상세 OCR 좌표는 손끝 안내 목표로 사용하지 않는다.

검사 결과: JVM 단위 테스트 **83개 통과**, API 35 에뮬레이터 전체 실행 **19개 통과·카메라 1개 시간 초과·음성 1개 생략**, 카메라 단독 재검사 통과, 배포용 릴리스 APK 자체 기능 검사 **3개 통과**. 기본 영상/OCR·메뉴 DB·실제 서버 상태/메뉴/모델 목록 연결, 주문 자동 안내, 환경 설정·종료·백그라운드 복귀, 큰 글자 페이지 표시를 검사했다. 생략한 항목은 Whisper 모델/음성 fixture를 사용하는 추론 검사다.

실제 한국어 마이크 인식 정확도, 실물 키오스크 전체 주문, S26 Ultra 초광각·진동·TalkBack 사용성은 미검증이다. 이전 PC 합성 음성 평가를 휴대폰 성능으로 주장하지 않는다. 자체 Whisper 첫 사용에는 약 485MB의 모델 다운로드가 필요하다.

[0.2.2 검증 기록](develop-ux-stage5.md) · [APK/모델/소스 해시](develop-ux-0.2.2-validation.json). 검증 문서는 5단계 종료 시점의 로컬 상태를 기록한 것이며 이후 배포 위치는 [현재 README](../README.md)와 GitHub 릴리스를 따른다.

## AI 연결 0.2.3

관련 표현·설명을 서버 메뉴에서 MenuDocument에 전달하고 매장별 SQLite 스냅샷 처리 후 현재 카탈로그에서 복원한다. 정식 이름을 다른 메뉴의 별칭보다 우선하도록 입력·음성 보정의 카탈로그를 정리한다. 추천 정보는 주문 별칭으로 승격하지 않는다. 현재 기본 서버에는 새 관련 표현·설명이 없어 실데이터 검색 효과는 미검증이다. 기존 UX 및 QA 항목은 유지하며 병합은 사용자 승인 후 수행한다. 최신 결과는 [0.2.3 검증 기록](develop-ai-validation-0.2.3.md)을 따른다.

0.2.3 전달 APK 추가 검사에서는 주문·설정·종료 3개 통과, 카메라 초기 프레임 대기 1개 시간 초과를 확인했다. 자세한 재현·기존 APK 비교 결과는 최신 0.2.3 검증 기록을 따른다.
