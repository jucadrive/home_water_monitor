# 개인용 인증·Android 연동·HTTPS 배포

> 후속 승인: 장비 없는 MQTT 준비는 [별도 명세](PERSONAL_MQTT_READINESS.md), 사용자 수동 적용은 [MQTT 추가 안내](../PERSONAL_MQTT.md)를 따른다. 기본3서비스 배포의 이력을 보존하며 서버에서 추가한 DB loopback 포트를 덮어쓰지 않는다.

## 승인과 목적
- 2026-10-26 사용자 전체 계획 승인 및 문서부터 순차 진행 지시. 단일 에이전트·기존 6단계 루프·Phase당 수정5회/동일원인3회 중단을 유지한다.
- 생활의 불편을 뽀싹 개인용 Android·서버 앱으로 해결하고 AI 에이전트와의 요구사항·설계·검증 경험을 근거로 소개한다. 회원 사업·농업 성과 입증·오프라인 전용 앱은 목표가 아니다.
- 기준 main fc09362(PR13), 브랜치 codex/single-user-auth-deployment. 기존 첨부 폴더 보존. 이번 커밋·푸시·PR·병합은 별도 승인 전 수행하지 않는다.
- Ubuntu24.04 LTS 추정·Intel N150·Docker 사용·PuTTY SSH 가능은 사용자 제공 정보다. 실제 버전·자원·포트·기존 서비스는 배포 전에 확인한다. 도메인은 ppossak-api.jucadrive.com.
- 단일 계정·Spring Security/Resource Server·Android Keystore 연동·전용 DB/서비스·cloudflared/Tunnel·HTTPS 인수는 승인 범위다. 다중 회원·회원 DB·외부 IdP·유료 서비스·기존 서비스 변경·기존 데이터 삭제·장비 시험은 제외한다.
- 2026-10-27 사용자 변경 지시: 실제 서버 배포는 사용자가 직접 수행한다. 에이전트의 미니 PC SSH·원격 조작을 중단하며 재접속하지 않는다. 단계4는 수동 배포 자료 제공까지, 실제 적용·단계5 HTTPS 인수는 사용자 실행 후 결과 확인 범위로 전환한다.
- 2026-10-27 추가 승인: 사용자가 인증 파일을 생성했다. 이를 보존한 채 수동 안내를 Docker 방식으로 다시 쓰고 Dockerfile·backend/DB/cloudflared Compose·로컬 격리 인수를 진행한다. 운영 서버 접속·배포는 승인 범위 밖으로 유지한다.

## 인증 계약
- 계정 이름과 BCrypt 비밀번호 해시를 외부 설정으로 제공한다. 필수 설정 누락/잘못된 설정은 기동 거절한다. 자동 기본 비밀번호·운영 인증 끄기 스위치를 두지 않는다.
- POST /api/auth/login: username/password JSON. 성공200에 accessToken/tokenType=Bearer/expiresAt. 인증 실패401의 일반화된 code/message JSON. 로그인 남용429, 요청 입력400. 원문 비밀번호·토큰을 로그/문서/저장소에 남기지 않는다.
- GET /api/auth/session: 인증 계정·만료 조회. POST /api/auth/logout: 현재 토큰 폐기204. 로그인 외 업무 HTTP API는 인증 필수이며 실패401/권한거절403 JSON, HTML 로그인/리다이렉트 없음.
- 암호학적 난수256비트 토큰, 서버에는 SHA256 식별자만 보관. 기본8시간/최대5개 활성 세션, 만료 정리·로그아웃 폐기. 서버 재시작/계정 변경 재시작 시 전부 무효화. 자동 갱신 없이 재로그인한다.
- Spring Security의 Bearer 토큰 필터·커스텀 OpaqueTokenIntrospector를 사용한다. 쿠키 인증·서버 HTTP 세션·Basic/form login은 사용하지 않는다. CSRF 제외는 이 Bearer 전용 API 경계에 근거한다.
- 회원/소유권 테이블이나 기존 마이그레이션을 변경하지 않는다. 단일 계정은 기존 데이터를 그대로 관리한다. MQTT 인증·계약·트랜잭션은 유지한다.

