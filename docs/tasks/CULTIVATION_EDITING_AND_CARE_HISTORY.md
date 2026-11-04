# 재배 정보·관리 기록 수정 및 돌봄 이력 정리

## 상태·승인 경계 — 2026-10-30

- 문서·계약 정리 VERIFIED. 사용자는 앞선 검토 계획에 이름 변경을 추가하여 문서와 계약을 확정하도록 지시했다. 문서4개 링크 검사(깨진 링크0), git diff --check 통과. 제품 코드 테스트는 NOT RUN이며 구현 검증을 의미하지 않는다.
- 2026-10-30 사용자가 전체 계약을 확정하고 구현 시작을 승인했다. 구현·로컬 인수 VERIFIED. 사용자 최종 리뷰/운영 배포·실기기 새 APK 인수 전 전체 DONE은 아니다. 운영 데이터는 변경하지 않았다.
- 기존 구조/인증/미확정 요청 보호 유지. 서버 배포는 사용자 수동이며 SSH·장비 조작·새 의존성·Git 커밋/푸시/병합은 이번 승인에 포함하지 않는다.

## 착수 전 실제 코드에서 확인한 상태

- CultivationController는 등록/조회/종료만, WaterMaintenanceController는 등록/목록만 제공한다. 수정 API는 없다.
- 앱 Forms의 식물명은 plantName, 재배 별명은 name이다. 상세 큰 제목은 name, 작물 표시는 plantName이다. 두 필드 모두100자, 메모2000자다.
- 상세는 측정50건/관리50건을 읽어 LazyColumn에 섞는다. DB 전체 무제한 조회는 아니지만 매시간 측정이 돌봄 기록을 밀어낸다. 더 보기로 보유 목록도 누적된다.
- 등록 키가 리소스 ID를 겸하며 등록 재시도 시 현재 행 내용과 입력을 비교한다. 현재 행만 수정하면 기존 등록 재시도가 충돌하므로 최초 입력 보존이 선행돼야 한다.
- MutationRunner는 현재 POST 고정이다. PUT 지원에는 HTTP 메서드의 미확정 요청 영속화와 기존 저장 데이터의 POST 기본값 호환이 필요하다.

## 제품 계약

- 재배 정보 편집: plantName(식물명), name(재배 별명), memo. 두 이름은 앞뒤 공백 제거 후1~100자, 메모는 공백 제거 후0~2000자. 메모 빈 문자열은 삭제 의미다.
- 종료된 재배도 정보 정정 가능. 재배 ID/시작·종료 시각/상태/장비 연결/측정 연결은 이 API로 변경하지 않는다. 이름 변경은 같은 재배의 표시 정보 정정이며 실제 다른 식물·용기로 교체한 경우 새 재배를 등록한다.
- 관리 기록은 type(REPLACE/TOP_UP/NUTRIENT_ADD), replacementScope, occurredAt, amountMl, memo를 정정한다. ID/cultivationId/createdAt은 유지한다. REPLACE만 FULL/PARTIAL 필수, 나머지는 null. amountMl은 null(미상) 또는 기존 양수·소수2자리·상한 검증, memo는0~2000자다.
- 관리 정정 시각은 재배 시작 이상, 서버 현재 이하, 종료 재배는 종료 시각 이하. 종료 후 신규 등록 금지는 유지하되 과거 기록 정정은 허용한다.
- createdAt은 최초 등록 시각, updatedAt은 마지막 변경 서버 시각이다. 기존 WaterMaintenance 생성자의 occurredAt<=createdAt 규칙을 정정 검증에 그대로 쓰지 않는다. 생성/정정의 검증 시점을 분리하고 과거 행의 createdAt은 보존한다.
- 수정 전후 내용·버전·서버 수정 시각을 보존한다. 변경 없는 저장은200/현재 값 반환, 버전 증가·실질 수정 이력 추가 없음. 삭제/재개/측정 원본 수정은 제외한다.

