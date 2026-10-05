# Kotlin·UI 통합 APK 검증 — 2026-10-06

- 소스: `develop_kotlin_ui_integration` / `9dae9db`, AI `d1685e9`
- APK: `sonkkeut-kotlin-ui-integration.apk`, Kotlin 0.2.0 / code 10, ARM64, 개발용 서명
- 크기: 65,197,277바이트
- SHA-256: `dfa21dc7be93a1a55da423e929c339c401fb21920cac0a101e817216e380e76d`
- release·debug·Android 검사 APK 빌드 성공. 에뮬레이터에서 기존 앱 위 업데이트 설치 성공.

## 확인 결과

단위 테스트 67개 통과. Android 15 / Sonkkeut_Event_Test / 1080×2400 / 4KB 페이지 에뮬레이터에서 Android 검사 12개 통과, 음성 추론 1개 생략. 실제 Whisper 가중치와 음성 입력 fixture를 제공하지 않아 생략했으며 음성 성능 통과로 간주하지 않는다.

카메라 프레임 처리·중지·재개, 주문 입력 중 카메라 유지, 명시적 주문 확인, 설정 진입 시 중지, 안내 중 서버·매장 변경 금지, 종료 시 세션 초기화를 확인했다. 영상/OCR 모델·메뉴 DB fixture 검사와 실제 서버 메뉴·상태 연결도 통과했다. release APK의 실행·시스템 큰 글자·설정 복원을 화면 캡처로 확인했다. 앱 크래시 기록은 발견하지 않았다.

## 발견한 표시 문제

1. 시스템 글자 크기 2배에서 주문 입력 패널을 열면 카메라가 매우 좁아지고 카메라 위 안내 문구가 잘린다. 시작·중지·재안내 버튼은 보이지만 전체 화면이 정상이라고 판정하지 않는다.
2. 밝은 테마에서 노란색 메뉴 글자의 대비를 개선해야 한다.

두 문제는 이번 빌드·검증 단계에서 기록했으며 앱 코드 수정은 수행하지 않았다. 시스템 글자 크기는 기존 1.0으로 복원했다.

## 남은 실물 검증

실제 한국어 녹음·Whisper 추론, 초광각 렌즈, 화면 변화에 따른 실제 재인식, 손끝 안내와 전체 키오스크 주문, 이전 앱 설정 자동 이전은 미검증이다. 에뮬레이터 카메라는 가상 장면이며 실제 시각장애인 사용성이나 주문 완료를 입증하지 않는다.

검증 후 개발용 APK를 [포크 저장소의 사전 릴리스](https://github.com/hy2oni/sonkkeut-frontend/releases/download/kotlin-ui-integration-20261006/sonkkeut-kotlin-ui-integration.apk)로 공개했다. 표시 문제는 수정하지 않고 기록만 유지했다. README와 이 검증 기록은 `develop_kotlin_ui_integration` 브랜치에 포함한다. 다른 브랜치와의 추가 병합은 수행하지 않는다.
