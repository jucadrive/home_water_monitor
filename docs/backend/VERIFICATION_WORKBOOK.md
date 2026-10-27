# 백엔드 검증 실습

각 실습은 **예측 → 코드 추적 → 로컬 재현 → 관측 → 반례/한계 설명** 순서로 진행한다. 예상 결과를 먼저 적고 나중에 설명과 비교한다. 이미 통과한 테스트를 실행했다는 사실만으로 코드 흐름을 이해했다고 판정하지 않는다.

이 문서의 Gradle 명령은 재현 안내다. 문서 작성 과정에서 전체 테스트를 새로 실행했다는 뜻은 아니다. 최신 실제 실행 이력은 [HANDOFF](../HANDOFF.md), 기능별 인수는 tasks를 따른다. 체크 표시와 관측 기록은 직접 실행한 뒤에만 채운다.

## 0. 환경과 실행 원칙

### 실행 위치와 JDK

아래 Gradle 명령은 **backend/home-water-monitor-backend**에서 실행한다. Windows PowerShell에서 저장소 루트부터 시작하는 예시는 다음과 같다.

```powershell
$env:JAVA_HOME = 'C:\dev\jdk-25.0.4+7'
& "$env:JAVA_HOME\bin\java.exe" -version
Set-Location backend/home-water-monitor-backend
.\gradlew.bat --version
```

JDK 경로는 이 개발 PC의 실제 설치 예시다. 다른 환경은 자신의 JDK25 경로로 맞춘다. Android의 JDK21과 혼동하지 않는다. 미니 PC에 JDK를 별도 설치하라는 절차가 아니다.

- 순수 domain 테스트는 Docker가 필요 없다. 보안의 순수 토큰 테스트도 Docker 없이 실행된다.
- 통합 테스트는 Docker 엔진과 Testcontainers가 필요하다. `docker version`으로 **Server** 연결까지 확인한다. 운영 PostgreSQL 주소/비밀번호를 테스트에 주입하지 않는다.
- 테스트가 사용하는 공개 fixture 계정은 test resources/classpath에만 있다. 운영의 auth.env/postgres.env/mqtt.env를 가져오지 않는다.
- 이 작업 트리에는 기존 미커밋 변경이 있다. `git reset --hard`, `git clean`, 운영 DB 초기화로 학습 환경을 만들지 않는다. 코드 변경 실습은 기준 소스를 보존한 별도 학습 작업공간을 준비한 뒤에만 한다.
- 처음에는 기존 테스트 읽기·breakpoint·로컬 실행만 수행해도 된다. 기존 테스트 삭제/기대값 약화/검사 비활성화로 통과시키지 않는다.
- `UP-TO-DATE`/`FROM-CACHE`는 새 실행과 다르다. 직접 재현이 목적이면 **선택한 테스트에 한해** `--rerun-tasks`를 추가할 수 있다. 성공 검사를 이유 없이 반복하지 않는다.

### 결과 읽기

성공 종료 코드와 함께 `build/test-results/test`의 XML, `build/reports/tests/test/index.html`의 실패/오류/건너뜀을 본다. 테스트가0개이거나 모두 건너뛰어졌는데 목표 동작을 검증했다고 적지 않는다. 이름이 IntegrationTest라고 해서 실제 브로커/DB를 쓰는지 단정하지 말고 `@Container`, mock 설정, fixture를 읽는다.

루트의 `scripts/verify.ps1 -Mode Unit`은 `*.domain.*`만 선택한다. application의 mock 단위 테스트와 security 패키지의 순수 테스트까지 전부 실행하는 명령이 아니다. Full은 build이며 현재 별도 backend Lint/Arch 자동 검사까지 제공하지 않는다.

## 1. 값·동등성·계산: Spring 없이 시작

**먼저 예측한다:** `300.0`과 `300.00`은 같은가? 같은 값이 다른 seq에 있으면 같은 원본인가? 중앙값 계산이 입력 목록을 정렬해도 되는가?

읽기: `TdsMeasurement.compareWith`, `TdsStatisticsCalculator.average/median` → 해당 domain 테스트.

```powershell
.\gradlew.bat test --tests '*TdsMeasurementTest' --tests '*TdsStatisticsCalculatorTest' --no-daemon --console=plain
```

관측 항목:

1. 같은 sessionId/seq에서 scale 차이는 IDENTICAL이고, elapsedMs/isWarmup/전압의 변화는 CONFLICT인 assertion을 찾는다.
2. 다른 키는 CONFLICT가 아니라 입력 오류인지 확인한다. null 전압의 양방향도 확인한다.
3. 평균의 반올림과 원본 비교의 무반올림을 구분한다.
4. 중앙값이 원본을 복사하는 지점과 시간복잡도를 설명한다.

