# Android UI 사용자 검수·Git 통합 승인 — 2026-10-28

- 사용자가 수정 APK 확인 후 만족 의사를 표시하고 현재 UI 변경의 커밋·푸시·PR·main 병합을 명시 승인했다. UI 작업 DONE. 승인 범위는 소스·테스트·검증 스크립트·문서이며 실제 비밀 설정·APK·로컬 캡처/검증 산출물은 제외한다.
- 브랜치 codex/android-ui-simplification, 기준 main685325b와 원격 main 일치 확인. 커밋 메시지 `feat: simplify owner login and measurement UI`. 이미 통과한 Android Full/JVM17·UI13·격리 종단14와 사용자 APK 검수를 재사용하고 푸시 SHA/PR/main의 backend Full CI를 별도로 확인한다. 현재 원격 CI는 Android 검증을 실행하지 않는다.
- 최종 커밋·PR·병합 SHA와 원격 CI 결과는 Git/PR/Actions 이력이 기준이다. 서버 원격 조작·재배포·강제 Git 작업·브랜치 삭제는 포함하지 않는다.

---

# Android UI 종단 재검증 — 2026-10-28

- 사용자 Docker 재시작 후 실행 지시. Docker29.8.1·JDK25 전용 backend/임시 PostgreSQL18·MQTT와 API35 emulator-5554/JDK21 Android로 새 실행했다. 운영 서버·DB·실물 접근 없음.
- EndToEnd Forms13+AppFlow1=14개 새 실행 PASS, 실패0/오류0/건너뜀0·exit0·1분33초. 로그인/장치 설정/재배 생성/전체 교체/모의 START/60원본·50통계/NORMAL·버퍼 해제/출처 구분/재배 종료/불완전 과거 조회 인수 PASS. 이전 Docker/JDK 차단 해소, UI·격리 종단 VERIFIED.
- `.tools/android-acceptance/ui-e2e-20261028-1`에 결과 XML/로그·캡처10장 보존. 메인/정상 결과 시각 검수 PASS. stop→stopped=closed/서버exit0·이번 시험 DB/MQTT 컨테이너 제거·이번 adb reverse 해제 확인. 속성 없는 assembleDebug exit0으로 고정 HTTPS 기본 빌드 복원. 배포용 기존 `.tools/ui-20261028/ppossak-ui-debug.apk` 유지.
- 상세 [tasks/ANDROID_UI_SIMPLIFICATION.md](tasks/ANDROID_UI_SIMPLIFICATION.md). 코드 수정 없음·커밋/푸시 없음·작업 브랜치/기존 unstaged 보존. 실제 폰/운영HTTPS/실물 재검증은 이번 범위 밖이며 전체 DONE은 사용자 리뷰 후다.

---

# Android 돌봄 UI 간소화 — 2026-10-28

- 사용자 전체 계획 승인 후 로그인→메인→측정 결과를 순차 수정했다. 명세 [tasks/ANDROID_UI_SIMPLIFICATION.md](tasks/ANDROID_UI_SIMPLIFICATION.md). 서버 주소는 번들 HTTPS로 고정, 장치 설정은 인증 후 제공하며 기존 미확정 요청의 원래 origin·키·본문은 보존한다. debug loopback 인수만 빌드 속성으로 주입하며 release 고정 HTTPS/인증서 검증은 유지한다.
- 상단바/공통 소개의 식물 장식 제거·기본 브랜드 `뽀싹 돌봄 일지`, 로그인 라이선스 버튼/HTTP 안내 제거(라이선스 자산 유지). 결과는 대표값/출처/시각근거/보존/측정한계 요약과 펼침 상세로 분리했다. 원본 영역/안정화 제외는 유지했다.
- Android Full exit0: JVM17개 새 실행·실패0/오류0/건너뜀0, debug/미서명 release PASS, Lint0오류/11경고. API35 emulator-5554에서 FormsTest12개 + 후속 메인 테스트1개 새 실행 PASS(각 exit0, 실패/오류/건너뜀0). 로그인·결과·320dp/1.6배 글꼴·메인 캡처5장 검수 PASS. 최초 Gradle URI 이름 충돌1회 수정 후 성공.
- UI 인수 VERIFIED, 전체 DONE은 사용자 리뷰 후. EndToEnd NOT VERIFIED: Docker Linux 엔진 pipe 부재; 별도 서버 시도는 JDK21 지정으로25 toolchain 탐색 실패. 시험 DB/브로커는 생성되지 않았다. Docker 엔진+JDK25 준비 후 별도 재검증 가능하다. 실제 폰 설치/공개HTTPS 변경 후 검증은 NOT RUN이다.
- 결과 APK `.tools/ui-20261028/ppossak-ui-debug.apk`, 캡처 `.tools/ui-20261028/screens/`, 상세 명세에 해시와 명령 기록. 신규 Java/Kotlin200줄 초과 없음·diff 검사 PASS·backend/펌웨어 변경 없음·서버 원격 조작 없음. 브랜치 codex/android-ui-simplification, 변경 unstaged·커밋/푸시 없음. 커밋 초안 `feat: simplify owner login and measurement UI`.

---

# 인증·배포·장비·문서 Git 통합 — 2026-10-27

- 사용자 명시 승인: 지금까지의 코드/문서/배포 양식을 비밀·개인 설정·위험 산출물을 제외하고 stage/commit/push/PR/main 병합까지 수행한다. 기존 codex/single-user-auth-deployment를 사용하며 force push·브랜치 삭제·서버 배포는 포함하지 않는다.
- 포함 범위: backend owner 인증/HTTP 테스트·Android Keystore/login/미확정 요청 보존·Docker3서비스+MQTT/LAN TLS 추가 구성·개인 ESP32 설정/시간/만료 검증·수동 배포/실물 인수·백엔드 기술 가이드/검증 도구.
- 제외 범위: 실제 auth/DB/MQTT/Tunnel 파일·CA/서버key·Wi-Fi/개인 헤더·sdkconfig 개인본/백업·JAR/APK/bin·빌드/SDK 캐시·로컬 증거/도구·첨부 이미지. 루트 ignore에 개인 파일·첨부·배포 산출물 보호를 추가하며 공개 시험 fixture와 설정 생성 코드는 포함한다.
- 기존 성공 근거: backend216·Android JVM15/Lint0errors11warnings/에뮬레이터9, 설정 도구·독립 Docker/MQTT/TLS 인수, 최종 ESP32 build/기간 검증 guard/실측60·보존/해제. backend/앱 변경 없이 성공 검사를 임의 반복하지 않고, 푸시 SHA 및 PR/main의 backend Full CI를 확인한다. 정적/아키텍처 검사는 MISSING이다.
- 현재 인수 결과는 아래 체크포인트와 기능 명세를 따른다. 최종 커밋·PR·병합 SHA와 CI는 Git/해당 PR/Actions 이력이 기준이다. 커밋 초안: feat: add personal authentication and secure ESP32 deployment.

---

# 개인 서버·ESP32 정상 경로 검증 — 2026-10-27

- 사용자 집에서 재개·전체 계획 승인. 서버 원격 조작 금지는 유지하며 사용자 수동 서버 적용, LAN TLS/개인 설정/업로드/실측 인수만 진행한다. 상세 tasks/PERSONAL_DEVICE_ACCEPTANCE.md·PERSONAL_DEVICE_SETUP.md.
- CH340 COM3, UART STATUS IDLE/sessionnone/원본0, 기존 Wi-Fi/MQTT 미연결 확인. 기존 미커밋·첨부/인덱스·bench 비밀/header를 보존했다. 사용자 로컬 숨김 Wi-Fi 입력 완료.
- TLS 추가 Compose·RFC1918 CA/서버 인증서/개인 헤더 도구·SNTP 후 MQTT gate·Personal 빌드 옵션을 구현했다. 특정 LAN8883만TLS, 내부1883/기존계정·ACL·볼륨·DB loopback 보존. authority/ca.key는 PC에만 유지한다.
- 로컬 단위5개/실제 TLS 인수5그룹 모두 새 실행PASS·exit0, 독립 시험 자원 정리exit0. 잘못된 CA/peer IP·계정·금지 발행 거절. 결과 .tools/deployment/ppossak-tls-check-9833687675/result.json. 최초 해시 도구 준비1회 실패의 원인은 미확정, 독립 공개 fixture 성공 후 같은검증PASS. 상세 명세 참조.
- 고정 IDF credential-free 및 개인 설정 포함 ADS1115/MQTT 빌드PASS. 실제 개인 binary948576bytes·앱1MiB의10% 여유. 자동 Flash1회는 wrong boot mode0x13으로 쓰기 전 실패했으나, 사용자 수동 BOOT/EN 후 Flash exit0/각 이미지 hash verified/PASS. NVS 전체 삭제 없음. 사용자가 서버 LAN TLS 적용/포트를 확인했다.
- 최초 UART 원본seq1~60/각1회·fault0·해제/IDLE PASS, 사용자 앱 ACCEPTED/NORMAL/RELEASE_CONFIRMED/FRESH/60/ADS1115 및 DB NORMAL/원본60/통계50/실제60/sen0244-uncalibrated-v1 확인. 첫 증거 .tools/esp32-personal/accept-b574333ab0304a3ab9a23c15aa6ae167.json. 단 최종 검토에서 SDK 기간 검증 옵션OFF를 발견해 아래 수정 후 다시 인수했다.
- 보안 보완1/5: SNTP만으로 기간 검증이 켜진다고 가정한 부분 정정. 개인 sdkconfig.personal에 MBEDTLS_HAVE_TIME_DATE=y 강제·기본값 명시·main guard 추가, 기존 bench config 보존. guard OFF거절/ON통과 C 전처리 검사PASS, 최종 전체1024작업 빌드PASS·설정 헤더확인·ESP_TLS_INSECURE 꺼짐. 최종binary949632bytes·파티션9%여유, SHA256 B5728F01D447BAC37A5659FDE36E499E8C1862DCFBD3A6343F987AC62359990F.
- 최종 수동 Flash/hash verified/PASS 후 실제 TLS/MQTT 재연결·정상 실측 회귀1회 PASS. 직접 seq1~60/센서fault0/보존 확인 뒤BUFFER_RELEASED/최종IDLE·원본0·MQTT유지, min_heap40752. 최종 증거 .tools/esp32-personal/accept-15c5c1a8f96a45c4873d83e0672d6c02.json. 사용자 앱 ACCEPTED/NORMAL/RELEASE_CONFIRMED/원본60/ADS1115 확인. 두 번째 DB 직접 조회/전 필드 값 대조는 NOT RUN.
- read-only 관측기 단위3개 PASS, TLS/개인 설정 단위5개·로컬 TLS5그룹 PASS. 종합 근거 .tools/esp32-personal/normal-flow-verification.json. 초기 TIME_READY 부팅 로그 자체는 미수집이며 SNTP gate/기간 옵션과 실제 TLS 준비/송수신 경로로 판단한다. 정상2회와 로컬 인수 범위 VERIFIED, 장기/전체 장애/교정/독립 정확도·실수온은 별도 NOT VERIFIED다.
- 신규6파일 TLS 업로드 묶음은 .tools/deployment/mqtt-lan-upload. 서버 적용/앱/DB는 사용자 보고이며 에이전트 SSH·원격 명령0·커밋/푸시0. 기존 미커밋/인덱스/첨부 보존·AGENTS118줄·적용 마이그레이션/센서·세션 도메인 변경0. 다음은 정상 사용 관찰과 별도 교정/장기/장애 검증 계획이며 전체 DONE은 최종 리뷰 후다.

---

# 백엔드 기술 해설·검증 안내 — 2026-10-27 (이전 체크포인트)

- 문서 범위 VERIFIED: docs/backend/README.md부터 ARCHITECTURE.md·EXECUTION_FLOWS.md·VERIFICATION_WORKBOOK.md로 연결한다. 상세 인수/검증은 tasks/BACKEND_TECHNICAL_GUIDE.md. 기존 설계/구현 이력은 보존하고 현재 소스의 근거·실제 의존성·대안·한계·실행/검증 경계를 별도로 설명한다.
- 내용: 단일 프로세스/DB 구조, domain/JPA 분리·BigDecimal, 트랜잭션 프록시/PK 경합 복구, HTTP 멱등성, MQTT 커밋/확인·유실, 비동기 명령/관측, 인증 필터/메모리 토큰, 조회 출처/시각/비용. 기동+주요7개 기능을 메서드·스레드·트랜잭션·정상/실패 종료까지 추적한다.
- 실습은 예측→추적→로컬 재현→관측→한계 설명8종, 추가 Java 테스트 예시·기록 양식. 운영 DB/서버 조작을 실습에 포함하지 않으며 문서 완성을 독자의 학습/재현 완료로 판정하지 않는다.
- 최종 문서 검사 exit0/PASS: 로컬 링크/앵커84개·현재 클래스/메서드 참조62개·테스트 선택 대상15개 존재 확인, Java 예시1개 JDK25 컴파일/직접 호출 assertion PASS. 결과 .tools/backend-guide/result.json. Gradle/통합/전체 테스트 새 실행은 아님. 임시 검사기에서 '.java' 링크를 메서드로 오인한 부분1회 보완 후 PASS.
- 변경은 가이드4종·진입 링크·기술 작업/인계 문서뿐이다. 이번 작업의 애플리케이션/배포/DB/펌웨어 변경·서버/장비 접근0. 기존 미커밋·인덱스/첨부 보존, 커밋/푸시0, AGENTS117줄 유지. 다음은 가이드 읽기와 독립 로컬 실습이다.

---

# 실물 없는 개인 서버 MQTT 준비 — 2026-10-27 (이전 체크포인트)

