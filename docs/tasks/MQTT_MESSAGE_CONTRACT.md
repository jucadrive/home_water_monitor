# MQTT 5.0 메시지 계약

상태: DONE — 2026-10-22 실제 연동과 함께 사용자 최종 리뷰 승인. 같은 날 MQTT 5.0 기준 계약 진행을 승인받았다.
범위 선택: 최종 목록·보존 확인 우선. 기준 main 810ed9a, 시작 전 변경 없음.
브랜치: codex/mqtt-message-contract. 단일 에이전트로 하나의 문서/계약 검증 Phase를 수행한다.

## 목표와 승인 범위
- SESSION_RESULT 및 SESSION_PRESERVED의 토픽·필드·의미·버전·크기·전송 설정을 정의한다.
- MQTT 5.0, QoS 1, retain=false, UTF-8 JSON, payload 최대 16 KiB를 기준으로 한다.
- 기존 세션/샘플 식별·동일/충돌·정상/불완전 보존 정책을 유지한다.
- 계약 문서, JSON Schema 두 개, 정상/불완전/빈 목록 및 확인 JSON 예제를 작성한다.
- 기존 PowerShell Test-Json 및 추가 의미 검증으로 긍정/부정/경계 사례를 검증한다.
- Java 서비스·DB·Gradle/CI·의존성·브로커·펌웨어·실제 MQTT 어댑터는 변경하지 않는다.
- START·수동 재전송·명령 결과·상태 보고, 오류 응답·연결/발행 재시도는 후속 계약이다.
- sessionId 발급/전달은 START 계약에서 확정한다. 이번에는 UUID 형식과 재전송 시 불변을 정의한다.

## 변경 파일
- docs/MQTT_MESSAGE_CONTRACT.md: 외부 메시지 계약 및 기존 내부 타입 매핑.
- docs/mqtt/v1/*.schema.json, examples/*.json: 독립 payload 스키마 및 유효 예제.
- scripts/mqtt-contract/Test-MqttContract.ps1: 오프라인 계약 검사 함수.
- scripts/verify-mqtt-contract.ps1: 예제·경계·부정 사례 검증과 집계/종료 코드.
- 이 명세, HANDOFF, BACKEND_DESIGN_SUMMARY: 승인/진행/검증 결과와 다음 작업 기록.

## 인수 조건
- 두 payload의 필수/nullable/타입/버전/enum/알 수 없는 필드를 검증한다.
- 입력 목록 0~60개·중복 seq·범위·수치 정밀도/범위·정상 60개 통계 대상 필수 조건 확인.
- 토픽/본문/허용 장치 일치, MQTT 5.0·QoS/retain·Content Type/Payload Format Indicator 규칙 확인.
- 확인의 NORMAL=60/INCOMPLETE=0~59·비교 건수 합계·보관 버퍼 장치/UUID/건수 상관관계 확인.
- UTF-8 byte 크기 16384 이하, 중복 JSON 키·잘못된 JSON·잘못된 시각 거절.
- 실제 MQTT 없이도 예제/스키마/의미 검증을 구분하고 성공/실패 개수와 종료 코드를 기록한다.
- Java/DB 동작 변경이 없으므로 기존 로컬 Full 97개 및 main CI 성공 결과를 재사용한다.
  이번 Gradle Full/브로커·장치 통신은 NOT RUN. 계약 검사 성공을 실제 MQTT 연동 성공으로 표현하지 않는다.

## 검증과 승인 경계
- PowerShell 7.6.5 및 내장 Test-Json의 JSON Schema 2020-12 처리를 사전 확인했다.
- 정적 구문 검사 → ./scripts/verify-mqtt-contract.ps1 → git status/diff/변경 범위 검토.
- 자동 수정 최대 5회, 동일 근본 원인 3회 반복 시 중단. 검사 약화로 실패를 숨기지 않는다.
- 프로토콜 3.1.1 제안은 사용자 선택으로 철회했다. 코드/스키마 버전과 MQTT 프로토콜 버전을 구분한다.
- 커밋·푸시·PR·병합은 별도 승인 대상이며 현재 기능에는 아직 승인되지 않았다.

## 수정 기록
- 최초 실행은 검사 함수의 다중 행 if 식에서 줄 첫머리에 둔 연산자 때문에 구문 분석 실패 (종료 코드 1).
- 자동 수정 1회: 연산자를 앞줄 끝으로 옮겼다. 정책/스키마/예상 판정 변경 없음.
- 구문 재검사 후 계약 검사 108개 PASS, 실패/건너뜀 0, 종료 코드 0.
- 동일 근본 원인 반복 실패 1회. 실제 Java/MQTT 동작 변경 없음.

## Phase Result
- 구현: MQTT 5.0 두 메시지 계약, JSON Schema 2개, 예제 9개 및 오프라인 검증 함수/runner.
- 변경: 계약·작업 명세·설계 요약·HANDOFF, schema/example 11개, PowerShell 2개. 총 17개 파일.
- 검증: 두 PowerShell 파일 AST 구문 검사 PASS. ./scripts/verify-mqtt-contract.ps1 종료 코드 0,
  실제 실행 108개 PASS·실패 0·건너뜀 0. 최대 허용 16,384 bytes 및 16,385 bytes 거절 확인.
- 정상 60개 예제는 UTF-8 8,666 bytes. 한글을 포함한 byte 경계·nullable·재수신 비교/버퍼 문맥도 검증했다.
- 인수 조건: 위 오프라인 계약 조건 충족. Java/DB 변경 없음. 기존 Full 97개는 이전 실행 결과다.
- 이번 Gradle/실제 MQTT/원격 CI는 NOT RUN. 새 검증 스크립트는 기존 CI에 연결하지 않았다.
- Git: codex/mqtt-message-contract, staged 없음. 커밋·푸시·PR·병합 없음. 최종 diff/범위 점검 PASS.
- 위험: 실제 MQTT 속성/구독 옵션·SDK 지원·커밋 후 발행·버퍼 삭제는 연동에서 검증해야 한다.
  연결·발행 실패/타임아웃/재시도·Expiry는 다음 계약 범위다. 자동 아키텍처 검사 MISSING 유지.
- 커밋 초안: docs: define MQTT 5 session result and preservation contracts
- 다음: 사용자 최종 리뷰. 이후 연동 실패 처리 계획을 별도 수립한다.
