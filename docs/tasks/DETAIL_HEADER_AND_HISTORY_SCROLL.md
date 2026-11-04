# 상세 헤더·이력 카드 스크롤 긴급 개선 — 2026-10-30

상태 VERIFIED(로컬). 사용자가 이름/메모/관리 수정 API 동작을 확인하고 다음 UI 수정을 직접 지시했다. 아래 전체 범위를 구현·검증했으며 실제 폰의 연속 드래그 최종 확인은 남아 있다. 기존 미커밋39개 변경/staged 없음과 codex/cultivation-editing을 유지했다.

## 범위·인수 조건

- ‘나의 식물 일지’ 문구/큰 세로 나열을 없애고 재배 이름·작물/상태/일수·시작/종료 시각·메모를 압축한 헤더를 만든다. 작은 화면/큰 글꼴에서 이름과 날짜를 자르지 않고 긴 메모는 펼칠 수 있게 한다.
- 이름 옆48dp 점3개 메뉴에 재배 정보 편집과 활성 재배의 재배 종료를 모은다. 기존 종료 확인/쓰기 차단/pending 보호를 유지하고 최하단 종료 버튼은 제거한다. 종료된 재배는 편집만 허용한다.
- 전체 돌봄/측정 이력의 카드 위 실제 손가락 스와이프를 재현한다. 기록 목록과 시트의 세로 드래그 경합 가설을 확인하고 목록 시트는 완전히 펼친 상태/고정된 높이에서 목록만 스크롤하도록 한다. 닫기/뒤로/바깥 영역 닫기·선택·50개 페이지/오류/빈 상태는 유지한다. 세부 이벤트 시트는 이번 긴 목록 결함과 구분한다.
- 카드 중앙에서 반복 위/아래 스와이프 시 목록 진행과 되돌림, 시트 위치 안정성, 항목 클릭/페이지 탐색을 시험한다. 의미 노드로 강제 스크롤한 성공을 터치 제스처 성공으로 대신하지 않는다.

## 단계·변경·검증

1. 현재 코드/고정 Material3 1.4.0 소스 확인, 동작 재현 테스트를 제품 수정 전에 실행한다. 소스는 기본 sheetGesturesEnabled=true의 nestedScroll/draggable과 부분 확장 anchor를 사용하고 있다. 정확한 폰 떨림까지 에뮬레이터에서 재현했는지는 분리 기록한다.
2. 공통 압축 헤더와 메뉴, 이력 공통 시트 상태·제스처 처리 수정. 예상 파일 DetailScreen/CareHistory/신규 CultivationHeader와 관련 Android 테스트/AppFlowTest/verify-android, 이 문서/HANDOFF다. 새 의존성/백엔드/API/DB/펌웨어/자동 측정 설정 변경 없음.
3. Android Full(JDK21), 에뮬레이터 터치/메뉴/큰 글꼴·기존 UI 회귀, 새 전용 루프백 API/DB/MQTT의 수정/종료 메뉴 종단을 실행한다. 백엔드 제품 변경이 없으므로 앞선270개 성공 검증을 반복하지 않는다.
4. 캡처 검수·표준 HTTPS APK·결과/위험/미검증·diff/비밀 검사 기록, 시험 자원 정리. 폰/운영 서버 조작과 Git 발행은 제외한다. 사용자는 새 APK에서 원래 폰의 연속 드래그를 최종 확인한다.

자동 실패 수정은 구현 Phase당5회/동일 근본 원인3회 한도를 지킨다. 새로운200줄 초과 파일은 리뷰 대상으로 보고한다. 기존 미커밋/비밀·첨부는 보존한다. 초기 재현 실패는 결함을 드러내는 기대 실패이며 실패 검사를 약화하지 않는다.

## 진행 결과

- 수정 전 API35 에뮬레이터의 두 실제 카드 제스처 검사 모두 기대 실패: 목록 상단1087px가 스와이프 후190px로 이동했다. 자동 검사 강제 스크롤 없이 클릭 가능한 카드 중앙에서 손가락 스와이프를 시작했다. 폰의 연속적인 떨림 자체를 에뮬레이터 영상으로 재현했다고 주장하지 않는다.
- 공통 StableHistorySheet는 skipPartiallyExpanded=true·sheetGesturesEnabled=false·dragHandle 없음, 창 높이90%와 고정 제목/닫기·남은 높이 LazyColumn을 사용한다. 부모 시트 nestedScroll/draggable을 끄고 목록을 유일한 세로 스크롤 주체로 둔다. 단순히 검사에서 시트를 미리 펼쳐 결함을 숨기지 않았다.
- CultivationHeader에 이름·식물/상태/일수·날짜/긴 메모 펼침과48dp 메뉴를 배치했다. 편집/종료 메뉴는 pending/busy 보호, 종료된 재배는 종료 메뉴 없음, Detail의 기존 종료 확인창 유지. 기존 AppFlow도 실제 메뉴를 열어 편집/종료하도록 바꿨다.
- 첫 Android Full PASS/exit0/1분19초, JVM36개 실패0/오류0/건너뜀0. UI32개 회귀와 별도 종단을 진행한다. 현재 생성된 Kotlin 세션 캐시가 추적 후보에 보여 루트 .gitignore에 .kotlin/를 추가했다. 생성 파일은 삭제하거나 Git에 포함하지 않는다.

