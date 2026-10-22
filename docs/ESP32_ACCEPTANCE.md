# ESP32 실제 MQTT 인수 재개 절차

2026-10-22 개방형 Wi-Fi 사용을 이번 가상 데이터 인수에 한정해 추가 승인하고 재개했다.
로컬 wifi.local.json에 `authMode: "open"`과 빈 password를 명시한다. 생략 시 wpa2로 처리한다.
개방형 망에서 인증 MQTT TCP는 암호화되지 않는다. 이 예외는 운영/센서 데이터 전송에 확대하지 않는다.
현재 실행 결과는 HANDOFF와 기능 명세의 최종 Phase Result를 따른다.

최종 상태 DONE: 승인된 가상 데이터 장비 인수13종 PASS, 2026-10-22 사용자 최종 리뷰 및 전체 Git 작업 승인.
normal 0/1/59/60, retry, exhaust, db-outage, broker-restart, proxy-puback, proxy-early,
proxy-negative, 실제 wifi-loss, EN reboot를 실행했고 모두 최종 IDLE/0건 증거를 확인했다.
부정13종·유효/중복16KiB 확인·소진 후65초 무전송·실제 단절 중 버퍼 유지/동일 목록 RETRY를 포함한다.
EN 재부팅은 합의한 RAM 유실/자동 재측정 없음·이전 확인 거절을 검증했으며 영속 버퍼 구현이 아니다.
최종 경계/부정/수동 검증은 개인 WPA2 2.4GHz 핫스팟에서 수행했다. 사무실 망 지연 원인은 미확정이다.
현재 보드/build는 일반18883/가상60개(913264 bytes). 전용 서비스 중지/시험 규칙2개 비활성화 완료.
재실행 시 아래 절차로 활성 IP·필요한 포트·설정/이미지를 다시 확인한다. 상세 실패/재검증 이력은 명세를 따른다.

## 1. 먼저 네트워크 맞추기

- PC 유선과 같은 공유기의 2.4GHz Wi-Fi를 ESP32에 쓰거나, PC를 ESP32의 핫스팟에 연결한다.
- PC Wi-Fi 어댑터가 Disconnected인데 과거 IP가 남아 있던 것을 확인했다.
  최초 사용한 192.168.0.38을 재개할 때 그대로 신뢰하지 않는다.
- 현재 유선 주소는 조사 시 192.168.1.14였다. DHCP로 바뀔 수 있으므로 다시 확인한다.
- `Get-NetAdapter`, `Get-NetIPAddress -AddressFamily IPv4`로 Up/Preferred 인터페이스를 확인한다.
  게스트 Wi-Fi/AP 격리·서로 다른 공유기/핫스팟이면 PC 포트가 열려 있어도 ESP32가 접근하지 못한다.
- 실제 SSID/비밀번호는 wifi.local.json에만 저장한다. 채팅·로그·커밋에 남기지 않는다.

```powershell
# <LAN_IP>는 확인한 활성 private IPv4로 대체한다.
python scripts/setup-device-test.py --lan-ip <LAN_IP> --rebind
# 필요 시 관리자 PowerShell. 이 인수 규칙만 해당 IP/실제 서브넷으로 이동한다.
./scripts/device-test-firewall.ps1 -LanAddress <LAN_IP>
python scripts/configure-esp32.py --samples 60
./scripts/verify-esp32.ps1 -Mode Build
# BOOT 누름 → EN 눌렀다 놓음 → 1초 뒤 BOOT 놓음
./scripts/esp32.ps1 -Action Flash -ManualDownload
```

Flash는 장치 프로그램을 교체한다. 테스트 이미지/일반 이미지/credential-free 이미지를 구분한다.
실제 환경 변경 후 반드시 configure → Build → Flash를 다시 한다.
UART는 115200/COM3, 다른 시리얼 모니터는 닫는다. 이후 IDF Python에서 다음을 실행한다.

```powershell
python scripts/esp32-control.py status --seconds 5 --expect rx_ready=1
python scripts/device-test/server.py start
```

