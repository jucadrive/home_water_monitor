# 정상 보존 세션 재수신 진단 — 구현 계획

상태: VERIFIED — 2026-10-21 승인 범위 구현·로컬 검증 완료, 최종 사용자 리뷰 대기.
작성일: 2026-10-21. 기준 커밋: main의 88682a2, 계획 작성 전 작업 트리 깨끗함 확인.

## 1. 목표와 현재 사실
- 신규 정상 저장 서비스·V1/V2와 순수 측정 비교 도메인이 존재한다.
- 정상 60개 보존 후 같은 세션의 목록이 다시 도착하면 원본·통계를 유지하고
  동일 재수신과 내용 충돌을 별도로 기록하는 유스케이스를 추가한다.
- 재수신 진단 테이블·서비스·잠금·카운터는 아직 없으며 아래는 승인받을 제안이다.
- 실제 MQTT·REST·불완전 세션·새 세션 동시 생성은 이번 범위가 아니다.

## 2. 입력과 처리 범위
- 별도 ProcessNormalTdsSessionRedeliveryService를 추가하고 기존 신규 저장 진입점은 유지한다.
- 입력 데이터 구조는 SaveNormalTdsSessionCommand를 재사용한다. 이 이름 재사용은 내부 구현 선택이며
  공개 API 계약은 아니다. 새 범용 수집 서비스나 자동 신규/재수신 라우팅을 추가하지 않는다.
- 입력 목록은 60개, seq=1..60 중복 없음, 간격 1000ms이며 기본 값·문자열·수치 저장 범위는
  기존 신규 정상 경로와 같게 검사한다. 리스트 순서가 달라도 seq로 대응한다.
- 재수신 목록이 모두 isWarmup=true여도 원본은 기존에 있으므로 충돌 비교를 수행한다.
  신규 경로의 '통계 대상 필수' 검사는 신규 저장에만 유지한다.
- DB 세션은 NORMAL, 기대·보존 건수 60, 원본 seq 집합이 정확히 1..60이어야 한다.
  세션 없음·INCOMPLETE·기존 데이터 불일치는 거절하고 이 경로에서 복구하지 않는다.
- 메타데이터는 deviceId·startedAt·samplingIntervalMs·temperatureAssumedC·calibrationVersion을 확인한다.
  deviceId/교정 문자열/간격은 동일, 온도는 BigDecimal.compareTo, startedAt은 양쪽 null 허용·
  한쪽 null 거절 및 같은 순간의 DB 저장 표현으로 비교한다. ingestedAt은 비교하지 않는다.
- 메타데이터가 다르면 이번 범위의 샘플 충돌로 처리하지 않고 거절한다. 해당 입력·거절 진단의
  DB 보존과 장치 오류 응답은 후속 계약이다. 원본·성공 재수신 진단에는 변경이 없어야 한다.
- startedAt은 PostgreSQL TIMESTAMPTZ의 마이크로초 정밀도와 실제 JDBC/JPA 변환에 맞춘다.
  영속성 계층의 동일 DB 표현 비교를 사용하고 초 경계·서브마이크로초 반올림·null을 통합 검증한다.
  단순 나노초 equals나 임의 허용 시간 오차로 동일 재전송을 잘못 거절/수락하지 않는다.

## 3. 신규 테이블 — V3만 추가
V1/V2 및 기존 세션·원본 컬럼은 수정하지 않는다. 아래 이름·필드·제약을 승인 대상으로 제안한다.

### tds_session_redelivery — 성공적으로 커밋한 재수신 목록 한 건
- redelivery_id UUID PRIMARY KEY: 서버가 호출마다 새로 생성한다.
- session_id UUID NOT NULL: tds_session FK, ON DELETE 기본 제한. 장치 전송 ID가 아니다.
- received_at TIMESTAMPTZ NOT NULL: 서비스 진입 시각. MQTT 실제 도착 시각으로 주장하지 않는다.
- sample_count INTEGER NOT NULL: 이번 범위에서는 60.
- identical_count INTEGER NOT NULL, conflict_count INTEGER NOT NULL.
- CHECK: sample_count=60, 두 판정 건수 >=0, identical_count + conflict_count = sample_count.
- INDEX(session_id, received_at): 세션별 재수신 이력과 집계 조회에 사용한다.
- 결과는 conflict_count=0이면 ALL_IDENTICAL, 1 이상이면 HAS_CONFLICT로 반환하고
  같은 의미의 outcome 문자열을 중복 저장하지 않는다.

