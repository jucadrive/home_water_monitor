# 불완전 세션 보존

상태: DONE — 2026-10-22 사용자 최종 리뷰를 통과했다.
2026-10-21 전체 계획을 사용자 승인받았다.
기준 main 276d6a6, 작업 전 변경 없음. 브랜치 codex/incomplete-session-preservation.

## 승인 범위
- 최초 유효 최종 목록 0~59개를 원본·INCOMPLETE·누락 순번 진단과 원자 보존한다.
- 빈 입력도 입력된 세션만 보존한다. 미도착 세션 생성/값 보간/다른 측정으로 채우기 없음.
- 통계 대상 건수 0, 최소/최대/평균/중앙값 NULL. 참고 통계는 계산하지 않는다.
- 기존 신규 60개 경로와 통계 대상 필수 조건을 유지한다. 안정화 원본은 모두 보존한다.
- 최초 결과 확정: 같은 seq 집합은 네 필드 동일/충돌을 기록하지만 추가·삭제는 거절한다.
- INCOMPLETE를 NORMAL로 승격하지 않는다. 메타데이터 변경/DB 불일치/잘못된 입력은 전체 거절한다.
- 기존 내부 SaveNormalTdsSessionCommand를 재사용하며 MQTT/공개 API 계약은 변경하지 않는다.
- 새 진입점 NEVER·READ_COMMITTED·세션 행 잠금·기존 timeout·PK 충돌 후 1회 복구를 유지한다.
- 정상/불완전 경합에서는 먼저 커밋한 결과에 따라 후속 목록을 검증한다.
- V4에 tds_session_missing_sample (session_id, seq) PK/FK/순번 CHECK를 추가한다.
- 재수신 헤더 제약은 0~60개 및 동일+충돌=목록 건수로 확장한다. V1/V2/V3 수정 없음.
- 기존 MANDATORY/persist 경로·domain·의존성 유지. DB 간 집합 정합성은 서비스/통합 테스트로 검증한다.

## 순차 Phase
1. 공통 검증·V4·최초 불완전 저장 및 원본/누락 진단 원자성 검증.
2. 불완전 재수신·통합 라우팅·확정 결과 유지와 관련 회귀 검증.
3. 실제 경합·상대 롤백·정상/불완전 경합·timeout·쓰기/커밋 실패, 전체 build·자체 리뷰.

## 인수 조건
- 0/1/59개·중간 누락·안정화 보존·통계 미계산·정확한 원본/누락 집합.
- 잘못된 값·중복/범위 밖 seq·61개·잘못된 메타데이터 전체 거절.
- 동일/순서/표현 자릿수·네 필드 충돌·전압 null·빈 재수신 헤더 1/상세 0.
- 추가/삭제·메타데이터 변경 거절, 원본·누락·상태·과거 진단 불변.
- DB 원본/누락/건수 불일치 거절. 같은 ID 경합·다른 세션 독립·상대 롤백.
- 잠금 제한·원본/누락/재수신 쓰기 및 실제 커밋 실패의 전체 롤백·이전 데이터 보존.
- 기존 정상 전용 진입점 및 전체 회귀 성공.

## 검증/보고
- JDK 25, PostgreSQL 18 Testcontainers. 개발 DB/실제 비밀번호 사용 없음.
- 관련 컴파일·선택 test -> ./scripts/verify.ps1 -Mode Full. 종료 코드·개수·XML 시각·캐시 여부 기록.
- Phase당 자동 수정 최대 5회, 동일 근본 원인 3회 반복 시 중단. 검사 약화/삭제 없음.
- 새 Java 200줄 초과 보고. Lint/정적 분석/아키텍처 자동 검사 MISSING.
- 시작 전 신규 테스트 NOT RUN, 인수 조건 NOT VERIFIED. Git 커밋·푸시·PR·병합은 별도 승인 대상.

## Phase Result — Phase 1
- 상태: VERIFIED (최초 저장/스키마 범위). 전체 기능은 IN_PROGRESS.
- V4·누락 영속성·공통 검증 및 정상 60개 wrapper·불완전 최초 저장을 구현했다.
- 선택 test 18개 PASS (불완전 저장 5·공통 2·기존 정상 검증 4·스키마 4·정상 저장 3).
- 종료 코드 0, 실패/오류/건너뜀 0. compileJava/compileTestJava/processResources/test 실제 실행.
- PostgreSQL 18 Testcontainers, JDK 25.0.4.1. XML 2026-10-21 23:52 KST.
- 원본/누락 쓰기 실패·59개 원본+1개 진단 후 실제 커밋 실패의 전체 롤백과 이전 데이터 보존 확인.
- 기존 정상 저장 및 통계 필수 조건 회귀 통과. 자동 수정 0회. 다음 Phase 2로 진행한다.

## Phase 2 수정 기록
- 최초 선택 검증 19개 중 단위 테스트 1개 실패. 반복 when(...).thenThrow 설정 과정에서
  이전에 설정한 예외가 다음 stubbing 호출에 발생했다. 실제 서비스/DB 통합 17개는 통과했다.
- 자동 수정 1회: doThrow(...).when(...)으로 테스트 설정 중 메서드 호출을 피했다.
  기대값과 서비스 동작은 유지한다. 동일 근본 원인 실행 실패 1회.