## HTTP 계약

기존 단일 사용자 인증·오류 형식을 유지하며 JSON을 사용한다. PUT은 해당 편집 가능 정보의 전체 교체다. 아래 필드는 모두 필수이며 미지정/알 수 없는 수정 필드는400으로 거절한다. null 허용은 명시한 필드뿐이다. 응답은 저장 완료된 리소스와 version/updatedAt을 포함한다.

| 요청 | 계약 |
|---|---|
| PUT /api/cultivations/{id}/profile | Idempotency-Key(UUID v4), body: expectedVersion(0이상 정수), plantName, name, memo. 시작/종료 등 추가 필드 금지. 성공200 Cultivation. |
| PUT /api/cultivations/{id}/water-maintenances/{maintenanceId} | Idempotency-Key(UUID v4), body: expectedVersion, type, replacementScope(null 허용), occurredAt(UTC Instant), amountMl(null 허용), memo. 성공200 WaterMaintenance. |
| GET /api/cultivations/{id}/water-maintenances/{maintenanceId} | 정정 전 최신 원본 읽기/충돌 후 재조회. 성공200 WaterMaintenance. 타 재배에 속한 ID도404. |

- 재배 및 관리 기존 GET/등록/종료 응답에 version을 추가한다. 관리에는 updatedAt도 추가한다. 기존 필드는 제거/재해석하지 않는다. 기존 클라이언트가 신규 필드를 무시하는지 회귀 검증한다.
- version은 리소스별0부터 시작한다. 실제 변경마다+1, 재배 종료로 상태가 바뀔 때도+1이다. 충돌 검사는 트랜잭션 내부에서 잠금 또는 조건부 갱신으로 강제하고 확인/갱신 분리를 금지한다.
- 실패:400 입력 오류,401/403 기존 인증 계약,404 CULTIVATION_NOT_FOUND 또는 WATER_MAINTENANCE_NOT_FOUND,409 VERSION_CONFLICT 또는 IDEMPOTENCY_CONFLICT. 기존 신규 등록의 CULTIVATION_ENDED 처리는 유지한다.
- VERSION_CONFLICT에서는 자동 덮어쓰기하지 않는다. 사용자 입력을 보존하고 최신 내용을 재조회한 뒤 다시 편집한다. 새로 확정한 수정은 새 키를 사용한다.

## 멱등성·트랜잭션·영속성 계약

- 수정 요청 식별은 인증된 소유자 범위의 키이며 메서드/경로/대상/정규화 입력/expectedVersion을 함께 비교한다. 같은 키·같은 요청은 최초 성공 응답을 재생하고 다시 적용하지 않는다. 키 재사용·다른 내용/대상은409이다.
- 성공 요청 재생 판별은 현재 version 충돌 검사보다 먼저 한다. 이후 다른 수정이 있어도 예전 요청 재시도가 최신 내용을 되돌리지 않는다. 앱은 성공 응답 후 최신 GET을 수행해 오래된 재생 응답을 현재 상태로 고정하지 않는다.
- 최초 등록 입력은 불변 자료로 보존한다. 기존 데이터는 이번 기능 전 현재 등록 필드로 초기화한다. 기존 등록 재시도는 이 입력과 비교하고200/현재 리소스를 반환한다. 수정된 내용을 최초 등록 입력으로 재해석하거나 재적용하지 않는다.
- 신규 Flyway 마이그레이션으로 version/updatedAt·최초 등록 입력·수정 요청 영수증/전후 이력을 보존한다. 기존 V1~V10은 수정하지 않는다. 테이블/인덱스 상세는 구현 Phase에서 본 계약을 만족하도록 설계한다.
- 현재 상태 갱신/수정 이력/성공 영수증은 application 서비스의 하나의 트랜잭션으로 커밋한다. 롤백 시 부분 기록을 남기지 않는다. 동시 중복 키도 단일 반영한다.
- 재배 정정·종료·관리 정정/등록의 잠금 순서를 재배→관리로 통일해 경합을 검증한다. 최근 전체 물 교체는 정정된 전체 기록에서 다시 조회한다. 측정 원본/통계/요청 시 고정한 재배 ID는 바꾸지 않는다.
- 앱은 PUT 메서드/원래 origin/경로/키/본문을 전송 전에 보존한다. 기존 pending 레코드는 메서드가 없으면POST로 읽는다. 인증 만료·로그아웃·프로세스 재시작에도 요청을 유실하거나 다른 서버로 보내지 않는다.

