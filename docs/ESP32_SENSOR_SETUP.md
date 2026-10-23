# SEN0244·ADS1115 연결·인수

상태 IN_PROGRESS. [승인 범위/인수 조건](tasks/TDS_SENSOR_INTEGRATION.md), 최신 상태는 [HANDOFF](HANDOFF.md).
사용자 부품: DFRobot SEN0244/ADS1115(VDD,GND,SCL,SDA,ADDR,ALRT,A0~A3)/ESP32,
헤더 납땜됨, MF/MM 점퍼와830홀 빵판. TDS 미터/멀티미터 없음, 표준액 미확인.

## 전원 OFF에서 먼저 확인
- 사용자 사진 확인(2026-10-23): ESP32 USB 아래 방향에서 오른쪽 맨아래3V3/바로위GND,
  오른쪽 D21/D22를 사용한다. 왼쪽 VIN은 이번 전원 연결에 사용하지 않는다.
- ADC는 HW-198 표기/사진상 SCL·SDA 주변103(10kΩ) 저항이 보인다. 실제 저항값·연속성은 미측정이다.
  ADDR-GND의 현재 연결 여부를 사진만으로 확정하지 않고 외부점퍼로 ADDR를GND에 명시 연결해0x48로 정한다.
  추가 납땜/저항 제거는 하지 않는다. 전원은 VDD3.3V 한 곳만 사용한다.
- 센서 케이블은 흰색3핀잠금커넥터↔검정3구암단자다. 흰색은 센서보드에 끼우고,
  검정단자에는수-수점퍼3개로 빵판/ADC에 연결한다. 실제색상은 센서보드 +/-/A 라벨과 대조한다.
  사진의 빨강/검정/파랑을 단독 근거로 전원 극성을 확정하지 않는다.
- 빵판 전원레일이 불명확하면 별도5공묶음 두 개(a1~e1=3.3V/a3~e3=GND)를 분배점으로 사용한다.
  두묶음은 연결하지 않으며, 전원OFF 배선사진/센서보드 라벨·케이블 매칭 검토 후 USB를 다시 연결한다.
- ADS1115 앞·뒷면의 주소 패드/저항/풀업을 확인한다. 핀 이름이 인접했다고 연결된 것은 아니다.
  이미 다른 전원에 고정된 ADDR를 GND로 연결하지 않는다. 사진/회로 확인 전 임의 점퍼를 연결하지 않는다.
- SDA/SCL 풀업은3.3V여야 한다. 내부 풀업에 의존하지 않는다. 저항이 없거나 회로가 불명확하면 보고 후 부품 조건을 확정한다.
- USB/별도 전원을 분리한 상태에서 배선한다. 빵판 전원 레일의 중간 단절/양쪽 레일 분리를 확인한다.
- 프로브만 물에 담근다. 보드/빵판/USB는 건조한 책상에 둔다. 전압만으로 물속 여부/프로브 단선을 확정하지 않는다.

| 출발 | 도착 |
|---|---|
| ESP32 3V3 | ADS1115 VDD, SEN0244 + |
| ESP32 GND | ADS1115 GND, SEN0244 - |
| GPIO21 | SDA |
| GPIO22 | SCL |
| 센서 A | ADS1115 A0 |
| ADDR | 모듈 회로 확인 후 GND, 주소0x48 |

ALRT/A1~A3 미사용. ±4.096V 게인은3.3V 전원에서4V 입력을 허용한다는 뜻이 아니다.
ADC ACK/설정 재조회만으로 칩 모델·보드 정품 여부를 인증할 수는 없다.

## 빌드 모드와 순수 테스트
```powershell
./scripts/esp32.ps1 -Action Test -CredentialFree
# IDLE/보관0건 확인 후 BOOT/EN 수동 다운로드 준비
./scripts/esp32.ps1 -Action Flash -Port COM5 -ManualDownload
# 고정 IDF Python 환경에서:
python scripts/esp32-control.py watch --port COM5 --seconds 4 --expected-tests 54

./scripts/verify-esp32.ps1 -Mode CredentialFree -Source ADS1115
./scripts/verify-esp32.ps1 -Mode Build -Source ADS1115
# 기존 가상 인수 전용:
./scripts/verify-esp32.ps1 -Mode Build -Source SIMULATED
```
일반 Build 기본 Source는ADS1115다. ControlMode 기본MQTT, USB 실행 인수는 명시UART를 사용한다.
Test 이미지는 센서/네트워크를 사용하지 않는다. Build만으로 실제 실행 성공이라고 하지 않는다.
CredentialFree는 빈 자격 증명 때문에 실행부가 최적화될 수 있어 실제 이미지 크기 증거가 아니다.

