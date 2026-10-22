# ESP32 MQTT 5.0 펌웨어와 실제 장비 인수 계획

상태: DONE — 승인된 가상 데이터 펌웨어·실제 장비 인수13종/필수 검증 완료. 2026-10-22 사용자 최종 리뷰 승인.
2026-10-22 전체 계획의 5개 선택을 승인받았다. 이후 사용자가 네트워크 확인은 나중에 하고
나머지를 완성하도록 지시했다. 당시 보류한 실제 장비 인수는 현장 복귀 후 재개해 아래 최종 결과로 갱신했다.
작성: 2026-10-22. 기준 main d2332a0, 계획 작성 전 Git clean.
실행 브랜치: codex/esp32-firmware-integration. 시작 시 기존 미커밋은 이 계획 문서뿐이며 보존했다.
운영 규칙: AGENTS.md, docs/Harness.md. 단일 에이전트, 최초 승인 후 순차 실행.

## 1. 복원한 사실과 검증 경계(최초 계획 작성 시점)
- 서버 MQTT 5.0 연동은 PR #6으로 main d2332a0에 병합됐다.
- HANDOFF의 Git 실행 전 체크포인트를 현재 미커밋 상태로 해석하지 않는다.
- 서버 Full 158개·계약 검사 108개·병합 후 CI는 이전 실행/사용자 확인 기록이다.
  이번 계획 작성에서 재실행하지 않았다.
- 저장소에서 .ino/.c/.cpp/.h, platformio.ini, sdkconfig, idf_component.yml 펌웨어를 찾지 못했다.
- 사용자의 Arduino 점검 스케치 저장 위치는 미확인이다. 프로젝트 펌웨어 완료로 간주하지 않는다.
- 실제 장비 사용자 확인: ESP32-D0WD-V3 rev3.1, 40MHz, WROOM-32 상품, CH340 USB-C, COM3.
- Arduino-ESP32 3.3.12 설치. 데이터 케이블 교체로 포트 인식, 수동 BOOT/EN 진입 후 쓰기 100%·
  Hash verified·리셋 및 'ESP32 upload test OK' 1초 반복 출력 확인. 자동 다운로드는 미확인이다.
- MQTT/측정/센서 정확도·장치 재전송·버퍼 삭제는 아직 실제 장비 검증하지 않았다.

## 2. 승인할 개발 환경 제안
- 프로젝트 펌웨어는 firmware/esp32/에 새로 만든다. 기존 백엔드 계층·DB·메시지 두 타입은 유지한다.
- ESP-IDF v5.5.5와 해당 릴리스에서 고정한 ESP-MQTT를 제안한다.
  Arduino-ESP32 3.3.12 역시 IDF 5.5.5 기반이지만 Arduino IDE 설치만으로 MQTT 5 API가
  프로젝트에 제공된다고 가정하지 않는다. 이번에 확인한 Arduino는 USB 업로드 선행 점검 환경이다.
- native ESP-IDF를 선택하는 이유는 MQTT 5 속성, QoS 1 PUBACK 이벤트, 연결 수명주기,
  분할 수신과 outbox 동작을 공식 API/소스 기준으로 제어하기 위해서다.
- Windows ESP-IDF 도구·툴체인 설치는 승인 후 진행한다. VS Code는 선택이며 필수 설치하지 않는다.
- 별도 외부 라이브러리는 최소화한다. ESP-IDF 구성요소/버전을 고정하고 wildcard 의존성을 두지 않는다.
- target=esp32, 업로드 COM3·115200을 초기 기준으로 사용하고 실제 flash 크기·파티션·여유 heap을 확인한다.
- sdkconfig.defaults/빌드 명세는 공유, 실제 Wi-Fi·브로커 비밀값과 로컬 sdkconfig는 Git에서 제외한다.
  민감한 빌드 산출물도 공유하지 않으며 .gitignore를 필요한 범위에서 추가한다.

## 3. START와 sessionId: 먼저 정할 최소 범위
- 완성된 원격 측정 흐름에는 START·명령 식별/만료/중복·sessionId 발급 계약이 필요하다.
- 다만 최초 실제 장비의 결과/보존 확인 인수에 MQTT START·앱·REST까지 필수로 묶지는 않는다.
- 이번 최소안: USB 시리얼 제어로 START <UUID>, RETRY <UUID>, STATUS를 전달한다.
  PC 인수 스크립트가 새 측정마다 UUID v4를 생성해 전달하고 같은 회차 재시도에는 같은 UUID를 사용한다.
- 이는 인수용 명시적 발급/전달 규칙이다. 제품의 최종 발급 주체는 서버를 권고하며
  MQTT START/명령 결과·만료·영속 중복 방어는 별도 공개 계약 승인으로 정한다.
- 부팅/재연결 시 자동 측정·새 UUID 생성·이전 버퍼 폐기를 하지 않는다.
- 측정 중 또는 미확인 버퍼 보관 중 START는 거절한다. 같은 활성 UUID의 START도 측정을 다시 시작하지 않는다.
- 직전 완료 UUID의 START 재사용은 같은 부팅 동안 거절한다. 무한 이력/재부팅 후 명령 중복 방어를 보장하지 않는다.
- RETRY는 보관 UUID가 맞고 자동 회차를 소진한 사용자 대기 상태에서만 새 최대 5회 회차를 시작한다.
  이미 진행 중인 RETRY·잘못된 UUID는 거절한다. 보관 목록/시각/메타데이터는 바꾸지 않는다.
- BOOT은 업로드용으로 유지한다. 사용자 START 버튼으로 재사용하지 않는다.
- EN/전원 재시작에 따른 RAM 원본 유실은 기존 MVP 합의대로 허용하고 자동 재측정하지 않는다.

