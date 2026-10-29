# 서버 주도 매시간 자동 측정

상태 VERIFIED(로컬 구현·자동 검증). 2026-10-29 사용자가 전체 구현 계획 승인. 운영 배포/활성화·실시간 정각 장비 인수는 NOT RUN이며 사용자 최종 리뷰 전 DONE으로 표시하지 않는다.

## 범위·인수 조건

- 서버가 매시간 정각(UTC 기준, 한국에서도 매 정각)에 기존 START application 경로로 요청한다. 앱 수동 버튼·펌웨어·MQTT 계약은 유지한다. 단일 장비와 대상 재배를 서버 설정에 명시하며 기본 OFF다. 별도 라이브러리는 추가하지 않는다.
- 기존 장비 잠금·멱등성·재배 활성 검증·센서 준비 probe·보존 후 해제 규칙을 재사용한다. 진행 중/미확정/오프라인/센서 오류/종료 재배는 자동으로 새 측정을 강행하지 않는다. 자동 재시도·버퍼 삭제·인증 우회 없음.
- 새 V10으로 장비+시간 회차의 유일한 실행 기록을 보존한다. 실제 커밋된 회차 확보 후에만 명령을 요청한다. 수동 명령은 기존 경로 그대로이며 자동 기록의 명령 키/ID로 구분한다.
- 재시작 즉시 실행·지난 회차 보충 없음. 시작 이후의 정각만 허용하며 정각 이후60초를 넘겨 지연된 실행도 보내지 않는다. 같은 회차 중복 호출/동시 서버는 DB 유일 키로 하나만 확보한다.
- 회차 확보 직후 장애로 요청되지 않을 수 있다. 안전을 위해 중복 재실행하지 않고 다음 시간에 남은 CLAIMED를 SUBMITTED(명령 존재) 또는 UNKNOWN으로 정리한다. SUBMITTED는 요청 생성이며 실제 ACCEPTED/저장 성공 의미가 아니다. 명령 상태/실패 사유는 기존 명령과 조인해 조회한다.
- 수동이 먼저 실행되면 자동은 기존 BUSY 처리로 건너뛴다. 자동이 먼저 확보했으면 수동은 기존 BUSY 응답을 받는다. 진행 중 측정 취소/우선권 탈취 없음.

## 순차 작업

1. 순수 시각 정책·기본 OFF 설정·V10 및 회차 저장/요청 조율·Spring 스케줄링 구현.
2. 가상 시각, 중복/동시성, 재시작, 수동 충돌/미확정 버퍼, 비활성 재배, 오프라인 및 MQTT 기존 경로 연계 테스트. Docker 전용 PostgreSQL/브로커 사용.
3. backend Full build 및 migration 회귀. 앱/펌웨어는 변경하지 않으므로 기존 검증 재사용. 운영 장비로 테스트하지 않는다.
4. 수동 배포/켜기/끄기·조회 안내와 HANDOFF 기록, diff/비밀 검사. 서버 SSH/운영 DB 적용/커밋/푸시는 하지 않는다. V1~V9 편집 금지.

## 변경 범위·위험

- backend measurement.domain 시각 정책, session.application 자동 요청, infrastructure scheduling/영속성, application.yaml, V10 및 관련 테스트. infra/personal의 opt-in Compose와 수동 안내를 추가한다.
- 기존 온도/UI 미커밋 변경·첨부·비밀 설정을 보존한다. 현재 codex/temperature-compensation에서 진행하며 임의 stash/브랜치 전환하지 않는다.
- 실수로 켜면 매시간 실제 DB 기록/장비 구동이 발생한다. 명시적 장비/재배 설정과 기본 OFF, 사용자 수동 활성화로 관리한다. 켜기 전 재배/프로브·기존 미확정 원본을 확인한다.
- 테스트/인수 전 VERIFIED라고 보고하지 않는다. 구현 Phase 수정5회/동일원인3회 한도. 커밋 초안 `feat: schedule hourly measurements through existing control flow`.

## 실행 체크포인트

