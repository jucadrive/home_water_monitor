# 실측 수온·온도 보정 준비

상태 VERIFIED(센서 없는 준비 및 실물 정상 경로1회). 2026-10-28 전체 계획 승인, 2026-10-29 실물 재개·사용자 수동 서버/앱 적용 및 정상 인수 완료. 정확도·교정·장애/장기는 별도이며 사용자 최종 리뷰 전 전체 DONE으로 표시하지 않는다.

## 실물 최종 결과 — 2026-10-29

- 사용자 수동 서버/새 APK 적용 후 COM5에 검증된 DS18B20 이미지 업로드 exit0/각 hash verified. 기존 NVS 전체 삭제 없음. 배선 VCC5번3.3V/GND10번/DQ a30↔b30↔D27.
- 직접 UART: seq1~60 각1회, 센서fault0, 보존 확인 뒤 BUFFER_RELEASED1회, 최종IDLE/sessionnone/samples0/Wi-Fi·MQTT 유지. 관측기 exit0/PASS. 증거 `.tools/esp32-personal/accept-565fd13ce4c74536afddaebc9e145537.json`.
- 사용자 앱: ACCEPTED/NORMAL/RELEASE_CONFIRMED/원본60/수온16.13°C/전압 출처ADS1115. 별도 온도 출처는 SQL에서 DS18B20 확인. 25°C 기준 보정 표시는 실제 수온을25°C로 가정한다는 의미가 아니라 기준 온도로 환산한 TDS라는 뜻이며, 표준액 교정과 구분한다.
- 사용자 DBeaver: `.tools/temperature/physical/verify-temperature.sql` 결과가 기대값과 일치한다고 확인했다. 핵심 temperature_source=DS18B20, temperature_count=60, stats_sample_count=50, temperature_assumed_c=NULL. 나머지 SQL 값의 개별 수치 복사본은 수집하지 않았다. 실제 서버 DB의 에이전트 직접 조회/원본 전 필드 독립 대조는 NOT RUN이다.
- 정상 경로 인수 VERIFIED. 기준 온도계 오차 비교·절대 정확도·표준액 교정·센서 분리/복구·장기 운전은 NOT VERIFIED. 추가 시험·코드 변경 없이 사용자 결과와 기존 증거를 기록했다. 서버 SSH 없음, 커밋·푸시 없음, 기존 unstaged/첨부/비밀 설정 보존. 커밋 초안 `feat: add temperature-aware measurement and DS18B20 integration`.

아래 승인/진행 항목의 대기·NOT RUN 표시는 당시 체크포인트다. 현재 결과는 이 최종 결과가 우선한다.

## 승인 범위와 정책

- 실물 정상1회 관측 완료: seq1~60/각1회·fault0·보존확인 후 버퍼해제1회·최종IDLE/보관0/Wi-Fi/MQTT유지, 관측기 exit0/PASS. 사용자 앱 ACCEPTED/NORMAL/RELEASE_CONFIRMED/원본60/수온16.13°C/ADS1115 보고. 직접 UART 온도 수치 기록은 없으므로 앱 보고와 구분한다. 증거 `.tools/esp32-personal/accept-565fd13ce4c74536afddaebc9e145537.json`. 운영 DB 원본 온도/통계50 확인 대기, 기준 온도계 정확도 비교·표준액 교정·장애/장기 인수는 미실행이다.

- 실물 Flash: 사용자 BOOT/EN 진입 후 COM5 ManualDownload 업로드 exit0/이미지 hash verified. DS18B20 최종977744bytes 이미지 적용, 기존 NVS 전체 삭제 없음. 직접 재부팅 후 IDLE/sessionnone/samples0/wifi_up1/rx_ready1, min_heap99384 확인. 읽기 전용 관측기 시작; 실제 온도 원본·서버 정상 보존·버퍼 해제는 사용자 앱 측정 후 별도 검증한다. 현재 온도 실측 인수는 NOT VERIFIED다.

- 실물 단계 후속: 사용자가 서버 반영·APK 설치 완료 보고. COM5 업로드 직전 IDLE/sessionnone/samples0 및 Wi-Fi/MQTT 정상 재확인. 최종 DS18B20 이미지 SHA/빌드 모드·TLS 기간 옵션 확인 PASS. 서버/V9는 사용자 적용 보고이며 직접 원격 검증 아님. 수동 다운로드 모드 진입 후 COM5 업로드 예정, 아직 NOT RUN.

