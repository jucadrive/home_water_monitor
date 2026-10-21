# 정상 세션 신규/기존 라우팅과 최초 저장 경합

상태: DONE — 2026-10-21 사용자가 구현 결과 최종 리뷰를 통과시켰다.
2026-10-21 전체 계획 및 외부 트랜잭션 금지·PK 충돌 후 1회 복구를 사용자 승인받았다.
기준: main 2364c27, 작업 전 staged/unstaged 변경 없음. 브랜치: codex/normal-session-routing.

## 승인 범위와 설계
- 새 내부 application 진입점에서 기존 신규 저장·재수신 서비스를 재사용한다.
- 외부 조율 진입점은 NEVER로 외부 트랜잭션 참여를 거절한다.
- 별도 Spring Bean의 READ_COMMITTED 트랜잭션에서 세션 잠금 조회 후 신규/기존으로 분기한다.
- 없는 행은 잠기지 않는다. 세션 PK 경합은 기존 DB 제약으로 판정한다.
- SQLSTATE 23505와 tds_session_pkey가 함께 확인된 경우에만 첫 트랜잭션 종료 후
  새 트랜잭션으로 기존 세션 재수신 처리를 1회 수행한다. 같은 트랜잭션 재사용·무한 반복 없음.
- 그 외 UNIQUE/FK/CHECK·timeout·연결·커밋 실패는 그대로 전달한다.
- 기존 MANDATORY/persist 경로와 원본·통계 불변, 네 필드 비교·메타데이터 검증을 유지한다.
- 기존 lock_timeout 기본 5000ms를 라우팅 트랜잭션에도 적용해 INSERT 경합도 제한한다.
  각 잠금 대기의 제한이며 호출 전체 시간 상한은 아니다. timeout은 자동 복구 대상이 아니다.
- 신규 분기에서는 통계 대상이 필요하다. 모두 안정화인 목록은 기존 세션이면 비교하지만,
  미존재 조회 후 신규 검증에서 실패하면 복구하지 않는다. 경합 여부 추정을 위한 재시도 없음.
- 새 공개 API/MQTT·불완전 보존·의존성·스키마·V1/V2/V3 수정은 범위 밖이다.
- 기존 직접 저장 진입점 자체를 자동 라우팅으로 바꾸지 않는다.

## 인수 조건
| 사례 | 기대 |
|---|---|
| 신규 정상 입력 | 세션 1·원본 60·통계 정확·진단 0 |
| 기존 동일/충돌 | 원본·통계 불변, 헤더 1·상세 60, 기존 비교 정책 |
| 신규 동일 ID 동시 2개 | 최초 저장 1, 나머지는 재수신, 원본 60 |
| 동시 다른 내용 | 먼저 커밋한 원본 유지, 나머지는 충돌 진단 |
| 경합 상대 롤백 | 대기하던 유효 신규 입력 저장 가능 |
| 경합 후 메타데이터 불일치 | 거절, 상대 원본 보존, 이번 진단 0 |
| INCOMPLETE/기존 불일치/잘못된 입력 | 거절, 신규 경로 우회 없음 |
| 서로 다른 세션 | 특정 세션 잠금에 함께 막히지 않음 |
| 잠금 timeout | 제한된 대기 후 예외·롤백, 해제 후 새 호출 성공 |
| 쓰기/실제 커밋 실패 | 예외, 이번 변경 전부 롤백, 이전 기록 보존 |
| 외부 트랜잭션 | 새 진입점 호출 거절, DB 변경 없음 |
| 충돌 식별 | 다른 제약/불명 오류는 거절, 세션 PK만 복구 대상 |

## 순차 Phase와 예상 파일
1. 통합 조율·트랜잭션 실행 서비스, 처리 결과, 영속성 PK 충돌 식별기 및 정상/거절/경계 테스트.
2. 최초 저장 경합·상대 롤백·timeout·쓰기/커밋 실패 통합 테스트, 전체 build와 문서/자체 리뷰.

