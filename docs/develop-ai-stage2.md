# develop_ai · AI 연결 2단계 프론트 연결

2026-10-06. 프론트 `develop_ai`, AI `d9938b2` 기준. QA 레이아웃은 보류한다.

## 실제 데이터 확인

기본 서버 `/api/stores/Z9XZSN/menu`가 HTTP 200, menu_version 1, 메뉴 3개를 반환했다. 메뉴 필드는 aliases/category/id/name/options/price/sold_out이며 related_terms/relatedTerms/description은 현재 없다. 새 관련 표현·설명 추천의 실데이터 효과를 검증 완료로 간주하지 않는다. 임의 관련 표현·설명은 추가하지 않았다.

## 연결한 내용

- NativeMenuDocuments: 서버 메뉴의 선택적 related_terms(또는 relatedTerms)·description을 MenuDocument로 전달한다. 누락·null이면 빈 값이며 관련 표현은 최대 100개/각 80자, 설명은 2000자까지 검증한다. 관련 표현을 주문 별칭으로 승격하지 않는다.
- NativeMenuClient: 기존 메뉴 응답과 원문 오프라인 캐시를 유지하면서 위 파서를 사용한다. 캐시 원문에도 선택적 메타데이터가 보존된다.
- NativeAppModel: SQLite의 매장별 스냅샷을 읽은 뒤 같은 메뉴 이름의 현재 카탈로그에서 관련 표현·설명·가격을 복원한다. 기존 AI SQLite 코드를 수정하지 않는다. 추천은 현재 매장의 MenuDocument 목록으로 실행한다.
- 정식 이름 충돌: 다른 메뉴의 정식 이름과 같은 별칭만 제외한다. 품절 메뉴의 정식 이름도 예약하며 일반 중복 별칭의 모호성은 유지한다. 서버 메뉴 적용·음성 보정 입력·직접 주문 파서에 동일 규칙을 적용한다.
- 후보 선택은 기존대로 입력 초안만 제공하며 수량·옵션 확인 전에는 자동 안내하지 않는다.

## 확인 결과와 남은 단계

`:app:testDebugUnitTest` 성공, 기존 JVM 회귀 검사 74개 통과·실패 0. 새 AI 모듈과 프론트 Kotlin 컴파일도 성공했다. 새로운 메타데이터·정식 이름 충돌의 전용 검사와 Android 실행 검사는 3단계에서 수행한다. 실제 서버에 관련 표현·설명이 등록되지 않아 실데이터 추천 효과는 아직 미검증이다.

APK 빌드·설치·버전 변경·README 변경·커밋·푸시·배포·develop_ux 병합은 하지 않았다. 보류 QA와 주문 확인 팝업 검토안은 stage1 기록을 유지한다.