## 4. 첫 장비 인수 범위와 측정 데이터
- 먼저 실제 ESP32 + 가상 고정 측정 소스로 전송/버퍼/복구를 검증한다.
  calibrationVersion에 simulated 등 식별을 넣고 실제 센서 실측으로 보고하지 않는다.
- 1초 간격 60회, 초기 10개 안정화 제외의 정상 사례와 0/1/59개 최종 목록 사례를 재현한다.
- 시작 시각은 동기화가 검증되면 UTC, 모르면 명시적 null로 고정한다. 재전송 중 갱신하지 않는다.
- 완료한 최종 목록은 immutable 버퍼와 동일 직렬화 payload로 고정하고 이후 원본을 추가/수정하지 않는다.
- 수치는 정수 고정소수점(예: ppm*100)과 정확한 십진 숫자 출력으로 관리한다.
  센서 환산/반올림 정책이나 비교 허용 오차를 새로 확정하지 않는다.
- 실제 SEN0244/ADS1115 배선·ADC·교정·센서 오차 검증은 별도 계획이다.
  연결부/전원/핀을 확인하지 않고 자동 배선하거나 실제 수질 판정을 제공하지 않는다.

## 5. MQTT 전송과 메모리
- docs/MQTT_MESSAGE_CONTRACT.md를 그대로 적용한다. MQTT 5.0만 사용하며 3.1.1로 하향하지 않는다.
- 발행: events, QoS 1, retain=false, Content Type=application/json, Payload Format Indicator=1.
- 수신: 해당 장치의 preservation 토픽만 QoS 1 구독한다. retained=true 확인은 거절할 수 있도록
  Retain As Published를 적용하고 기존 retained 메시지를 요청하지 않는다.
- deviceId·clientId는 서로 구분하고 서버 수신/발행 clientId와 겹치지 않는다.
- payload는 UTF-8 16384 bytes 이하, 프레임/토픽/속성 공간은 별도 확보한다.
  계측 값 버퍼·고정 payload·SDK outbox·수신 조립·스택·TLS 여유를 합산해 실제 heap으로 검증한다.
- 확인 메시지가 여러 MQTT_EVENT_DATA 조각으로 오면 total_data_len/current_data_offset에 따라
  상한 내 조립하고, 토픽·속성·본문이 모두 확인된 완성 메시지만 처리한다.
  끊긴 조각·중첩/초과·잘못된 UTF-8·중복 JSON 키·알 수 없는 필드는 버퍼 삭제의 근거가 아니다.
- SDK 콜백은 제한된 이벤트 큐로 전달하고 단일 제어 task가 상태와 원본 버퍼를 소유한다.
  큐/수신 조립은 유한 크기이며 초과 시 오류를 기록하고 원본은 보존한다.
- 콜백 내부에서 client destroy나 긴 PUBACK 대기를 수행하지 않는다.

## 6. 재전송 회차와 라이브러리 outbox 분리
- 최초 전송 포함 최대 5회, PUBACK 후 보존 확인 대기 최대 30초.
- 두 번째~다섯 번째 시도 전 Full Jitter 상한 1/2/4/8초를 적용한다.
- 연결/준비 제한 10초와 발행 PUBACK 제한 10초를 제안한다. 실패도 각각 현재 시도 1회를 소모한다.
- PUBACK 성공만으로 원본을 삭제하지 않는다. 거절 PUBACK·잘못된 msg_id·이전 연결의 이벤트는 성공이 아니다.
- 전송 시도와 라이브러리 QoS 1 outbox 재발행을 같은 것으로 세지 않는다.
- 제안 구조: preservation 수신 클라이언트는 회차/백오프/사용자 대기 중 유지하고,
  결과 발행 클라이언트는 시도별로 수명을 제한한다. 수신·발행 client ID를 분리한다.
- 발행 client는 자동 재연결을 끄고, 내부 재발행 제한을 애플리케이션의 PUBACK 제한보다 길게 잡는다.
  실패/시간 초과 시 제어 task가 stop/destroy와 outbox 해제를 마친 뒤 다음 시도를 허용한다.
  성공 PUBACK 후에는 발행 연결을 정리하고 독립 수신 연결로 보존 확인을 기다린다.
- 수신 client의 재연결은 상한 16초의 백오프를 제안한다. 새 연결마다 구독 완료를 확인한다.
  초기 장비 인수는 Clean Start=true·Session Expiry=0으로 오래된 장치 세션에 의존하지 않는다.
  수신 연결 유실 중의 확인 유실은 동일 원본 재전송으로 복구한다.
- Wi-Fi/수신 구독이 준비되지 않아도 시도 준비 제한을 무한 연장하지 않는다.
- SDK의 실제 stop/destroy·재발행/삭제 이벤트·고유 연결 세대 식별은 고정 버전 소스와 테스트로 확인한다.
  위 수명 제한을 강제할 수 없으면 테스트를 약화하지 말고 설계 변경 승인을 요청한다.
- 최대 5회는 애플리케이션 시도 수다. 브로커 전달·TCP 재전송까지 물리 패킷 정확히 5개라고 주장하지 않는다.
- 회차 소진 후 발행/자동 회차를 중지하고 버퍼 보관·수신 연결·사용자 RETRY 대기를 유지한다.

