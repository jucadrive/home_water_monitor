# 수경재배 전환 실행·인수 명세

## 승인 범위
2026-10-26 사용자가 조사 브리핑의 전체0~6단계와 5단계 Kotlin·Jetpack Compose 안드로이드 앱을 명시 승인했다.
제품 정책은 ../HYDROPONIC_MVP.md. 단일 에이전트·단계별 순차 실행, 최초 승인 유지, 단계당 자동수정5회·동일원인3회 중단.
신규 도메인·V6~V8·추가 REST·기본 앱 기술 도입은 승인 범위다. 웹 UI·알림·자동제어·새 센서·운영 DB 적용·배포·커밋·푸시는 제외한다.
시작 main 2d81431, 브랜치 codex/hydroponic-migration. 사용자 MIGRATION.md의 AM(staged 추가+unstaged 내용)을 보존하며 변경하지 않는다.

## 단계·파일·인수 조건
|단계|예상 파일|인수 조건|위험|
|---|---|---|---|
|0 조사|읽기 전용|실제 소스·문서·완료 수준 구분|완료|
|1 문서|AGENTS/README/Harness/HANDOFF/설계·기존 명세·신규 MVP/이 문서|모바일 승인·정책 반영, AGENTS250줄 이하, 과거 이력 보존|낮음|
|2 재배|cultivation domain/application/infrastructure·V6·테스트|생성/조회/종료, 입력·멱등·동시성·실제 커밋 롤백|중간|
|3 관리|WaterMaintenance 관련 클래스·V7·테스트|유형/양/시각·격리·최근 전체 교체·종료와 경합|중간|
|4 연결/조회|ControlCommand/Runtime/TransactionService/DeviceControlStore·V8·조회/HTTP·테스트|예약 원자성·모든 멱등 경로·지연 결과·RETRY·소유 장치·과거 조회·MQTT 회귀|높음|
|5 Android|mobile/android의 Kotlin·Compose·Gradle·테스트|화면·서버 연동·추세·원본·명령/보존/해제 구분·로딩/오류·중복 방지|중간|
|6 통합|종단 테스트·인수도구·최종 문서|V5→신규 데이터 보존, 전체 자동검증, 실물/시뮬레이션 구분|환경 의존|

## 검증
- 문서 단계: git diff --check, 링크·정책·250줄 확인. 문서만 변경한 경우 빌드 반복 안 함.
- backend 루트 gradlew.bat compileJava compileTestJava, 관련 테스트. 저장소 scripts/verify.ps1 -Mode Unit/Full.
- V5까지 Flyway 적용→기존 기록 생성→신규 마이그레이션→기존 모든 테이블 내용·통계 보존 검증. 개발/운영 DB를 사용하지 않는다.
- Testcontainers PostgreSQL/Mosquitto로 저장·실제 커밋 실패·중복·INCOMPLETE·확인 유실·소유 장치·종료/START 경합을 검증한다.
- Android는 도구 설치 상태와 공식 호환 버전 확인 후 실제 명령을 기록한다. 빌드·단위·Lint·에뮬레이터/기기 UI는 별도로 보고한다.
- 최종 실제 흐름: 앱→REST START→ESP32→MQTT→DB→앱 조회와 물 관리/추세 표시. 환경 부재는 NOT VERIFIED, 완료 조작 금지.
- 자동 Lint/Arch는 backend MISSING 유지. 신규 Java/Kotlin200줄 초과는 리뷰 대상으로 보고한다.

## 현재 상태
- 단계1~6 소프트웨어 범위 VERIFIED(최종 backend204/Android JVM9/실기기5개 PASS). 실제 센서 통합·교정·독립 정확도는 사용자 지시로 후속 검증하며 NOT VERIFIED. 아래는 단계별 당시 상태와 실패·복구 이력이다.
- 2026-10-26 후속 사용자 지시로 현재 변경의 커밋·푸시·PR·CI 확인·main 병합까지 승인. 이 기록은 이전 ‘미승인’ 체크포인트를 갱신한다. 운영 배포·운영 DB 반영은 미승인.