## 앱 계약
- 서버별 로그인/로그아웃·만료 안내. 비밀번호는 화면 메모리에서만 사용하고 상태 저장·로그·오류 메시지에 포함하지 않는다.
- Keystore AES/GCM 키로 서버 origin에 귀속된 토큰을 암호화한다. 키/암호문 오류는 안전하게 재로그인하며 업무 미확정 요청은 보존한다. 기존 백업 제외·HTTPS 검증·리다이렉트 추적 금지 유지.
- 401/403·로그아웃·재로그인에서도 미확정 요청 키/본문/원본 origin을 유지한다. 로그인 후 자동 업무 재전송을 하지 않고 같은 요청을 명시적으로 재시도한다. 다른 origin으로 토큰/미확정 요청을 전송하지 않는다.

## 단계와 인수
|단계|파일 범위|검증/완료 조건|
|---|---|---|
|1 문서|README/AGENTS/Harness/MVP/설계/연결·실행 안내/HANDOFF/이 명세|정책·링크·승인 경계·AGENTS250줄 이하·diff 확인|
|2 backend|build.gradle·security 패키지·application 설정·인증/HTTP 테스트·테스트 서버|정상/실패/만료/폐기/재시작/제한/미설정·인가 경계, 기존 HTTP 인수는 인증해서 유지, JDK25 Full|
|3 Android|인증 모델/보관/API/VM/설정·로그인 화면·JVM/기기 시험|Keystore·서버 격리·만료·동일 키 재시도, Full/JVM/Lint/APK·명시 기기 UI/소프트웨어 종단|
|4 배포|Dockerfile·3개 서비스 Compose·DB·Tunnel 안내/도구|기존 서비스·볼륨 보존, 호스트 포트 미공개·전용 호스트명·자동 시작·로그·백업/복원·롤백|
|5 HTTPS|실제 서버/폰 인수·결과|무인증401·로그인·재배/관리·만료/로그아웃·재시작/데이터 보존, 외부 직접 우회 차단|

## 운영 경계와 기록
- 2026-10-27 사용자 Docker 전환 승인: backend·PostgreSQL18·cloudflared를 같은 Compose 네트워크에서 실행한다. Java25는 이미지에 포함하고 호스트 포트를 공개하지 않는다. cloudflared→backend:8080, backend→postgres:5432다. 기존 systemd 양식은 제거하며 Cloudflare 계정·터널 비밀과 기존 서비스/공유기 규칙은 유지한다.
- 신규 전용 DB만 준비한다. 기존 DB·볼륨이 발견되면 초기화/덮어쓰기 없이 내용을 확인하고 범위 밖 변경은 중단한다.
- MQTT/제어 기본 비활성화 상태로 재배·관리 API를 인수한다. 장치 측정/보존/해제와 교정·독립 정확도는 별도 NOT VERIFIED 유지.
- 실제 배포는 사용자 직접 수행으로 변경됐다. 서버 OS·권한·Cloudflare·systemd 실행은 에이전트가 확인하지 않았으며 NOT RUN/NOT VERIFIED로 유지한다. 미실행을 PASS로 기록하지 않는다.
- 상태: 단계1 VERIFIED, 단계2 VERIFIED(최종216개), 단계3 VERIFIED, 단계4 수동 자료 VERIFIED/실제 적용 NOT RUN, 단계5 TODO. 전체 DONE은 사용자 최종 리뷰 후다.

## 단계2 결과 — 2026-10-27
- JDK25 compileJava/compileTestJava PASS. 신규 인증11개 포함 backend Full215개 새 실행, 실패0/오류0/건너뜀0·exit0/PASS. build 4분35초,8작업 중6실행/2재사용. 기존 domain·저장·재배·MQTT 회귀 포함. backend 자동 Lint/Arch MISSING 유지.
- 단일 계정 설정/BCrypt 검증·로그인 제한·256비트 임의 토큰/해시 보관·만료/용량·폐기/재시작 무효화, 실제 HTTP 무인증401/인가403·로그인/업무 접근·로그아웃·만료 경계·JSON 오류를 검증했다. 최대 세션 초과 시 가장 오래된 토큰을 폐기한다.
- 기존 HTTP 인수는 테스트 전용 계정으로 로그인하도록 보완했다. 테스트 설정·계정은 test resources/classpath에만 두며 운영 기본 계정이나 인증 비활성화를 도입하지 않았다. 인증 구성은 HTTP 서버에 필수이며 HTTP를 열지 않는 기존 배치형 테스트에는 적용하지 않는다.
- 신규 main Java200줄 초과0. 실패기반 자동수정0/5. V1~V8·업무 도메인·MQTT 계약 변경0. 앱/실제 HTTPS·서버 인수는 아직 NOT RUN.