## 7. 보존 확인 후에만 버퍼 해제
- strict JSON·토픽·프로토콜 속성·schemaVersion/eventType·deviceId·sessionId를 확인한다.
- preservedSampleCount는 보관한 최종 목록 개수와 같아야 한다.
- NORMAL 60 / INCOMPLETE 0..59 및 comparison=null 또는 동일+충돌=보존 수의 일관성을 검사한다.
- 충돌 건수가 양수인 유효 확인도 기존 합의대로 처리 완료이며 버퍼를 해제한다.
- 확인은 PUBACK 전, 확인 대기, 백오프, 사용자 대기 중 언제 도착해도 유효하면 처리한다.
- 해제 시 활성 시도/타이머를 취소하고 발행 client/outbox 사용이 끝난 뒤 버퍼를 안전하게 해제한다.
  늦은 PUBACK·timeout·동일 확인이 완료 상태를 되돌리거나 버퍼를 두 번 해제하지 못하게 한다.
- 다른 UUID/장치·불일치 건수·거짓 결과·잘린 확인·버퍼가 없는 상태의 확인은 무시하고 사유를 남긴다.
- STATUS는 현 세션·원본 수·시도/회차·연결 상태를 로컬에 표시하되 비밀값/전체 payload는 출력하지 않는다.
- 브로커 PUBACK, 서버 확인 발행, 장치의 실제 확인 수신/버퍼 해제는 별개 증거로 보고한다.

## 8. 실제 장비용 PC 환경과 브로커
- Arduino 기반 USB 업로드 선행 점검은 완료. 다음 도구 설치/설정 변경은 승인 후 진행한다.
- ESP-IDF 도구 준비 후 JDK 25·Docker·현재 서버 설정을 확인한다. 테스트 성공을 개발 DB 준비로 간주하지 않는다.
- 센서 없는 최초 인수에는 PC와 ESP32가 통신 가능한 2.4GHz Wi-Fi/LAN이 필요하다.
  ESP32의 localhost는 PC가 아니다. AP 격리/게스트망·Windows 방화벽·Docker 포트 바인딩을 확인한다.
- 장비 인수용 Mosquitto 2.0.22 별도 Compose/config/ACL 예제를 제안한다.
  서버/장치 전용 계정·정확한 토픽 권한, 비밀 파일/환경값은 로컬 Git 제외로 관리한다.
- 운영 Compose의 기존 DB를 교체하거나 볼륨을 삭제하지 않는다. 익명 접속·공개 인터넷 노출은 하지 않는다.
- 승인 후 지정한 LAN 인터페이스/포트와 필요한 제한된 방화벽 규칙만 적용한다.
  전역 방화벽 해제·TLS 인증서 검증 비활성화는 하지 않는다.
- 첫 인수는 격리된 개발 LAN의 인증된 TCP 구성을 승인 대상으로 제안한다.
  비신뢰망/운영 TLS·인증서 발급/장치 배포는 후속 계획으로 구분한다.
- 새 DB/브로커는 장비 인수 전용으로 만들어 기존 개발/운영 데이터를 건드리지 않는 것을 기본으로 한다.
  원하면 기존 개발 DB 사용은 별도 확인 후 선택한다.

## 9. 예상 파일과 실행 Phase
신규 파일의 책임별 위치:
- docs/tasks/ESP32_FIRMWARE_INTEGRATION.md, docs/ESP32_SETUP.md, docs/DEVICE_CONTROL_CONTRACT.md.
- firmware/esp32/CMakeLists.txt, sdkconfig.defaults, main/CMakeLists.txt, 로컬 설정 예제.
- firmware/esp32/components/session/: 상태 머신·고정 원본·확인 검증·재시도 정책·정수 직렬화.
- firmware/esp32/components/transport/: Wi-Fi·MQTT 5 연결/세대·분할 수신·시리얼 제어.
- firmware/esp32/main/: 시작·구성·가상 측정 공급자, firmware/esp32/test/: 단위/경계/시계 제어 검증.
- scripts/: 가상 UUID 발급/시리얼 제어/장비 인수 도구와 검증 진입점.
- infra/device-test/: Mosquitto/전용 DB·ACL·비밀값 예제. .gitignore 및 HANDOFF 갱신.
- 기존 SESSION_RESULT/SESSION_PRESERVED·서버 저장 로직·V1~V4는 변경하지 않는다.

Phase 1: 도구 버전 고정·빌드/업로드 재현·시리얼 제어/UUID 최소 계약과 상태 모델 확정.
Phase 2: 순수 상태 머신·최종 버퍼·정확한 JSON·확인 검증·타이머/난수 제어 테스트.
Phase 3: MQTT 5 수신/시도별 발행·SDK 수명 제한·전용 장비 인수 브로커/서버 연결.
Phase 4: 실제 ESP32 가상 측정 -> 브로커 -> 서버/DB -> 보존 확인 -> 버퍼 해제 및 장애 인수.
승인 범위 내 순차 진행하며 센서 배선·제품 MQTT START·운영 배포를 추가하지 않는다.