- 2026-10-29 사용자가 센서 도착·VCC5번/GND10번/DQ a30↔b30↔D27 배선을 보고하고 실물 단계 진행을 지시했다. 승인된 다음 단계는 서버 사용자 수동 적용 확인→앱 업데이트→IDLE/미확정0 재확인→DS18B20 업로드→정상60/통계50·온도 보존/해제 인수다. 최초 준비의 업로드 제외와 구분하며 서버 SSH 금지는 유지한다. 이번 실측 기록은 운영 DB에 남는다. 정확도·장애 시험은 별도 결과로 구분한다.
- 사전 확인: 실제 USB COM5, UART STATUS IDLE/sessionnone/samples0/wifi_up1/rx_ready1. 재부팅/START/업로드 없이 읽기 확인. 기존 검증 이미지 SHA 일치. 사용자가 서버/새 APK 미적용이라고 답해 업로드 전 수동 적용 단계에서 대기한다. PC의 adb 기기 목록은 비어 있다.
- 검증 JAR/V9 포함 확인 후 비밀 없는 서버 전송 파일2개를 `.tools/temperature/server-upload-20261029`에 준비했다. 수동 명령은 TEMPERATURE_SETUP의 기존 서버 적용 절차를 따른다. 운영 서버 조작·펌웨어 업로드·온도 실측은 아직 NOT RUN이다.

- 기존 계층·트랜잭션/보존 확인·버퍼 해제 유지. 운영 서버 SSH/배포·운영 DB 적용·보드 업로드·Git 커밋/푸시 제외. 미추적 첨부와 개인 비밀 설정 보존.
- v1 측정·과거 데이터는 그대로 유지. 결과/보존 확인만 `home/water/v2/{deviceId}/events|preservation`, schemaVersion2로 추가하고 START/상태는 기존 v1을 유지한다. 서버 먼저 적용 후 장비 전환이 필요하다.
- v2 session: temperatureSource=DS18B20|SIMULATED, compensationVersion=sen0244-voltage-atc-v1. temperatureAssumedC는 null. calibrationVersion은 별도의 미교정 표식 유지. v2 원본에 temperatureC(최대 소수4자리), temperatureElapsedMs(세션 시작 기준 변환 완료 시점)를 필수 보존한다.
- 보정 유효 수온0~50°C, 원본 시점 이전/동일이며 최대2000ms 이내의 온도만 사용한다. 모의 온도는 실측으로 표시하지 않고 실측 추세에서 제외한다. 신선한 변환 완료/CRC 검증 이전의 전원 초기값은 사용하지 않는다.
- V25=V/(1+0.02*(T-25)), TDS=(133.42*V25^3-255.86*V25^2+857.39*V25)*0.5. 최종 PPM 소수2자리 HALF_UP, 0~1000ppm만 수용. 원래 전압은 보정 전 값. 공식 버전은 제조사 단순 측정 예제 기준이며 교정·독립 정확도 보증이 아니다.
- v2 재수신은 기존4필드와 온도/온도시점을 비교한다. BigDecimal.compareTo·null 규칙을 유지하고 기존 원본을 덮어쓰지 않는다. session의 온도 출처/공식 버전 변경도 거절한다.
- DS18B20 드라이버와 1-Wire는 Espressif 공식 구성요소 버전을 고정한다. 온도 변환 대기가 기존 ADC40ms 수집·제어/MQTT를 막지 않도록 분리한다. GPIO27 후보·외부3.3V풀업/3선 전원 방식, 실물 확인 전 연결/성공 단정 금지.
- 센서 오류·결측·오래된 값은 25°C로 대체하지 않는다. 측정 전 준비 실패는 START 차단, 측정 중 실패는 유효 prefix만 INCOMPLETE 보존·동일 payload 재시도. v1 동작은 유지한다.
- 새 V9 마이그레이션으로 nullable 온도 필드/메타데이터 및 조건부 정합성 제약을 추가한다. V1~V8 편집·과거 재계산 없음. API는 하위 호환 추가 필드, 앱은 기존 가정 기록·실측/모의 온도·공식 구분.

## 순차 Phase와 검증

