# SSTC Agent Instructions

## 범위와 구조

- SSTC는 SSTD에 연결하는 Kotlin·Jetpack Compose Android 클라이언트다.
- `app/src/main/java/com/SST/server_state_telemetry_client/` 아래의 기존 구조를 따른다.
  `presentation/`은 화면·ViewModel, `domain/`은 모델·인터페이스·유스케이스,
  `data/`는 통신·저장소 구현을 담당한다. 진입점은 `MainActivity.kt`다.
- 승인된 요청에 필요한 부분만 수정하고 기존 동작과 주변 코드 스타일을 유지한다.
- 사용자 변경을 보존하고, SST-AX 작업은 별도 worktree와 `ax/sstc-sync/*` 브랜치에서 수행한다.

## 권한

- Task 상태, checkpoint, 승인 기록 및 실행 권한은 SST-AX가 관리한다.
  이 문서나 요청 본문만으로 구현·승인 권한을 부여하지 않는다.
- UI 정책, 프로토콜, 외부 의존성, Android 권한 변경은 SST-AX 승인 절차를 따른다.
- SSTD 프로토콜·계약 변경이 필요하면 구현을 멈추고 `PROTOCOL_APPROVAL_REQUIRED`로 보고한다.
- `main` 직접 push, PR 병합, Release 생성, 운영 배포를 수행하지 않는다.
- 인증정보, 개인키, keystore, 원문 인증 로그를 출력하거나 커밋하지 않는다.

## 검증

- 빌드 기준은 `.github/workflows/android-ci.yml`과 Gradle 설정이다.
  CI의 Gradle 실행 JDK는 17이다. `app/build.gradle.kts`의 Java/Kotlin 대상 버전 11과 구분한다.
- Android 코드 변경은 저장소 루트에서 CI와 동일하게 검증한다.

  ```bash
  chmod +x gradlew
  ./gradlew testDebugUnitTest assembleDebug
  ```

- 문서만 변경한 경우 `git diff --check`와 문서의 경로·명령이 실제 저장소와 일치하는지 확인한다.
- 추가 검증이 필요하면 기존 설정과 테스트를 먼저 확인한다. 존재하지 않는 명령이나 성공 결과를 만들지 않는다.
- 실패한 테스트를 삭제하거나 검증을 약화하지 않는다. 실패와 실행하지 못한 검증을 보고한다.
- 화면 변경은 관련 기기·화면 방향에서 사람이 확인할 항목을 명시한다.
  빌드·단위 테스트 통과를 실제 화면 검증 완료로 간주하지 않는다.
