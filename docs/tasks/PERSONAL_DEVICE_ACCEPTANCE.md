# 개인 서버·실물 ESP32 인수

## 승인과 계획 — 2026-10-27
- 사용자 집에서 실물 연동 재개 및 전체 계획 승인: LAN 전용 MQTT TLS/검증·시간 동기화, 개인 서버용 설정 도구/테스트/빌드, IDLE·미확정 버퍼 확인 뒤 업로드, 사용자 수동 서버 적용 후 앱 START→60원본→DB 보존→장치 해제 인수.
- 서버 SSH/원격 조작은 계속 금지한다. 서버 변경은 신규 파일·명령을 제공하고 사용자가 직접 실행한다. 실제 시험 이력은 DB에 남으며 기존 원본/재배/관리/서버 인증/볼륨을 삭제하지 않는다. 커밋/푸시 별도 승인 전 수행하지 않는다.
- 초기 읽기: CH340 COM3 확인, STATUS state=IDLE/session=none/samples=0/wifi_up=0/rx_ready=0. UART STATUS는 재부팅/측정을 하지 않았다. 기존 미커밋/인덱스·첨부 보존.
- 기존 브로커는 Docker 내부1883만 사용한다. 새 추가 Compose로 특정 LAN IPv4의8883만 TLS로 열고 인터넷 포워딩은 추가하지 않는다. 내부 backend→broker 경로/계정/ACL은 유지한다.
- 개인 서버용 인증서/설정은 Git 제외 로컬 전용 경로에 생성하고 기존 시험용 비밀/header를 보존한다. 개인 펌웨어는 WPA2·MQTTS·CA/서버명/유효기간 검증을 요구하며 인증서 검사 해제·평문 fallback을 도입하지 않는다.
- 펌웨어 변경 범위: transport의 CA 연결·SNTP 준비 상태, 개인용 헤더 선택·빌드 옵션. 기존 세션/센서/측정·재전송/보존/해제·MQTT wire 계약은 변경하지 않는다.
- 검증: 인증서/개인 설정 단위, 독립 로컬 브로커의 TLS/잘못된 CA·서버 IP/계정·권한, 고정 IDF credential-free/개인 빌드, 실제 UART Wi-Fi/time/MQTT 상태 및 앱 실측/보존/해제. 미실행을 PASS로 기록하지 않는다.
- 인수 시 실측 Source ADS1115/가정 수온/미교정 근거를 유지한다. 프로브만 물에 담그고 보드/USB/배선은 건조하게 둔다. 교정·독립 정확도·물의 안전성 판정은 이번 범위가 아니다.
- 단계1 문서/기반·단계2 로컬 테스트/최종 빌드·단계3 적용/업로드·단계4 정상 실측 인수 VERIFIED. 서버 적용/앱/DB는 사용자 보고, UART/빌드/로컬 테스트는 직접 관측으로 구분한다. 전체 DONE은 최종 리뷰 후이며 교정·장기/장애 인수는 별도다. 구현 Phase당 수정5회/동일원인3회 중단, 단일 에이전트 유지.

