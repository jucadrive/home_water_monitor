# Hydroponic Nutrient Monitor MVP

2026-10-26 사용자 승인. 전환 배경은 ../MIGRATION.md, 실행·검증 상태는 tasks/HYDROPONIC_MIGRATION.md와 HANDOFF.md를 따른다.

## 목적·경계
- 하나의 재배 활동은 독립적으로 관리하는 영양액 용기 하나의 재배 기간이다. 식물명은 문자열이다.
- Kotlin·Jetpack Compose 안드로이드 앱에서 재배·관리·TDS 추세를 조회한다. 웹사이트는 만들지 않는다.
- TDS는 영양소별 농도·식물 건강·안전성·정확한 교체 시점을 확정하지 않는다.
- SEN0244 0~1000ppm, 미교정·25°C 가정은 현재 제약이다. 교정·독립 정확도·실수온·안정화 충분성은 NOT VERIFIED.
- 자동 투입·임계값·알림·식물 마스터·다중 공유 탱크·신규 센서·운영 배포는 범위 밖이다.

## 재배·관리 규칙
- Cultivation: UUID, plantName(1~100자), name(1~100자), startedAt, endedAt, ACTIVE/ENDED, memo(최대2000자), createdAt/updatedAt.
- 이름은 앞뒤 공백 제거, 메모 null은 빈 문자열로 정규화한다. 시작 시각은 미래 불가다. 시각은 UTC Instant로 저장하고 앱에서 로컬 표시한다.
- 종료 시각은 서버 현재 시각이며 재종료는 기존 결과를 반환한다. 종료 후 신규 START·관리 등록을 거절하고 조회는 허용한다. 재개·수정·삭제는 MVP 제외다.
- 종료 전 커밋된 START 예약은 실행·지연 결과 수신을 허용한다. RETRY는 원본 재전송이며 대상 변경·새 측정이 아니다.
- WaterMaintenance: UUID, cultivationId, REPLACE/TOP_UP/NUTRIENT_ADD, replacementScope(FULL/PARTIAL), occurredAt, amountMl, memo, createdAt.
- scope는 REPLACE에 필수, 다른 유형은 null이다. amountMl은 null(미상) 또는 양수·소수2자리 이하이며 액상 ml다. 분말의 질량을 환산하지 않는다.
- 작업 시각은 재배 시작 이상·현재 이하. 생성 시각과 별개다. 종료 재배에는 과거 작업을 소급 등록하지 않는다.
- 마지막 전체 교체일은 REPLACE/FULL의 최대 occurredAt이다. 부분 교체·보충·추가는 이를 갱신하지 않는다. 없으면 null이다.
- 등록은 Idempotency-Key(UUIDv4)를 요구한다. 같은 키·정규화 내용은 기존 결과, 다른 내용은409다. 종료와 등록은 재배 행 잠금으로 직렬화한다.

## 측정 연결·호환성
- V6 Cultivation, V7 WaterMaintenance, V8 START 명령에 nullable cultivation_id FK와 조회 인덱스를 추가한다. V1~V5는 수정하지 않는다.
- 재배 대상 검증·잠금과 START 예약 저장은 같은 트랜잭션이다. 커밋 뒤 기존 런타임이 MQTT를 발행한다. 예약으로 빈 측정 세션을 생성하지 않는다.
- 세션은 START 명령의 sessionId와 deviceId로 조회 연결한다. 최초 수신도 알려진 예약 장치와 대조한다. 기존 미연결 세션은 그대로 수신·조회한다.
- START 멱등 비교에 cultivationId를 포함한다. 기존 API의 null 대상과 새 대상도 구분한다. RETRY는 기존 대상을 바꾸지 않는다.
- 연결의 근거인 START 명령을 재배 이력과 함께 보존한다. 향후 명령 삭제 정책 도입 시 연결 모델을 재검토한다.
- 원본·통계·중복 비교·불완전 보존·커밋 후 확인·MQTT v1·펌웨어 계약은 유지한다.
- 현재 startedAt=null이다. 조회 시 startedAt, START requestedAt, ingestedAt 순으로 표시 기준을 선택하되 MEASURED/REQUESTED/INGESTED를 함께 반환한다. 실제 측정 시각으로 보정·덮어쓰지 않는다.
- 추세는 NORMAL의 medianTdsPpm만 사용한다. INCOMPLETE는 통계 null 유지, 가상·미교정·알 수 없는 소스를 구분한다. EC는 MVP에서 제공하지 않는다.

## REST·앱
- POST/GET /api/cultivations, GET /api/cultivations/{id}, POST /api/cultivations/{id}/end.
- POST/GET /api/cultivations/{id}/water-maintenances.
- POST /api/cultivations/{id}/measurement-commands: deviceId와 Idempotency-Key로 START만 요청한다.
- GET /api/cultivations/{id}/measurement-sessions, /tds-trend.
- GET /api/measurement-sessions 및 /{id}: 과거 미연결 기록·원본 포함.
- 기존 장치 명령·상태 API는 유지한다. 신규 API 오류는 code/message JSON, 입력400·없음404·종료/멱등충돌409·일시 장애503이다.
- 목록은 limit(기본50, 최대200), offset(0이상)으로 제한한다. 요청 시각·ID 등 명시적 보조 정렬을 사용한다.
- 앱: 재배 목록/생성/상세/종료, 관리 등록·타임라인, 장치 ID 입력·START·상태·결과·추세, 미연결 측정 조회.
- 앱은 명령 접수·보존·장치 해제·신선도를 구분하고 로딩·빈 상태·통신 실패를 표시한다. 변경 요청의 통신 실패 시 동일 키로 재시도한다.
- 앱은 서버 API만 호출한다. 장치·재배 ID·비밀정보를 코드에 고정하지 않는다. 공개 인터넷 노출·보안 정책 완화는 별도 승인이다.

## 의사결정 이력
초기 가정 수질 문제에서 TDS의 해석 한계를 확인했고, 추세 관측 기반을 영양액 관리에 재사용한다. 코드 실패·검증하지 않은 농업 성과로 서술하지 않는다.
2026-10-26 사용자 변경 승인: 정적 웹 UI 제안을 폐기하고 Kotlin·Jetpack Compose 안드로이드 앱을 채택했다. 초기 MIGRATION.md의 UI 기술 제한보다 이 승인을 우선한다.

구현 구체화: 재배·관리 등록 Idempotency-Key는 각 리소스 UUID를 겸한다(테이블별 키 공간). 재배 status는 endedAt에서 파생한다. PostgreSQL 마이크로초 정밀도로 입력·응답 일관성을 유지한다.