## Phase Result
- 단계0: 조사·계획 사용자 승인 완료. 단계1 VERIFIED. 단계2 VERIFIED. 단계3 VERIFIED. 단계4 VERIFIED, 단계5 BLOCKED(Android UI 실행 환경), 단계6 TODO.
- 환경 초기 진단: 기본 java21, C:/dev/jdk-25.0.4.1+1 확인. Docker Linux 엔진 중지, Android SDK/adb 미확인.
- 검증: 구현 테스트 NOT RUN. 과거181/보드60/Host25는 이 전환의 새 실행 증거가 아니다.
- 변경 파일/검증/수정횟수/잔여위험은 단계 종료 시 아래에 갱신한다.
- 커밋 초안: feat: extend monitoring to hydroponic cultivation with Android client. 커밋/푸시 미승인.

### 단계1 결과
- 목적·모바일 승인·현재 정책·과거 문서 이력 표시·문서 정합성 수정. 사용자 MIGRATION.md는 그대로 보존.
- 문서 링크 확인·AGENTS109줄·git diff --check exit0/PASS. 코드 빌드 NOT RUN(문서 단계).
- 기존 실행 루프·Git 승인·수정한도 유지. 신규 완료 기능으로 허위 표시하지 않음. 상태 VERIFIED(사용자 최종 리뷰 전).

### 단계2 체크포인트
- 재배 순수 도메인·JPA 매핑·원자 등록·HTTP 생성/조회/종료·V6 추가. 등록 키(UUIDv4)를 리소스 UUID로 사용해 동시 재요청을 직렬화한다.
- status는 endedAt로 파생하며 모순되는 상태 컬럼을 중복 저장하지 않는다. 시작/종료·정규화 내용·정확한 시각을 검증한다.
- JDK25 compileJava/compileTestJava PASS. 새 테스트7개(도메인2/통합5; HTTP 실제 서버 포함) 실패0/오류0/건너뜀0 exit0/PASS.
- 첫 실행6개 중2개 실패: 종료 응답 나노초/DB 마이크로초 차이. 저장 전에 정밀도 통일 후 통과. 자동수정1/5, 동일원인 실패1회.
- Gradle 캐시 접근은 샌드박스 거절 후 승인 실행. Docker Desktop은 사용자 명령 승인으로 기동했다. 개발 DB/운영 DB·보드 변경 없음.
- 기존 정상 저장 회귀 실행 중. 커밋/푸시 없음. Java200줄 초과 없음.

### 단계2 회귀·단계3 결과
- 기존 SaveNormalTdsSessionServiceIntegrationTest3개 실패0/오류0/건너뜀0 exit0/PASS. 단계2 VERIFIED.
- 관리 이력 도메인·JPA·원자 등록·REST·V7, 전체/부분 교체와 마지막 전체 교체·종료 직렬화 구현.
- cultivation 전체14개(도메인4/통합10) 새 실행 실패0/오류0/건너뜀0 exit0/PASS. 단계3 자동수정0/5, 신규 Java200줄 초과 없음.
- 단계4 진입: 기존 MQTT/원본/통계와 프로세스 트랜잭션 경계를 보존하고 명령 예약에 재배 FK를 추가한다.