## 최종 Phase Result

- UI32개 새 실행 PASS/실패0·오류0·건너뜀0/exit0/1분54초. 수정 전 실패한 두 실제 카드 스와이프 검사가 성공했다. 50개 관리/측정 카드 각각 중앙에서 위4회·아래3회 스와이프하며 목록 진행/복귀와 시트 위치2px 이내 고정을 확인했다. 강제 ScrollTo로 해당 제스처 검사를 대체하지 않았다.
- 압축 헤더의 문구 제거·메뉴 편집/종료·종료된 재배 메뉴·320dp/글꼴2배 긴 메모 펼침·pending 메뉴 차단과 기존 UI 회귀 PASS. 종료 확인창/기존 쓰기 보호 유지.
- 새 전용 API/PostgreSQL/MQTT 종단1개 PASS/실패0·오류0·건너뜀0/exit0/1분24초. 실제 점3개 메뉴로 편집·종료, 기존 관리 정정/52건 페이지·START/60원본·통계50·NORMAL·버퍼 해제·과거 불완전 조회까지 확인했다. 센서 데이터가 아닌 모의 fixture다.
- 최종 표준 HTTPS의 Full+카드 제스처2개 PASS/실패0·오류0·건너뜀0/exit0/58초. 드래그 중 항목 클릭이 발생하지 않는 assertion도 추가해 확인했다. debug/미서명release 빌드와 Lint 오류0/기존11경고. JVM36개는 첫 Full의 새 실행 성공 결과이며 최종 실행은 UP-TO-DATE다. 서로 다른 실행 수를 합쳐 단일 실행으로 보고하지 않는다.
- 캡처 시각 검수 PASS: compact header/점3개, 실제 전용 API의 이력2페이지. 이력 캡처는 목록 부분만 촬영돼 고정 제목/닫기는 이미지 밖이며 코드/실제 종단의 닫기 동작으로 확인했다. `.tools/detail-scroll/screens`는 테스트 fixture이며 운영/실측 값으로 주장하지 않는다. 폰에서 보고된 연속 떨림 자체의 최종 수동 인수는 NOT RUN.
- 표준 APK `.tools/detail-scroll/ppossak-detail-scroll-debug.apk`, SHA256 `16A1DB1C6478BE2CA79AE7517FB6E16AAC477B7757998C8A38EE94EA5C58C720`, origin `https://ppossak-api.jucadrive.com`. 상세 결과/실행별 XML/로그/재현 실패는 `.tools/detail-scroll/result.json` 및 관련 디렉터리에 보존한다.
- 이번 제품 변경은 CultivationHeader/DetailScreen/CareHistory 및 관련 테스트·검증 스크립트·문서/캐시 ignore다. 백엔드/API/DB/펌웨어/자동 측정/인증·미확정 요청은 이전 검증 상태 그대로다. 앞서 API를 적용했다면 서버 재배포 없이 새 APK만 업데이트한다. 기존 백엔드270개 PASS는 이전 작업 결과이며 이번 재실행은 NOT RUN이다.
- 기대한 수정 전 실패 외 구현 후 자동 검사 실패0, assertion 삭제/약화 없음. 신규 Java/Kotlin200줄 초과 없음. 기존 AppFlowTest221줄과 ViewModel252줄은 리뷰 대상으로 표시하며 줄 수를 맞추기 위한 임의 분할 없음.
- 시험 서버 stop/stopped/exit0, 관련 Testcontainers·ADB reverse 없음. 이번 소유 flutter_emulator만 종료했다. 폰 설치/운영 API·서버 SSH/배포·장비/운영 데이터 변경·Git 발행은 NOT RUN. 전체 TalkBack/물리 기기 프레임 측정은 NOT RUN.
- Git은 같은 브랜치의 unstaged 변경으로 유지했다. 다음은 사용자 폰에서 새 APK의 카드 스크롤과 헤더/메뉴 최종 리뷰다. 커밋 초안(별도 승인): `fix: stabilize history card scrolling and simplify cultivation header`.
