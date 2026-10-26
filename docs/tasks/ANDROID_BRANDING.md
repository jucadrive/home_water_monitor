# 뽀싹 이름·아이콘 적용과 설치 통합

- 2026-10-26 사용자 승인: 첨부 이미지로 런처 아이콘 적용, 앱 이름·상단 제목을 뽀싹으로 변경, 빌드·Lint 후 폰 설치 확인. 추가로 기존 앱과 UI 확인용 앱을 하나로 통일하도록 지시했다.
- 범위: Android 표시 이름·브랜드 문구·런처 리소스, 제공 원본 보존, 연결 폰 설치 통합, 인계 기록. 패키지 식별자는 기존 com.juca.hydroponic을 유지한다.
- 인수 조건: 런처 이름 뽀싹·첨부 캐릭터 표시, 원형 마스크에서 주요 캐릭터 보존, 앱 상단 뽀싹, 기존 서버 설정 보존·미확정 요청 유실 방지, 최종 설치 앱 하나.
- 검증: JDK21/Gradle8.13에서 testDebugUnitTest lintDebug assembleDebug, APK 이름·패키지·서명 확인, 연결 폰 설치·런처/앱 화면 검수, 최종 패키지 목록. backend·MQTT·실물 종단 재실행은 범위 밖이다.
- 기존 docs/HANDOFF.md 미커밋 변경과 미추적 첨부 폴더를 보존한다. 2026-10-26 사용자 후속 지시로 스테이징·커밋·푸시·PR·CI 확인·main 병합을 승인받았다. 첨부 폴더·APK·로컬 설정은 제외한다.
- 상태 DONE(2026-10-26 사용자 결과 승인 및 Git 통합 지시). 설치 통합 전 로컬 확인: 원래 앱은 monitor.xml 없음, preview 앱은 서버 설정 있음·미확정 요청 없음. 설정 파일 내용은 추적 문서에 기록하지 않는다.

## Phase Result
- 앱 이름을 strings.xml의 뽀싹으로 지정하고 상단 제목·소개 문구에 반영했다. 첨부 원본을 artwork/에 보존했으며 SHA256 일치를 확인했다. 최소 SDK26에 맞춰 adaptive icon을 기본 제공하고 시스템 테마 아이콘용 새싹·몸체 실루엣을 추가했다. 기존 물방울 벡터는 교체했다.
- JDK21·공식 SHA256을 검증한 Gradle8.13: `clean testDebugUnitTest lintDebug assembleDebug --no-daemon --console=plain` exit0/PASS, 1분32초·56작업 새 실행. JVM9개 실패0/오류0/건너뜀0. 빈 v26 폴더 정리 후 `lintDebug` exit0/PASS, 49초·5실행/25재사용, 오류0/경고9(기존 의존성 안내·KTX 권고). 마지막 Lint에서 컴파일·JVM·APK는 재실행하지 않았다.
- 실패기반 수정2/5: (1) 여러 wildcard import의 R 참조 충돌에 명시 import 추가, (2) 같은 아이콘 리소스의 폴더 이동 후 증분 산출물 누락을 확인하고 clean 재생성. 각 근본원인1회. 테스트 삭제·조건 약화·Lint 억제 없음. 초기 아이콘 경고16개는 불필요한 레거시 밀도 리소스 제거·단색 아이콘·기본 리소스 경로로 정리했다.
- 기존 서명이 다른 앱을 교체하기 전 preview의 monitor.xml을 Git 제외 .tools/android-branding에 보존했다. 기존 앱 교체→정식 com.juca.hydroponic APK 설치→설정 복원/전체 XML 일치→실행 확인→preview 제거→최종 APK 업데이트와 설정 보존 확인. 최종 패키지 목록은 com.juca.hydroponic 하나다.
- 폰 SM-S947N에서 install Success·am start Status: ok, 앱 정보의 첨부 캐릭터 아이콘·뽀싹 이름과 실제 앱 상단 뽀싹을 확인했다. 캡처 .tools/android-branding/phone-info.png, phone-final.png. 전체 런처·OS·테마 모드 검증을 의미하지 않는다.
- APK: .tools/android-branding/ppossak-debug.apk(정식 패키지, preview suffix 없음). 서버·DB·MQTT는 기동하지 않아 보존한 서버 주소로 연결 실패 안내가 표시된다. 새 backend/종단/실물 테스트 NOT RUN. backend·MQTT·보안 계약 변경 없음.
- Git: 이전 HANDOFF 미커밋 기록·미추적 첨부 보존, 신규 Java/Kotlin 파일0개. diff --check PASS. 커밋·푸시 없음. 다음은 사용자 폰 UI 리뷰다. 커밋 초안: feat(android): brand app as 뽀싹 with supplied launcher icon.

## Git 통합 승인
- 사용자 후속 승인에 따라 main b0a2d82 기준 codex/ppossak-branding에서 현재 브랜드 변경과 관련 인계 기록을 함께 커밋한다.
- 메시지: feat(android): brand app as ppossak with supplied launcher icon.
- Android 검증 이후 소스 변경은 없으므로 기존 JVM9개·Lint0오류/9경고·실기기 설치/표시 성공 결과를 재사용한다. 원격 CI는 backend Full 범위이며 해당 SHA 결과를 별도로 확인한다.
- PR·병합 SHA·최종 main CI는 Git 및 해당 PR/Actions 이력을 기준으로 확인한다. 운영 배포·운영 DB 변경은 승인 범위가 아니다.