## 돌봄/측정 이력 화면 계약

- 상세 돌봄 타임라인: 최근 관리10개와 재배 시작/종료 이벤트를 날짜별 표시한다. 관리 API는 limit=11로 조회해 초과 여부만 확인한다. 과거 재배 시작/종료는 날짜순의 실제 이벤트로 표시하며 최근 관리10개가 전체 관리 이력이라는 인상을 주지 않는다.
- 매시간/수동 측정은 타임라인에서 제외하고 최신 측정 카드·기존 추세 차트·전체 측정 이력에서 확인한다. 원본 데이터/자동 스케줄은 그대로 유지한다.
- 전체 돌봄 기록과 전체 측정 이력은 각각50개 단위 이전/다음 페이지. 한 화면에 모든 이전 페이지를 누적하지 않는다. 기존 limit/offset API와 정렬을 유지하며 동일 시점 스냅샷을 보장한다고 주장하지 않는다. 변경 후 첫 페이지를 다시 조회한다.
- 상세 진입 시 타임라인 목적의 측정50건은 조회하지 않는다. 측정 이력을 열 때 첫 페이지를 읽는다. 차트의 별도 최대1600건/20초 한도는 유지한다.
- 차트의 물 교체 표식은 로드된 관리 기록만 나타냄을 계속 명시한다. 상세 관리 조회가11건으로 줄어도 전체 기간의 교체 기록이 모두 보인다고 주장하지 않는다.
- 재배 정보 편집에서 두 이름/메모의 현재 값을 제공한다. 관리 항목 상세에서 편집 진입. 저장 실패/충돌 시 작성 중 입력을 보호하고 취소는 서버 값을 변경하지 않는다. 미확정 쓰기 존재 시 다른 쓰기는 기존 정책으로 차단한다.

## 구현 순서·변경 파일·인수 조건

1. 문서·계약 확정(현재): 이 문서, HYDROPONIC_MVP, AGENTS, HANDOFF. 링크/diff 확인. 제품 코드 테스트는 NOT RUN.
2. 백엔드: cultivation domain/application/http/persistence 및 새 마이그레이션. 최초 입력 보존을 선행하고 수정/경합/영수증을 구현. 등록→수정→최초 등록 재시도, 중복 수정, 다른 키 동시 수정, 종료 경합/기간, 영수증 롤백, 정정 후 최근 전체 교체, 과거 데이터 마이그레이션을 PostgreSQL 통합 테스트한다.
3. 앱: 데이터 모델/Remote·MutationRunner/영속 설정·ViewModel, 편집 Forms/Detail/CareHistory/CareTimeline. POST pending 호환, PUT 전송/재시도/인증 만료, 충돌 입력 보존, 이름 변경 후 홈·상세,10개 미리보기/50개 페이지·지연 조회·빈 상태/오류를 검증한다.
4. 최종: backend Full(Testcontainers Docker), Android Full(JDK21), 테스트 기기 UI 및 전용 API/DB/MQTT 종단. 실제 센서/운영 데이터는 사용하지 않는다. APK·수동 배포 안내·HANDOFF·diff/상태/위험을 제공한다.