- 사용자 승인 범위 구현·로컬 인수 VERIFIED. 실제 미니 PC SSH/원격 조작/운영 MQTT 적용·ESP32·LAN/TLS·실측/펌웨어 업로드 NOT RUN. 서버 적용은 사용자 직접 진행하며 상세 docs/PERSONAL_MQTT.md·tasks/PERSONAL_MQTT_READINESS.md.
- 사용자 보고: 기존 HTTPS 배포·앱 로그인/API·SSH/DBeaver DB 조회/식물 등록 데이터 확인 성공. 에이전트 직접 관측이 아니며 새 MQTT 또는 실물 인수의 근거로 확대하지 않는다.
- infra/personal/compose.mqtt.yml로 기존 compose.yml에4번째 broker를 추가한다. 기본파일/DB15433 loopback/기존 인증 원본/운영 볼륨을 교체하지 않는다. MQTT/제어는 CLI 속성으로 활성화하며 기존 운영 JAR/DB 스키마/메시지 계약/펌웨어 변경0. 새 JAR 전송/빌드 불필요.
- Mosquitto2.0.22 비루트 UID1883·읽기 전용·전용 영속 볼륨·인증/장비별 방향 ACL. 호스트/LAN/WAN 포트 미공개. scripts/create-personal-mqtt.py는 NEW MQTT 전용 파일만 생성하며 덮어쓰기 거부, 원문 비밀번호는 stdin으로 해시 도구에 전달하고 명령 인수/출력에 포함하지 않는다. 장비용 device-mqtt.json은 개발 PC에만 보관한다.
- 단위5개 새 실행 PASS(실패0/오류0/건너뜀0·exit0). 기존 파일 보존·입력 주입 거절·실패 시 신규 파일만 정리·방향 ACL·비밀번호 stdin/분리/해시 파일 평문 미포함 검증. 최초4개 PASS 이후 보강한5개는 sandbox 임시 폴더 권한으로4errors, 코드 수정 없이 승인 환경 재실행5PASS. 환경 문제/검증 조건 약화 없음.
- Docker 새 종단 인수9그룹 exit0/PASS: 실제4서비스 설정/포트미공개·백엔드 로그인 유지·MQTT 익명/오류 비밀번호 거절·방향/타장비 발행 ACL·금지 읽기, 최초/브로커 재시작/백엔드 재시작의 START→상태→결과→DB커밋 확인 후 보존 확인→해제, SIMULATED 표시/실측 추세 제외,3세션180원본/재배 보존·이전 앱 토큰 거절. 결과 .tools/deployment/ppossak-mqtt-check-353e1d1da9/result.json.
- 추가 실제 Linux 파일 권한 인수1개 exit0/PASS: root:1883/640의 passwd/ACL을1883만 읽고 다른 UID10001은 거절. 같은 폴더 permissions.json. 실험 파일은 독립 시험 계정만 사용하며 사용자 실제 비밀은 읽거나 수정하지 않았다. 모든 이번 시험 컨테이너/볼륨 정리 exit0.
- Mosquitto digest sha256:09b62ff32c2e7b64d4b431b9a89236073289b68db273de2b089edeb9b0d92b5c, Paho는 운영 JAR의1.2.5 재사용. 새 외부 테스트 라이브러리 도입0. backend/Android/펌웨어 소스 무변경으로 기존 Full 결과 재사용, 실패기반 코드수정0/5·같은 원인 반복0. backend 정적/아키텍처 자동 검사는 MISSING 유지.
- Git codex/single-user-auth-deployment, 기존 미커밋/staged 상태·첨부 보존, 이번 커밋/푸시0. AGENTS117줄. 다음: 사용자가 신규5개 파일만 업로드하고 문서3~6단계 적용/기존 API 확인. 실제 장비는 집에서 LAN/TLS·Wi-Fi·개인 서버용 펌웨어 설정을 확정한 뒤 인수한다.

---

# Docker 수동 배포 전환 — 2026-10-27 (MQTT 추가 전 이력)

- 사용자 승인: 생성한 인증 파일을 유지하고 Dockerfile·backend/PostgreSQL/cloudflared 3개 서비스 Compose·수동 안내를 작성하며 로컬 검증한다. 미니 PC SSH·원격 조작·Cloudflare 실제 연결은 수행하지 않는다.
- 상태 VERIFIED(로컬 Docker 범위), 실제 미니 PC·Tunnel·공개 HTTPS·폰 인수 NOT RUN. 전체 DONE은 사용자 리뷰 후다. 기존 인증·앱 검증 결과는 아래 이력 그대로 유효하며 이번에는 업무 소스를 변경하지 않았다.
- docs/PERSONAL_DEPLOYMENT.md를 Windows 준비→WinSCP→Ubuntu→Cloudflare→앱 순서로 다시 작성했다. 이미 만든 auth.env/postgres.env/tunnel.token을 재생성/수정하지 않는다. Compose>=2.30 필요, raw env_file로 BCrypt '$' 보존. 호스트 JDK/cloudflared 설치·포트 공개 없음. 토큰 파일 root:65532/640 권한 안내 포함.
- infra/personal: Dockerfile(빌드 단계 Java25 JDK/실행 JRE), Healthcheck.java(19줄), .dockerignore, 3개 서비스 compose.yml. 이전 systemd 양식/server.env.example 제거. API backend:8080·DB postgres:5432, backend 비루트·읽기 전용·tmpfs·capability 제거. DB 전용 볼륨·로그 회전·재시작·준비 상태 의존성.
- scripts/verify-personal-docker.py 최종 exit0/PASS: 새 실행 9개 인수 그룹(3개 서비스/포트 미공개, 이미지/기동/실제 해시 보존, 401/잘못된 비밀번호/로그인, 재배 생성/조회/동일키 재시도, 컨테이너간 DNS 접근, DB/backend 재시작 후 데이터/토큰, 별도 DB 백업 복원, 오프라인 Tunnel 옵션/마운트 읽기, Linux 토큰 권한). 실제 비밀은 읽지 않고 도구가 생성한 격리 시험 계정을 사용했다.
- 결과 .tools/deployment/ppossak-check-640404a8fcc5/result.json. 앞 4회는 검증 코드 문제로 중단: config 출력의 '$$' 표시, Windows 기본 인코딩, 재시작 임시 포트 재할당, 상세 응답/DB 열 이름 가정. 검증 코드 보완4/5·동일 원인 반복 없음. 실제 앱 조건을 약화하지 않고 마지막 전체 9그룹 통과. 각 시도 격리 자원 정리 exit0; 기존 사용자 컨테이너/볼륨은 변경하지 않았다.
- 이미지: cloudflared 2026.10.4, digest c6c7ae1d5dfdcb094ad344ed68ccffc26635c9c6be80965664c33baf064b0515; postgres:18 digest 74935e72241653ca55e0414067e6d8763aceb8a810eb51b452253ec3dcfc4336. Java25 JRE/JDK 다이제스트는 작업 명세 참조. 태그는 가변이므로 서버의 실제 이미지도 별도 운영 기록한다.
- Git codex/single-user-auth-deployment: 기존 미커밋/staged 구분·첨부 유지, 이번 커밋/푸시0. 커밋 초안: feat: add single-owner authentication and Docker deployment. 다음은 사용자가 안내 1~7단계 실행 후 HTTPS/앱 인수 결과 확인이다.

---

# 개인용 인증 구현·수동 배포 인계 — 2026-10-27 (Docker 전환 전 이력)

- 사용자 최신 지시: 미니 PC 배포는 사용자가 직접 수행한다. 에이전트 SSH·원격 조작 중단, 이후 접속/원격 명령0. 별도 사용자 재개 지시 전 원격 접속하지 않는다.
- 단계1 문서·2 backend 인증·3 Android 연동 VERIFIED. 단계4는 수동 자료 제공 VERIFIED/실제 배포 NOT RUN, 단계5 공개 HTTPS·실제 폰 인수 TODO. 실물 장비·교정·독립 정확도는 계속 보류/NOT VERIFIED다. 상세 tasks/SINGLE_USER_AUTH_DEPLOYMENT.md.
- Backend 최종 JDK25 build exit0/PASS:216개 새 실행·실패0/오류0/건너뜀0,4분39초·8작업 중6실행/2재사용. Spring Security·단일 계정·토큰 만료/폐기/제한·HTTP 접근 제어·personal 프로필·기존 저장/재배/MQTT 회귀 포함. main 계정/비밀번호 해시 설정 누락 시 HTTP 서버 기동 거절, 비활성 우회 없음.
- Android JVM15개 새 실행 PASS, debug·미서명 release·테스트 APK PASS. Full134작업 중52실행/82재사용. 마지막 Lint 오류0/경고11·28초·3실행/27재사용. 최종 에뮬레이터9개(Forms8+AppFlow1) 새 실행 PASS·실패0/오류0/건너뜀0, 실제 Keystore·로그인/재배/관리·모의 측정 종단 검증. 상세 화면 전환 대기 보완1/5, 최초 실패8PASS/1FAIL도 보존.
- Android APK .tools/android-acceptance/auth-1/ppossak-auth-debug.apk. 캡처/결과는 동일 폴더 screens/results, 첫 실패는 attempt-1. 새 APK는 에뮬레이터에 설치했고 현재 실제 폰은 연결되어 있지 않다. 기존 폰 앱의 인증 지원 업데이트/HTTPS 인수는 사용자 배포 후 필요하다.
- 수동 배포 안내 docs/PERSONAL_DEPLOYMENT.md, 양식 infra/personal, 비밀 파일 숨김 생성 도구 scripts/create-personal-secrets.py. 로컬 도구2개·Compose config 문법·Python 구문·diff --check PASS. Linux systemd/cloudflared 실제 실행 NOT RUN. 비밀번호·Cloudflare 토큰을 수집하지 않았다.
- 운영 JAR는 backend/home-water-monitor-backend/build/libs/home-water-monitor-backend-0.0.1-SNAPSHOT.jar. personal 프로필 포함·테스트 계정 파일/클래스 제외 확인. SHA256 615B29993F56992FCB913A3D5029E2997082A98B76CA30EF84D067D57384AFF6.
- 시험 서버 stop/stopped·정상 종료 확인, 이번 ADB reverse만 정리. 에이전트 임시 SSH 개인키 폐기, 서버에 등록한 공개키 한 줄 제거는 사용자 직접 수행 안내. Docker 엔진/다른 서비스/볼륨 보존. 서버·Cloudflare·DNS·공유기 실제 변경 없음.
- Git: codex/single-user-auth-deployment, 기준fc09362. 변경 unstaged·신규 파일 포함, 미추적 첨부 보존, 커밋·푸시 없음. AGENTS116줄·신규 Java/Kotlin200줄 초과0·기존 마이그레이션/펌웨어 변경0. 사용자 최종 리뷰 전 전체 DONE 아님.
- 다음: 사용자 수동 배포·서버 구성/HTTPS API/실제 폰 인수 결과 확인. 원격 조작·커밋을 임의로 재개하지 않는다.

---

# 개인용 인증·앱 연동·HTTPS 배포 — 승인 계획 진행

- 사용자 전체 계획 승인: 문서→단일 계정 인증→Android 연동→미니 PC/Tunnel→HTTPS API 인수. 실물 장비 시험은 보류한다. 커밋·푸시는 이번 작업 별도 승인 전 수행하지 않는다.
- 기준 main fc09362(PR13 병합·최종 CI 성공 확인), 브랜치 codex/single-user-auth-deployment. 미추적 첨부 폴더 보존. 상세 계약·인수·수정 한도는 tasks/SINGLE_USER_AUTH_DEPLOYMENT.md.
- 제품 목적: 본인 생활의 불편을 개인용 뽀싹 앱·서버로 해결하고 AI 에이전트와의 설계·검증 경험을 실제 근거로 기록한다. 다중 회원·성과 과장·기존 이력 삭제 없음.
- 단계1 문서 VERIFIED: 목적/승인 경계/인증·앱·배포 계약 반영, diff --check PASS·AGENTS115줄. 단계2 IN_PROGRESS: Spring Security Bearer·단일 계정/BCrypt·프로세스 토큰·로그인 제한/폐기 구현, compileJava/compileTestJava PASS. 인증 관련 시험 실행 중이며 통과 여부 미확정. 단계3~5 TODO.
- Ubuntu24.04 LTS 추정·Intel N150·Docker·PuTTY SSH 가능·ppossak-api.jucadrive.com은 사용자 정보이며 실제 서버 접근/환경은 아직 확인하지 않았다. 기존 운영 서비스/포트/DB를 변경하지 않는다.
- 현재 Docker 엔진이 꺼져 있어 테스트용 Docker Desktop 기동 명령 승인을 받아 시작했다. 테스트는 격리된 Testcontainers만 사용한다. 실제 서버·Cloudflare·장비는 조작하지 않았다.
- 구현 중인 코드의 테스트를 PASS로 간주하지 않는다. 기존 Android9개/backend204개는 새 인증의 검증 근거가 아니다.
- 2026-10-27 단계2 VERIFIED: JDK25 Full215개 새 실행·실패0/오류0/건너뜀0·exit0. 신규 인증11개 포함,4분35초·8작업 중6실행/2재사용. 설정 미비/실제 HTTP/토큰 만료·폐기/기존 저장·MQTT 회귀 확인, 자동수정0/5. 단계3 앱 연동으로 진행한다.

---

# 뽀싹 Git 통합 승인 — 2026-10-26

- 사용자가 UI 결과를 승인하고 스테이징·커밋·푸시·PR 생성·main 병합까지 명시 지시했다. 뽀싹 이름·아이콘·설치 통합은 DONE, Git 통합은 이 승인에 따라 진행한다. 아래 커밋·푸시 미실행 문구는 당시 이력이다.
- 기준 main b0a2d82, 작업 브랜치 codex/ppossak-branding. 커밋 메시지: feat(android): brand app as ppossak with supplied launcher icon.
- 이번 범위는 Android 브랜드 리소스·문구와 관련 작업/인계 기록이다. APK·로컬 설정·개인 첨부는 제외하고 .codex-remote-attachments/를 보존한다.
- 직전 검증 이후 앱 소스 변경 없음: JVM9개·Lint0오류/9경고·debug APK·실제 폰 설치/단일 패키지/아이콘·뽀싹 표시 성공 결과를 재사용한다. 원격 CI는 backend Full이며 커밋 SHA별 성공을 확인한 뒤 병합한다.
- 최종 PR·병합 SHA·main CI 결과는 Git 및 PR/Actions 이력을 따른다. 운영 배포·운영 DB 변경은 수행하지 않는다.

---

# 뽀싹 이름·아이콘·앱 통합 — 2026-10-26

- 사용자 승인: 첨부 아이콘·뽀싹 이름 적용 계획 및 기존/preview 앱을 하나로 통일. 상태 VERIFIED(사용자 최종 시각 리뷰 전).
- 정식 com.juca.hydroponic에 뽀싹 표시 이름·상단 제목·소개 문구와 사용자 제공 이미지 기반 adaptive icon/테마 실루엣 적용. 원본은 mobile/android/artwork에 보존했다. backend·MQTT·보안 정책 변경 없음.
- 최종 clean debug 검증 exit0/PASS, 56작업 새 실행·JVM9개 실패0/오류0/건너뜀0. 빈 폴더 정리 뒤 최종 Lint exit0/PASS, 오류0/경고9(5실행/25재사용). 마지막 Lint는 앞 JVM·APK 성공 결과를 재사용했다. 실패기반 자동수정2/5, 상세 tasks/ANDROID_BRANDING.md.
- UI 앱의 서버 설정을 로컬 보존·복원하고 전체 XML 일치를 확인했다. 서명이 다른 기존 앱 교체·정식 debug APK 설치·preview 제거 완료. 폰 최종 패키지는 com.juca.hydroponic 하나다. 미확정 요청 없음, 실제 설정 값은 출력·기록하지 않았다.
- 실제 폰에서 이름·아이콘·상단 제목 확인, install Success·시작 Status: ok. APK .tools/android-branding/ppossak-debug.apk, 앱 정보/최종 UI 캡처 phone-info.png·phone-final.png(Git 제외). 이전 별도 설치 기록은 아래 이력이며 현재 두 앱 상태가 아니다.
- 서버는 연결하지 않아 저장된 주소의 연결 실패 안내가 표시된다. 앱 데이터 화면/서버 종단·실물·모든 런처 모양 검증은 이번 작업 NOT RUN. 커밋·푸시 없음. 다음: 사용자 폰 UI 리뷰.

---

# Android debug UI 별도 설치 — 2026-10-26

