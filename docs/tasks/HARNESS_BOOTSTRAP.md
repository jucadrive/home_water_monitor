# Harness 구축 — 2026-10-21

상태: DONE (2026-10-21 사용자 최종 리뷰·승인 완료). 사용자 승인: 2026-10-21 대화의 최소 구축 계획 1~5항.

## 목적과 승인 범위
사용자 Architect & Verifier, AI 단일 자율 에이전트로 운영을 전환한다.
루트 AGENTS.md 교체, 기존 문서 정리, HANDOFF 재사용, 작업 명세 도입,
JDK 25 실행 확인, 컨텍스트 테스트 DB 격리, 검증 스크립트와 GitHub Actions를 구축한다.
새 Lint·정적 분석 의존성은 추가하지 않고 MISSING으로 공개한다.
기능 구현·DB 마이그레이션·공개 계약 변경·커밋·푸시는 범위 밖이다.

## 인수 조건
1. AGENTS.md가 250줄 이하이며 단일 에이전트·승인·검증·완료 규칙을 안내한다.
2. 기존 문서가 과거 직접 작성 지침을 현재 지시로 사용하지 않도록 정리된다.
3. Java 25와 Gradle Wrapper로 도메인 단위 테스트를 실행할 수 있다.
4. 전체 테스트가 개발 DB 설정 없이 Testcontainers에서 통과한다.
5. verify.ps1이 Unit/Full을 지원하고 Gradle 실패 종료 코드를 전파한다.
6. CI가 JDK 25·Full 검증·테스트 증거 업로드를 설정한다.
7. 실행하지 못한 검사와 로컬·원격 검증을 구분하여 HANDOFF에 남긴다.

## 순차 Phase와 변경 파일
- Phase 1: AGENTS.md, docs/AGENTS.md, HANDOFF, 상세 명세, 설계 요약,
  NEXT_MEASUREMENT_COMPARISON, README, 이 명세의 전환 정리.
- Phase 2: HomeWaterMonitorBackendApplicationTests의 테스트 DB 격리와 scripts/verify.ps1.
- Phase 3: .github/workflows/verify.yml 및 최종 검증·기록.

## 설계·검증·위험
- build.gradle은 이미 Java 25 toolchain이다. JDK 경로를 저장소에 하드코딩하지 않는다.
- 기존 Testcontainers 패턴을 재사용한다. 도메인·서비스·V1/V2는 변경하지 않는다.
- PowerShell 구문 검사, Unit, Full, git diff --check, 최종 diff 검토를 수행한다.
- Docker가 없으면 전체 검증은 BLOCKED이며 테스트를 건너뛰어 성공 처리하지 않는다.
- GitHub Actions 원격 실행·브랜치 보호는 로컬 파일 작성만으로 검증되지 않는다.
- 사용자 docs/Harness.md의 staged/unstaged 상태를 보존한다.

## Phase Result
최신 실행 증거·환경 제한·다음 조치는 ../HANDOFF.md에 기록한다.
커밋 초안: chore: establish single-agent harness and isolated verification

검증 결과: Unit 7개·Full 전체 11개 성공, 실패/오류/건너뜀 0, build 성공, 스크립트 구문·diff 검사 통과. 실패 종료 코드 1 전파도 최초 캐시 접근 거부에서 확인했다. 이 기록 이후 원격 Linux CI도 성공했다. PR #1과 main 병합 커밋 abe4d3c, main Verify 실행 39563945349로 확인했다. 브랜치 보호는 설정하지 않았다.

후속 승인: 사용자가 하네스 변경 커밋·브랜치 전략 추가를 명시 승인했다. docs/GIT_WORKFLOW.md를 적용하며 푸시·PR·병합은 별도 승인받는다.

후속 완료: 사용자가 푸시·PR 생성·리뷰 후 병합을 승인했고 PR #1을 main에 병합했다. 로컬 main도 abe4d3c로 갱신했다. 새 기능은 별도 작업 명세와 승인으로 진행한다.
