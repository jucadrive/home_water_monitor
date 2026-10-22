# ESP32 장비 인수용 USB 제어 계약 v1

2026-10-22 전체 계획 승인. 제품 MQTT START 계약과 별개다.
장치는 ESP-IDF UART0, 115200 baud, 8N1로 한 줄씩 LF 또는 CRLF 명령을 받는다.
완성 명령은 최대 79 ASCII 문자이며 초과/비ASCII/NUL 줄 전체를 거절한다.
명령은 대문자이며 앞뒤 공백·추가 인자를 허용하지 않는다.

| 명령 | 의미 |
|---|---|
| `START <UUID>` | 새 가상 측정을 시작한다. PC가 새 측정마다 UUID v4를 발급한다. |
| `RETRY <UUID>` | 회차 소진 후 보관 중인 동일 세션의 최대 5회 회차를 다시 시작한다. |
| `STATUS` | 현 상태, 세션, 원본 수, 회차/시도, 수신 연결 상태를 출력한다. |

UUID 입력은 기존 MQTT 계약의 8-4-4-4-12 hex 형식, 비교/출력은 소문자다.
측정/미확인 버퍼가 있으면 START를 거절한다. 같은 활성 UUID도 다시 측정하지 않는다.
직전 완료 UUID는 같은 부팅 동안 재사용할 수 없다. 재부팅 후 영속 중복 방어는 없다.
RETRY는 MANUAL_WAIT 상태에서 보관 UUID와 일치할 때만 허용한다.
부팅·연결 복구·전송 실패가 새 세션을 만들지 않는다. EN/전원 차단은 RAM 버퍼를 잃는다.

상태: IDLE → MEASURING → PREPARING → WAIT_PUBACK → WAIT_CONFIRMATION.
실패 후 BACKOFF → PREPARING, 5회 소진 후 MANUAL_WAIT.
유효 보존 확인은 전송 시작 이후 모든 보관 상태에서 수락하고 IDLE로 돌아간다.
측정 중에는 최종 목록이 아직 확정되지 않아 확인을 수락하지 않는다.
단일 제어 task가 상태·원본·payload를 소유한다. 비동기 이벤트에는 연결 세대를 붙인다.

로그는 `HWM_` 접두사와 key=value의 로컬 진단이며 공개 MQTT 상태 메시지가 아니다.
`HWM_BUFFER_RELEASED`는 확인 검증과 전송 클라이언트 정리 후에만 출력한다.
비밀번호/SSID/전체 payload/SDK 예외 원문은 출력하지 않는다.
센서 없는 가상 소스는 calibrationVersion=simulated-v1, startedAt=null을 사용한다.
기본 60개, 장비 경계 인수용 빌드 설정으로 0/1/59개 최종 목록을 선택할 수 있다.