## 단계3 결과 — 2026-10-27
- Android JVM15개 새 실행·실패0/오류0/건너뜀0, debug·미서명 release·테스트 APK PASS. 최종 Full/test APK 빌드는59초·134작업 중52실행/82재사용. 서버별 토큰·실제 AES/GCM 무결성/다른 키·origin 거절·만료·로그인/로그아웃·401/403 미확정 요청 보존·같은 키 재시도·비밀 응답 오류 노출 방지 검증.
- emulator-5554(API35)에서 Forms8개+AppFlow1개 새 실행9개·실패0/오류0/건너뜀0·exit0/PASS,1분4초·71작업 중5실행/66재사용. 실제 Keystore·비밀번호 제출 후 초기화·실제 HTTP/DB/MQTT의 로그인→재배/관리→모의 측정→보존/해제→조회/종료 확인. 실물·공개 HTTPS 인수가 아니다.
- 첫9개는 Forms8 통과/AppFlow1 실패: 이름이 이전 목록에도 남아 있어 상세 전환 전 detail-list를 찾았다. 같은 조건을 유지하고 대상 화면 tag 생성까지 기다리도록 수정 후9개 통과. 자동수정1/5, 동일원인1회. 첫 실패 XML은 .tools/android-acceptance/auth-1/attempt-1에 보존했다.
- 최종 Lint exit0/PASS, 오류0/경고11(KTX·의존성 안내),28초·30작업 중3실행/27재사용. JVM/APK는 앞 성공 결과를 재사용했다. 신규 Java/Kotlin200줄 초과0, 기존 VM169줄.
- XML·화면: .tools/android-acceptance/auth-1/results 및 screens. 홈·상세·정상/SIMULATED 결과 캡처 검수 PASS. APK .tools/android-acceptance/auth-1/ppossak-auth-debug.apk. 현재 연결은 에뮬레이터뿐이므로 실제 폰 인증 시험 NOT RUN.
- 임시 서버 stop/stopped·Gradle 종료코드0 확인, 이번 ADB 매핑만 제거했다. 에뮬레이터에 새 APK를 설치·실행했다. Docker 엔진/기존 사용자 서비스·볼륨은 유지했다.

## 수동 배포 자료와 인수 경계
- docs/PERSONAL_DEPLOYMENT.md, infra/personal의 Compose/systemd/server.env 양식, application-personal.yaml, scripts/create-personal-secrets.py 제공. 새 비밀 파일을 덮어쓰지 않고 숨김 입력·BCrypt 해시·임의 DB 비밀번호를 사용하며 실제 계정/토큰을 생성·수집하지 않았다.
- 로컬 비밀 생성 도구2개 테스트·Compose config --quiet PASS. 첫 Python 실행2개는 sandbox 임시 폴더 PermissionError였고 명령 승인 후 같은 테스트2개 PASS. 제품 코드 실패/조건 약화가 아니다. systemd·cloudflared 실제 실행은 NOT RUN.
- 임시 SSH 키는 사용자 등록 승인 후 읽기 전용 접속을 시도했으나 로그인/키 형식·공개키 인증 거절로 실제 서버 환경 명령은 실행되지 않았다. 사용자 원격 중단 지시 후 SSH 재시도·서버 조작 없음. 로컬 개인키를 폐기했으며 서버의 이번 공개키 제거는 사용자가 직접 수행한다(배포 안내 참조).
- Cloudflare 계정·Tunnel·DNS는 조작하지 않았다. 실제 기존 서비스·DB·방화벽·공유기 설정 변경 없음. 사용자 비밀번호/Cloudflare 토큰을 받거나 기록하지 않았다. 커밋·푸시 없음.

## 최종 로컬 체크포인트 — 2026-10-27
- personal 프로필을 실제 HTTP 인증 통합 테스트에 적용하고 loopback·MQTT/제어 비활성 검증을 추가했다. 최종 JDK25 build exit0/PASS,216개 새 실행·실패0/오류0/건너뜀0,4분39초·8작업 중6실행/2재사용. 운영 JAR에 personal 프로필 포함·테스트 계정 리소스/클래스 제외를 확인했다.
- JAR: backend/home-water-monitor-backend/build/libs/home-water-monitor-backend-0.0.1-SNAPSHOT.jar, SHA256 615B29993F56992FCB913A3D5029E2997082A98B76CA30EF84D067D57384AFF6. 이 JAR의 서버 기동·systemd/Tunnel·공개 HTTPS·실제 폰 인수는 NOT RUN이다.
- 수동 배포 Compose 문법·로컬 설정 도구2개·Python 구문·diff --check PASS. systemd 양식은 텍스트 리뷰만 했으며 Linux 실행 검증을 주장하지 않는다. AGENTS116줄·신규 Java/Kotlin200줄 초과0. V1~V8·펌웨어 변경0.
- 사용자 원격 중단 지시 이후 서버 접속/원격 명령0. 에이전트 임시 개인키 파일 부재·로컬 시험 서버 stopped 확인. 기존 미추적 첨부/사용자 설정 보존. 현재 변경은 unstaged이며 이번 작업 커밋·푸시0. 커밋 초안: feat: add single-owner authentication and manual HTTPS deployment guide.