- 최초 관련18개 새 실행 PASS/exit0/39초: 순수 시각3·서비스7·DB통합7·과거 업그레이드1. 결과 `.tools/hourly-automatic/targeted-results`. 첫 셸 호출은 로그 폴더 경로 오류로 Gradle이 실행되지 않았으며 성공 테스트로 계산하지 않았다.
- 첫 Full261개 새 실행 중259개 통과/실패2·오류0·건너뜀0, exit1/5분26초. 결과 `full-attempt1` 보존. 수정1/5: ApplicationContextRunner에서 조건부 모의 ControlRuntime도 활성화되도록 control.enabled=true 지정. 수정2/5: 큰 온도 원본 fixture를 제어 codec의4096byte 제한 파서로 읽던 테스트를 일반 결과 JSON 파서로 변경. 운영 제어 크기 제한/검사·제품 코드는 약화하지 않았다.
- 수정 후 설정4개 및 실제 브로커 자동 START/온도 보존·해제1개 재검증5개 PASS/exit0/1분13초, `repair-results`. 동일 근본원인3회 실패 없음. 아래 최종 Full 결과가 우선한다.
- 비밀 없는 Compose ON/OFF2조건 config 검사 PASS, 기존4서비스/백엔드·DB 미공개/선택 LAN TLS8883 유지. `compose-result.json`. 컨테이너 생성/운영 호출 없음.

## 최종 Phase Result

- 후속 운영 보고: 사용자가 수동 배포/켜기 절차 수행 완료를 알렸다. 직접 서버 검증/정각 회차 결과 수집은 아직 NOT RUN이다. 충전기 전원 이동 직전 직접 UART IDLE/보관0/Wi-Fi·MQTT 정상 확인. 이동 후 재연결·자동 정각 인수는 대기이며 로컬 VERIFIED와 구분한다.

- 구현: 순수 정각/재시작 정책, 기본OFF 및 장비·재배 명시/시작 검증, V10 회차 유일 키·커밋 후 START·중단 회차 조정, 기존 제어/보존 경로 재사용. 자동/수동 기록은 기존 명령 키로 조인한다. opt-in Compose·수동 배포·Java 설계/실행 흐름/실습 안내를 추가했다.
- 최종 `./gradlew.bat build --no-daemon --console=plain` PASS/exit0/5분22초. 261개 새 테스트 실행·실패0/오류0/건너뜀0. 이번 최종 명령의8작업 중 test1개 실행·나머지7개 UP-TO-DATE(이미 빌드된 같은 제품 코드 산출물 재사용). 테스트 미실행을 새 성공으로 계산하지 않았다.
- 자동 기능 신규23개(순수3/서비스7/DB7/설정4/MQTT2) 및 기존238개 회귀를 포함한다. 자동 START→실제 시험 브로커/모의 장비→v2 온도60원본/통계50→버퍼 해제, 오프라인 미전송 기록·기존 수동 충돌/불확실 버퍼 보호 확인. 실제 운영 ESP32로 자동 측정한 증거는 아니다.
- 증거 `.tools/hourly-automatic/result.json`, `full-final.log`, `full-final-results`, 초기 실패/수정 결과 폴더. Testcontainers 잔여 자원 없음 확인. backend 자동 Lint/정적/아키텍처 검사는 MISSING이다. 앱/펌웨어는 변경 없이 앞선 검증을 재사용했다.
- 전송 폴더 `.tools/hourly-automatic/server-upload/`: `app.automatic.jar`, `compose.automatic-measurement.yml`, 기본OFF `automatic-measurement.env`, 대조용 `sha256.json`. JAR에 V10/실행 클래스 포함 확인. JAR SHA256 `ED580E6CB0F4369743ACB1E0A542110F35DA5FC7B51D0398A394D8CBFC9937AB`. 실제 계정/비밀번호·토큰·인증서 없음.
- 비밀 검사95개 파일/실제 비밀값8종 메모리 비교 이슈0, diff 공백 검사 PASS, 새 Java/Kotlin200줄 초과 없음, AGENTS250줄 이하. 기존 미커밋/인덱스·첨부 보존. 브랜치 codex/temperature-compensation, 변경 unstaged, 커밋/푸시 없음.
- 위험/다음 Phase: 운영에서는 아직 미배포·기본OFF. 사용자 수동 백업/V10 배포→장비·활성 재배 지정/켜기→다음 정각의실제 NORMAL/60/50/수온/해제 확인이 남았다. 서버 SSH/원격 변경·운영 장비 START는 수행하지 않았다. 미확정 회차 누락을 허용하는 설계이며 exactly-once 하드웨어 실행이나 장기 안정성을 보증하지 않는다.
