# Botanical Aqua Glass UI 개편

상태 DONE(2026-10-30 사용자 UI 리뷰·Git 통합 승인). 브랜드 ‘뽀싹’ 유지. 에이전트의 실기기 설치·운영 API 새 빌드 인수는 NOT RUN이며 로컬 자동 검증과 사용자 UI 리뷰를 구분한다.

## 범위와 불변식

- 기존 Kotlin/Compose·Material3·단일 Activity·enum 화면 전환 및 ViewModel/Remote 구조 유지. 별도 Navigation/Repository/차트/아이콘 라이브러리를 추가하지 않는다.
- 지정 배경/초록/민트/아쿠아/흰색과 토큰, 시스템 Sans Serif 본문/숫자 및 기존 손글씨 브랜드를 사용한다. 글꼴/라이선스·앱 아이콘은 보존한다. Glass는 핵심 지표에 제한하며 정적인 수면 장식은 수위/건강과 무관하다.
- 홈→재배 상세/차트→날짜별 타임라인/이력→측정 결과와 로그인/폼/설정/명령 화면을 순차 개선한다. 48dp 터치, 작은 화면/큰 글꼴, 초기 Skeleton/새로고침/오류 재시도, 데이터 없음과0값을 구분한다.
- 실제 API/쓰기/인증/미확정 요청의 키·본문·원래 origin과 단일 사용자 보안은 유지한다. 읽기 재시도가 쓰기를 새로 만들지 않는다. 서버/펌웨어/DB/공개 계약/의존성 변경 없음. 운영 서버·장비 조작/설치/커밋·푸시 제외.
- 차트는 기존 tds-trend(limit/offset)의 실측 정상 세션 중앙값이다. 최근24시간/7일/30일, 페이지당200/최대8페이지1600건·조기 종료·시간 제한, 한도 도달 시 부분 조회 표시. 전체 이력 무제한 조회/가상 데이터/임의 집계·곡선 극값 생성 금지.
- 실제 displayAt 시간 간격/ppm축/관측 선택(터치와 접근성 버튼), 부족·가정/실측 혼합·오래된 시각을 명시한다. 표시용 단위/반올림은 저장 수치/통계 계산을 바꾸지 않는다.
- 관리/측정/재배 이벤트는 실제 필드로 날짜별 그룹화한다. 관리 상세는 기존 데이터 읽기 전용 sheet, 측정은 기존 세션 화면. 원본/안정화 제외와 모든 기존 등록/더보기/재시도/종료 기능 보존.

## Phase·검증

1. 디자인 토큰·정적 벡터·공통 카드/상태, 홈 구현 및 Kotlin 컴파일.
2. 한도 있는 차트 읽기/표시 정책·상세/이력/타임라인·결과 및 보조 화면. 읽기 상태는 표시 목적에 한정해 ViewModel에 추가한다.
3. 순수 표시/범위/조회 한도 테스트, 기존 인증/쓰기 회귀, Android Full(debug/미서명release·JVM·Lint), API35 UI(320dp/큰 글꼴·선택/펼침/에러/빈 상태) 및 격리 API/DB/MQTT 종단. 운영 API/비밀 사용 안 함.
4. 캡처 시각 검수·필요한 수정/재검증·표준 HTTPS APK 복원/제공·HANDOFF/변경/검증/미검증 보고. 수정5회/동일원인3회 한도, 이미 성공한 검사는 근거 없이 반복하지 않는다.

## 위험·기록

- 현재 API에 날짜 조건/합계가 없어 차트 한도와 목록 일부 개수를 전체로 가장하지 않는다. offset 페이지 중복은 sessionId로 제거하며 범위 완전성을 보장할 수 없을 때 표시한다.
- 새로운 Java/Kotlin200줄 초과 파일은 보고서에 리뷰 대상으로 기록한다. 기준 main9d64b1c, 브랜치 codex/botanical-aqua-ui. 초기 작업 트리 clean/첨부·개인 설정 보존. 커밋 초안 `feat: redesign Android care dashboard with botanical aqua UI`.
- 이전 backend261/Android JVM21·UI17 검증은 기준 이력이며 이번 UI를 입증하지 않는다. 이번 실행 결과는 아래 최종 기록을 따른다.

## 진행 결과

- Phase1 테마·공통 아쿠아/화이트 표면·홈, compileDebugKotlin PASS/20초. Phase2 조회/차트/상세/타임라인/결과·보조 화면 compileDebugKotlin PASS/28초.
- 첫 Android Full PASS/1분7초·exit0: JVM32개 새 실행/실패0·오류0·건너뜀0, debug/미서명 release, Lint 오류0/기존 경고11. 이후 표시 상태/스크롤 보완의 최종 결과는 별도로 기록한다.
- UI 첫22개 중20개 PASS/2실패(실패/오류/건너뜀2/0/0), 1분37초/exit1. `.tools/aqua-ui/ui-attempt1` 보존. 수정1/5: 큰 글꼴 화면 밖 버튼을 LazyList 노드 검색으로 먼저 이동, 이력 시트의 동일값은 시트 내부 선택자로 한정했다. 테스트 삭제/약화 없음. 긴 메모 시트 스크롤, 테마의 기본 글자색 제공도 시각 검수 후 보완했다.
- 서버 테스트 fixture만 사용한 `.tools/aqua-ui/e2e-1` 전용 임시 환경을 준비했다. 실제 계정/운영 API·DB·실물 센서는 사용하지 않는다. 처음 캡처는 테스트용 값이며 실제 센서 측정 결과로 주장하지 않는다.

