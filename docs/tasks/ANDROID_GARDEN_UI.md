# Android 작은 정원 UI 개편

- 2026-10-26 사용자 승인: 초록·둥근·아기자기한 식물 테마와 한글 폰트 적용, 에뮬레이터 검증. 디자인 확인 후 커밋 여부 결정. 사용자 후속 지시로 전체 전환 변경의 커밋·PR·main 병합 승인(2026-10-26).
- 범위: Android 테마·공통 그림/컴포넌트·홈·상세·폼·설정·측정 화면, 폰트/라이선스, 관련 Compose 테스트. API/DB/MQTT/보안 정책 변경 없음.
- 순서: 1 테마·공통 요소 → 2 홈·상세·입력·측정 화면 → 3 Full·에뮬레이터 UI/종단·캡처 검수 → 4 HANDOFF.
- 인수: 한글 둥근 폰트/초록 팔레트/새싹 그림, 정상·빈 화면·오류·로딩·종료 상태 가독성, 기존 요청/보존/실측 구분 유지. 작은 화면/큰 글꼴 스크롤·입력·주요 버튼 접근 확인.
- 위험: 폰트 교체에 따른 줄바꿈·스크롤, 연한 색 대비, 장식으로 상태 의미 오해. 수치는 SansSerif로 분리하고 상태를 글자로 병기한다. 캐릭터는 장식이며 식물 건강 판정이 아니다.
- 검증: Android Full(JVM/Lint/APK), emulator-5554 명시 Connected/EndToEnd, 실제 화면 PNG 검수. backend 코드 무변경 시 기존204개 결과 유지하고 불필요한 전체 재실행 안 함. 실물 센서 NOT VERIFIED 유지.
- VERIFIED(사용자 디자인 리뷰 대기, DONE 아님). 자동수정0/5. 기존 staged/unstaged와 MIGRATION.md AM 보존. 단일 에이전트.

## 구현 결과
- GardenTheme: 크림/연두/초록 팔레트, Material3 전체 명시 색상(기본 보라색 카드 제거), 둥근 Shapes, 감자꽃 제목·고운돋움 본문·SansSerif 주요 측정 수치.
- GardenElements: 외부 이미지/런타임 라이브러리 없는 Canvas 화분·새싹 얼굴, 배지·빈 상태·섹션·소개 요소. 장식은 측정값/건강 상태를 나타내지 않는다.
- 홈·재배 상세·추세·등록/관리·설정·측정/명령/과거 기록에 공통 스타일 적용. 기존 HTTP/멱등/저장/보존/해제·종료 차단·통계 제외 동작 보존.
- 폰트 원본 고정 리비전/SHA256/출처: mobile/android/FONT_NOTICES.md. OFL1.1 전문을 APK assets에 포함하고 앱 설정에서 열람 가능. 원본 TTF2개 약19MB를 포함하며 다운로드형 폰트가 아니다.
- 첫 Full exit0: JVM9개 새 실행 실패0/오류0/건너뜀0, Lint0오류/11경고, debug/미서명 release APK PASS. 52초,105작업 중48실행/57재사용.
- Connected6개 새 실행 PASS(실패0/오류0/건너뜀0), 1분1초. 이어 첫 EndToEnd7개 PASS,51초(Connected작업1개 새 실행/70재사용). 실제 Spring·임시 DB/MQTT와 모의 장치로 검증. 실제 센서 인수 아님.
- 1차 캡처7장(garden-settings/empty/home/detail-top, normal-result, cultivation-detail, legacy-incomplete) 육안 검수 PASS. 정상/빈 화면·SIMULATED·불완전 통계 없음·원본 제외 표시 유지.
- 추가 좁은320dp+1.6배 글꼴 입력/스크롤/등록과 실제 앱 오류 표시·등록/관리 캡처를 강화했다. 테스트 코드 변경 후 최종 Full exit0(19초,4작업실행/101재사용), JVM9·APK는 앞 성공 결과 UP-TO-DATE 재사용, AndroidTest Lint 분석 재실행. 최종 EndToEnd 결과는 아래 기록.
- Backend 이번 UI 변경0, 기존 최종204개 PASS 결과 유지, 재실행 NOT RUN. 실물 센서/교정/독립 정확도 NOT VERIFIED 유지.

## 최종 인수
- 최종 EndToEnd7개 새 실행, 실패0/오류0/건너뜀0, exit0/PASS.57초,71작업 중5실행/66재사용. Forms6개에는320dp 폭·1.6배 글꼴 등록과 라이선스 열람 포함. AppFlow1개는 잘못된 입력의 오류 안내·수정 후 정상 연결·재배/관리/측정/종료/과거 조회를 포함한다.
- 최종 캡처10장과 XML: .tools/android-acceptance/garden-ui-1/screens 및 results(Git 제외). 추가 오류/등록/관리 화면 육안 검수. 동일 동작 실패나 자동 수정0회. 모든 화면/글꼴/OS/스크린리더 인수를 완료했다는 의미는 아니다. 로딩 indicator 코드는 유지했고 짧은 전이 상태를 별도 캡처하지 않았다.
- 시험 서버 stop 요청·stopped 및 종료 로그 확인, 전용 DB/브로커 종료. 생성한 emulator-5554 adb reverse만 제거. 에뮬레이터에 새 디버그 APK 재설치·실행. 실제 폰은 이 UI 작업에서 조작하지 않았다. 서버 종료 뒤 앱은 설정 화면에서 연결을 준비하는 상태다.
- 변경 파일: ui/GardenTheme.kt·GardenElements.kt 신규, MonitorApp/Dashboard/Detail/Forms/Settings/SessionScreens/TrendChart 수정, FormsTest/AppFlowTest 보강, res/font·assets/licenses·FONT_NOTICES, 이 명세/HANDOFF/ANDROID_SETUP. 런타임 의존성 추가0.
- 상태 VERIFIED, 사용자 시각 리뷰·커밋 승인 대기. 커밋 초안: feat(android): add a cozy green garden theme. 현재 커밋/푸시 없음. 기존 MIGRATION.md AM 및 index 보존. 신규 Kotlin200줄 초과0.