**선택 코딩 과제:** 분리된 학습 작업공간의 `TdsStatisticsCalculatorTest`에 입력 순서 불변성 테스트를 직접 추가한다. 기존 테스트를 수정하지 않는다. 메서드 내부 예시는 다음과 같고 새 테스트를 이번 문서가 자동 추가하지는 않는다.

```java
@Test
void medianKeepsCallerOrder() {
    var input = new java.util.ArrayList<>(java.util.List.of(
            new java.math.BigDecimal("30"),
            new java.math.BigDecimal("10"),
            new java.math.BigDecimal("20")));
    var before = java.util.List.copyOf(input);
    var result = new TdsStatisticsCalculator().median(input);
    org.junit.jupiter.api.Assertions.assertEquals(0,
            new java.math.BigDecimal("20").compareTo(result));
    org.junit.jupiter.api.Assertions.assertEquals(before, input);
}
```

완료 기준: `record.equals()`를 사용하면 안 되는 업무 동등성, BigDecimal의 표현/수치 차이, 계산의 부수 효과를 자신의 말과 테스트로 설명한다. ‘BigDecimal을 쓰면 물리적 측정 오차가 사라진다’는 결론은 내리지 않는다.

## 2. 재배·물 관리: HTTP에서 서비스와 DB까지

**먼저 예측한다:** 같은 키/같은 본문과 같은 키/다른 본문은 어떻게 다른가? 이미 성공한 물 관리 요청을 재배 종료 후 재시도하면 어떻게 되는가?

```powershell
.\gradlew.bat test --tests '*CultivationIntegrationTest' --tests '*WaterMaintenanceIntegrationTest' --no-daemon --console=plain
```

읽기/중단점 순서:

1. HTTP 시험이 있는 경우 Controller에서 DTO/Idempotency-Key를 확인한다. 서비스를 직접 호출하는 시험이면 Controller를 통과하지 않는다는 차이를 기록한다.
2. `CultivationService.create` → `CultivationStore.insert`의 SQL → 저장 값 비교 → 서비스 프록시 반환을 추적한다.
3. `WaterMaintenanceService.create`에서 existing 조회가 requireActive보다 먼저인 이유를 설명한다.
4. `closureSerializesWithRegistration()`에서 두 업무가 어떤 재배 잠금을 공유하는지 확인한다.

관측은 테스트의 `JdbcTemplate`과 assertion으로 제한한다. 운영 DBeaver 연결에서 실습용 INSERT/UPDATE를 실행하지 않는다. DB에서1행만 남았다고 내용 충돌 규칙까지 증명된 것은 아니므로 반환 결과/오류 코드도 함께 읽는다.

완료 기준: 최초201·같은 요청200·충돌409를 분리하고, DB 고유 키가 왜 사전 조회만으로는 대체되지 않는지 설명한다. 관리 이력의 occurredAt/createdAt 역할도 구분한다.

## 3. 두 요청의 경합: 없는 행은 무엇으로 보호하는가

**먼저 예측한다:** 두 트랜잭션이 모두 세션이 없다고 읽었을 때 누가 신규를 확정하는가? 첫 요청이 롤백하면 결과가 바뀌는가?

```powershell
.\gradlew.bat test --tests '*TdsSessionRoutingConcurrencyIntegrationTest' --no-daemon --console=plain
```

읽기: `RoutingConcurrencySupport` → 테스트의 Executor/래치/두 연결 → `ProcessTdsSessionService` → `TdsSessionProcessingTransactionService` → lock adapter/PK classifier.

아래 표를 직접 채운다.

| 시점 | T1 트랜잭션/원본 | T2 트랜잭션/결과 | 관측 증거 |
|---|---|---|---|
| 두 신규 조회 후 | | | |
| T1 미커밋 insert | | | |
| T1 commit 또는 rollback | | | |
| T2 종료 | | | |

집중 테스트는 `commitsOneOriginalAndOneIdenticalRedeliveryForConcurrentNewRequests`, `savesTheWaitingRequestAsNewWhenTheFirstTransactionRollsBack`, `allowsAnIndependentNewSessionToCommitWhileTheFirstSaveIsUncommitted`다. 이름을 읽은 뒤 실제 assertion이 원본/진단/독립 세션을 어디까지 검사하는지 확인한다.

