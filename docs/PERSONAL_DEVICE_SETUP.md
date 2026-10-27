# 집에서 개인 서버·ESP32 실측 연결

서버 원격 조작은 사용자가 직접 수행한다. 기존 앱/DB/Tunnel/MQTT 계정·볼륨·DB loopback 포트를 보존하며, [실물 인수 명세](tasks/PERSONAL_DEVICE_ACCEPTANCE.md)를 따른다. 현재 브로커의 내부1883은 그대로 두고 LAN IPv4의8883에만 TLS listener를 추가한다. 공유기에 새 포트 포워딩을 만들지 않는다.

## 1. 개발 PC 준비

미니 PC에서 `ip -4 route get 1.1.1.1`의 src IPv4를 확인한다. ESP32는 그 미니 PC에 도달할 수 있는 WPA2 2.4GHz Wi-Fi를 사용한다. 개발 PC와 대역이 달라도 UART 관측은 가능하지만 두 공유기 사이에 직접 통신이 된다고 가정하지 않는다. 브로커 IP를 DHCP 예약 등으로 유지하는 것은 사용자 네트워크 설정으로 별도 확인한다.

저장소 루트 PowerShell(아래 IP는 실제 값으로 바꿈):

```powershell
python scripts/create-mqtt-lan-tls.py --lan-ip <MINI_PC_LAN_IP>
python scripts/configure-personal-esp32.py --broker-ip <MINI_PC_LAN_IP>
```

첫 명령은 cryptography를 사용하는 개발 PC용 인증서 도구다. 이미 생성했다면 재실행/덮어쓰지 않는다. `.tools/deployment/mqtt-lan/authority/ca.key`는 **개발 PC에만 비공개 보관**하며 서버/장비에 전송하지 않는다. 서버 인증서는 입력한 IPv4 SAN·서버 인증 용도·1년 유효기간이며 CA는10년이다. 만료/IP 변경 전 별도 갱신 절차를 준비하고 검증을 끄지 않는다.

둘째 명령은 Wi-Fi 이름과 비밀번호를 로컬 숨김 입력으로 받는다. 기존 `.tools/deployment/mqtt/device-mqtt.json`의 장비 계정을 사용하고, 새 `firmware/esp32/main/local_personal_config.h`를 만든다. 기존 bench용 local_config.h/wifi.local.json은 바꾸지 않는다. 공개 CA만 펌웨어에 넣고 개인키는 넣지 않는다. 이미 개인 헤더가 있으면 자동 덮어쓰지 않으므로 변경 전에 별도 비공개 백업/검토가 필요하다.

## 2. 서버에 새 파일6개 전송

이 세션에서 준비한 `.tools/deployment/mqtt-lan-upload/`가 있다면 **그 내용물만** WinSCP로 기존 `~/ppossak/`에 병합한다. 폴더 자체를 한 단계 더 넣지 않는다.

```text
mqtt-lan-upload/
  compose.mqtt-lan.yml
  mosquitto-lan.conf
  secrets/mqtt-lan/
    mqtt-lan.env
    certs/ca.crt
    certs/server.crt
    certs/server.key
```

묶음이 없다면 저장소 infra/personal의 두 설정과 `.tools/deployment/mqtt-lan/`의 env/certs만 같은 구조로 전송한다. **authority/ca.key·개인 펌웨어 헤더·app.jar·기존 compose.yml·기존 비밀 파일은 전송/교체하지 않는다.** 위 이름으로 이미 설치한 자료가 있으면 임의 덮어쓰기 대신 현재 연결/인증서를 먼저 확인한다.

PuTTY에서 업로드 후:

```bash
cd "$HOME/ppossak"
chmod 700 secrets/mqtt-lan secrets/mqtt-lan/certs
chmod 600 secrets/mqtt-lan/mqtt-lan.env secrets/mqtt-lan/certs/ca.crt
sudo chown root:1883 secrets/mqtt-lan/certs/server.crt secrets/mqtt-lan/certs/server.key
sudo chmod 640 secrets/mqtt-lan/certs/server.crt secrets/mqtt-lan/certs/server.key
chmod 644 compose.mqtt-lan.yml mosquitto-lan.conf
```

일반 rootful Docker 기준이며 rootless/user namespace remap이면 실제 UID 매핑을 별도 확인한다. CA/서버 개인키 내용을 출력하거나777로 권한을 완화하지 않는다.

## 3. 서버의 broker만 TLS 구성으로 적용

같은 PuTTY 셸에서:

```bash
pc() {
  sudo docker compose --env-file secrets/postgres.env \
    --env-file secrets/mqtt-lan/mqtt-lan.env \
    -f compose.yml -f compose.mqtt.yml -f compose.mqtt-lan.yml "$@"
}
pc config --quiet
pc up -d --wait --wait-timeout 90 broker
pc ps
pc port broker 8883
pc logs --tail 40 broker
```

포트 결과는 `<MINI_PC_LAN_IP>:8883`이어야 한다. 1883을 호스트/LAN에 추가로 열지 않는다. 특정 LAN IP로 묶지만 라우터/방화벽 정책까지 자동 구성하는 것은 아니다. 필요한 LAN 경로가 막혔다면 집 내부 네트워크 정책을 확인하고 인터넷 공개/인증서 검증 해제로 우회하지 않는다.

브로커 재생성 때 backend MQTT 연결은 잠깐 끊겼다가 재연결한다. 백엔드/JAR/DB를 교체할 필요는 없다. 기존 계정·ACL·브로커 볼륨·DBeaver용 포트를 유지한다. 이후 운영 Compose 명령에는 세 파일과 두 env-file을 포함한다.

## 4. 펌웨어 빌드·업로드와 준비 상태