## 로컬 준비 결과
- scripts/create-mqtt-lan-tls.py·configure-personal-esp32.py, TLS 추가 Compose/mosquitto-lan.conf, transport의 CA/검증 시각 gate, 별도 개인 헤더 선택/Personal 빌드 옵션, docs/PERSONAL_DEVICE_SETUP.md를 작성했다. 기존 bench 비밀/header는 보존했다.
- Python312 scripts/test_personal_tls.py:5개 새 실행, 실패0/오류0/건너뜀0·exit0. RFC1918 제한·CA 서명/정확한 IP SAN/EKU·덮어쓰기 거절·CA키 분리·개인 WPA2/TLS·개인키 미포함·입력 주입/leaf 신뢰 거절 확인.
- Python312 scripts/verify-mqtt-lan-tls.py:5개 새 인수 그룹·exit0/PASS, 특정 LAN IPv4의8883만 공개·실제 TLS/계정 성공·잘못된 CA/peer IP 검증 거절·익명/오류 비밀번호·기존 방향 ACL 확인. 시험 계정/CA만 사용했고 임시 컨테이너/볼륨 정리 exit0. 결과 .tools/deployment/ppossak-tls-check-9833687675/result.json. 최초 준비의 해시 도구1회 실패는 원인 미확정이며 독립 공개 fixture 해시 진단exit0 후 같은 인수 재실행이 통과했다. 실제 계정 문제로 단정하지 않는다.
- 고정 IDF CredentialFree/ADS1115/MQTT/Personal Build exit0/PASS. 이어 개인 헤더 포함 Build exit0/PASS, image948576bytes(0xe7960),1MiB 앱 파티션10% 여유. 비밀 없는 이미지는 실행 기능 일부가 최적화될 수 있어 실제 개인 이미지 검증과 구분한다. 실제 CA 기간/이름 검증은 SDK 기본 검증을 유지하고 SNTP 준비 전 MQTT 연결을 시작하지 않는다.
- 사용자 로컬 숨김 Wi-Fi 입력 완료 보고. 새 개인 header·CA/서버key·개인 binary의 git-ignore 확인. 업로드 묶음 .tools/deployment/mqtt-lan-upload에는 신규6개 파일만 포함하고 authority/ca.key는 제외했다.
- 실제 COM3 STATUS 재확인: IDLE/sessionnone/원본0건. 자동 Flash1회는 ROM download 진입 전 wrong boot mode0x13으로 실패해 쓰기 시작 없음. 수동 BOOT/EN 진입을 요청했으며 무단 반복/전체 flash 삭제를 하지 않는다. 이 실패는 컴파일/제품 로직 실패와 구분한다.
- 서버는 새6개 파일의 WinSCP 전송·권한·broker 적용을 사용자에게 안내했다. 현재 적용 확인/실제 TLS·측정·DB 보존은 아직 NOT VERIFIED. 원격 SSH/서버 조작0·커밋/푸시0.

## 실물 진행 결과
- 사용자가 LAN TLS 적용/선택한 LAN IPv4:8883 및 수동 ROM download 진입 완료를 보고했다. 서버 상태를 SSH로 직접 조회하지 않았다.
- 수동 Flash exit0/PASS: 개인 image948576bytes와 bootloader/partition 각각 hash verified, 프로그램 쓰기53초, hard reset 후 정상 기동. NVS 전체/flash 전체 삭제 없음. binary SHA256 A2A7495E7A8141B1114C0ACE7F66C3068BAF220A6C4618ED800E2288B9ECEDCC.
- 직접 UART 상태: IDLE·원본0·wifi_up1/rx_ready1. 개인 CA/MQTTS/시간 gate가 적용된 이미지에서 실제 연결·인증/두 구독 완료를 관측했다. 초기 HWM_TIME_READY 부팅 로그 자체는 수집하지 못했으며, SNTP gate 통과는 해당 소스 경로와 실제 TLS/MQTT 준비 결과로 판단한다.
- 사용자 센서 배선/프로브/앱 준비 확인 후 read-only scripts/observe-personal-esp32.py 실행. 이 도구는 STATUS만 보내고 START/RETRY/리셋을 하지 않는다. 사용자가 앱에서1회 시작했다.
- 직접 관측 결과 exit0/PASS: ADS1115 원본seq1~60 각1회, 센서 fault0, 보존 확인 처리 뒤 BUFFER_RELEASED1회, 최종 IDLE/sessionnone/samples0·wifi_up1/rx_ready1/tx_active0. heap109780/min_heap43408. 원본/보존 확인/해제의 정상1회이며 장시간/모든 장애·정확도 검증으로 확대하지 않는다.
- UART 증거 .tools/esp32-personal/accept-b574333ab0304a3ab9a23c15aa6ae167.json. 세션 식별/원본 수치는 Git 제외 로컬 증거에만 보관한다. 공개 인계에는 payload/비밀번호를 기록하지 않는다.
- 관측기 단위3개 새 실행·실패0/오류0/건너뜀0·exit0. 60순번·단일해제·빈버퍼 필수, 누락/중복/추가 원본·센서fault/미해제 상태의 거짓 성공 거절을 검증했다.
- 앱 상태/출처·DBeaver 읽기 전용 쿼리의 NORMAL/원본60/통계50/센서 calibrationVersion/실제 행60은 사용자 결과 대기 중이다. 초기10개 안정화 제외는 firmware session.c 기준이며 raw60은 모두 보존해야 한다. 아직 직접 DB 전 필드/샘플 값 대조 또는 운영 장애 인수를 수행했다고 주장하지 않는다.