디버거로 모든 스레드를 정지하면 경합 자체가 사라지거나 timeout이 날 수 있다. 동시성 증거는 breakpoint 스크린샷보다 래치로 순서를 만든 테스트와 DB 관측을 우선한다. 디버깅으로 발생한 시간 초과와 정상 실행의 실패를 구분한다.

완료 기준: Java synchronized·DB 행 잠금·PK/UNIQUE의 범위를 각각 설명하고, 모든 DB 예외를 중복 요청으로 복구하면 안 되는 반례를 든다.

## 4. flush·return·실제 commit 실패 구분

**먼저 예측한다:**60개 원본에 대한 쓰기 호출이 모두 끝나도 커밋은 실패할 수 있는가? 그때 이번 세션/원본/진단은 무엇이 남아야 하는가?

```powershell
.\gradlew.bat test --tests '*TdsSessionRoutingRollbackIntegrationTest' --no-daemon --console=plain
```

`rollsBackNewSessionWhenTheRealCommitFailsAfterSixtyMeasurements()`와 `rollsBackExistingDiagnosticsWhenTheRealCommitFailsAfterSixtyDetails()`를 먼저 읽는다. 시험 전용 지연 제약/트리거가 실제 PostgreSQL 커밋 때 실패하도록 설치되는 부분과 finally 정리를 찾는다. 이 DDL을 운영 DB에 복사하지 않는다.

중단점은 저장 본문의 마지막 줄, 호출자 `ProcessTdsSessionService.process`의 try/catch, 실제 테스트 assertion이다. Bean 본문 return 이후 프록시가 커밋을 마쳐야 호출자에게 정상 반환할 수 있다는 순서를 그린다. 내부 REQUIRED 서비스와 바깥 물리 트랜잭션을 별개의 커밋으로 세지 않는다.

원본 일부 쓰기 실패 사례와 마지막 커밋 실패 사례가 왜 둘 다 필요한지 설명한다. mock repository가 예외를 던지는 테스트만으로 지연 제약/진짜 rollback을 증명할 수는 없다.

완료 기준: `saveAll`/`persist`/`flush`/프록시 정상 반환을 구분하고, 실패한 트랜잭션 내부에서 재조회하는 설계의 문제를 설명한다.

## 5. 인증: 필터·메모리 상태·시간 경계

**먼저 예측한다:** 유효 토큰의 만료 시각과 현재 시각이 정확히 같으면 허용되는가? 서버를 재시작하면 무슨 일이 생기는가? STATELESS는 무엇을 없애는가?

```powershell
.\gradlew.bat test --tests '*TokenRegistryTest' --tests '*OwnerAuthenticationTest' --no-daemon --console=plain
.\gradlew.bat test --tests '*AuthenticationIntegrationTest' --no-daemon --console=plain
```

첫 명령은 순수 테스트, 두 번째는 실제 HTTP/필터와 시험 DB를 포함한다. 외부 계정의 비밀번호나 토큰을 사용하지 않는다.

읽기/관측:

1. `TokenRegistry.find`의 형식 검사·prune·다이제스트 조회를 추적한다. `expiresAt.isAfter(now)` 경계를 표로 쓴다.
2. `expiryRejectsHttpRequestsAtTheExactBoundary()`가 실제 필터를 통과하며 Clock을 제어하는지 확인한다. 긴 sleep을 넣는 방법과 비교한다.
3. 무인증/만료 요청에서 업무 Controller가 호출되지 않는 것을 breakpoint로 확인한다.
4. 성공 로그인/업무 접근/로그아웃/재접근의 상태 코드와 no-store/리다이렉트/쿠키 assertion을 읽는다.
5. BCrypt로 저장된 비밀번호를 검증하는 부분과 SHA256 토큰 조회를 혼동하지 않는다. 디버거에서도 실제 운영 비밀번호/토큰을 열거나 기록하지 않는다.

완료 기준:401/403/400/429의 구분, 프로세스 메모리 상태의 제약, 쿠키 인증 도입 시 CSRF 근거를 다시 봐야 하는 이유를 설명한다.

## 6. 비동기 명령:202 이후 어디서 무엇이 끝나는가

**먼저 예측한다:**202를 받았는데 장치가 실행하지 않을 수 있는가? 응답 유실 뒤 UNKNOWN을 곧바로 실패로 바꾸면 어떤 중복 위험이 있는가?

```powershell
.\gradlew.bat test --tests '*ControlRuntimeTest' --tests '*CommandTransactionIntegrationTest' --no-daemon --console=plain
```