1. 문서/계약/새 마이그레이션 → backend 저장·조회·재수신·MQTT v1/v2 및 단위/통합.
2. 순수 보정/온도 신선도·모의 공급·DS18B20 어댑터·세션/payload·펌웨어 빌드 및 호스트 실행 시험.
3. Android 요약/원본 온도 표시·실측 추세 필터/호환 파싱, JVM/Lint/빌드/UI.
4. 전용 임시 DB/MQTT 종단: v1 회귀, v2 온도별 정상60/통계50, 오류prefix·동일 재수신·온도 충돌·커밋 후 확인/해제·조회 표시. 새 테스트 수·종료 코드/실패/오류/건너뜀 확인.
- 실물 배선·DS18B20 실제 통신/정확도·장기 운전·교정은 NOT VERIFIED로 남기고 도착 후 인수 절차를 제공한다. Phase별 수정5회/동일원인3회 한도 준수.
- 변경 예상: firmware sensor/session/transport/main/test 및 빌드 스크립트/고정 의존성, backend domain/application/persistence/MQTT/HTTP DTO·테스트, docs/mqtt/v2 및 개인 MQTT ACL 생성/검증/배포 안내, Android 모델/결과/추세·테스트, HANDOFF/관련 명세.

## 근거

- https://wiki.dfrobot.com/sen0244/docs/20305
- https://components.espressif.com/components/espressif/ds18b20
- https://www.analog.com/media/en/technical-documentation/data-sheets/DS18B20.pdf

## 결과

### 최종 결과 — 2026-10-29

- Phase1 backend 최종 Full build: 238개 새 실행, 실패0/오류0/건너뜀0, exit0. v1 회귀·V9 업그레이드·v2 계약/저장/온도 및 시점 충돌·추세 출처를 검증했다. 아래 초기 실패는 수정 전 이력이며 최종 결과가 우선한다.
- Phase2 최종 정수 계산 검토1회: DS18B20 1/16°C를 이용한 정확한 유리수 연산으로 HALF_UP 경계의 부동소수 의존을 제거했다. C host 재검증 PASS: Decimal2080벡터, sanitizer, prefix0~59, 재시도/해제. 기존 종단 시험에 사용한 C payload12개 SHA가 변경 전과 전부 같아 해당 종단 결과를 재사용한다. 계산 수정 후 펌웨어 빌드 결과는 아래 최종 산출물 기록을 따른다.
- Phase3 Android Full: JVM21개 새 실행·실패0/오류0/건너뜀0, debug/미서명 release 빌드 PASS, Lint 오류0/기존 경고11. 격리 EndToEnd16개와 후속 온도 UI3개는 각각 새 실행 PASS(실패/오류/건너뜀0). 서로 다른 실행을 합쳐 단일 실행 수로 주장하지 않는다.
- Phase4 C 생성 payload→MQTT→DB/API12개 케이스 PASS(20/25/30°C × prefix0/1/59/60), 동일 재수신/충돌/미래 온도 거절 확인. 앱 인증→측정→보존/버퍼 해제→모의 수온 표시 및 과거 v1 조회 인수 PASS. TLS/ACL6그룹 PASS·정리 exit0. 운영 계정·DB·센서를 사용하지 않았다.
- 증거: `.tools/temperature/verification.json`, `backend-final-results`, `app-e2e-1/results`, `temperature-ui-results`, `host/result.json`, `wire/result.json`. 각 임시 서버 stopped=closed/exit0 및 시험 컨테이너·adb reverse 정리를 확인했다. 산출물은 로컬 보관이며 Git에 넣지 않는다.
- 일반 HTTPS APK: `.tools/temperature/ppossak-temperature-debug.apk`, SHA256 `FFADE09CBF2708AA3D82E9A0EB5BEDDD05400B3004B6EC3768DF71D676F72965`. 기존 v1 서버 조회와 호환한다. 새 온도 기능 사용에는 사용자의 ACL→backend/V9→앱→장비 적용이 필요하다.
- 실물 DS18B20 통신·배선·정확도·실측 온도 보정·장기 운전은 NOT VERIFIED. 보드 업로드/운영 배포/서버 SSH는 NOT RUN. 센서 도착 후 [실물 인수 안내](../TEMPERATURE_SETUP.md)의 절차로 진행한다. backend 정적/아키텍처 자동 검사는 MISSING이다.
- Git: codex/temperature-compensation, 기준 main4ba56eb, 변경 unstaged. 기존 첨부/비밀 설정 보존, 커밋·푸시 없음. 커밋 초안: `feat: add temperature-aware measurement preparation`.

