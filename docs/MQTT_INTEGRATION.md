# MQTT 5.0 서버 연동 실행 안내

범위/검증 기록: [작업 명세](tasks/MQTT_INTEGRATION.md). 메시지 형식: [계약](MQTT_MESSAGE_CONTRACT.md).
서버 연동은 기존 application 저장 서비스를 호출하며 펌웨어·START/명령·상태 보고는 포함하지 않는다.

## 설정

기본값은 `water-monitor.mqtt.enabled=false`다. 활성화하지 않으면 브로커에 연결하지 않는다.
Spring 외부 설정 또는 아래 환경변수로 활성화한다. 실제 비밀번호는 저장소/로그에 기록하지 않는다.

| 환경변수 | 용도 |
|---|---|
| MQTT_ENABLED | true로 설정하면 연동 활성화 |
| MQTT_BROKER_URI | 필수. tcp://호스트:포트 또는 ssl://호스트:포트. URI에 계정을 넣지 않음 |
| MQTT_INBOUND_CLIENT_ID | 필수. 서버 인스턴스마다 고유하고 재시작 간 유지되는 수신 ID |
| MQTT_OUTBOUND_CLIENT_ID | 필수. 수신 ID와 다른 고유 발행 ID |
| MQTT_ALLOWED_DEVICE_IDS | 필수. 쉼표로 구분한 허용 장치 ID |
| MQTT_USERNAME / MQTT_PASSWORD | 브로커 자격 증명. 비밀 관리 수단을 통해 주입 |

장치/클라이언트 ID는 `[A-Za-z0-9][A-Za-z0-9_-]{0,63}`을 따른다. 중복 서버 ID는 연결을 끊을 수 있으므로
각 인스턴스에서 서로 달라야 한다. ssl은 JVM 신뢰 저장소와 호스트 이름 검증을 사용하며 이를 끄지 않는다.
운영 브로커 계정·TLS·ACL 구축/배포는 이번 구현 범위에 포함하지 않았다.
DB 설정은 기존 방식이며 서버를 실행하면 미적용 Flyway 마이그레이션이 실행될 수 있다.

## 처리와 실패 정책

- MQTT 프로토콜은 Paho v5 1.2.5 및 Spring Integration 7.1.1 어댑터로 고정한다.
- 장치별 정확한 events 토픽만 QoS 1 구독하고 원본 byte 크기·UTF-8·JSON·속성·업무 입력을 검증한다.
- 수신 구독은 Retain As Published=true, Retain Handling=2다. 저장된 retained 메시지는 요청하지 않고
  새 retained 발행도 수신 플래그를 보존하여 거절한다. 실제 RETAIN/PFI/Content Type은 브로커 테스트로 검증한다.
- 수신은 Clean Start=false, Session Expiry 86400초. 발행 연결은 Clean Start=true, Expiry=0이다.
  두 클라이언트 모두 keepalive 30초, 연결 제한 10초, 재연결 대기 최대 16초다.
- Paho 1.2.5 소스의 실제 재연결 루프는 setMaxReconnectDelay를 읽는다. 1초에서 두 배씩 늘어
  1/2/4/8/16초 대기를 사용한다. 두 클라이언트의 지연 상태가 라이브러리 내부 static 필드를 공유하므로
  각각이 독립된 순서대로 대기한다고 보장하지 않는다. 연결 시도 자체의 제한은 별도 10초다.
- 수신 Receive Maximum=1, DirectChannel 직렬 처리로 애플리케이션 작업 큐를 만들지 않는다.
  브로커의 대기 큐 용량/지속성은 브로커 운영 정책이다. 패킷 수신 상한은 65536 bytes,
  JSON 본문은 별도로 16384 bytes 제한을 적용한다.
- ProcessTdsSessionService의 트랜잭션 프록시 호출이 실제 커밋을 마친 후에만 보존 확인을 발행한다.
  신규 세션 PK 충돌 1회 복구·기존 원본/불완전 확정 규칙은 유지한다.
- 발행 연결이 오프라인이면 즉시 실패한다. 온라인 발행의 PUBACK 대기는 최대 10초다.
  시간 초과가 이미 보낸 패킷을 취소하는 것은 아니므로 확인이 뒤늦게 전달될 수도 있다.
- 입력/저장/발행 실패에 보존 확인은 보내지 않는다. 수신 MQTT ACK는 실패에도 수행해 독성 메시지의
  무한 재전달을 막는다. MQTT ACK는 업무 보존 확인이 아니며 장치는 버퍼를 삭제하면 안 된다.
- 커밋 후 발행 실패에도 원본은 유지한다. 장치가 같은 목록을 재전송하면 재수신 진단 커밋 후 다시 확인한다.
  서버 outbox/별도 업무 재시도 큐는 없다. 서버 종료 전 미ACK 메시지는 브로커가 재전달할 수 있다.
- 지연/중복 확인은 계약의 장치 버퍼 상관관계로 처리한다. 확인 유실 시 장치의 회차당 5회,
  PUBACK 후 최대 30초·Full Jitter 재전송 합의를 따른다. 펌웨어 자체는 이번에 구현하지 않았다.
- 종료 순서는 수신 → 발행 어댑터 → 발행 연결이다. 수신 중지 시 세션 구독을 유지하고 재시작하면
  해당 세션의 대기 메시지를 처리한다. 세션 만료/브로커 저장소 유실 이후는 장치 원본 재전송이 필요하다.
- 실패 로그는 INPUT/STORAGE/PUBLISH 및 연결/프로토콜/ACK 범주를 남긴다. MQTT 원문과 예외 원문은
  수신기에서 출력하지 않는다. 전체 Spring/Hibernate 진단의 민감정보 관리는 운영 로깅 정책의 별도 대상이다.

## 검증

Docker 엔진과 JDK 25를 준비하고 저장소 루트에서 실행한다. 개발 DB나 실제 장치가 필요하지 않다.

```powershell
./scripts/verify-mqtt-contract.ps1
./scripts/verify.ps1 -Mode Full
```

통합 테스트는 Mosquitto 2.0.22와 PostgreSQL 18의 일회성 컨테이너를 사용한다.
익명 접속은 무작위 호스트 포트의 테스트 브로커에만 사용하며 운영 설정을 바꾸지 않는다.
브로커 재시작 검증은 Docker의 무작위 호스트 포트를 유지하기 위해 컨테이너 안의 Mosquitto 프로세스를 재시작한다.
실제 장비 SDK의 MQTT 5 지원·패킷 버퍼·재전송·버퍼 삭제는 장비 인수 검증 대상으로 남는다.