`wifi_up=1`은 Wi-Fi/IP 성공이고 `rx_ready=1`은 MQTT 5 연결과 QoS 1 구독 성공이다.
둘은 다르다. 잘못된 AP 자격 증명은 로컬 파일에서 수정한다. NVS/flash 전체 삭제로 우회하지 않는다.

## 2. 자동 인수 시나리오

인수 환경은 전용 DB/브로커만 사용한다. accept.py는 IDLE·rx_ready=1을 먼저 확인하며,
보관/측정 세션이 있으면 START를 거절한다. RETRY는 기존 UUID로 수행한다.
원본·DB 행 수/내용·실제 UART BUFFER_RELEASED/STATUS를 모두 확인해야 성공한다.
증거는 Git 제외된 `.tools/device-test/accept-<case>-<samples>.json`에만 기록한다.
원문 payload·자격 증명은 요약/증거 파일에 기록하지 않는다.

| --case | 실제 통과한 인수 조건 |
|---|---|
| normal | 실제 최종 목록 발행 → DB 원본 일치 → 유효 확인 → 버퍼 0 |
| retry | 전용 서버 중지, 2회 발행까지 보관, 서버 재개 후 동일 payload/재수신 진단 |
| exhaust | 서버 중지, 5회·사용자 대기·65초 추가 관찰 후 자동 전송 없음, 뒤늦은 확인 수락 |
| db-outage | 전용 DB 중지 중 보관, DB 복구 후 재전송/보존 확인 |
| broker-restart | 전용 브로커 재시작·구독 복구, 원본을 유지해 다시 발행 |
| proxy-puback | PUBACK/업무 확인 차단, 5회 소진·outbox 잔여 전송 없음, 같은 UUID RETRY |
| proxy-early | PUBACK을 전달하지 않아도 실제 보존 확인이 먼저 오면 해제 |
| proxy-negative | 5회 후 대기 중 다른 ID·잘못된 건수/JSON·PFI/Content Type·QoS/retain/초과 크기 거절, 유효 16KiB 분할 확인 수락 |

일반 이미지(18883) 예:

```powershell
python scripts/device-test/accept.py --case normal --samples 60
python scripts/device-test/accept.py --case retry --samples 60
```

0/1/59개는 각 `configure-esp32.py --samples N` → Build → Flash 후 `accept.py --case normal --samples N`으로 검사한다.
가상 고정 소스이며 실제 센서 정확도·교정 인수가 아니다.
완료한 UUID를 다시 START하지 않는다. 부팅 중 직전 완료 UUID는 거절한다.

## 3. PUBACK/확인 장애 주입 이미지

프록시는 장비 인수 전용 TCP 도구다. 제품 펌웨어에 새 서비스/라이브러리를 넣지 않는다.
실제 Wi-Fi 전파 단절과 프록시 TCP 단절을 같은 검증으로 보고하지 않는다.
CONNECT 인증 값/원문 payload를 로그나 증거에 저장하지 않는다.
프록시가 확인을 숨길 때는 브로커 쪽 QoS ACK를 완료해 업무 확인 유실을 재현한다.

```powershell
# 관리자 PowerShell, 이 포트가 필요한 시나리오에만 추가
./scripts/device-test-firewall.ps1 -LanAddress <LAN_IP> -Port 18884
python scripts/configure-esp32.py --samples 60 --broker-port 18884
./scripts/verify-esp32.ps1 -Mode Build
# 수동 다운로드 진입 후
./scripts/esp32.ps1 -Action Flash -ManualDownload
python scripts/device-test/accept.py --case proxy-puback --samples 60
python scripts/device-test/accept.py --case proxy-negative --samples 60
```