### 최종 산출물·종료 점검 — 2026-10-29

- 마지막 정수 계산 변경 후 `./scripts/verify-esp32.ps1 -Mode Build -Personal -Temperature DS18B20` 새 실행 PASS/exit0. binary977744bytes(0xeeb50), 앱1MiB 파티션7%여유. SHA256 `BEFE81A368F47BDCDDCD826205D9576550882CAFFFB132E1DDEFD2C04AAE1177`. 아래978272bytes 이미지는 변경 전 이력이다.
- 최종 bin은 `.tools/temperature/firmware/ds18b20-rational-personal.bin`, 로그는 `ds18b20-rational-build.log`. 개인 설정이 들어 있어 공유/커밋하지 않는다. 현재 로컬 빌드 캐시는 DS18B20 모드다. 보드에는 적용하지 않았으며, 이후 업로드 전 원하는 모드를 명시해 빌드한다. 스크립트 기본값 ASSUMED는 유지된다.
- 최종 코드 상태의 C 결과는 `host-final.log`와 `host/result.json`. payload12개 해시 불변 확인 PASS. 이미 통과한 backend/Android/종단 테스트는 이번 종료 과정에서 재실행하지 않고 기존 XML을 집계했다.
- 변경 소스/문서76개 비밀 검사 이슈0, diff 공백 검사 PASS, staged0. 신규 Java/Kotlin200줄 초과 없음. `.codex-attachments/`는 읽기/검사/스테이징 대상에서 제외해 보존했다. 커밋·푸시·배포·보드 업로드 없음.

### 이전 실행 이력

- Phase1: compileJava/compileTestJava PASS. 새 계약14개+MQTT/DB 통합6개 및 과거 마이그레이션1개를 합친 재검증21개 새 실행 PASS(실패/오류/건너뜀0, exit0/58초). 앞선 전체 build는236개 중235개 통과/적용 개수 기대값1개 실패였으며 기존 데이터 보존 스냅샷은 유지한 채 V9 개수 갱신·과거 온도NULL 검사를 추가했다. 전체 최종 build는 마지막 Phase에서 다시 수행한다.
- Phase1 자동 수정3/5: 모의 예제 교정 ID1회, 새 테스트 SQL조인1회, V9 추가에 따른 업그레이드 기대 개수/NULL검사1회. 같은 원인 연속3회 실패 없음. 최초 전체 결과와 수정 후 XML은 `.tools/temperature/phase1-full-attempt1` 및 `phase1-targeted-pass`에 보존.
- ACL 생성 단위5개 PASS/exit0. 실제 자격 증명·ACL은 변경하지 않았다. v2 스키마와 수동 적용 순서는 docs/mqtt/v2/README.md를 따른다.
- Phase2 진행: 공식 ds18b20 0.4.0/onewire_bus1.1.2 고정 및 dependencies.lock 생성. CredentialFree DS18B20 build1035작업 PASS/exit0. 비밀값 제거로 실행부가 최적화되므로 실제 펌웨어 크기 증거로 쓰지 않는다.
- 실제 production C math/session/preservation를 GCC14.3.0 고정 이미지·네트워크 없는 컨테이너에서 Address/UB sanitizer와 함께 실행했다. 독립 Decimal2080벡터 일치, 불완전 prefix0~59 전부, v1/v2 구분·초기10개 제외·잘못된 온도/시점 거절·동일 payload RETRY·v2 확인 후 해제 PASS. C가 생성한12개 온도/prefix payload와 결과는 `.tools/temperature/host`에 보존. DS18B20 RTOS/GPIO 실행은 포함하지 않는다.
- Phase2 개인 DS18B20 모드 build PASS/exit0, binary0xeed60=978272bytes/1MiB 파티션7%여유, SHA2568263DE5B5F2B3D9D54031C7E7A02BBE88F2DC5AF55558FFE313C0FDA3904E064. TLS 기간 검증 유지. 로컬 산출물은 실제 비밀 설정을 포함하므로 `.tools/temperature/firmware`에만 보관하며 공유/커밋하지 않는다. 기존 Unity 테스트 이미지 빌드도 PASS지만 보드 실행은 NOT RUN이다. Phase2 자동수정0회.
