# 0.3.0 Git·배포 준비

2026-10-06 확인. 이 문서는 실행 전 준비 자료이며 이번 작업에서 커밋·푸시·태그·릴리스·원본 브랜치·PR을 생성하지 않았다.

## 확인한 기준

| 항목 | 값 |
|---|---|
| 현재 작업 브랜치 | `develop_ui_update` |
| 3차 시작 HEAD / origin HEAD | `7855efa68a5aece8673ae3269b081546605d5118` |
| 포크 origin | `https://github.com/hy2oni/sonkkeut-frontend.git` |
| 원본 upstream | `https://github.com/fingertip-vision/sonkkeut-frontend.git` |
| 제품 기준 / 확인한 upstream develop_ui/ux | `dc9fdc7118687074cd0c34db0ba87cf425e63fa6` |
| 새 원본 PR 대상 | `develop_ui/ux_v2` (확인 시 아직 없음) |
| PR source | `hy2oni:develop_ui_update` |
| GitHub 권한 | 현재 인증 계정의 원본 push/admin 권한 확인 |

현재 3차 변경은 작업 트리에 있으며 커밋 SHA는 아직 없다. 배포와 PR 생성 직전에 다시 fetch/상태 확인하고 최종 SHA를 기록해야 한다. 다른 브랜치의 변경을 가져오거나 병합하지 않는다.

## 실행 순서

1. `git status --short --branch`, `git diff --check`, 의도한 변경 목록과 테스트·APK 해시를 확인한다. APK·빌드 로그·임시 AVD는 `artifacts/`에 두고 커밋하지 않는다.
2. `develop_ui_update`의 3차 소스·테스트·README·검증 문서·화면 캡처·Maestro 흐름만 커밋한다. 권장 메시지: `feat(android): complete Signal integration for 0.3.0`.
3. origin의 `develop_ui_update`에 일반 push한다. 강제 push하지 않는다. 직후 로컬/원격 HEAD 일치를 확인한다.
4. 원본 `develop_ui/ux_v2`가 여전히 없는지 확인한다. 없는 경우 **제품 기준 `dc9fdc7118687074cd0c34db0ba87cf425e63fa6`**에서 생성한다. 이미 있으면 SHA와 이력을 확인하고 덮어쓰지 않는다. 통합 완료 커밋으로 base를 만들면 PR 차이가 없어지므로 그렇게 하지 않는다.
5. 원본 저장소에서 base=`develop_ui/ux_v2`, head=`hy2oni:develop_ui_update`로 PR을 생성한다. 제목: `손끝길 0.3.0: Signal UI/UX 기능 통합`. 본문은 [PR_BODY.md](PR_BODY.md)를 사용한다. 생성 직후 실제 base/head를 다시 검증한다. PR 병합은 별도 작업이다.
6. 포크의 최종 커밋 SHA를 지정해 0.3.0 릴리스를 준비한다. 권장 태그 `develop-ui-0.3.0-20261006`은 중복 여부부터 확인한다. [RELEASE_NOTES.md](RELEASE_NOTES.md)와 검증한 `artifacts/sonkkeut-0.3.0.apk`를 사용한다. 기존 0.2.4 릴리스와 파일은 보존한다.
7. 공개 배포가 실제로 완료된 뒤 README의 다운로드 링크·배포 상태를 갱신한다. PR을 만들면 Codex 작업에도 연결한다.

PR 명령의 형태(준비용, 아직 실행하지 않음):

```powershell
gh pr create --repo fingertip-vision/sonkkeut-frontend --base develop_ui/ux_v2 --head hy2oni:develop_ui_update --title '손끝길 0.3.0: Signal UI/UX 기능 통합' --body-file docs/uiux-integration/stage3/PR_BODY.md
```

`main`, `develop`, `develop_ux`, `develop_ui`, 기존 `develop_ui/ux`, 디자인 보존 `develop_ui_design`에는 이 작업을 병합하지 않는다. React Native 코드·AI 저장소·기존 APK·사용 중인 기기 데이터도 유지한다.
