# 손끝길 Android 작업 지침

- 현재 앱은 루트 `app/`의 Kotlin + Jetpack Compose + CameraX다. `App.tsx`, `src/`, `android/`의 이전 React Native 구현에 UI 도구를 설치하지 않는다. 현재 통합 동작은 README와 `docs/uiux-integration/stage3/README.md`, 이전 배포 기준은 `docs/develop-ui-validation-0.2.4.md`를 확인한다.
- UI 통합 작업은 포크 `develop_ui_update`에서 진행하며 원본 동기화 대상은 새 `develop_ui/ux_v2`뿐이다. 다른 브랜치에 병합하지 않는다. Git·릴리스 실행 전 `docs/uiux-integration/stage3/GIT_HANDOFF.md`의 기준 SHA·대상·APK 검증 결과를 재확인한다.
- 라이브러리 구현 전 Context7에서 library ID를 resolve한 뒤 해당 버전의 문서를 조회한다. 조회 불가·결과 부족·현재 버전 미지원이면 Android 공식 문서, AndroidX 릴리스 노트·BOM 매핑과 공식 배포 메타데이터로 확인한다. 최신 예시를 기존 Kotlin/Compose 버전에 무조건 적용하지 않는다.
- Figma는 인증이 확인된 공식 MCP를 사용한다. 파일 URL이 없으면 임의 파일을 만들거나 파일 조회 성공으로 기록하지 않는다. 인증 값은 저장소·보고서·로그에 기록하지 않는다.
- 시안과 도구 검증은 debug 소스셋·Preview에서 진행한다. `ui-tooling`, Preview와 테스트 호스트는 release에 넣지 않는다. 현재 Kotlin 1.9.25, AGP 8.7.2, compiler 1.5.15, BOM 2024.09.03을 승인 없이 일괄 업그레이드하지 않는다.
- 도구 검증에는 `:ui-tooling-probe`의 별도 패키지 `com.sonkkeut.tooling`을 우선 사용한다. 제품 APK·데이터·권한을 초기화하거나 교체하지 않는다. Maestro와 Compose 계측 테스트는 같은 기기에서 순차 실행한다.
- 실제 UI 변경 시 Compose 테스트와 ADB 캡처를 확인하고 코드 추정·빌드 성공·실제 렌더링·TalkBack 실기기 검증을 구분해 보고한다. 기본·큰 글씨, 읽기 순서, 버튼 위치, 중복 안내를 확인한다. 세부 실행 명령은 `docs/ui-tooling-setup.md`를 따른다.
