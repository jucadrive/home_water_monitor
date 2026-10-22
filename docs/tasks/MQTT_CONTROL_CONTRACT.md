# START·상태 보고 계약 설계 및 후속 구현 계획

상태: 계약 및 전체 실행 계획 사용자 승인 완료. 구현/인수 진행 중.
실제 변경·실행 결과: [MQTT_CONTROL_IMPLEMENTATION.md](MQTT_CONTROL_IMPLEMENTATION.md).
아래 조사·초안 기록은 계약 승인 전 체크포인트이며 현재 승인 상태는 이 상단 기록을 따른다.
2026-10-22 사용자가 '1. START 상태 보고 계약 설계'를 요청했다. 승인 범위는 조사·설계 문서와 계획이다.
계약 본문: [../MQTT_CONTROL_CONTRACT.md](../MQTT_CONTROL_CONTRACT.md).

## 1. 현재 사실과 보존 범위

- 기준 main 1aaaa06(PR #7), push/PR/병합 CI 성공은 앞선 Git 작업의 확인 결과다. 이번 설계에서 재실행하지 않는다.
- 시작 전 docs/HANDOFF.md와 docs/tasks/ESP32_FIRMWARE_INTEGRATION.md에 미커밋 변경2개가 있었다.
  Git 완료와 사무실 망 조사 제외 기록이며 보존한다. stage/commit/push/장치 조작은 하지 않는다.
- 서버 SessionMqttConfiguration/MqttSessionReceiver/BoundedMqttPublisher는 결과 수신/커밋 후 확인만 처리한다.
  HTTP Controller/START 발급/명령 저장/장치 상태 수신은 없다. 기존 발행 타임아웃10초는 취소를 보장하지 않는다.
- 펌웨어 main.c/control.c/session.c에는 USB 제어와 불변 RAM 버퍼가 있다. 단일 제어 task가 상태를 소유한다.
  transport.c의 RX는 Clean Start=true/Expiry0, preservation 구독 하나이며 commands/status는 없다.
- 기존 V1~V4는 보존된 세션/원본/누락/재수신 진단이다. 실행 요청을 기존 세션 테이블에 먼저 넣으면 안 된다.
- 기존 ACL은 events/preservation만 허용한다. 신규 명령/상태 ACL은 아직 적용하지 않았다.
- 기존 SESSION_RESULT/SESSION_PRESERVED/31개 순수 C/13종 장비 인수는 현재 기능의 기준이며 신규 제어 검증으로 대체하지 않는다.

## 2. 이번 산출물과 설계 결정 제안

- 서버 UUIDv4 sessionId/commandId 발급, REST Idempotency-Key와 서버 영속 명령 기록.
- 짧은 fresh 상태 조회 → bootId와 장치 단조10초의 일회성 window → START/RETRY.
  NTP 의존 없이 과거/재부팅 전/이미 소비한 실행 허가를 거절한다.
- 업무 자동 명령 재발행0회, QoS1 중복은 한 번만 실행. 타임아웃은 UNKNOWN과 fresh 상태 조정.
- snapshot 전이 보고+15초 heartbeat/상태 조회,45초 stale. 역전 방지 bootId/statusSeq.
- 명령 수락/서버 DB 보존/장치 해제/마지막 관측 신선도를 별도 표시.
- USB와 MQTT 실행 모드 상호 배제. 재부팅 RAM 유실 허용 유지, 서버 명령 이력은 신규 영속화 필요.
- 계약 값/필드/새 API와 저장·연결·권한의 방향은 사용자 승인받았다. 실제 V5/파일/실행기 한도는 별도 실행 계획 승인 대상이며 미구현이다.

## 3. 인수 조건과 후속 테스트 목록

| ID | 사례 | 기대 결과 |
|---|---|---|
| C01 | REST START 정상 | 서버 UUID/명령 기록 → fresh 허가 → 장치1회 측정 → 보존/해제 별도 확인 |
| C02 | 같은 REST 키/본문 재요청 | 같은 ID/상태, 새 측정/발행 없음 |
| C03 | 같은 키/다른 장치·종류·세션 | 409, 기존 기록 유지 |
| C04 | 서로 다른 키의 동시 START | 장치별 하나만 예약, 나머지409, 실제 측정1회 |
| C05 | MQTT START 중복/뒤늦은 중복 | 원래 수락 보고 또는 유효 허가 없음으로 거절, 재측정 없음 |
| C06 | 같은 commandId/다른 내용 | COMMAND_CONFLICT, 기존 측정/버퍼 불변 |
| C07 | 재부팅 전 bootId의 START | BOOT_MISMATCH, 새 측정 없음 |
| C08 | window10초 경계 직전/정각/이후 | 직전만 수락 가능, 정각부터 만료 |
| C09 | 새 상태 조회 뒤 이전 window | 거절, 예전 명령 재활성화 없음 |
| C10 | STATE_REQUEST 중복/재정렬 | 매 처리 새 허가, 과거 window 재사용 불가, 안전 거절 허용 |
| C11 | 오프라인/retained/만료 MQTT 명령 | 오프라인 예약 실행 없음, retained/유효성 위반 거절 |
| C12 | PUBACK만 수신 | 명령 ACCEPTED/측정 완료/버퍼 해제라고 표시하지 않음 |
| C13 | 수락 응답 유실/발행 타임아웃 후 늦은 실행 | UNKNOWN, 중복 발행 없음, fresh lastExecution으로 조정 |
| C14 | 서버 예약 전/후·발행 전/후 종료 | durable 같은 키 복구, NOT_DISPATCHED 또는 UNKNOWN, 과거 자동 실행 없음 |
| C15 | UNKNOWN 뒤 새 START | 조정 전 차단, 새 probe의 허가 무효화/실행 기록 확인 후에만 해제 |
| C16 | 측정/백오프/미확인 버퍼 중 START | 실제 장치 거절, 기존 버퍼/세션 유지 |
| C17 | MANUAL_WAIT RETRY 정상/중복 | 같은 세션/목록, 새 commandId, 새5회 회차는 한 번만 |
| C18 | RETRY 직전 보존 확인/다른 세션 RETRY | 버퍼 없음/다른 세션 거절, 재측정 없음 |
| C19 | status 중복/seq 역전/다른 boot 늦은 보고 | 현재 상태/완료 결과 되돌림 없음, 새 boot는 probe 확인 |
| C20 | heartbeat 지연/45초 초과/서버 재시작 | 과거 수신으로 FRESH 연장 없음, STALE/UNKNOWN 표시 |
| C21 | DB 보존 후 장치 확인 유실 | 서버 보존 완료/장치 해제 미확인, 새 START 차단 |
| C22 | 해제 상태와 DB 관측 순서 역전 | 각 사실 따로 기록, 자체 DB 확인 없이 보존 성공 단정 없음 |
| C23 | 해제 보고 유실 후 같은 부팅 재연결 | lastRelease로 해제 관측 복구 |
| C24 | 미확인 중 EN 재부팅 | INTERRUPTED_REBOOT/UNKNOWN_AFTER_REBOOT, IDLE을 정상 해제로 오해하지 않음 |
| C25 | NORMAL/INCOMPLETE 결과 | 기존 보존 규칙 유지, 불완전을 정상 측정으로 표시하지 않음 |
| C26 | 잘못된 JSON/필드/크기/속성/장치/ACL | 전체 거절, 부분 실행/임의 응답 경로/권한 우회 없음 |
| C27 | 서버 상태 DB 장애/느린 처리·상태 송신 실패 | 기존 결과 저장/보존 확인·장치 측정 루프 독립, 무제한 큐 없음 |
| C28 | USB START/RETRY와 MQTT 제어 동시 사용 | 모드에 따라 하나만 허용, 서버 조정 전제 유지 |
| C29 | 기존 USB 인수/결과 재시도 회귀 | 명시적 인수 모드와 기존 5회/불변 목록/커밋 후 확인 유지 |

현재 표는 검증 설계이며 실제 PASS가 아니다. 기계 JSON 검증/실제 중복·시간·재시작 테스트가 필요하다.

## 4. 승인 후 구현 Phase 제안

1. 계약 구체화: 신규 commands/status JSON Schema·정상/부정 예제·독립 계약 검사.
   현행 두 메시지 검사108개 회귀, 새 메시지 형식/의미/경계/시간 관련 인수 기준을 검증한다.
2. 서버: session.application의 제어 서비스/순수 판단, infrastructure의 REST·MQTT 제어·상태 수신·JPA,
   새 V5 명령/장치 제어 테이블과 조회. 기존 원본 저장 경로는 유지한다. DB 잠금·같은 키 경합·서버 종료/UNKNOWN을 통합 검증한다.
3. 펌웨어: session 제어 상태/허가·중복 기록, transport의 명령 구독/상태 발행, main 단일 task 제어.
   순수 C 단위/가짜 단조 시간/Host·실제 UART로 중복·만료·순서 역전·상태 송신 압력을 검증한다.
4. 전용 인수: ACL/PC 도구/프록시를 확장하고 가상 장치 후 실제 ESP32에서 REST부터 보존/해제까지 검증한다.
   실제 센서/사무실 망 조사는 제외한다. 장비 업로드가 필요할 때만 현장 BOOT/EN 조작을 안내한다.

위는 예상 순서다. 계약 승인 후 실제 파일·DDL·동시성·실행기 한도를 포함한 구현 계획을 먼저 확정/승인받는다.
신규 의존성/계층 전환/서버 outbox/운영 보안 정책 완화는 제안하지 않는다.

## 5. 예상 변경 파일과 검증

- 이번 변경: docs/MQTT_CONTROL_CONTRACT.md, 이 작업 명세, docs/HANDOFF.md.
  기존 미커밋 ESP32 명세 수정은 보존한다. backend/firmware/ACL/schema/script 변경 없음.
- 후속 후보: docs/mqtt/v1의 제어 schema/examples, scripts의 신규 계약 검사,
  backend/session/application 및 infrastructure/{http,mqtt,persistence}, resources/db/migration/V5__*.sql,
  firmware/esp32/{components/session,components/transport,main,test}, infra/device-test/acl, scripts/device-test.
- 후속 검증: 새 계약 검사 + 기존 verify-mqtt-contract.ps1, verify.ps1 -Mode Full,
  verify-esp32.ps1 Build/CredentialFree/Host, esp32.ps1 Test 실제 실행, 명령/상태 실제 장비 인수.
- 이번 실행: Git/소스/기존 계약/공식 MQTT 규격 조사, 문서 수동 대조, git diff --check.
  신규 계약/명세/HANDOFF 3개 문서의 로컬 링크·후행 공백 검사 PASS, git diff --check 종료0.
  미커밋 변경4개(기존2개 보존+신규2개), staged0. 신규 구현/자동 수정 횟수는 적용 대상 없음.
  컴파일/DB/브로커/장비/자동 계약 테스트는 NOT RUN. 문서 링크·공백 검사는 의미적 검증의 대체가 아니다.

## 6. 위험과 다음 단계

- 안전한 명령 만료와 중복 처리를 위해 상태 조회/짧은 허가 왕복이 추가된다. 느린 망에서는 안전 거절이 늘 수 있다.
- 부팅 전 명령 이력을 장치에 영속화하지 않으므로 재부팅 이전 실행 여부가 미확정인 경우를 없애지는 못한다.
- status 별도 서버 연결/새 V5/API/ACL/제어 모드는 공개 계약·DB·연결 구성 변경이며 사용자 승인 대상이다.
- 제한1MiB 이미지의 현재 여유13%이므로 빌드 크기/heap/queue와 상태 송신·보존 확인의 상호 영향을 실제 확인한다.
- 사용자 계약 승인을 받았고 별도 실행 계획을 제시했다. 구현 승인은 아직 없다.
- 문서 공유 목적의 커밋·푸시·PR·main 병합은 별도 사용자 승인받았다. 계약 내용 승인을 뜻하지 않는다.
  codex/mqtt-control-contract에서 문서4개만 통합한다. 이 기록은 실행 전 체크포인트다.
- 커밋 초안: docs: propose MQTT start and device status contracts.