## 10. 인수 조건과 완료 판정
- 재현 가능한 firmware build/flash, 실장 target/flash 크기·실제 여유 메모리 기록.
- MQTT 5.0·QoS 1·retain=false·PFI=1·Content Type과 전체 정상 60개 payload를 브로커에서 검증.
- 최종 목록 0/1/59/60과 JSON 최대 16384 bytes·프레임 추가 공간·분할 수신 경계를 검증.
- 정상/불완전/빈 목록의 DB 결과와 유효 확인 후 장치 버퍼 0건을 각각 확인.
- PUBACK만 수신·서버 중단·DB 실패·확인 차단 시 버퍼가 그대로 있고 다음 시도/사용자 대기로 진행.
- 실제 10초 연결/10초 PUBACK/30초 보존 확인·최대5회·지터 범위를 제어 가능한 시계/난수 테스트로 확인.
- 브로커 단절/재시작·Wi-Fi 단절·PUBACK 유실에서 outbox가 회차 종료 후 임의 발행하지 않는지 확인.
- 같은 UUID·시각·목록을 유지한 재전송으로 DB 원본이 늘지 않고 성공 재수신 진단은 증가하는지 확인.
- 다른 세션·잘못된 비교 합계·불일치 건수·retained·중복 키·미지 필드·깨진 확인은 버퍼를 지우지 못함.
- PUBACK 전/백오프/사용자 대기의 늦은 유효 확인, 중복 확인·늦은 이전 이벤트의 안전성 검증.
- 미확인 버퍼 중 START·진행 중 RETRY 거절, 정상/불완전 확인 후 새 세션 허용.
- EN 재부팅 시 RAM 유실 허용·자동 측정 없음과 서버에 이미 저장된 원본 보존을 구분해 확인.
- 실제 장치 증거는 사용자 시리얼 로그·브로커 기록·독립 DB 조회로 남기고 테스트 모의 결과와 구분한다.
- 펌웨어 신규 검증 명령은 도구 선택 후 실제 실행 가능하도록 추가하고 문서화한다.
  기존 ./scripts/verify-mqtt-contract.ps1 및 ./scripts/verify.ps1 -Mode Full도 영향에 맞게 검증한다.
- SDK 설치/장치/무선망/브로커 접근이 불가하면 해당 항목은 NOT VERIFIED/BLOCKED, 성공으로 보고하지 않는다.
- Phase당 자동 수정 최대5회·같은 근본 원인3회 중단. Git 커밋/푸시/PR/병합은 별도 승인.
- 완료 후 VERIFIED, 사용자 최종 리뷰 후 DONE. 실제 센서 TDS 측정 완료로 표현하지 않는다.

## 11. 승인할 핵심 선택
1. native ESP-IDF v5.5.5 + 고정 ESP-MQTT, firmware/esp32 경로와 Windows 도구 설치.
2. 첫 장비 인수는 가상 측정 + PC 시리얼 START/UUID 발급·RETRY. 제품 MQTT START 계약은 별도.
3. 독립 수신 연결과 시도별 발행 연결, 준비/연결10초·PUBACK10초·기존 보존 확인30초.
4. 전용 인수 DB/인증된 LAN 브로커·필요한 한정 네트워크 설정. 운영/기존 DB는 변경하지 않음.
5. 센서 배선/교정·제품 원격 명령·운영 TLS/배포는 후속 계획으로 진행.

## 추가 승인 — 개방형 Wi-Fi 재개
- 2026-10-22 추가 승인: 이번 가상 데이터 장비 인수에 한정해 개방형 Wi-Fi와 기존 인증 MQTT TCP 사용.
  authMode=open을 명시한 로컬 설정에서만 빈 Wi-Fi 암호를 허용한다. 기존 wpa2 보호 설정은 유지한다.
  변경: configure-esp32.py/설정 경계 테스트·transport/main/예시 헤더·설치/인수/진행 문서.
  검증: 명시적 open/보호 설정/불일치·길이·제어문자 경계, 두 종류 빌드, 실제 Wi-Fi/MQTT 준비,
  기존 정상·재시도·장애·부정 확인 인수. AP 격리/웹 인증 때문에 연결 불가하면 BLOCKED로 보고한다.
  전용 계정/ACL·DB/방화벽 범위를 유지하며 시험 종료 시 자신의 서비스만 중지한다.

## 공식 근거
- ESP-IDF v5.5.5: https://github.com/espressif/esp-idf/releases/tag/v5.5.5
- Arduino core 3.3.12/IDF 기준: https://github.com/espressif/arduino-esp32/releases/tag/3.3.12
- MQTT 5·QoS outbox·분할 수신: https://docs.espressif.com/projects/esp-mqtt/en/latest/esp32/
실제 고정 SDK의 API/소스를 구현 전 재확인하며 latest 문서를 해당 버전의 실행 증거로 사용하지 않는다.

## 실행 기록 — Phase 1 VERIFIED
- IDF b774170ff46c393eeb5e495ea37936038d3f4f4f, ESP-MQTT 0e9ec171b448db2101ea537300976b7ffe595941.
- 공식 install.bat esp32 완료, 격리 Python 3.12.10, xtensa GCC 14.2.0(esp-14.2.0_20260121), CMake 3.30.2.
- 파일: 펌웨어 최소 프로젝트/USB 파서·제어 계약·설치 안내·scripts/esp32.ps1·esp32-control.py·루트 ignore.
- MQTT 계약 108 PASS/실패 0/건너뜀 0(종료 0). PS AST·Python compile·ignore 검사 PASS.
- build 첫 2MB 기본값 PASS 후 실측한 4MB로 재빌드 PASS. 실제 플래시 4MB/3.3V.
- 자동 연결 조사 1회 0x13 실패. 사용자 BOOT/EN 후 --before no_reset으로 flash_id PASS.
- ./scripts/esp32.ps1 -Action Flash -ManualDownload PASS, 각 영역 hash verified.
- IDF Python scripts/esp32-control.py watch --seconds 4 --expect HWM_UPLOAD_OK PASS, 4회 직접 수신.
- app 160336 bytes, 1MiB app partition 여유 85%. 자동 수정 0회, 하드웨어 자동 진입 실패는 별도 환경 기록.
- Git staged 0, 신규 계획 포함 unstaged/untracked 보존. commit/push 없음.
- 위험: 자동 다운로드 미지원으로 이미지 교체마다 사용자 BOOT/EN이 필요하다. MQTT/센서 NOT VERIFIED.
- 커밋 초안: feat: add ESP32 MQTT 5 firmware and device acceptance harness.
- 다음 Phase 2: 상태 머신·불변 목록·확인 검증·재시도 단위/경계 검증.

