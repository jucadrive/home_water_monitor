# ESP32 개발·장비 인수 환경

실측 구현 진행 중: [SEN0244·ADS1115 안내](ESP32_SENSOR_SETUP.md).
일반 Build 기본값은 Source ADS1115다. 이 문서의 기존 가상 MQTT 인수용 Build에는
`-Source SIMULATED`를 명시한다. 실제 보드/이미지 상태는 HANDOFF를 따른다.

상세 승인 범위: [작업 명세](tasks/ESP32_FIRMWARE_INTEGRATION.md).
USB 제어: [제어 계약](DEVICE_CONTROL_CONTRACT.md).

## 고정 도구

- ESP-IDF v5.5.5, commit b774170ff46c393eeb5e495ea37936038d3f4f4f.
- ESP-MQTT는 해당 IDF의 Git submodule commit으로 고정한다. 별도 latest 설치 금지.
- ESP-MQTT commit: 0e9ec171b448db2101ea537300976b7ffe595941.
- 설치 위치 기본값: `%USERPROFILE%/esp/esp-idf-v5.5.5`.
- target esp32, UART COM3/115200. Arduino IDE는 USB 선행 점검에 사용했다.

공식 소스를 recursive clone한 뒤 `install.bat esp32`로 공식 도구를 설치한다.
전역 Python/PATH를 변경하지 않고 프로젝트 작업 셸에서만 export한다.
예상 명령은 저장소 루트 PowerShell에서 다음과 같다.

```powershell
./scripts/esp32.ps1 -Action Version
./scripts/esp32.ps1 -Action Build
./scripts/esp32.ps1 -Action Flash -Port COM3
./scripts/esp32.ps1 -Action Monitor -Port COM3
```

Arduino 시리얼 모니터는 먼저 닫는다. 자동 다운로드 실패 시 BOOT을 누른 상태에서 EN을
한 번 눌렀다 놓고 BOOT을 유지한 채 flash한다. 쓰기가 시작되면 BOOT을 놓는다.
전송 중 EN을 누르지 않는다. monitor 종료는 Ctrl+]다.
수동 모드에 미리 들어간 뒤 BOOT을 놓고 다음 명령을 실행할 수도 있다.
`./scripts/esp32.ps1 -Action Flash -ManualDownload`는 SDK의 flasher_args.json을 사용해
추가 reset 없이 ROM에 연결한다. 먼저 Build를 완료해야 한다.
처음 최소 이미지의 HWM_UPLOAD_OK는 빌드/업로드 검증이며 MQTT 성공 증거가 아니다.

Wi-Fi·브로커 계정은 승인된 장비 인수 시 로컬 설정에 입력한다. 채팅/문서/커밋으로 공유하지 않는다.
sdkconfig·sdkconfig.local·build/는 루트 .gitignore 대상이다.
빌드 디렉터리·펌웨어 바이너리에는 자격 증명이 포함될 수 있으므로 외부에 올리지 않는다.
브로커/DB 전용 구성은 아래, 실제 장비 인수 재개 절차는 [인수 안내](ESP32_ACCEPTANCE.md)를 따른다.

## 장치에서 순수 상태·계약 테스트

`Test`는 테스트 이미지의 **빌드만** 수행한다. 실행 성공은 UART 결과까지 확인해야 한다.

```powershell
./scripts/esp32.ps1 -Action Test
# BOOT/EN으로 다운로드 모드 진입 후
./scripts/esp32.ps1 -Action Flash -ManualDownload
# ESP-IDF export를 한 셸의 Python 사용
python scripts/esp32-control.py watch --seconds 15 --expected-tests 60
```

테스트 이미지는 네트워크/실제 센서를 사용하지 않는다. UART 수집기는 테스트 개수·실패 0·건너뜀 0을
확인하며 타임아웃/다른 결과면 종료 1이다. 운영 이미지로 돌아가려면 Build 후 다시 Flash한다.

## 전용 장비 인수 환경

- PC는 유선이어도 된다. ESP32의 2.4GHz AP와 PC가 서로 통신 가능한 같은 LAN을 사용한다.
- 연결이 끊긴 어댑터에 남은 IP/게이트웨이를 활성 주소로 간주하지 않는다. Get-NetAdapter의 Up 상태,
  IPv4 AddressState와 실제 경로를 확인한다. 게스트 Wi-Fi/AP 격리·다른 핫스팟은 연결을 막을 수 있다.
- `python scripts/setup-device-test.py --lan-ip <활성 LAN IPv4>`로 전용 Mosquitto 2.0.22/PG 18을 준비한다.
  브로커는 loopback와 선택 LAN:18883, DB는 loopback:25432만 사용한다.
- 사용자 입력: `firmware/esp32/wifi.local.json`에 ssid/password를 저장한다. 값은 채팅에 보내지 않는다.
- 기본 인증은 wpa2이며 password는 UTF-8 기준 8~63 byte다. 이번 가상 데이터 인수에 한해 승인된
  개방형 망은 `authMode: "open"`, `password: ""`를 명시한다. 빈 암호만으로 개방형으로 전환하지 않는다.
- 기본 `esp32.ps1 -Action Build -ControlMode MQTT`는 MQTT START/RETRY를 사용하고 USB 실행을 거절한다.
  기존 USB 인수는 명시 `-ControlMode UART`로 빌드한다. 양쪽 모두 USB STATUS는 허용한다.
- `python scripts/configure-esp32.py --samples 60`은 Git 제외된 local_config.h를 만든다.
  경계 인수는 --samples 0/1/59 빌드를 사용한다. 실제 센서 환산값이 아니다.
- 주소가 바뀌면 `setup-device-test.py --lan-ip <새 주소> --rebind`로 **이 인수 환경만** 재바인딩한다.
  자격 증명/DB 볼륨은 유지한다. 새 펌웨어 설정 생성·빌드/업로드도 필요하다.
- 필요한 경우 관리자 PowerShell에서 `./scripts/device-test-firewall.ps1 -LanAddress <활성 LAN IPv4>`.
  해당 IP:18883/실제 서브넷만 허용하며 전역 방화벽 정책은 변경하지 않는다.
- 암호 파일은 읽기 전용 마운트에서 private tmpfs로 복사하고 mosquitto 소유 0600으로 읽는다.
  익명 접속은 금지, 장치 계정은 자신의 events/status 쓰기와 preservation/commands 읽기만 허용한다.
- `infra/device-test/.env`, secrets.local.json, private/ 및 local_config.h는 Git에서 제외한다.
- IDF Python에서 `scripts/device-test/server.py start`로 JDK 25의 별도 백엔드를 실행한다.
  MQTT 제어 인수는 `start --control`로 활성화하며 REST는 loopback:18080만 사용한다.
  DB/브로커는 위 전용 환경만 사용한다. stop은 자신의 PID/생성 시각/인스턴스 식별이 맞는 프로세스만 중지한다.
- `scripts/device-test/smoke.py`는 PC 모의 장치의 속성·DB·확인·ACL 검사다. 실제 ESP32 검증으로 표시하지 않는다.
  Paho는 새 의존성을 받지 않고 현재 backend bootJar의 기존 1.2.5를 재사용한다.
- 종료: server.py stop, 해당 compose.yml의 `docker compose ... stop`으로 전용 환경만 중지한다.
  볼륨 삭제는 하지 않는다. 방화벽 제거가 필요하면 관리자 셸에서
  `Remove-NetFirewallRule -Name HWM-ESP32-Device-Test-18883`을 명시적으로 실행한다.
