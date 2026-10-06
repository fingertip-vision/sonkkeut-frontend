# 0.3.0 Git·배포 기록

2026-10-06 사용자 요청에 따라 커밋·푸시·태그·공개 릴리스와 지정 원본 브랜치·PR 생성을 완료했다. PR 병합은 하지 않았다.

- 배포 코드 커밋: `6e66ec232e556d70c2543e68593427500692eec5` (`feat(android): complete Signal integration for 0.3.0`).
- 포크 push: `hy2oni/sonkkeut-frontend:develop_ui_update`.
- 원본 `develop_ui/ux_v2`는 제품 기준 `dc9fdc7118687074cd0c34db0ba87cf425e63fa6`에서 생성했다.
- [PR #9](https://github.com/fingertip-vision/sonkkeut-frontend/pull/9): `hy2oni:develop_ui_update` → `fingertip-vision:develop_ui/ux_v2`, OPEN.
- [공개 릴리스](https://github.com/hy2oni/sonkkeut-frontend/releases/tag/develop-ui-0.3.0-20261006): 태그 `develop-ui-0.3.0-20261006`, 위 배포 코드 커밋을 정확히 지정했다.
- 첨부 파일은 `sonkkeut-0.3.0.apk` 한 개다. GitHub 업로드 digest와 검증 파일 해시 일치를 확인했다.
- 배포 후 다운로드 링크·상태 문서를 추가 커밋하므로 브랜치 최신 SHA와 릴리스 코드 SHA는 다를 수 있다. 그 후속 변경은 문서·배포 메타데이터만 포함하며 APK를 다시 빌드하거나 태그를 이동하지 않는다.

## 확인한 기준

| 항목 | 값 |
|---|---|
| 현재 작업 브랜치 | `develop_ui_update` |
| 3차 시작 HEAD / origin HEAD | `7855efa68a5aece8673ae3269b081546605d5118` |
| 포크 origin | `https://github.com/hy2oni/sonkkeut-frontend.git` |
| 원본 upstream | `https://github.com/fingertip-vision/sonkkeut-frontend.git` |
| 제품 기준 / 확인한 upstream develop_ui/ux | `dc9fdc7118687074cd0c34db0ba87cf425e63fa6` |
| 새 원본 PR 대상 | `develop_ui/ux_v2` (제품 기준 SHA에서 생성 완료) |
| PR source | `hy2oni:develop_ui_update` |
| GitHub 권한 | 현재 인증 계정의 원본 push/admin 권한 확인 |

위 시작 기준은 3차 작업 전 기록이다. 원격을 다시 확인한 뒤 지정 브랜치에만 일반 push했으며 강제 push·다른 브랜치 병합을 하지 않았다.

## 사용한 실행 절차 (재실행 전 기존 항목 확인)

1. `git status --short --branch`, `git diff --check`, 의도한 변경 목록과 테스트·APK 해시를 확인한다. APK·빌드 로그·임시 AVD는 `artifacts/`에 두고 커밋하지 않는다.
2. `develop_ui_update`의 3차 소스·테스트·README·검증 문서·화면 캡처·Maestro 흐름만 커밋한다. 권장 메시지: `feat(android): complete Signal integration for 0.3.0`.
3. origin의 `develop_ui_update`에 일반 push한다. 강제 push하지 않는다. 직후 로컬/원격 HEAD 일치를 확인한다.
4. 원본 `develop_ui/ux_v2`가 여전히 없는지 확인한다. 없는 경우 **제품 기준 `dc9fdc7118687074cd0c34db0ba87cf425e63fa6`**에서 생성한다. 이미 있으면 SHA와 이력을 확인하고 덮어쓰지 않는다. 통합 완료 커밋으로 base를 만들면 PR 차이가 없어지므로 그렇게 하지 않는다.
5. 원본 저장소에서 base=`develop_ui/ux_v2`, head=`hy2oni:develop_ui_update`로 PR을 생성한다. 제목: `손끝길 0.3.0: Signal UI/UX 기능 통합`. 본문은 [PR_BODY.md](PR_BODY.md)를 사용한다. 생성 직후 실제 base/head를 다시 검증한다. PR 병합은 별도 작업이다.
6. 포크의 최종 커밋 SHA를 지정해 0.3.0 릴리스를 준비한다. 권장 태그 `develop-ui-0.3.0-20261006`은 중복 여부부터 확인한다. [RELEASE_NOTES.md](RELEASE_NOTES.md)와 검증한 `artifacts/sonkkeut-0.3.0.apk`를 사용한다. 기존 0.2.4 릴리스와 파일은 보존한다.
7. 공개 배포가 실제로 완료된 뒤 README의 다운로드 링크·배포 상태를 갱신한다. PR을 만들면 Codex 작업에도 연결한다.

PR 생성에 사용한 명령:

```powershell
gh pr create --repo fingertip-vision/sonkkeut-frontend --base develop_ui/ux_v2 --head hy2oni:develop_ui_update --title '손끝길 0.3.0: Signal UI/UX 기능 통합' --body-file docs/uiux-integration/stage3/PR_BODY.md
```

`main`, `develop`, `develop_ux`, `develop_ui`, 기존 `develop_ui/ux`, 디자인 보존 `develop_ui_design`에는 이 작업을 병합하지 않는다. React Native 코드·AI 저장소·기존 APK·사용 중인 기기 데이터도 유지한다.
