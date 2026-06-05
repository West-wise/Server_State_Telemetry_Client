# SSTC — Server State Telemetry Client

**SSTD(Server State Telemetry Daemon)** 에 접속하여 리눅스 서버·임베디드 장비의 시스템 상태를 실시간으로 모니터링하는 Android 클라이언트 앱.

QR 스캔으로 서버를 등록하고, **Noise_XX** 암호화 핸드셰이크를 통해 인증된 후, 1초 단위로 CPU·메모리·디스크·네트워크 통계를 스트리밍 수신합니다.

> **Companion project**: [SSTD (서버 데몬, C++)](https://github.com/West-wise/SSTD)

---

## Features

| 기능 | 설명 |
|---|---|
| QR 서버 등록 | `sst://` URI 포맷 QR 스캔 → 서버 즉시 등록 |
| 멀티 서버 관리 | 여러 서버 동시 등록·연결·삭제·이름 편집 |
| Noise XX 핸드셰이크 | `Noise_XX_25519_ChaChaPoly_BLAKE2b` 암호화 연결 |
| 공개키 핀닝 | QR의 `pub_key`와 핸드셰이크 키 불일치 시 즉시 차단 (MITM 방지) |
| 실시간 대시보드 | CPU, 메모리, 네트워크 RX/TX, 디스크, 프로세스·사용자 수, uptime |
| 재전송 방지 | 수신 패킷 timestamp 검증 (미래 +1s / 과거 5s 초과 거절) |
| 자동 재연결 | 시작 시 저장된 모든 서버에 자동 연결 시도 |

---

## Screenshots

> *(준비 중)*

---

## Architecture

```
┌─────────────────────────────────────────────────────┐
│                  Presentation Layer                  │
│  MainActivity ─ NavHost                              │
│  Screens: Splash / Dashboard / ServerDetail /        │
│           ServerRegister / QrScan / Settings         │
│  ViewModel: MainViewModel (Hilt, StateFlow)          │
└────────────────────┬────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────┐
│                   Domain Layer                       │
│  TelemetryRepository (interface)                     │
│  UseCases: ParseServerQrUseCase                      │
│            ValidateServerRegisterUseCase             │
│  Models: SystemStats, ServerState, RegistedServerList│
└────────────────────┬────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────┐
│                    Data Layer                        │
│  Remote: SocketDataSource ─ NoiseSession             │
│          (TCP + Noise_XX + ChaCha20-Poly1305)        │
│  Local:  Room DB (ServerEntity, ServerDao)           │
└─────────────────────────────────────────────────────┘
```

---

## Protocol Overview

| 항목 | 값 |
|---|---|
| 전송 계층 | TCP (기본 포트 **41924**) |
| 엔디안 | Little Endian |
| 핸드셰이크 | `Noise_XX_25519_ChaChaPoly_BLAKE2b` |
| 전송 암호화 | ChaCha20-Poly1305 (IETF, 12바이트 nonce) |
| 푸시 주기 | 1초 (서버→클라이언트 단방향) |
| 평문 구조 | `SecureHeader (24B)` + `SystemStats (134B)` |

핸드셰이크 흐름:

```
Client                          Server
  ── e (32B) ──────────────────►
  ◄── e, ee, s, es (80B) ───────
  ── s, se (48B) ──────────────►
              [Transport phase]
  ◄── [4B len][ChaCha ciphertext] (SystemStats) ──
```

---

## Tech Stack

| 영역 | 라이브러리 |
|---|---|
| UI | Jetpack Compose, Material3 |
| DI | Hilt 2.51 |
| DB | Room 2.6 |
| 네트워크 | Ktor Network (TCP 소켓) |
| 암호화 | lazysodium-android 5.2 (libsodium 바인딩) |
| QR 스캔 | CameraX 1.3 + ML Kit Barcode |
| 비동기 | Kotlin Coroutines + StateFlow |
| 내비게이션 | Navigation Compose 2.8 |

---

## Requirements

- Android **8.0+ (API 26)**
- Android Studio Ladybug 이상
- JDK 11
- 연결 대상: [SSTD 데몬](https://github.com/West-wise/SSTD) 실행 중인 서버

---

## Build & Run

```bash
# 저장소 클론
git clone https://github.com/West-wise/SSTC.git
cd SSTC

# Android Studio에서 열기 또는 CLI 빌드
./gradlew assembleDebug

# 기기/에뮬레이터에 설치
./gradlew installDebug
```

> Windows: `gradlew.bat assembleDebug`

---

## Server Registration (QR Format)

SSTD 서버가 생성하는 QR URI 포맷:

```
sst://server?name=<서버명>&ip=<IP>&port=<포트>&pub_key=<64자리 hex>&ts=<unix초>
```

- `pub_key`: 서버 X25519 정적 공개키 (32바이트 → 64자리 hex) — 핀닝에 사용
- QR 스캔 불가 시 수동 입력(IP / 포트 / 공개키) 지원

---

## Project Structure

```
app/src/main/java/com/SST/server_state_telemetry_client/
├── MainActivity.kt
├── SstcApp.kt
├── di/                        # Hilt 모듈
│   ├── AppModule.kt
│   ├── DatabaseModule.kt
│   └── RepositoryModule.kt
├── domain/
│   ├── model/                 # SystemStats, ServerState, RegistedServerList
│   ├── repository/            # TelemetryRepository (interface)
│   └── usecase/               # QR 파싱, 서버 등록 검증
├── data/
│   ├── remote/
│   │   ├── NoiseSession.kt    # Noise XX 핸드셰이크 구현
│   │   └── SocketDataSource.kt
│   └── local/
│       ├── AppDatabase.kt
│       ├── dao/ServerDao.kt
│       └── entity/ServerEntity.kt
└── presentation/
    ├── viewmodel/MainViewModel.kt
    ├── navigation/Screen.kt
    └── ui/
        ├── screens/           # Splash, Dashboard, ServerDetail, QrScan,
        │                      # ServerRegister, Settings
        └── components/        # MetricCard, NetworkGraph, PartitionWidgets, ...
```

---

## Security

- **공개키 핀닝**: 핸드셰이크에서 수신한 서버 공개키가 QR 등록 시 저장한 값과 다르면 즉시 연결 차단
- **재전송 방지**: 수신 패킷 timestamp를 현재 시각과 비교 (±5s/+1s 범위 초과 거절)
- **nonce 단조 증가**: 수신 카운터 재사용 불가, 복호화 실패 시 즉시 재핸드셰이크

---

## License

MIT License. See [LICENSE](LICENSE) for details.
