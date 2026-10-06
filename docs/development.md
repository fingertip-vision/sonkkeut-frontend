# Android 앱 개발 안내

## 사용할 소스

기준 앱 0.2.3은 `native/kotlin-20261005`의 `fffdb92`, AI는 `codex/unified-ai-20261003`의 `d9938b2`입니다. `main`에 구현 전체가 들어 있지 않습니다. 별도 `develop_kotlin_ui_integration`의 UI 변경은 기준 앱과 분기되어 있으므로 확인 없이 브랜치를 교체하지 마세요.

```bash
git clone --branch native/kotlin-20261005 https://github.com/fingertip-vision/sonkkeut-frontend.git
git clone --branch codex/unified-ai-20261003 https://github.com/fingertip-vision/sonkkeut-ai.git
```

두 저장소는 같은 상위 폴더 아래에 둡니다. 재현용 빌드는 각각 위 커밋으로 checkout하고, 새 커밋을 사용할 때는 호환성을 다시 확인합니다.

## Android Studio와 빌드

- JDK 17, Android SDK 35. 지원 범위는 Android 8.0(API 26) 이상이며 배포 ABI는 `arm64-v8a`입니다.
- Android Studio에서 프론트 루트 폴더를 엽니다. 현재 실행 모듈은 `app/`입니다.
- `settings.gradle.kts`가 형제 폴더 `../sonkkeut-ai/android/sonkkeut-native`를 참조합니다.
- SDK 경로는 로컬 `local.properties`의 `sdk.dir` 또는 개발 환경 설정으로 지정합니다. 개인 경로를 커밋하지 않습니다.
- 첫 Gradle 동기화는 의존성과 손 모델 다운로드를 위해 인터넷이 필요합니다.

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
```

연결된 Android 기기 또는 에뮬레이터가 있어야 마지막 검사를 실행할 수 있습니다. 결과 APK는 `app/build/outputs/apk/debug/app-debug.apk`입니다. 개발 APK는 Play Store용 서명이 아닙니다.

## 기능과 코드 위치

| 영역 | 위치 | 역할 |
|---|---|---|
| UI | `app/src/main/java/com/sonkkeut/app/MainActivity.kt` | Compose 화면·카메라·설정 |
| 앱 상태 | `NativeAppModel.kt` | 서버 연결, AI 작업, 세션 관리 |
| 주문 안내 | `NativeOrderFlow.kt`, `OrderDraft.kt` | 확인·수정·다중 메뉴·단계 진행 |
| 메뉴 연결 | `NativeMenuClient.kt`, `MenuKnowledgeStore.kt` | 서버 메뉴·기기 내 검색 보조 메모 |
| 화면 읽기 | `CheckoutReading.kt`, `ScreenOptions.kt` | 장바구니·완료 문구·읽을 수 있는 옵션 |
| AI 라이브러리 | 형제 AI 저장소 `android/sonkkeut-native/` | 영상·OCR·Whisper·메뉴 검색 |

파일명은 기준 브랜치의 구조입니다. 이전 `App.tsx`, `android/` 자료는 React Native 구현 기록이며 현재 Kotlin 앱 실행 경로와 구분합니다.

## 실제 기기 확인

카메라와 음성 기능에 필요한 권한을 허용하고 메뉴·설정에서 서버 주소·매장을 확인합니다. 공개 서버 주소는 임시 터널인 경우 바뀔 수 있으므로 백엔드 Actions 배포 요약을 기준으로 입력합니다. 첫 음성 모델 다운로드는 약 485MB이고 설치·압축 해제를 위한 추가 공간이 필요합니다. 모델은 백엔드가 중계하지 않고 AI 릴리스에서 받습니다.

설치 후 메뉴 수신, 카메라 시작·중지·재개, 한 메뉴/여러 메뉴 확인, 잘못 인식된 메뉴 수정, 장바구니 불일치, 화면 완료 읽기를 점검합니다. 오프라인에서는 저장된 메뉴를 사용할 수 있지만 가격·품절은 최신이 아닐 수 있습니다.

카메라 종료 문제가 발생하면 실제 기기의 `adb logcat`으로 해당 시점의 예외를 확보합니다. PC가 원격 환경이면 휴대폰이 물리적으로 연결된 PC에서 ADB를 실행해야 합니다.

## 검증 기록

0.2.3의 기록은 해당 구현 브랜치의 `docs/knowledge-checkout-validation.json`과 릴리스 첨부 파일에 있습니다. 단위 테스트 107개·Android 검사 23개는 해당 빌드의 검증 결과이며 모든 버전이나 실물 환경에 일반화하지 않습니다. 실제 마이크·매장·접근성 사용자 검증은 별도로 필요합니다.