## 실행 기록 — Phase 2 VERIFIED
- session 컴포넌트에 상태 머신·고정 목록/payload·정수 소수 직렬화·strict 보존 확인 파서·유한 분할 조립 구현.
- 준비 10초/PUBACK 10초/확인 30초, 회차 5회/지터 상한/수동 RETRY·세대/이전 이벤트 방어.
- 확인 건수는 double로 반올림하지 않고 십진 지수/소수 자리를 정확히 계산한다.
  59.999...가 60으로 반올림돼 버퍼 삭제를 허용하지 않는다.
- ./scripts/esp32.ps1 -Action Test 빌드 PASS(종료 0), PUBACK 정확한 시간 경계 리뷰 보강 후 다시 빌드 PASS.
- 사용자 다운로드 모드 진입 후 Flash -ManualDownload PASS(종료 0, hash verified).
- IDF Python scripts/esp32-control.py watch --seconds 4 --expected-tests 31 PASS(종료 0).
  실제 장치 HWM_TEST_RESULT tests=31 failed=0 ignored=0 직접 수신. 반복 출력은 31개 테스트의 재실행이 아니다.
- 정상/빈/1/59개 목록·5회 소진/불변·지터·PUBACK 단독/거절/지연·유효/부정/중복 확인·16KiB/분할 검사 통과.
- 테스트 app 188192 bytes, 1MiB partition 여유 82%. 실패 기반 자동 수정 0회.
- Git staged 0, 기존 계획 포함 unstaged/untracked만 유지. 서버/DB 코드 변경 없음.
- 위험: MQTT 라이브러리 수명·실제 네트워크/DB/버퍼 해제는 다음 Phase에서 별도 인수.
- 다음 Phase 3: Wi-Fi/MQTT 5 어댑터·UART 제어·전용 인수 브로커/DB.

## 실행 기록 — Phase 3 구현/PC 검증 완료, 실제 MQTT 연결 NOT VERIFIED
- Wi-Fi RAM 설정·독립 MQTT 5 RX/시도별 TX·고정 토픽/속성·RAP/RH2·유한 이벤트 큐 구현.
- 콜백 문자열 수명 안에서 분할 데이터를 조립/검증하고 고정 값 이벤트만 제어 task로 전달한다.
  잘못된 한 프레임의 나머지 조각은 폐기해 오류 이벤트로 큐를 채우지 않는다.
- SDK source 확인: client stop 시 outbox_delete_all_items, destroy 시 outbox_destroy.
  MQTT 5 PUBACK reason은 event.data의 선택 byte, 없는 경우 성공이며 >=0x80은 거절이다.
  속성은 enqueue/subscribe 중 직렬화된 뒤 SDK가 해당 포인터를 초기화한다.
- 실제 ESP32 일반 이미지 build/flash PASS. Wi-Fi/IP 성공(wifi_up=1), UART STATUS/7종 부정 명령·CRLF PASS.
  rx_ready=0, TX 미실행 상태에서 heap 약 17~18만 bytes, min_heap 약 17만 bytes.
  성공한 두 MQTT 연결의 최대 메모리 사용을 검증한 값이 아니다.
- 환경 조사 오류: Disconnected Wi-Fi에 남은 192.168.0.38을 초기 bind로 선택했다.
  실제 PC 활성 유선은 조사 시 192.168.1.14였고, 사용자 확인상 ESP32는 다른 AP/핫스팟을 사용했다.
  따라서 PC→포트 성공과 장치 Wi-Fi/IP 성공을 같은 LAN/MQTT 성공으로 판단하지 않는다.
- UAC로 초기 주소/서브넷에만 18883 방화벽 규칙을 추가했다. 이후 helper는 활성 어댑터 확인/서브넷 계산,
  기존 전용 규칙 갱신·선택적 18884 proxy 포트를 지원한다. 재개 전 활성 IP로 갱신/제거가 필요하다.
- 전용 Mosquitto 2.0.22/PG18/계정 ACL·고유 DB/loopback 바인딩 구성. 기존 운영 Compose/볼륨 변경 없음.
  read-only Windows 암호 파일 권한으로 최초 기동 실패 후 private tmpfs/0600 소유 파일로 수정해 PASS.
- 새 도구 의존성 없이 backend Paho 1.2.5와 IDF Python의 pyserial/psutil을 재사용한다.
  로컬에 남아 있던 예전 JAR 대신 현재 소스를 JDK 25 bootJar로 새 빌드한 뒤 실행했다.
- Host 검증 14개 PASS/실패 0/오류 0/건너뜀 0. 실제 브로커에 대한 PUBACK 유실/거절·확인 유실 복구,
  프레임/변수 길이/상한·loopback/private bind 경계를 검사했다. Observer stdout 회수 경고도 수정했다.
- PC 모의 장치: MQTT 5 속성·빈 최종 목록·독립 DB 보존 조회·확인·장치의 preservation 쓰기 거절 PASS.
  PC 모의 장치와 실제 ESP32 검증을 구분한다. 실패 기반 수정 2회(브로커 파일 권한/observer 자원 회수).

## 실행 기록 — Phase 4 도구 구현 완료, 실제 인수 사용자 보류
- accept.py: 정상/재전송/5회 소진/DB 실패/브로커 재시작/PUBACK 유실/조기 확인/부정·16KiB 확인 시나리오.
- IDLE·rx_ready 및 proxy 이미지 연결 식별 후에만 START. UUID/목록 hash/속성·DB 원본 내용/재수신 진단·
  실제 UART 해제와 최종 0건을 교차 확인한다. 실패 후 환경 복구는 자신의 전용 서버/DB에만 적용한다.