## 최종 Phase Result — 2026-10-30

- 구현: GardenTheme 토큰·Sans 본문/숫자·손글씨 브랜드, CareCards의 정적 AquaMetricCard/반응형 통계/Skeleton, 홈·상세·TrendChart·CareTimeline/CareHistory·측정 결과·로그인/설정/폼/명령 화면을 개선했다. 기존 글꼴/라이선스와 아이콘을 보존했다.
- 기존 등록/수정/관리/종료·수동 START·원본/안정화·페이지 조회·인증/로그아웃·미확정 요청의 키/본문/origin 보호를 유지했다. backend/firmware/의존성과 MonitorApi/OwnerSession/Vault/MutationRunner는 diff가 없다. 화면 상태·읽기 재시도·한도 있는 추세 읽기만 기존 ViewModel 구조에 추가했다.
- 차트는 기존 GET API의 200건×최대8페이지/1600건, 20초 예산과 기간 경계 조기 종료를 사용한다. 중복 sessionId 제거, 실제 시간 간격·관측 선택·부분 조회/오류/빈 상태를 제공한다. offset 페이지는 시점 고정 스냅샷이 아니며 모든 기간의 완전성을 보장하지 않는다.
- UI Connected: 22개 PASS, 실패0/오류0/건너뜀0, exit0/1분26초. 첫 격리 종단은23개 중22개 PASS/1실패(화면 밖 원본 개수 검색). 수정2/5로 명시적 Lazy 스크롤을 적용하고 작은 폭/큰 글꼴의 통계를1열로 보완했다. 테스트 삭제·검사 약화 없음. 새 전용 API/DB/MQTT 환경에서 종단23개 PASS, 실패0/오류0/건너뜀0, exit0/2분16초. 이전 실패 로그도 보존했다.
- 수정3/5: Muted/Aqua 대비4.38을 확인해 보조 글자 토큰을 조정했다. 최종 텍스트9조합 모두4.5 이상(최저4.87). 수정 한도와 동일 원인 연속 실패 한도를 넘지 않았다.
- 최종 표준 HTTPS Android Full: exit0/1분10초, JVM32개 새 실행·실패0/오류0/건너뜀0, debug와 미서명release 빌드 성공. Lint 오류0/기존 경고11(의존성/UseKtx). 최종 표준 빌드의 차트 선택·캡처 검사1개도 PASS/exit0/44초. 서로 다른 실행의 검사 수를 하나의 실행으로 합산하지 않는다.
- 시각 검수: 홈/상세/차트/날짜별 타임라인/로그인/결과·320dp/글꼴2배 캡처 확인. `.tools/aqua-ui/screens-final` 및 `e2e-screens`는 명시적인 테스트 fixture이며 실제 센서 값으로 주장하지 않는다. 전체 TalkBack 수동 인수·실제30일 대량 데이터 성능·실기기 설치/운영 API 인수는 NOT RUN.
- 제공 APK: `.tools/aqua-ui/ppossak-aqua-debug.apk`, SHA256 `A1FBF32EE1655208457D5CAB84ED618278C8C3D3DF6E0F6122076C96C8071904`. BuildConfig origin은 `https://ppossak-api.jucadrive.com`. 글꼴 라이선스 assets 포함을 확인했다. 실행별 XML/로그/대비·요약은 `.tools/aqua-ui/result.json`과 각 결과 디렉터리에 보존한다.
- 시험 자원: 전용 수용 서버2개 종료, 해당 Testcontainers와 ADB reverse 없음. 이번 작업 소유 flutter_emulator만 종료했다. 운영 서버·장비/실제 DB에 접근하거나 서버 SSH·배포를 수행하지 않았다.
- 신규 Kotlin 파일은200줄 이하. 기존 MonitorViewModel은206줄로 읽기 상태 변경 리뷰 대상이며 줄 수만 위한 분할은 하지 않았다. Git은 codex/botanical-aqua-ui의 unstaged 변경으로 유지하고 커밋/푸시/PR/병합은 수행하지 않았다. 다음은 사용자 실기기 UI 리뷰다.

## Git 통합 승인 — 2026-10-30

- 사용자가 개선 UI를 리뷰한 뒤 현재 작업의 Git 반영을 요청하고 푸시·PR·main 병합까지 명시적으로 승인했다. 위 미수행 Git 상태는 승인 전 체크포인트다.
- 소스·테스트·검증 스크립트·공개 문서27개만 명시적으로 stage한다. `.tools`/첨부/비밀 설정/APK/캡처/빌드 산출물은 제외한다. origin의 codex/botanical-aqua-ui에 푸시하고 CI 성공 후 PR을 main에 merge commit 방식으로 통합한다. 강제 push/삭제/운영 변경은 하지 않는다.
- 커밋 초안: `feat: redesign Android care dashboard with botanical aqua UI`. 이미 성공한 검증은 문서만 수정한 이번 Git 단계에서 반복하지 않는다. SHA·PR·원격 CI 결과는 최종 사용자 보고로 제공한다.