- 사용자 요청: 폰에서 debug APK로 UI 확인. 기존 앱과 새 APK의 서명이 달라, 사용자가 기존 앱 유지·UI 확인용 별도 설치를 선택했다.
- main b0a2d82의 앱 소스를 변경하지 않고 Git 제외 .tools/debug-apk/preview.init.gradle 및 preview-manifest.xml로 debug applicationIdSuffix=.preview, 표시 이름 `영양액 기록 · UI`만 적용했다. 패키지는 com.juca.hydroponic.preview다.
- JDK21·Android API36/Build Tools35.0.0 사용. Gradle8.13 Wrapper 다운로드 시간 초과·연결 초기화 2회 후 공식 직접 다운로드와 공식 SHA256 비교를 통과한 동일 버전 Gradle을 .tools/gradle-runtime에서 실행했다. 저장소 빌드 설정은 변경하지 않았다.
- 기본 debug: testDebugUnitTest lintDebug assembleDebug exit0/PASS, 55개 작업 새 실행. JVM9개 실패0/오류0/건너뜀0, Lint 오류0/경고9. UI 별도 APK: 로컬 init script로 assembleDebug lintDebug exit0/PASS, 50작업 중19실행/31재사용, Lint 오류0/경고9. JVM은 앞 기본 debug 결과이며 별도 APK에서 재실행하지 않았다.
- 로컬 manifest 이름 충돌 1회는 tools:replace 지정으로 해결했다. 앱 소스·검사 조건 변경 없음. 보조 빌드 설정 수정1/5.
- 사용자가 USB 디버깅을 승인한 연결 폰에 adb install -r Success, am start -W Status: ok. 기존/preview 두 패키지 공존 확인. 기존 앱 삭제·데이터 초기화 없음. 캡처로 설정 화면·정원 테마·한글 표시 확인. 상태 VERIFIED(설치·초기 UI 범위).
- APK: .tools/debug-apk/hydroponic-ui-debug.apk, 캡처: .tools/debug-apk/phone-ui.png(Git 제외). 서버·DB·MQTT·센서는 시작하지 않았다. 데이터 화면·앱 종단·실물 시험은 이번 작업 NOT RUN. 전체 UI를 보려면 서버 연결이 필요하다.
- 추적 변경은 이 인계 기록뿐이며 커밋·푸시 없음. 기존 미추적 .codex-remote-attachments/ 보존. 다음: 사용자 폰 UI 확인, 필요 시 별도 서버 연결. 커밋 초안: docs: record debug UI preview installation.

---

# 수경재배 전환 Git 통합 — 2026-10-26

- 사용자 명시 지시: 에이전트/하네스 규칙에 따라 현재 수경재배 전환 변경을 커밋하고 PR 생성, CI 확인, main 병합까지 진행한다. 이 지시는 앞선 UI 디자인 리뷰 후 Git 처리 지시를 갱신한다.
- 작업 브랜치 codex/hydroponic-migration. 커밋·푸시·PR·병합은 이번 요청으로 승인됐다. 운영 배포·운영 DB 반영은 승인 범위에 없다.
- UI 및 전환 결과는 아래 내용을 포함해 변경 파일을 먼저 검토하고, 연결된 GitHub 권한·필수 검사를 확인한다.
- 상태 VERIFIED(디자인 최종 리뷰 전, DONE 아님). 크림/연두/초록 테마·둥근 카드/입력·새싹 화분 Canvas·감자꽃 제목/고운돋움 본문·SansSerif 측정값을 전 화면에 적용했다. 폰트는 원본/OFL1.1을 APK에 포함하고 설정에서 라이선스 전문을 열람할 수 있다.
- 실제 연결된 emulator-5554(API37)에서 최종7개 새 실행 PASS, 실패0/오류0/건너뜀0. 좁은320dp/1.6배 글꼴 입력·등록, 폰트 라이선스, 오류 안내·정상 연결·전체 재배/측정 흐름 포함. 실제 폰은 조작하지 않았다.
- Android Full PASS: JVM9개 새 실행, Lint0오류/11경고, debug/미서명 release APK 성공. 첫105작업 중48실행/57재사용. 테스트 코드 보강 후 Full은4실행/101재사용이며 JVM/APK 성공 결과 재사용. 최종 Connected는 새 실행(5실행/66재사용).
- 화면10장 검수 및 XML 보존: .tools/android-acceptance/garden-ui-1/screens, results. 홈·설정·빈 상태·상세·등록·관리·오류·정상/불완전 결과. 전체 기기·글꼴·접근성 검증 완료를 의미하지 않는다.
- 시험용 서버/전용 컨테이너 종료, 생성한 에뮬레이터 포트 연결 정리. 새 APK를 에뮬레이터에 재설치·실행했다. 서버는 종료 상태라 다시 사용하려면 서버 연결이 필요하다. 에뮬레이터·ADB·Docker 엔진은 유지.
- Backend/API/DB/MQTT/보안 정책 이번 변경0, 기존 backend204개 검증 유지(이번 UI 작업 재실행 NOT RUN). 실물 센서/교정/독립 정확도 NOT VERIFIED 유지.
- 자동수정0/5. Git 기존 index와 MIGRATION.md AM 보존. 신규 Kotlin200줄 초과0. 상세 인수/폰트 출처는 tasks/ANDROID_GARDEN_UI.md 및 mobile/android/FONT_NOTICES.md.
- 다음: 사용자가 캡처/에뮬레이터에서 디자인 리뷰. 승인받기 전 커밋하지 않는다. UI 커밋 초안: feat(android): add a cozy green garden theme.

---

# 수경재배 전환 최종 소프트웨어 인수 — 2026-10-26

- 승인 범위: Kotlin·Jetpack Compose Android 앱과 기존 백엔드 확장, 웹사이트 제외. 디버그 루프백 HTTP3호스트 예외는 사용자 별도 승인 유지, 릴리즈 HTTPS 유지.
- 단계1~6 소프트웨어 범위 VERIFIED. 실기기 종단5개·화면 검수·최종 backend Full 회귀 완료. 전체 DONE은 사용자 최종 리뷰·승인 후다. 아래의 과거 BLOCKED/TODO는 당시 이력이다.
- 실제 SM-S947N(Android17/API37)에서 Forms4개+AppFlow1개 새 실행, 실패0/오류0/건너뜀0, exit0. 실제 앱→HTTP→전용 Spring/PostgreSQL/MQTT→모의 장치→보존 확인/버퍼 해제→앱 조회·재배 종료·미연결 과거 기록을 검증했다. SIMULATED는 실측 추세에서 제외된다.
- Backend 최종 JDK25 build --no-daemon --console=plain exit0/PASS: 204개 새 실행, 실패0/오류0/건너뜀0. 3분43초, test 작업1개 실행/컴파일·패키징6개 UP-TO-DATE 재사용.
- Android Full exit0: JVM9개 새 실행 실패0/오류0/건너뜀0, Lint0오류/11경고, debug·미서명 release APK PASS(105작업 중19실행/86재사용). 실기기 최종 Connected 작업은 새 실행, 컴파일 등70작업은 UP-TO-DATE였다.
- 캡처3장 육안 검수 PASS. .tools/android-acceptance/phone-2/screens 및 results에 PNG/XML 보존(Git 제외). 전체 기기·글꼴·접근성 검증 완료를 의미하지 않는다.
- 임시 인수 서버·전용 컨테이너 정상 종료(stopped·종료 로그 확인), 시험용 adb reverse 제거. 폰에 디버그 APK 재설치·실행 완료. 다른 ADB/에뮬레이터/Docker 엔진 유지. 서버는 종료했으므로 앱 사용 시 별도 서버 연결이 필요하다.
- 사용자 지시로 실제 ESP32·ADS1115·SEN0244는 나중에 검증한다. 실물 통합·교정·독립 정확도 NOT VERIFIED. 가상 시험 통과를 실물 인수로 간주하지 않는다.
- 자동수정: 단계5 3/5(Android17 Espresso 호환 포함), 단계6 3/5(스크롤·캡처 내보내기·파일 충돌). USB 단절/승인·서버 수명 만료 등 환경 실패와 과거 실패 이력은 tasks/HYDROPONIC_MIGRATION.md에 보존했다.
- Git: codex/hydroponic-migration, 기존 사용자 MIGRATION.md AM 및 staged/unstaged 구분 보존. 커밋·푸시·배포·운영 DB·실제 보드 변경 없음. 기존 V1~V5·펌웨어·MQTT 공개 계약 보존.
- 리뷰 검사: git diff --check·신규 파일 공백·문서 링크 PASS, AGENTS112줄, 신규 Java/Kotlin200줄 초과0. backend Lint/아키텍처 자동 검사는 MISSING 유지.
- 다음: 사용자 최종 리뷰, 별도 실물 인수 시 ESP32/ADS1115/SEN0244 연결·물 준비 후 기존 장비 절차 적용. 커밋 초안: feat: extend monitoring to hydroponic cultivation with Android client (실행 미승인).

---

# 수경재배 전환 체크포인트 — 2026-10-26

- 사용자 전체 계획 승인: 단계1 문서→2 재배→3 관리→4 측정 연동/조회→5 Kotlin·Jetpack Compose 안드로이드 앱→6 통합. 웹사이트 제외.
- 기준 main 2d81431, 작업 브랜치 codex/hydroponic-migration. 사용자 MIGRATION.md의 AM 상태 보존. 커밋/푸시/운영 배포/운영 DB 적용 미승인.
- 명세: HYDROPONIC_MVP.md, 실행·인수·검증: tasks/HYDROPONIC_MIGRATION.md. 기존 하네스·단일 에이전트·수정 한도 유지.
- 단계1 VERIFIED, 단계2 VERIFIED(새7개+기존정상저장3개 PASS), 단계3 VERIFIED(cultivation 전체14개 PASS), 단계4 VERIFIED(Full204/실패0/오류0/건너뜀0), 단계5 BLOCKED(Android UI 실행 환경), 단계6 TODO. 과거 인수는 아래 이력이며 새 전환 검증이 아니다.
- 기본 java21, JDK25 별도 설치 확인. Docker Desktop 기동 승인·Testcontainers 검증 완료. Android SDK/adb·에뮬레이터를 명령 승인으로 .tools에 설치했다.
- Android JVM9개(실패0/오류0/건너뜀0), Lint0오류/12경고, debug/release(미서명) APK·화면 테스트 APK 빌드 PASS. 마지막 Full 진입점은 성공 결과 UP-TO-DATE 재사용(103/105). 상세·실패이력은 작업 명세.
- 디버그 루프백3호스트 HTTP 예외 별도 사용자 승인 반영. 릴리즈 HTTPS 유지, SDK/서명키/개인설정/산출물 Git 제외.
- BLOCKED: 에뮬레이터 부팅 전 종료(exit1), ADB 기기 없음. WHPX 진단코드6, 소프트웨어 모드도 실패. 정확한 종료 원인은 미확정. Compose4개 실행·앱 종단·실물 NOT RUN/NOT VERIFIED.
- 다음: Android 실기기 또는 부팅 가능한 에뮬레이터 준비→verify-android.ps1 Connected→화면 검수/앱 실제 연동→단계6 최종 통합. Windows 기능/드라이버/재부팅은 추가 승인 없이 변경하지 않는다.
- APK: mobile/android/app/build/outputs/apk/debug/app-debug.apk. 설치·화면 사용성 검증은 아직 하지 못했다. 실행 안내 docs/ANDROID_SETUP.md, 승인 정책 docs/ANDROID_CONNECTIVITY.md.


---

# Home Water Monitor 인계 — 2026-10-23

## 최신 Git 체크포인트 — 3단계 DONE·PR #11
- 사용자 추가 승인(2026-10-23): 푸시 → PR 생성 → CI 확인·최종 리뷰 → main 병합·로컬 동기화까지 진행.
- 기능 커밋: `9351d2aaf56eb841504cfc3cebb95570e6612de2` (`feat: acquire SEN0244 readings through ADS1115`).
- 원격 작업 브랜치 `origin/codex/tds-sensor-integration` 반영 확인, PR: https://github.com/jucadrive/home_water_monitor/pull/11.
- 최종 리뷰: 작업/세션 소유권, 측정 시각 보존, 오류 prefix·불변 재전송, 소스 전환과 인수 도구를 검토했다. 추가 수정 사항 없음.
- 원격 CI는 backend Full만 실행한다. 펌웨어 실제 보드 60개·Host 25개·실측 인수는 아래 로컬 검증 증거를 따른다.
  최종 병합 여부·병합 SHA·해당 SHA CI 결과는 위 PR 및 Git/Actions 이력을 기준으로 확인한다.
- 교정·절대 정확도는 NOT VERIFIED 유지. 다음 기능은 4단계 조회·제어 화면과 결과·추세 조회 API(TODO).
- 추적 소스 변경은 완료했으며 첨부사진·비밀 설정·빌드 산출물은 커밋 대상이 아니다. 아래 승인 대기 표기는 이전 시점 기록이다.

## 최신 체크포인트 — SEN0244 실측 기능 DONE·Git 커밋 승인
- 상태 DONE(3단계실측기능). 사용자가 현재검증으로사용가능하다고판단되면교정·절대정확도는미검증으로유지하고
  3단계를마무리해Git반영하도록승인했다. 최종리뷰에서추가수정문제없음을확인하고커밋을진행한다.
  푸시/PR/병합은GIT_WORKFLOW.md에따라커밋승인에서확장하지않으며별도명시승인대기다.
  전체5개Phase 및 측정실행격리 추가계획 사용자승인(2026-10-23), 단일에이전트 수행.
  브랜치codex/tds-sensor-integration/기준main b247145, staged0. 아래진행중/실패/미실행표기는이전이력이다.
- 현재보드/build 모두ADS1115 일반MQTT60/18883/943600 bytes, Build/flash/hash exit0/PASS.
  PC192.168.0.38/COM5. 실제SEN0244 +3.3V/-GND/A→ADS1115 A0, VDD3.3V/ADDR GND0x48/SDA21/SCL22.
  SDA노란선은사용자전원OFF후정상복원완료했다. 재부팅/다운로드모드대기상태가아니다.
- 구현: 고정IDF I2C/25개전압중앙값/정수유리식환산/미교정메타데이터/실측·가상모드.
  정적FreeRTOS worker1개(4KiB stack/우선순위6/소유core0), 요청큐1/결과큐최대61,
  세대·실제완료시각·수치만복사/취소·포화별도원자신호. session·제어·MQTT는main단독소유.
  START수락후보고전worker시작, 원본은측정시각/전송deadline은처리시각 기준.
  1000ms간격/100ms지연거절/불변원본/커밋확인후해제/기존공개계약유지. backend/DB수정0.
- 실제보드60개(기존45+센서11+worker4) 실패0/무시0 exit0/PASS, Test205184 bytes/flash/hash PASS.
  3.5초소비지연에도실제시각유지/새전송deadline/큐포화/다음세대/취소/읽기지연오류 검증.
  CredentialFree160656/SIMULATED919856/ADS1115 943600 Build exit0/PASS(실제partition여유10%).
- Host25개 실패0/오류0/건너뜀0·PC smoke·확장ACL 새실행 exit0/PASS.
  Python구문/diff PASS, 알려진로컬자격증명문자열25후보파일일치0. 신규Java/Kotlin0개.
  backend181/계약100·108/기존가상장비종단은성공결과재사용(이번새실행아님). 자동Lint/Arch검사는기존MISSING.
- worker 정상물측정 두번 exit0/PASS: 각60원본/통계50/UART-MQTT-DB전체필드·독립환산/NORMAL/해제/IDLE0건.
  세션74e85b96-ff0b-48b4-8ba9-30f444c1c5d8(네통계121.11ppm, 미교정25°C가정),
  세션6708b71d-f669-456b-9b18-38162d11f7cb. 증거worker-normal-first/second.json.
- worker 물리SDA오류 exit0/PASS: 세션172fb165-60e0-4663-8c10-53de178829fd,
  29건INCOMPLETE/통계NULL/원본일치/해제/신규START차단. worker-interrupted.json.
  SDA분리유지·EN재부팅 init-fault exit0/PASS: 새bootId/START미발행/가짜세션없음.
