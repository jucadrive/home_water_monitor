# 측정 원본 비교 도메인

2026-10-21: 기존 미작성 예시를 승인된 계획에 따라 실제 소스로 구현했다.
현재 검증·승인 상태는 [작업 명세](tasks/MEASUREMENT_COMPARISON.md)와 [HANDOFF](HANDOFF.md)를 따른다.

## 구현 소스
- [비교 결과 enum](../backend/home-water-monitor-backend/src/main/java/com/juca/homewatermonitorbackend/measurement/domain/TdsMeasurementComparisonResult.java)
- [측정 도메인 record](../backend/home-water-monitor-backend/src/main/java/com/juca/homewatermonitorbackend/measurement/domain/TdsMeasurement.java)
- [비교 단위 테스트](../backend/home-water-monitor-backend/src/test/java/com/juca/homewatermonitorbackend/measurement/domain/TdsMeasurementTest.java)

문서에 전체 코드를 중복 보관하지 않고 위 실제 소스를 기준으로 관리한다.

## 확정된 정책

> 2026-10-28 후속 승인: [온도 보정 준비](tasks/TEMPERATURE_COMPENSATION.md)의 v2 원본은 아래 네 필드에 temperatureC·temperatureElapsedMs를 추가 비교한다. 기존 v1은 양쪽 추가 필드가 null인 동일한 네 필드 판정을 유지한다. 실제 구현·검증 상태는 HANDOFF를 따른다.
- 같은 sessionId + seq에서 tdsPpm·elapsedMs·isWarmup·rawVoltageMv를 모두 비교한다.
- 모두 같으면 IDENTICAL, 하나라도 다르면 CONFLICT다. 신규 여부는 서비스 조회의 책임이다.
- BigDecimal.compareTo로 표현 자릿수를 무시하고 반올림·허용 오차 없이 비교한다.
- 전압 양쪽 null은 동일, 한쪽만 null이면 충돌이다.
- 다른 sessionId·seq는 IllegalArgumentException으로 거절한다.
- 필수 sessionId·tdsPpm 또는 비교 대상 null은 NullPointerException으로 거절한다.
- seq는 1 이상, elapsedMs는 0 이상이다. DB 저장 정밀도·물리 범위를 추가 제한하지 않는다.
- record의 equals/hashCode 대신 업무 비교 메서드 compareWith를 사용한다.

## 검증과 후속 작업
저장소 루트에서 실행한다.

```powershell
# 측정 비교와 통계 도메인을 DB 없이 검증
./scripts/verify.ps1 -Mode Unit
# Docker 실행 후 전체 테스트·빌드 검증
./scripts/verify.ps1 -Mode Full
```

이 도메인은 정상·불완전 세션 재수신 저장 서비스에 연결돼 있다. 실제 구현은 session.application의 재수신 서비스와 관련 테스트를 따른다. 원본 보존·목록 원자성·커밋 후 확인 정책을 유지한다. 다음 제품 작업은 [수경재배 전환](tasks/HYDROPONIC_MIGRATION.md)이다.
