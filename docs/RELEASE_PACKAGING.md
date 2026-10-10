# 배포용 APK 패키징

이 변경은 승인된 별도 기여 절차로 검토한다. SST-AX 일반 app/src 게시 경로를
사용하거나 validator·publisher 범위·정책·상태·승인 기록을 변경하지 않는다.
Task 0003의 변경에 의존하지 않는다. SSTD 통신 계약·앱 UI·Android 권한은 변경하지 않는다.

## 실행과 버전

main에 구현이 반영된 뒤 운영자가 GitHub Actions의 **SSTC Package**에서
workflow_dispatch를 실행한다. main 이외의 ref에서는 job을 실행하지 않는다.
push와 PR은 이 workflow를 실행하지 않는다. version_code는 선행 0 없는
1..2100000000 정수, version_name은 공백만으로 이루어지지 않은 1..64자의
인쇄 가능한 ASCII 표시 문자열이다. 줄바꿈·제어 문자·비ASCII 문자와 작은따옴표·큰따옴표·역슬래시는 Python과 Gradle에서 동일하게 거부한다.
aapt의 문자열 이스케이프 차이로 잘못된 일치 판정을 하지 않도록 제한한다.
버전 비교에는 versionCode만 사용하며 versionName이나 Release 태그를 사용하지 않는다.
기존 설치 버전보다 높은 versionCode를 운영자가 선택해야 한다.
Gradle 입력을 생략하면 기존 1 / 1.0을 유지한다.

JDK 17로 기존 검증을 먼저 실행하고 성공한 뒤 미서명 release를 빌드한다.
Java/Kotlin 대상 버전 11은 변경하지 않는다. 실제 Android 검증은 이후 기존
GitHub Sensor에서 수행한다. 다음은 workflow의 명령과 추가 Android 도구 명령이다.
아래 VERSION_CODE와 VERSION_NAME은 설명용 자리표시자이며 실제 workflow는
환경 변수와 Python 인자 목록으로 전달하여 셸 명령으로 보간하지 않는다.

```text
python3 scripts/package_release.py validate
python3 -m unittest discover -s tests -p 'test_package_release.py' -v
python3 scripts/package_release.py build
  bash ./gradlew testDebugUnitTest assembleDebug
  bash ./gradlew --no-daemon assembleRelease -PsstcVersionCode=VERSION_CODE -PsstcVersionName=VERSION_NAME
python3 scripts/package_release.py sign
  zipalign -P 16 -f 4 unsigned.apk aligned.apk
  apksigner sign --ks PRIVATE_KEYSTORE --ks-key-alias KEY_ALIAS --ks-pass file:PRIVATE_STORE_PASSWORD --key-pass file:PRIVATE_KEY_PASSWORD --out signed.apk aligned.apk
  zipalign -c -P 16 4 signed.apk
  apksigner verify --verbose --print-certs signed.apk
  aapt dump badging signed.apk
```

SDK build-tools의 가장 높은 안정 버전에서 aapt·zipalign·apksigner를 선택한다.
도구 누락이나 명령 실패는 실패 처리하며 원문 도구 출력을 로그에 내보내지 않는다.
최종 APK의 applicationId는 com.SST.server_state_telemetry_client, 최소 API는 26이며
입력 버전과 일치하고 application-debuggable 표시가 없어야 한다.

## 비밀정보와 서명 호환성

운영자가 GitHub 비밀 저장소에 다음 이름으로 값을 설정한다. 값은 문서·Task·로그에
기록하지 않는다. Python 테스트에는 합성 입력만 사용한다.

- `SSTC_KEYSTORE_BASE64`: 기존 앱과 호환되는 JKS 또는 PKCS12 keystore의 엄격한 base64 인코딩
- `SSTC_KEYSTORE_PASSWORD`: keystore 비밀번호
- `SSTC_KEY_ALIAS`: 서명키 alias
- `SSTC_KEY_PASSWORD`: 키 비밀번호
- `SSTC_SIGNING_CERT_SHA256`: 기대하는 서명 인증서의 SHA-256 지문(64자리 hex 또는 콜론 구분)

기존 설치 APK의 서명키와 호환되는 키를 반드시 사용한다. 기대 지문도 운영자가
신뢰할 수 있는 기존 인증서에서 확인한다. 다른 키로 서명한 APK는 정상 업데이트가
되지 않을 수 있다. 키 부재는 실행 전 조건이며 debug·미서명 APK로 대체하지 않는다.
비밀번호는 줄바꿈과 NUL을 허용하지 않는다.

비밀정보는 마지막 서명 단계에만 전달한다. Gradle과 단위 테스트에는 전달하지 않는다.
Python은 자식 프로세스 환경에서도 이 다섯 변수를 제거한다. 비밀번호는 0700 임시
디렉터리 안의 0600 파일로 전달하고 프로세스 인자에는 비밀번호 값을 넣지 않는다.
셸 보간·추적을 사용하지 않고 성공·실패 시 임시 keystore와 비밀번호·중간 APK를 제거한다.
서명 단계의 코드와 CI 변경은 키 유출 및 잘못된 패키지 생성 위험이 높으므로 검토가
필요하다. 검토된 main 코드만 실행하고 workflow와 빌드 도구의 신뢰성을 유지해야 한다.

