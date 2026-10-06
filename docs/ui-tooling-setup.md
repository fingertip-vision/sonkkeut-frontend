# UI 개발 도구 준비 · 2026-10-06

## 기준과 보존

작업 위치: `../sonkkeut-uiux-audit`, 브랜치 `codex/uiux-design-audit-20261006`, 기준 SHA `dc9fdc7118687074cd0c34db0ba87cf425e63fa6`.
이번 작업 전의 debug 디자인 비교 시안·캡처·문서는 그대로 보존했다. 원래 `../sonkkeut-frontend`와 `../sonkkeut-ai`의 tracked/untracked 변경은 없다.
현재 앱과 UX는 루트 `app/`, README, `develop-ui-validation-0.2.4.md` 기준이다. 이전 React Native에 의존성을 추가하지 않았다.

| 항목 | 현재 버전 |
|---|---|
| Gradle / AGP | 8.10.2 / 8.7.2 |
| Kotlin / Compose compiler | 1.9.25 / 1.5.15 |
| Compose BOM / UI·Animation / Material 3 | 2024.09.03 / 1.7.3 / 1.3.0 |
| CameraX / Lifecycle / Activity Compose | 1.4.1 / 2.8.7 / 1.9.3 |
| min / compile / target SDK | 26 / 35 / 35 |
| Java compile target / build runtime | 17 / Android Studio JBR 21.0.10 |

## 도구 설치·인증·검증

| 도구 | 위치·버전 | 설치·설정 | 인증 | 실제 검증 |
|---|---|---|---|---|
| Codex | `%LOCALAPPDATA%/OpenAI/Codex/bin/8aaf1547b825b104/codex.exe`, CLI 0.160.0 | 기존 재사용 | 기존 계정 | `mcp list`, `mcp get` 확인 |
| Context7 | `https://mcp.context7.com/mcp`, 호스팅 버전 미노출 | 사용자 `~/.codex/config.toml`의 `mcp_servers.context7`만 추가 | 공식 OAuth 완료, Codex secure credential store 사용 | resolve-library-id와 query-docs 실제 성공. Android 공식 AnimatedContent·animateColorAsState 문서 반환 |
| Figma 공식 MCP | `https://mcp.figma.com/mcp`, 호스팅 버전 미노출 | 사용자 설정의 `mcp_servers.figma` 추가 | 공식 OAuth 완료 | `whoami` 계정 조회 성공. 디자인 파일 URL 미제공으로 디자인 조회 미수행 |
| Figma 공식 플러그인 | `~/.codex/plugins/cache/openai-curated-remote/figma/15.0.0` | 사용자 설치 완료 확인, 추가 재설치 없음 | 플러그인 connector의 인증 조회는 완료 확인 안 됨 | connector whoami는 반복해서 인증 요청 응답. 검증된 공식 MCP 경로로 사용 가능 |
| Material 3 | Gradle cache, 1.3.0 | 기존 그대로 | 불필요 | 제품 debug/release, 실제 probe 렌더링 성공 |
| Animation / Preview tooling | Gradle cache, BOM의 1.7.3 | Animation 명시 선언, Preview/tooling debugImplementation 추가 | 불필요 | Preview 함수 컴파일, 기기 렌더링·상태 전환 성공. Android Studio Preview 패널 렌더는 미수행 |
| Lottie Compose | Gradle cache, 6.6.0 | 공식 Maven Central 의존성 추가 | 불필요 | debug/release 빌드 성공, release DEX에 패키지 확인. 에셋 미제공으로 재생 미수행 |
| SDK / ADB / Emulator | `%LOCALAPPDATA%/Android/Sdk`, API 35 / platform-tools 37.0.0 / emulator 36.6.11.0 | 기존 재사용 | 불필요 | `emulator-5554`, Sonkkeut_Event_Test, API 35; APK 실행·ADB PNG/XML 캡처 성공 |
| Maestro | `../.local-tools/maestro-2.11.0/maestro/bin/maestro.bat`, 2.11.0 | 공식 Windows ZIP 설치, SHA-256 검증 | 로컬 실행은 계정 불필요 | 버전 실행, 기기 연결, 비파괴 smoke 1/1 통과 |
| Compose UI test | 기존 ui-test-junit4 + 별도 probe 테스트 | 기존 버전 재사용 | 불필요 | 격리 패키지의 계측 1개 통과, 실패·생략 0 |

