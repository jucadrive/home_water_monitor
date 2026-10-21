# 측정 원본 비교 도메인 구현 계획

상태: DONE — 사용자 승인에 따라 PR #2 리뷰·main 병합 완료 (88682a2). 아래 Phase Result는 구현 당시 기록이다.
작성일: 2026-10-21. 기준: main의 abe4d3c, 작업 트리 깨끗함을 확인한 뒤 계획 작성.

## 목표와 범위
같은 sessionId + seq의 측정 원본 두 개를 순수 Java 도메인에서 비교한다.
기존 정책은 docs/NEXT_MEASUREMENT_COMPARISON.md를 따른다.
신규/재수신 조회·저장 조율은 이번 범위가 아니며 비교 결과에는 NEW를 추가하지 않는다.

## 계획 작성 시 확인한 사실
- measurement.domain에는 TdsStatisticsCalculator만 존재한다.
- 측정 비교 record·결과 enum·단위 테스트는 아직 없다.
- TdsMeasurementEntity는 DB 매핑이며 비교 업무 로직을 넣지 않는다.
- SaveNormalTdsSessionService는 신규 정상 세션 저장 전용이다.
- 기존 Gradle·JUnit 구성과 Unit/Full 검증 진입점을 재사용할 수 있다.

## 설계와 근거
- TdsMeasurement record는 식별자·경과시간·TDS·안정화 여부·선택 전압을 보유한다.
- compareWith는 같은 업무 식별자에서만 IDENTICAL 또는 CONFLICT를 반환한다.
- 네 필드 tdsPpm·elapsedMs·isWarmup·rawVoltageMv를 모두 비교한다.
- BigDecimal.compareTo를 사용해 표현 자릿수를 무시하고 수치를 그대로 비교한다.
- null 전압은 양쪽 null일 때만 동일하다.
- Spring·JPA·Repository에 의존하지 않아 DB 없이 규칙을 검증할 수 있다.
- record의 equals/hashCode는 Java 기본 의미를 유지한다. 업무 비교는 compareWith로만 수행한다.
- DB 소수 자릿수·센서 물리 범위·세션당 60개 제약을 새 도메인에 추가하지 않는다.
  현재 저장 서비스의 제약과 도메인의 내용 비교 책임을 구분한다.

## 인수 조건
1. 같은 키·같은 네 필드는 IDENTICAL. TDS·전압의 소수 표현 자릿수 차이는 허용한다.
2. 같은 키에서 네 필드 중 각각 하나만 바뀌어도 CONFLICT다.
3. 전압 양쪽 null은 IDENTICAL, 한쪽만 null은 방향과 무관하게 CONFLICT다.
4. 다른 sessionId 또는 seq의 비교는 IllegalArgumentException이며 CONFLICT로 분류하지 않는다.
5. null sessionId·tdsPpm, null 비교 대상은 NullPointerException으로 거절한다.
6. seq < 1 또는 elapsedMs < 0은 IllegalArgumentException으로 거절한다.
7. seq=1·elapsedMs=0의 경계값은 허용한다.
8. 151.3과 151.30은 같고, 반올림하면 같아질 151.301과 151.302는 충돌이다.
9. 입력 값을 반올림·정규화·변경하지 않으며 비교 결과 enum은 두 값만 가진다.
10. 기존 통계·정상 저장·롤백 테스트를 포함한 전체 build가 성공한다.

## 변경 파일
Gradle 프로젝트 기준 신규 소스:
- src/main/java/com/juca/homewatermonitorbackend/measurement/domain/TdsMeasurementComparisonResult.java
- src/main/java/com/juca/homewatermonitorbackend/measurement/domain/TdsMeasurement.java
- src/test/java/com/juca/homewatermonitorbackend/measurement/domain/TdsMeasurementTest.java

문서:
- 이 명세: 승인·진행·검증·리스크 기록.
- docs/HANDOFF.md: 실제 구현 상태와 하네스 PR·CI 최신 근거 정리.
- docs/NEXT_MEASUREMENT_COMPARISON.md: 미구현 예시를 실제 소스 참조로 전환.
- docs/tasks/HARNESS_BOOTSTRAP.md·docs/GIT_WORKFLOW.md: 이전 체크포인트의 미확인/승인 대기를
  실제 완료된 PR #1·원격 CI 결과에 맞게 갱신.

## 실행 계획 — 구현 Phase 하나
1. 승인 후 main 기준 codex/measurement-comparison 브랜치 생성.
2. enum·record·JUnit 테스트 구현. 코드에는 목적과 중요한 제약을 짧게 주석으로 남긴다.
3. 정상·필드별 충돌·null·다른 키·생성자 경계·반올림 금지 시나리오 검증.
4. Unit 후 Full 검증, XML의 테스트 수·실패·오류·건너뜀 확인.
5. git diff --check 및 변경 범위·도메인 의존성 자체 리뷰, 문서 기록 후 VERIFIED 보고.

검증 명령(저장소 루트, JDK 25):
```powershell
# 도메인 비교와 기존 통계 단위 테스트 검증
./scripts/verify.ps1 -Mode Unit
# Docker가 실행 중인 상태에서 기존 통합 테스트와 패키징까지 검증
./scripts/verify.ps1 -Mode Full
```

## 영향·승인 경계
- 기존 서비스·엔티티·Repository·통계 계산기·DB·공개 API·MQTT 계약은 변경하지 않는다.
- 새 도메인은 서비스에 연결하지 않으므로 기존 저장 동작은 그대로다.
- 비교 도메인의 검증 성공을 재수신 저장·멱등성 기능 전체 완료로 표현하지 않는다.
- 새 의존성·아키텍처·스키마 결정은 필요 없다. 범위 확대가 필요하면 중단하고 승인받는다.
- 자동 수정 최대 5회, 동일 근본 원인 3회 실패 시 중단한다.
- 구현 승인과 커밋·푸시·PR·병합 승인은 별개다. 검증 후 구체적 대상을 제시해 승인받는다.
- 계획 작성 당시에는 기능 변경·검증을 하지 않았다. 이후 사용자 승인으로 이번 구현을 진행한다.

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
## Git 체크포인트 승인 — 2026-10-21
- 사용자가 의도한 동작 확인을 조건으로 이번 변경의 커밋·푸시를 승인했다.
- 커밋 전 코드 리뷰에서 계획된 비교 정책과의 불일치를 발견하지 못했다.
- 기존 Full 24개 통과 기록과 이후 비교 테스트 13개 성공 XML을 확인했다.
  최신 XML은 비교 클래스 단독 실행 기록이며 전체 스위트 재실행으로 해석하지 않는다.
- 승인 범위: 소스 3개와 문서 5개, codex/measurement-comparison 브랜치 커밋·푸시.
- 커밋 메시지: feat: add measurement content comparison domain.
- 푸시 후 해당 SHA의 원격 CI를 확인한다. PR 생성·main 병합은 이번 승인 범위가 아니다.

최종 원격 근거: PR https://github.com/jucadrive/home_water_monitor/pull/2 , main CI https://github.com/jucadrive/home_water_monitor/actions/runs/39568032613 성공. 이후 재수신 구현은 NORMAL_SESSION_REDELIVERY.md를 따른다.
