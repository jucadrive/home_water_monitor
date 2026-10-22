# START·장치 상태 보고 실행 계획

상태: IN_PROGRESS — 전체 실행 계획 승인, 사용자 요청으로 Phase 4 현재 시험까지만 마치고 안전 종료.
Git 통합: 2026-10-23 사용자 현재 변경 커밋·푸시·CI·PR·main 병합 전체 승인. 잔여 장비 인수는 후속이며 전체 VERIFIED/DONE 아님.
진행: Phase 1 계약100/기존108 PASS, Phase 2 전체 backend181 PASS, Phase 3 실제 C45개 PASS.
최신 체크포인트(2026-10-23 새벽): 기존 UART13종 중10종 PASS. proxy-puback/proxy-negative 미실행,
물리 Wi-Fi 단절 환경 제약 NOT VERIFIED. 신규 proxy-status-pressure·최종 Host21·일반 MQTT 이미지 복원/normal 남음.
보드/build 모두 UART60·프록시18884·919744 bytes. 서비스/시험 규칙 종료, 보드 IDLE0건, staged0 보존.
SDK 예외 승인: 사용자 요청으로 고정 ESP-MQTT 유지·장치 수신 Expiry 직접 검사만 제외한다.
서버/브로커 Expiry와 bootId/일회성10초 장치 만료는 유지한다. 실제 공개 헤더/파싱 코드로 제약을 확인했다.
2026-10-22 사용자가 MQTT_CONTROL_CONTRACT.md의 계약을 승인하고 실행 계획 제시를 요청했다.
기준 main 62bac79(PR #8 문서 병합), 시작 시 staged/unstaged/untracked 없음.
Wi-Fi 로컬 파일은 Git 제외·내용 비공개로 보존한다. 단일 에이전트로 승인 후 Phase 1~5를 순차 수행한다.

## 1. 목표·불변 조건·승인 경계

- REST START/RETRY → 상태 조회 → 부팅/일회성 허가 검사 → 장치 실행 → 기존 결과 보존 → 해제 관측까지 구현한다.
- 계약: [../MQTT_CONTROL_CONTRACT.md](../MQTT_CONTROL_CONTRACT.md). 인수 조건 C01~C29는
  [MQTT_CONTROL_CONTRACT.md](MQTT_CONTROL_CONTRACT.md)의 표를 따른다.
- 기존 SESSION_RESULT/PRESERVED·원본/재수신·NEVER/READ_COMMITTED·MANDATORY/persist·PK 충돌 1회 복구는 유지한다.
- 실행 요청은 기존 tds_session에 미리 넣지 않는다. 실패한 START를 빈 INCOMPLETE 측정으로 생성하지 않는다.
- 신규 V5, REST, 제어 MQTT 연결, 펌웨어 제어, 전용 인수 ACL/방화벽/로컬 설정 반영을 이 계획 승인 범위로 요청한다.
- 기존 JPA/JdbcTemplate·Spring Integration/Paho·고정 ESP-IDF/ESP-MQTT·순수 C 도구만 사용한다. 새 의존성/범용 포트 전환 없음.
- 제품 센서·ADC/교정·영속 장치 버퍼·OTA·STOP·예약/자동 측정·운영 TLS/배포·사무실 망 지연 조사는 제외한다.
- 코드/DDL/DB/ACL/펌웨어는 계획 승인 후만 변경한다. 커밋·푸시·PR·병합은 별도 승인이다.

## 2. 실제 환경과 인수 준비

- 집 PC: 유선 192.168.0.2/24, ESP32는 같은 공유기의 일반 2.4GHz Wi-Fi를 사용한다.
  실행 시 IP/포트 충돌을 다시 확인하며 Hyper-V/WSL 가상 주소는 선택하지 않는다.
- 보드: CH340 COM4/115200. STATUS로 IDLE/session=none/samples=0 확인했다. 문서의 과거 COM3를 그대로 쓰지 않는다.
- 고정 ESP-IDF 5.5.5/ESP-MQTT 설치·버전/도구 검사 PASS. 기본 Java는21이므로 검증 프로세스에 JDK25를 지정한다.
- 로컬 Wi-Fi JSON의 형식 검사 PASS, 값은 출력/기록하지 않는다. 실제 집 Wi-Fi/MQTT 연결은 아직 NOT VERIFIED다.
- 인수 DB/브로커·방화벽 규칙·계정 설정은 집 PC에 없다. Phase 4에서 전용 환경만 생성한다.
  setup-device-test.py를 현재 LAN으로 실행하고 로컬 비밀값을 생성한다. 운영/개발 DB는 사용하지 않는다.
- 새 ACL은 서버 commands write/status read, 장치 자신의 commands read/status write만 추가한다.
  inspector는 승인된 시험 장치의 commands/status 관찰·장애 주입에 필요한 권한만 명시한다. 다른 장치 접근은 거절한다.
- 방화벽은 LAN 주소/실제 서브넷/18883만, 프록시 사례에서만18884를 추가한다. 전역 방화벽/프로필 변경 없음.
- API 인수는 PC loopback:18080에서 수행한다. 미인증 REST를 공유기 외부나 공용망에 노출하지 않는다.
- 업로드 직전 STATUS와 RAM 버퍼를 확인한다. 미확인 버퍼가 있으면 같은 세션 처리부터 하며 임의 리셋/폐기하지 않는다.
  BOOT/EN이 필요한 시점에 안내한다. flash/NVS 전체 삭제·파티션 확대는 포함하지 않는다.

## 3. DB V5 구체안

파일: backend/home-water-monitor-backend/src/main/resources/db/migration/V5__add_device_control.sql.
V1~V4는 수정하지 않는다. UUID는 DB uuid, 시각은 timestamptz, 제어 enum은 문자열+CHECK를 사용한다.

| 테이블 | 주요 컬럼·제약 | 역할 |
|---|---|---|
| tds_device_control | device_id PK(varchar64), current_boot_id nullable, active_command_id nullable, held_session_id nullable, version bigint, updated_at | 장치별 제어 자리/보관 세션·조건부 갱신 |
| tds_device_command | command_id UUID PK, idempotency_key UUID UNIQUE NOT NULL, device_id FK, command_type START/RETRY, normalized_request jsonb, session_id UUID, command_state, target_boot_id/window_id nullable, dispatch_payload text nullable, requested_at/dispatching_at/decided_at/updated_at, reason nullable | durable 요청/불변 발행 의도/수락·미확인 이력 |
| tds_device_observation | device_id PK/FK, boot_id, status_seq bigint(1..2147483647), snapshot jsonb, received_at, last_probe_id/confirmed_at nullable, server_epoch UUID | 최신 관측. raw MQTT 원문은 저장하지 않음 |
| tds_device_boot | (device_id,boot_id) PK, confirmed_at, retired_at nullable | fresh probe로 확인한 부팅 교체와 과거 부팅 늦은 보고 방지 |

- command.session_id는 tds_session에 FK를 걸지 않는다. 요청 시 측정 원본이 아직 없기 때문이다.
- active_command_id는 command FK, device FK와 함께 장치 일치를 서비스에서 검사한다. 순환 FK가 있으면
  control 행 생성 → command 생성 → active 포인터 갱신 순서로 같은 트랜잭션에서 처리한다.
- held_session_id는 미확인 보관 세션을 유지한다. ACCEPTED START가 끝났어도 버퍼 해제 근거 없이는 자리를 풀지 않는다.
  RETRY는 같은 held_session_id에 새 command 행을 만들고 이전 ACCEPTED 행을 덮어쓰지 않는다.
- CHECK: 명령 종류/상태/필수 UUID, DISPATCHING 이후 필요한 발행 의도, payload UTF-8 최대4096 bytes,
  seq 범위. 수치·시간/명령-장치 교차 정합성은 서비스와 통합 테스트에서도 확인한다.
- 인덱스: command(device_id,requested_at), command(command_state,updated_at), boot(device_id,retired_at).
  key UNIQUE는 전체 장치에 걸쳐 같은 키의 다른 요청을409로 거절한다. 새 보존/삭제 정책은 도입하지 않는다.
- 인메모리 probe/window는 서버 재시작 후 재사용하지 않는다. 저장된 snapshot은 마지막 관측으로 제공하되
  server_epoch가 다르면 liveness UNKNOWN이다. current_boot/retired 기록은 자동으로 지우지 않는다.

## 4. 예약·동시성·복구 구현

- session.application.control에 순수 상태/판정 record와 정책, 예약·발행 조율·상태 반영·조회 서비스를 둔다.
  영속 구현은 session.infrastructure.persistence, REST/MQTT는 각각 infrastructure.http/mqtt에서 application을 호출한다.
- 예약 조율은 외부 트랜잭션을 받지 않는다. 별도 READ_COMMITTED 서비스에서 장치 행 생성/잠금,
  멱등 키 재검사·command 저장·제어 자리 예약을 하나로 커밋한다.
- 장치 행 최초 생성은 PK로 경쟁을 결정한다. 식별한 해당 PK/멱등 key UNIQUE 충돌만 롤백 종료 후
  새 트랜잭션에서 1회 재조회한다. 다른 제약·쓰기/커밋 실패를 무조건 재시도하지 않는다.
- 장치 제어 잠금 대기1초, 제어 SQL statement 제한2초, 제어 트랜잭션 제한3초를 로컬 범위로 설정한다.
  기존 결과 저장의 timeout/DB 전역 설정은 바꾸지 않는다. 별도 장치의 독립 실행도 검증한다.
- 멱등 재요청 조회를 먼저 수행한다. 신규 요청은 실행 자리 확보 후 DB 예약을 시도하며,
  경합 중 같은 키 생성은 트랜잭션 안에서 다시 확인한다. 같은 키/같은 요청은200, 다른 요청은409다.
- 예약 커밋 전 슬롯 포화는503·DB 변경 없음. 예약이 커밋됐으면202+Location+ID를 반환한다.
  커밋 후 worker 제출 실패/종료도 요청을 없던 것으로 취급하지 않고 추적 가능하게 기록한다.
- 트랜잭션 밖에서 probe 발행/응답 대기(최대5초), 필요한 상태/제어 모드/허가를 확인한다.
  이어 짧은 트랜잭션으로 불변 START/RETRY 본문·DISPATCHING을 커밋한 뒤 딱 한 번 발행한다.
- 관찰 기한은 DISPATCHING 이후10초 전체다. PUBACK 대기는 남은 시간 이내이며 단계마다10초를 새로 주지 않는다.
  단조 시계/주입 가능한 시간원을 사용해 테스트한다. 시간 초과 후 SDK가 실제 발행할 수도 있으므로 UNKNOWN으로 둔다.
- valid ACCEPTED/lastExecution은 실제 같은 command/session/boot에만 반영한다. timeout 뒤 보고도 무시하지 않는다.
- UNKNOWN은 fresh probe로 이전 window를 무효화한 뒤 조정한다. 같은 boot의 lastExecution·버퍼·제어 모드와
  수락 기록 보존 조건을 검사한다. 증거가 부족하면 UNKNOWN/새 START 차단을 유지한다.
- 서버 재시작: REQUESTED/PROBING은 NOT_DISPATCHED, DISPATCHING은 UNKNOWN으로 정리한다.
  ACCEPTED/UNKNOWN과 보관 세션은 fresh probe로 조정한다. 과거 START를 자동 재발행하지 않는다.
- 부팅 교체는 현재 probe 응답으로만 확정한다. 미확인 진행 건은 INTERRUPTED_REBOOT/UNKNOWN_AFTER_REBOOT,
  완료 확인한 과거 세션은 되돌리지 않는다. 새 boot의 준비/IDLE 확인 후 새 사용자 요청만 허용한다.
- DB 보존은 기존 서비스 조회로 따로 확인한다. 제어 상태 DB 오류가 기존 SESSION_PRESERVED를 억제하지 않게 한다.

## 5. 실행기·MQTT 자원 한도

- 신규 명령 worker 2개, 대기 큐0(SynchronousQueue), 전체 admission permit2, 장치별 명령 판정1개.
- background probe worker2/대기 큐0, 장치별 probe1개. 15초 scheduler는 비차단 예약만 하고,
  해당 장치의 실행 허가~판정 중에는 background probe를 멈춘다. 포화 시 다음 주기에 조회하며 측정을 자동 실행하지 않는다.
- status consumer1개/큐32개/본문 각 최대4096 bytes. 콜백은 검증 후 유한 큐에 전달하고 DB 처리를 하지 않는다.
  큐 포화는 ACK/drop+범주 진단으로 처리하고 fresh 증거로 쓰지 않는다. lastExecution/lastRelease는 후속 probe로 복구한다.
- 수신 시 단조 시각을 보관해 큐에 지연된 응답을 fresh로 오인하지 않는다. 상태 seq는 이전 보고로 되돌리지 않는다.
- status 수신은 별도 client ID·Clean Start=true/Expiry0·QoS1·RAP=true/RH=2·수동 ACK다.
- 기존 결과 발행 연결과의 지연/포화 간섭을 피하려고 제어 발행도 전용 client ID/연결을 둔다.
  계약이 허용한 발행 연결 재사용 대신 선택한 구현 구성이다. 기존 events/preservation 연결은 그대로 둔다.
- 제어 발행은 오프라인 즉시 거절, pending token 최대16, deadline을 넘겨 기다리지 않는다.
  만료가 패킷 취소를 보장하지 않으므로 pending 자리는 실제 완료 또는 client stop/destroy로 outbox가 제거될 때까지 추적한다.
  단순 연결 유실만으로 자리를 풀지 않는다. 포화는 새 업무 발행을 거절한다.
- 제어 메시지/상태는 MQTT5·QoS1·retain=false·PFI1·application/json·4096 bytes·Message Expiry10초다.
  전달된 유효 Expiry1~10만 허용한다. 명령의 최종 기한은 장치 window 검사로 보장한다.
- 제어 기능은 enabled=false 기본값이며 기존 mqtt.enabled와 허용 장치 설정을 재사용한다.
  활성화 시 기존 ID와 구분되는 status/control-publish ID를 필수 검증한다. 재연결/연결 제한은 기존 유한 정책을 따른다.
- 초기 버전은 단일 서버 프로세스의 제어 소유권을 전제로 한다. 제어를 켠 다중 서버 배포/분산 lease는 범위 밖이다.
  여러 실행기가 동시 운영되는 경우에는 이 전제를 해소하는 별도 계획이 필요하다.

## 6. REST/조회/오류

- POST /api/devices/{deviceId}/measurement-commands, GET /api/measurement-commands/{commandId},
  GET /api/devices/{deviceId}/status를 구현한다. START/RETRY 필드는 승인된 계약과 같고 unknown/null/타입 오류를 거절한다.
- POST 신규202+Location, 멱등 재요청200, 충돌/제어 중409, 문법400, 없는 장치/명령404,
  미접수/DB 결과 불명확503. DB 결과 불명확 시 클라이언트는 같은 키로 재요청한다.
- 오류 JSON은 {code,message}로 제한한다. ID가 있을 때만 해당 리소스 조회를 안내하고 stack trace/SQL/원문/비밀값은 반환하지 않는다.
- GET은 commandState/serverPreservation/deviceBuffer/liveness와 관련 식별/관측 시각을 분리한다.
  상태 조회나 화면 새로고침이 새 측정/실행 허가를 발급하지 않는다.
- 버퍼 해제 보고만 있고 DB 보존 조회가 실패하면 DB 미확인으로 표시한다. NORMAL/INCOMPLETE를 하나의 SUCCESS로 합치지 않는다.

## 7. 펌웨어 변경 경계

- main 단일 task가 hwm_session·bootId/statusSeq·window·lastExecution/lastRelease를 소유한다.
  새 command_control.c/.h(판정), command_json.c/.h(엄격한 bounded 해석), device_status.c/.h(보고 직렬화)를 추가한다.
- 기본 제품 모드 MQTT, 명시적 UART 인수 모드는 빌드 설정으로 구분한다. MQTT 모드의 USB START/RETRY는 거절,
  STATUS는 허용한다. 인수 UART 모드는 기존 시나리오를 유지하되 MQTT 실행 명령을 거절한다.
- RX에 commands와 preservation을 함께 구독하고 두 QoS1 SUBACK을 확인해야 controlReady가 된다.
  두 토픽의 크기/속성/분할 프레임을 구별한다. 기존16KiB 보존 확인 수락 경계를 축소하지 않는다.
- MQTT 콜백은 bounded 파싱/값 복사 후 기존 유한 event queue에 전달한다. 실행/상태 소유/DB 대기는 하지 않는다.
  window 검사는 task 처리 시점의 단조 시각으로 하고 정각 만료(>=10000ms)를 적용한다.
- 실행 수락 기록은 같은 task에서 window 소비·기존 hwm_start/hwm_retry 성공과 함께 갱신한다.
  commandId 전체 업무 필드 비교, 부팅/만료/새 probe/이미 소비된 window 거절을 순수 C로 검증한다.
- 상태는 RX 연결로 비차단 enqueue한다. QoS1 상태 in-flight1개+최신 pending snapshot1개만 유지하고
  추가 heartbeat 이력을 쌓지 않는다. 한 snapshot 최대4096 bytes, 송신 실패가 결과 회차/타이머를 소비하지 않는다.
- 상태 전이/판정/해제/재연결/15초 heartbeat를 보고한다. lastExecution/lastRelease는 보고 유실에도 RAM에 보존한다.
  RX 연결 정리 시 오래된 상태 outbox를 폐기하고 재연결 후 최신 snapshot을 생성한다.
- statusSeq 상한에서 fault/제어 중단, bootId는 재연결로 바꾸지 않는다. 재부팅에서만 초기화한다.
- 새 RAM 사용량·queue·최소 heap·이미지 크기를 측정한다. 현재1MiB partition을 넘으면 변경하지 않고 보고한다.

## 8. Phase·변경 파일·검증 순서

### Phase 1 — 계약 산출물과 순수 판단
- docs/mqtt/v1/{state-request,start,retry,device-status}.schema.json 및 정상/부정 예제를 추가한다.
- scripts/verify-mqtt-control-contract.ps1와 의미/경계 검사를 추가한다. 기존108개 검사는 유지한다.
- session.application.control의 CommandState/Request/DeviceSnapshot/ControlDecision 등 순수 타입·판정을 테스트한다.
- 검증: JSON/필수/nullable/중복/Expiry/4096 bytes, C05~C10·C19~C20·C24~C26의 순수 판정.
  신규 검사·기존108개, 관련 Java 컴파일/선택 테스트. 과거 인수는 신규 PASS로 표시하지 않는다.

### Phase 2 — 서버 V5·예약·REST·MQTT·복구
- application.control/{MeasurementCommandService,CommandTransactionService,CommandDispatcher,
  DeviceStatusService,DeviceControlQueryService,ControlRecoveryService} 및 cohesive DTO/정책을 구현한다.
- infrastructure.persistence의 DeviceControl/DeviceCommand/DeviceObservation/DeviceBoot 엔티티·Repository·잠금/충돌 처리,
  infrastructure.http의 MeasurementCommandController/DeviceStatusController/오류 매핑을 추가한다.
- infrastructure.mqtt의 ControlMqttConfiguration/ControlMessageCodec/DeviceStatusReceiver/ControlMqttPublisher를 추가한다.
- V5·application.yaml의 제어 비활성 기본값/별도 ID/한도, 단위·PostgreSQL/Mosquitto 통합 테스트를 추가한다.
- 가상 MQTT 장치로 C01~C04·C11~C15·C19~C27을 검증한다. 실제 쓰기/커밋 실패·동시 같은 키/장치,
  제어 DB 장애와 결과 저장 독립, 실행기 포화·deadlines·서버 재시작·새 boot/늦은 보고를 검증한다.
- 검증: compileJava compileTestJava → 관련 선택 테스트 → 필요한 기존 저장/MQTT 회귀.

### Phase 3 — 펌웨어 순수 제어·transport·빌드
- 위 새 C 파일과 firmware/esp32/{main/main.c,components/session/CMakeLists.txt,
  components/transport/transport.c,include/transport.h,test/session_tests.c} 및 제어 전용 테스트를 변경한다.
- configure-esp32.py/esp32.ps1에 명시 제어 모드 선택을 추가하고 기존 모드/credential-free 경로를 보존한다.
- C05~C10·C16~C18·C23~C24·C26~C29: 가짜 단조 시각, 같은 명령/다른 본문,
  중복 probe·분할/최대크기·큐/상태 송신 압력·재부팅/seq·USB 모드 거절을 검증한다.
- Build/CredentialFree 및 Test 이미지 빌드를 각각 구분한다. 실제 순수 C 테스트는 보드 UART 결과까지 확인한다.
  업로드 직전 IDLE/0건 확인과 필요한 BOOT/EN 안내를 진행한다. 빌드만 하고 테스트 실행 PASS라고 하지 않는다.

### Phase 4 — 집 전용 환경·실제 장비 종단 인수
- infra/device-test/acl, scripts/device-test/{BenchMqtt.java,smoke.py,fault_proxy.py}와
  신규 control_accept.py/제어 장애 주입·순수 Host 테스트를 확장한다. 기존 도구에는 명시 COM4 선택을 제공한다.
- 집 LAN용 전용 DB/브로커/좁은 방화벽·로컬 비밀 설정을 준비하고 기존 인수 모드로 연결 smoke를 확인한다.
- MQTT 제어 이미지 configure → Build → 사용자 BOOT/EN 안내 → 수동 Flash 후 IDLE/controlReady를 확인한다.
- C01~C29 중 하드웨어 조건을 실제 실행한다. REST→1회 측정→DB 원본→유효 확인→lastRelease를 교차 확인한다.
  지연 명령/중복/만료/응답 유실/UNKNOWN·서버/브로커 재시작·부팅 변경·버퍼 보관/RETRY·USB 거절을 포함한다.
- 프록시 지연과 실제 Wi-Fi 단절을 구분한다. 물리 Wi-Fi/EN 조작이 필요하면 해당 시점에만 안내한다.
- UART 인수 모드로 기존13종 장비 회귀를 수행하고 마지막 MQTT 제어 이미지로 복귀·IDLE/0건 확인한다.
  새 계정/원문/SSID는 증거에서 제외하고 Git 제외된 로컬 요약만 남긴다. 전용 서비스/시험 규칙을 안전 종료한다.

### Phase 5 — 최종 통합·결과 보고
- 신규 제어 계약 검사 + 기존 verify-mqtt-contract.ps1 + verify.ps1 -Mode Full을 실행한다.
- verify-esp32.ps1 Build/CredentialFree/Host, 새/기존 순수 C 실제 UART 결과 및 장비 인수 증거를 집계한다.
  포트 지정이 없는 과거 COM3 기본 호출은 사용하지 않고 새 Port 옵션 또는 명시 COM4 도구를 사용한다.
- 기존158/108/순수C31/Host19/장비13종을 기준으로 신규 추가·변경분과 실행/재사용을 구분한다.
- 종료 코드·테스트 수·실패·오류·건너뜀·캐시/새 실행·flash hash·바이너리 크기·heap·버퍼 종료를 보고한다.
- HANDOFF·작업 명세·실행 안내·설계 요약을 갱신하고 status/diff/비밀값 출력 여부/신규 Java 줄 수를 검토한다.
- 전체 인수/필수 검증 PASS면 VERIFIED, 사용자 최종 리뷰 후 DONE. 커밋 초안:
  feat: control ESP32 measurements and observe device status over MQTT 5

## 9. 완료·중단·위험

- C01~C29 각각에 자동/모의/실제 장비 증거를 연결한다. 실제 센서 성능·운영 보안은 이 PASS에 포함하지 않는다.
- Phase당 자동 수정 최대5회, 동일 근본 원인3회 실패 시 원인/시도/필요 조치를 보고하고 중단한다.
  장비 미연결·업로드 불가·계약 밖 결정·파티션 초과는 해당 단계에서 보고한다. 테스트 약화/삭제로 통과시키지 않는다.
- 신규 Java/Kotlin200줄 초과는 보고서에 리뷰 대상으로 표시한다. 응집된 코드를 줄 수만으로 분할하지 않는다.
- 승인된5초/10초의 짧은 허가는 집 망에서도 지연 시 안전 거절을 낳을 수 있다. 측정 실행으로 오인하지 않는다.
- DB/발행/장치 실행은 원자적이지 않다. UNKNOWN과 재부팅 전 미확정은 설계상 남는다.
- 장치 영속 버퍼/명령 저널을 새로 넣지 않는다. RAM 유실을 정상 버퍼 해제로 표시하지 않는다.
- 구현/검증 결과는 아래 Phase Result를 따른다. 최종 통합·실제 장비 인수 전에는 전체 VERIFIED로 표시하지 않는다.

## 10. Phase Result — 현재 체크포인트

- Phase 1: 신규 제어 스키마/예제/검사100, 기존 계약108 PASS. Java 정책10 PASS, compileJava/compileTestJava exit0.
- Phase 2: V5/장치별 잠금/예약/REST/별도 MQTT 제어·상태/UNKNOWN·재부팅·해제 이력을 구현했다.
  기존 JdbcTemplate을 사용하는 DeviceControlStore에 제어 SQL을 모았고, application의 짧은 트랜잭션으로 조율한다.
  불필요한 JPA 매핑 중복 없이 기존 application→영속 구현 의존을 유지했다. accepted_at/release_confirmed_at으로 완료 이력을 보존한다.
  관련 선택 테스트21개(정책10/runtime2/PostgreSQL8/HTTP+Mosquitto1) 실패0/오류0/건너뜀0, exit0.
  실제 커밋 실패 rollback·전역 멱등 키 경합·버퍼 보존/해제·새 boot·서버 복구·실행기 한도를 포함한다.
- Phase 3: 명령 파싱/일회성 창/중복 실행 방지/상태 직렬화와 transport/main 연결, C12개 추가.
  esp32.ps1 -ControlMode MQTT(기본)/UART로 모드를 선택한다. UART 검증에 명시 Port를 전달할 수 있다.
  Build -CredentialFree와 Test -CredentialFree exit0. 테스트 이미지는196528 bytes/1MiB 여유81%.
  CredentialFree 일반 빌드는 빈 설정으로 실행부가 최적화될 수 있으므로 실제 MQTT 이미지 크기 증거로 사용하지 않는다.
  COM4 IDLE/샘플0 확인 후 사용자의 BOOT/EN 준비에 따라 Test flash/hash검증 PASS. 실제 UART43개 실패0/무시0, exit0.
- Phase 4 진행: 전용 인증 브로커/PG18/V5와 LAN192.168.0.2·192.168.0.0/24·TCP18883 규칙 준비.
  초기 방화벽 생성은 관리자 권한 부족으로 실패했고 UAC 관리자 실행 후 해당 규칙만 활성화했다.
  실제 MQTT 이미지919488 bytes/1MiB 여유12%, flash/hash PASS. 집 Wi-Fi/IP·두 QoS1 구독 PASS.
  Host19개/실패0/오류0/건너뜀0, 기존 PC smoke PASS. 실제 guards+normal/unknown/release-loss PASS.
  REST 멱등성/충돌·MQTT 중복/내용 충돌·boot/window/만료/retained/중복 JSON 거절·USB 모드 거절을 확인했다.
  상태 보고만 차단한 UNKNOWN/신규 START 차단/자동 명령 재발행0회, 실제 NORMAL 보존·해제,
  해제 보고 차단 중 DB와 장치 축 분리/서버 재시작 후 lastRelease 복구를 확인했다.
  보드 최저 heap108336 bytes(첫 normal), 종료 IDLE/샘플0. 현재 RETRY/소진 인수 중이며 기존 장비13종/최종 통합은 남았다.
  probe 순서 검토에서 취소된 background 발행이 새 probe 뒤에 도착할 수 있어 장치별 발행/응답을 직렬화했다.
  대기 시간도5초/10초 기한에 포함하며 probe pool의 장치 순서를 회전한다. 신규 동시성+MQTT 선택4개 PASS.
  제어 DB 실제 행 잠금과 결과 커밋/보존 확인 독립성 테스트 PASS. 백엔드 전체181개/30 suite,
  실패0/오류0/건너뜀0, test exit0(新실행3m26s). 테스트 패키지화 최종 build는 아직 남았다.
  실제 retry PASS: 첫5회+REST RETRY1회 전체payload hash1개, 소진 후65초 무전송, 같은 세션 정상 보존/해제.
  이전 도구 UART 갱신 누락 시험의 MANUAL_WAIT/60개도 같은 세션 REST RETRY로 해제했다.
  실제 EN 재부팅 PASS: 미확인/샘플 보관 중 재부팅, INTERRUPTED_REBOOT/UNKNOWN_AFTER_REBOOT,
  정상 해제로 오인 없음/빈 INCOMPLETE 생성 없음/이전 bootId 명령 거절/최종IDLE0건.
  기존 UART0개 이미지는 Build PASS(919600 bytes)만 완료했다. 보드는 아직 MQTT60/919488 bytes이며 추가 flash 전이다.
- Phase 5: 최종 통합 진행 중. 재개 후 제어 계약100/기존 계약108 실패0/건너뜀0 PASS.
  루트 verify.ps1 -Mode Full exit0/build PASS(JDK25). test는 UP-TO-DATE로 앞선 실제181개 PASS를 재사용했다.
  장비 잔여 인수와 최종 이미지 복원/Host 회귀/안전 종료는 아직 남았다.
- Git: codex/mqtt-control-implementation, 전체 미커밋/staged0. 커밋·푸시·PR·병합 미실행, 별도 승인 대상.
- 자동 수정: Phase 1/2 검증 실패0, Phase 3 빌드/실제 C 실패0.
  Phase 4 동일 도구 원인 실패2회(retained 부정 입력을 정상 속성 검사에 넣음), 진단 보강 후 검사 경로 분리1회 수정·재검증 PASS.
  추가 DB 독립성 테스트: Paho AutoCloseable 컴파일 오류→명시 close(2번째 수정), 연결 상태 close 오류→disconnect(3번째),
  고정 Paho String[]/listener subscribe의 자기 재귀를 javap로 확인→MqttSubscription 오버로드(4번째), 재검증 PASS.
  실제 소진 판정의 UART STATUS 갱신 누락→2초마다 갱신(5번째), 기존 버퍼 복구/전체 소진 재검증 PASS.
  Phase 4 자동 수정5회에 도달했다. 추가 실패로 수정이 필요하면 중단한다. 테스트 삭제/조건 약화 없음.
  이후 신규 Host21개 중1개 실패: 실제 DEVICE_STATUS heartbeat를 시험 응답으로 오인하는 테스트 격리 문제.
  규칙에 따라 수정 중단/보고 후, 사용자가 이 원인에 한정한 추가 수정1회를 명시 승인했다.
  UUID 상관값으로 자기 시험 프레임을 구분하고 실제 heartbeat는 무시한다. 자기 프레임 유실/복구/Expiry 검사는 유지한다.
  추가 한도 사용1회, Host21개 실패0/오류0/건너뜀0 PASS, PC smoke/확장 ACL 거절4종 PASS.
  추가 실패가 생기면 더 수정하지 않고 중단한다. 현재 최신 서버의 동시 REST START 실제 인수 중이다.
  최신 동시 REST START 인수 PASS: 서로 다른 키의202 하나/409 하나, 실제1회 측정·원본60개·최종IDLE0건.
  추가 계약 대조에서 round 상한 RETRY 증가와 마지막 statusSeq 보고 시점의 제어 차단 누락을 확인해 다시 중단/보고했다.
  사용자가 카운터 경계 보완1회를 별도 승인했다. round 증가 거절·마지막 유효 보고의 fault/새 실행 차단,
  UART에서도 fault 뒤 새 실행 거절, 경계 C2개 추가(기존43+2=45)를 구현했다.
  45개 Test/CredentialFree Build·flash/hash·실제 UART45개 PASS(197392 bytes, 실패0/무시0), exit0.
  추가 승인 두 건은 각각 Host 격리/카운터 경계로 한정하며 이후 실패 시 더 수정하지 않고 중단한다.
  카운터 보완 이후 실제 UART0개 이미지919744 bytes Build/flash/hash, 문법7종/CRLF/STATUS와 normal0개 PASS.
  UART모드 MQTT START 거절·실행 허가 없음·controlReady=false·최종IDLE0건 PASS. UART1개 이미지 Build 중이다.
  최신 UART1개 Build/flash/hash·실제 normal1개 PASS, 최종IDLE0건. 현재 UART59개 Build 중이다.
  최신 UART59개 Build/flash/hash·normal59개 PASS. UART0/1/59개 신규 회귀 완료, 각 최종IDLE0건.
  UART60개 Build PASS(919744 bytes), 현재 flash 중이며 정상60개/기존 장애 회귀는 다음이다.
  최신 UART60개 flash/hash·normal60개 PASS. 최신 UART normal0/1/59/60 네 종류 완료.
  실제0/1/59 세션 stats_sample_count0/통계4개NULL/누락 건수60-N 별도 DB 읽기 대조 PASS.
  현재 UART60/일반18883/919744 bytes로 기존 retry 회귀 진행 중이다.
  기존 UART retry PASS(서버 중지 중2회 발행·같은payload/원본/재수신 진단·최종해제), 현재 exhaust 회귀 중이다.
  C27용 proxy-status-pressure를 추가했다. 상태 PUBACK만 차단해 RX outbox 정리/재연결 중 실제 보존/해제를 검사하고,
  이후 상태 송신 장애를 제거한 뒤 독립적인 완료 UUID/중복 확인 guard를 검사한다. 아직 실제 실행 전이다.
  기존 UART exhaust PASS(5회/고정버퍼/65초 무전송/지연 확인 해제), 현재 db-outage 회귀 중이다.
  보드는 일반18883이고 로컬 build는 이후용 프록시18884/UART60/919744 bytes다. 아직 프록시 이미지를 flash하지 않았다.
  LAN 한정 TCP18884 시험 규칙을 UAC 관리자 실행으로 추가했다. 종료 시 두 시험 규칙만 비활성화한다.
  기존 UART db-outage PASS(전용 DB 중지 중 보관/복구 후 같은 원본 보존·해제), broker-restart 회귀 중이다.
  알려진 로컬 비밀번호의 Git 대상205개 파일 검사 위반0, 실제 비밀 설정/빌드/증거의 Git 제외 확인 PASS(값 미출력).
  중단 후 broker-restart exit0/PASS 확인: 실제 발행2회·payload hash1개·구독 복구 후 보존/해제·최종IDLE0건.
  현재 일반18883/UART60 보드로 수동 Wi-Fi 단절·복구 인수 중이다. 프록시18884 build는 아직 flash하지 않았다.
  Wi-Fi만 끄기 어렵다는 사용자 응답으로 물리 Wi-Fi 단절은 환경 제약 NOT VERIFIED다(실제 단절 미실행).
  MANUAL_WAIT/60개를 초기화하지 않고 시험 프로세스만 중지/서버 재개 후 IDLE0건을 확인했다. 수동 EN 재부팅 회귀로 진행한다.
  기존 UART reboot exit0/PASS: EN 부팅·옛 확인 거절·자동 START 없음·서버 원본60개 일치·최종IDLE0건.
  프록시18884/UART60 이미지919744 bytes를 확인했고 사용자 다운로드 모드 준비 후 flash 대기 중이다.
  프록시 이미지 flash/hash exit0/PASS, 실제 proxy-early PASS(결과 PUBACK 없이 보존 확인 선도착/해제/IDLE0건).
  사용자가 현재 시험 후 안전 종료·다음 재개를 요청했다. 추가 시험/일반 MQTT 이미지 복원은 하지 않았다.
  전용 backend·브로커/DB 중지, 두 시험 방화벽 규칙 False, 시험4포트 listener0 확인.
  종료 COM4 STATUS IDLE/샘플0/tx_active0/rx_ready0, min_heap107400 bytes. DB 볼륨/로컬 설정 보존.
  Git staged0·diff --check PASS. 남은 시험과 재개 순서는 HANDOFF 최신 체크포인트를 따른다.

## 11. 인수 증거 매핑 — 진행 중

| 조건 | 현재 증거와 남은 부분 |
|---|---|
| C01~C03 | 실제 normal/both·DB 원본 대조, REST 멱등/충돌·PostgreSQL 커밋/키 경합 PASS |
| C04 | 최신 normal의 서로 다른 키 동시202/409·실제1회 측정·DB 원본60개 PASS |
| C05~C10 | 실제 guards/normal/reboot 및 실제 C12개(가짜 단조 시각 경계 포함) PASS |
| C11~C15 | 실제 UNKNOWN/신규 START 차단/후속 복구와 MQTT 모의 장치·예약/dispatch 복구 테스트 PASS |
| C16~C18 | 실제 retry:5회/65초 무전송/동일payload/한회차/해제 후 RETRY 거절, C 판정 PASS |
| C19~C20 | PostgreSQL seq/retired boot/epoch 복구와 정책45초 경계, 실제 서버 재시작 PASS |
| C21~C23 | release-loss의 DB/해제 축 분리·lastRelease 복구, DB 대조 순서 테스트 PASS. 기존 확인 유실 회귀는 남음 |
| C24 | 실제 EN/INTERRUPTED_REBOOT/UNKNOWN_AFTER_REBOOT·옛 boot 거절 PASS |
| C25 | 실제 NORMAL60개·서버 INCOMPLETE 전체 회귀·최신 UART0/1/59개 PASS |
| C26 | 제어 계약100/기존108·엄격 codec/C·실제 guards·확장 ACL PASS. 기존16KiB/부정 확인 회귀는 남음 |
| C27 | 실제 PG 제어 행 잠금 중 기존 결과 커밋/확인, 실행기/큐 한도, 실제 상태 보고 유실 PASS. 장치 송신 압력 회귀는 남음 |
| C28 | 실제 MQTT모드 USB START/RETRY 거절과 UART모드 MQTT START/허가 거절 PASS |
| C29 | 기존 C31 포함 총45·Host21·backend181 PASS. 기존 장비13종 중 normal0/1/59/60+retry/exhaust/db-outage/broker-restart/reboot/proxy-early 신규 PASS. Wi-Fi 단절은 환경 제약 NOT VERIFIED, 프록시2종 미실행 |

각 행의 미검증 부분까지 끝나기 전 전체 VERIFIED로 표시하지 않는다. 로컬 증거는 .tools/device-test/control-*.json에 보관한다.