- 최종worker strict retry exit0/PASS: 세션08aba623-573b-4f48-8f88-c4341c0ac249,
  실제60개/자동5회소진/65초추가전송0/보관중START거절/수동RETRY1회/총6회hash1,
  UART-MQTT-DB/독립환산/NORMAL/RELEASE_CONFIRMED/최종IDLE0건. proxy결과차단5/errors0.
  증거.tools/device-test/worker-retry.json 및 worker-retry-diagnostic.json. 실패한이전시도를PASS로덮어쓰지않는다.
- 오류prefix의동일payload RETRY는 C회귀 및 이전실제6건복구검증 재사용.
  새worker물리오류와정상60건strict RETRY는각각새실행이며, 오류prefix+차단프록시결합시험을추가실행했다고주장하지않는다.
- 안전종료 PASS: 전용backend/DB/브로커중지,18883/18884규칙False,시험4포트18883/18884/18080/25432 listener0.
  COM5 IDLE/session none/samples0/tx_active0/wifi_up1/rx_ready0/min_heap93376 bytes.
  DB볼륨/로컬비밀설정/다른서비스보존. 보관원본을리셋으로버리지않았다.
- 수정이력3/5: 최초진단1/1ms조기append시각수정1/Unity64비트assertion호환·tearDown정리1.
  첫Test60 실패4(비교지원2+정리누락연쇄2)였으나보완후60/0/0. 조건삭제·허용치완화없음.
  worker전late오류를일으킨정확한SDK API구간은미확정. 현재승인된격리구조에서반복실측·오류·strict RETRY를검증했다.
- 미검증범위: 표준액/TDS미터/멀티미터없음→교정·실수온·독립전압/절대정확도 NOT VERIFIED.
  초기10개warmup이충분한안정화시간이라는증거없음. 물리Wi-Fi단절은기존사용자승인제외 유지.
- 다음: 승인된3단계변경커밋→커밋SHA·원격대상을제시하고푸시/PR/병합승인확인.
  4단계조회·제어화면/결과·추세조회API는TODO. 이번커밋은기능을추가변경하지않아기존검증결과를재사용한다.
  커밋초안 feat: acquire SEN0244 readings through ADS1115.
  사진.codex-remote-attachments는커밋제외. 상세명세/결과 tasks/TDS_SENSOR_INTEGRATION.md.

