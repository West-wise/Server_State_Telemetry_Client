# SST-AX Task 원격 빌드 검증

OCI의 ARM 환경에서 Android 빌드 도구를 설치하는 대신 GitHub Actions의
`SSTC Task Validation` workflow로 후보 커밋을 검증한다.
기존 `Android CI`의 PR/main 자동 검증은 유지한다.

## 실행 기준

- workflow는 `main`에서 수동 실행한다. 다른 브랜치나 태그 실행은 차단한다.
- `task_id`: SST-AX Task ID. `sstc-feature-YYYYMMDD-NNNN` 또는
  `sstd-sync-YYYYMMDD-NNNN` 형식이다.
- `candidate_sha`: SSTC 저장소에서 접근 가능한 후보 커밋의 40자리 소문자 SHA.
  브랜치명, 축약 SHA, 미커밋 diff는 받지 않는다.
- JDK 17에서 기존 CI 명령인 `chmod +x gradlew`와
  `./gradlew testDebugUnitTest assembleDebug`를 실행한다.
- 인증정보를 checkout에 남기지 않고, 후보 빌드의 Gradle 캐시를 공유하지 않는다.
  별도 secret, Release, 서명, 배포 설정은 필요하지 않다.

## 최초 실행

이 workflow가 기본 브랜치에 병합된 뒤 수동 실행할 수 있다.

1. GitHub의 **Actions → SSTC Task Validation → Run workflow**를 연다.
2. 실행 브랜치는 `main`을 선택한다.
3. Task ID와 검증할 후보 commit SHA를 입력한다.
4. 실행 페이지에서 `prepare`, `build`, `receipt` 결과를 확인한다.

최초 동작 확인에는 전용 검증 Task ID와 병합된 `main` 커밋 SHA를 사용한다.
기존 실패 Task의 미커밋 변경은 포함되지 않는다. 그 변경을 검증하려면
SST-AX의 승인·복구 절차로 후보 커밋을 준비해야 한다.

## 결과 JSON과 SST-AX 연결

`sstc-validation-<run_id>-<run_attempt>` artifact의 `validation.json`에는
Task ID, 저장소, 후보 SHA, workflow ref/SHA, run ID/attempt,
`build_result`, 검증 명령과 JDK 버전을 기록한다.
결과는 후보 코드와 분리된 새 runner에서 생성하며 후보 파일을 읽지 않는다.
빌드 실패도 결과 JSON에 기록한다. 입력 검증 실패나 취소 시에는
artifact가 없을 수 있으므로 결과 부재를 성공으로 처리하지 않는다.

SST-AX Controller는 GitHub API의 실행 정보와 이 JSON을 함께 확인해야 한다.
수동 실행의 run head SHA는 workflow의 SHA이므로 후보 SHA와 같다고 가정하지 않는다.
Task ID·후보 SHA·workflow 경로와 SHA·run ID/attempt가 일치하고,
실행 완료 상태와 `build` job 및 전체 run의 성공을 확인한 경우에만 검증을 인정한다.
재실행은 같은 run ID여도 attempt가 다르므로 이전 artifact를 재사용하지 않는다.

이 workflow는 checkpoint, 승인 기록, Task 상태를 변경하지 않는다.
`authorization: NONE`이며 구현이나 Draft PR 생성 권한을 부여하지 않는다.
SST-AX의 요청·결과 확인 및 실패 Task 복구 연결은 별도 작업이다.
UI 변경의 실제 기기 세로·가로 확인은 사람이 수행한다.

기준: [GitHub 수동 workflow 실행](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow),
[workflow artifact](https://docs.github.com/en/actions/concepts/workflows-and-actions/workflow-artifacts).