## 최종 검토·수정·회귀 결과
- 최초 정상1회에서 사용자가 앱 ACCEPTED/NORMAL/RELEASE_CONFIRMED/FRESH/원본60/ADS1115, DBeaver NORMAL/preserved60/stats50/sen0244-uncalibrated-v1/실제원본60을 확인했다. 읽기 전용 SQL 결과의 사용자 보고이며 에이전트의 원격 DB 조회가 아니다.
- 이 시점의 설정 리뷰에서 SDK 기존 sdkconfig의 MBEDTLS_HAVE_TIME_DATE가 꺼져 있음을 발견했다. SNTP gate가 있어도 이 옵션을 켜지 않으면 기간 검증이 활성화되지 않는다. 최초 실행은 CA/peer·기능 흐름만 입증하며 만료 검증 완료로 판정하지 않는다. 앞 준비 기록의 SDK 기본 기간 검증 가정은 이 결과로 정정한다.
- 보안 설정 수정1/5: sdkconfig.defaults의 HAVE_TIME/HAVE_TIME_DATE 명시, 기존 bench 설정과 분리된 sdkconfig.personal 강제·개인 main 컴파일 guard 추가. 미래 개인 빌드가 기간 검증 없이 통과하지 않게 했다. 기존 세션/센서/측정/메시지 계약은 변경하지 않았다.
- 실제 Xtensa C 전처리기로 소스의 guard를 추출해 기간 옵션 OFF 거절/ON 통과2조건 PASS. 임시 검사기의 compiler 경로 파싱1회 보완, sandbox DLL 실행 제한은 같은 검사 승인 환경 재실행으로 PASS. 제품 코드 재시도/검사 약화가 아니다.
- 최종 개인 SDK 전체1024개 Ninja 작업 빌드 exit0/PASS, 생성 sdkconfig.h의 HAVE_TIME_DATE=1·ESP_TLS_INSECURE 꺼짐 확인. image949632bytes(0xe7d80),1MiB 앱 파티션9% 여유. 최종 SHA256 B5728F01D447BAC37A5659FDE36E499E8C1862DCFBD3A6343F987AC62359990F. 첫 credential-free 검사는 기간 옵션 수정 전 결과이며 최종 보안 프로필의 새 실행으로 확대하지 않는다.
- IDLE/보관0 재확인·사용자 BOOT/EN 후 최종 수동 Flash exit0/hash verified. 실제 재기동 후 wifi_up1/rx_ready1/IDLE 관측. 추가 서버 변경/새 인증서 생성 없이 동일 TLS endpoint로 연결했다.
- 최종 이미지 정상 회귀1회: 직접 seq1~60/각1회·fault0·BUFFER_RELEASED1회·최종IDLE/sessionnone/samples0·Wi-Fi/MQTT 유지, exit0/PASS. min_heap40752, heap109844. 증거 .tools/esp32-personal/accept-15c5c1a8f96a45c4873d83e0672d6c02.json.
- 최종 회귀 앱은 사용자 ACCEPTED/NORMAL/RELEASE_CONFIRMED/원본60/ADS1115 확인. 두 번째 DB 직접 조회·모든 원본 필드 값 대조는 NOT RUN이며 첫 DB 조회와 최종 API 관측의 범위를 분리한다. 관측 신선도 FRESH의 명시 보고는 최초 실행에서 확인했다.
- 종합 근거 .tools/esp32-personal/normal-flow-verification.json. 실제 정상2회(기간 옵션 보완 전1/최종1), 각60개를 관측했다. 개인키/비밀번호·실제 환경 값·raw payload를 Git 문서에 기록하지 않았다. 초기10개 안정화 제외/통계50의 DB 확인은 최초 실행의 사용자 보고다.
- 인수 범위: 개인 LAN TLS/장치 인증·앱 제어·ADS1115 실측·서버 보존·장치 버퍼 해제의 정상 경로. 어항 안전성·식물 건강·교정/독립 정확도·실수온·모든 네트워크/DB 장애·장기 메모리 안정성을 검증한 것은 아니다.
- 최종 diff/인덱스·Git 제외 확인: 기존 미커밋/첨부 유지, 도메인 session/sensor·적용 마이그레이션 변경0, 신규 Java/Kotlin0·AGENTS118줄. 백엔드/Android 무변경으로 기존 Full을 반복하지 않았다. 에이전트 서버 SSH/원격 명령0·커밋/푸시0. 커밋 초안: feat: connect personal ESP32 over verified LAN MQTT TLS.