### 단계4 결과
- V8 nullable START 재배 FK/고유 세션 인덱스, 예약 트랜잭션 내 활성 검증·잠금, 모든 런타임 멱등 경로의 대상 비교, 수신 장치 소유 검증, 조회·추세·원본·대시보드 구현.
- 신규 단위/통합/마이그레이션/MQTT 테스트를 포함한 전체 build 새 실행204개 실패0/오류0/건너뜀0 exit0/PASS. compile/bootJar/jar/test/check/build 실제 실행(processResources만 UP-TO-DATE).
- V5의 기존9개 tds 테이블 모든 기존 열·행을 보존했다. 원본·통계·진단·명령·관측·부팅·누락 자료를 비교했다. 원격CI/실물/앱은 이 결과에 포함되지 않는다.
- 수정1: Repository가 입력 예외를 영속성 예외로 변환하던 문제→저장은 장치 일치 여부 반환, application이 입력 거절. 대상 테스트32개 중1개 실패 후 수정.
- 수정2: 새 MQTT 시험이 제어 전용 파서로 결과 메시지를 읽음→일반 JSON parser와 장치/startedAt fixture를 정상화, 실패 시 다른 시험에 남긴 예약 정리. 첫 Full204개 중2개 실패(근본1+연쇄1) 후 전체 통과.
- 편집 도구의 기본 cp949 읽기 오류1회는 UTF-8 명시로 정정. 검증기반 자동수정2/5, 각 근본원인 실패1회. 테스트 삭제·조건 완화 없음.
- 기본 추세/최근 측정은 알려진 실측(sen0244-uncalibrated-v1)의 NORMAL만 선택한다. 다른 교정/출처는 이력에서 확인하며 자동 실측 판정하지 않는다.
- 신규 main Java200줄 초과 없음. 기존 V1~V5·펌웨어·MQTT 스키마 변경0. 커밋/푸시 없음.

### 단계5 결과·환경 BLOCKED
- Kotlin/Compose 앱: 서버·장치 설정, 재배 목록/등록/상세/종료, 관리 이력/등록, 실측 추세·전체 교체 표시, START/명령/보존/해제/장치 상태, RETRY, 미연결 이력·원본 조회 구현.
- 전송 전 키/본문/서버를 private SharedPreferences에 동기 저장한다. 불명 응답은 같은 요청 재시도, 서버 변경 차단, 명시적 추적 중단은 취소가 아님을 표시한다.
- 2026-10-26 사용자 별도 승인: 디버그의 localhost/127.0.0.1/10.0.2.2만 HTTP 허용. includeSubdomains=false, 릴리즈 HTTPS/인증서 검증/리다이렉트 자동추적 금지 유지.
- SDK 명령 승인으로 도구·API36·build-tools35·adb·에뮬레이터37.2.12·AOSP API35 rev2를 .tools에 설치했다. SDK 다운로드 SHA256 검증. 시스템 Windows 기능/드라이버/재부팅·실물 보드는 변경하지 않았다.
- JVM9개 새 실행 실패0/오류0/건너뜀0 PASS. 실제 소켓 HTTP/키/UTF8/오류/리다이렉트, 멱등 재시도/프로세스 재생성/저장실패, 주소·날짜·JSON null/원본을 검증했다.
- Android Lint 오류0/경고12 PASS. 경고는 고정 호환 버전의 업데이트 안내9개와 commit 반환값을 확인하는 SharedPreferences KTX 권고3개다. 규칙 비활성화·baseline 없음. SDK XML4/AGP XML3 도구 경고는 별도 남는다.
- assembleDebug/assembleRelease PASS, 디버그 APK와 미서명 릴리즈 APK 생성. assembleDebugAndroidTest PASS: Compose4개 시험은 컴파일만 완료, 실행 NOT RUN/NOT VERIFIED.
- verify-android.ps1 Full 실제 진입점 exit0/PASS. 최종 확인은 105 tasks 중2 실행/103 UP-TO-DATE이며 JVM9개 등의 새 실행은 그 앞 성공 실행 결과다.
- 수정1/5: Android JVM 테스트 boot classpath에 없는 JDK HttpServer→표준 ServerSocket fixture로 같은 실제 HTTP 인수 조건 유지.
- 수정2/5: Lint4개 오류(local.properties 드라이브 콜론/세 domain 속성) 수정. 앱 아이콘·백업 제외 규칙도 보완했다. 숫자 원본 JSON 파싱은 코드 리뷰에서 명시 변환으로 보완했다.
- 신규 Java/Kotlin200줄 초과0. Android 실제 화면/접근성·앱→서버→MQTT 종단·실물 측정은 미검증이다.
- 환경 진단: emulator -accel-check는 코드6와 WHPX 기능 확인 필요 메시지. 소프트웨어(-accel off/SwiftShader) 시작 시도는 부팅 전에 종료했고 직접 실행 exit1. 정확한 종료 원인은 미확정. ADB devices는 비었으며 Connected 진입점 exit1(선택 기기 없음), 시험 실패와 환경 부재를 구분한다.
- 필요한 조치: USB 디버깅이 승인된 Android 테스트 기기 연결 또는 정상 부팅되는 에뮬레이터 준비. Windows 기능·드라이버 변경 및 재부팅은 별도 승인 대상이므로 수행하지 않는다.
- Harness의 환경 BLOCKED 중단 규칙에 따라 단계5에서 대기한다. 정상 기기 확보 후 Connected4개 실행·화면 검수·실제 앱 연동을 마무리하고 승인된 단계6을 진행한다. 새 전체 계획 승인은 필요 없다.
- Git 커밋/푸시/배포/운영 DB 변경 없음. 사용자 MIGRATION.md AM 보존. 커밋 초안 유지(최종 인수·리뷰 전 커밋하지 않음).
- 종료 정리: 이번에 시작한 ADB 서버 종료 exit0. 에뮬레이터는 종료 상태다. Docker Desktop 엔진은 유지하며 다른 서비스·볼륨은 변경하지 않았다. 최종 diff/문서 링크/신규 소스 공백 검사 PASS, AGENTS112줄, Git 제외 SDK/local.properties/APK 확인.