- accept.py 전체 시나리오는 **NOT RUN/NOT VERIFIED**. 구문/CLI 확인과 PC 장애 주입 도구 검증만 완료했다.
  실제 Wi-Fi 전파 단절·장치 SDK outbox·늦은/중복 MQTT 확인·실제 삭제/재부팅 인수는 남았다.
- [재개 절차](../ESP32_ACCEPTANCE.md)에 활성 망 확인·재바인딩·설정/빌드/수동 flash·케이스별 명령을 기록했다.
- 최신 일반 이미지 빌드 913104 bytes, 1MiB app partition 여유 13%; credential-free 빌드 160656 bytes PASS.
  최종 일반 이미지 HWM_TEST_IMAGE=OFF/HWM_CREDENTIAL_FREE=OFF. 보류 후 최종 이미지 flash는 NOT RUN.
  장치에는 이전 연결 진단 이미지가 남아 있다. 재개 시 올바른 망/주소로 configure→build→flash해야 한다.

## 최종 자동 검증/검토 체크포인트
- ./scripts/verify-esp32.ps1 -Mode CredentialFree 및 Build PASS(종료 0).
- ./scripts/verify-esp32.ps1 -Mode Host PASS(종료 0), 14개/실패 0/오류 0/건너뜀 0 및 PC smoke PASS.
- 실제 ESP32 Unity 31개와 UART smoke는 앞선 새 실행 결과를 재사용한다. 이후 순수 session 소스 변경 없음.
- ./scripts/verify.ps1 -Mode Full PASS(종료 0), 26 클래스·158개/실패 0/오류 0/건너뜀 0.
  test 새 실행, XML KST 2026-10-22 12:38:06. compileJava/processResources/bootJar는 UP-TO-DATE.
- 계약 108개 PASS(이번 작업 초반 실행), 계약 자체는 이후 변경하지 않았다. PS AST/Python compile PASS.
- Git 대상 161개 파일에서 실제 로컬 비밀값 일치 0건. .env/자격 증명/SSID 설정/생성 헤더/바이너리 제외 확인.
  이 검사는 포괄적 보안 보증이 아니다. 신규 Java 1개 85줄, 200줄 초과 없음. AGENTS 103줄.
- 리뷰: backend domain/application/영속성·V1~V4·SESSION_RESULT/SESSION_PRESERVED·기존 Compose/CI 변경 없음.
- staged 0, unstaged/untracked만 존재. 기존 계획 문서 보존, 커밋·푸시·PR·병합·원격 CI 없음.
- 커밋 초안: feat: add ESP32 MQTT 5 firmware and device acceptance harness.
- 다음: 실제 네트워크 준비 후 Phase 3 연결/Phase 4 장비 인수를 재개한다. 센서/TLS/제품 START는 별도 후속이다.
- 종료 정리: 자신의 백엔드 PID와 전용 장비 인수 컨테이너만 중지했다. DB 볼륨/로컬 설정을 지우지 않았다.
  초기 주소용 18883 방화벽 규칙은 유지되므로 재개 시 활성 IP로 갱신하거나 명시적으로 제거한다.

## 재개 실행 기록 — 개방형 Wi-Fi, 2026-10-22
- 추가 승인 범위는 이번 가상 데이터 인수뿐이다. 비밀값은 로컬 제외 파일에만 반영했다.
- 명시적 open 설정·기존 보호 설정·불일치/길이/제어문자 검증 추가. Host 17개/실패·오류·건너뜀 0,
  PC smoke PASS. credential-free 160656 bytes 및 일반 913136 bytes 빌드/수동 flash PASS.
- 활성 Wi-Fi IPv4/서브넷으로 전용 Docker bind와 18883 방화벽 규칙을 갱신했다.
  실제 ESP32 UART wifi_up=1/rx_ready=1, 정상 60개 최종 목록·DB 원본 내용·보존 확인 후 해제 PASS.
- retry/db-outage/broker-restart 각 실제 PASS. 각 2회 관찰/동일 payload 해시 1개, DB 보존·실제 버퍼 0 확인.
  보관 중 새 START/다른 UUID RETRY, 완료 UUID START·중복 확인 거절도 실제 PASS.
- exhaust 최초 FAIL은 인수 도구의 잘못된 전제(5시도=5개 브로커 수신)였다. 장치는 5시도/60개 보관/
  tx_active=0/MANUAL_WAIT, 브로커 수신 1개였다. 합의대로 준비/송신 실패도 시도를 소모하므로
  장치 시도=5와 실제 발행<=5를 구분하고, 소진 시 관찰 수가 이후 65초간 증가하지 않는지 검사한다.
  테스트 삭제/비활성화 없이 원본/불변·버퍼 유지·뒤늦은 확인·DB 대조 조건을 유지했다. 수정 1회.
  재실행 PASS: 5시도/브로커 수신 4개/동일 hash 1개, MANUAL_WAIT에서 65초 추가 발행 0,
  서버 복구 후 뒤늦은 실제 보존 확인·DB 원본 대조·버퍼 해제·중복 확인/완료 START 거절 통과.
- 실제 송신 지연 조사를 위해 값 없는 MQTT 오류 코드 진단을 보강했다. 프록시 이미지 빌드 913248 bytes PASS.
  이 진단 이미지는 아직 flash 전이며 최초 이미지의 통과 증거와 구분한다.