### tds_measurement_redelivery — 해당 재수신의 샘플별 비교 상세
- redelivery_id UUID NOT NULL: 위 헤더 FK. 자동 연쇄 삭제는 추가하지 않는다.
- seq INTEGER NOT NULL: CHECK 1..60. PRIMARY KEY(redelivery_id, seq).
- comparison_result VARCHAR(16) NOT NULL: CHECK IDENTICAL 또는 CONFLICT.
- original_elapsed_ms / incoming_elapsed_ms: INTEGER NOT NULL, CHECK >=0.
- original_tds_ppm / incoming_tds_ppm: NUMERIC(9,2) NOT NULL.
- original_is_warmup / incoming_is_warmup: BOOLEAN NOT NULL.
- original_raw_voltage_mv / incoming_raw_voltage_mv: NUMERIC(9,2), 각각 nullable.
- 기존·수신 네 필드를 당시 수치 스냅샷으로 저장한다. 전송 문자열의 소수 표현 자릿수까지
  보존하는 원문 로그가 아니며 payload·네트워크 메타데이터는 저장하지 않는다.
- sessionId는 헤더를 통해 얻고 중복 저장하지 않는다. seq가 해당 세션의 원본에 대응하는지와
  상세 60건·판정별 건수가 헤더에 맞는지는 서비스가 검사하고 통합 테스트로 확인한다.
  테이블 간 합계는 일반 CHECK만으로 강제된다고 주장하지 않는다.
- 기존 원본 테이블에는 INSERT/UPDATE/DELETE하지 않는다.

## 4. 건수 정의
- 고유 원본 수: 기존 tds_measurement의 60건. 재수신으로 증가하지 않는다.
- 성공 재수신 목록 수: tds_session_redelivery COUNT(*). 최초 저장·실패·거절·네트워크 전달은 제외한다.
- 동일 재수신 샘플 수: 헤더 identical_count 합계. 충돌 샘플 수: conflict_count 합계.
- 예: 동일 목록 2번 성공 처리 -> 헤더 2개, 상세 120개, 동일 120·충돌 0, 원본 60개.
- '전체 메시지 수신 수' 또는 '기기 전송 시도 수'로 이 집계를 표시하지 않는다.
- 누적 카운터 컬럼은 이번에 추가하지 않고 진단 기록의 COUNT/SUM으로 집계한다.
  per-call 결과도 헤더와 같은 판정 집계에서 만든다. 향후 성능 근거가 생기면 저장형 집계를 별도 승인한다.

## 5. 트랜잭션과 실패 처리
1. 입력 구조·기본 값 검증. 서버 redeliveryId·진입 시각 준비.
2. 서비스의 단일 Spring 트랜잭션(READ_COMMITTED)에서 잠금 대기 제한 설정.
3. sessionId로 세션 행의 PESSIMISTIC_WRITE 잠금 획득.
4. 세션 상태·메타데이터·기존 원본 60개 확인 후 seq별 도메인 compareWith 호출.
5. 진단 헤더 1개와 상세 60개를 같은 트랜잭션에서 저장하고 결과 생성.
6. Spring 트랜잭션 경계의 실제 커밋까지 성공한 호출만 정상 반환한다.

- 결과 제안: redeliveryId, sessionId, preservationResult=NORMAL, preservedSampleCount=60,
  identicalCount, conflictCount. 내부 Java 결과 record이며 MQTT/REST 계약을 확정하지 않는다.
- 충돌 자체는 정상 처리 결과다. 원본·통계·보존 결과를 바꾸지 않고 충돌 진단을 커밋한다.
- 헤더/상세 쓰기 또는 실제 커밋 실패 시 이번 진단 전부 롤백하고 호출자에게 예외를 전달한다.
- 이전에 커밋한 원본·통계·다른 재수신 진단은 그대로 유지한다.
- 진단 저장에 REQUIRES_NEW를 쓰지 않고 실패 이후 같은 트랜잭션에서 계속 저장하지 않는다.
- 호출 외부가 더 큰 트랜잭션에 참여시키면 메서드 내부 return 자체는 커밋 증거가 아니다.
  통합 테스트는 테스트 @Transactional 없이 서비스 프록시 반환 후 별도 트랜잭션으로 조회한다.