- 기존 기능/실제 측정값·모든 보존 원본 유지, 라이브러리 추가/계층 재설계 없음. 신규200줄 초과 파일은 리뷰 대상으로 보고한다.
- 인수 전 구현 상태는 TODO/NOT VERIFIED다. 자동 수정 최대5회·동일 근본 원인3회 중단, 이미 통과한 검증은 변경 근거 없이 반복하지 않는다.
- 핵심 위험: 멱등성 후퇴, 구버전 pending 호환, 종료/수정 경합, 부분 관리 조회의 차트 표식 범위. 각 항목을 위 테스트와 표시 규칙으로 확인한다.
- 커밋 초안(별도 승인 필요): `feat: edit cultivation profiles and care records with bounded history`.

## Phase Result 기록

- 기준 main fc89cde, codex/cultivation-editing 생성. 기존 문서4개 미커밋 변경은 보존했다. 전체 계획 승인 후 구현 IN_PROGRESS.
- 백엔드: V11에 원래 등록의 편집 가능 필드를 initial_* 컬럼으로 복사/보존, version/관리 updatedAt과 care_edit_receipt를 추가했다. edit key 공간은 단일 소유자의 수정 API 전체에 공유하며 기존 등록 키 공간과 별개다. 영수증은 정규화 요청/성공 응답과 실질 변경의 전후 스냅샷을 함께 보관한다. 자동 정리/이력 조회 UI는 이번 범위 밖이다.
- 동시 수정은 edit key advisory transaction lock→재배 행→관리 행 순서다. 전체 UUID로 영수증을 판별하며 advisory hash 충돌은 불필요한 직렬화만 유발한다. 행 version은 명시적 잠금 아래 증가시키며 JPA @Version 자동 동작이라고 주장하지 않는다.
- 최초 컴파일 실패: 네이티브 다중 열 조회 결과가 raw Object로 추론돼 배열 접근 오류. 수정1/5로 Object[] 캐스트 적용. 후속 컴파일 및 재배 관련28개 테스트 PASS/실패0·오류0·건너뜀0, exit0/1분44초. 기간 형식 오류를400으로 변환하는 처리와 V10 데이터 보존/종료 경합 테스트를 추가한 백엔드 Full은 진행 중이다.
- 백엔드 최종 Full PASS/exit0/5분41초,270개 새 실행·실패0/오류0/건너뜀0, JAR 생성. V10 기존 컬럼·행 보존/초기값 복사, 종료·수정 경합 포함. `.tools/care-editing/backend-results`/backend-full.log 보존. 첫 컴파일 외 자동 실패 수정1회이며 새 마이그레이션은 적용 후 편집하지 않았다.
- Android Full PASS/exit0/1분40초, JVM36개 새 실행·실패0/오류0/건너뜀0, debug/미서명release 성공·Lint 오류0/기존 경고11. 이후 테스트 코드/표준 origin 복원은 최종 단계에서 확인한다.
- 첫 격리 UI/종단 실행28개 중 UI27개 PASS/종단1실패, 실패/오류/건너뜀1/0/0. 이름·메모/관리 수정·52건 이력2페이지 이동까지 성공했으나 모달 시트 캡처에서 root2개로 실패했다. 앱 코드 실패가 아니며 수정1/5(앱 Phase)로 최상위 root 캡처를 선택했다. 검증 assertion 삭제/약화 없음. 이전 결과는 e2e-attempt1에 보존하고 종단만 새 임시 환경에서 재실행한다.
- 두 번째 종단1개는 캡처 통과 후 이전 페이지의 화면 밖 footer를 기다려 timeout1개/exit1(1분49초). 수정2/5로 네트워크 로딩 완료 후 LazyColumn의 해당 footer까지 명시적으로 이동하고, 새로 지연 조회하는 측정 이력은 로딩 완료·원본60 표시를 확인한다. 앱 제품 코드는 변경하지 않았고 기존 assertion은 유지했다. e2e-attempt2 보존, 세 번째 새 전용 환경에서 종단1개 실행 중이다. 별도 첫 실행 명령의 상대 경로 오류는 Gradle/테스트 착수 전에 수정했다.

## 최종 Phase Result — 2026-10-30

