# 손끝길 Kotlin Android 앱 · 0.2.3

현재 빌드는 [매장 검색 DB·주문 확인](knowledge-checkout.md)을 포함한다. 현재 검증 기록은 `knowledge-checkout-validation.json`이다.

0.2.2의 주문 목록·추가 옵션·음성 확인 변경과 검증 범위는 [추가 구현 문서](order-improvements.md)를 참고한다. 아래 0.2.1 커밋·검사 수는 이전 릴리스 기록이며, 현재 로컬 결과는 `order-improvements-validation.json`에 별도 기록한다.

이 브랜치의 기본 앱은 **Kotlin + Jetpack Compose + CameraX**다. 프론트 화면·음성 결과·주문 해석·주문 확인·버튼 계획·메뉴 검색을 Kotlin으로 실행한다. 빌드와 실행에 npm, Node, Metro, React Native가 필요하지 않다.

## Android Studio 열기

두 레포를 같은 부모 폴더 아래에 배치한다.

```text
workspace/
  sonkkeut-frontend/     ← Android Studio에서 이 폴더를 Open
  sonkkeut-ai/
```

프론트는 `native/kotlin-20261005`, AI는 `codex/unified-ai-20261003` 브랜치가 필요하다. 프론트 루트의 `settings.gradle.kts`가 AI 레포의 `android/sonkkeut-native` 모듈을 직접 포함한다. SDK 35, JDK 17, Gradle 8.10.2, AGP 8.7.2, Kotlin 1.9.25 / Compose compiler 1.5.15를 사용한다. Android Studio가 SDK 경로를 지정한 `local.properties`를 만든 뒤 `app` 실행 구성을 선택한다.

0.2.3 APK의 AI 소스 기준 커밋: `d9938b2f3e999b093942c4a882264a3fbc2ca8b8`. 이전 0.2.1 기준은 `80d3dfcdaec24144334e1236319237bf44651154`이다.

```powershell
./gradlew.bat :app:assembleRelease :app:testDebugUnitTest
./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest
```

명령행에서는 `ANDROID_HOME`과 `JAVA_HOME`을 실제 설치 위치로 설정한다. 모델과 Android 의존성의 첫 준비에는 인터넷이 필요하다. 루트 `app/`가 Kotlin 앱이며, `android/`와 TypeScript 파일은 이전 구현의 비교 자료다. 이 앱의 빌드에 포함되지 않는다.

## 기능 연결

| 영역 | Kotlin 구현 |
|---|---|
| 메인·메뉴·주문·설정 | `MainActivity.kt`, Compose |
| 카메라 | CameraX `ImageAnalysis`, 1280×960 우선/미지원 시 대체 해상도, FIT_CENTER 전체 프레임, 분석 스레드 |
| 영상·OCR·손끝 | `sonkkeut-native` → 기존 실제 ONNX·ML Kit·MediaPipe 엔진 |
| 음성 | 자체 CT2 Whisper v3, 완료/취소, 선택형 기기 음성 인식 |
| 메뉴 검색 | 매장별 SQLite DB → `MenuSpeechIndex`, 한글 이름·별칭·발음 유사도 |
| 주문 | `NativeOrderParser`, `NativeOrderFlow`, 확인 전 목표 생성 금지 |
| 서버 | `NativeMenuClient`, 시작·재연결 시 자동 동기화, 매장별 오프라인 캐시 |
| 출력·설정 | 고대비 기본 화면·밝은 테마·글자 크기·음성·진동·속도·재안내 |
| 통계 | 기본 꺼짐, 동의한 경우 집계 결과만 전송, 동의 해제 시 대기 기록 삭제 |

메인 화면은 스크롤하지 않으며 카메라와 안내 중지/계속·재안내를 표시한다. 주문·화면 읽기·설정은 별도 페이지다. 화면 읽기에서 선택한 버튼은 메인으로 돌아온 뒤 **새 프레임에서 같은 글자의 버튼을 다시 확인**해 좌표를 연결한다. 페이지 이동·중지 이전의 영상/음성 결과는 무효화한다.

미등록 메뉴·지원하지 않는 옵션·품절·불확실한 OCR은 자동 선택하지 않는다. 장바구니 수량·금액 확인과 중단된 담기 결과 검사를 유지한다. 모델 가중치는 그대로이며 이전 앱과 같은 application ID `com.sonkkeut`, 개발 서명, 더 높은 versionCode 11을 사용해 업데이트 설치한다. Android namespace와 Activity는 `com.sonkkeut.app`다.

## 범위와 검사 기록

0.2.1은 화면별 음성 대화·탐색·근접 OCR을 포함한다. [음성 상호작용 문서](voice-interaction.md)에 모듈·기준·사용법을 기록했다. LLM·신경망 임베딩은 포함하지 않는다. 음식 개념 벡터 추천을 추가했으며 ‘크림 파스타’에 대해 실제 매장의 ‘까르보나라’를 관련 후보로 제안할 수 있지만 사용자 확인 없이 바꾸지 않는다. 새 플랫폼의 실제 마이크 정확도는 별도 측정해야 하며 이전 PC 합성 음성 평가를 휴대폰 성능으로 주장하지 않는다.

Android 에뮬레이터에서 실제 모델·OCR·JNI·메뉴 DB·네이티브 UI를 검사한다. S26 Ultra 실물의 카메라·마이크·진동·TalkBack 사용성은 미검증이다. 기존 AWS 서버는 그대로 사용한다.

검사 결과: JVM 단위 테스트 83개, API 35 에뮬레이터 테스트 18개(실제 Whisper JNI, OCR·영상 모델, SQLite, 자동 연결, 주문 확인, 카메라 중지·재개)가 통과했다. APK 검사에서 React 클래스·Hermes·JavaScript 번들이 없고 기존 모델·Whisper JNI·개발 서명이 동일함을 확인했다. 음성 JNI 검사는 합성 PCM 입력이며 실제 마이크 정확도 검사가 아니다.
