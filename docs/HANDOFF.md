# Home Water Monitor 인계 — 2026-10-22

## 현재 진행 — MQTT 5.0 실제 서버 연동
- 상태 DONE, 2026-10-22 사용자 최종 리뷰 승인. [작업 명세](tasks/MQTT_INTEGRATION.md) · [실행 안내](MQTT_INTEGRATION.md).
- 사용자가 커밋·푸시·소스 CI·PR·main 병합·병합 후 CI 및 로컬 동기화를 승인했다.
  아래 Git/원격 CI 기록은 실행 전 체크포인트다. 실제 결과는 GitHub PR/Actions와 Git 로그를 확인한다.
- 2026-10-22 사용자가 전체 MQTT 구현 계획을 승인했다. Phase 1~3 순차 구현·검증 완료.
- MQTT 수신 → strict 계약 검증 → 기존 application 저장 → 실제 커밋 성공 후 보존 확인 발행을 구현했다.
- 브랜치 codex/mqtt-message-contract, 기준 main 810ed9a. 기존 미커밋 계약 변경을 보존했다.
- 기본 MQTT_ENABLED=false. 장비/운영 브로커/개발 DB는 실행하지 않았다. 테스트 브로커/DB/모의 장치만 사용했다.

## 최신 Phase Result — MQTT 서버 연동
- 본체: session.infrastructure.mqtt의 codec·수신기·설정·발행기 5개. 테스트/지원 Java 6개.
  Paho v5 1.2.5 의존성, 외부 MQTT 설정, 연동 안내·승인/검증 기록을 추가했다.
- 저장은 ProcessTdsSessionService 프록시를 호출한다. 기존 NEVER/READ_COMMITTED·PK 충돌 1회 복구를 유지했다.
  실제 커밋 성공 후만 확인한다. 실패 시 MQTT ACK하되 업무 확인은 없고 장치 원본 재전송으로 복구한다.
- 수신/발행 연결 분리·Receive Maximum=1·DirectChannel 직렬 처리. 유한 연결/발행 대기와 재연결·세션 정책은 실행 안내 참조.
- 검증: 선택 Phase 1 33개, Phase 2 구성 9개 및 브로커/DB 통합 10개 PASS.
  최종 ./scripts/verify-mqtt-contract.ps1 108개 PASS·실패/건너뜀 0·종료 코드 0.
- 최종 ./scripts/verify.ps1 -Mode Full PASS, 종료 코드 0, 기존 97 + 신규 61 = 158개,
  26개 클래스·실패/오류/건너뜀 0. XML KST 2026-10-22 00:58:00~01:00:19.
  compileJava/processResources UP-TO-DATE, compileTestJava/test/bootJar/jar/check/build 실행.
- 인수 조건: 정상/불완전/빈 목록·동일/충돌 재수신·추가/삭제 거절·실제 쓰기/커밋 실패 시 미확인/롤백,
  커밋 후 발행 실패·재전송 복구·브로커 재시작 재구독·수신 중지/재개·연속 메시지 직렬 처리 PASS.
- 자동 수정: Phase 1 3회, Phase 2 1회, Phase 3 2회. 상세/진단 재실행은 작업 명세 참조.
  Docker 컨테이너 재시작 시 무작위 포트 변경을 확인하고 같은 포트의 Mosquitto 프로세스 재시작으로 검증했다.
- 변경 총 34개 (앞선 계약 파일 포함). 신규 Java 최대 152줄, 200줄 초과 없음.
  domain/application/영속성·V1~V4·운영 Compose·CI 설정 변경 없음. Lint/정적 분석/아키텍처 자동 검사 MISSING.
- Git: staged 0, unstaged/untracked만 존재. diff/공백/범위 점검 PASS. 커밋·푸시·PR·병합/이번 원격 CI 없음.
- 제한: 실제 펌웨어 MQTT 5·버퍼 삭제·재전송은 NOT VERIFIED. 운영 배포/개발 DB 실행 NOT RUN.
  DB/MQTT 원자성·서버 outbox는 없다. 수신 세션 만료/확인 유실의 복구는 장치 버퍼 재전송에 의존한다.
- 커밋 초안: feat: integrate MQTT 5 session ingestion and post-commit confirmations
- 다음: 사용자 최종 리뷰. 승인 후 Git 통합 또는 장비 인수/START 계약을 진행한다.

## 이전 작업 — MQTT 5.0 메시지 계약
- 상태 VERIFIED, 사용자 최종 리뷰 대기. 명세: [tasks/MQTT_MESSAGE_CONTRACT.md](tasks/MQTT_MESSAGE_CONTRACT.md).
- 사용자가 최종 목록·보존 확인 우선 및 MQTT 5.0 기준 진행을 승인했다.
- 계약: [MQTT_MESSAGE_CONTRACT.md](MQTT_MESSAGE_CONTRACT.md). JSON 계약 버전 1과 프로토콜 5.0을 구분한다.
- 브랜치 codex/mqtt-message-contract, 기준 main 810ed9a. 시작 전 작업 트리 깨끗함.
- 이전 불완전 보존은 PR #5로 main 810ed9a에 병합 완료, main CI 성공:
  https://github.com/jucadrive/home_water_monitor/actions/runs/39623869675
- 이번 작업의 커밋·푸시·PR·병합은 아직 별도 승인되지 않았다.