accept.py가 선택 LAN:18884 프록시를 띄우며 upstream은 loopback:18883으로 고정한다.
프록시 이미지가 아니면 RX 연결 식별 확인 단계에서 측정을 시작하지 않는다.
케이스가 끝나면 프록시가 종료되어 해당 이미지의 rx_ready는 다시 0이 될 수 있다.
일반 사용으로 돌아갈 때 configure의 --broker-port 18883/Build/Flash가 필요하다.
프록시의 관찰 5회는 통제된 MQTT 전달의 검사이며 TCP 물리 패킷이 정확히 5개라는 뜻이 아니다.

## 4. 실제 수동 장비 인수(이번 작업에서 통과, 재실행 절차)

- Wi-Fi AP를 실제로 끊었다 복구했을 때 같은 원본/UUID를 유지하는지 확인한다.
- 확인 대기/사용자 대기의 지연 확인·중복 확인은 실제 MQTT로 검증했고, 세부 시간 경계는 순수31개를 함께 따른다.
- EN/전원 재시작 시 RAM 유실은 MVP 합의대로 허용한다. 자동 재측정은 시작하지 않아야 한다.
- 새 START와 진행 중 RETRY 거절을 보관 상태에서 확인한다.
- 실제 힙·최소 힙은 UART STATUS로 기록한다. PC 도구 성공으로 장치 메모리 안전을 단정하지 않는다.
- 측정 도중 실패해도 기존 미확인 버퍼를 지우거나 새 UUID로 바꾸지 않는다.
  실패한 인수를 다시 시작하려면 먼저 STATUS/같은 UUID RETRY로 처리한다.
- TLS/제품 MQTT START·서버 sessionId 발급·센서 배선/ADC/교정은 별도 후속 승인 범위다.

실제 수동 인수는 일반 60개 이미지/같은 망에서 다음 도구로 진행한다.

```powershell
python scripts/device-test/manual_accept.py --case wifi-loss
python scripts/device-test/manual_accept.py --case reboot
```

첫 도구는 최종 목록 보관/회차 소진 후 MANUAL_READY를 출력한다. 안내 시 개인 핫스팟을
20초 껐다 켠다. 실제 wifi_up=0에서 동일 UUID/60개 보관, 복구 후 같은 목록 RETRY/DB/해제를 검증한다.
둘째 도구는 미확인 버퍼가 있을 때 MANUAL_READY를 출력한다. 안내 시 BOOT 없이 EN만 한 번 누른다.
합의한 MVP의 RAM 유실을 관찰하고 자동 재측정 없음/이전 확인 거절/서버 원본 보존을 검증한다.
공용 사무실 AP는 끄지 않는다. 조작 준비 전에 EN/USB를 건드리지 않는다.

## 5. 현재 가능한 검증과 종료

```powershell
./scripts/verify-esp32.ps1 -Mode CredentialFree  # 비밀값 없는 컴파일, 장비 인수 성공 아님
./scripts/verify-esp32.ps1 -Mode Build
./scripts/verify-esp32.ps1 -Mode Host            # 전용 환경 준비 필요, PC 도구/모의 장치만
./scripts/verify-esp32.ps1 -Mode UART            # COM3/IDLE, 측정 시작 없음
```

Host 도구는 기존 backend Paho 1.2.5와 IDF Python의 pyserial/psutil을 재사용한다.
호스트 테스트는 실제 ESP32 client ID를 사용하지 않아 장치 연결을 탈취하지 않는다.
테스트 이미지 31개는 [설치 안내](ESP32_SETUP.md)의 별도 build/flash/UART 결과 확인 절차를 따른다.

종료는 server.py stop과 전용 compose stop만 사용한다. DB 볼륨을 지우지 않는다.
시험 종료 시 관리자 셸에서 자신의 한정 규칙만 비활성화한다.

```powershell
./scripts/device-test-firewall.ps1 -LanAddress <LAN_IP> -BothPorts -Disable
```

다음 인수 때 필요한 포트를 명시적으로 호출하면 현재 IP/서브넷으로 갱신하고 다시 활성화한다.
두 포트가 모두 필요하면 `-BothPorts`를 사용한다. 전체 방화벽 정책이나 다른 규칙은 변경하지 않는다.