신규 Java: session/application/ProcessNormalTdsSessionService,
NormalTdsSessionProcessingTransactionService, NormalTdsSessionProcessingResult;
session/infrastructure/persistence/TdsSessionInsertConflictClassifier 및 대응 테스트.
기존 테스트 지원 파일·application.yaml 주석과 HANDOFF/BACKEND_DESIGN_SUMMARY를 필요한 범위에서 갱신한다.
새 Java 200줄 초과는 리뷰 대상으로 보고한다. 응집도를 위해 의미 없이 분리하지 않는다.

## 검증 및 환경
- JDK 25, PostgreSQL 18 Testcontainers. 테스트에 개발 DB/비밀번호 사용 없음.
- compileJava compileTestJava -> 관련 선택 test -> 루트 ./scripts/verify.ps1 -Mode Full.
- Unit 스크립트는 domain만 실행하므로 새 application/영속성 테스트는 명시 선택하거나 Full에서 실행한다.
- 동시성은 독립 트랜잭션·latch/barrier·제한 시간 Future와 DB 잠금 관측으로 검증한다.
- 쓰기/커밋 실패는 테스트 전용 제약/지연 트리거로 주입하고 finally에서 제거한다.
- 종료 코드, 테스트 수·실패·오류·건너뜀 및 캐시 여부를 확인한다.
- Phase당 자동 수정 최대 5회, 동일 근본 원인 3회 반복 시 중단한다.
- 이미 성공한 검증은 변경/위험 근거 없이 반복하지 않는다.
- 조사 당시 CLI Java 21, JDK 25 설치 경로 C:/dev/jdk-25.0.4.1+1.
  Docker 엔진 연결 실패. 구현 후 JDK 25 지정 및 Docker 엔진 준비가 필요하다.
- 계획 단계 테스트 NOT RUN, 신규 기능 NOT VERIFIED. Lint/정적 분석/아키텍처 자동 검사 MISSING.
- 커밋·푸시·PR·병합은 별도 승인 대상이다.

## Phase Result — Phase 1
- 상태: VERIFIED (이 Phase의 라우팅/복구 제한 검증). 전체 기능은 Phase 2 검증 전 IN_PROGRESS.
- 구현: NEVER 조율 서비스, READ_COMMITTED 실행 Bean, 신규/재수신 결과, 구조화된 PK 충돌 식별.
- 검증: JDK 25 compileJava compileTestJava PASS (모두 실제 실행).
- 선택 test: 라우팅 통합 4·복구 제한 단위 3·충돌 식별 단위 3 = 10 PASS.
  종료 코드 0, 실패 0·오류 0·건너뜀 0. test 실제 실행, 컴파일은 UP-TO-DATE.
  XML: 2026-10-21 22:46 KST. 통합 테스트는 PostgreSQL 18 Testcontainers에서 실행했다.
- 회귀: 라우팅 통합에서 기존 신규 저장/통계·재수신 동일/충돌·원본 불변·신규 통계 필수 조건을 검증했다.
- 환경: Docker Desktop 실행, JDK 25.0.4.1 사용. 샌드박스 Gradle 캐시 거부 후 승인된 명령으로 실행.
- 자동 수정: 0회. 신규 Java 파일 200줄 이하. 소스/테스트 컴파일 오류 또는 테스트 실패 없음.
- Git: codex/normal-session-routing, 소스·테스트·작업 명세 신규 및 설정 주석 수정, 커밋 없음.
- 다음: Phase 2 실제 경합·실패 검증. 기존 서비스/저장 어댑터/domain/마이그레이션 변경 없음.

## Phase 2 수정 기록
- 최초 선택 검증: 11개 중 8개 실패. MANDATORY 잠금 어댑터의 프록시에 spy stubbing을 적용해
  테스트 준비 중 트랜잭션 부재 예외가 발생했고 후속 Mockito 미완료 설정 오류로 전파됐다.
- 자동 수정 1회: AopTestUtils로 프록시 내부 spy에만 stubbing을 적용한다.
  실제 호출은 기존 Spring 프록시를 통과하며 본체 트랜잭션 규칙·테스트 기대값은 유지한다.
- 동일 원인 실행 실패: 1회. 수정 후 선택 11개 및 최종 Full에서 모두 통과했다.

## Phase Result — Phase 2 및 최종 통합 검증
- 구현: 실제 INSERT 경합 6개·쓰기/커밋 롤백 5개 통합 테스트와 테스트 전용 동기화 지원.
  spy로 미커밋 시점을 제어하고 독립 연결의 pg_stat_activity에서 INSERT 잠금 대기를 확인한다.