## 이전 Phase Result — MQTT 메시지 계약
- SESSION_RESULT/SESSION_PRESERVED의 고정 토픽·필드·의미·버퍼 상관관계를 정의했다.
  MQTT 5.0·QoS 1·retain=false, Content Type application/json, Payload Format Indicator 1,
  UTF-8 JSON 최대 16,384 bytes. 보존 확인은 실제 커밋 성공 후에만 발행한다.
- 변경: 문서 4개, JSON Schema 2개, JSON 예제 9개, PowerShell 검증 2개. 총 17개 파일.
- 검증: PowerShell AST 구문 검사 PASS, ./scripts/verify-mqtt-contract.ps1 종료 코드 0.
  실제 실행 108개 PASS, 실패/건너뜀 0. 정상/불완전/빈 목록·재수신·부정/byte 경계를 확인했다.
- 자동 수정 1회: 다중 행 if 식 연산자 위치를 수정했다. 계약/기대값 약화 없음.
- Java·DB·의존성·기존 CI 변경 없음. 기존 Full 97개/병합 main CI 결과를 재사용했다.
  이번 Gradle·실제 MQTT·원격 CI NOT RUN. 계약 검증은 수동 스크립트이며 CI에 연결하지 않았다.
- 인수 조건: 오프라인 형식/의미/전달받은 전송 메타데이터 검사 충족. 실제 인증·패킷·커밋 후 발행은 미검증.
- Git: staged 없음, 문서/검증 파일만 unstaged/untracked. diff/변경 범위 점검 PASS.
- 커밋 초안: docs: define MQTT 5 session result and preservation contracts
- 다음: 사용자 최종 리뷰. 이후 연동 실패 처리 계약 계획, 그 뒤 실제 MQTT 어댑터 구현.

## 이전 작업 — 불완전 세션 보존
- 전체 계획 사용자 승인. 명세: [tasks/INCOMPLETE_SESSION_PRESERVATION.md](tasks/INCOMPLETE_SESSION_PRESERVATION.md).
- 브랜치 codex/incomplete-session-preservation, 기준 main 276d6a6, 시작 전 작업 트리 깨끗함.
- 최초 결과 확정: 같은 seq 집합의 변경은 충돌 진단, 추가/삭제는 거절하며 NORMAL로 승격하지 않는다.
- 전체 상태 DONE: 2026-10-22 사용자 최종 리뷰 통과.
- Phase 1 선택 18개, Phase 2 통합 17개 및 수정 후 단위 2개, Phase 3 선택 12개 PASS.
- 개발 DB 실행/마이그레이션 적용 없음.
- Git 통합 승인: 2026-10-22 사용자가 커밋·푸시·해당 SHA CI·PR·main 병합을 일괄 승인했다.
  병합 main CI와 로컬 동기화도 확인한다. 아래 기록은 Git 통합 실행 전 체크포인트다.
- 이전 라우팅 기능은 PR #4로 main 276d6a6에 병합 완료, main Verify 성공:
  https://github.com/jucadrive/home_water_monitor/actions/runs/39618593683
  아래 라우팅 Git 실행 전 기록은 이전 체크포인트다.

## 이전 Phase Result — 불완전 세션 보존
- 구현: ProcessTdsSessionService.process()가 정상/불완전 신규/기존을 조율한다.
  NEVER·READ_COMMITTED·세션 잠금·timeout·세션 PK 충돌 후 1회 복구를 유지했다.
- 0~59개 원본과 INCOMPLETE·누락 순번을 한 트랜잭션으로 확정한다.
  stats_sample_count=0, 네 대표 통계 NULL. 최초 원본/누락 진단/상태는 고정한다.
- 같은 seq 집합의 변경은 기존 네 필드 비교로 진단한다. 추가/삭제/순번 교체·메타데이터 변경은 거절한다.
- V4는 누락 seq 테이블과 재수신 헤더 0~60개 제약을 추가한다. V1/V2/V3는 변경하지 않았다.
- 변경: 신규 Java 19개 (본체 10·테스트/지원 9), V4·명세 신규 및 기존 7개 수정, 총 28개.
- 검증: JAVA_HOME=C:/dev/jdk-25.0.4.1+1, ./scripts/verify.ps1 -Mode Full PASS, 종료 코드 0.
  21개 클래스, 기존 66 + 신규 31 = 97개, 실패/오류/건너뜀 0. PostgreSQL 18 Testcontainers 사용.
- XML UTC 2026-10-21T15:00:26.678Z~15:01:19.293Z (KST 2026-10-22 00:00:26~00:01:19).
  test/compileJava/bootJar/jar 실제 실행, compileTestJava/processResources UP-TO-DATE.
- 인수 조건: 빈/1/59개·중간 누락·원본/누락/통계 정합성·동일/충돌·시각·잘못된 입력/추가/삭제 거절,
  정상/불완전 최초 경합 양방향·상대 롤백·기존 직렬화·독립 세션·timeout·쓰기/커밋 롤백 충족.
- 자동 수정: Phase 2 테스트 예외 stubbing 1회. 정책/기대값 약화 없음. Phase 1/3 0회.
- 자체 리뷰: 신규 Java 최대 141줄. domain·기존 MANDATORY/persist·의존성·공개 API/MQTT 변경 없음.
- Git: codex/incomplete-session-preservation, staged 없음, unstaged/untracked만 존재. diff 점검 PASS.
  커밋/푸시/PR/병합 없음, 원격 CI NOT RUN. 이번 기능의 Git 통합은 별도 승인 대상이다.