## Docker 전환 결과 — 2026-10-27
- 구현: infra/personal/compose.yml 3개 서비스, Dockerfile·Healthcheck.java·.dockerignore 추가. 기존 ppossak.service/ppossak-tunnel.service/server.env.example 제거. docs/PERSONAL_DEPLOYMENT.md·README·HANDOFF를 사용자 수동 Docker 절차로 변경. 실제 인증 원본은 읽거나 수정하지 않았다.
- 인수: 호스트 API/DB 포트 미공개, 내부 서비스 이름 통신, Java25 이미지 포함, non-root backend·읽기 전용 파일 시스템, DB 준비 후 backend/Tunnel 시작, 토큰 파일 마운트/권한, 실제 인증 유지·동일키·재시작·복원. Compose raw env_file은 2.30 이상을 요구한다. 실제 Tunnel은 외부와 연결하지 않았다.
- 명령: Python312 scripts/verify-personal-docker.py. 최종 새 실행 exit0/PASS,9개 그룹·실패0. 구성/해시 보존, build/DB migrations/healthy, HTTP 인증, 생성/조회/동일키, 다른 컨테이너 backend DNS, DB/backend 재시작 데이터 보존/기존 토큰 거절, pg_dump→별도 DB 복원 및 생성 행 확인, cloudflared --token-file와 오프라인 가짜 토큰 읽기, 실제 Linux root:65532/640 파일 권한 검증.
- 결과: .tools/deployment/ppossak-check-640404a8fcc5/result.json. Windows에서는 실제 임의 loopback 포트를 시험 override로만 열었다. 운영 Compose에는 ports가 없는 것을 별도 검사했다. 모든 격리 컨테이너/DB 볼륨/권한 시험 볼륨을 정리했고 정리 exit0이다.
- 검증 스크립트 보완4/5: config가 '$'를 '$$'로 표시하는 점은 실제 컨테이너 환경값 비교로 확인, subprocess UTF8 지정, Docker Desktop 재시작 후 새 임시 포트 조회, 상세 JSON cultivation.id/DB cultivation_id 계약 적용. 앞4회 실패도 각 result.json에 성공 구간/정리 결과를 보존했다. 동일 근본 원인3회 반복 없음. 업무 소스 수정/테스트 약화/인증 우회0.
- 검증 이미지: eclipse-temurin:25-jre-noble@sha256:3f2223fe04b750aa5a8179e29b5c9a1ddf7665fc9bb08bfe11ad01d143bc13a5; eclipse-temurin:25-jdk-noble@sha256:ba0f0e1a33864105e1d454e88105c617ab40f71062db1f306bf58be32a3f8e5e; postgres:18@sha256:74935e72241653ca55e0414067e6d8763aceb8a810eb51b452253ec3dcfc4336; cloudflare/cloudflared:latest@sha256:c6c7ae1d5dfdcb094ad344ed68ccffc26635c9c6be80965664c33baf064b0515 (2026.10.4). 배포 태그는 가변이며 업데이트 시 실제 사용 이미지를 별도 기록한다.
- 회귀: backend/Android/마이그레이션/MQTT 업무 소스 변경 없음. 기존 backend216·Android JVM15·기기9·Lint 결과를 근거 없이 반복하지 않았다. 신규 Java19줄. backend 정적/아키텍처 자동 검사는 MISSING 유지.
- 상태: Docker 로컬 범위 VERIFIED, 실제 Ubuntu/진짜 Tunnel/공개 HTTPS/폰은 NOT RUN. 원격 명령0·커밋/푸시0, 사용자 실제 인증 파일/기존 첨부/서비스 보존. 다음은 사용자 수동 배포와 외부 HTTPS 인수다.