- 변경 파일: 신규 Java 10개 (본체 4·테스트/지원 6), 작업 명세 신규,
  application.yaml 주석·HANDOFF·설계 요약·기존 재수신 명세 상태 갱신. 총 15개.
- 선택 검증: 수정 후 동시성 6개·롤백 5개 PASS, 종료 코드 0, 실패/오류/건너뜀 모두 0.
- 최종 명령: JAVA_HOME=C:/dev/jdk-25.0.4.1+1 지정 후 ./scripts/verify.ps1 -Mode Full.
- 최종 결과: PASS, 종료 코드 0. test·bootJar·jar·resolveMainClassName 실제 실행,
  compileJava·compileTestJava·processResources는 최신 출력에 대해 UP-TO-DATE.
  컴파일은 이번 작업의 앞선 실행에서 실제 수행했다. build/check 성공은 Lint 검사 성공을 뜻하지 않는다.
- XML 증거: 2026-10-21 22:52:21~22:52:55 KST 새 실행, 14개 클래스 총 66개.
  기존 45 + 신규 21 (분기 통합 4·복구 제한 3·충돌 식별 3·동시성 6·롤백 5).
  실패 0·오류 0·건너뜀 0. Testcontainers PostgreSQL 18 사용, 개발 DB 접속 없음.
- 인수 조건: 정상 신규·동일/충돌 재수신·기존 거절·외부 트랜잭션 거절,
  최초 저장 커밋/롤백 경합·서로 다른 세션 독립·메타데이터 불일치·INSERT timeout,
  신규/기존/복구 트랜잭션의 실제 커밋 실패 및 원본/이전 진단 보존 모두 충족.
- 수정 이력: Phase 1 자동 수정 0회, Phase 2 테스트 spy 설정 1회. 테스트 약화/삭제 없음.
- 자체 리뷰: 새 Java 최대 147줄, 200줄 초과 없음. domain·기존 저장/재수신 서비스·
  MANDATORY/persist 어댑터·V1/V2/V3·의존성·DB/공개 계약 변경 없음.
- 위험/제한: NORMAL 60개 전용, 최초 커밋 승자가 원본을 결정한다. PK 복구는 1회이며
  복구 중 실패·timeout·불명 커밋 결과는 호출자에게 전달한다. 실패 사건은 진단 DB에 남지 않는다.
  기존 직접 저장 진입점은 자동 라우팅을 하지 않는다. 새 입력 어댑터는 통합 진입점을 사용해야 한다.
  설정값은 잠금별 대기 제한이며 전체 호출 시간 상한이 아니다.
  불완전 보존·MQTT 수신/커밋 후 확인 발행은 미구현이다.
- Git: codex/normal-session-routing, staged 없음, 변경은 unstaged/untracked. git diff --check PASS.
- 원격 CI: NOT RUN (이번 변경은 커밋/푸시하지 않음). Lint/정적 분석/아키텍처 자동 검사 MISSING.
- 커밋 초안: feat: route normal sessions and recover initial insert races
- 다음: 사용자 최종 리뷰. DONE 처리·커밋·푸시·PR·병합은 별도 승인 후 진행한다.

## 사용자 최종 승인 및 Git 통합 승인 — 2026-10-21
- 사용자가 구현 결과 리뷰 통과를 확인해 기능 상태를 DONE으로 전환했다.
- 이어서 커밋·origin/codex/normal-session-routing 푸시·해당 SHA의 CI 확인,
  성공 시 PR 생성·변경/검사 확인·main 병합·병합 main CI 확인까지 순차 진행을 승인했다.
- 커밋 대상은 보고한 15개 파일이며 실제 코드 변경 없이 승인/상태 문서만 갱신했다.
- 기존 Full 66개 성공 결과를 재사용한다. CI 실패 시 중단·원인 보고하며 강제 병합하지 않는다.
- 커밋 메시지: feat: route normal sessions and recover initial insert races.
- 이 기록은 Git 통합 실행 전 체크포인트이며 원격 결과는 PR/Actions의 실제 상태로 확인한다.
