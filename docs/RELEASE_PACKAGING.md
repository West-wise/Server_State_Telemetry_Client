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
  apksigner verify --verbose signed.apk
  javac -encoding UTF-8 --release 17 -cp BUILD_TOOLS_37/lib/apksigner.jar -d PRIVATE_CLASSES scripts/VerifyApkCertificate.java
  java -cp PRIVATE_CLASSES:BUILD_TOOLS_37/lib/apksigner.jar VerifyApkCertificate signed.apk
  aapt dump badging signed.apk
```

SDK Build Tools **37.0.0**의 aapt·zipalign·apksigner와 apksigner.jar를 사용한다.
공식 Linux 37.0.0 JAR의 SHA-256도 고정하며 다른 SDK 버전으로 자동 대체하지 않는다.
JAVA_HOME의 JDK 17을 확인하고 0700 임시 폴더에서 인증서 helper를 컴파일한다.
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

## 검증된 인증서 DER 비교와 안전한 실패

`apksigner verify --verbose`의 기존 전체 서명 검증을 유지한다. 인증서 지문은 사람이
읽는 출력 문구나 stdout/stderr의 위치에서 추출하지 않는다. 고정된 SDK apksig API의
`ApkVerifier`로 API26 이상 범위의 서명을 검증하고 `isVerified()`가 참인 결과의
단일 서명자 X.509 인증서 DER(`getEncoded()`)에 SHA-256을 적용해 기대 지문과 비교한다.
Java helper의 네 필드(`verified`, `signerCount`, `sha256`, `rotation`)만 엄격하게 읽는다.
누락·중복 필드·잘못된 유형·손상 JSON·추가 출력은 성공으로 처리하지 않는다.

- `SIGNATURE_VERIFICATION_FAILED`: 기존 SDK 또는 구조화된 결과의 서명 검증 실패.
- `SIGNING_CERTIFICATE_EXTRACTION_FAILED`: 인증서 누락 또는 손상된 helper 결과.
- `SIGNING_CERTIFICATE_MULTIPLE_DIGESTS`: 서명자가 둘 이상인 결과. 동일 지문도 거부한다.
- `SIGNING_CERTIFICATE_ROTATION_UNSUPPORTED`: 서명 방식·플랫폼별 인증서가 다르거나
  복수 인증서 회전 lineage가 있다. 회전된 키의 호환성은 이 단일 인증서 계약에 포함하지 않는다.
- `SIGNING_CERTIFICATE_MISMATCH`: 검증된 단일 인증서의 DER 지문이 기대값과 다르다.
- `MISSING_CERTIFICATE_VERIFIER`, `CERTIFICATE_VERIFIER_VERSION_MISMATCH`, `MISSING_JDK_17`,
  `MISSING_CERTIFICATE_HELPER`, `CERTIFICATE_HELPER_COMPILATION_FAILED`, `CERTIFICATE_HELPER_FAILED`:
  고정된 도구·helper가 없거나 변경됐거나 실행할 수 없다. 다른 버전이나 파서로 대체하지 않는다.

인증서 실패의 고정 진단은 추출 여부·개수·일치 여부와 `SDK_CERTIFICATE` 또는
`UNRECOGNIZED` 분류만 출력한다. 비교할 수 없는 경우 일치 여부는 `null`이다.
원문 도구 출력, 지문, subject, alias, 비밀번호, keystore, private 경로 및 SDK 예외는
실패 로그·artifact에 기록하지 않는다. Java 환경 옵션으로 인증정보가 인쇄되지 않도록
JAVA_TOOL_OPTIONS·JDK_JAVA_OPTIONS·_JAVA_OPTIONS·CLASSPATH도 자식 환경에서 제거한다.
기존 정렬·앱 식별정보·버전·debuggable=false·메타데이터·해시 검사와 실패 시 private 파일
정리, 전체 성공 후에만 네 artifact를 공개하는 계약을 유지한다.

실제 run 38051297945의 원인 코드는 인증서 추출 실패이며 기대 지문 불일치가 아니다.
원문 도구 출력은 기록되지 않아 구체적 출력 원인은 알 수 없다. 로컬 합성 Python 테스트,
공개 APK를 이용한 실제 JDK/SDK API 검증 및 GitHub debug Sensor 결과를 실제 SSTC 서명
패키지 생성·서명키 호환성·기기 업데이트 성공과 구분한다. 검토 후 사람이 main에 머지한
뒤 새 SSTC Package를 versionCode=2 / versionName=1.0.1의 같은 입력으로 실행해 확인한다.

패키징은 Gradle 캐시를 끈 상태에서 debug 테스트·빌드와 release 빌드를 모두 수행한다.
전체 workflow 제한은45분, 각 Gradle 명령 제한은600초다. run38051297945는 빌드 성공 후
인증서 단계에서 실패했으며6분 이상의 빌드 시간이 timeout 실패의 증거는 아니다.
이번 교정은 캐시·workflow·검증 명령·시간 제한을 변경하지 않는다.