Context7 설치에는 공식 Codex CLI `mcp add`를 사용했으며 로컬 npm 서버를 중복 설치하지 않았다. 기존 MCP·Codex 항목은 보존했다. OAuth 비밀 값을 프로젝트·보고서에 기록하지 않았다.
Figma 플러그인은 인증 중 사용자가 설치한 경로다. 플러그인 connector 인증은 직접 MCP 인증과 별개이므로 성공으로 혼동하지 않는다. 현재 검증된 실행 경로는 직접 MCP다. 플러그인 연결까지 사용하려면 Codex 플러그인 설정의 계정 연결을 완료한 뒤 connector 계정 조회를 확인한다. 필수 MCP 인증은 완료되어 개발을 막지 않는다.

## 변경 내용

- `app/build.gradle.kts`: `animation` (BOM), `com.airbnb.android:lottie-compose:6.6.0`, debug 전용 `ui-tooling-preview`, `ui-tooling`.
- Material 3, Compose BOM, compiler, Kotlin, AGP, CameraX와 기존 ui-test-manifest를 변경·중복 선언하지 않았다.
- `app/src/debug/java/com/sonkkeut/app/tooling/UiToolingProbe.kt`: 제품과 독립된 Material 3 화면, AnimatedContent·400ms 색상 전환, 기본/2배 글씨 Preview. 헤딩·읽기 순서·polite live region과 64dp 버튼 포함. TalkBack 실사용 검증은 별도다.
- `settings.gradle.kts`, `ui-tooling-probe/`: 위 debug 소스만 재사용하는 검증 호스트. 패키지 `com.sonkkeut.tooling`, 제품 AI·카메라·음성·주문 서비스와 연결 없음. release variant 비활성.
- `tools/ui/environment.ps1`, `tools/ui/verify-unit.ps1`, `tools/ui/smoke.yaml`: 터미널 단위 환경, Windows 인코딩 대응 단위 테스트 명령과 비파괴 UI 흐름. 영구 PATH·SDK·Gradle 설정 덮어쓰기 없음.
- `AGENTS.md`: Android 앱 대상·문서 조회 fallback·debug/실기기 캡처 절차 기록.

## 빌드와 기기 검증

`app:assembleDebug`, `app:assembleRelease`, `ui-tooling-probe:assembleDebug`, `ui-tooling-probe:assembleDebugAndroidTest` 모두 성공했다. 제품 release 필수 lint도 성공했다.
기존 제품 JVM 테스트 83개도 최종 통과했다(실패·생략 0). 첫 기본 실행은 10개 테스트 클래스가 ClassNotFoundException으로 초기화에 실패했다. 기존 `compose-prototype.md`의 Windows 한글 경로 대응을 따라 `-Dorg.gradle.jvmargs=-Xmx3072m -Dfile.encoding=COMPAT`를 실행 명령에만 적용하자 통과했다. Gradle 설정 파일·제품 코드는 바꾸지 않았다. 로그는 `unit-tests.txt`, `unit-tests-compat.txt`; 재사용 명령은 `tools/ui/verify-unit.ps1`이다.
별도 APK만 기기에 설치했다. `com.sonkkeut`의 기존 APK를 교체하거나 데이터를 삭제하거나 권한을 초기화하지 않았다. 원래 APK와 설치된 제품 APK SHA-256은 모두 `12e705610533f7121fb8a1530961c75d847876d88031c47ba468441911a37c3f`; 버전·권한·codePath·dataDir 비교도 동일하다.
releaseRuntimeClasspath에는 `ui-tooling`, `ui-tooling-preview`, `ui-test-manifest`가 없다. release DEX에도 ComposeViewAdapter·UiToolingProbeActivity·DesignComparisonActivity가 없다. Lottie Compose는 라이브러리 준비 목적으로 포함된다.
Maestro 1개 흐름과 Compose 계측 1개 테스트를 실행했다. 상태 전환 후 완료 표시, 이전 상태 제거, 준비 상태 복귀를 확인했다. 같은 기기에서 검증 도구를 동시에 실행하지 않는다.

