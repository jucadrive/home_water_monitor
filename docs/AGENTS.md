# 개발 운영 문서 안내

2026-10-21부터 사용자 직접 작성 중심의 페어 프로그래밍을 종료하고,
Harness Engineering 기반 단일 자율 에이전트 방식으로 전환했다.

- 항상 적용할 규칙: [루트 AGENTS.md](../AGENTS.md)
- 전체 실행·승인 정책: [Harness.md](Harness.md)
- 최신 구현·검증 상태: [HANDOFF.md](HANDOFF.md)
- 업무 설계 합의: [BACKEND_DESIGN_SUMMARY.md](BACKEND_DESIGN_SUMMARY.md)
- 현재 제품 명세: [HYDROPONIC_MVP.md](HYDROPONIC_MVP.md), 승인된 실행 범위: [tasks/HYDROPONIC_MIGRATION.md](tasks/HYDROPONIC_MIGRATION.md)
- 구축 명세: [tasks/HARNESS_BOOTSTRAP.md](tasks/HARNESS_BOOTSTRAP.md)

사용자는 목표·제약·주요 설계를 승인하고 결과를 검수한다.
AI는 승인된 계획 안에서 단일 에이전트로 구현·검증·기록을 책임진다.
기존 업무 정책은 유지하며 과거 학습 모드를 현재 실행 지시로 적용하지 않는다.
