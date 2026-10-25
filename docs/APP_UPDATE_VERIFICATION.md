# 앱 업데이트 구현 검증 기록

- 검증일: 2026-10-25 (Asia/Seoul)
- 기준 커밋: `a4a6dc6898b82d168c1629c8f19e4fc8692eb630`
- 브랜치: `ax/sstc-sync/app-update`
- SST-AX Task: `sstc-feature-20261025-0001`
- 실행: 사용자가 이번 작업에 한해 채팅 승인으로 구현·검증을 허용한 수동 실행.
  SST-AX Slack Gateway 승인 또는 승인된 Worker 실행으로 표시하지 않는다.

## 확인한 결과

| 검증 | 결과 | 범위 |
|---|---|---|
| JDK 17 `testDebugUnitTest assembleDebug` | 통과, 단위 테스트 27개 | 버전·메타데이터·해시·패키지·SDK·서명 인증서 비교 및 기존 회귀 |
| JDK 17 `assembleRelease` | 통과 | 서명 없는 release APK 생성. 실제 배포 서명 아님 |
| JDK 17 `lintDebug` | 통과 | 새 설치 관리자 조회의 package visibility 선언 포함 |
| Linux/WSL `python3 -B -m unittest discover -s tests -p 'test_*release.py' -v` | 30개 통과 | 실제 서명·공개 대신 합성 도구/CLI 응답 사용 |
| actionlint 1.7.7 | 통과 | `.github/workflows/sstc-package.yml`, shellcheck 미사용 |
| 공식 SDK 37.0.0 검증 JAR | 고정 SHA-256과 일치 | Google 공식 Linux SDK 아카이브를 체크섬 확인 후 검사 |
| API 35 기기 테스트 | 4개 통과, skip 없음 | 실제 APK 파싱·다운로드 검사·FileProvider·취소 정리; HTTP만 fixture |
| 실제 Android 설치 출처 권한 | 거절·허용 확인 | 설정에서 실제 허용 스위치 사용. 보호 기능 비활성화 없음 |
| 실제 Android 설치 취소·재시도 | 확인 | 취소 후 `versionCode=1` 유지, 재시도 시 정상 업데이트 확인 화면 |

Android 검증에는 기존 `Pixel_9_API_35` AVD의 읽기 전용 임시 인스턴스 `emulator-5580`을 사용했다.
기존 AVD의 영구 사용자 데이터를 삭제하거나 초기화하지 않았다. 같은 개발용 debug 서명의
버전 코드 1과 2 APK를 사용했으며, 배포용 keystore를 읽거나 사용하지 않았다.

실행한 기기 테스트 클래스는 `AppUpdateDeviceTest`다.

1. 동일 버전 APK 거절과 부분 다운로드 파일 정리.
2. 손상된 APK와 HTTP 오류 거절.
3. 다운로드 취소 후 안내창이 다시 열리지 않고 부분 파일이 제거되는지 확인.
4. 공식 릴리스 형식의 fixture 탐지 → 새 APK 다운로드 → 실제 APK/인증서 비교 →
   content URI 읽기 확인 → 전경 Activity에서 설치 관리자 실행 → 설치 관리자 읽기 권한과 화면 확인.

공식 도구 검증값:

```text
SDK archive: build-tools_r37_linux.zip
SDK archive SHA-1 (Google repository metadata): 70954e99f4c3d9d46ee70fa32624672fe7cd6ebe
apksigner.jar SHA-256: 2defad215d7ff52968a409cde528cdaef7918b115e276b8e3378ca7a178e4180
```

기기 화면·계층 증적은 작업 worktree의 `app/build/update-flow/permission.png`, `confirm.png`와
각 XML에 보관했다. 이 임시 build 산출물은 Git에 커밋하지 않는다.

## 실제 설치와 원격 배포의 한계

Android의 `Do you want to update this app?` 화면에서 업데이트를 진행하자 Google Play Protect가
처음 보는 APK의 외부 전송·보안 검사를 요청했다. 자동 승인 검토가 명시적 외부 전송 승인이
없다는 이유로 해당 검사를 거절했고, 사용자는 **외부 전송 없이 설치 직전까지 검증**을 선택했다.
APK 전송, 보호 검사 우회, 최종 설치는 수행하지 않았다.

따라서 설치 후 `versionCode=2` 상승과 사용자 데이터 보존은 **미검증**이다.
테스트용 보존 표식은 준비했지만 설치가 완료되지 않았으므로 보존 성공 증거로 사용하지 않는다.

현재 GitHub API에서 이 저장소의 공개 Release 목록이 비어 있음을 확인했다.
실제 GitHub Actions 실행, 배포용 secret/keystore의 유효성, Release 업로드·공개,
GitHub CDN을 경유한 앱 업데이트는 검증하지 않았다. 태그나 Release도 만들지 않았다.

추가 수동 검증 대상은 API 26 및 다른 기종, 앱 안내창의 가로·세로 회전과 큰 글꼴,
설정 복귀 시 앱 안내창의 전체 흐름, 정상 release 서명 APK 간 업데이트, 기존 서버 데이터 유지다.
실제 공개 검증은 사람이 PR 검토·머지와 배포 버전을 결정한 뒤 [운영 절차](APP_UPDATES.md)를 따른다.

## PR #15 후속 타임아웃 교정 (2026-10-26)

- 후속 Task: `sstc-feature-20261026-0001`.
- 사용자 지정: 전체 다운로드 3분, 연결 대기 10초, 데이터 무응답 1분.
- 다운로드 전용 CIO 클라이언트의 실제 HTTP 제한을 설정하고, 다운로드·검증의
  coroutine 상한도 3분으로 맞춘다. 기존 메타데이터 조회 클라이언트는 유지한다.
- 추가 JVM 회귀 범위는 실제 CIO의 15초 초과 본문 전송과 전체 요청 제한·무응답 제한이다.
  실행 결과는 해당 후보 SHA의 CI 및 후속 Task 기록으로 확인한다.
- 위의 초기 기기 검증 4개를 이번 교정 이후에 다시 실행한 것으로 표시하지 않는다.
  최종 설치·데이터 보존·실제 Release 공개의 미검증 범위도 그대로 유지한다.