- 세 번째 새 전용 API/PostgreSQL/MQTT 환경의 종단1개 PASS/실패0·오류0·건너뜀0/exit0/1분12초. 실제 인증 HTTP로 이름2종/메모 비우기·관리 정정·최근 전체 교체 갱신·52건 돌봄50개 페이지 왕복·홈 재조회·기존 START→60원본/통계50/NORMAL/버퍼 해제·측정 상세/이력·재배 종료·과거 불완전 측정 조회를 확인했다. 실제 센서 아닌 모의 fixture이며 운영 서버와 무관하다.
- 시각 검수에서 root 열거 순서가 모달의 Z순서와 다름을 확인해 캡처만 care-history-list로 지정했다(앱 Phase 수정3/5). 이전 잘못된 페이지 캡처는 배경 타임라인이며 시트 증거로 사용하지 않는다. 표준 HTTPS 빌드의 시트 검사1개 PASS/실패0·오류0·건너뜀0/exit0/57초, 명시적 시트 노드 PNG 시각 검수 PASS. 이름/관리 편집·타임라인 캡처도 검수했다. 스크린샷은 모두 테스트 데이터다. assertion 삭제/약화 없음.
- 최종 표준 HTTPS Full PASS/exit0/45초, debug/미서명release 빌드. JVM36개는 앞선 Full의 새 실행 PASS 결과이며 마지막 Full은 UP-TO-DATE다. 최종 Lint 오류0/기존 경고11. UI27개는 첫 종단 실행의 개별 성공 결과이며 종단/시트 재검사1개씩과 하나의 실행으로 합산하지 않는다. 최종 요약은 `.tools/care-editing/result.json`, 각 실행 XML·로그는 같은 디렉터리에 보존한다.
- APK `.tools/care-editing/ppossak-editing-debug.apk`, SHA256 `6C7498124F6E275C7CEE1C133EBBA025A5E063BD6E0AF0B59A1627878AABE62E`. origin은 `https://ppossak-api.jucadrive.com`. JAR `.tools/care-editing/server-upload/app.editing.jar`, SHA256 `E8665BBC83410C2BB2B2FCC84C0041450C9D973AB74E994A56F7B74AFFA36936`. 검증된 JAR에 V11 포함 확인. [수동 적용](../CARE_RECORD_EDITING_SETUP.md)은 백업→기존 overlay 유지→backend만 재빌드→V11/healthy→새 APK 순서다.
- API/모델/등록 멱등성·수정/영속성, 앱 PUT/구버전 POST pending·편집/충돌·최근 관리10개/50개 페이지·지연 조회 및 관련 테스트만 변경했다. firmware/infra/기존 session·측정 도메인/자동 스케줄/의존성은 diff 없음. 적용된 V1~V10과 테스트 적용 후 V11은 수정하지 않았다.
- 새 Java/Kotlin 파일200줄 초과 없음. 기존 MonitorViewModel252줄과 AppFlowTest214줄은 리뷰 대상이다. 줄 수만 위한 구조 분할 없음. 새 의존성/계층 변경 없음. 백엔드 수정1/5, 앱 테스트/캡처 수정3/5로 한도 안이며 같은 근본 원인3회 연속 실패 없음.
- 시험 서버3개는 stop/stopped와 종료코드0 확인, 관련 Testcontainers/ADB reverse 없음. 이번 작업 소유 flutter_emulator만 종료하고 Docker/다른 서비스는 유지했다. 서버 SSH·운영 배포·물리 폰 설치/실장비 새 회귀·운영 DB 변경·Git 발행은 NOT RUN. 대량 데이터 벤치마크/전체 TalkBack 수동 인수는 NOT RUN. offset 조회는 동시 정정 시 스냅샷 완전성을 보장하지 않는다.
- Git은 codex/cultivation-editing의 unstaged 변경을 유지한다. 사용자 최종 리뷰와 수동 서버 업데이트가 다음 단계다.
