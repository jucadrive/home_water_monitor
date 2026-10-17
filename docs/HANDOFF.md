# Home Water Monitor 인계 — 2026-10-17

## 읽는 순서와 협업 방식

1. 이 파일: 최신 구현 상태, 환경 재현, 바로 다음 단계
2. [AGENTS.md](AGENTS.md): 학습 모드와 프로젝트 배경
3. [상세 명세](HOME_WATER_MONITOR_CODEX_SPEC_PAIR_PROGRAMMING.md): 업무 규칙과 참고 설계
4. [백엔드 설계 요약](BACKEND_DESIGN_SUMMARY.md): 상태표·계약 후보·첫 저장 기능의 목표

진행 상태는 이 파일과 실제 소스를 우선한다. 과거 명세의 예시 DDL·코드를 현재 구현으로 간주하지 않는다. 사용자가 직접 작성·실행하며 **파일 위치 → 작은 코드 → 각 부분의 의미 → 실행 결과 읽기** 순서로 한 단계씩 안내한다. 이미 결정한 사항을 다시 퀴즈로 묻지 않는다. 요청 없는 기능 구현·파일 수정·테스트 실행·커밋·푸시는 하지 않는다.

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
| 통계 단위 테스트 | 7개. 사용자 실행 성공 보고와 로컬 XML 보고서의 실패 0·오류 0 확인. 이번 Git 정리에서는 재실행하지 않음 |
| PostgreSQL / 서버 접속 | 사용자 확인: 컨테이너 실행, SELECT 1, Spring Boot의 DB 접속·서버 실행 |
| V1 | `tds_session` 생성. 사용자 적용·테이블 조회 확인 |
| V2 | `tds_measurement` 생성. 사용자 적용·테이블 조회 확인 |
| `TdsMeasurementEntity` | `measurement.infrastructure.persistence`에 작성. PK는 DB IDENTITY, `sessionId`는 UUID 직접 매핑 |
| 엔티티 작성 후 서버 재실행 | 완료 보고가 없어 매핑 검증 완료 여부는 미확인 |
| 환경변수 분리 후 서버 재실행 | 설정의 변수 참조는 확인. IntelliJ 환경변수 설정 및 실제 재실행 성공은 미확인 |
| Git | 루트 `.git` 한 개. 조사 시 최초 커밋 없음·원격 없음. 이번 작업은 공통 제외 규칙과 최초 커밋 준비를 정리하는 범위 |

## 현재 DB 모델

- 실제 DDL은 `src/main/resources/db/migration/V1__create_tds_session.sql`과 `V2__create_tds_measurement.sql`이다. 오래된 명세의 참고 DDL보다 실제 마이그레이션을 기준으로 확인한다.
- 세션: 식별·장치·시각, 기대/보존/통계 대상 건수, `NORMAL`/`INCOMPLETE` 보존 결과, 가정 온도·교정 버전, 최소/최대/평균/중앙값.
- 원본: 내부 자동 생성 ID, 세션 UUID, seq, 경과시간, TDS, 안정화 여부, 선택 전압.
- 원본의 업무 식별은 `(session_id, seq)` UNIQUE다. 측정 시각·PPM은 UNIQUE에 넣지 않는다. 원본은 세션을 FK로 참조한다.
- 수치 정밀도·입력 범위·NULL·불완전 결과 정책의 세부 검증은 계속 검토한다. DB 제약만으로 최종 업무 정책이 모두 구현된 것은 아니다.

## 아직 구현하지 않은 것

- `TdsSessionEntity`, 저장용 생성자·조회 메서드, Repository, 애플리케이션 저장 서비스
- 안정화 원본을 제외하는 세션 통계 처리
- 정상 60개 원본·세션·통계 저장과 롤백 통합 테스트
- 동일 재수신/내용 충돌 비교·진단 기록·동시성 처리
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

## 바로 다음 단계

1. 데스크톱 환경변수·DB 연결·현재 엔티티 매핑을 확인한다.
2. 통계 테스트 7개를 기준으로 재확인한다. 실행은 사용자 요청 범위에서 진행한다.
3. `TdsSessionEntity` 하나를 파일 위치·코드·의미·실행 결과 순서로 안내한다.
4. 객체 생성·조회 방법과 Repository·저장 서비스로 연결한다.
5. 가상 seq 1~60 중 첫 10개는 안정화 300.0ppm, 나머지 50개는 151.3ppm인 사례로 안정화 제외와 세션 통계를 확인한다.
6. 정상 세션 1건·원본 60건·통계 대상 50건과 통계를 한 트랜잭션으로 저장하고, 쓰기/커밋 실패 시 이번 변경 전체가 롤백되는지 실제 PostgreSQL에서 검증한다. 커밋 검증을 테스트의 바깥 트랜잭션에 가리지 않도록 설계한다.

전체 DDD·모니터링·MQTT 계약을 다시 완성하느라 이 첫 저장 기능을 지연시키지 않는다. 기존에 합의한 중복/충돌·원자성·불완전 보존·재전송 정책은 상세 문서에서 이어받는다.