- 제한: INCOMPLETE 승격/부분 수락/참고 통계/추세 조회 API/실패 사건 별도 저장/MQTT는 범위 밖이다.
  V4는 테스트 DB에만 적용했다. 개발 DB/서버 실행 없음. 다음 서버 실행 시 V4가 적용될 수 있다.
- Lint·정적 분석·아키텍처 자동 검사 MISSING. check가 이를 검증한다고 주장하지 않는다.
- 커밋 초안: feat: preserve incomplete sessions with immutable final results
- 다음: 승인된 Git 통합·main CI 확인. 이후 MQTT 메시지 계약 → 연동 실패 처리 계획.

## 이전 작업 — 신규/기존 라우팅과 최초 저장 경합
- 기준 main: 2364c27 (PR #3 병합). 로컬 origin/main 참조도 동일했으며 작업 전 변경 없음.
  아래 과거 PR #3 병합 대기 기록은 이 체크포인트 이전 이력이다. 이번에 최신 원격 CI는 조회하지 않았다.
- 사용자 승인: 전체 라우팅 계획, 새 진입점의 외부 트랜잭션 금지, 세션 PK 충돌 후 1회 복구.
- 명세: [tasks/NORMAL_SESSION_ROUTING.md](tasks/NORMAL_SESSION_ROUTING.md).
- 브랜치: codex/normal-session-routing. 상태 DONE — 사용자 최종 리뷰 통과.
- Phase 1: NEVER 조율·READ_COMMITTED 분기·PK 충돌 식별·새 트랜잭션 복구를 구현했다.
- Phase 2: 실제 최초 저장 경합·상대 롤백·timeout·쓰기/커밋 실패와 최종 Full 검증 완료.
- 로컬 CLI 기본 Java는 21이다. 검증 프로세스 JAVA_HOME=C:/dev/jdk-25.0.4.1+1을 사용한다.
  Docker Desktop을 실행했고 Testcontainers 통합 테스트 연결을 확인했다.
- Git 통합 승인: 사용자가 커밋·푸시·해당 SHA CI 확인 후 성공 시 PR 생성·main 병합 및 main CI 확인까지 승인했다.
  아래 검증 기록은 Git 통합 실행 전 체크포인트다. 원격 결과는 PR/Actions의 실제 상태를 확인한다.

## 이전 Phase Result — 신규/기존 라우팅과 최초 저장 경합
- 구현: ProcessNormalTdsSessionService가 입력 검증 후 별도 READ_COMMITTED Bean으로 분기한다.
  세션 PK 충돌만 첫 트랜잭션 종료 후 새 트랜잭션에서 기존 세션 처리로 1회 전환한다.
  새 진입점은 NEVER이며 외부 트랜잭션에서 호출하면 거절한다. 기존 직접 서비스 계약은 유지했다.
- 변경: 본체 Java 4·테스트/지원 Java 6·새 작업 명세, 설정 주석과 관련 문서. 총 15개 파일.
- 검증: compileJava compileTestJava PASS, Phase 1 선택 10개 PASS,
  Phase 2 수정 후 선택 11개 PASS, 최종 ./scripts/verify.ps1 -Mode Full PASS (종료 코드 0).
- 최종 XML: 2026-10-21 22:52:21~22:52:55 KST, 기존 45 + 신규 21 = 66개,
  실패 0·오류 0·건너뜀 0. test/bootJar/jar는 실제 실행, 최종 컴파일은 UP-TO-DATE.
- 인수 조건: 정상/거절 분기·복구 제한·최초 저장 경합·상대 롤백·다른 세션 독립·
  메타데이터 거절·INSERT timeout·신규/기존/복구 커밋 실패·이전 데이터 보존 충족.
- 수정: Phase 2 테스트 spy stubbing이 MANDATORY 프록시를 호출한 문제 1회 수정.
  실제 트랜잭션 규칙/테스트 기대값 변경 없음. 신규 Java 모두 200줄 이하 (최대 147줄).
- 제한: NORMAL 60개 전용. 실패 사건 보존·불완전 세션·MQTT 수신/확인 발행은 미구현.
  잠금별 timeout이며 전체 호출 상한이 아니다. 불명 커밋 실패를 성공으로 간주하거나 재시도하지 않는다.
- Git: codex/normal-session-routing, staged 없음, unstaged/untracked만 존재. diff 점검 PASS.
- 원격 CI NOT RUN. Lint·정적 분석·아키텍처 자동 검사는 MISSING.
- 커밋 초안: feat: route normal sessions and recover initial insert races
- 다음: 승인된 커밋·푸시·CI·PR·main 병합·main CI를 순차 실행한다. 사용자의 최종 리뷰는 통과했다.

## 운영 방식과 현재 작업
- 최상위 프로젝트 실행 지침: [루트 AGENTS.md](../AGENTS.md), [Harness.md](Harness.md).
- 사용자: Architect & Verifier. AI: 하위 에이전트 없는 단일 자율 구현·검증 담당.
- 최초 계획 승인 후 승인 범위의 Phase를 순차 진행한다. 최종 사용자 승인 전 DONE으로 표시하지 않는다.
- 기존 페어 프로그래밍·사용자 직접 작성 지침은 종료했다. 기존 업무 합의는 유지한다.
- 하네스 최소 구축 계획 1~5항 및 관련 문서 전환은 사용자가 승인했다.
- 구축 명세: [tasks/HARNESS_BOOTSTRAP.md](tasks/HARNESS_BOOTSTRAP.md).
- 하네스 상태: DONE. PR #1을 main의 abe4d3c로 병합했고 병합 후 원격 CI도 성공했다.
- 측정 비교 도메인: 사용자 승인으로 PR #2를 main 88682a2에 병합했고 원격 CI도 성공했다.
- 정상 세션 재수신 진단: 구현·검증 결과를 사용자 승인받았고 커밋 5336e3e를 푸시했다. 원격 CI 성공.
- PR #5 불완전 보존 병합/main CI 성공 완료. 현재 MQTT 5.0 실제 서버 연동 구현·검증 결과의 사용자 리뷰 대기다.

## 데스크톱에서 이어가기 — 2026-10-22

- 현재 구현: 정상/불완전 저장·측정 비교·재수신 진단·세션 잠금·통합 라우팅·최초 저장 경합 처리.
- 최신 로컬 Full 158개·계약 검사 108개 성공. 이번 브랜치 원격 CI는 NOT RUN이다.
- 이전 라우팅 PR #4: https://github.com/jucadrive/home_water_monitor/pull/4 (main 276d6a6 병합/main CI 성공).
- 불완전 보존은 main 810ed9a에 병합됐다. 이번 계약 변경은 codex/mqtt-message-contract에 미커밋 상태다.
  임의 switch/reset/stash하지 않는다.
- 실제 MQTT 수신/확인 발행을 구현했고 테스트 브로커·DB로 검증했다. 펌웨어/실제 장비 검증은 별도다.

데스크톱 저장소 루트에서:
```powershell
# 로컬 변경이 있으면 먼저 차이를 확인하고 보존한다. 임의 reset/clean/stash를 하지 않는다.
git status
# 작업 트리가 깨끗한 경우에만 main으로 전환하고 병합된 최신 내용을 받는다.
git switch main
git pull --ff-only origin main
```

1. 루트 AGENTS.md -> 이 HANDOFF -> docs/tasks/MQTT_INTEGRATION.md 및 계약/기존 명세를 소스와 대조한다.
2. IntelliJ Project SDK와 Gradle JVM을 JDK 25로 맞춘다. CLI도 JAVA_HOME/PATH를 JDK 25로 설정한다.
3. Docker Desktop을 실행한다. 테스트는 Testcontainers를 사용하므로 개발 DB/.env 설정이 필요 없다.
4. PC 변경 후 재현 검증이 필요하면 저장소 루트에서 ./scripts/verify.ps1 -Mode Full을 실행한다.
5. V3/V4는 개발 DB에 아직 적용하지 않았다. 서버를 시작하면 Flyway가 적용하므로 정상 접속 DB를 확인한다.
   기존 V1/V2와 데이터 볼륨을 수정·삭제하지 않는다. 실제 .env·DB 데이터·IDE 개인 설정은 Git으로 공유되지 않는다.
6. 정상/불완전 통합 진입점은 ProcessTdsSessionService.process()다. 외부 트랜잭션 없이 호출한다.
   기존 정상 전용 진입점/직접 서비스는 유지했다. MQTT 입력 어댑터도 이 진입점을 사용한다.
7. 새 요구사항에서는 실행 계획과 최초 승인을 먼저 받고, 커밋·푸시·PR·병합도 합의한 승인을 따른다.

재개 문구:
> AGENTS.md와 HANDOFF.md, tasks/MQTT_INTEGRATION.md 및 현재 작업 브랜치 파일을 확인해 줘.
> MQTT 5.0 실제 수신·저장·커밋 후 확인을 구현했고 Full 158개·계약 검사 108개를 통과했어.
> 미커밋 변경을 보존하고 결과를 리뷰해 줘. 커밋·푸시·PR·병합은 별도 승인 전 진행하지 마.
## 이전 Phase Result — 정상 세션 재수신 진단
- 상태: VERIFIED. 승인된 Phase 1~3 구현·로컬 검증 완료, 사용자 최종 리뷰 대기.
- 구현: V3 진단 헤더/60개 상세, 원본 스냅샷, seq별 비교, 메타데이터 거절, 공통 입력 검증,
  READ_COMMITTED 서비스 트랜잭션과 MANDATORY persist, 세션별 비관적 잠금·대기 제한.
- 변경 파일: 신규 Java 11개(애플리케이션/영속성), 신규 테스트·지원 Java 7개, V3, 계획 문서.
  기존 신규 저장 서비스·세션 Repository·application.yaml 및 관련 문서 3개를 수정했다.
- 실행: JDK 25, Gradle Wrapper, PostgreSQL 18 Testcontainers. 실제 개발 DB·V1/V2 변경 없음.
- 검증 명령: compileJava, Phase 1 선택 test(10개), Phase 2 선택 test(수정 후 10개),
  Phase 3 동시성 선택 test(3개), ./scripts/verify.ps1 -Mode Full.
- 최종 결과: Full PASS, 종료 코드 0, compileJava·compileTestJava·test·bootJar·jar 실제 실행.
  XML 시각 2026-10-21 17:47 KST, 전체 45개·실패 0·오류 0·건너뜀 0.
- 최종 테스트 구성: 기존 24개 + 입력 검증 4개 + 재수신 서비스 7개 + 진단 롤백 3개
  + 스키마/신규 경로 회귀 4개 + 동시성 3개 = 45개.
- 인수 조건: 동일·순서 역전·자릿수·필드별 충돌·전압 null 양방향·전부 충돌·집계·거절·
  DB 시각 반올림·실제 쓰기/커밋 롤백·같은 세션 대기·다른 세션 독립·timeout 후 복구 모두 충족.
- 수정 이력: Phase 2 자동 수정 1회(미존재 세션 업무 판단을 Repository 밖 서비스로 이동).
  테스트 삭제·약화·검사 비활성화 없음. Phase 1/3 수정 재시도 0회.
- 자체 리뷰: 새 Java 파일 모두 200줄 이하, 순수 domain은 수정하지 않음.
  공통 검증은 기존 기본 검증을 보존하며 신규의 통계 대상 필수 조건도 회귀 검증했다.
  V1/V2·빌드 의존성·공개 API·MQTT·원본 저장 컬럼 변경 없음. diff/문서 점검 통과.
- 제한: 기존 NORMAL 60개 세션 전용. 신규/기존 자동 라우팅·최초 저장 경합·불완전 보존·
  거절/실패 사건 영속화·MQTT 확인 발행·보관/삭제 정책은 미구현이다.
- V3는 테스트 DB에만 적용했다. 개발 DB는 서버 실행 시 Flyway로 적용할 수 있으며 이번에는 실행하지 않았다.
- 원격 CI: 소스 커밋 5336e3e Verify 성공(실행 39572555646). 이후 문서 갱신·PR 병합 검사는 PR #3 참고.
  Lint·정적 분석·아키텍처 자동 검사는 기존 MISSING 상태다.
- Git: codex/normal-session-redelivery, 변경 26개 파일(기존 6·신규 20), 커밋·푸시 없음.
- 커밋 초안: feat: record normal session redelivery diagnostics atomically
- 다음: 사용자 최종 리뷰와 커밋·푸시 승인. 재승인 전 추가 기능·PR·병합은 진행하지 않는다.

## 이전 Phase Result — 측정 비교 도메인
- 상태: VERIFIED — 2026-10-21 최초 계획 승인 범위 구현·검증 완료, 최종 사용자 리뷰 대기.
- 구현 내용: 같은 sessionId + seq에서 네 필드의 동일·충돌 비교, 기본 입력·비교 대상 검증.
- 변경 파일: TdsMeasurement.java, TdsMeasurementComparisonResult.java, TdsMeasurementTest.java,
  작업 명세·HANDOFF·도메인 안내·하네스/Git 체크포인트 문서. 서비스·DB·의존성 변경 없음.
- 검증 명령: ./scripts/verify.ps1 -Mode Unit — PASS, 종료 코드 0, 비교 13개 + 통계 7개.
- 검증 명령: ./scripts/verify.ps1 -Mode Full — PASS, 종료 코드 0, 전체 build 성공.
- XML 증거: 2026-10-21 16:56 KST 새 실행. 비교 13개·통계 7개·컨텍스트 1개·저장 통합 3개,
  전체 24개, 실패 0·오류 0·건너뜀 0. JDK 25 사용. Unit에서 compileJava·compileTestJava 실행,
  Full에서 test·bootJar·jar 실행. 기존 쓰기/커밋 오류 롤백 테스트도 통과했다.
- 인수 조건: 동일·필드별 충돌·자릿수·전압 null 양방향·다른 키·필수값·순번·경과시간·경계·
  반올림 금지·원본 수치 보존 및 전체 회귀 검증 충족.
- 자체 리뷰: 도메인 import는 java.math/java.util뿐, 신규 Java 파일은 각각 50/7/133줄.
  git diff --check 및 변경 범위 검토 통과. 자동 수정 재시도 0회.
- 남은 위험: record의 equals/hashCode는 업무 비교가 아니다. 서비스 연결·재수신 저장·진단·동시성은 미구현.
  이번 브랜치의 원격 CI는 NOT RUN이며 Lint·정적 분석·아키텍처 자동 검사는 기존 MISSING 상태다.
- Git 상태: codex/measurement-comparison, 소스 3개 신규·계획 1개 신규·문서 4개 수정.
  커밋·푸시·PR·병합은 하지 않았다.
- 커밋 초안: feat: add measurement content comparison domain
- 다음 Phase: 사용자 최종 리뷰 및 구체적인 변경 범위의 커밋·푸시 승인 대기.
## 하네스 구축 당시 검증 기록
### Phase Result — 하네스 구축
- 구현 내용: 단일 에이전트 운영 문서, 컨텍스트 테스트 DB 격리, Unit/Full 검증 스크립트, CI 구성.
- 변경 파일: 루트 AGENTS.md·README.md, docs 운영·설계 안내·구축 명세,
  HomeWaterMonitorBackendApplicationTests.java, scripts/verify.ps1, .github/workflows/verify.yml.
- 실행 환경: JAVA_HOME을 설치된 C:/dev/jdk-25.0.4+7로 지정한 검증 프로세스.
  build.gradle은 원래 Java 25 toolchain이므로 변경하지 않았다. IDE 개인 설정도 수정하지 않았다.
- 검증 명령: ./scripts/verify.ps1 -Mode Unit — PASS, 종료 코드 0, 도메인 테스트 7개.
- 검증 명령: ./scripts/verify.ps1 -Mode Full — PASS, 종료 코드 0, build 성공.
- 전체 테스트 XML: 2026-10-21 15:49~15:50 KST 새 실행. 단위 7개·컨텍스트 1개·저장 통합 3개,
  총 11개, 실패 0·오류 0·건너뜀 0. compileTestJava와 test는 이번 검증에서 실행됐고
  compileJava는 기존 출력에 대해 UP-TO-DATE였다. Full에서 bootJar·jar도 실행됐다.
- 스크립트 PowerShell 구문 검사·git diff --check: PASS.
- 실패 처리 확인: 최초 샌드박스 실행에서 Gradle 캐시 잠금 접근 거부로 스크립트 종료 코드 1 확인.
  명령 승인을 받은 후 같은 Unit 검증 성공. 검사 비활성화·테스트 약화는 하지 않았다.
- Docker 조회는 일반 샌드박스에서 연결 불가/접근 거부였으나 승인된 Full 프로세스는
  Testcontainers PostgreSQL에서 컨텍스트·저장 통합 테스트를 실제 통과했다.
- 인수 조건: 로컬 구축 조건 충족. AGENTS.md 98줄. 개발 DB 설정 없이 테스트 전용 DB 사용.
- 당시 미확인: Linux·원격 CI. 이후 PR #1과 main CI에서 동일 스크립트 Full 실행 성공으로 확인했다.
  CI가 작성됐다고 브랜치 보호·필수 검사 또는 원격 성공이 설정됐다고 주장하지 않는다.
  Lint·정적 분석·아키텍처 자동 검사 MISSING은 승인된 후속 과제다.
- Git 상태: main, 기존 사용자 docs/Harness.md는 AM 그대로 유지. 나머지 변경은 unstaged/untracked.
  애플리케이션 기능·의존성·V1/V2·DB 계약 변경, 커밋·푸시는 없음.
- 커밋 초안: chore: establish single-agent harness and isolated verification
- 다음 Phase: 사용자 최종 리뷰 DONE. IntelliJ 백엔드 프로젝트 JDK 25 설정 완료를 사용자 확인했다.
  하네스 브랜치에서 승인된 커밋을 수행하고, 푸시 승인 후 원격 CI 결과를 확인한다.

## 구현 이력 읽기
아래 2026-10-20 구현·설계 이력은 현재 소스와 대조한다. 과거 사용자 통과 보고를 이번 재실행으로 해석하지 않는다.
## 현재 환경과 구조

- Git / Codex 프로젝트 루트: `home-water-monitor`
- 실제 Gradle / IntelliJ 프로젝트 루트: `backend/home-water-monitor-backend`
- 위의 한 단계 중첩은 사용자와 확인한 구조이며 임의로 평탄화하지 않는다.
- Java 25, Spring Boot 4.1.1, Gradle Wrapper 9.8.1, Groovy DSL
- 기본 패키지: `com.juca.homewatermonitorbackend`
- PostgreSQL 18, 호스트 접속 포트 15432, 컨테이너 포트 5432
- Compose 프로젝트명: `home-water-monitor`, 서비스명: `postgres`
- DB 생성·변경은 Flyway, Hibernate 설정은 `ddl-auto: validate`, `open-in-view: false`

## 구현·검증 상태

| 항목 | 상태와 근거 |
|---|---|
| `TdsStatisticsCalculator` | 평균·최소·최대·중앙값 구현. 평균과 짝수 중앙값은 소수 둘째 자리 HALF_UP |
| 통계 단위 테스트 | 7개. 이전 사용자 통과 보고와 당시 XML 확인 기록. 이번 인계에서는 재실행하지 않음 |
| PostgreSQL / 서버 접속 | 사용자 확인: Docker Desktop 정상 실행, PostgreSQL 컨테이너 생성, 로컬 환경변수 설정, Spring Boot 정상 실행 |
| V1 | `tds_session` 생성. 사용자 적용·테이블 조회 확인 |
| V2 | `tds_measurement` 생성. 사용자 적용·테이블 조회 확인 |
| 엔티티 | `TdsSessionEntity` 전체 컬럼, `TdsMeasurementEntity` 저장 생성자·Lombok 조회 메서드 작성. 사용자 서버 정상 실행 보고 |
| Repository | `TdsSessionJpaRepository`, `TdsMeasurementJpaRepository` 작성. 원본 세션별 정렬 조회·건수 조회 제공. 사용자 Repository 2개 등록 확인 |
| 저장 어댑터 | `TdsSessionPersistenceAdapter.insert()`에서 `EntityManager.persist()` 사용. `MANDATORY`로 바깥 서비스 트랜잭션 요구 |
| 입력 객체·서비스 | `SaveNormalTdsSessionCommand`, `SaveNormalTdsSessionService` 작성. 신규 정상 목록 검증·안정화 제외 통계·세션과 원본 원자적 저장 |
| 저장 통합 테스트 | `SaveNormalTdsSessionServiceIntegrationTest`에 3개 작성. 사용자가 각 테스트 통과 보고. Testcontainers PostgreSQL 18 사용, 테스트에 `@Transactional` 없음 |
| 최신 실행 보고서 | 재수신 진단 추가 후 전체 45개 성공. 상단 최신 Phase Result 참고 |
| 원본 비교 도메인 | PR #2 병합 완료. 이번 재수신 서비스가 순수 비교 도메인을 사용 |
| 정상 세션 재수신 | V3·헤더/상세 스냅샷·서비스·잠금 구현. 최종 검증 결과는 상단 최신 기록 참고 |
| Git | 모노레포 루트 `.git` 한 개, `main`이 `origin/main` 추적. 원격은 `https://github.com/jucadrive/home_water_monitor`. 커밋·푸시 결과는 실제 Git 상태로 확인 |

2026-10-20 기록은 이전 문서·Git 공유 작업 범위다. 2026-10-21 하네스 작업에서는 승인된 테스트 격리·검증을 수행했다.

## 첫 저장 기능의 실제 범위와 검증

- `SaveNormalTdsSessionService.save()`는 **신규 정상 세션 전용**이다. 기대 원본 60개·측정 간격 1000ms, `seq=1..60`의 범위·목록 내부 중복, 필수 문자열, 경과시간, DB 수치 저장 범위 등을 검증한다.
- 안정화 원본도 모두 저장한다. `isWarmup=false`인 값만 최소·최대·평균·중앙값 계산에 사용하며, 현재 경로는 통계 대상이 없는 입력을 거절한다.
- 가상 원본의 첫 10개는 안정화 300.0ppm, 나머지 50개는 151.3ppm. 세션 1건·원본 60개·통계 대상 50개, 네 통계 모두 151.30ppm을 커밋 후 재조회한다.
- 저장 중 오류 테스트: 테스트 DB에 특정 세션의 `seq=40`을 거절하는 임시 CHECK를 추가한다. 실패 세션·원본은 전부 롤백되고 이전에 커밋한 다른 세션은 유지되는지 확인한다.
- 커밋 오류 테스트: 테스트 DB에 지연 제약 트리거·함수를 추가한다. 원본 60개가 보이는 커밋 시점에 SQLSTATE `23514` 오류를 발생시키고, 호출자가 예외를 받으며 세션·원본 전체가 롤백되는지 확인한다. 임시 DB 객체는 `finally`에서 제거한다.
- 개발 DB와 테스트 DB는 별개다. Testcontainers 2.0.5의 `org.testcontainers.postgresql.PostgreSQLContainer` 및 `@DynamicPropertySource`로 테스트 전용 접속 정보를 등록한다. Docker Desktop은 필요하지만 이 테스트에 개발 DB 환경변수는 필요하지 않다.
- 수동 UUID를 가진 세션의 기본 `JpaRepository.save()`는 `merge()`를 선택할 수 있어 신규 저장에서는 `persist()` 경로를 사용했다. 기존 세션 ID의 재전송은 아직 제약 위반 오류 경로이며, 이를 최종 멱등성 구현으로 간주하지 않는다.
- 정상 전용 경로는 59개를 계속 거절한다. 새 통합 경로는 유효 0~59개 목록을 INCOMPLETE로 보존하고 대표 통계를 계산하지 않는다.
- 정상 세션 진단·재수신·통합 라우팅·최초 저장 경합은 위 최신 구현을 따른다. 실제 MQTT 확인 발행은 없다.
  커밋 명시 거절 테스트가 모든 네트워크 장애와 커밋 결과 불명 상황까지 검증한 것은 아니다.

## 현재 DB 모델

- V3는 재수신 헤더·상세, V4는 누락 seq 진단과 재수신 목록 0~60개 제약을 추가한다.
  실제 DDL은 V1~V4를 함께 참조한다. 세션/원본 컬럼은 유지했다.

- 실제 DDL은 `src/main/resources/db/migration/V1__create_tds_session.sql`과 `V2__create_tds_measurement.sql`이다. 오래된 명세의 참고 DDL보다 실제 마이그레이션을 기준으로 확인한다.
- 세션: 식별·장치·시각, 기대/보존/통계 대상 건수, `NORMAL`/`INCOMPLETE` 보존 결과, 가정 온도·교정 버전, 최소/최대/평균/중앙값.
- 원본: 내부 자동 생성 ID, 세션 UUID, seq, 경과시간, TDS, 안정화 여부, 선택 전압.
- 원본의 업무 식별은 `(session_id, seq)` UNIQUE다. 측정 시각·PPM은 UNIQUE에 넣지 않는다. 원본은 세션을 FK로 참조한다.
- 현재 서비스는 NUMERIC의 소수 둘째 자리·정수부 범위를 검증하며 전압은 선택값이다. 센서의 물리적 유효 범위, 실제 입력 계약, 불완전 결과의 세부 검증은 계속 검토한다.
- 적용한 V1/V2는 정렬·공백 변경도 체크섬 불일치를 일으킬 수 있어 수정·자동 포맷하지 않는다. 사용자가 정렬 변경을 되돌린 뒤 정상 실행을 확인했다. 다음 스키마 변경은 새 마이그레이션으로 진행한다.

## 원본 내용 비교 — 최신 사용자 선택

- 업무 식별은 `sessionId + seq`. 키가 같은 두 원본의 `tdsPpm`·`elapsedMs`·`isWarmup`·`rawVoltageMv`를 **모두 비교**하기로 선택했다. PPM 외 비교 필드를 다시 미정으로 돌리거나 재질문하지 않는다.
- 수치는 `BigDecimal.compareTo()`로 비교하여 `151.3`과 `151.30`을 동일하게 본다. 이 비교에서 반올림하거나 허용 오차를 추가하지 않는다.
- 전압은 양쪽 모두 `null`이면 동일, 한쪽만 `null`이면 충돌이다.
- 안내한 도메인 설계: 결과 enum `IDENTICAL`/`CONFLICT`, 측정 record의 `compareWith()`. 다른 세션·seq 비교는 `IllegalArgumentException`. 신규 여부는 서비스 조회 결과로 판정한다.
- record·enum과 비교 단위 테스트 13개를 구현했다. [도메인 안내](NEXT_MEASUREMENT_COMPARISON.md)는 실제 소스를 참조하며 검증 증거는 상단에 기록한다.

## 아직 구현하지 않은 것

- 거절·롤백 사건 별도 보존, 원문 로그·운영 보관 정책
- 실제 장비 펌웨어 인수 검증, START/장치 명령·상태 보고·운영 모니터링/배포
- 센서 연결·배선·실측·펌웨어

통계 단위 테스트 통과나 테이블 생성 성공을 전체 수집 기능 완료로 표현하지 않는다.

## 새 PC의 설정

1. [루트 README](../README.md)에 따라 Git·JDK 25·Docker Desktop을 준비하고 저장소를 받는다.
2. `infra/.env.example`을 `infra/.env`로 복사한 뒤 DB 이름·사용자·비밀번호를 로컬에서 채운다. 실제 값은 문서·커밋·대화 출력에 넣지 않는다.
3. 루트에서 `docker compose --env-file infra/.env -f infra/compose.yml up -d`로 실행한다. 기존 DB 볼륨의 비밀번호는 `.env` 수정만으로 바뀌지 않는다. 볼륨을 임의로 삭제하지 않는다.
4. IntelliJ 서버 실행 설정에 같은 `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`를 등록한다. Compose의 `.env`가 별도 Java 프로세스에 자동 전달되는 것은 아니다.
5. IDE 개인 설정과 `.env`는 Git에서 제외한다. 빈 `.env.example`, Gradle Wrapper, 소스·테스트·마이그레이션·문서는 공유한다.
6. Docker의 실제 DB 데이터·볼륨과 Codex 대화 내역은 이 Git 저장소에 포함되지 않는다. 새 DB의 구조는 서버 시작 시 Flyway가 생성한다.

통합 테스트만 실행할 때는 Gradle 프로젝트 폴더에서 다음 명령을 사용한다.

```powershell
# Docker Desktop을 실행한 상태에서 통합 테스트 클래스만 실행
./gradlew.bat test --tests "com.juca.homewatermonitorbackend.session.application.SaveNormalTdsSessionServiceIntegrationTest"
```

전체 test의 contextLoads도 이번 하네스에서 Testcontainers 연결을 적용했다. 개발 DB 환경변수는 필요하지 않으며 Docker 엔진이 필요하다. 전체 실행 결과는 상단 최신 검증 기록을 따른다.

## 다음 작업

1. MQTT 5.0 실제 연동·Full 158개/계약 108개 검증 결과를 사용자 최종 리뷰한다. Git 작업은 별도 승인 후 진행한다.
2. 실제 장비 인수 또는 START/명령/상태 보고 계약의 다음 범위를 선택해 별도 계획을 수립한다.
3. 운영 브로커/TLS/ACL·배포·실패 사건 영속화/관측 고도화는 해당 승인 범위를 따른다.

## Git 체크포인트 — 2026-10-21
- 하네스 사용자 최종 승인: DONE.
- 승인된 후속 범위: docs/GIT_WORKFLOW.md 추가, 승인 경계·브랜치 정책 안내, 하네스 변경 커밋.
- 브랜치: codex/harness-bootstrap. main으로의 직접 통합은 하지 않는다.
- 커밋 메시지: chore: establish single-agent harness and verification workflow.
- 이번 추가 변경은 문서뿐이며 기존 전체 11개 테스트 성공 결과를 재사용한다.
- 하네스 후속 승인과 푸시·PR #1·리뷰·main 병합 완료. 원격 main CI 성공: https://github.com/jucadrive/home_water_monitor/actions/runs/39563945349
## Git 체크포인트 승인 — 2026-10-21
- 사용자가 의도한 동작 확인을 조건으로 이번 변경의 커밋·푸시를 승인했다.
- 커밋 전 코드 리뷰에서 계획된 비교 정책과의 불일치를 발견하지 못했다.
- 기존 Full 24개 통과 기록과 이후 비교 테스트 13개 성공 XML을 확인했다.
  최신 XML은 비교 클래스 단독 실행 기록이며 전체 스위트 재실행으로 해석하지 않는다.
- 승인 범위: 소스 3개와 문서 5개, codex/measurement-comparison 브랜치 커밋·푸시.
- 커밋 메시지: feat: add measurement content comparison domain.
- 푸시 후 해당 SHA의 원격 CI를 확인한다. PR 생성·main 병합은 이번 승인 범위가 아니다.
## Git 승인 — 2026-10-21
- 사용자가 보고된 26개 파일의 커밋·푸시 및 원격 CI 확인을 승인했다.
- 커밋 전 변경 범위·diff와 기존 Full 45개 성공 XML을 재확인했다. 소스 추가 변경은 없다.
- 브랜치: codex/normal-session-redelivery.
- 커밋 메시지: feat: record normal session redelivery diagnostics atomically.
- PR 생성·main 병합은 이번 승인 범위에 포함하지 않는다. 원격 CI 결과는 푸시 후 확인한다.
