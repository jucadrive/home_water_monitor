# MQTT 5.0 실제 서버 연동 실행 계획

상태: DONE — 2026-10-22 사용자 최종 리뷰 승인. Phase 1~3 구현·자동 검증 완료.
Git 통합 승인: 같은 날 사용자가 커밋·푸시·CI·PR·main 병합 및 병합 후 CI 확인까지 승인했다.
아래 Phase Result의 Git/원격 CI 기록은 Git 통합 실행 전 체크포인트다.
2026-10-22 사용자가 계약 작성만으로 종료한 범위를 정정하고 실제 MQTT 연동 구현을 요청했다.
기준: main 810ed9a, 현재 codex/mqtt-message-contract의 미커밋 계약 파일 17개를 보존한다.
운영 규칙: AGENTS.md와 Harness.md. 단일 에이전트, 승인 후 Phase 1~3 순차 실행.

## 완료 목표
- 실제 MQTT 5 브로커에서 SESSION_RESULT를 구독·검증하고 기존 ProcessTdsSessionService로 저장한다.
- 실제 서비스 트랜잭션 커밋 성공 후 SESSION_PRESERVED를 QoS 1로 발행한다.
- 장비 없이 Mosquitto 테스트 컨테이너·모의 장치·PostgreSQL로 전체 경로를 검증한다.
- 장비 펌웨어/START/명령/장치 상태 보고, 운영 배포·DB 변경·Git 통합은 범위 밖이다.

## 승인할 기술·실패 처리 범위
- 기존 Spring Integration MQTT의 v5 어댑터와 명시적 Eclipse Paho v5 의존성을 사용한다.
  버전은 실제 Boot 의존성 관리 및 공식 지원 정보를 확인해 고정하고 기록한다.
- Mosquitto는 테스트 전용 컨테이너로 추가한다. 운영 브로커·계정·ACL·Compose를 변경하지 않는다.
- MQTT 입력 코드는 session.infrastructure.mqtt에 두고 application 서비스로만 저장한다.
  domain·DB 스키마·기존 트랜잭션/최초 PK 충돌 1회 복구는 유지한다.
- 기본 enabled=false. 활성화 시 broker URI·허용 장치·고유 수신/발행 client ID를 검증한다.
  자격 증명은 외부 설정으로 받고 로그/문서/예제에 실제 값을 남기지 않는다.
- 수신 클라이언트는 고정 ID, Clean Start=false, Session Expiry=86400초로 구독을 유지한다.
  이 기간/브로커 저장소를 넘는 유실 복구는 장치가 보유한 동일 목록 재전송에 의존한다.
- 허용 장치의 정확한 events 토픽만 QoS 1 구독한다. Retain As Published=true로 발행 RETAIN을
  보존해 retained 입력을 거절하며 기존 retained 메시지를 요청하지 않는다.
- 입력/확인에 Message Expiry를 지정하지 않는다. 지연 확인은 기존 버퍼 상관관계로 처리한다.
- 연결 제한 10초, 확인 발행 완료/PUBACK 대기 10초, keepalive 30초.
  재연결은 라이브러리의 유한 상한 지수 백오프를 사용하며 실제 간격/상한을 소스와 테스트로 기록한다.
- 수신은 수동 MQTT ACK를 사용한다. 정상/불완전 저장 및 비교 커밋 후 확인 발행을 시도한다.
  원본 처리 실패/확인 발행 실패를 성공으로 응답하지 않는다.
- 잘못된 입력·DB 실패·확인 발행 실패도 해당 수신을 ACK해 브로커의 독성 메시지 반복을 막는다.
  이는 업무 보존 확인이 아니다. 장치 버퍼는 SESSION_PRESERVED 수신 전까지 유지하며 기존 회차당
  5회·PUBACK 후 최대 30초·Full Jitter 재전송 합의로 복구한다. 장치 구현은 이번 범위 밖이다.
- 서버가 ACK 전에 종료되면 브로커 재전달이 가능하며 기존 재수신 로직으로 처리한다.
  DB 커밋 후 확인 유실에도 원본을 되돌리거나 삭제하지 않는다.