### 실기기 재개·단계5 인수 — 2026-10-26
- 사용자 실제 폰 연결·USB 디버깅·PC 승인을 확인하고 SM-S947N(Android17/API37)을 명시 지정했다. 동시에 연결된 에뮬레이터는 사용하지 않았다.
- 최초 Connected4개는 Espresso의 제거된 InputManager.getInstance 호출로 모두 실패(동일 근본원인1회). 공식 AndroidX Test 릴리즈 근거로 Espresso3.7.0/runner1.7.0/ext.junit1.3.0으로 수정했다. https://developer.android.com/jetpack/androidx/releases/test
- 단계5 자동수정3/5(기존2회+Android17 호환1회). 테스트 조건·횟수 유지. ADB 서버 재시작으로 unauthorized가 발생했고 사용자 폰 재승인 후 재개했다(환경 실패, 시험 미실행).
- Connected 화면4개 새 실행 실패0/오류0/건너뜀0 exit0/PASS. UI 등록·보충 의미·불완전 통계null·실측/가상 추세 격리 확인. 단계5 VERIFIED.
- 사용자 추가 지시: 실제 ESP32·ADS1115·SEN0244 통합은 나중에 검증한다. 단계6은 실제 폰+실제 서버/DB/MQTT+모의 장치의 소프트웨어 종단 범위로 진행하며 실물/정확도는 NOT VERIFIED 유지한다.
- 단계6 IN_PROGRESS: 임시 Testcontainers PostgreSQL/Mosquitto와 루프백 서버를 구성했다. 생성 데이터는 simulated-v1만 사용하고 실측으로 위장하지 않는다. 개인 앱 설정은 보존·복원하며 기존 미확정 요청이 있으면 시험을 거절한다.