## 산출물과 후속 사람 확인

전체 검증 성공 시에만 artifact에 다음 네 파일을 업로드한다.

- sstc-release.apk: 서명·정렬·앱 식별정보 검증을 통과한 APK
- sstc-update.json: schemaVersion=1, APK에서 확인한 versionCode·versionName·minSdkVersion,
  정확한 apkAssetName과 최종 APK 바이트의 sha256을 가진 JSON 객체
- SHA256SUMS: 최종 APK의 SHA-256과 파일명
- packaging-verification.json: 앱 식별정보, 인증서 지문, 해시 및 검증 결과

메타데이터의 유형·값·해시를 APK와 다시 대조한다. 앱은 GitHub Release asset 목록에서
apkAssetName과 정확히 일치하는 링크를 찾아야 한다. 이 패키징 변경은 앱 내 업데이트
검사 기능을 구현하지 않는다. 인증서 지문은 공개 식별정보이며 artifact에 개인키나
비밀번호·keystore·원문 서명 로그를 포함하지 않는다.

실제 credential 제공·실제 서명·Android 빌드·기기 업데이트는 이번 로컬 검증에서
수행하지 않았다. Python 가짜 도구 테스트는 실제 서명이나 설치 성공 증거가 아니다.
기존 GitHub Sensor는 debug 테스트·빌드만 검증한다. 실제 서명 검증은
main에 반영된 신규 SSTC Package workflow를 서명 입력과 함께 실행해야 확인된다.
이 실제 패키징 검증 이후에도 운영자는 네 산출물과 해시·인증서·버전을
검토해야 한다. 공개된 정식 GitHub Release에 APK와 sstc-update.json을 함께 올리는
작업, 기존 설치 기기의 업데이트 및 데이터 보존 확인은 별도 사람 작업이다.
이 workflow는 Release 생성·발행·배포·PR 병합을 수행하지 않고 contents: read만 사용한다.

## 인증서 검증 실패의 안전한 진단

기존 허용 형식 `Signer #번호 certificate SHA-256 digest:`에서 정확히 한 개의
64자리 hex 지문을 추출하고 정규화된 기대 SHA-256과 일치할 때만 성공한다.
성공 파서의 허용 형식은 확장하지 않는다. 실패 코드는 다음과 같다.

- `SIGNING_CERTIFICATE_EXTRACTION_FAILED`: 기존 형식의 지문을 하나도 추출하지 못했다.
  누락·잘못된 지문 형식·지원하지 않는 출력 형식이 여기에 포함된다.
- `SIGNING_CERTIFICATE_MULTIPLE_DIGESTS`: 기존 형식의 지문이 둘 이상 추출되었다.
  동일한 지문이 반복되어도 실패한다.
- `SIGNING_CERTIFICATE_MISMATCH`: 기존 형식에서 정확히 한 지문을 추출했지만 기대값과 다르다.

실패 명령은 고정 오류 코드 다음에 `SIGNING_CERTIFICATE_DIAGNOSTIC` 진단을 stderr로
출력한다. `extracted`는 기존 성공 파서의 추출 여부, `recognized_count`는 추출 개수,
`matches`는 단일 지문 비교 결과이며 비교할 수 없는 0개·복수에서는 JSON `null`이다.
`format`은 허용 목록의 `EXISTING_SIGNER`, `VERSIONED_SIGNER`, `MIXED`,
`UNRECOGNIZED` 중 하나다. 숫자 minSdkVersion/maxSdkVersion을 포함하는
`Signer (minSdkVersion=..., maxSdkVersion=...)` 형식은 진단 분류에만 사용하며,
이 형식만 있는 출력은 계속 추출 실패로 거절한다. 이 분류는 실제 실행에서 해당
형식이 나왔다는 증거가 아니다.

실패 진단에는 원문 도구 출력이나 공급된 값, 지문·subject·alias·비밀번호·keystore·
private 경로를 기록하지 않으며 실패 artifact도 만들지 않는다. 기존 성공 산출물과
검증 영수증의 계약은 유지한다. 모든 기존 서명·정렬·식별정보·메타데이터·해시 검사와
실패 시 private 파일 정리를 유지한다.

실제 Package run 38037164127 attempt 1의 실패 원인은 아직 알 수 없다.
검토된 코드가 main에 반영된 뒤 사람이 실제 패키징 실행의 안전한 진단으로 확인해야 한다.
가짜 SDK Python 테스트와 Controller의 고정 후보 GitHub debug Sensor PASS는
실제 서명 APK 생성·서명키 호환성·기기 업데이트 성공의 증거가 아니다.