- 실제 MQTT 확인 발행은 추가하지 않는다. DB 커밋과 외부 발행의 원자성을 주장하지 않는다.

## 6. 동시성
- 같은 기존 세션의 재수신끼리는 동일 세션 행 잠금으로 직렬화한다.
- 서로 다른 세션은 같은 세션 행 잠금에 의해 서로 막히지 않음을 통합 검증한다.
- 잠금 획득 -> 원본 조회 -> 진단 저장 순서를 고정하고 메모리 synchronized를 사용하지 않는다.
- 이 규칙은 같은 세션을 변경하는 향후 경로에도 적용해야 한다. 원본 조회만 잠근다거나
  UNIQUE만으로 카운터·통계·진단의 모든 경합이 해결된다고 가정하지 않는다.
- 잠금 대기 기본 5초를 제안한다. 이 트랜잭션에만 PostgreSQL lock_timeout을 적용하고
  전역 DB 설정을 변경하지 않는다. 값은 애플리케이션 설정으로 테스트에서 낮출 수 있게 한다.
- JPA 표준 timeout 힌트만으로 강제됐다고 주장하지 않고 PostgreSQL에서 실제 대기를 검증한다.
- 제한 초과 시 예외·전체 롤백. 자동 재시도하지 않으며 송신 측 재전송 정책과 분리한다.
- 동시에 두 요청이 성공하면 각각 별도 수신 사건이다. 헤더 2개·상세 120개를 남긴다.
  이것은 원본 중복 저장이 아니며 수신 사건을 sessionId만으로 중복 제거하지 않는다.
- 신규 세션에는 잠글 기존 행이 없다. 최초 저장과 재수신 라우팅의 경합은 이 설계로
  해결됐다고 주장하지 않는다. 이미 커밋된 NORMAL 세션만 이 진입점을 지원한다.

## 7. 인수 조건과 검증 증거
| 사례 | 기대 결과 |
|---|---|
| 60개 동일 재수신 | 헤더 1·상세 60, 동일 60·충돌 0, 원본·통계 동일 |
| 목록 순서만 역전 | seq로 대응, 동일 60 |
| TDS/전압 표현 자릿수만 변경 | 동일 판정, 충돌 건수 증가 없음 |
| 네 필드 각각 변경, 전압 null 양방향 | 해당 seq만 CONFLICT, 나머지 IDENTICAL |
| 60개 모두 충돌·안정화 여부 모두 변경 | 동일 0·충돌 60, 기존 NORMAL·통계 유지 |
| 같은 목록 반복 2회 | 헤더 2·상세 120, 원본 60, 성공 재수신 집계 정확 |
| 메타데이터 각각 불일치 | 거절·이번 진단 0, 이전 데이터 보존 |
| 시각 DB 정밀도·null 경계 | 같은 DB 저장 표현 허용, 다른 순간/한쪽 null 거절 |
| 세션 없음·INCOMPLETE·기존 원본 누락 | 거절·진단 없음, 신규 저장 경로로 전환 안 함 |
| 59개·61개·중복/범위 밖 seq·유효하지 않은 값 | 거절·진단 없음 |
| 헤더 저장 실패·seq=40 상세 쓰기 실패 | 이번 헤더/상세 전부 롤백, 이전 기록 유지 |
| 상세 60개 저장 후 커밋 실패 | 예외, 이번 진단 0, 정상 반환 없음 |
| 같은 세션 동시 2개 | 첫 잠금 보유 중 두 번째 대기, 해제 후 둘 다 커밋, 집계 정확 |
| 다른 세션 동시 처리 | A 잠금 보유 중 B 처리 커밋 가능 |
| 잠금 시간 초과 | 제한된 대기 후 실패·이번 진단 없음, 잠금 해제 후 새 호출 성공 |
| DB 제약·신규 저장/비교 회귀 | V3 적용·FK/PK/CHECK 위반 거절, 기존 테스트 포함 전체 성공 |