- 서버의 별도 업무 재시도 큐/outbox·무한 저장 재시도·새 오류 응답 타입은 도입하지 않는다.
- 입력 오류/저장 실패/발행 실패/연결 실패는 원문·자격 증명 없이 구분해 기록한다.
  콜백 스레드에서 확인 PUBACK을 기다려 교착되지 않도록 수신과 발행 클라이언트를 분리한다.
  무제한 작업 큐를 도입하지 않고 과부하/종료 시 동작도 검증한다.

## 구현 Phase
### Phase 1 — Java 메시지 해석·처리 경계
- UTF-8/16 KiB/중복 JSON 키/타입/미지 필드/수치·시각/seq/토픽·허용 장치를 엄격히 검증한다.
- Java DTO/codec 및 기존 명령·결과 매핑, 커밋 이후 확인을 수행하는 처리기를 추가한다.
- 외부 트랜잭션에서 실행하지 않고 기존 서비스 프록시 호출이 커밋을 마친 후 발행한다.
- 정상·불완전·빈 목록·동일/충돌·부정 입력과 실패 시 확인 미발행을 단위 검증한다.

### Phase 2 — MQTT 5 어댑터·설정·수명주기
- 의존성, 조건부 설정, 수신/발행 어댑터와 속성·수동 ACK·연결/발행 제한을 구현한다.
- 비활성 시 기존 컨텍스트 동작을 유지하고 활성 설정 오류는 조기에 거절한다.
- 설정 예제·실행 안내·계약의 실패 처리 절을 갱신한다. 비밀번호/원문 로깅은 금지한다.
- 프로토콜/실제 속성·retain·ACK·재연결·종료 동작을 테스트 브로커로 검증한다.

### Phase 3 — DB+브로커 통합·회귀·보고
- 모의 장치 → MQTT 5 → 실제 application/DB → MQTT 확인을 검증한다.
- 정상 60개·불완전·빈 목록·동일/충돌 재수신·추가/삭제 거절을 검증한다.
- 실제 쓰기/커밋 실패 시 확인 없음, 커밋 후 확인 발행 실패 시 원본 유지,
  같은 목록 재전송으로 재확인, 브로커 재시작 후 재구독/수신을 확인한다.
- docs/mqtt 예제와 Java codec의 일치를 검사해 오프라인 스키마와 동작의 차이를 방지한다.
- 관련 컴파일/선택 테스트, ./scripts/verify-mqtt-contract.ps1,
  ./scripts/verify.ps1 -Mode Full 순으로 검증한다. 성공한 검사는 근거 없이 반복하지 않는다.
- 실제 실행/캐시, 테스트 수·실패·오류·건너뜀·종료 코드를 기록한다.

## 변경 예상 파일
- build.gradle: Paho v5, 필요한 테스트 컨테이너 의존성만.
- src/main/java/.../session/infrastructure/mqtt/: 설정·codec·입력 처리·확인 발행·수명주기.
- application.yaml: 비활성 기본값과 외부 설정 매핑.
- src/test/java/.../session/infrastructure/mqtt/, src/test/resources/: codec·처리·브로커/DB 통합 테스트.
- docs/MQTT_MESSAGE_CONTRACT.md, 이 명세, HANDOFF 및 관련 실행 안내.
- 마이그레이션·기존 domain·운영 Compose·CI 보안 설정은 변경하지 않는다.

## 위험·완료 판정
- 테스트 컨테이너 이미지 다운로드와 Docker 실행이 필요하다. 접근 실패 시 승인 정책대로 처리한다.
- ACK와 DB/MQTT는 하나의 원자적 트랜잭션이 아니다. 장치 버퍼 재전송 의무와 복구 한계를 명시한다.
- 실제 장비 MQTT 5 지원·패킷 버퍼·펌웨어 재전송/버퍼 삭제는 NOT VERIFIED로 남긴다.
- 신규 Java 200줄 초과 파일은 리뷰 대상으로 보고하고 응집성을 해치는 분할은 하지 않는다.
- Phase당 자동 수정 최대 5회, 동일 근본 원인 3회 실패 시 중단한다.
- 전체 인수 조건·필수 자동 검증 통과 시 VERIFIED, 사용자 최종 리뷰 후 DONE.
- 커밋·푸시·PR·main 병합은 이번 구현 승인과 별개다.

