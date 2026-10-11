# Android 네이티브 라이브러리 16KB 정렬 교정

2026-10-11 기준. Galaxy S24+에서 보고된 CameraX LOAD 정렬 경고를 줄이기 위한 의존성 교정이다. 라이브러리 파일의 정적 검사, 후보 빌드, 최종 APK 검사, 실제 기기 실행은 각각 별도 증거로 확인한다.

## 변경 범위

| 의존성 | 기존 | 교정 |
|---|---|---|
| CameraX camera2·core·lifecycle·view | 1.3.2 | 1.5.3 |
| JNA Android AAR | 5.14.0 | 5.19.1 |

CameraX 네 모듈의 공통 `version.ref`와 JNA의 AAR 선택, lazysodium의 중복 JNA JAR 제외를 유지한다. compileSdk 35, minSdk 26, AGP 8.8.0, CI JDK 17, 앱 Java/Kotlin 대상 11과 기존 QR 화면·CAMERA 권한·SSTD 통신·Noise 구현은 그대로 사용한다.

## 교정 근거와 한계

기존 APK와 공개 AAR에서 CameraX 1.3.2의 `libimage_processing_util_jni.so`는 arm64-v8a와 x86_64 모두 LOAD 정렬이 4KB였다. JNA 5.14.0의 `libjnidispatch.so`는 x86_64 LOAD 정렬이 부족했고, ARM64는 LOAD 정렬을 통과하지만 GNU_RELRO를 16KB로 올림했을 때 쓰기 영역과 겹치는 정적 위험이 확인됐다.

공개 AAR의 arm64-v8a·x86_64 검사에서 CameraX 1.5.3의 `libimage_processing_util_jni.so`와 `libsurface_util_jni.so`는 LOAD 정렬을 통과했다. JNA 5.19.1은 두 ABI의 LOAD 정렬과 GNU_RELRO 끝 정렬을 통과했다. CameraX 1.5.3의 AAR 요구 조건은 compileSdk 35·AGP 8.6.0 이상·minSdk 23이며 현재 직접 빌드 설정에 맞는다. 전이 의존성과 API 호환성은 후보 CI에서 확인한다.

CameraX 1.5.3의 `libsurface_util_jni.so`에는 GNU_RELRO 끝이 16KB 배수가 아닌 문제가 남아 있다. 검사한 파일에서 올림 영역에 쓰기 데이터가 겹치지는 않았지만, 엄격한 GNU_RELRO 기준의 통과로 취급하지 않는다. AndroidX에는 이 정렬을 보완하는 후속 수정이 있으므로 배포된 버전에 포함됐는지 실제 AAR로 확인한 뒤 후속 업그레이드를 판단한다. 이번 변경만으로 완전한 16KB 호환성을 선언하지 않는다.

Android 경고의 “알 수 없는 오류” 목록만으로 libsodium·ML Kit·AndroidX Graphics를 모두 불량 라이브러리로 단정하지 않는다. 조사한 AOSP 구현에서는 정상 반환도 목록에 포함될 수 있다. 실제 Samsung 펌웨어의 동일 구현 여부는 미확인이다. 또한 “디버그 가능 앱” 문구는 빌드 변형과 관련되어 있으므로 의존성 교정만으로 해당 문구의 소멸을 보장하지 않는다.

공개 AAR 검사 대상의 SHA-256:

| 파일 | SHA-256 |
|---|---|
| camera-core-1.5.3.aar | `54e3d6dc7e19546e782bf8677f1fbc315633dc0205127c5ea00c41e1fc4c88dd` |
| jna-5.19.1.aar | `b57125cb7d16253f0d65a80f7d3a4c3664effa711b8bdbb7f87fb572ce1624ed` |

## 검증과 설치 확인

저장소의 필수 검증은 고정 후보 SHA에 대해 JDK 17로 실행한다.

```bash
chmod +x gradlew
./gradlew testDebugUnitTest assembleDebug
```

`SSTC Task Validation`은 신뢰된 main workflow에서 후보를 빌드하고 JSON 영수증을 보관한다. 현재 후보 APK는 업로드하지 않는다. 따라서 CI 성공과 공개 AAR 검사 결과는 최종 후보 APK의 ELF·ZIP 정렬, release 서명 또는 실제 기기 실행의 완료 증거가 아니다. `SSTC Package`의 main 전용 패키징은 사용자 병합 후 [기존 패키징 절차](RELEASE_PACKAGING.md)에 따라 별도로 실행한다.

최종 APK는 파일 해시와 버전을 기록하고 Android 공식 지침의 ELF LOAD·GNU_RELRO 및 ZIP 정렬 검사를 수행한다. S24+에서는 설치한 APK의 동일성을 확인하고 Android·One UI 버전과 실제 페이지 크기를 기록한다.

```bash
adb shell getconf PAGE_SIZE
```

`4096`인 기기에서 실행됐다는 결과를 실제 16KB 환경의 통과로 취급하지 않는다. `16384` 환경에서도 확인하고 QR 미리보기·스캔·카메라 권한 요청, 서버 연결·Noise 암호화 및 재연결, 경고 표시를 확인한다. 이 문서는 기기 검증 완료를 주장하지 않는다.

## 출처

- [Android 16KB 페이지 크기 지원과 검사 지침](https://developer.android.com/guide/practices/page-sizes)
- [CameraX 1.5.3 공식 릴리스](https://developer.android.com/jetpack/androidx/releases/camera#1.5.3)
- [JNA 5.19.1 변경 기록](https://github.com/java-native-access/jna/blob/5.19.1/CHANGES.md)
- [CameraX GNU_RELRO 후속 교정](https://android.googlesource.com/platform/frameworks/support/+/f931edc7e606dd50eca630433d32312dc2d57945)
- [조사에 사용한 AOSP 네이티브 검사 구현](https://github.com/aosp-mirror/platform_frameworks_base/blob/45034f0663f960d9ee5fb0a101a4732b71f6e2f4/core/jni/com_android_internal_content_NativeLibraryHelper.cpp#L811)
