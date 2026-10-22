# MQTT START·장치 상태 보고 계약 v1

상태: 계약·전체 실행 계획 사용자 승인 완료. 구현/인수 진행 중이며 실제 검증 결과는 실행 계획을 따른다.
실행 계획: [tasks/MQTT_CONTROL_IMPLEMENTATION.md](tasks/MQTT_CONTROL_IMPLEMENTATION.md).
작성: 2026-10-22. 기준 main 1aaaa06(PR #7). 작업 명세: [tasks/MQTT_CONTROL_CONTRACT.md](tasks/MQTT_CONTROL_CONTRACT.md).
이 문서는 **승인된 계약**이다. 기존 SESSION_RESULT/SESSION_PRESERVED v1의 필드·보존 규칙을 변경하지 않는다.

## 1. 목적과 현재 경계

- 앱/PC의 REST 요청으로 서버가 sessionId를 발급하고, MQTT START로 ESP32의 측정을 시작한다.
- 서버의 명령 접수, MQTT 전달 확인, 장치 실행 확인, DB 보존, 장치 버퍼 해제를 구분한다.
- 설계 시작 시에는 USB START/RETRY/STATUS만 있었다. 현재 구현 진행 상태는 HANDOFF와 실행 계획에서 확인한다.
- 한 장치에 측정/미확인 목록은 하나다. 원본은 완료 후 일괄 전송하며 상태 보고에 원본 배열을 싣지 않는다.
- 자동 시작/예약 측정/STOP/임의 버퍼 폐기/OTA/센서 교정/TLS 배포/재부팅 버퍼 영속화는 범위 밖이다.
- 현행 가상 60개·1초·첫10개 안정화·startedAt=null을 유지한다. 서버 요청 시각을 측정 시작 시각으로 대입하지 않는다.

## 2. 식별과 실행 권한

| 식별 | 발급/수명/역할 |
|---|---|
| deviceId | 기존 허용 장치 규칙과 MQTT 계정/ACL 유지 |
| sessionId | 서버 UUIDv4. START 한 번에 하나, 재전송/RETRY/REST 중복에서 불변 |
| commandId | 서버 UUIDv4. 사용자 의도 한 번의 START 또는 RETRY. RETRY는 새 commandId/기존 sessionId |
| Idempotency-Key | 클라이언트 UUIDv4. 장치·명령 종류·정규화한 요청과 함께 서버 DB에 영구 대응, 같은 키의 다른 요청은 409 |
| bootId | 장치 부팅마다 새 UUIDv4. Wi-Fi/MQTT 재연결로 변경하지 않음. 장치 UTC 시계 불필요 |
| probeId | 서버의 상태 조회마다 새 UUIDv4. 현재 진행 중인 조회 응답과 상관관계를 검사 |
| windowId | 장치가 상태 조회를 처리할 때 새 UUIDv4. bootId와 묶인 일회성 실행 허가 |
| statusSeq | 부팅 동안 모든 상태 보고에 증가하는 정수 1..2147483647. 재연결에도 유지, 넘치면 제어 중단 후 점검 |

sessionId는 측정 **요청** 단계부터 존재하지만, 기존 tds_session은 실제 최종 목록을 저장할 때만 생성한다.
요청만 생성됐거나 응답이 없는 상황을 빈 INCOMPLETE 측정으로 저장하지 않는다.
위 UUID는 장치/명령 상관관계이며 인증 토큰을 대체하지 않는다. MQTT packet ID/DUP는 업무 식별자가 아니다.

## 3. MQTT 전송과 토픽

| 방향 | 토픽 | eventType |
|---|---|---|
| 서버 → 장치 | home/water/v1/{deviceId}/commands | STATE_REQUEST, START, RETRY |
| 장치 → 서버 | home/water/v1/{deviceId}/status | DEVICE_STATUS |
| 기존 장치 → 서버 | home/water/v1/{deviceId}/events | SESSION_RESULT (변경 없음) |
| 기존 서버 → 장치 | home/water/v1/{deviceId}/preservation | SESSION_PRESERVED (변경 없음) |

- 신규 두 토픽은 MQTT 5.0, QoS1, retain=false, Content Type=application/json, PFI=1.
- UTF-8 JSON object, 중복 키/알 수 없는 필드/잘못된 수치/null 생략 혼용을 거절한다. 신규 본문 최대4096 bytes.
- 신규 메시지 Message Expiry Interval은 발행 시10초. 중개 시 감소한 유효 값1..10을 수락하고 없거나0/초과는 거절한다.
- SDK 예외(사용자 별도 승인): 고정 ESP-MQTT는 수신 Expiry를 내부 파싱 후 공개 이벤트에 전달하지 않는다.
  ESP32의 commands 수신에서는 이 값의 직접 검사만 제외한다. 서버 발행10초/브로커 만료/장치 bootId·일회성10초
  단조 window 검사는 유지한다. Paho 서버의 status 수신은 기존대로 Expiry1~10을 검사한다. SDK 패치/버전 변경은 없다.
- 구독은 Retain As Published=true, Retain Handling=2. live retained도 거절한다.
- 장치 RX는 현행 Clean Start=true/Session Expiry=0을 유지하며 commands와 preservation의 SUBACK을 모두 확인한다.
- 서버의 신규 status 수신 연결은 Clean Start=true/Expiry=0, 기존 events 수신의 false/86400초는 유지한다.
  별도 status 어댑터/클라이언트 ID로 결과 저장의 직렬 DB 처리와 상태 보고를 분리한다. 기존 발행 연결은 재사용 가능하다.
- 장치는 상태를 RX 연결로 발행하는 방안을 우선 검증한다. 기존 결과 TX의 시도별 수명·5회 재시도와 분리한다.
- 서버/장치는 오프라인 명령을 업무 큐에 쌓아 연결 복구 후 실행하지 않는다. 발행 타임아웃은 취소 보장이 아니다.
- Message Expiry는 브로커의 대기 메시지 만료다. 이미 전달을 시작한 명령의 지연 실행까지 막는 장치 만료 검사와 구분한다.
  근거: [OASIS MQTT 5.0 §3.3.2.3.3/§3.1.2.11.2](https://docs.oasis-open.org/mqtt/mqtt/v5.0/os/mqtt-v5.0-os.html).

## 4. JSON 필드 초안

아래 각 타입은 공통 schemaVersion=1, eventType, deviceId를 필수로 가진다. UUID는 소문자 정규형으로 출력한다.
표에 있는 필드만 허용하며 nullable은 명시적 null을 사용한다. 향후 JSON Schema/예제로 기계 검증한다.
정수는 소수 부분을 버려 수락하지 않는다. enum/string/boolean/null은 표의 JSON 타입을 따른다.
round 상한도2147483647이며 한도 도달 시 fault로 중단한다. uptimeMs 외 건수/시도/순번은 명시된 정수 범위만 허용한다.

### STATE_REQUEST

추가 필드: probeId(UUIDv4). 측정/원본/전송 회차를 변경하지 않는 상태 조회다.
장치는 현재 snapshot과 replyToProbeId를 담아 DEVICE_STATUS를 발행한다.
실행 가능한 IDLE 또는 MANUAL_WAIT일 때만 bootId에 속한 새 windowId를 만들고 장치 단조 시각 기준10초 뒤 만료한다.
새 STATE_REQUEST 처리는 이전 window를 무효화한다. 같은 probeId의 MQTT 중복이어도 새 무작위 window를 발급하며
예전 windowId를 다시 활성화하거나 그 기한을 연장하지 않는다. 이 중복은 START를 안전하게 거절시킬 수 있으나 재측정하지 않는다.
바쁜 상태/장치 fault/필수 구독 미완료에서는 controlWindow=null이다.

### START

추가 필드: commandId, sessionId, targetBootId, windowId (모두 UUIDv4).
expectedSamples=60, samplingIntervalMs=1000을 필수 고정값으로 둔다. 장치 센서 설정은 명령으로 바꾸지 않는다.
장치는 단일 제어 task에서 다음 순서로 검사한다.

1. MQTT/JSON/토픽·허용 장치·명령 필드를 검사한다.
2. 마지막 수락 실행과 commandId가 같고 **모든 업무 필드**가 같으면 기존 ACCEPTED를 보고하고 다시 실행하지 않는다.
   같은 commandId의 다른 내용은 COMMAND_CONFLICT다. 기존 실행의 수락 보고는 현재 실행 중이라는 뜻이 아니다.
3. targetBootId가 현재 부팅과 같고, 현재 windowId와 같으며, 아직10초가 지나지 않았는지 검사한다.
4. IDLE/버퍼 없음/fault 없음/필수 수신 구독 준비를 확인한다. 직전 완료 sessionId 재사용도 거절한다.
5. window를 먼저 소비하고 기존 hwm_start를 한 번 호출한다. 성공한 경우에만 lastExecution을 RAM에 기록한다.
   같은 task의 상태 변경과 실행 기록은 중간 이벤트가 끼어들지 못한다. 호출 실패는 DEVICE_FAULT로 거절하고
   fault로 제어를 중단한다. 수락 기록을 만들거나 자동 재실행하지 않는다.
6. 장치 상태가 MEASURING으로 전이된 뒤 ACCEPTED를 담은 DEVICE_STATUS를 발행한다.

기존 window는 소비/만료/새 조회/재부팅 후 다시 유효해지지 않는다. 오래된 commandId 전체 목록을 RAM에 보관하지 않아도
예전 window의 START를 재실행하지 못하게 한다. 서버는 같은 commandId에 새 window를 붙여 재시도하지 않는다.

### RETRY

추가 필드: commandId, sessionId, targetBootId, windowId. 새 commandId와 기존 보관 sessionId를 쓴다.
START와 같은 중복·부팅·일회성 허가 검사를 한다. MANUAL_WAIT이고 sessionId가 현재 버퍼와 같아야 한다.
기존 불변 payload로 최대5회 새 회차를 시작한다. 측정/원본/startedAt/sessionId를 새로 만들지 않는다.
직전에 유효 보존 확인이 와서 버퍼가 해제됐으면 NO_PENDING_BUFFER로 거절하며 새 세션을 만들지 않는다.
동일 RETRY의 중복 수락 보고로 회차가 추가되지 않는다.

### DEVICE_STATUS

| 필드 | 규칙/의미 |
|---|---|
| bootId, statusSeq | 부팅 ID와 이 보고의 순서. 동일/낮은 seq는 현재 상태를 덮어쓰지 않음 |
| uptimeMs | 장치 부팅 후 단조 밀리초, 정수0..9007199254740991. UTC 시각 아님 |
| replyToProbeId | STATE_REQUEST 응답이면 그 probeId, 그 외 null |
| controlWindow | null 또는 {windowId, validForMs:10000}. 해당 응답 생성 시점부터의 장치 검사 기한이며 서버 수신부터10초가 아님 |
| state | IDLE/MEASURING/PREPARING/WAIT_PUBACK/WAIT_CONFIRMATION/BACKOFF/MANUAL_WAIT |
| sessionId | IDLE이면 null, 그 외 현재 보관 UUID |
| sampleCount, finalized | 0..60, boolean. IDLE=0/false, MEASURING=false, 나머지=true |
| round, attempt | IDLE=0/0, MEASURING=1/0, 전송 상태는 round>=1/attempt=1..5 |
| controlMode | MQTT/UART. 서버는 MQTT 모드에서만 실행 허가를 사용하며 UART 모드의 window는 null |
| controlReady | commands/preservation 구독 확인 완료 및 제어 가능한 내부 상태 여부. 온라인 보장으로 사용하지 않음 |
| fault | null 또는 CONTROL_UNAVAILABLE. fault이면 실행 허가 없음 |
| commandResult | null 또는 {commandId, commandType:START/RETRY, sessionId, outcome:ACCEPTED/REJECTED, reason} |
| lastExecution | null 또는 마지막 수락 {commandId, commandType, sessionId, windowId}. 완료 뒤에도 같은 부팅에서 유지 |
| lastRelease | null 또는 {sessionId, preservationResult:NORMAL/INCOMPLETE, preservedSampleCount, reason:PRESERVATION_CONFIRMED} |

commandResult.reason은 ACCEPTED이면 null, REJECTED이면 아래 코드 중 하나다.
BOOT_MISMATCH/WINDOW_INVALID/WINDOW_EXPIRED/BUSY/BUFFER_PENDING/NO_PENDING_BUFFER/
SESSION_MISMATCH/SESSION_REUSED/COMMAND_CONFLICT/NOT_READY/DEVICE_FAULT.
형식·인증·토픽 오류처럼 신뢰할 수 없는 명령에는 업무 응답을 만들지 않고 제한된 오류 범주만 기록한다.
lastRelease는 실제 유효 보존 확인 검사와 TX 정리·버퍼 삭제 이후에만 갱신한다. 단순 IDLE/샘플0은 삭제 증거가 아니다.
새 측정이 시작돼도 직전 lastRelease는 다음 해제까지 유지한다. 재부팅 후 둘 다 null이며 RAM 유실을 정상 해제로 보고하지 않는다.
commandResult가 REJECTED여도 lastExecution의 이전 수락 기록은 지우지 않는다.
새 부팅에서는 seq=1부터, lastExecution/lastRelease/window는 초기화한다. IDLE인데 fault일 수 있으므로 둘을 따로 검사한다.

## 5. 정상 흐름과 시간 제한

1. REST START 요청의 Idempotency-Key를 확인하고 짧은 DB 트랜잭션에서 command/session ID와 장치 제어 자리를 예약한다.
2. 커밋 후 STATE_REQUEST를 발행한다. 현재 probeId 응답을 서버 단조 대기 시간5초 내에 받아야 새 실행 허가로 사용한다.
   필요한 상태/구독/bootId/controlWindow를 검증한다. 이전 heartbeat의 window를 재사용하지 않는다.
3. 짧은 DB 트랜잭션에서 bootId/windowId/불변 발행 본문과 DISPATCHING을 커밋한 뒤 START를 발행한다.
   장치 window10초는 계속 흐른다. 지연돼 만료되면 거절시키며 유효 시간을 새로 발급해 몰래 다시 보내지 않는다.
4. 장치 ACCEPTED 또는 일치하는 lastExecution이 있는 상태 보고를 받으면 실행 수락을 기록한다.
5. 장치가60개를 모아 기존 SESSION_RESULT/DB 커밋/SESSION_PRESERVED 흐름을 수행한다.
6. 서버는 DB 보존을 자체 조회로 확인하고 장치 lastRelease를 별도 수신하여 표시한다.

제어 명령은 업무 자동 재발행0회로 시작한다. MQTT 자체 QoS1 재전달은 중복 안전하게 처리한다.
명령 발행/PUBACK/수락 응답의 서버 관찰 기한은 DISPATCHING 이후10초다. 초과 시 실행 여부 UNKNOWN이며 실패 확정이 아니다.
기한 뒤 도착한 유효 수락/해제 보고도 반영한다. 서버 재시작 시 DISPATCHING/UNKNOWN을 자동 재발행하지 않고 상태 조회로 조정한다.
STATE_REQUEST 실패/오프라인이고 START 발행 의도조차 기록하지 않았다면 NOT_DISPATCHED다. 같은 REST 키는 같은 실패 결과를 반환한다.
새 사용자 의도로 재시작할 때만 새 키/commandId/sessionId를 발급한다. UNKNOWN일 때 새 START는 먼저 차단한다.

## 6. 중복·동시성·미확인 조정

- 같은 REST 키·같은 정규화 요청은 원래 commandId/sessionId/현재 처리 상태를 반환한다. 새 발행/허가 갱신 없음.
- 다른 키로 동시에 START해도 장치별 DB 제어 행 잠금/유일 제약으로 하나만 예약한다. 다른 요청은409 CONTROL_BUSY다.
- RETRY는 해당 보관 세션이 점유한 제어 자리를 이어 사용한다. 그 세션의 명령 판정이 진행 중이면 추가 RETRY도409다.
  이전 ACCEPTED 명령 행을 덮어쓰지 않고 새 RETRY 명령 행을 생성한다.
- 장치도 실제 버퍼를 검사한다. 서버의 IDLE 추정이나 DB 저장 완료가 버퍼 보관 중 START를 우회하지 못한다.
- 명령 행 생성과 실제 MQTT 발행은 원자적이지 않다. DISPATCHING 커밋 후 미발행/응답 전 종료는 UNKNOWN으로 복구한다.
- UNKNOWN 조정은 새로운 probe를 통해 기존 window를 무효화한 snapshot을 받아 수행한다.
  같은 부팅의 lastExecution이 해당 명령이면 수락 기록, 없으면 미실행 판정이 가능한지 확인한다.
  이 판정에는 새 probe 응답·이전 window 무효화·다른 제어 경로 없음·이전 수락 기록 보존이 모두 필요하다.
  이전 commandId에 새 window를 붙여 다시 실행하지 않는다. 조정된 미실행 요청은 NOT_EXECUTED로 닫고 새 사용자 요청을 받는다.
- 장치 수락 후에는 MEASURING/버퍼 보관이면 제어 자리를 유지한다. IDLE 및 lastRelease와 DB 보존을 교차 확인하면 정상 종료한다.
- 부팅이 달라졌다면 기존 명령을 NOT_EXECUTED/COMPLETED로 단정하지 않는다. INTERRUPTED_REBOOT와
  bufferOutcome=UNKNOWN_AFTER_REBOOT로 종결하고 서버 DB 보존 여부는 별도 조회한다.
  새 부팅의 fresh probe가 IDLE/준비 완료임을 확인한 뒤 새 사용자 요청을 허용한다. 재부팅 자동 재측정은 없다.
  이미 보존/해제 완료를 확인한 과거 세션의 결과는 이후 재부팅으로 취소하지 않는다. 미확인 진행 세션에만 적용한다.
- 정상 MQTT 제어 모드에서는 USB START/RETRY를 거절하고 STATUS 진단만 허용한다.
  기존 USB 인수 모드는 명시적 별도 설정이며 MQTT 제어와 동시에 켜지 않는다. 이 경계가 없으면 서버 조정 판정은 성립하지 않는다.

## 7. 상태 관측·순서·유실

- 장치는 제어 구독 완료/재연결, 상태 전이, 명령 판정, 버퍼 해제 때 snapshot을 보고하고 연결 중15초마다 heartbeat를 보낸다.
  측정 진행 sampleCount는 heartbeat로 관측한다. 초당 원본이나 모든 샘플별 상태를 보내지 않는다.
- 상태 전송은 실패 시 측정 루프를 막지 않는 bounded 방식이다. 재연결 후 최신 snapshot을 보내며 과거 heartbeat 전체를 재생하지 않는다.
  마지막 수락/해제 요약은 RAM에 유지하므로 같은 부팅에서 누락 보고를 복구한다. 상태 장애가 결과 재시도 횟수를 소모하지 않는다.
- 서버는 (deviceId, bootId, statusSeq)를 사용한다. 중복/순서 역전은 현재 snapshot과 완료 판정을 되돌리지 않는다.
  다른 bootId의 비동기 보고는 현재 부팅을 즉시 교체하지 않고 새 probe로 확인한다. 이미 교체한 부팅의 늦은 보고는 무시한다.
  각 probeId의 응답은 한 번만 채택한다. 후속 중복은 새 boot/허가의 근거로 쓰지 않고 필요하면 새 probeId로 조회한다.
- 서버는 receivedAt(UTC), 마지막 snapshot, 마지막 유효 probe 확인 시각을 구분해 기록한다. 장치 uptime을 UTC로 환산하지 않는다.
- liveness는 FRESH(현재 probe 응답을10초 내 수락하고 확인 후45초 이내)/STALE(45초 초과)/UNKNOWN(없음/서버 재시작)이다.
  heartbeat 수신 시각만으로 FRESH를 연장하지 않는다. 지연 메시지는 지금 연결돼 있다는 증거가 아니다.
- 연결 중 서버 상태 조회 주기는15초, 한 장치당 probe 하나만 진행한다. START/RETRY의 허가 발급~판정 동안
  background probe를 중지하여 실행 직전에 자신의 window를 무효화하지 않는다. 타임아웃 뒤 새 probe로 조정한다.
- 이 값은 마지막 관측의 신선도이며 물리적으로 지금 온라인이라는 절대 보장이 아니다. LWT/retained presence는 v1에서 도입하지 않는다.

## 8. REST 초안과 표시 모델

REST는 승인된 신규 계약이며 아직 Controller가 없다. DTO/검증/오류 형식의 구현 구체안은 실행 계획을 따른다.

| 요청 | 동작 |
|---|---|
| POST /api/devices/{deviceId}/measurement-commands | Idempotency-Key 필수, 본문 {"type":"START"}. 새 요청202+commandId/sessionId/조회 Location |
| POST /api/devices/{deviceId}/measurement-commands | 본문 {"type":"RETRY","sessionId":"보관 UUID"}. 새 commandId/같은 세션, 새 회차 |
| GET /api/measurement-commands/{commandId} | 명령 상태/ID/시간·실행 관측·보존·버퍼 결과 조회 |
| GET /api/devices/{deviceId}/status | 마지막 관측과 liveness/receivedAt/probe 확인 시각. 상태 조회가 새 측정을 만들지 않음 |

같은 키 재요청은200으로 기존 리소스를 돌려준다. 키 충돌/제어 중/버퍼 보관은409, 문법 오류400,
미등록 장치/없는 명령404, durable 요청 생성 자체가 불가능하면503이다. 요청을 이미 커밋했으면202로 추적 ID를 반환하고
후속 오프라인/시간 초과는 조회 리소스에 남긴다. 등록 여부는 기존 allowedDeviceIds를 재사용한다.
DB 장애로 커밋 결과가 불명확하면 성공/실패를 추측하지 않고503을 반환하며 클라이언트는 **같은 키**로 조회/재요청한다.
API 인증/권한의 운영 도입은 별도 설계이며 미인증 제어를 공용망에 배포하지 않는다. 초기 인수는 로컬/전용 시험망이다.

한 개의 SUCCESS boolean으로 합치지 않고 다음 축을 제공한다.

| 축 | 값/근거 |
|---|---|
| commandState | REQUESTED/PROBING/DISPATCHING/ACCEPTED/REJECTED/NOT_DISPATCHED/NOT_EXECUTED/UNKNOWN/INTERRUPTED_REBOOT |
| serverPreservation | NOT_OBSERVED/NORMAL/INCOMPLETE. 실제 기존 DB 결과 조회 기준 |
| deviceBuffer | NOT_OBSERVED/HELD/RELEASE_CONFIRMED/UNKNOWN_AFTER_REBOOT |
| liveness | FRESH/STALE/UNKNOWN + 마지막 관측/확인 시각 |

ACCEPTED는 측정/재전송 실행을 수락했다는 뜻이며 완료가 아니다. commandState는 수락 이력을 보존한다.
해당 세션의 서버 보존과 같은 boot의 lastRelease를 모두 확인해야 보존/해제 완료로 표시한다.
INCOMPLETE도 보존 처리 완료지만 정상 측정 성공으로 표시하지 않는다.
DB가 먼저 저장돼도 장치 확인이 없으면 '서버 보존 완료·장치 해제 미확인', 장치 해제가 먼저 보고되면
'장치 해제 보고 수신·서버 보존 조회 중'으로 표시한다. 보고 순서를 DB 커밋 순서로 오해하지 않는다.

## 9. 저장·트랜잭션·책임 제안

- 신규 제어 명령 기록과 장치별 제어/최신 관측 저장을 추가한다. V1~V4는 유지하고 승인 후 새 V5로 생성한다.
  구체 컬럼/인덱스/DDL은 구현 계획에서 확정한다. 명령 idempotency key와 sessionId 요청 대응을 서버 재시작에도 보존한다.
- application 서비스가 예약/조건부 갱신/트랜잭션을 조율한다. HTTP/MQTT 어댑터가 Repository를 직접 호출하지 않는다.
- 장치별 제어 행 생성 경합은 PK/유일 제약으로 결정하고 종료된 트랜잭션 이후만 복구한다. 장치별 짧은 잠금만 사용한다.
- DB 트랜잭션/행 잠금을 잡고 MQTT PUBACK·장치 응답을 기다리지 않는다. 발행 전후 별도 트랜잭션으로 기록하며 UNKNOWN을 허용한다.
- REST 접수 후 처리는 장치별 동시1개인 bounded 실행기로 수행한다. 포화 시 신규 요청을 접수하지 않고503으로 응답한다.
  이미 접수한 요청을 무제한 메모리 큐에 쌓지 않는다. 재시작의 REQUESTED/PROBING은 NOT_DISPATCHED로 닫고,
  DISPATCHING 이후는 UNKNOWN으로 조정한다. 서버 재시작 후 과거 START를 새 허가로 자동 실행하지 않는다.
- 기존 ProcessTdsSessionService와 신규 세션 저장·재수신·MANDATORY/persist·커밋 후 확인은 변경하지 않는다.
  제어 명령 DB 장애 때문에 기존 결과 보존 확인을 억제하지 않는다. 결과는 기존 경로로 독립 수신하고 제어 조회가 결합한다.
- 도메인에 Spring/JPA/MQTT를 넣지 않는다. 기존 application→영속 구현 사용 패턴을 유지한다. 범용 명령 플랫폼/outbox는 도입하지 않는다.
- 상태 이력 전체 테이블/장치 재부팅 전 명령 저널은 v1 범위 밖이다. 서버 명령/최신 상태·부팅 교체 기록은 필요한 만큼 영속화한다.
- ACL은 서버 commands write/status read, 장치 자신의 commands read/status write만 추가한다.
  기존 events/preservation 권한은 유지한다. 실제 ACL 변경은 구현 계획 승인 후 전용 시험 환경에서 검증한다.

## 10. 승인할 결정과 검증 경계

핵심 제안은 서버 UUID 발급/별도 명령 저장, 부팅+일회성10초 허가, 업무 자동 명령 재발행0회,
15초 상태/45초 stale, MQTT/USB 제어 모드 상호 배제, 재부팅 RAM 유실 허용 유지다.
서버 영속 명령 이력은 중복 방지·미확인 복구를 위해 이번 제어 기능에 필요하다. 장치 영속 버퍼와 구분한다.
이 계약은 사용자 승인받았다. 별도의 실행 계획 승인 전 코드/DDL/ACL/펌웨어/인수 환경을 변경하지 않는다.
설계 검토는 소스/기존 계약 대조와 실패 사례 점검이다. JSON Schema/자동 테스트/실제 송수신은 아직 NOT RUN이다.