### 단계6 소프트웨어 종단 결과 — 2026-10-26
- 신규 AndroidAcceptanceServer/Gradle JavaExec 작업은 테스트 classpath에서만 실행한다. 임시 PostgreSQL18·Mosquitto2.0.22·실제 Spring 서버를 루프백으로 제한하고 simulated-v1 장치와 미연결 INCOMPLETE fixture를 제공한다. 30분 제한/stop 파일·자원 자동 종료를 지원한다. 운영 부트 경로는 변경하지 않았다.
- Android AppFlowTest와 목록 semantic tag, verify-android.ps1 EndToEnd를 추가했다. 재배 생성·전체 교체500ml·START·NORMAL 보존·RELEASE_CONFIRMED·원본60/통계50·가상값 실측 추세 제외·종료 후 등록 차단·과거 INCOMPLETE0/통계 없음 조회를 실제 폰에서 검증했다.
- 최종 합동 새 실행5개(Forms4+AppFlow1), 실패0/오류0/건너뜀0, exit0/PASS. connectedDebugAndroidTest는 새 실행, 컴파일 등70개 작업은 UP-TO-DATE 재사용했다. 실행31초. XML과 캡처는 Git 제외 .tools/android-acceptance/phone-2/results 및 screens에 보존했다.
- normal-result/cultivation-detail/legacy-incomplete 캡처3장 육안 검수 PASS. 한글·NORMAL/SIMULATED·가정 수온·원본/안정화 제외·불완전 통계 없음·가상값 추세 제외 표시를 확인했다. 스크롤 화면 전체·모든 기기/글꼴/접근성 인수를 완료했다고 주장하지 않는다.
- 단계6 자동수정3/5: (1) 종료 뒤 LazyColumn이 아래에 있어 헤더 assertion 시간 초과→처음으로 스크롤, (2) executeShellCommand가 셸 연산자를 해석하지 않아 캡처 보존 실패→개별 명령, (3) 앞 실패가 만든 동명 디렉터리와 충돌→새 accepted- 파일명·바이트 일치 확인. 각 근본원인 실패1회, 테스트 삭제·조건 약화 없음.
- 환경/명령 실패 별도: Gradle -P 속성을 따옴표 없이 전달해 빈 값으로 구성 실패→인수 전체 인용. USB 단절/unauthorized로 미실행된 시도는 NOT RUN이며 사용자 재연결·승인으로 복구했다. 첫 서버30분 만료 뒤 AppFlow 접속 시간 초과1회(Forms4 통과)는 새 임시 서버로 복구했다.
- Android 최종 Full exit0/PASS: JVM9개 새 실행 실패0/오류0/건너뜀0, Lint0오류/11경고, debug/미서명 release APK 성공. 105개 작업 중19개 실행/86개 UP-TO-DATE. Android17 호환 테스트 의존성 갱신으로 업데이트 경고가 기존12개에서11개로 줄었다.
- 임시 서버 정상 종료(stopped 확인), 전용 컨테이너 종료, 생성한 adb reverse만 제거했다. 실제 폰에 디버그 앱을 재설치·실행했다. 사용자의 다른 ADB/에뮬레이터/Docker 엔진은 유지했다.
- 실제 ESP32·ADS1115·SEN0244 및 교정·독립 정확도는 사용자 지시로 후속 검증하며 NOT VERIFIED다. 펌웨어·기존 V1~V5·MQTT 공개 계약 변경 없음. 커밋/푸시/배포/운영 DB 변경 없음. 사용자 MIGRATION.md AM 보존.
- 신규 Java/Kotlin200줄 초과 없음. 변경 파일: backend 테스트 서버/build.gradle, Android 테스트·목록 tag·테스트 의존성, scripts/verify-android.ps1, README/ANDROID_SETUP/HANDOFF/이 명세. 커밋 초안: feat: extend monitoring to hydroponic cultivation with Android client.
- 백엔드 최종 JDK25 build --no-daemon --console=plain exit0/PASS, 204개 새 실행 실패0/오류0/건너뜀0. 3분43초, test1개 실행/컴파일·패키징6개 UP-TO-DATE. 단계1~6 소프트웨어 범위 VERIFIED, 사용자 최종 리뷰 전 DONE으로 표시하지 않는다.
