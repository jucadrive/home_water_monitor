# MQTT 5.0 세션 메시지 계약 v1

상태: 계약 오프라인 검증 VERIFIED. 실제 서버 연동 구현·검증 상태는 [연동 명세](tasks/MQTT_INTEGRATION.md)를 따른다.
승인 범위/실행 기록: [작업 명세](tasks/MQTT_MESSAGE_CONTRACT.md).
기준 소스 main 810ed9a. MQTT 프로토콜 버전은 **5.0**, JSON 계약 버전은 **1**이다.
이 문서는 이전 문서의 토픽·JSON 후보 중 아래 두 메시지에 대한 최신 계약이다.

## 1. 범위와 전송 설정

| 항목 | v1 규칙 |
|---|---|
| 프로토콜 | MQTT 5.0. 3.1.1로 자동 하향 전환하지 않음 |
| QoS | 발행/구독의 목표 QoS 1. 중복 수신 가능성을 업무 계층에서 처리 |
| RETAIN | 두 메시지 모두 발행 시 false |
| MQTT 5 Content Type | 정확히 `application/json` |
| MQTT 5 Payload Format Indicator | 1 (UTF-8) |
| payload | UTF-8 JSON object, BOM·주석·후행 쉼표·중복 속성 키 없음 |
| payload 크기 | 1~16,384 UTF-8 bytes. 공백/줄바꿈 포함. MQTT 프레임·토픽·속성 오버헤드는 별도 |