- 실제 PostgreSQL 18 Testcontainers를 사용한다. 개발 DB/비밀번호는 필요 없다.
- 동시성 테스트는 독립 커넥션·트랜잭션과 latch/barrier 및 제한 시간 있는 Future를 사용한다.
  같은 세션의 대기는 pg_stat_activity/pg_locks 등 관측 근거와 함께 확인하고 단순 sleep 성공으로 판단하지 않는다.
- 쓰기/커밋 실패는 기존 테스트처럼 테스트 DB 전용 제약·지연 트리거로 주입하고 finally로 제거한다.
- DB 차원의 상세/헤더 합계 불변식은 추가 트리거 없이 애플리케이션 검증·테스트로 보장한다.

## 8. 예상 파일과 기존 구조 유지
Gradle 루트: backend/home-water-monitor-backend. 기존 패키지 구조를 재사용한다.

신규:
- src/main/resources/db/migration/V3__create_tds_redelivery_diagnostics.sql
- session/application/ProcessNormalTdsSessionRedeliveryService.java, TdsSessionRedeliveryResult.java
- session/application/NormalTdsSessionInputValidator.java: 기존 기본 검증을 옮겨 두 경로에서 재사용.
- session/infrastructure/persistence/TdsSessionRedeliveryEntity.java, TdsSessionRedeliveryJpaRepository.java,
  TdsRedeliveryPersistenceAdapter.java: 신규 UUID 헤더 persist·상세 저장·같은 트랜잭션 요구.
- session/infrastructure/persistence/TdsSessionLockAdapter.java: 세션 잠금과 트랜잭션 한정 timeout 설정.
- measurement/infrastructure/persistence/TdsMeasurementRedeliveryEntity.java,
  TdsMeasurementRedeliveryId.java, TdsMeasurementRedeliveryJpaRepository.java.
- session/application/NormalTdsSessionInputValidatorTest.java,
  ProcessNormalTdsSessionRedeliveryServiceIntegrationTest.java,
  TdsSessionRedeliveryConcurrencyIntegrationTest.java.

수정:
- SaveNormalTdsSessionService.java: 기본 검증 추출만 반영. 통계 대상 필수 검증과 기존 동작 유지.
- TdsSessionJpaRepository.java: 잠금 조회와 DB 시각 표현 비교에 필요한 영속성 조회.
- src/main/resources/application.yaml: 잠금 대기 설정 추가. 접속정보·전역 DB 설정 변경 없음.
- docs/tasks/NORMAL_SESSION_REDELIVERY.md, HANDOFF.md, BACKEND_DESIGN_SUMMARY.md:
  승인·구현·검증 상태·최종 DDL·미구현 경계를 기록한다.
- 측정 비교 작업 명세/HANDOFF의 이전 VERIFIED·CI 대기 기록을 PR #2 병합·CI 성공 근거로 정리한다.

위 Java 경로는 src/main/java/com/juca/homewatermonitorbackend 또는 테스트 대응 경로 기준이다.
구현 중 응집도 때문에 작은 내부 타입/테스트 지원 파일이 필요하면 같은 책임 범위 안에서 정리해 보고한다.
새 프레임워크·의존성·모듈·메시지 브로커·공개 API는 추가하지 않는다.

## 9. 순차 실행 Phase
1. V3·영속성·기본 검증 추출 및 제약/회귀 테스트.
2. 재수신 서비스·도메인 연결·진단 원자 저장·실패/실제 커밋 검증.
3. 세션별 잠금·timeout·동시성 검증, 최종 전체 build·문서·자체 리뷰.
각 Phase는 승인 범위 내 순차 진행하며 다음 Phase마다 재승인을 요구하지 않는다.
기능의 전체 검증 전에는 부분 테이블 생성이나 build만으로 VERIFIED 처리하지 않는다.