## Phase Result — 전체 완료
- Phase 1: strict JSON codec·기존 명령/결과 매핑·외부 트랜잭션 거절·커밋 반환 후 발행·실패 분류/수동 ACK.
  선택 단위 33개 PASS. 최종에는 경계 사례 7개를 추가해 codec 35 + receiver 5 = 40개다.
- Phase 2: 기본 비활성 설정·허용 장치별 MQTT 5 구독·분리된 발행 연결·유한 PUBACK 대기·수명주기.
  구성/발행 제한 9개 PASS. 기존 어댑터의 연결 대기 루프를 재사용하지 않고 오프라인 발행을 즉시 거절한다.
- Phase 3: 실제 Mosquitto 2.0.22/PostgreSQL 18/모의 장치로 메시지·DB 연동 10개, 수신 중지/재개·직렬 처리 2개 PASS.
  중지 동안 브로커에 대기한 메시지는 동일 세션 재시작 후 처리된다. 브로커 재시작 후 재구독도 PASS.
- 실패 검증: 실제 CHECK 쓰기 실패·지연 제약 트리거 커밋 실패에서 확인 미발행/전체 롤백,
  발행 연결 중단 시 커밋 원본 유지·같은 목록 재전송으로 진단/재확인. 추가/삭제는 거절한다.
- 의존성: 명시 Paho v5 1.2.5 한 개만 추가. Testcontainers GenericContainer는 기존 전이 의존성을 사용한다.
  Paho 실제 재연결 소스를 확인해 setMaxReconnectDelay(16000)를 사용했다. 메모리 persistence이며 outbox는 없다.
- 검증: compileJava/compileTestJava 및 선택 테스트 PASS. ./scripts/verify-mqtt-contract.ps1
  실제 실행 108개 PASS·실패/건너뜀 0·종료 코드 0.
- 최종 ./scripts/verify.ps1 -Mode Full PASS, 종료 코드 0. 26개 클래스, 기존 97 + 신규 61 = 158개,
  실패/오류/건너뜀 0. XML UTC 2026-10-21T15:58:00.787Z~16:00:19.936Z
  (KST 2026-10-22 00:58:00~01:00:19). 최종 build 2분 36초.
- 최종 compileJava/processResources UP-TO-DATE, compileTestJava/test/bootJar/jar/check/build 실행.
  check에 Lint/정적 분석/아키텍처 자동 검사가 포함됐다는 의미가 아니며 해당 검사는 MISSING 유지.
- 수정 기록: Phase 1 자동 수정 3회 (import 경로·ACK 메서드명·테스트 MQTT 속성 객체),
  Phase 2 1회 (SmartLifecycle 구현), Phase 3 2회 (Transferable 경로·고정 endpoint 브로커 재시작).
  포트 변경 원인 진단 1회 추가 실행은 같은 환경 원인 2회째이며 3회 반복 전 해결했다.
  테스트 삭제·약화·검사 비활성화 없음. 커밋 실패 사례의 최초 미실행은 이전 재시작 실패의 영향이며 이후 PASS.
- 변경: 신규 Java 본체 5·테스트/지원 6, Gradle/application.yaml, 연동 안내/명세 및 기존 계약·운영 문서.
  AGENTS의 프로젝트 현황 한 줄과 README 재개 안내도 현재 상태로 갱신했다. 앞선 계약 파일 포함 총 34개.
- 자체 리뷰: 신규 Java 최대 152줄, 200줄 초과 없음. domain/application/영속성·V1~V4·운영 Compose 변경 없음.
  기존 17개 미커밋 계약 파일을 보존·연동 상태로 갱신했다. staged 0, diff/공백/변경 범위 점검 PASS.
- 위험/제한: 장치 SDK·실측·펌웨어 버퍼 삭제 NOT VERIFIED. 운영 브로커/개발 DB 실행·배포 및 원격 CI NOT RUN.
  DB와 MQTT는 비원자적이며 ACK 후 장애/세션 만료에는 장치 버퍼 재전송이 필요하다.
- Git: codex/mqtt-message-contract, 커밋·푸시·PR·병합 없음.
- 커밋 초안: feat: integrate MQTT 5 session ingestion and post-commit confirmations
- 다음: 사용자 최종 리뷰. Git 통합 또는 실제 장비 인수/START 계약은 별도 승인 범위를 따른다.
