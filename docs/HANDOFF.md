# Home Water Monitor 인계 — 2026-10-20

## 읽는 순서와 협업 방식

1. 이 파일: 최신 구현 상태, 환경 재현, 바로 다음 단계
2. [AGENTS.md](AGENTS.md): 학습 모드와 프로젝트 배경
3. [상세 명세](HOME_WATER_MONITOR_CODEX_SPEC_PAIR_PROGRAMMING.md): 업무 규칙과 참고 설계
4. [백엔드 설계 요약](BACKEND_DESIGN_SUMMARY.md): 상태표·계약 후보·첫 저장 기능의 목표
5. [다음 작업 코드](NEXT_MEASUREMENT_COMPARISON.md): 아직 작성하지 않은 측정 도메인 비교 코드와 단위 테스트

진행 상태는 이 파일과 실제 소스를 우선한다. 과거 명세의 예시 DDL·코드를 현재 구현으로 간주하지 않는다. 사용자가 직접 작성·실행하며 **파일 위치 → 해당 작성 단위의 전체 코드 → 각 부분의 의미 → 실행 결과 읽기** 순서로 안내한다. 모든 제시 코드에 간단한 주석을 반드시 넣고 코드 아래에서도 설명한다. 이미 결정한 사항을 다시 퀴즈로 묻지 않는다. 요청 없는 기능 구현·파일 수정·테스트 실행·커밋·푸시는 하지 않는다.

**현재 중단 지점:** 정상 신규 세션 저장과 통합 테스트 3개까지 사용자가 작성·통과 보고했다. 원본 네 필드를 모두 비교하는 정책을 채택했고 도메인 코드·테스트를 대화에서 안내했지만, 사용자는 다음 작업일에 노트북에서 직접 작성하기로 했다. **`TdsMeasurement`, `TdsMeasurementComparisonResult`, `TdsMeasurementTest`는 아직 소스에 없다.** 다음 대화는 [동일한 주석 포함 코드](NEXT_MEASUREMENT_COMPARISON.md)를 기준으로 이 세 파일 작성부터 재개한다.

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
| 최신 실행 보고서 | 이번 읽기 전용 확인에서 XML은 커밋 실패 테스트 1개·실패 0·오류 0을 담고 있음. 클래스 전체 3개나 전체 테스트 스위트의 최신 실행 보고서로 확대 해석하지 않음 |
| 원본 비교 도메인 | 네 필드 비교 정책 채택, 코드 안내까지 완료. 실제 소스 3개 작성·테스트 실행은 다음 작업 |
| Git | 모노레포 루트 `.git` 한 개, `main`이 `origin/main` 추적. 원격은 `https://github.com/jucadrive/home_water_monitor`. 커밋·푸시 결과는 실제 Git 상태로 확인 |

이번 요청에서는 문서 최신화와 현재 변경의 Git 공유만 수행한다. 애플리케이션 기능 추가와 테스트 재실행은 하지 않는다.

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
- 위 세부 코드와 단위 테스트 6개는 [다음 작업 문서](NEXT_MEASUREMENT_COMPARISON.md)의 **미작성 안내 코드**다. 아직 실행 검증한 구현이 아니다.

## 아직 구현하지 않은 것

- `TdsMeasurement`, `TdsMeasurementComparisonResult`, `TdsMeasurementTest` 실제 파일 작성과 비교 테스트 실행
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

전체 `test`에는 기존 `HomeWaterMonitorBackendApplicationTests.contextLoads()`도 포함된다. 그 테스트에는 Testcontainers 연결이 없으므로 별도로 개발 DB·환경변수가 필요하다. 현재 통합 테스트의 성공을 전체 테스트 스위트 성공으로 표현하지 않는다.

## 바로 다음 단계

1. 노트북 저장소 루트에서 `git status`로 로컬 변경을 확인하고, 깨끗하면 `git pull --ff-only`로 `main`을 갱신한다. 충돌·로컬 변경이 있으면 임의로 지우거나 덮어쓰지 않는다.
2. 이 문서와 [다음 작업 코드](NEXT_MEASUREMENT_COMPARISON.md)를 읽고 세 비교 파일이 아직 없는지 실제 소스와 확인한다.
3. 해당 문서의 enum → 도메인 record → 단위 테스트 전체 코드를 주석·설명과 함께 안내한다. 사용자가 직접 작성한다. 이전 저장 서비스와 통합 테스트를 다시 작성시키지 않는다.
4. 사용자가 `TdsMeasurementTest`만 실행해 6개 통과를 확인한다. 이 테스트에는 Spring·DB·Docker가 필요 없다.
5. 이후 진단 기록의 정보와 메시지/샘플 단위, 카운터 정의를 함께 정하고 새 마이그레이션·서비스 연결로 진행한다. 세션 메타데이터 변경 비교·동시성은 별도 검토한다.

전체 DDD·모니터링·MQTT 계약을 다시 완성하느라 이 첫 저장 기능을 지연시키지 않는다. 기존에 합의한 중복/충돌·원자성·불완전 보존·재전송 정책은 상세 문서에서 이어받는다.

## 노트북 Codex에 전달할 시작 문구

> 루트 AGENTS.md와 docs/HANDOFF.md를 먼저 읽고 안내된 상세 명세·설계 요약·docs/NEXT_MEASUREMENT_COMPARISON.md도 읽어줘. 정상 세션 저장과 통합 테스트 3개는 이미 작성·통과 보고했고, 다음 도메인 비교 파일 3개는 아직 작성하지 않았어. 네 필드 전체 비교는 선택을 마쳤으니 다시 묻지 말고, NEXT_MEASUREMENT_COMPARISON.md의 주석 포함 전체 코드부터 같은 순서로 안내해줘. 나는 직접 코드를 작성·실행할 테니 요청 없는 소스 수정이나 테스트 실행은 하지 마.