검증: JDK 25, 관련 단위/통합 테스트 -> ./scripts/verify.ps1 -Mode Full.
현재 Unit 스크립트는 domain만 선택하므로 신규 application 단위 테스트는 관련 클래스 명시 실행과
Full에서 검증한다. 새 CI 설정이나 Lint 의존성은 추가하지 않는다.
각 Phase 자동 수정 최대 5회, 같은 근본 원인 3회 반복 시 중단·보고한다.
커밋·푸시·PR·병합은 구체적인 검증 결과와 대상을 제시하고 별도 승인받는다.

## 10. 승인할 결정과 제한
- 두 진단 테이블·60개 상세 스냅샷·헤더 판정 건수 및 COUNT/SUM 집계.
- 기존 NORMAL 세션 전용 서비스, 메타데이터 불일치·범위 밖 입력 거절.
- READ_COMMITTED 트랜잭션, 세션 행 PESSIMISTIC_WRITE, 트랜잭션 한정 기본 lock_timeout 5초.
- 기존 기본 검증 추출과 V3 추가, 위 테스트·문서 범위.
- 거절/롤백된 사건은 이 진단 DB에 남지 않는다. 실패 관측·원문 로그·보관/삭제 정책은 후속 과제다.
- 상세는 재수신당 60행이므로 저장량이 증가한다. MVP에서 자동 보관 만료·삭제를 도입하지 않는다.
- 진단의 원본 스냅샷은 최초 저장값이며 물리적으로 참인 측정이라는 의미가 아니다.
- 설계 승인 전에는 소스·마이그레이션 수정·테스트 실행을 하지 않는다.

## 공식 근거
- PostgreSQL 18 행 잠금: https://www.postgresql.org/docs/18/explicit-locking.html
- PostgreSQL lock_timeout: https://www.postgresql.org/docs/18/runtime-config-client.html
- PostgreSQL 시각 정밀도: https://www.postgresql.org/docs/18/datatype-datetime.html
- Jakarta Persistence 잠금 timeout 힌트 한계:
  https://jakarta.ee/specifications/persistence/3.2/apidocs/jakarta.persistence/jakarta/persistence/EntityManager.html

## 실행 기록 — 구현 중
- Phase 1: compileJava PASS. 공통 검증 4개·V3 제약 3개·기존 정상 저장/롤백 3개, 총 10개 PASS.
- Phase 2: 정상·충돌·거절·시각 DB 정밀도 7개 및 진단 쓰기/실제 커밋 롤백 3개, 총 10개 PASS.
- Phase 2 수정 1회: 미존재 세션 판단을 @Repository 어댑터에서 서비스로 이동했다.
  어댑터의 IllegalArgumentException이 Spring 데이터 접근 예외로 변환돼 실패한 테스트를 고쳤다.
  테스트 기대값을 약화하지 않았으며 같은 원인 반복 실패는 없다.
- Phase 3: 동시성 3개 PASS. 최종 Full 전체 45개 PASS, 실패·오류·건너뜀 0.
- 구현 추가 타입: 중복 스냅샷 매핑용 TdsMeasurementSnapshot, 테스트 fixture/support,
  DB 제약 및 롤백 전용 테스트 클래스로 책임을 나눴다. 신규 프레임워크·의존성 없음.

## 최신 Phase Result — 정상 세션 재수신 진단
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
- 원격 CI: NOT RUN. Lint·정적 분석·아키텍처 자동 검사는 기존 MISSING 상태다.
- Git: codex/normal-session-redelivery, 변경 26개 파일(기존 6·신규 20), 커밋·푸시 없음.
- 커밋 초안: feat: record normal session redelivery diagnostics atomically
- 다음: 사용자 최종 리뷰와 커밋·푸시 승인. 재승인 전 추가 기능·PR·병합은 진행하지 않는다.
## Git 승인 — 2026-10-21
- 사용자가 보고된 26개 파일의 커밋·푸시 및 원격 CI 확인을 승인했다.
- 커밋 전 변경 범위·diff와 기존 Full 45개 성공 XML을 재확인했다. 소스 추가 변경은 없다.
- 브랜치: codex/normal-session-redelivery.
- 커밋 메시지: feat: record normal session redelivery diagnostics atomically.
- PR 생성·main 병합은 이번 승인 범위에 포함하지 않는다. 원격 CI 결과는 푸시 후 확인한다.