개발 PC에서 고정 ESP-IDF 5.5.5를 사용한다. COM 포트는 실제 CH340 장치를 확인한 값으로 바꾼다. 아래 Flash는 프로그램을 교체하므로 **먼저 STATUS에서 IDLE·session=none·samples=0**을 확인한다. 보관 중 원본이 있으면 업로드/리셋하지 않는다.

```powershell
./scripts/verify-esp32.ps1 -Mode CredentialFree -Personal -Source ADS1115 -ControlMode MQTT
./scripts/verify-esp32.ps1 -Mode Build -Personal -Source ADS1115 -ControlMode MQTT
./scripts/esp32.ps1 -Action Flash -Port <COM_PORT> -ManualDownload
```

자격 증명 없는 이미지는 컴파일 검사용이며 실측용으로 업로드하지 않는다. 실제 Flash는 바로 앞의 개인 설정 포함 Build 결과를 사용한다. 기존 시험용 빌드를 하려면 Personal을 생략하며, 개인용/시험용 빌드를 혼동하지 않는다. 이번 CH340 보드는 자동 진입에서0x13을 관측했으므로, Flash 직전에 BOOT을 누른 채 EN을 눌렀다 놓고1초 뒤 BOOT을 놓는 수동 절차를 사용한다. NVS/flash 전체 삭제는 하지 않는다.

Personal 빌드는 별도 Git 제외 `sdkconfig.personal`에서 `CONFIG_MBEDTLS_HAVE_TIME_DATE=y`를 명시한다. 기존 sdkconfig가 이 옵션을 끈 상태이면 sdkconfig.defaults 추가만으로는 켜지지 않을 수 있다. 스크립트가 개인 설정을 맞추고, main의 컴파일 guard가 옵션 누락을 거절한다. SNTP 동기화만으로 만료 검사가 켜지는 것은 아니다. 기존 bench sdkconfig는 유지한다.

UART는115200이다. SDK Python의 scripts/esp32-control.py status/watch를 사용해 HWM_ 진단만 수집한다. IDF Python 경로는 개발 PC의 .espressif/python_env에서 확인한다. serial monitor를 동시에 열지 않는다.

정상 준비 증거:

1. `wifi_up=1`: Wi-Fi/IP 성공. MQTT 성공과 다르다.
2. `HWM_TLS_REQUIRED enabled=1`: 개인 TLS 설정으로 기동.
3. `HWM_TIME_READY synchronized=1`: SNTP 시각 동기화 후 인증서 유효기간 검증 준비.
4. `HWM_RX_READY mqtt=5`, STATUS `rx_ready=1`: TLS 연결·인증과 두 구독 완료.
5. 앱 장비 상태가 신선한 probe 응답을 관측. 저장 원본은 아직0이며 자동 측정하지 않는다.

`HWM_TIME_WAIT`가 계속되면 DNS/NTP(pool.ntp.org, outbound UDP123) 경로를 확인한다. 기다리는 동안 UART/컨트롤러는 계속 동작한다. SNTP가 되지 않는다고 인증서 기간 검사나 peer 이름 검사를 끄지 않는다. TLS 루트 신뢰·IP SAN·시간 검증은 기본 ESP-MQTT/mbedTLS 검증에 맡기며 CA를 명시한다.

## 5. 앱에서 실제 측정1회

센서 배선을 전원 OFF에서 검토하고 프로브만 물에 담근다. 보드/빵판/USB를 물에서 떨어뜨려 건조하게 유지한다. 기존 [센서 연결 안내](ESP32_SENSOR_SETUP.md)를 따른다.

- 앱 서버 주소/로그인·장비 ID가 기존 개인 서버 설정과 일치하는지 확인한다.
- 재배를 선택하고 측정 시작을 **한 번** 누른다. 시험 기록이 남는 것을 인지하고 대상 재배를 선택한다.
- HTTP 접수·장치 실행 수락·원본 저장·장치 해제는 다른 단계다. 요청이 미확정이면 새 키로 반복 생성하지 않고 같은 요청 조회/재시도를 따른다.
- UART에서 측정/전송·보존 확인·해제를 확인하고 최종 IDLE/session=none/samples=0으로 돌아왔는지 본다.
- 앱에서 서버 보존 NORMAL/원본60개·통계 대상 건수·Source ADS1115·가정 수온/미교정·시각 근거를 확인한다. 실패했다면 유효 prefix/불완전 보존을 정상 측정 성공으로 표시하지 않는다.
- 기존 SQL 읽기 전용 조회로 해당 sessionId의60개 원본·재배 연결을 확인하고 결과를 기록한다. DELETE/데이터 초기화를 하지 않는다. 비밀번호·payload 원문을 인수 문서에 넣지 않는다.

교정·실제 수온·독립 TDS 미터 대비 정확도는 별도다. 수치만으로 어항 안전성·식물 건강을 판단하지 않는다.

## 6. 롤백과 남은 경계

문제 시 broker를 기존 내부 구성으로 되돌릴 수 있다(짧은 MQTT 중단). 기존 계정/볼륨은 유지한다.

```bash
sudo docker compose --env-file secrets/postgres.env \
  -f compose.yml -f compose.mqtt.yml up -d --wait broker
```

이후 ESP32의8883 접속은 실패하는 것이 정상이다. 평문 listener를 LAN에 여는 우회가 아니다. 원인 확인 후 TLS 구성으로 재적용한다. `down -v`, 비밀 재생성·원본 삭제로 복구하지 않는다.

로컬 TLS 시험, 컴파일, 실제 Wi-Fi/TLS/MQTT, 실측/보존/해제의 상태를 각각 기록한다. 실제 결과는 HANDOFF와 작업 명세를 따른다.