읽기: Controller → `ControlRuntime.submit/doSubmit/dispatch` → `CommandTransactionService.reserve/dispatch/observe/recover` → `DeviceStatusReceiver` → `ControlProbeExchange`.

다음 사건을 별도 선으로 그린다: HTTP202, 예약 커밋, probe 시작, 신선한 상태 수신, DISPATCHING 커밋, START 발행, 실행 수락 관측, 결과 DB 보존, 장치 해제 관측. 각 선에 실행 스레드와 열려 있는 트랜잭션 여부를 적는다.

- `admissionIsBoundedAndConcurrentSameKeyWaitsForOriginalReservation()`에서 worker/동일 키/용량 제한을 읽는다.
- `timeoutNeedsFreshProbeAndPreservesEarlierWindow()`에서 일반 상태 수신과 신선한 probe의 증거 차이를 확인한다.
- `acceptedExecutionAndDbPreservationBothPrecedeSlotRelease()`에서 장치 IDLE만으로 다음 측정을 허용하면 안 되는 이유를 찾는다.
- 코드의 recover 분기를 읽고 REQUESTED/PROBING/DISPATCHING별 재시작 결과를 먼저 예측한다. 네트워크가 조용하다고 미실행으로 확정하지 않는다.

완료 기준: 단일 JVM의 제한/락과 DB의 영속 경합 방어를 구분하고, HTTP 스레드가 측정 종료를 기다리지 않는 구조를 설명한다. ‘스레드를 분리하면 트랜잭션도 자동 전파된다’는 결론은 틀리다.

## 7. MQTT: mock 순서와 실제 시스템 증거 비교

**먼저 예측한다:** 저장 실패 때 수신 ACK는 나갈 수 있는가? 커밋 성공 뒤 확인 발행이 실패하면 원본을 삭제해야 하는가?

```powershell
.\gradlew.bat test --tests '*MqttSessionReceiverTest' --no-daemon --console=plain
.\gradlew.bat test --tests '*MqttSessionIntegrationTest' --no-daemon --console=plain
```

첫 테스트의 `processesThenPublishesThenAcknowledges`, `storageFailureSendsNoConfirmationButAcknowledges`, `publisherFailureDoesNotRepeatStorageAndAcknowledges`를 읽는다. 그 다음 실제 시스템 테스트의 `actualDeferredCommitFailureHasNoConfirmationAndRollsBackEverything`, `publishingFailureKeepsCommitAndDeviceRedeliveryRecoversConfirmation`으로 증거의 범위를 넓힌다.

MQTT wire 메타데이터와 애플리케이션 payload를 모두 확인한다. QoS1만 맞고 retain/PFI/content-type/장비 토픽이 틀리면 저장되는지 예측한다. PUBACK·수신 ACK·SESSION_PRESERVED는 한 종류의 성공 신호가 아니다.

### 선택: 현재 개인 배포 Compose까지 연결한 로컬 모의 인수

개발 PC의 Docker·Python3/bcrypt·JDK25와 현재 검증된 실행 JAR가 필요하다. backend 디렉터리에서 저장소 루트로 돌아와 실행한다.

```powershell
Set-Location ../..
python scripts/test_personal_mqtt.py
python scripts/verify-personal-mqtt.py
Set-Location backend/home-water-monitor-backend
```

이 도구는 무작위 프로젝트·새 시험 비밀번호·독립 DB/브로커를 만들고 정상 종료 시 해당 시험 자원을 정리한다. 실제 서버·사용자의 배포 비밀·장비를 읽거나 접속하지 않는다. 결과는 `.tools/deployment/ppossak-mqtt-check-*/result.json`에 기록된다. 프로세스를 강제 종료하면 정리 여부를 별도 확인하고, 임의의 운영 프로젝트에 down/볼륨 삭제를 실행하지 않는다.

9개 그룹은 인증/ACL·모의 명령/60원본·보존 확인 관측 시 DB 가시성·해제·브로커/백엔드 재시작·180원본 보존을 확인한다. ‘3회 모의 측정 성공’을 센서 정확도나 실제 Wi-Fi의 검증으로 바꾸어 기록하지 않는다.

완료 기준: 확인 유실의 복구 입력을 설명하고, 현재 outbox가 없다는 사실과 보존 확인 이후 장치 해제 관측의 차이를 말한다.

## 8. 읽기 모델: 출처·시각·통계와 쿼리 비용

**먼저 예측한다:** SIMULATED 정상 결과는 이력과 실측 추세에 똑같이 나타나는가? startedAt이 null이면 화면에 어떤 시각과 근거가 나오는가?