## Phase Result — Phase 2
- 상태 VERIFIED (재수신/통합 분기 범위), 전체 기능 IN_PROGRESS.
- 구현: 공통 메타데이터 검증, 불완전 재수신, 정상/불완전 통합 NEVER 진입점과 READ_COMMITTED 실행 Bean.
- 입력 집합 불변·기존 원본/누락/건수/통계 정합성·빈 재수신·4필드/전압 null·메타데이터/DB 시각 확인.
- 선택 통합 17개 PASS (불완전 7·통합 분기 3·기존 정상 재수신 7).
- 실패한 단위 클래스 수정 후 2개 PASS. 종료 코드 0, 실패/오류/건너뜀 0.
- 컴파일 실제 수행. 이미 통과한 통합 테스트는 테스트 설정만 수정한 뒤 반복하지 않았다.
- Phase 2 자동 수정 1회, 기존 정상 정책/트랜잭션/비교 기대값 유지. 다음 Phase 3으로 진행한다.

## Phase Result — Phase 3 선택 검증
- 실제 경합 8개·재수신/통합 롤백 4개 총 12 PASS. 종료 코드 0, 실패/오류/건너뜀 0.
- 신규 0/59개 경합·다른 내용·상대 롤백·정상/불완전 양방향 경합·다른 세션 독립 확인.
- 기존 불완전 세션 대기·동일 집계, INSERT/행 잠금 timeout 후 새 호출 성공을 확인했다.
- 빈/59개 재수신 실제 커밋 실패, 경합 복구 후 커밋 실패, 통합 최초 0개 커밋 실패 모두 롤백했다.
- 기존 서비스에 테스트 훅 없이 spy·독립 DB 연결의 pg_stat_activity 잠금 대기로 검증했다.
- Phase 3 자동 수정 0회. 다음 최종 Full 및 문서/자체 리뷰.

## 최종 Phase Result
- 구현: V4·최초 불완전 저장/누락 진단·불완전 재수신·정상/불완전 통합 라우팅.
- 변경: 총 28개 (신규 Java 19: 본체 10·테스트/지원 9, V4·명세 신규,
  기존 본체 2·테스트 3·HANDOFF·설계 요약 수정). 신규 Java 최대 141줄, 200줄 초과 없음.
- 검증: Phase별 선택 test, 최종 JAVA_HOME=C:/dev/jdk-25.0.4.1+1로
  ./scripts/verify.ps1 -Mode Full. 승인된 Gradle/Docker 접근으로 검증했다.
- 최종 결과 PASS, 종료 코드 0, 21개 클래스 총 97개.
  기존 66 + 신규 31 (저장 5·공통 2·불완전 재수신 7·통합 3·복구 제한 2·경합 8·롤백 4).
  실패/오류/건너뜀 0. PostgreSQL 18 Testcontainers, JDK 25.0.4.1 사용.
- XML UTC 2026-10-21T15:00:26.678Z~15:01:19.293Z
  (KST 2026-10-22 00:00:26~00:01:19). test·compileJava·resolveMainClassName·bootJar·jar 실제 실행,
  compileTestJava/processResources UP-TO-DATE. 앞선 선택 검증에서 테스트 컴파일 실제 수행.
- 인수 조건: 빈/1/59개·중간 누락·통계 제외·동일/필드 충돌/전압 null·빈 재수신·추가/삭제 거절,
  메타데이터/DB 시각·DB 불일치 거절·원본/누락/과거 진단 불변·외부 트랜잭션 거절 충족.
  최초/기존 경합·빈 목록 경합·정상/불완전 양방향·상대 롤백·독립 세션·INSERT/행 잠금 timeout,
  쓰기/실제 커밋 롤백 및 기존 정상 회귀 모두 충족.
- 자동 수정: Phase 1 0회, Phase 2 테스트 Mockito 예외 설정 1회, Phase 3 0회.
  테스트 삭제/약화/검사 비활성화 없음. 스키마 테스트는 승인된 V4 경계를 검증하도록 갱신했다.
- 자체 리뷰: 기존 정상 전용 조건 유지, 공통 검증/메타데이터 검사만 추출.
  domain·V1/V2/V3·기존 MANDATORY/persist·의존성·공개 API/MQTT 변경 없음.
  DB 간 집합/건수 불변식은 서비스·통합 테스트로 확인하며 CHECK로 강제된다고 주장하지 않는다.
- 제한: 최초 결과 고정, 59→60 승격/샘플 교체/잘못된 값 부분 수락 없음.
  상세 참고 통계·추세 조회 API·실패 사건 별도 보존·MQTT 수신/확인 발행은 범위 밖이다.
  V4는 테스트 DB에만 적용했으며 개발 DB/서버를 실행하지 않았다. timeout은 잠금별 대기 제한이다.
- Git: codex/incomplete-session-preservation, staged 없음, unstaged/untracked. diff 점검 PASS.
  커밋/푸시/PR/병합 없음, 원격 CI NOT RUN. Lint/정적 분석/아키텍처 자동 검사 MISSING.
- 커밋 초안: feat: preserve incomplete sessions with immutable final results
- 다음: 사용자 최종 리뷰 후 DONE. 이후 MQTT 메시지 계약 → 연동 실패 처리 계획.

## 최종 승인 및 Git 통합 승인 — 2026-10-22
- 사용자가 DONE을 확인하고 보고한 28개 파일의 커밋·푸시·CI·PR·main 병합을 일괄 승인했다.
- 대상: origin/codex/incomplete-session-preservation → PR → main. 각 SHA의 CI 성공 후 다음 단계로 진행한다.
- 병합된 main의 CI와 로컬 동기화까지 확인한다. 실패 시 중단·원인 보고하며 강제 병합하지 않는다.
- 커밋 메시지: feat: preserve incomplete sessions with immutable final results.
- 최종 Full 97개 성공 이후 코드 변경 없음. 이번에는 승인/상태 문서만 갱신해 기존 검증을 재사용한다.
- 이 기록은 Git 통합 실행 전 체크포인트다. 실제 원격 결과는 PR/Actions와 Git 기록을 확인한다.
