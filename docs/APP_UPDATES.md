# 자동 릴리스와 앱 내 업데이트

## 운영 절차

사람이 PR을 검토·머지한 뒤 `main`에 포함된 커밋에 `v<versionName>-<versionCode>` 태그를
등록한다. 예: `v1.1.0-2`는 표시 버전 `1.1.0`, Android 버전 코드 `2`다.
표시 버전은 숫자 세 부분으로 구성하며, 버전 코드는 기존 공개 릴리스보다 커야 한다.

```bash
# 검토·머지가 끝난 실제 배포 커밋에서 운영자가 실행한다.
git tag v1.1.0-2 <approved-main-commit>
git push origin v1.1.0-2
```

`SSTC Package`는 태그 커밋이 `main`에 포함되어 있는지 확인하고 다음을 수행한다.

1. JDK 17로 단위 테스트·디버그 빌드 후 태그의 버전으로 release APK를 빌드한다.
2. 고정된 Build Tools 37.0.0과 기존 서명 설정으로 서명·인증서·패키지·버전·정렬을 검증한다.
3. APK, `sstc-update.json`, `SHA256SUMS`, `packaging-verification.json`을 artifact로 저장한다.
4. 별도 `contents: write` job에서 기존 공식 릴리스의 메타데이터와 버전 증가를 확인한다.
5. 비공개 draft Release에 파일을 업로드하고 다시 다운로드한다.
6. 파일·해시·검증 기록의 일치를 확인한 뒤에만 공개한다. 실패하면 공개하지 않는다.

수동 `workflow_dispatch`는 기존처럼 `main`에서 패키징과 artifact 업로드까지만 수행한다.
에이전트의 빌드·테스트는 태그 생성이나 실제 Release 실행을 포함하지 않는다.

## 최초 설정

- 저장소 Actions secret: `SSTC_KEYSTORE_BASE64`, `SSTC_KEYSTORE_PASSWORD`, `SSTC_KEY_ALIAS`,
  `SSTC_KEY_PASSWORD`, `SSTC_SIGNING_CERT_SHA256`. 값은 운영자가 비밀 저장소에서 설정한다.
- 기존에 배포한 앱과 같은 applicationId와 서명키를 유지한다. debug 서명 앱에서
  다른 release 서명 APK로 앱 내 업데이트할 수 없다.
- 태그 생성 권한과 `v*` 태그 ruleset을 운영자가 관리한다. 배포할 커밋과 버전 결정은 사람의 책임이다.
- workflow token의 Release 공개 권한, SDK 37.0.0 설치와 고정 verifier 해시는 실제 Actions 실행에서
  확인해야 한다. 로컬 모의 테스트가 GitHub 설정·secret의 유효성을 증명하지 않는다.
- 저장소와 릴리스 자산은 인증 없이 조회·다운로드할 수 있어야 한다. 앱에는 GitHub token을 넣지 않는다.

## 앱 동작

앱 실행·화면 복귀 시 기존 업데이트 정책에 따라 공식 릴리스를 조회한다.
조회와 같은 버전의 `나중에` 알림은 24시간 캐시한다. 설치 버전·Android API 변경은 재조회 조건이다.
draft/prerelease, APK·메타데이터 쌍이 없는 릴리스, 현재 이하 버전과 미지원 Android는 제외한다.

`업데이트` → 앱 전용 cache에 다운로드(최대 512 MiB, 10분) → SHA-256,
applicationId, versionCode, versionName, minSdk, 설치된 앱과 현재 서명 인증서 비교 →
`설치` → 필요하면 이 출처의 앱 설치 허용 설정 → Android 설치 확인 화면 순서다.
APK의 최종 서명 무결성과 설치 여부는 Android 설치 관리자가 검증한다.

설정에서 거절하거나 설치를 취소하면 기존 앱을 계속 사용할 수 있다. 설치 취소 후에는
준비된 파일로 재시도할 수 있다. 다운로드 취소·실패 시 부분 파일은 제거하고, 실패 안내에서
다시 다운로드할 수 있다. 설치 관리자에 넘긴 APK는 읽는 동안 유지하며 다음 다운로드가 교체한다.
공유 provider는 외부에 export하지 않고 `cache/app-updates/`만 공유한다. 확인된 설치 관리자에
해당 APK의 읽기 권한만 부여하며, 다음 검증된 APK로 파일을 교체할 때 기존 권한을 회수한다.