```powershell
.\gradlew.bat test --tests '*CultivationMeasurementIntegrationTest' --tests '*HydroponicMigrationIntegrationTest' --no-daemon --console=plain
```

읽기: `MeasurementQueryStore.mapper/list` → `MeasurementQueryService.detail/latest/list` → `CultivationOverviewService.list/get`.

1. source/timeBasis/displayAt·보존/통계 건수가 어디서 만들어지는지 표시한다.
2. 전체 원본과 정상 통계의 대상이 다른 이유를 설명한다. 불완전 결과를0ppm이나 정상 평균으로 대신하지 않는다.
3. 과거 재배 미연결 데이터가 사라지지 않는지, 예약된 재배와 결과가 어떤 키로 이어지는지 테스트에서 찾는다.
4. 목록1개/여러 개에 대해 호출되는 추가 조회 수를 소스로 세어 본다. 정확한 SQL 수·비용은 별도 로컬 관측 전에는 NOT MEASURED로 적는다.

확장 과제는 독립 시험 DB의 SQL 로그/쿼리 계수로 목록 크기별 쿼리 수를 측정하고 개선 후보를 비교하는 것이다. 운영 로그에 요청 본문·인증·환경 값을 켜지 않는다. 현재 keyset 페이지·쿼리 수 상한 테스트·부하 인수가 구현됐다고 가정하지 않는다.

완료 기준: 화면용 DTO의 의미를 원본 데이터와 구분하고, `readOnly=true`를 여러 SELECT의 강한 단일 스냅샷이나 쓰기 보안 통제로 설명하지 않는다.

## 9. 설계 대안을 설명하는 연습

정답 문장을 먼저 외우지 말고 각 문항에 코드/테스트1개와 비용1개를 붙인다.

| 질문 | 최소로 포함할 근거 |
|---|---|
| 왜 application과 순수 domain을 나눴는가? | 인프라 없이 비교/계산 테스트, 현재 application→persistence 직접 의존의 한계 |
| 왜 모든 객체를 Bean으로 만들지 않았는가? | 자원/설정 주입이 필요한 서비스와 순수 계산 객체의 차이 |
| 왜 없는 행의 FOR UPDATE만 믿지 않는가? | 동시 신규 PK 경합과 패자의 새 트랜잭션 복구 |
| 왜 재수신을 원본 UPDATE로 처리하지 않는가? | 최초 사실/감사·충돌 진단·불완전 최종성 |
| 왜 SERIALIZABLE로 전부 바꾸지 않는가? | 불변식별 잠금/제약, 직렬화 재시도·대기 비용을 포함한 검토 |
| 왜 DB와 MQTT 성공을 나누는가? | 로컬 트랜잭션 한계·확인 유실·같은 목록 재수신 |
| 왜 단순 JWT가 아닌 메모리 opaque 토큰인가? | 즉시 폐기/단순성, 재시작/다중 인스턴스 제약 |
| 현재 가장 먼저 측정할 개선 후보는? | 목록 추가 쿼리·예외 분류·관측·자동 아키텍처 검사 중 근거 있는 하나 |

대안의 존재를 곧바로 변경 지시로 읽지 않는다. 예를 들어 outbox·포트 인터페이스·분산 세션 저장소는 적용 문제/검증/운영 비용을 먼저 정의해야 한다.

## 10. 관측 기록과 완료 기준

개인 비밀 없이 아래 양식으로 기록한다. 테스트 fixture 데이터와 실제 운영 데이터를 섞지 않는다.

```text
대상 기능/코드 버전:
입력/불변식:
실행 전 예측:
읽은 클래스·메서드:
실행 명령/환경:
새 실행 여부/종료 코드/테스트·실패·오류·건너뜀:
관측한 HTTP·DB·스레드·메시지 사실:
예측과 다른 점/원인:
선택의 이유/대안/한계:
아직 확인하지 않은 것:
```

설명 수준은 단계적으로 확인한다: **읽음 → 입력별 결과 예측 → 소스에서 근거 찾기 → 로컬 재현 → 다른 실패 상황으로 확장 설명**. 문서 완독이나 테스트 초록 표시만으로 마지막 단계가 완료되는 것은 아니다.

품질 검증도 구분한다. 순수 테스트는 값/업무 규칙, 서비스 mock은 조율, PostgreSQL 통합은 제약/잠금/커밋, 브로커 종단은 전송/복구를 다룬다. 자동 backend Lint·아키텍처 검사는 현재 MISSING, 실제 장비·LAN/TLS·센서 정확도는 별도 인수다. 어떤 근거도 실행하지 않은 범위까지 확대하지 않는다.