- 사용자가 외부에 있어 다음 UAC/BOOT/EN은 수행할 수 없다고 답했다. 프록시 3종/경계 0·1·59개/
  실제 Wi-Fi AP 단절/EN 재부팅 인수는 NOT RUN/NOT VERIFIED. 현재까지 5종 성공을 전체 완료로 표현하지 않는다.
- 안전 종료: 전용 서버/컨테이너 중지, 시험 listener 4개 포트 모두 0. 자신의 UAC 실행기는 PID/생성 시각
  확인 후 종료했지만 consent 창 1개는 남았다. 현장 복귀 시 취소한다. 18884 규칙은 미생성,
  18883 한정 규칙만 유지하며 listener는 없다. 기존 DB/볼륨·자격 증명·미커밋 변경은 보존했다.
- 재개: 대기 UAC 취소 → 활성 IPv4 재확인/bench 기동 → 18884 한정 규칙 → 프록시 이미지 수동 flash
  → proxy-puback/proxy-early/proxy-negative → 0/1/59개 → 일반 60개 이미지 복귀/재부팅 인수.
  현재 생성 헤더/build는 18884/60개이며 보드에는 이전 18883/60개 이미지가 있다.
- 새 Java 200줄 초과 없음. PS AST/Python 구문/diff 검사 PASS, Git 후보 162개 비밀값 일치 0.
  backend/계약은 변경하지 않아 앞선 Full 158/계약 108을 재사용했다. 커밋/푸시 없음.

## 현장 복귀 실행 기록
- 동일 활성 망/COM3 IDLE·버퍼 0을 재확인했다. 18884 한정 규칙은 생성돼 있고 consent 창은 없었다.
- 전용 환경 재기동 후 프록시 진단 이미지 913248 bytes 수동 flash/hash 검증 PASS.
- proxy-early 최초 시도는 UART STATUS 1초 제한을 넘겨 START 전 중단했다(원본/전송 0).
  SDK RX/TX 정리의 각 유한 네트워크 대기를 반영해 도구 refresh 제한을 25초로 수정했다.
  판정 조건은 유지했고 proxy-early 재실행 PASS: 성공 PUBACK 없이 실제 보존 확인/DB 대조/버퍼 해제,
  완료 UUID 재시작/중복 확인 거절 통과. Phase 4 실패 기반 수정 누적 2회(횟수 가정/UART 대기).
- proxy-puback/proxy-negative 진행 중. 이후 0개 일반 이미지 913248 bytes 빌드 PASS, 아직 flash 전.
- 실제 Wi-Fi 단절 검사에 개인 2.4GHz 핫스팟 준비 가능하다고 사용자가 답했다. 공용 AP는 끄지 않는다.
- proxy-puback은 5회 소진/65초 무전송까지 관찰 후 수동 RETRY의 초기 2시도 동안 발행되지 않아,
  기존 database()의 40초 증거 대기 제한으로 FAIL했다. 버퍼는 60개/같은 UUID를 유지했다.
  수동 회차는 최대 5시도를 관찰하도록 유한 360초/다음 MANUAL_WAIT 조기 실패 조건을 추가했다.
  인수 결과/조건을 낮추지 않는다. 실패 기반 수정 누적 3회, 재검증 중.
- 실패 버퍼는 전용 DB의 커밋된 세션 건수·원본 수를 독립 조회한 뒤 동일 UUID의 유효 확인만 보내
  실제 해제/IDLE·0건을 확인했다(recover_buffer.py). 재부팅/삭제로 실패를 지우지 않았다.
- manual_accept.py는 공개 제어 계약 변경 없이 실제 핫스팟 OFF/ON·EN 조작 전후의 UART/DB 증거를
  수집한다. 구문 확인 PASS, 실제 실행은 후속 단계다. 생성 자료는 기존 Git 제외 경로만 쓴다.
- proxy-puback 수정 후 실제 재실행 PASS: ACK/확인 차단, 5회 소진·65초 추가 무전송,
  같은 UUID RETRY·동일 payload/DB 원본·유효 확인 후 해제·완료/중복 거절을 모두 확인했다.
- proxy-negative 첫 실행: 부정 13종/16KiB 유효 확인·DB 대조·실제 해제까지 통과했으나 같은 16KiB
  확인의 중복 검사 10초 제한에서 FAIL. 앞선 실제 전달 약 19초에 맞춰 40초로 수정(누적 4회).
  재실행은 5시도 소진 직후 늦은 발행 관찰로 무전송 검사 FAIL. 기존 실패 기록을 숨기지 않으며
  새 송신/지연 전달을 구분할 TX 연결 식별·MQTT DUP 진단을 보강했다(원문/인증정보 저장 없음).
  이 실행도 DB 커밋 독립 확인 후 동일 세션의 유효 확인으로 버퍼를 해제해 복구했다.
- 개인 2.4GHz 핫스팟으로 옮겨 나머지를 검증하기로 진행한다. 실제 proxy-negative 전체 PASS는 아직 아니다.
- 개인 핫스팟 활성 IP 10.206.235.177/24로 전용 bench·18883/18884 규칙을 갱신했다.
  처음에는 실제 AP가 5GHz/149만 송출해 ESP32 reason=201/AP 미발견이었으며 START는 하지 않았다.
  SSID는 실제 연결망과 일치했다(Windows 프로필 이름 차이는 SSID 오타가 아니었다).
  사용자 Galaxy S26+에서 2.4GHz 전용 설정 후 OFF/ON, 실제 2.4GHz/채널1·같은 PC IP 유지,
  장치 Wi-Fi/MQTT 자동 복구를 확인했다. 0개 이미지 재업로드 없이 normal/0 실제 PASS.
