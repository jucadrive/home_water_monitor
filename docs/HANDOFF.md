# Home Water Monitor 인계 — 2026-10-21

## 운영 방식과 현재 작업
- 최상위 프로젝트 실행 지침: [루트 AGENTS.md](../AGENTS.md), [Harness.md](Harness.md).
- 사용자: Architect & Verifier. AI: 하위 에이전트 없는 단일 자율 구현·검증 담당.
- 최초 계획 승인 후 승인 범위의 Phase를 순차 진행한다. 최종 사용자 승인 전 DONE으로 표시하지 않는다.
- 기존 페어 프로그래밍·사용자 직접 작성 지침은 종료했다. 기존 업무 합의는 유지한다.
- 하네스 최소 구축 계획 1~5항 및 관련 문서 전환은 사용자가 승인했다.
- 구축 명세: [tasks/HARNESS_BOOTSTRAP.md](tasks/HARNESS_BOOTSTRAP.md).
- 하네스 상태: DONE. PR #1을 main의 abe4d3c로 병합했고 병합 후 원격 CI도 성공했다.
- 현재 작업: 측정 비교 도메인 VERIFIED, 사용자 최종 리뷰 대기. 브랜치 codex/measurement-comparison. 커밋·푸시는 별도 승인 대상이다.

## Phase Result — 측정 비교 도메인
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
| 최신 실행 보고서 | 측정 비교 추가 후 전체 24개 성공. 상세 증거는 상단 측정 비교 Phase Result |
| 원본 비교 도메인 | record·enum·단위 테스트 13개 구현. 최신 검증은 상단 측정 비교 Phase Result 참고. 저장 서비스 연결은 미구현 |
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
- 현재 정상 경로의 59개 목록 거절은 최종 불완전 보존 정책이 아니다. 불완전 원본 보존과 정상 추세 제외는 이후 구현할 합의다.
- 실제 MQTT 확인 발행, 진단·카운터, 재전송·동시성 처리는 아직 없다. 커밋 명시 거절 테스트가 모든 네트워크 장애와 커밋 결과 불명 상황까지 검증한 것은 아니다.

## 현재 DB 모델

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

- 기존 원본 조회 → 도메인 비교 → 결과별 저장으로 연결하는 재수신 서비스
- 동일 재수신/내용 충돌 진단 테이블·기록 단위·카운터·동시성 처리
- 불완전 세션 저장과 불완전 종료 뒤 변경·추가 목록 정책
- 실제 MQTT 구독·장치 명령·커밋 후 보존 완료 확인·모니터링
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

1. 측정 비교 구현 검증 결과를 사용자에게 보고하고 최종 리뷰 및 커밋·푸시 승인을 받는다.
2. 다음 기능 후보는 재수신 진단의 저장 단위·필드·카운터·동시성 설계이며 별도 계획 승인이 필요하다.
3. 승인된 범위에서 단일 에이전트가 구현·검증한다. 미정 진단 저장·동시성 정책은 별도 검토한다.

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