실행 증거는 ignored `artifacts/ui-tools/`에 보관한다: `ready.png`, `transition.png`, UI XML, `maestro-report.xml`, `build-and-release-dependencies.txt`, 제품 package before/after. Compose 계측 XML은 `ui-tooling-probe/build/outputs/androidTest-results/connected/debug/`.

## 호환성·미수행 항목

Lottie 6.6.0의 공식 POM은 Kotlin 1.9.22, Compose BOM 2024.02.01을 선언한다. 프로젝트에서 Kotlin 1.9.25와 BOM 2024.09.03으로 해석됨을 의존성 보고서·실제 빌드로 확인했다. 도구 체인 업그레이드는 없고 외부 애니메이션 에셋도 내려받지 않았다.
Material 3 stable 1.4.0의 릴리스 기록에는 ExperimentalMaterial3ExpressiveApi가 1.4 계열에서 제거되어 1.5 alpha 계열을 사용하도록 명시되어 있다. 현재 BOM의 1.3.0에는 해당 Expressive 도구를 도입하지 않는다. 일부 API의 alpha 내 안정화와 전체 라이브러리 stable 배포를 구분하며 알파·일괄 업그레이드를 보류한다.
Context7은 Preview 의존성 질문에 결과가 없었고, Android 공식 Preview 문서와 BOM 매핑으로 보완했다. 현재 버전의 문서가 없거나 결과가 부족한 다른 라이브러리도 공식 Android 문서·공식 배포 메타데이터로 확인한다.
디자인 파일 조회, Lottie 에셋 재생, Android Studio Preview 패널, TalkBack 실기기 사용, 실제 키오스크·AI·마이크 성능은 이번 도구 준비 검증에 포함되지 않는다.

## 다시 실행

```powershell
. ./tools/ui/environment.ps1
./gradlew.bat :app:assembleDebug :app:assembleRelease :ui-tooling-probe:assembleDebug
adb -s emulator-5554 install -r ui-tooling-probe/build/outputs/apk/debug/ui-tooling-probe-debug.apk
maestro --device emulator-5554 test tools/ui/smoke.yaml --format junit --output artifacts/ui-tools/maestro-report.xml --test-output-dir artifacts/ui-tools/maestro
./gradlew.bat :ui-tooling-probe:connectedDebugAndroidTest
./tools/ui/verify-unit.ps1
```

새 기기를 선택할 때 serial을 확인한다. Preview는 Android Studio에서 debug variant와 `UiToolingProbe.kt`를 연다. 제품 UI 개편에는 인증된 Figma MCP → Context7/Android 공식 문서 → Compose Preview·Animation → 별도 probe·Compose 테스트·Maestro → ADB 화면 확인 조합을 사용한다. Lottie는 라이선스가 확인된 에셋을 받은 뒤 재생 검증한다.

## 공식 근거

- [Codex MCP 설정·OAuth](https://learn.chatgpt.com/docs/extend/mcp?surface=cli)
- [Context7 공식 Codex 설정](https://github.com/upstash/context7/blob/master/docs/resources/all-clients.mdx)
- [Figma 공식 Codex 플러그인·MCP](https://developers.figma.com/docs/figma-mcp-server/remote-server-installation/)
- [Android Compose Preview](https://developer.android.com/develop/ui/compose/tooling/previews), [BOM 매핑](https://developer.android.com/develop/ui/compose/bom/bom-mapping), [Animation](https://developer.android.com/develop/ui/compose/animation/composables-modifiers)
- [Material 3 릴리스](https://developer.android.com/jetpack/androidx/releases/compose-material3)
- [Lottie 공식 소스](https://github.com/airbnb/lottie-android/tree/v6.6.0), [공식 Maven POM](https://repo.maven.apache.org/maven2/com/airbnb/android/lottie-compose/6.6.0/lottie-compose-6.6.0.pom)
- [Maestro Windows 설치](https://docs.maestro.dev/maestro-cli/how-to-install-maestro-cli), [2.11.0 배포](https://github.com/mobile-dev-inc/Maestro/releases/tag/cli-2.11.0). ZIP SHA-256: `5384593cb4e7a106489e75a821d157dd43f4e438df6bc308b72e82c685e1283a`.