- Host 최신 전체 19개/실패·오류·건너뜀 0·PC smoke PASS. 핫스팟 일반 이미지 913264 bytes 빌드 PASS.
  1개 이미지 수동 업로드/인수 진행 중, 59개/프록시 부정 전체/실제 버퍼 보관 중 Wi-Fi 단절·EN은 남았다.
- 핫스팟 normal/1 및 normal/59 실제 PASS. 각 DB 원본 전체 내용/INCOMPLETE 보존 확인·실제 버퍼 0,
  완료 UUID 재시작/중복 확인 거절 통과. 일반 18883/59개 보드에서 프록시 18884/60개 이미지 교체 준비.
- 핫스팟 proxy-negative 전체 실제 PASS(종료0): 5회 발행/동일 hash1·각기 다른 TX 연결5,
  MQTT DUP 없음, 65초 추가 무전송, 부정13종 거절 시 보관, 유효16KiB 분할 확인 후 해제,
  동일16KiB 중복/완료 UUID START 거절. 사무실 지연 현상의 근본 원인은 단정하지 않는다.
  제품 코드를 바꾸거나 무전송 판정 조건을 완화하지 않고 망을 바꿔 재검증했다. 수정 누적4회 유지.
- 최종 일반 18883/60개 이미지 913264 bytes 수동 flash/hash 검증 PASS.
  manual_accept 첫 실행은 부팅 직후 구독 준비 전이라 START 없이 중단했다. 같은 일반 도구의
  유한 40초 IDLE/수신 준비 대기를 적용해 재개했다. 실패 기반 수정 누적5회/한도 도달,
  추가 실패 시 이번 구현 Phase에서 더 수정하지 않고 원인·필요 조치를 보고한다.
- 실제 wifi-loss 인수 진행 중. 현재 장치/로컬 build 모두 개인 핫스팟용 일반 18883/60개다.
  최신 Git 후보165개에서 SSID/실제 비밀값 일치0, 미커밋/staged0 보존.

## 최종 Phase Result — VERIFIED
- 구현 내용: 승인한 ESP32 MQTT5 펌웨어·불변 최종 목록·확인 전 보관·유한 재시도/수동 RETRY·
  실제 커밋 근거의 유효 확인 후 삭제, USB 제어·PC 관찰/장애 주입·수동 장비 인수 하네스 완료.
- 변경 파일: firmware/esp32, scripts의 설정/SDK/전용 인수 도구, infra/device-test, ignore/AGENTS와 관련 문서.
  최초 미커밋 계획 문서 포함 보존. backend 도메인/저장/트랜잭션·V1~V4·공개 MQTT 계약 변경 없음.
- 실제 인수13종/종료0: normal 0/1/59/60, retry, exhaust, db-outage, broker-restart, proxy-puback,
  proxy-early, proxy-negative, wifi-loss, reboot. 모두 최종 IDLE/0건 직접 증거 확인.
- 마지막 wifi-loss PASS: 실제 핫스팟 OFF20초/ON, wifi_up=0에서도 UUID/60개/확정 상태 유지,
  복구 후 같은 UUID RETRY/6발행·동일 hash1·원본 전체/DB 보존·유효 확인 후 해제.
- 마지막 reboot PASS: 미확인60개 상태의 실제 EN 부팅을 UART로 관찰, IDLE/0건·자동 START 없음,
  재부팅 전 세션의 늦은 보존 확인 거절·서버 원본60개 전체 내용 일치. RAM 유실은 합의한 MVP다.
- 검증 명령: esp32.ps1 Build/Flash -ManualDownload, accept.py 각 case/목록 수,
  manual_accept.py --case wifi-loss/reboot, verify_host.py, PS AST/Python 구문/git diff --check.
  최신 Host19개/실패·오류·건너뜀0·PC smoke PASS. 순수 C31/실제 UART7종·backend Full158/계약108은
  앞선 직접 성공 결과를 재사용하며 새 실행으로 주장하지 않는다. 자동 Lint/아키텍처 검사 MISSING 유지.
- 마지막 펌웨어 일반18883/60개/개인 WPA2 2.4GHz 핫스팟, 913264 bytes/1MiB 여유13%.
  보드·로컬 build 일치. HWM_TEST_IMAGE=OFF/HWM_CREDENTIAL_FREE=OFF.
- 실패/제한: Phase4 자동 수정5회 후 모두 재검증. 사무실 망 지연 근본 원인은 미확정이며
  그 망에서 부정 인수 전체가 통과했다고 표현하지 않는다. 최종 경계·부정·단절/EN은 개인 핫스팟.
- 안전 종료: 전용 백엔드·컨테이너 중지, 시험 listener4개 포트0, 자신의 한정 방화벽 규칙2개 비활성화.
  다른 프로젝트/DB·볼륨·로컬 비밀 설정 보존. Git 후보165개 비밀값 일치0, 새 Java85줄.
- Git 상태: codex/esp32-firmware-integration, tracked 수정3개/untracked44개/staged0,
  커밋/푸시/PR/병합/원격 CI 없음. 커밋 초안은 feat: add ESP32 MQTT 5 firmware and device acceptance harness.
- 사용자 최종 리뷰 및 커밋·푸시·PR 생성·리뷰·main 병합·CI 확인·로컬 동기화 승인(2026-10-22).
  위 Git 상태는 실행 전 체크포인트이며 실제 통합 결과는 Git/PR/Actions로 확인한다.
- Git 통합 완료: PR #7/main 1aaaa06, push·PR·병합 후 CI 성공. 사무실 망 지연 원인 조사는 사용자 지시로 제외했다.
  센서 실측/교정·제품 MQTT START/서버 UUID 발급·TLS/운영·전원 손실 시 영속 버퍼는 별도 후속이다.