## 현재 진행 — SEN0244·ADS1115 실측 연결
- 상태 IN_PROGRESS. 사용자 전체5개Phase 승인. 명세: tasks/TDS_SENSOR_INTEGRATION.md,
  배선/실행: ESP32_SENSOR_SETUP.md. 기준main b247145(PR #10), 시작clean, 브랜치codex/tds-sensor-integration.
- 부품: SEN0244/ADS1115(VDD,GND,SCL,SDA,ADDR,ALRT,A0~A3)/ESP32/점퍼/830홀 빵판.
  두보드 헤더 납땜·센서 +/-/A 암커넥터 사용자 확인. ADDR/풀업 미확인, TDS미터/멀티미터 없음.
  사진 확인 전 배선/ADDR 임의GND연결/전원 인가하지 않는다. 교정 표준액 여부도 미확인.
- 구현: 신규sensor 컴포넌트(고정IDF I2C/ADS1115/25개전압중앙값/정수유리식TDS),
  실측샘플 입력·불완전prefix확정·교정메타고정·ADC오류후기존버퍼RETRY/해제뒤신규START차단.
  Source ADS1115(스크립트 기본)/SIMULATED 명시선택, 실측 인수도구4종과PC검사4개/운영문서 추가.
- 검증: 실제보드Unity54개(기존45+신규9) 실패0/무시0 exit0/PASS, Test이미지200944 bytes/flash/hash PASS.
  CredentialFree160656/SIMULATED919840/ADS1115일반942016 bytes Build exit0/PASS, 실측partition여유10%.
  Host25개 실패0/오류0/건너뜀0, PC smoke·확장ACL exit0/PASS. PS AST/Python구문/diff PASS.
- 현재 보드=Test54(네트워크/실제측정 없음), 로컬build=ADS1115일반MQTT60/18883/942016 bytes.
  실측 이미지 flash/ADC응답/배선/물측정·오류/RETRY 종단은 아직 NOT RUN/NOT VERIFIED.
  표준액·비교미터·멀티미터 없는 교정/독립정확도는 NOT VERIFIED. 실측데이터로 가상값 대체 금지.
- backend/DB/공개스키마 변경0, 기존backend181개/계약100·108 성공결과 재사용. 신규Java/Kotlin0개.
  Phase2/3/4 실패기반자동수정0회. 커밋/푸시 없음. 최초전체승인은 유지한다.
- 다음: 모듈앞뒷면/ESP32핀표기 사진으로ADDR/풀업·핀 확인→전원OFF배선→일반실측flash/종단 인수.
  이 물리 확인은 BLOCKED이며 소프트웨어 준비 완료를 센서연결 완료로 표시하지 않는다.
- 후속 사진4개 검토: ESP32오른쪽3V3/GND/D21/D22와ADC HW-198/103저항, 센서케이블 흰3핀↔검정암3구 확인.
  사진만으로ADDR-GND연속성은 단정하지 않는다. 전원OFF에서ADDR외부GND점퍼/3.3V전원 배선안 안내.
  센서보드라벨 +/-/A와케이블색 대조·배선전체사진 확인 후 전원인가한다. 현재 배선/실측은 여전히 NOT VERIFIED.
  사진대기 조건은 해소됐으며 다음은 사용자 전원OFF배선·검토다. 추가 소스 수정/재빌드/장치조작 없음.
- 사용자 ADC 배선 사진3개 검토: ESP32 D21청록/D22주황, ADC VDD흰색/GND보라/SCL빨강/SDA노랑으로 보인다.
  ADDR선은ADC쪽 출구가 가려짐(빵판10번파랑으로 보임). 빵판5번검정·흰/10번회색·보라·파랑/
  15번청록·노랑/20번주황·빨강의 조합은 계획과 일치해 보이나 실제핀삽입구멍은 하우징에 가려 확정불가.
  사용자 동일숫자행·수핀삽입 확인 전 USB전원대기. 사진을 전기적 연속성/ADC통신 PASS로 처리하지 않는다.
- 사용자가 안내한9선/빵판5·10·15·20행/ADDR 연결과 정확히 일치한다고 직접 확인했다.
  ADC만 연결한 채USB전원/다운로드모드 준비를 요청했다. TDS/A0는 미연결 유지.
  준비된실측이미지942016 bytes/REAL_SENSOR ON/TEST_IMAGE OFF/CONTROL_MQTT ON/CREDENTIAL_FREE OFF 재확인.
  다음flash 후 ADC0x48 ACK·설정재조회 및IDLE0건을 확인한다. 임의START/물측정은 아직 하지 않는다.
- 최신 물리 체크포인트: USB/다운로드모드 준비 사용자 확인 후 COM5 실측이미지942016 bytes flash/hash exit0/PASS.
  부팅UART HWM_SENSOR_INIT ready=1 address=0x48 calibration=sen0244-uncalibrated-v1,
  HWM_BOOT simulated=0 target60 modeMQTT 확인. ADC ACK/설정쓰기·재조회 성공이다.
  이후STATUS IDLE/session none/samples0/finalized0/tx_active0/wifi_up1/rx_ready0,
  min_heap148436 bytes. 브로커/DB/시험방화벽은 종료상태 유지, rx_ready0은 예상이다.
  현재 보드/build 모두 일반ADS1115 실측이미지다(Test54 이미지가 아님). 측정 START는 보내지 않았다.
- 다음: IDLE0건 확인됐으므로 사용자 USB분리→SEN0244 흰색3핀 연결/보드 +/-/A와선색사진 확인→
  검정3구에수-수점퍼를사용해3.3V/GND/A0 연결. 현재TDS/A0 미연결, 아날로그값·물측정은NOT VERIFIED.
- 센서배선 사진후속: SEN0244 3핀은검정-/빨강+/파랑A를사진으로확인했다.
  사용자파랑A의연장점퍼는흰색이다. 흰색신호선↔빵판25번↔ADC A0갈색선의조합은사진상일치해보인다.
  전원은센서빨강→기존3.3V5번줄/검정→GND10번줄로안내했고사진검토했다.
  센서보드의2핀프로브소켓은사진상비어있다. USB분리상태에서프로브연결확인후전원/실측을진행한다.
  색상변경자체는문제없으며회로연결대상으로판정한다. 실제전압/물측정은아직NOT VERIFIED.
- 안전 종료: 전용backend/DB/브로커 중지, 시험4포트 listener0, 두시험방화벽False 확인.
  변경후보22개 알려진 로컬 비밀값 일치0/공백검사PASS/staged0. 바이너리/로컬설정은Git제외 유지.
- 커밋 초안: feat: acquire SEN0244 readings through ADS1115

## 최신 체크포인트 — 사무실 전환 후 잔여 인수·안전 종료
- 기존 승인 Phase 4/5 중 남았던 proxy-puback/proxy-negative/C27/Host/일반 MQTT 복원·normal을 완료했다.
  2026-10-23 사용자가 물리 Wi-Fi 단절 시험을 생략하고 넘어가도록 명시했다.
  해당 시험은 NOT VERIFIED/사용자 승인 제외이며 PASS로 바꾸지 않는다.
  변경된 인수 범위의 START·상태 보고/가상 데이터 구현은 검증을 통과했고,
  사용자가 완료 범위 보고를 수락하고 최종 리뷰 후 Git 진행을 승인했다. 현재 단계 DONE.
- 실제 Git: codex/mqtt-control-acceptance-resume, 기준0f31ccd(PR #9 병합), 문서2개 unstaged/staged0.
  소스 수정/새 커밋/푸시/PR/병합 없음. 아래 과거 기록의 미실행 항목은 이 체크포인트로 갱신한다.
- 실제 신규 PASS: proxy-puback(5회+RETRY1회/hash1/65초 무전송), proxy-negative(부정13종/유효16KiB/중복 guard),
  C27 proxy-status-pressure(상태PUBACK6개 차단/RX7연결/장애 중 보존·해제/상태수신 복구).
- Host21개 실패0/오류0/건너뜀0/PC smoke·확장 제어 ACL exit0/PASS. JDK25 최신 bootJar exit0/PASS.
  기존 backend181개·계약100/108·실제C45개 결과는 재사용이며 이번 새 실행이라고 주장하지 않는다.
- 사용자 요청으로 개인 핫스팟에서 사무실 WPA2 Wi-Fi로 전환했다. PC192.168.0.38/24, 보드COM5.
  로컬 설정 형식 검증/전용 환경 rebind/한정18883 규칙 갱신 후 일반 MQTT60/18883 Build919616 bytes,
  파티션 여유12%, 사용자 BOOT/EN 후 flash/hash exit0/PASS. 보드/build 모두 이 일반 이미지다.
- 실제 control normal exit0/PASS: 동시START202 하나/409 하나, 멱등·충돌·USB모드 거절,
  실제 결과1회/원본60개 전체 필드 DB 일치/ACCEPTED·NORMAL·RELEASE_CONFIRMED/FRESH/최종IDLE0건.
- 안전 종료: 전용 backend/브로커/DB 중지,18883/18884/18080/25432 listener0,
  시험 방화벽18883/18884 Enabled=False. COM5 IDLE/session none/samples0/tx_active0/rx_ready0,
  min_heap108780 bytes. DB 볼륨/비밀 로컬 설정/다른 서비스 보존. diff --check PASS.
- 추가 코드 수정0회. 기존 자동 수정 한도 소진 상태 유지. 신규 Java/Kotlin 파일 없음.
- 4단계 판정: 1 START·상태 보고 계약 설계 완료/기승인, 2 백엔드·펌웨어 가상 데이터 구현·인수 DONE,
  3 실제 TDS 센서 연결 TODO, 4 조회·제어 화면 TODO. 현재 펌웨어는 hwm_sample_simulated를 호출한다.
  START/RETRY·장치/명령 상태 REST는 있지만 화면과 측정 결과·추세 조회 기능은 구현하지 않았다.
- Git 후속 승인: 사용자가 최종 리뷰 후 Git 진행을 요청했다. 재개 문서2개를 검토한 뒤
  커밋·푸시·CI·PR 리뷰/병합·main 동기화를 진행한다. 구현 소스는 PR #9로 이미 main에 통합됐다.
  이후 실제 센서 모델·회로·교정 조건을 확인하고 3단계 계획을 수립해 최초 승인을 받는다.
  공용 사무실 AP 단절/사무실 망 원인 조사/추가 코드 수정은 진행하지 않는다.
- 커밋 초안: docs: record resumed MQTT device acceptance and safe shutdown

## 재개 체크포인트 — 2026-10-23 사무실
- 사용자가 새벽 중단 지점부터 재개를 요청했다. 기존 승인 계획 Phase 4/5의 잔여 인수를 이어간다.
- 실제 Git: PR #9 병합 main `0f31ccd`, 시작 작업 트리 clean. 재개 브랜치 `codex/mqtt-control-acceptance-resume`.
  아래 이전 브랜치/미커밋/Git 승인 대기 문구는 과거 기록이며 현재 Git 상태가 아니다.
- 현재 PC/로컬 산출물은 집 체크포인트와 다르다. 최초 유선192.168.1.14/COM5,
  STATUS IDLE/session none/samples0/wifi_up0/rx_ready0. 로컬 이전 이미지913264 bytes, 이전 backend JAR 확인.
- 사용자가 개인2.4GHz 핫스팟 사용과 PC 연결·로컬 Wi-Fi 설정 완료를 확인했다.
  현재 Wi-Fi IPv4는10.206.235.177/24. 기존 전용 DB/브로커를 해당 주소로 rebind/start했다.
- 기존 시험 방화벽18883/18884는 일반 실행 권한 거절 후 UAC 관리자 실행으로 갱신,
  두 규칙 Enabled=True를 확인했다. 다른 서비스/방화벽 정책/DB 볼륨은 변경하지 않았다.
- 최신 소스 JDK25 bootJar exit0/PASS(compileJava/processResources/resolveMainClassName/bootJar 실제 실행).
  전체 테스트 재실행 결과가 아니며, 기존181개 성공 증거와 구분한다.
- 최신 UART60/프록시18884 펌웨어 Build exit0/PASS(919744 bytes/partition 여유12%).
  최종 Host21개 실패0/오류0/건너뜀0, PC smoke/확장 제어 ACL 검사 exit0/PASS.
  사용자 다운로드 모드 준비 후 COM5 flash/hash exit0/PASS. 보드/build 모두 최신 UART60/프록시18884.
  실제 proxy-puback exit0/PASS: 5회 소진/65초 추가 무전송/동일 세션 RETRY/DB 원본·재수신/최종 해제.
  실제 proxy-negative exit0/PASS: 부정13종 거절/유효16KiB 분할 확인·중복 guard/최종IDLE0건.
  신규 C27 proxy-status-pressure exit0/PASS: 상태PUBACK6개 차단/RX7연결,
  장애 중 결과1회 보존·해제/상태수신 복구/최종IDLE0건.
  이전 로컬 인수 JSON은 새벽 집 인수 증거로 사용하지 않는다.
- 사용자가 다른 업무를 위해 개인 핫스팟 대신 PC·보드를 사무실 Wi-Fi로 바꾸도록 요청했다.
  진행 중 C27 세션의 보존·해제를 마친 뒤 새 시험 없이 안전 종료했다.
  전용 backend/브로커/DB 중지, 시험4포트 listener0, 방화벽18883/18884 Enabled=False 확인.
  COM5 STATUS IDLE/session none/samples0/tx_active0/rx_ready0/min_heap104500 bytes.
  현재 보드와 build는 핫스팟용 최신 UART60/프록시18884다. Wi-Fi 전환만으로 보드 설정이 바뀌지 않는다.
- 사무실 전환 재개: 사용자가 wifi.local.json 수정 완료/재개를 요청했다.
  PC Wi-Fi192.168.0.38/24/COM5, 보드 IDLE/session none/samples0 확인.
  로컬 Wi-Fi WPA2 형식 PASS(값 미출력), 전용 DB·브로커 rebind/start PASS.
  UAC 관리자 실행으로18883 규칙만 갱신·활성화(True),18884=False 확인.
  일반 MQTT60/18883 최신 이미지 Build exit0/PASS(919616 bytes/파티션 여유12%).
  사용자 다운로드 모드 준비 후 COM5 flash/hash exit0/PASS. 보드/build 모두 사무실용 일반 MQTT60/18883.
  첫 STATUS는 wifi_up0/reason4/rx_ready0였으나 추가20초 UART 관찰에서 HWM_RX_READY mqtt=5 확인.
  설정/소스 수정 없이 연결됐다. 실제 control_accept.py --case normal --port COM5 exit0/PASS.
- 사무실 환경 rebind/일반 MQTT60 복원·normal/안전 종료까지 완료했다. 결과는 최상단 최신 체크포인트 참조.
  물리 Wi-Fi 단절은 아직 NOT VERIFIED. 공용 사무실 AP를 끄지 않으며 사무실 망 지연 원인 조사는 범위 밖이다.
  추가 코드 수정 한도 소진 상태 유지. 수정이 필요한 실패는 보고 후 승인 대기한다.
  이번 재개 변경의 커밋·푸시·PR·병합은 실행하지 않는다.

## 현재 설계 — MQTT START·장치 상태 보고
- **Git 통합 승인(2026-10-23): 사용자가 현재 변경의 커밋·푸시·CI·PR·main 병합을 모두 명시 승인했다.**
  아래 장비 잔여 검증/이미지 복원은 후속으로 남긴 채 현재 구현을 통합한다. 전체 기능 VERIFIED/DONE 판정은 보류한다.
  대상 origin/codex/mqtt-control-implementation → PR → main, 커밋 초안 `feat: control ESP32 measurements and observe device status over MQTT 5`.
  Git 결과와 CI는 원격에서 확인한다. 장비/전용 서비스는 안전 종료 상태를 유지한다.
- **최신 체크포인트: 사용자 요청으로 현재 시험까지만 완료하고 안전 종료(2026-10-23 새벽).**
  broker-restart/reboot/proxy-early 추가 PASS. 기존 장비13종 중10종 PASS,
  proxy-puback/proxy-negative 미실행, 물리 Wi-Fi 단절은 공유기 Wi-Fi만 끄기 어려워 환경 제약 NOT VERIFIED.
  신규 C27 proxy-status-pressure, 최종 Host21 재검증, MQTT 일반 이미지 복원/최신 normal 인수도 남았다.
  현재 **보드와 build 모두 UART60/프록시18884/919744 bytes**(flash/hash PASS). 일반 MQTT 이미지로 복원하지 않았다.
  종료 STATUS IDLE/session none/샘플0/tx_active0/rx_ready0(브로커 중지에 따른 예상 상태), min_heap107400 bytes.
  전용 backend·브로커/DB 중지, 시험 포트18883/18884/18080/25432 listener0,
  시험 방화벽18883/18884 규칙 Enabled=False 확인. DB 볼륨/비밀 로컬 설정/전체 미커밋·staged0 보존, diff --check PASS.
  재개: IP/COM4·IDLE 확인 → 기존 전용 서비스와 한정 방화벽 규칙 재개 → 현재 이미지로 프록시2종+C27
  → 최종 Host/일반 MQTT60·18883 Build/사용자 BOOT·EN 후 flash/normal → 문서·안전 종료.
  Full build와 계약100/108은 이번 재개에서 PASS(test181은 UP-TO-DATE 재사용). 성공 검증은 새 근거 없이 반복하지 않는다.
  자동 수정5회+별도 승인2건은 소진됐다. 추가 코드 수정이 필요한 실패는 보고 후 승인을 기다린다.
  기능은 미완료(IN_PROGRESS/사용자 요청 중단), Git 커밋·푸시·PR·병합은 별도 승인 전 금지.
- 상태: START·상태 보고 계약 및 전체 실행 계획 사용자 승인 완료. Phase 1~3 검증 통과, Phase 4 실제 장비 인수 중.
- 실행 계획: [tasks/MQTT_CONTROL_IMPLEMENTATION.md](tasks/MQTT_CONTROL_IMPLEMENTATION.md).
  Phase 1 계약/순수 판단 → Phase 2 서버/V5/REST/MQTT → Phase 3 펌웨어 → Phase 4 집 장비 인수 → Phase 5 최종 통합.
  새 제어 발행 연결·실행기/큐 한도·V5 4개 제어/관측 테이블·장치별 잠금/복구·시험 권한을 계획에 명시했다.
- 기준 main 62bac79(PR #8 문서 병합), 현재 codex/mqtt-control-implementation. 구현·문서 미커밋 변경이 있으며 staged0을 유지한다.
- 집 준비: PC 유선 192.168.0.2/24, 보드 CH340 COM4/115200, STATUS IDLE/샘플0·Wi-Fi/MQTT 미연결 확인.
  고정 ESP-IDF5.5.5/ESP-MQTT 설치·도구 검사와 로컬 Wi-Fi JSON 형식 검사 PASS. 비밀값은 출력하지 않았다.
  집 전용 인증 브로커/DB와 LAN 한정 TCP18883 규칙을 준비했다. 기본 Java21이므로 검증 시 JDK25를 지정한다.
- 계약: docs/MQTT_CONTROL_CONTRACT.md. 인수 조건29종·후속 Phase/변경 후보/위험: docs/tasks/MQTT_CONTROL_CONTRACT.md.
- 승인 계약: 서버 UUID/sessionId·별도 명령 기록·REST 멱등 키, fresh 조회 후 부팅 ID+일회성10초 실행 허가,
  START/동일 세션 RETRY·명령 자동 재발행0회·UNKNOWN 조정, 상태15초/45초 stale·bootId/statusSeq 순서 검사.
- 서버 보존/장치 해제/실행 수락/관측 신선도를 구분한다. MQTT와 USB 실행 제어 모드는 상호 배제한다.
  재부팅 RAM 유실 허용은 유지한다. 제품 센서/TLS 배포/장치 영속 버퍼/사무실 망 조사는 범위 밖이다.
- SDK 예외 별도 승인: 고정 ESP-MQTT가 수신 Expiry를 공개하지 않아 장치의 직접 값 검사만 제외한다.
  서버 발행 Expiry10초·브로커 만료·bootId/일회성10초 창은 유지하며 SDK 패치/버전 변경은 하지 않는다.
- 새 실행: 제어 계약100/기존 계약108, 백엔드 전체181개(30 suite) 실패0/오류0/건너뜀0 PASS, exit0.
  V5는 Testcontainers와 집 전용 인수 DB에서 적용/기동 확인했다. 운영/개발 DB는 사용하지 않았다.
- 펌웨어: MQTT/UART 명시 모드·명령/상태 연결·신규 C12개 추가. CredentialFree와 Test 이미지 빌드 exit0.
  실제 UART43개 실패0/무시0 PASS, Test 이미지196528 bytes. 현재 보드/build는 MQTT/가상60개/일반18883이다.
  실제 이미지919488 bytes, 1MiB 여유12%, flash/hash검증 PASS. 집 Wi-Fi/MQTT 두 구독 rx_ready1 확인.
  실제 guards+normal, unknown, release-loss PASS. REST→60개 원본→NORMAL 보존→해제, 상태 차단 UNKNOWN,
  완료 보고 유실·서버 재시작 후 lastRelease 복구와 신규 START 차단을 확인했다.
  실제 retry PASS: 최초5회+동일 payload RETRY1회, hash1개, 소진 후65초 무전송, 최종IDLE/0건.
  도구의 UART 갱신 누락으로 실패한 앞선 시험의60개도 동일 세션 RETRY로 정상 보존/해제했다.
  최신 Host21개 실패0/오류0/건너뜀0와 PC smoke·확장 ACL 거절4종 PASS. 기존 장비13종 재실행/최종 build는 아직 남았다.
- 실제 EN 재부팅 인수 PASS: INTERRUPTED_REBOOT/UNKNOWN_AFTER_REBOOT, 빈 세션/정상 해제로 오인 없음,
  이전 bootId 거절과 최종IDLE0건. 로컬 build는 다음 회귀용 UART0개/919600 bytes로 바꿨으나 아직 flash하지 않았다.
- Phase 4 자동 수정5회 후 Host 격리 문제로 중단했고, 사용자가 해당 원인의 추가 수정1회만 승인했다.
  상관값 격리 수정 후 Host21+확장 ACL smoke PASS, 추가 한도 사용1회. 이후 추가 실패 시 수정 없이 중단한다.
  현재 최신 서버의 동시 REST START 인수 중이며, 이후 사용자 BOOT/EN 준비 후 UART0/1/59/60과 기존 장애 회귀를 진행한다.
- 최신 동시 REST START 인수 PASS(202 하나/409 하나·실제1회 측정/60개·IDLE0건).
  계약 대조 중 round/statusSeq 상한 누락을 보고했고 사용자가 카운터 경계 보완1회를 별도 승인했다.
  해당 보완과 C2개를 추가해 총45개 Test Build/실제 보드 UART PASS(197392 bytes, 실패0/무시0), flash/hash PASS.
  현재 보드는 Test45 이미지다. UART0개 일반 이미지를 다시 Build 중이며 이후 사용자 BOOT/EN 준비 후 인수를 재개한다.
- 최신 카운터 보완 UART0개(919744 bytes) flash/hash·문법7종/CRLF/STATUS·실제 normal0개 PASS.
  UART모드 MQTT START 거절/허가 없음·controlReady=false·최종IDLE0건 PASS. 현재 로컬 UART1개 Build 중이다.
- 최신 UART1개 Build/flash/hash·normal1개 PASS. 현재 보드는UART1개/919744 bytes/IDLE0건, 로컬 UART59개 Build 중이다.
- 최신 UART59개 Build/flash/hash·normal59개 PASS. 0/1/59개 모두 불완전 보존·원본/확인/해제·최종IDLE0건.
  UART60개 Build PASS(919744 bytes), 현재 사용자 BOOT/EN 준비 후 flash 중이다. 이후 기존 장애 인수로 진행한다.
- 최신 UART60개 Build/flash/hash·normal60개 PASS. 최신 UART normal0/1/59/60 네 종류 모두 PASS.
  0/1/59 실제 DB의 stats_sample_count0/통계4개NULL/누락 건수60-N 별도 읽기 대조 PASS.
  현재 보드와 build는 UART60/일반18883/919744 bytes, 기존 UART retry 인수 진행 중이다.
- 기존 UART retry PASS(같은 payload 재전송/DB 원본·재수신 진단/해제), 현재 exhaust 회귀 진행 중이다.
  C27 상태 송신 압력은 신규 proxy-status-pressure 인수로 구체화했다(실제 실행 전).
- 기존 UART exhaust PASS(5회·고정버퍼·65초 무전송·지연 확인 해제), DB-outage 회귀 진행 중이다.
  보드는 일반18883, 로컬 build는 이후용 프록시18884/UART60/919744 bytes로 다르다. 아직 프록시 이미지를 flash하지 않았다.
  집 LAN 한정 TCP18884 규칙도 UAC 관리자 실행으로 준비했다. 종료 시18883/18884 두 시험 규칙을 비활성화한다.
- 기존 UART db-outage PASS(전용DB 중지 중 보관/복구 후 보존·해제), 현재 broker-restart 회귀 중이다.
  알려진 로컬 비밀번호의 Git 대상205개 파일 검사 위반0, 비밀 설정/빌드/증거의 .gitignore 제외를 확인했다(값 미출력).
- 재개 확인: broker-restart PASS(실제 발행2회/payload hash1개·구독 복구·보존/해제·최종IDLE0건).
  기존 장비13종 중8종 완료, 현재 일반18883/UART60 보드로 수동 Wi-Fi 단절·복구 인수 중이다.
  이후 수동 EN 재부팅 → 프록시 이미지 flash/기존3종+C27 상태 송신 압력 → MQTT 일반 이미지 복원/최종 통합 순서다.
- Wi-Fi 단절 회귀는 사용자가 공유기 Wi-Fi만 끄기 어렵다고 응답해 환경 제약 NOT VERIFIED로 남겼다.
  해당 시험 프로세스만 중지하고 전용 서버를 재개했다. 보드 IDLE/0건을 확인했으며 현재 수동 EN 재부팅 회귀 중이다.
  재개 후 제어 계약100/기존108 PASS, 최종 Full build PASS(test는 앞선181개 결과 UP-TO-DATE 재사용).
- 기존 UART reboot PASS: 실제 EN/옛 확인 거절/자동 START 없음/서버 원본60개 일치/최종IDLE0건.
  기존13종 중9종 PASS, Wi-Fi 단절1종 환경 제약 NOT VERIFIED, 프록시3종 미실행이다.
  프록시18884/UART60 이미지919744 bytes 확인 완료, 사용자 다운로드 모드 준비 후 flash 대기 중이다.
- 설계 기준 main 1aaaa06. 시작 시 HANDOFF와 ESP32 명세의 미커밋2개를 보존했다. 실제 장비 조작 없음.
- 사용자가 집에서 이어가기 위해 설계 문서4개의 커밋·푸시·PR·main 병합을 승인했다.
  당시 문서 Git 승인은 계약/구현 승인이 아니었다. 이후 계약과 전체 구현 계획을 별도 승인받았다.
  당시 Git 작업 브랜치는 codex/mqtt-control-contract였다.
  이 기록은 Git 실행 전 체크포인트이며 실제 결과는 Git/PR/Actions로 확인한다.
- 다음: Phase 4 RETRY/재부팅·기존 UART 장비 회귀 → Phase 5 최종 통합·안전 종료. Git 통합은 별도 승인이다.

## 현재 진행 — ESP32 MQTT 5.0 펌웨어·장비 인수
- 상태 DONE. 승인된 가상 데이터 펌웨어/실제 장비 인수 완료, 2026-10-22 사용자 최종 리뷰 및 전체 Git 작업 승인.
- 기준 main d2332a0(PR #6), 브랜치 codex/esp32-firmware-integration. 기존 미커밋 계획 문서를 보존했다.
  사용자가 커밋·푸시·PR 생성·리뷰·main 병합·CI 확인과 로컬 동기화를 승인했다.
  이 Git 기록은 실행 전 체크포인트다. 실제 결과는 Git 로그와 GitHub PR/Actions로 확인한다.
- 명세/승인/실패와 재검증 이력: docs/tasks/ESP32_FIRMWARE_INTEGRATION.md.
  설치/실행: docs/ESP32_SETUP.md, docs/ESP32_ACCEPTANCE.md. USB 계약: docs/DEVICE_CONTROL_CONTRACT.md.
- ESP-IDF 5.5.5/고정 ESP-MQTT, 실제 ESP32-D0WD-V3 rev3.1/40MHz/flash4MB/CH340 COM3.
  자동 다운로드 대신 BOOT/EN 후 no_reset 수동 flash를 사용한다. 마지막 flash/hash 검증 PASS.
- 마지막 보드와 로컬 build: 일반 MQTT 18883/가상 60개/개인 WPA2 2.4GHz 핫스팟, 913264 bytes.
  1MiB app partition 여유13%, HWM_TEST_IMAGE=OFF/HWM_CREDENTIAL_FREE=OFF. SSID/계정/바이너리는 Git 제외.
- 독립 RX/시도별 TX·고정 최종 목록·유한5회/지터·동일 UUID RETRY·유효 보존 확인 후 삭제 구현.
  USB START 시 PC가 UUIDv4를 발급하며 부팅/복구/재전송이 새 세션을 만들지 않는다.
- 이번 기능 작업 누적 실제 장비 인수13종 PASS: normal 0/1/59/60, retry, exhaust, db-outage,
  broker-restart, proxy-puback, proxy-early, proxy-negative, wifi-loss, reboot. 모든 증거에서 최종 IDLE/0건 확인.
  원본 전체/동일 payload hash·MQTT 속성·DB 커밋/재수신·실제 UART 해제를 교차 확인했다.
- proxy-negative: 핫스팟에서 5연결/5발행·동일 hash1·MQTT DUP 없음·65초 추가 무전송,
  부정13종에서 보관/유효16KiB 분할 확인 후 해제/같은16KiB 중복·완료 UUID START 거절 PASS.
- wifi-loss: 실제 핫스팟 OFF/ON 중 wifi_up=0·같은 UUID/60개 보관, 복구 후 RETRY 6번째 발행도
  동일 hash1, DB 보존 확인 후 해제 PASS. reboot: 미확인60개 상태의 EN 부팅 로그/IDLE0/자동 START 없음,
  이전 세션 확인 거절·서버 원본 내용 보존 PASS. EN/전원에 따른 RAM 유실은 승인된 MVP 제약이다.
- Host 최신19개/실패·오류·건너뜀0 및 PC smoke PASS. PS AST/Python 구문/diff 검사 PASS.
  실제 ESP32 순수 상태31개/실패·건너뜀0·UART 부정7종은 앞선 직접 실행 결과를 재사용(순수 소스 변경 없음).
  backend Full158/26클래스·계약108은 이번 기능 초반 성공 결과를 재사용(backend/계약/V1~V4 변경 없음).
  credential-free160656 bytes 빌드는 앞선 성공 기록이며 최종 일반 이미지와 구분한다. Lint/아키텍처 자동 검사 MISSING.
- Phase4 실패 기반 자동 수정은5회로 한도 준수. 실패를 삭제/조건을 약화하지 않고 기록·수정·재검증했다.
  사무실 개방형 망의 지연 전달/송신 실패 근본 원인은 미확정. 최종 경계/부정/수동 인수는 개인 핫스팟이다.
  첫 핫스팟 AP 미발견은 실제5GHz 송출 때문이었고 사용자2.4GHz 전용/OFF·ON 후 해결했다.
- 안전 종료 확인: 전용 백엔드·브로커/DB 컨테이너 중지, 18883/18884/18080/25432 listener0,
  자신의 한정 방화벽 규칙2개 비활성화. 다른 서비스/DB·볼륨·로컬 설정·기존 변경은 보존했다.
  장치는 IDLE이며 시험 서비스를 다시 열기 전 rx_ready=0은 예상 상태다. 활성 IP는 재개할 때 다시 확인한다.
- Git 후보165개 실제 비밀값/SSID 일치0. 새 Java1개85줄, 200줄 초과 없음. 이 검사는 포괄적 보안 보증이 아니다.
- 남은 제품 기능은 별도 승인 범위: 실제 센서/ADC/교정, 제품 MQTT START·서버 sessionId 발급,
  TLS/운영 배포·영속 명령 이력·재부팅 시 미확인 원본 영속 보존.
- 커밋 초안: feat: add ESP32 MQTT 5 firmware and device acceptance harness.
- Git 통합 완료: PR #7, 구현 커밋9b458fa, main 병합1aaaa06. push/PR/병합 후 CI 모두 성공,
  로컬 main 동기화·작업 트리 clean 확인. 병합 CI: https://github.com/jucadrive/home_water_monitor/actions/runs/39743611691.
- 사무실 망 지연 원인 조사는 사용자 지시로 후속 범위에서 제외했다. 근본 원인 미확정 기록은 유지한다.
- 다음 제안: 제품 MQTT START·서버 sessionId 발급·장치 실행 확인/상태 보고 계약을 먼저 설계한다.
  새 기능 계획 승인 전 구현하지 않는다.

## 현재 진행 — MQTT 5.0 실제 서버 연동
- 상태 DONE, 2026-10-22 사용자 최종 리뷰 승인. [작업 명세](tasks/MQTT_INTEGRATION.md) · [실행 안내](MQTT_INTEGRATION.md).
- 사용자가 커밋·푸시·소스 CI·PR·main 병합·병합 후 CI 및 로컬 동기화를 승인했다.
  아래 Git/원격 CI 기록은 실행 전 체크포인트다. 실제 결과는 GitHub PR/Actions와 Git 로그를 확인한다.
- 2026-10-22 사용자가 전체 MQTT 구현 계획을 승인했다. Phase 1~3 순차 구현·검증 완료.
- MQTT 수신 → strict 계약 검증 → 기존 application 저장 → 실제 커밋 성공 후 보존 확인 발행을 구현했다.
- 브랜치 codex/mqtt-message-contract, 기준 main 810ed9a. 기존 미커밋 계약 변경을 보존했다.
- 기본 MQTT_ENABLED=false. 장비/운영 브로커/개발 DB는 실행하지 않았다. 테스트 브로커/DB/모의 장치만 사용했다.

## 최신 Phase Result — MQTT 서버 연동
- 본체: session.infrastructure.mqtt의 codec·수신기·설정·발행기 5개. 테스트/지원 Java 6개.
  Paho v5 1.2.5 의존성, 외부 MQTT 설정, 연동 안내·승인/검증 기록을 추가했다.
- 저장은 ProcessTdsSessionService 프록시를 호출한다. 기존 NEVER/READ_COMMITTED·PK 충돌 1회 복구를 유지했다.
  실제 커밋 성공 후만 확인한다. 실패 시 MQTT ACK하되 업무 확인은 없고 장치 원본 재전송으로 복구한다.
- 수신/발행 연결 분리·Receive Maximum=1·DirectChannel 직렬 처리. 유한 연결/발행 대기와 재연결·세션 정책은 실행 안내 참조.
- 검증: 선택 Phase 1 33개, Phase 2 구성 9개 및 브로커/DB 통합 10개 PASS.
  최종 ./scripts/verify-mqtt-contract.ps1 108개 PASS·실패/건너뜀 0·종료 코드 0.
- 최종 ./scripts/verify.ps1 -Mode Full PASS, 종료 코드 0, 기존 97 + 신규 61 = 158개,
  26개 클래스·실패/오류/건너뜀 0. XML KST 2026-10-22 00:58:00~01:00:19.
  compileJava/processResources UP-TO-DATE, compileTestJava/test/bootJar/jar/check/build 실행.
- 인수 조건: 정상/불완전/빈 목록·동일/충돌 재수신·추가/삭제 거절·실제 쓰기/커밋 실패 시 미확인/롤백,
  커밋 후 발행 실패·재전송 복구·브로커 재시작 재구독·수신 중지/재개·연속 메시지 직렬 처리 PASS.
- 자동 수정: Phase 1 3회, Phase 2 1회, Phase 3 2회. 상세/진단 재실행은 작업 명세 참조.
  Docker 컨테이너 재시작 시 무작위 포트 변경을 확인하고 같은 포트의 Mosquitto 프로세스 재시작으로 검증했다.
- 변경 총 34개 (앞선 계약 파일 포함). 신규 Java 최대 152줄, 200줄 초과 없음.
  domain/application/영속성·V1~V4·운영 Compose·CI 설정 변경 없음. Lint/정적 분석/아키텍처 자동 검사 MISSING.
- Git: staged 0, unstaged/untracked만 존재. diff/공백/범위 점검 PASS. 커밋·푸시·PR·병합/이번 원격 CI 없음.
- 제한: 실제 펌웨어 MQTT 5·버퍼 삭제·재전송은 NOT VERIFIED. 운영 배포/개발 DB 실행 NOT RUN.
  DB/MQTT 원자성·서버 outbox는 없다. 수신 세션 만료/확인 유실의 복구는 장치 버퍼 재전송에 의존한다.
- 커밋 초안: feat: integrate MQTT 5 session ingestion and post-commit confirmations
- 다음: 사용자 최종 리뷰. 승인 후 Git 통합 또는 장비 인수/START 계약을 진행한다.

## 이전 작업 — MQTT 5.0 메시지 계약
- 상태 VERIFIED, 사용자 최종 리뷰 대기. 명세: [tasks/MQTT_MESSAGE_CONTRACT.md](tasks/MQTT_MESSAGE_CONTRACT.md).
- 사용자가 최종 목록·보존 확인 우선 및 MQTT 5.0 기준 진행을 승인했다.
- 계약: [MQTT_MESSAGE_CONTRACT.md](MQTT_MESSAGE_CONTRACT.md). JSON 계약 버전 1과 프로토콜 5.0을 구분한다.
- 브랜치 codex/mqtt-message-contract, 기준 main 810ed9a. 시작 전 작업 트리 깨끗함.
- 이전 불완전 보존은 PR #5로 main 810ed9a에 병합 완료, main CI 성공:
  https://github.com/jucadrive/home_water_monitor/actions/runs/39623869675
- 이번 작업의 커밋·푸시·PR·병합은 아직 별도 승인되지 않았다.

## 이전 Phase Result — MQTT 메시지 계약
- SESSION_RESULT/SESSION_PRESERVED의 고정 토픽·필드·의미·버퍼 상관관계를 정의했다.
  MQTT 5.0·QoS 1·retain=false, Content Type application/json, Payload Format Indicator 1,
  UTF-8 JSON 최대 16,384 bytes. 보존 확인은 실제 커밋 성공 후에만 발행한다.
- 변경: 문서 4개, JSON Schema 2개, JSON 예제 9개, PowerShell 검증 2개. 총 17개 파일.
- 검증: PowerShell AST 구문 검사 PASS, ./scripts/verify-mqtt-contract.ps1 종료 코드 0.
  실제 실행 108개 PASS, 실패/건너뜀 0. 정상/불완전/빈 목록·재수신·부정/byte 경계를 확인했다.
- 자동 수정 1회: 다중 행 if 식 연산자 위치를 수정했다. 계약/기대값 약화 없음.
- Java·DB·의존성·기존 CI 변경 없음. 기존 Full 97개/병합 main CI 결과를 재사용했다.
  이번 Gradle·실제 MQTT·원격 CI NOT RUN. 계약 검증은 수동 스크립트이며 CI에 연결하지 않았다.
- 인수 조건: 오프라인 형식/의미/전달받은 전송 메타데이터 검사 충족. 실제 인증·패킷·커밋 후 발행은 미검증.
- Git: staged 없음, 문서/검증 파일만 unstaged/untracked. diff/변경 범위 점검 PASS.
- 커밋 초안: docs: define MQTT 5 session result and preservation contracts
- 다음: 사용자 최종 리뷰. 이후 연동 실패 처리 계약 계획, 그 뒤 실제 MQTT 어댑터 구현.

## 이전 작업 — 불완전 세션 보존
- 전체 계획 사용자 승인. 명세: [tasks/INCOMPLETE_SESSION_PRESERVATION.md](tasks/INCOMPLETE_SESSION_PRESERVATION.md).
- 브랜치 codex/incomplete-session-preservation, 기준 main 276d6a6, 시작 전 작업 트리 깨끗함.
- 최초 결과 확정: 같은 seq 집합의 변경은 충돌 진단, 추가/삭제는 거절하며 NORMAL로 승격하지 않는다.
- 전체 상태 DONE: 2026-10-22 사용자 최종 리뷰 통과.
- Phase 1 선택 18개, Phase 2 통합 17개 및 수정 후 단위 2개, Phase 3 선택 12개 PASS.
- 개발 DB 실행/마이그레이션 적용 없음.
- Git 통합 승인: 2026-10-22 사용자가 커밋·푸시·해당 SHA CI·PR·main 병합을 일괄 승인했다.
  병합 main CI와 로컬 동기화도 확인한다. 아래 기록은 Git 통합 실행 전 체크포인트다.
- 이전 라우팅 기능은 PR #4로 main 276d6a6에 병합 완료, main Verify 성공:
  https://github.com/jucadrive/home_water_monitor/actions/runs/39618593683
  아래 라우팅 Git 실행 전 기록은 이전 체크포인트다.

## 이전 Phase Result — 불완전 세션 보존
- 구현: ProcessTdsSessionService.process()가 정상/불완전 신규/기존을 조율한다.
  NEVER·READ_COMMITTED·세션 잠금·timeout·세션 PK 충돌 후 1회 복구를 유지했다.
- 0~59개 원본과 INCOMPLETE·누락 순번을 한 트랜잭션으로 확정한다.
  stats_sample_count=0, 네 대표 통계 NULL. 최초 원본/누락 진단/상태는 고정한다.
- 같은 seq 집합의 변경은 기존 네 필드 비교로 진단한다. 추가/삭제/순번 교체·메타데이터 변경은 거절한다.
- V4는 누락 seq 테이블과 재수신 헤더 0~60개 제약을 추가한다. V1/V2/V3는 변경하지 않았다.
- 변경: 신규 Java 19개 (본체 10·테스트/지원 9), V4·명세 신규 및 기존 7개 수정, 총 28개.
- 검증: JAVA_HOME=C:/dev/jdk-25.0.4.1+1, ./scripts/verify.ps1 -Mode Full PASS, 종료 코드 0.
  21개 클래스, 기존 66 + 신규 31 = 97개, 실패/오류/건너뜀 0. PostgreSQL 18 Testcontainers 사용.
- XML UTC 2026-10-21T15:00:26.678Z~15:01:19.293Z (KST 2026-10-22 00:00:26~00:01:19).
  test/compileJava/bootJar/jar 실제 실행, compileTestJava/processResources UP-TO-DATE.
- 인수 조건: 빈/1/59개·중간 누락·원본/누락/통계 정합성·동일/충돌·시각·잘못된 입력/추가/삭제 거절,
  정상/불완전 최초 경합 양방향·상대 롤백·기존 직렬화·독립 세션·timeout·쓰기/커밋 롤백 충족.
- 자동 수정: Phase 2 테스트 예외 stubbing 1회. 정책/기대값 약화 없음. Phase 1/3 0회.
- 자체 리뷰: 신규 Java 최대 141줄. domain·기존 MANDATORY/persist·의존성·공개 API/MQTT 변경 없음.
- Git: codex/incomplete-session-preservation, staged 없음, unstaged/untracked만 존재. diff 점검 PASS.
  커밋/푸시/PR/병합 없음, 원격 CI NOT RUN. 이번 기능의 Git 통합은 별도 승인 대상이다.
- 제한: INCOMPLETE 승격/부분 수락/참고 통계/추세 조회 API/실패 사건 별도 저장/MQTT는 범위 밖이다.
  V4는 테스트 DB에만 적용했다. 개발 DB/서버 실행 없음. 다음 서버 실행 시 V4가 적용될 수 있다.
- Lint·정적 분석·아키텍처 자동 검사 MISSING. check가 이를 검증한다고 주장하지 않는다.
- 커밋 초안: feat: preserve incomplete sessions with immutable final results
- 다음: 승인된 Git 통합·main CI 확인. 이후 MQTT 메시지 계약 → 연동 실패 처리 계획.

## 이전 작업 — 신규/기존 라우팅과 최초 저장 경합
- 기준 main: 2364c27 (PR #3 병합). 로컬 origin/main 참조도 동일했으며 작업 전 변경 없음.
  아래 과거 PR #3 병합 대기 기록은 이 체크포인트 이전 이력이다. 이번에 최신 원격 CI는 조회하지 않았다.
- 사용자 승인: 전체 라우팅 계획, 새 진입점의 외부 트랜잭션 금지, 세션 PK 충돌 후 1회 복구.
- 명세: [tasks/NORMAL_SESSION_ROUTING.md](tasks/NORMAL_SESSION_ROUTING.md).
- 브랜치: codex/normal-session-routing. 상태 DONE — 사용자 최종 리뷰 통과.
- Phase 1: NEVER 조율·READ_COMMITTED 분기·PK 충돌 식별·새 트랜잭션 복구를 구현했다.
- Phase 2: 실제 최초 저장 경합·상대 롤백·timeout·쓰기/커밋 실패와 최종 Full 검증 완료.
- 로컬 CLI 기본 Java는 21이다. 검증 프로세스 JAVA_HOME=C:/dev/jdk-25.0.4.1+1을 사용한다.
  Docker Desktop을 실행했고 Testcontainers 통합 테스트 연결을 확인했다.
- Git 통합 승인: 사용자가 커밋·푸시·해당 SHA CI 확인 후 성공 시 PR 생성·main 병합 및 main CI 확인까지 승인했다.
  아래 검증 기록은 Git 통합 실행 전 체크포인트다. 원격 결과는 PR/Actions의 실제 상태를 확인한다.

## 이전 Phase Result — 신규/기존 라우팅과 최초 저장 경합
- 구현: ProcessNormalTdsSessionService가 입력 검증 후 별도 READ_COMMITTED Bean으로 분기한다.
  세션 PK 충돌만 첫 트랜잭션 종료 후 새 트랜잭션에서 기존 세션 처리로 1회 전환한다.
  새 진입점은 NEVER이며 외부 트랜잭션에서 호출하면 거절한다. 기존 직접 서비스 계약은 유지했다.
- 변경: 본체 Java 4·테스트/지원 Java 6·새 작업 명세, 설정 주석과 관련 문서. 총 15개 파일.
- 검증: compileJava compileTestJava PASS, Phase 1 선택 10개 PASS,
  Phase 2 수정 후 선택 11개 PASS, 최종 ./scripts/verify.ps1 -Mode Full PASS (종료 코드 0).
- 최종 XML: 2026-10-21 22:52:21~22:52:55 KST, 기존 45 + 신규 21 = 66개,
  실패 0·오류 0·건너뜀 0. test/bootJar/jar는 실제 실행, 최종 컴파일은 UP-TO-DATE.
- 인수 조건: 정상/거절 분기·복구 제한·최초 저장 경합·상대 롤백·다른 세션 독립·
  메타데이터 거절·INSERT timeout·신규/기존/복구 커밋 실패·이전 데이터 보존 충족.
- 수정: Phase 2 테스트 spy stubbing이 MANDATORY 프록시를 호출한 문제 1회 수정.
  실제 트랜잭션 규칙/테스트 기대값 변경 없음. 신규 Java 모두 200줄 이하 (최대 147줄).
- 제한: NORMAL 60개 전용. 실패 사건 보존·불완전 세션·MQTT 수신/확인 발행은 미구현.
  잠금별 timeout이며 전체 호출 상한이 아니다. 불명 커밋 실패를 성공으로 간주하거나 재시도하지 않는다.
- Git: codex/normal-session-routing, staged 없음, unstaged/untracked만 존재. diff 점검 PASS.
- 원격 CI NOT RUN. Lint·정적 분석·아키텍처 자동 검사는 MISSING.
- 커밋 초안: feat: route normal sessions and recover initial insert races
- 다음: 승인된 커밋·푸시·CI·PR·main 병합·main CI를 순차 실행한다. 사용자의 최종 리뷰는 통과했다.

## 운영 방식과 현재 작업
- 최상위 프로젝트 실행 지침: [루트 AGENTS.md](../AGENTS.md), [Harness.md](Harness.md).
- 사용자: Architect & Verifier. AI: 하위 에이전트 없는 단일 자율 구현·검증 담당.
- 최초 계획 승인 후 승인 범위의 Phase를 순차 진행한다. 최종 사용자 승인 전 DONE으로 표시하지 않는다.
- 기존 페어 프로그래밍·사용자 직접 작성 지침은 종료했다. 기존 업무 합의는 유지한다.
- 하네스 최소 구축 계획 1~5항 및 관련 문서 전환은 사용자가 승인했다.
- 구축 명세: [tasks/HARNESS_BOOTSTRAP.md](tasks/HARNESS_BOOTSTRAP.md).
- 하네스 상태: DONE. PR #1을 main의 abe4d3c로 병합했고 병합 후 원격 CI도 성공했다.
- 측정 비교 도메인: 사용자 승인으로 PR #2를 main 88682a2에 병합했고 원격 CI도 성공했다.
- 정상 세션 재수신 진단: 구현·검증 결과를 사용자 승인받았고 커밋 5336e3e를 푸시했다. 원격 CI 성공.
- PR #5 불완전 보존 병합/main CI 성공 완료. 현재 MQTT 5.0 실제 서버 연동 구현·검증 결과의 사용자 리뷰 대기다.

## 데스크톱에서 이어가기 — 2026-10-22

- 현재 구현: 정상/불완전 저장·측정 비교·재수신 진단·세션 잠금·통합 라우팅·최초 저장 경합 처리.
- 최신 로컬 Full 158개·계약 검사 108개 성공. 이번 브랜치 원격 CI는 NOT RUN이다.
- 이전 라우팅 PR #4: https://github.com/jucadrive/home_water_monitor/pull/4 (main 276d6a6 병합/main CI 성공).
- 불완전 보존은 main 810ed9a에 병합됐다. 이번 계약 변경은 codex/mqtt-message-contract에 미커밋 상태다.
  임의 switch/reset/stash하지 않는다.
- 실제 MQTT 수신/확인 발행을 구현했고 테스트 브로커·DB로 검증했다. 펌웨어/실제 장비 검증은 별도다.

데스크톱 저장소 루트에서:
```powershell
# 로컬 변경이 있으면 먼저 차이를 확인하고 보존한다. 임의 reset/clean/stash를 하지 않는다.
git status
# 작업 트리가 깨끗한 경우에만 main으로 전환하고 병합된 최신 내용을 받는다.
git switch main
git pull --ff-only origin main
```

1. 루트 AGENTS.md -> 이 HANDOFF -> docs/tasks/MQTT_INTEGRATION.md 및 계약/기존 명세를 소스와 대조한다.
2. IntelliJ Project SDK와 Gradle JVM을 JDK 25로 맞춘다. CLI도 JAVA_HOME/PATH를 JDK 25로 설정한다.
3. Docker Desktop을 실행한다. 테스트는 Testcontainers를 사용하므로 개발 DB/.env 설정이 필요 없다.
4. PC 변경 후 재현 검증이 필요하면 저장소 루트에서 ./scripts/verify.ps1 -Mode Full을 실행한다.
5. V3/V4는 개발 DB에 아직 적용하지 않았다. 서버를 시작하면 Flyway가 적용하므로 정상 접속 DB를 확인한다.
   기존 V1/V2와 데이터 볼륨을 수정·삭제하지 않는다. 실제 .env·DB 데이터·IDE 개인 설정은 Git으로 공유되지 않는다.
6. 정상/불완전 통합 진입점은 ProcessTdsSessionService.process()다. 외부 트랜잭션 없이 호출한다.
   기존 정상 전용 진입점/직접 서비스는 유지했다. MQTT 입력 어댑터도 이 진입점을 사용한다.
7. 새 요구사항에서는 실행 계획과 최초 승인을 먼저 받고, 커밋·푸시·PR·병합도 합의한 승인을 따른다.

재개 문구:
> AGENTS.md와 HANDOFF.md, tasks/MQTT_INTEGRATION.md 및 현재 작업 브랜치 파일을 확인해 줘.
> MQTT 5.0 실제 수신·저장·커밋 후 확인을 구현했고 Full 158개·계약 검사 108개를 통과했어.
> 미커밋 변경을 보존하고 결과를 리뷰해 줘. 커밋·푸시·PR·병합은 별도 승인 전 진행하지 마.
## 이전 Phase Result — 정상 세션 재수신 진단
- 상태: VERIFIED. 승인된 Phase 1~3 구현·로컬 검증 완료, 사용자 최종 리뷰 대기.
- 구현: V3 진단 헤더/60개 상세, 원본 스냅샷, seq별 비교, 메타데이터 거절, 공통 입력 검증,
  READ_COMMITTED 서비스 트랜잭션과 MANDATORY persist, 세션별 비관적 잠금·대기 제한.
- 변경 파일: 신규 Java 11개(애플리케이션/영속성), 신규 테스트·지원 Java 7개, V3, 계획 문서.
  기존 신규 저장 서비스·세션 Repository·application.yaml 및 관련 문서 3개를 수정했다.
- 실행: JDK 25, Gradle Wrapper, PostgreSQL 18 Testcontainers. 실제 개발 DB·V1/V2 변경 없음.
- 검증 명령: compileJava, Phase 1 선택 test(10개), Phase 2 선택 test(수정 후 10개),
  Phase 3 동시성 선택 test(3개), ./scripts/verify.ps1 -Mode Full.
- 최종 결과: Full PASS, 종료 코드 0, compileJava·compileTestJava·test·bootJar·jar 실제 실행.
  XML 시각 2026-10-21 17:47 KST, 전체 45개·실패 0·오류 0·건너뜀 0.
- 최종 테스트 구성: 기존 24개 + 입력 검증 4개 + 재수신 서비스 7개 + 진단 롤백 3개
  + 스키마/신규 경로 회귀 4개 + 동시성 3개 = 45개.
- 인수 조건: 동일·순서 역전·자릿수·필드별 충돌·전압 null 양방향·전부 충돌·집계·거절·
  DB 시각 반올림·실제 쓰기/커밋 롤백·같은 세션 대기·다른 세션 독립·timeout 후 복구 모두 충족.
- 수정 이력: Phase 2 자동 수정 1회(미존재 세션 업무 판단을 Repository 밖 서비스로 이동).
  테스트 삭제·약화·검사 비활성화 없음. Phase 1/3 수정 재시도 0회.
- 자체 리뷰: 새 Java 파일 모두 200줄 이하, 순수 domain은 수정하지 않음.
  공통 검증은 기존 기본 검증을 보존하며 신규의 통계 대상 필수 조건도 회귀 검증했다.
  V1/V2·빌드 의존성·공개 API·MQTT·원본 저장 컬럼 변경 없음. diff/문서 점검 통과.
- 제한: 기존 NORMAL 60개 세션 전용. 신규/기존 자동 라우팅·최초 저장 경합·불완전 보존·
  거절/실패 사건 영속화·MQTT 확인 발행·보관/삭제 정책은 미구현이다.
- V3는 테스트 DB에만 적용했다. 개발 DB는 서버 실행 시 Flyway로 적용할 수 있으며 이번에는 실행하지 않았다.
- 원격 CI: 소스 커밋 5336e3e Verify 성공(실행 39572555646). 이후 문서 갱신·PR 병합 검사는 PR #3 참고.
  Lint·정적 분석·아키텍처 자동 검사는 기존 MISSING 상태다.
- Git: codex/normal-session-redelivery, 변경 26개 파일(기존 6·신규 20), 커밋·푸시 없음.
- 커밋 초안: feat: record normal session redelivery diagnostics atomically
- 다음: 사용자 최종 리뷰와 커밋·푸시 승인. 재승인 전 추가 기능·PR·병합은 진행하지 않는다.

## 이전 Phase Result — 측정 비교 도메인
- 상태: VERIFIED — 2026-10-21 최초 계획 승인 범위 구현·검증 완료, 최종 사용자 리뷰 대기.
- 구현 내용: 같은 sessionId + seq에서 네 필드의 동일·충돌 비교, 기본 입력·비교 대상 검증.
- 변경 파일: TdsMeasurement.java, TdsMeasurementComparisonResult.java, TdsMeasurementTest.java,
  작업 명세·HANDOFF·도메인 안내·하네스/Git 체크포인트 문서. 서비스·DB·의존성 변경 없음.
- 검증 명령: ./scripts/verify.ps1 -Mode Unit — PASS, 종료 코드 0, 비교 13개 + 통계 7개.
- 검증 명령: ./scripts/verify.ps1 -Mode Full — PASS, 종료 코드 0, 전체 build 성공.
- XML 증거: 2026-10-21 16:56 KST 새 실행. 비교 13개·통계 7개·컨텍스트 1개·저장 통합 3개,
  전체 24개, 실패 0·오류 0·건너뜀 0. JDK 25 사용. Unit에서 compileJava·compileTestJava 실행,
  Full에서 test·bootJar·jar 실행. 기존 쓰기/커밋 오류 롤백 테스트도 통과했다.
- 인수 조건: 동일·필드별 충돌·자릿수·전압 null 양방향·다른 키·필수값·순번·경과시간·경계·
  반올림 금지·원본 수치 보존 및 전체 회귀 검증 충족.
- 자체 리뷰: 도메인 import는 java.math/java.util뿐, 신규 Java 파일은 각각 50/7/133줄.
  git diff --check 및 변경 범위 검토 통과. 자동 수정 재시도 0회.
- 남은 위험: record의 equals/hashCode는 업무 비교가 아니다. 서비스 연결·재수신 저장·진단·동시성은 미구현.
  이번 브랜치의 원격 CI는 NOT RUN이며 Lint·정적 분석·아키텍처 자동 검사는 기존 MISSING 상태다.
- Git 상태: codex/measurement-comparison, 소스 3개 신규·계획 1개 신규·문서 4개 수정.
  커밋·푸시·PR·병합은 하지 않았다.
- 커밋 초안: feat: add measurement content comparison domain
- 다음 Phase: 사용자 최종 리뷰 및 구체적인 변경 범위의 커밋·푸시 승인 대기.
## 하네스 구축 당시 검증 기록
### Phase Result — 하네스 구축
- 구현 내용: 단일 에이전트 운영 문서, 컨텍스트 테스트 DB 격리, Unit/Full 검증 스크립트, CI 구성.
- 변경 파일: 루트 AGENTS.md·README.md, docs 운영·설계 안내·구축 명세,
  HomeWaterMonitorBackendApplicationTests.java, scripts/verify.ps1, .github/workflows/verify.yml.
- 실행 환경: JAVA_HOME을 설치된 C:/dev/jdk-25.0.4+7로 지정한 검증 프로세스.
  build.gradle은 원래 Java 25 toolchain이므로 변경하지 않았다. IDE 개인 설정도 수정하지 않았다.
- 검증 명령: ./scripts/verify.ps1 -Mode Unit — PASS, 종료 코드 0, 도메인 테스트 7개.
- 검증 명령: ./scripts/verify.ps1 -Mode Full — PASS, 종료 코드 0, build 성공.
- 전체 테스트 XML: 2026-10-21 15:49~15:50 KST 새 실행. 단위 7개·컨텍스트 1개·저장 통합 3개,
  총 11개, 실패 0·오류 0·건너뜀 0. compileTestJava와 test는 이번 검증에서 실행됐고
  compileJava는 기존 출력에 대해 UP-TO-DATE였다. Full에서 bootJar·jar도 실행됐다.
- 스크립트 PowerShell 구문 검사·git diff --check: PASS.
- 실패 처리 확인: 최초 샌드박스 실행에서 Gradle 캐시 잠금 접근 거부로 스크립트 종료 코드 1 확인.
  명령 승인을 받은 후 같은 Unit 검증 성공. 검사 비활성화·테스트 약화는 하지 않았다.
- Docker 조회는 일반 샌드박스에서 연결 불가/접근 거부였으나 승인된 Full 프로세스는
  Testcontainers PostgreSQL에서 컨텍스트·저장 통합 테스트를 실제 통과했다.
- 인수 조건: 로컬 구축 조건 충족. AGENTS.md 98줄. 개발 DB 설정 없이 테스트 전용 DB 사용.
- 당시 미확인: Linux·원격 CI. 이후 PR #1과 main CI에서 동일 스크립트 Full 실행 성공으로 확인했다.
  CI가 작성됐다고 브랜치 보호·필수 검사 또는 원격 성공이 설정됐다고 주장하지 않는다.
  Lint·정적 분석·아키텍처 자동 검사 MISSING은 승인된 후속 과제다.
- Git 상태: main, 기존 사용자 docs/Harness.md는 AM 그대로 유지. 나머지 변경은 unstaged/untracked.
  애플리케이션 기능·의존성·V1/V2·DB 계약 변경, 커밋·푸시는 없음.
- 커밋 초안: chore: establish single-agent harness and isolated verification
- 다음 Phase: 사용자 최종 리뷰 DONE. IntelliJ 백엔드 프로젝트 JDK 25 설정 완료를 사용자 확인했다.
  하네스 브랜치에서 승인된 커밋을 수행하고, 푸시 승인 후 원격 CI 결과를 확인한다.

## 구현 이력 읽기
아래 2026-10-20 구현·설계 이력은 현재 소스와 대조한다. 과거 사용자 통과 보고를 이번 재실행으로 해석하지 않는다.
## 현재 환경과 구조

- Git / Codex 프로젝트 루트: `home-water-monitor`
- 실제 Gradle / IntelliJ 프로젝트 루트: `backend/home-water-monitor-backend`
- 위의 한 단계 중첩은 사용자와 확인한 구조이며 임의로 평탄화하지 않는다.
- Java 25, Spring Boot 4.1.1, Gradle Wrapper 9.8.1, Groovy DSL
- 기본 패키지: `com.juca.homewatermonitorbackend`
- PostgreSQL 18, 호스트 접속 포트 15432, 컨테이너 포트 5432
- Compose 프로젝트명: `home-water-monitor`, 서비스명: `postgres`
- DB 생성·변경은 Flyway, Hibernate 설정은 `ddl-auto: validate`, `open-in-view: false`

## 구현·검증 상태

| 항목 | 상태와 근거 |
|---|---|
| `TdsStatisticsCalculator` | 평균·최소·최대·중앙값 구현. 평균과 짝수 중앙값은 소수 둘째 자리 HALF_UP |
| 통계 단위 테스트 | 7개. 이전 사용자 통과 보고와 당시 XML 확인 기록. 이번 인계에서는 재실행하지 않음 |
| PostgreSQL / 서버 접속 | 사용자 확인: Docker Desktop 정상 실행, PostgreSQL 컨테이너 생성, 로컬 환경변수 설정, Spring Boot 정상 실행 |
| V1 | `tds_session` 생성. 사용자 적용·테이블 조회 확인 |
| V2 | `tds_measurement` 생성. 사용자 적용·테이블 조회 확인 |
| 엔티티 | `TdsSessionEntity` 전체 컬럼, `TdsMeasurementEntity` 저장 생성자·Lombok 조회 메서드 작성. 사용자 서버 정상 실행 보고 |
| Repository | `TdsSessionJpaRepository`, `TdsMeasurementJpaRepository` 작성. 원본 세션별 정렬 조회·건수 조회 제공. 사용자 Repository 2개 등록 확인 |
| 저장 어댑터 | `TdsSessionPersistenceAdapter.insert()`에서 `EntityManager.persist()` 사용. `MANDATORY`로 바깥 서비스 트랜잭션 요구 |
| 입력 객체·서비스 | `SaveNormalTdsSessionCommand`, `SaveNormalTdsSessionService` 작성. 신규 정상 목록 검증·안정화 제외 통계·세션과 원본 원자적 저장 |
| 저장 통합 테스트 | `SaveNormalTdsSessionServiceIntegrationTest`에 3개 작성. 사용자가 각 테스트 통과 보고. Testcontainers PostgreSQL 18 사용, 테스트에 `@Transactional` 없음 |
| 최신 실행 보고서 | 재수신 진단 추가 후 전체 45개 성공. 상단 최신 Phase Result 참고 |
| 원본 비교 도메인 | PR #2 병합 완료. 이번 재수신 서비스가 순수 비교 도메인을 사용 |
| 정상 세션 재수신 | V3·헤더/상세 스냅샷·서비스·잠금 구현. 최종 검증 결과는 상단 최신 기록 참고 |
| Git | 모노레포 루트 `.git` 한 개, `main`이 `origin/main` 추적. 원격은 `https://github.com/jucadrive/home_water_monitor`. 커밋·푸시 결과는 실제 Git 상태로 확인 |

2026-10-20 기록은 이전 문서·Git 공유 작업 범위다. 2026-10-21 하네스 작업에서는 승인된 테스트 격리·검증을 수행했다.

## 첫 저장 기능의 실제 범위와 검증

- `SaveNormalTdsSessionService.save()`는 **신규 정상 세션 전용**이다. 기대 원본 60개·측정 간격 1000ms, `seq=1..60`의 범위·목록 내부 중복, 필수 문자열, 경과시간, DB 수치 저장 범위 등을 검증한다.
- 안정화 원본도 모두 저장한다. `isWarmup=false`인 값만 최소·최대·평균·중앙값 계산에 사용하며, 현재 경로는 통계 대상이 없는 입력을 거절한다.
- 가상 원본의 첫 10개는 안정화 300.0ppm, 나머지 50개는 151.3ppm. 세션 1건·원본 60개·통계 대상 50개, 네 통계 모두 151.30ppm을 커밋 후 재조회한다.
- 저장 중 오류 테스트: 테스트 DB에 특정 세션의 `seq=40`을 거절하는 임시 CHECK를 추가한다. 실패 세션·원본은 전부 롤백되고 이전에 커밋한 다른 세션은 유지되는지 확인한다.
- 커밋 오류 테스트: 테스트 DB에 지연 제약 트리거·함수를 추가한다. 원본 60개가 보이는 커밋 시점에 SQLSTATE `23514` 오류를 발생시키고, 호출자가 예외를 받으며 세션·원본 전체가 롤백되는지 확인한다. 임시 DB 객체는 `finally`에서 제거한다.
- 개발 DB와 테스트 DB는 별개다. Testcontainers 2.0.5의 `org.testcontainers.postgresql.PostgreSQLContainer` 및 `@DynamicPropertySource`로 테스트 전용 접속 정보를 등록한다. Docker Desktop은 필요하지만 이 테스트에 개발 DB 환경변수는 필요하지 않다.
- 수동 UUID를 가진 세션의 기본 `JpaRepository.save()`는 `merge()`를 선택할 수 있어 신규 저장에서는 `persist()` 경로를 사용했다. 기존 세션 ID의 재전송은 아직 제약 위반 오류 경로이며, 이를 최종 멱등성 구현으로 간주하지 않는다.
- 정상 전용 경로는 59개를 계속 거절한다. 새 통합 경로는 유효 0~59개 목록을 INCOMPLETE로 보존하고 대표 통계를 계산하지 않는다.
- 정상 세션 진단·재수신·통합 라우팅·최초 저장 경합은 위 최신 구현을 따른다. 실제 MQTT 확인 발행은 없다.
  커밋 명시 거절 테스트가 모든 네트워크 장애와 커밋 결과 불명 상황까지 검증한 것은 아니다.

## 현재 DB 모델

- V3는 재수신 헤더·상세, V4는 누락 seq 진단과 재수신 목록 0~60개 제약을 추가한다.
  실제 DDL은 V1~V4를 함께 참조한다. 세션/원본 컬럼은 유지했다.

- 실제 DDL은 `src/main/resources/db/migration/V1__create_tds_session.sql`과 `V2__create_tds_measurement.sql`이다. 오래된 명세의 참고 DDL보다 실제 마이그레이션을 기준으로 확인한다.
- 세션: 식별·장치·시각, 기대/보존/통계 대상 건수, `NORMAL`/`INCOMPLETE` 보존 결과, 가정 온도·교정 버전, 최소/최대/평균/중앙값.
- 원본: 내부 자동 생성 ID, 세션 UUID, seq, 경과시간, TDS, 안정화 여부, 선택 전압.
- 원본의 업무 식별은 `(session_id, seq)` UNIQUE다. 측정 시각·PPM은 UNIQUE에 넣지 않는다. 원본은 세션을 FK로 참조한다.
- 현재 서비스는 NUMERIC의 소수 둘째 자리·정수부 범위를 검증하며 전압은 선택값이다. 센서의 물리적 유효 범위, 실제 입력 계약, 불완전 결과의 세부 검증은 계속 검토한다.
- 적용한 V1/V2는 정렬·공백 변경도 체크섬 불일치를 일으킬 수 있어 수정·자동 포맷하지 않는다. 사용자가 정렬 변경을 되돌린 뒤 정상 실행을 확인했다. 다음 스키마 변경은 새 마이그레이션으로 진행한다.

## 원본 내용 비교 — 최신 사용자 선택

- 업무 식별은 `sessionId + seq`. 키가 같은 두 원본의 `tdsPpm`·`elapsedMs`·`isWarmup`·`rawVoltageMv`를 **모두 비교**하기로 선택했다. PPM 외 비교 필드를 다시 미정으로 돌리거나 재질문하지 않는다.
- 수치는 `BigDecimal.compareTo()`로 비교하여 `151.3`과 `151.30`을 동일하게 본다. 이 비교에서 반올림하거나 허용 오차를 추가하지 않는다.
- 전압은 양쪽 모두 `null`이면 동일, 한쪽만 `null`이면 충돌이다.
- 안내한 도메인 설계: 결과 enum `IDENTICAL`/`CONFLICT`, 측정 record의 `compareWith()`. 다른 세션·seq 비교는 `IllegalArgumentException`. 신규 여부는 서비스 조회 결과로 판정한다.
- record·enum과 비교 단위 테스트 13개를 구현했다. [도메인 안내](NEXT_MEASUREMENT_COMPARISON.md)는 실제 소스를 참조하며 검증 증거는 상단에 기록한다.

## 아직 구현하지 않은 것

- 거절·롤백 사건 별도 보존, 원문 로그·운영 보관 정책
- 실제 장비 펌웨어 인수 검증, START/장치 명령·상태 보고·운영 모니터링/배포
- 센서 연결·배선·실측·펌웨어

통계 단위 테스트 통과나 테이블 생성 성공을 전체 수집 기능 완료로 표현하지 않는다.

## 새 PC의 설정

1. [루트 README](../README.md)에 따라 Git·JDK 25·Docker Desktop을 준비하고 저장소를 받는다.
2. `infra/.env.example`을 `infra/.env`로 복사한 뒤 DB 이름·사용자·비밀번호를 로컬에서 채운다. 실제 값은 문서·커밋·대화 출력에 넣지 않는다.
3. 루트에서 `docker compose --env-file infra/.env -f infra/compose.yml up -d`로 실행한다. 기존 DB 볼륨의 비밀번호는 `.env` 수정만으로 바뀌지 않는다. 볼륨을 임의로 삭제하지 않는다.
4. IntelliJ 서버 실행 설정에 같은 `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`를 등록한다. Compose의 `.env`가 별도 Java 프로세스에 자동 전달되는 것은 아니다.
5. IDE 개인 설정과 `.env`는 Git에서 제외한다. 빈 `.env.example`, Gradle Wrapper, 소스·테스트·마이그레이션·문서는 공유한다.
6. Docker의 실제 DB 데이터·볼륨과 Codex 대화 내역은 이 Git 저장소에 포함되지 않는다. 새 DB의 구조는 서버 시작 시 Flyway가 생성한다.

통합 테스트만 실행할 때는 Gradle 프로젝트 폴더에서 다음 명령을 사용한다.

```powershell
# Docker Desktop을 실행한 상태에서 통합 테스트 클래스만 실행
./gradlew.bat test --tests "com.juca.homewatermonitorbackend.session.application.SaveNormalTdsSessionServiceIntegrationTest"
```

전체 test의 contextLoads도 이번 하네스에서 Testcontainers 연결을 적용했다. 개발 DB 환경변수는 필요하지 않으며 Docker 엔진이 필요하다. 전체 실행 결과는 상단 최신 검증 기록을 따른다.

## 다음 작업

1. MQTT 5.0 실제 연동·Full 158개/계약 108개 검증 결과를 사용자 최종 리뷰한다. Git 작업은 별도 승인 후 진행한다.
2. 실제 장비 인수 또는 START/명령/상태 보고 계약의 다음 범위를 선택해 별도 계획을 수립한다.
3. 운영 브로커/TLS/ACL·배포·실패 사건 영속화/관측 고도화는 해당 승인 범위를 따른다.

## Git 체크포인트 — 2026-10-21
- 하네스 사용자 최종 승인: DONE.
- 승인된 후속 범위: docs/GIT_WORKFLOW.md 추가, 승인 경계·브랜치 정책 안내, 하네스 변경 커밋.
- 브랜치: codex/harness-bootstrap. main으로의 직접 통합은 하지 않는다.
- 커밋 메시지: chore: establish single-agent harness and verification workflow.
- 이번 추가 변경은 문서뿐이며 기존 전체 11개 테스트 성공 결과를 재사용한다.
- 하네스 후속 승인과 푸시·PR #1·리뷰·main 병합 완료. 원격 main CI 성공: https://github.com/jucadrive/home_water_monitor/actions/runs/39563945349
## Git 체크포인트 승인 — 2026-10-21
- 사용자가 의도한 동작 확인을 조건으로 이번 변경의 커밋·푸시를 승인했다.
- 커밋 전 코드 리뷰에서 계획된 비교 정책과의 불일치를 발견하지 못했다.
- 기존 Full 24개 통과 기록과 이후 비교 테스트 13개 성공 XML을 확인했다.
  최신 XML은 비교 클래스 단독 실행 기록이며 전체 스위트 재실행으로 해석하지 않는다.
- 승인 범위: 소스 3개와 문서 5개, codex/measurement-comparison 브랜치 커밋·푸시.
- 커밋 메시지: feat: add measurement content comparison domain.
- 푸시 후 해당 SHA의 원격 CI를 확인한다. PR 생성·main 병합은 이번 승인 범위가 아니다.
## Git 승인 — 2026-10-21
- 사용자가 보고된 26개 파일의 커밋·푸시 및 원격 CI 확인을 승인했다.
- 커밋 전 변경 범위·diff와 기존 Full 45개 성공 XML을 재확인했다. 소스 추가 변경은 없다.
- 브랜치: codex/normal-session-redelivery.
- 커밋 메시지: feat: record normal session redelivery diagnostics atomically.
- PR 생성·main 병합은 이번 승인 범위에 포함하지 않는다. 원격 CI 결과는 푸시 후 확인한다.