기존 앱에 이 기능이 없는 경우 이 기능을 포함한 APK를 먼저 한 번 수동 설치해야 한다.

## 검증 명령

```bash
chmod +x gradlew
./gradlew testDebugUnitTest assembleDebug
python3 -B -m unittest discover -s tests -p 'test_*release.py' -v
```

Windows에서는 JDK 17을 설정하고 `gradlew.bat testDebugUnitTest assembleDebug`를 사용한다.
패키징 테스트의 Unix 파일 권한 검사는 Linux/WSL에서 실행한다. 테스트가 사용하는 키·SDK 응답과
GitHub CLI 응답은 합성 자료이며 실제 Release를 공개하지 않는다.

`AppUpdateDeviceTest`는 Android의 실제 APK 파싱·서명 인증서 비교·FileProvider를 사용하고,
HTTP 응답만 테스트 전용 engine으로 대체한다. 새 버전 테스트는 별도 APK fixture가 필요하다.
fixture가 없으면 새 버전 설치 준비 테스트만 skip하며, 동일 버전·손상 파일·HTTP 오류 테스트는 실행한다.

다음은 데이터가 없는 테스트 기기/임시 에뮬레이터에서 운영자가 실행할 예다.
기존 기기의 앱을 삭제하거나 versionCode를 낮추지 않는다.

```bash
mkdir -p app/build/update-flow
./gradlew assembleDebug -PsstcVersionCode=1 -PsstcVersionName=1.0
cp app/build/outputs/apk/debug/app-debug.apk app/build/update-flow/base.apk
./gradlew assembleDebug -PsstcVersionCode=2 -PsstcVersionName=1.1.0
cp app/build/outputs/apk/debug/app-debug.apk app/build/update-flow/candidate.apk
./gradlew assembleDebug assembleDebugAndroidTest
adb install -r app/build/update-flow/base.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell mkdir -p /sdcard/Android/data/com.SST.server_state_telemetry_client/files
adb push app/build/update-flow/candidate.apk /sdcard/Android/data/com.SST.server_state_telemetry_client/files/update-fixture.apk
adb shell am instrument -w -e class com.SST.server_state_telemetry_client.AppUpdateDeviceTest -e updateFixture update-fixture.apk -e launchInstaller true com.SST.server_state_telemetry_client.test/androidx.test.runner.AndroidJUnitRunner
```

여러 기기가 연결된 환경에서는 모든 `adb` 명령에 `-s <serial>`을 추가한다.
Windows PowerShell에서는 `-PsstcVersionName=1.1.0` 같은 Gradle 인수 전체를 따옴표로 감싼다.
명령 성공 후 설치 권한 거절·허용과 설치 취소·완료를 실제 Android 화면에서 확인한다.
설치 후 `adb shell dumpsys package com.SST.server_state_telemetry_client`에서 버전 코드 `2`를,
아래에서 테스트 데이터 보존을 확인한다.

```bash
adb shell run-as com.SST.server_state_telemetry_client cat shared_prefs/update_flow_test.xml
```

실제 GitHub 공개 파일 탐지·네트워크 다운로드, release 서명 APK 간 업데이트, 사용자 서버 데이터
보존, Android 8(API 26) 및 다른 기종의 설정/설치 UI, 가로·세로 회전은 별도 수동 검증 대상이다.
이번 구현의 검증 결과와 실제 설치를 진행하지 않은 범위는 [검증 기록](APP_UPDATE_VERIFICATION.md)에 정리했다.

## 공개 실패 및 문제 버전 대응

- 업로드·재다운로드 검증 실패는 draft를 남긴다. 운영자가 Actions 로그와 draft 자산을 검토하고
  draft 정리 여부를 결정한 뒤 재실행한다. 기존 Release나 태그를 자동 덮어쓰지 않는다.
- 공개된 문제 버전은 운영자가 신규 배포를 중단하고 기존보다 큰 versionCode로 수정 버전을
  같은 키로 배포한다. 이미 설치된 앱에 낮은 버전 APK를 강제로 설치하지 않는다.
- 공개 파일을 회수해도 기기에 이미 내려받은 APK까지 취소할 수는 없다.
  `packaging-verification.json`의 `releasePublished`·`deviceUpdateVerified`는 패키징 시점의 기록이며,
  GitHub 공개·실기기 검증 완료를 뜻하지 않는다.