QoS 1의 PUBACK은 해당 MQTT 전달 구간의 확인이다. DB 커밋 후 발행하는 SESSION_PRESERVED와 구분한다.
MQTT 5의 위 두 속성 및 프로토콜은 JSON payload 안에 중복 필드로 넣지 않는다.
[MQTT 5.0 규격](https://docs.oasis-open.org/mqtt/mqtt/v5.0/os/mqtt-v5.0-os.html).

Response Topic/Correlation Data는 이번 계약의 필수 속성이 아니며 수신 값으로 응답 경로나 업무 키를 바꾸지 않는다.
보존 확인은 아래 고정 토픽으로 보낸다. User Properties도 업무 식별/보존 여부의 근거가 아니다.
연동 계획 승인으로 Expiry·Clean Start·클라이언트 식별/재연결·구독 유지·브로커 ACK·실패 정책을 구체화했다.
정확한 값과 복구 한계는 [서버 연동 안내](MQTT_INTEGRATION.md)의 처리와 실패 정책을 따른다.

## 2. 토픽·장치 식별

| 방향 | 토픽 | eventType |
|---|---|---|
| 장치 → 서버 | `home/water/v1/{deviceId}/events` | `SESSION_RESULT` |
| 서버 → 장치 | `home/water/v1/{deviceId}/preservation` | `SESSION_PRESERVED` |

- deviceId는 `[A-Za-z0-9][A-Za-z0-9_-]{0,63}`이며 대소문자를 구분한다. `/`, `+`, `#`는 허용하지 않는다.
- 토픽의 deviceId, 본문의 deviceId, 허용 장치 설정이 일치해야 한다. topic v1과 schemaVersion=1도 일치한다.
- 이 일치 검사는 MQTT 인증/ACL의 대체가 아니다. 브로커 권한/계정과 장치의 연결은 실제 연동에서 확인한다.
- 장치는 자신의 보존 확인 토픽만 대상으로 처리한다. commands/START/수동 재전송/상태 보고는 이번 범위 밖이다.
- MQTT packet identifier/DUP 플래그로 업무 중복을 제거하지 않는다. 성공한 재수신 진단을 현재 서비스 규칙대로 남긴다.

## 3. SESSION_RESULT

[JSON Schema](mqtt/v1/session-result.schema.json). 모든 속성을 필수로 하며 알 수 없는 속성을 거절한다.

| 필드 | 규칙 |
|---|---|
| schemaVersion | 정수값 1 |
| eventType | `SESSION_RESULT` |
| deviceId | 위 장치 규칙 |
| sessionId | 하이픈 포함 8-4-4-4-12 형식 UUID. 출력 소문자 권장, 비교는 UUID 값 기준 |
| startedAt | UTC `YYYY-MM-DDTHH:mm:ss[.소수초]Z` 또는 명시적 null. 소수초 1~9자리, 달력상 유효한 0001~9999년, 초 00~59 |
| expectedSamples | 정수값 60 |
| samplingIntervalMs | 정수값 1000 |
| temperatureAssumedC | JSON number, -1000 초과/1000 미만, 실질 소수 둘째 자리까지 |
| calibrationVersion | 비공백 문자를 포함하고 제어문자가 없는 문자열, 최대 64 UTF-16 코드 단위 |
| measurements | 0~60개 원본 배열. null 불가 |

원본의 모든 속성도 필수다.

| 원본 필드 | 규칙 |
|---|---|
| seq | 중복 없는 정수값 1~60. 배열 순서에 의미를 부여하지 않음 |
| elapsedMs | 정수값 0~2,147,483,647. seq*1000과 같다고 강제하거나 보정하지 않음 |
| tdsPpm | JSON number, -10,000,000 초과/10,000,000 미만, 실질 소수 둘째 자리까지 |
| isWarmup | JSON boolean. 생략/null/문자열 불가 |
| rawVoltageMv | tdsPpm과 같은 수치 표현 규칙 또는 명시적 null |

수치는 문자열/NaN/Infinity를 허용하지 않는다. `151.3`, `151.30`, `151.300`은 같은 수치다.
정수값 필드도 소수 부분을 잘라 수락하지 않는다. 숫자는 JSON 숫자 값에 따라 검증하며 이진 Double을 거쳐 반올림하지 않는다.
위 수치 범위는 기존 저장 표현과 맞춘 것이며 물리적 센서 유효성/수질 안전성 판정 기준이 아니다.
startedAt이 null이면 모르는 측정 시작 시각이다. 서버 수신 시각으로 채우거나 재전송 중 시각을 새로 만들지 않는다.

신규 목록은 60개면 기존 NORMAL 경로, 0~59개면 INCOMPLETE 경로다. 60개 전부 안정화이면 기존 조건대로 거절한다.
안정화 원본도 모두 보존한다. 불완전 결과는 정상 대표 통계를 계산하지 않으며 누락값을 만들지 않는다.
잘못된 원본을 제거/중복 제거해 일부만 저장하지 않는다.

sessionId는 한 번의 측정에 하나이며 재전송에서 메타데이터·원본 목록을 유지한다. 발급/전달은 START 계약에서 정한다.
기존 세션과 같은 sessionId+seq는 기존 네 필드 비교 정책을 적용한다. INCOMPLETE의 순번 추가/삭제/교체·승격은 거절한다.
전체 원본 60개 예제와 빈/불완전 예제를 제공하며 `...` 같은 생략 표기는 유효 payload로 사용하지 않는다.

## 4. SESSION_PRESERVED

[JSON Schema](mqtt/v1/session-preserved.schema.json). 모든 속성을 필수로 하며 알 수 없는 속성을 거절한다.

| 필드 | 규칙 |
|---|---|
| schemaVersion | 정수값 1 |
| eventType | `SESSION_PRESERVED` |
| deviceId/sessionId | 원래 세션 식별. 위 장치/UUID 규칙 |
| preservationResult | `NORMAL` 또는 `INCOMPLETE` |
| preservedSampleCount | NORMAL은 60, INCOMPLETE는 0~59 |
| comparison | 최초 보존이면 null. 재수신 처리이면 아래 두 건수를 가진 object |
| comparison.identicalCount | 정수값 0~60 |
| comparison.conflictCount | 정수값 0~60 |

비교 건수 합계는 preservedSampleCount와 같아야 한다. 빈 목록 재수신은 object의 두 건수가 모두 0이다.
comparison=null은 원본과 비교하지 않은 최초 보존이다. 이를 동일 재수신 0건으로 표시하지 않는다.
conflictCount>0도 기존 원본/보존 결과를 유지하고 이번 진단을 커밋한 보존 처리 완료이며, 수신값으로 원본을 덮어썼다는 의미가 아니다.
INCOMPLETE도 보존 처리 완료지만 정상 측정 성공으로 표시하지 않는다.

해당 호출의 실제 DB 커밋 성공 후에만 이 메시지를 발행한다. 처리 예외/불명 커밋 결과를 이 타입으로 알리지 않는다.
커밋 후 발행 실패/확인 유실 때문에 원본을 지우거나 DB 실패로 바꾸지 않는다. 재수신 성공 후 다시 확인할 수 있다.
부정 입력/저장 실패에는 업무 응답을 발행하지 않는다. MQTT ACK와 장치 재전송의 관계는 연동 안내를 따른다.

## 5. 장치가 확인을 수락하는 조건

1. MQTT/토픽·schemaVersion/eventType·필수 필드가 유효해야 한다.
2. 보관 중인 버퍼의 deviceId와 UUID가 일치해야 한다.
3. preservedSampleCount가 버퍼의 원본 개수와 같고, 보존 결과/비교 건수도 일관돼야 한다.
4. 조건을 충족하면 확인 대기/백오프/사용자 대기 중에도 해당 버퍼를 해제한다. 이미 처리한 확인은 중복 해제하지 않는다.

다른 세션/장치·잘못된 확인·버퍼가 없는 상태의 확인은 버퍼 해제 근거가 아니다.
같은 세션의 버퍼 내용 불변은 장치 계약이다. 확인에는 원문 해시가 없으므로 UUID/건수만으로 임의의 내용 변경을 검출한다고 주장하지 않는다.
서버가 확인을 발행했다는 사실로 장치의 실제 수신/버퍼 삭제를 확정하지 않는다. 상태 보고는 별도 계약이다.
기존 회차당 5회·PUBACK 후 최대 30초·Full Jitter 합의는 유지한다. 서버 연결 제한은 10초,
확인 발행 PUBACK 대기는 10초이며 실제 펌웨어 재전송/버퍼 삭제는 장비 인수 검증 대상이다.

## 6. 내부 타입 매핑

- 입력 어댑터는 schemaVersion/eventType/expectedSamples/토픽·장치를 먼저 검증한다.
  나머지 필드를 기존 SaveNormalTdsSessionCommand로 변환해 ProcessTdsSessionService.process()를 호출한다.
- startedAt은 nullable Instant, 숫자는 BigDecimal로 변환한다. 현재 DB 시각 정밀도 비교 정책을 유지한다.
- TdsSessionProcessingResult의 sessionId/preservationResult/preservedSampleCount를 확인에 매핑한다.
- SAVED_NEW면 comparison=null, PROCESSED_REDELIVERY면 redeliveryResult의 두 건수를 매핑한다.
  Java Path enum, DB redeliveryId, 수신 시도 횟수는 이 payload에 노출하지 않는다.
- 위 매핑과 커밋 후 발행을 session.infrastructure.mqtt에 구현했다. 검증 증거는 연동 작업 명세를 따른다.

## 7. 예제와 오프라인 검증

examples의 UUID가 다른 normal/incomplete/empty는 서로 독립된 측정 사례다. 센서 실측/실제 교정 자료가 아니다.

| 입력 예제 | 대응 최초 확인 | 재수신 확인 |
|---|---|---|
| [정상 60개](mqtt/v1/examples/session-result-normal.json) | [NORMAL](mqtt/v1/examples/session-preserved-normal.json) | [동일](mqtt/v1/examples/session-preserved-identical.json), [충돌 1개](mqtt/v1/examples/session-preserved-conflict.json) |
| [불완전 1개](mqtt/v1/examples/session-result-incomplete.json) | [INCOMPLETE](mqtt/v1/examples/session-preserved-incomplete.json) | 건수 합계 1의 비교 object |
| [빈 최종 목록](mqtt/v1/examples/session-result-empty.json) | [INCOMPLETE 0](mqtt/v1/examples/session-preserved-empty.json) | [빈 재수신](mqtt/v1/examples/session-preserved-empty-redelivery.json) |

PowerShell 7.5 이상에서 저장소 루트를 기준으로 실행한다. 이번 환경은 7.6.5다.

```powershell
./scripts/verify-mqtt-contract.ps1
```

JSON Schema는 형태/필수값/nullable/숫자 범위/enum을 검사한다. 별도 의미 검사는 seq 중복·정상 통계 대상·달력 시각,
UTF-16 길이·중복 JSON 키·비교 합계·버퍼 상관관계·UTF-8 크기·전달받은 전송 메타데이터를 검사한다.
Schema만으로 실제 커밋/장치 권한/세션 상태/실측값/버퍼 삭제가 보장되지는 않는다.
검사 함수는 오프라인 도구이며 반환 Reason은 MQTT 오류 코드가 아니다. 긍정·부정 사례를 모두 검사하고 불일치 시 종료 코드 1이다.

## 8. 검증 범위와 후속 항목

PowerShell 계약 검사는 기존 Gradle verify.ps1/CI에 연결하지 않았다. Java MQTT 테스트는 Gradle test/build에 포함된다.
실제 브로커·DB·모의 장치 검증은 연동 작업 명세에 기록하며 장비 펌웨어/버퍼 삭제는 NOT VERIFIED다.
RETAIN=false는 발행자 계약이며 모델의 retained=true는 거절한다. 실제 수신 시 플래그 전달과 MQTT 5 구독 옵션은 브로커 연동에서 검증한다.
JSON 스키마 검사로 발행자의 실제 RETAIN 설정이나 인증을 확인했다고 주장하지 않는다.

Spring Integration 7.1.1의 v5 어댑터와 명시적 Paho v5 1.2.5 의존성을 사용한다.
테스트 브로커는 Mosquitto 2.0.22이며 ESP32 SDK·실제 장치 패킷 버퍼는 아직 확인하지 않았다.
[Spring Integration MQTT v5 공식 문서](https://docs.spring.io/spring-integration/reference/mqtt.html#mqtt-v5-support).

연결/발행/PUBACK 제한, Session/Message Expiry, 부정 입력·재연결·커밋 후 발행 실패는 승인된 연동 구현에 포함했다.
START/명령/상태 보고와 실제 장비 인수 검증은 후속 범위다.