## 실측 의미와 오류
- ADC A0-GND/128SPS/연속/FSR±4.096V, I2C100kHz/각 전송 호출 timeout20ms.
- 40ms 간격25개 입력의 중앙값을1개 원본으로 사용한다. rawVoltageMv는 그 중앙값 전압이며
  구성 ADC25개를 DB에 각각 저장한다는 뜻이 아니다. 각 START에서 창을 비운다.
- 다음 창은 직전 원본에 기록한 ADC 읽기 완료시각 이후부터 시작한다.
  I/O 소요시간 변화가 원본간 최소1000ms 검사와 충돌하지 않도록 같은 기준시각을 사용한다.
- ADC 읽기·필터·환산은 전용FreeRTOS task가 수행한다(정적4KiB stack/우선순위6/소유main과같은core).
  요청1개·결과최대61개 고정큐에 세대/실제완료시각/수치만 복사한다. session/MQTT는main만 수정한다.
  main이 늦게 소비해도 원본elapsedMs는 실제시각을 유지하고 전송timeout은 처리시각부터 시작한다.
  다른세대 결과는 버리며 큐포화/작업시작 실패/읽기오류는 유효prefix를 불완전 보존한다.
- ADC 입력0~18400 counts(0~2.3V), 환산TDS0~1000ppm 범위를 초과하면 실패한다.
  필터로 오류를 감추거나 값을 잘라 정상값으로 만들지 않는다. 낮은 전압의 잡음도 자동0 처리하지 않는다.
- 전압 V=raw/8000, TDS=(133.42*V^3-255.86*V^2+857.39*V)/2, K1/가정25°C.
  미교정이며 실측 수온이 아니다. wire 전압mV/PPM은 마지막에 소수둘째자리 HALF_UP.
- startup ADC 실패는CONTROL_UNAVAILABLE/새START 거절. 수집 중 실패는 유효prefix를INCOMPLETE로 확정한다.
  pending 원본의RETRY를 막지 않고, 해제 후 fault가 적용돼 새START를 차단한다.
  보관 중 USB/EN으로 버퍼를 버리지 않는다. 해제·IDLE 이후 전원OFF 결선 점검/재부팅으로 복구한다.

## 실제 장비 인수
전용 환경/한정 방화벽/개인 설정은 [기존 인수 안내](ESP32_ACCEPTANCE.md)를 따른다.
현재주소/COM·IDLE 확인 후 configure --samples60 --broker-port18883/Build/flash를 수행한다.
아래는 고정 IDF Python 환경에서 실행한다. 각 시험은 살아 있는 실제 보드를 요구한다.
```powershell
python scripts/device-test/sensor_accept.py --case init-fault --port COM5
python scripts/device-test/sensor_accept.py --case normal --port COM5
python scripts/device-test/sensor_accept.py --case interrupted --after 10 --port COM5
python scripts/device-test/sensor_accept.py --case retry --port COM5
```
- init-fault: ADC 미연결로 부팅한 경우만. 상태보고/START 미발행/가짜빈세션 없음 확인.
- normal: REST 동시START/멱등/명령부정/원본60개/실측교정버전/제조사 환산/실제UART-MQTT-DB값 일치/해제.
- interrupted: 확인된 정상 배선에서만 수행. MANUAL_READY 이후 승인된 SDA 신호선만 분리한다.
  전원/접지/프로브/USB는 건드리지 않는다. 불완전prefix/통계NULL/해제/새START차단 확인.
  회로가 확인되지 않았거나 신호선을 구분하지 못하면 실행하지 않고 보고한다.
- 수집 오류 UART의 reason은 late/adc_read/adc_range/conversion/session_append로 구분한다.
  elapsed_ms/due_ms/window_count로 주기 지연과 세션 추가 거절을 구분하며 오류 기준을 완화하지 않는다.
- retry: 서버 결과 수신 연결에만 프록시18884를 사용해5회 소진/65초 무전송/같은payload RETRY를 확인.
  보드 이미지는 일반18883을 유지한다. 프록시는loopback이므로LAN18884 방화벽은 필요하지 않다.
- 증거: Git 제외 .tools/device-test/sensor-*.json. 테스트 실패 시 버퍼를 먼저 확인하고 같은 세션을 복구한다.
- 종료: 전용server/compose stop, 자신의18883/18884 규칙만Disable, 시험4포트/IDLE0건 재확인.

## 교정과 제한
표준액/비교 미터 없는 상태에서는 실제 교정·정확도 NOT VERIFIED다. 물 두 종류의 수치가 다르다고
정확도 통과로 판단하지 않는다. 표준액을 준비하면 라벨·기준온도·안정화·반복성과 교정계수를 별도 검증한다.
초기warmup10개는 검증된 안정화 시간이라는 뜻이 아니다. 수온/채수/침지 조건을 일정하게 기록한다.
전기적 전압 비교 장비가 없어 독립 전압 정확도 확인도 NOT VERIFIED로 남긴다.
