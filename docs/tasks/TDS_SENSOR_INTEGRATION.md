# SEN0244·ADS1115 실제 측정 구현

상태: DONE(3단계 실측 기능, 교정·독립 정확도 제외). 2026-10-23 사용자가 현 검증 수준으로 마무리·Git 반영 승인.
2026-10-23 사용자 전체 계획 및 측정 실행 격리 추가 계획 승인. 기준 main b247145, 시작 clean.
단일 에이전트, 브랜치 codex/tds-sensor-integration. 커밋·푸시·PR·병합은 별도 승인 대상.

## 승인 범위
- SEN0244/ADS1115/ESP32, 납땜·전원·I2C 주소·풀업 확인 후 전원 OFF 배선.
- ADS1115 VDD3.3V, SDA21/SCL22, ADDR GND(0x48), A0=센서 A, 공통 GND.
  센서 +3.3V. ALRT/A1~A3 미사용. 모듈 실크·기존 ADDR 연결을 먼저 확인한다.
- 고정 ESP-IDF5.5.5/ESP-MQTT 유지, 내장 esp_driver_i2c만 사용. 외부 라이브러리 없음.
- 신규 sensor 컴포넌트와 세션 입력 API, 실측/가상 빌드 선택, 실측 인수 도구.
- ADC A0 single-ended/FSR±4.096V/128SPS 연속 변환, 40ms마다 읽은25개 전압 중앙값을 1초 샘플로 사용.
  첫 창은 새 START 이후 값만 사용. 음수/정격 초과/통신 오류/지연값을 정상값으로 보정하지 않는다.
- 전압=raw*4.096/32768 V. 제조사 EC polynomial/2의 TDS 추정, 가정온도25°C/K1.0.
  최종 전압(mV)·PPM만 소수둘째자리 HALF_UP, 환산 전압은 먼저 반올림하지 않는다.
  calibrationVersion=sen0244-uncalibrated-v1. 세션 시작에 측정 메타데이터를 고정한다.
- 부팅 ADC 실패: 새 START 차단/상태 보고 유지. 측정 중 실패: 유효 prefix0~59개를
  INCOMPLETE로 확정/동일 payload 재전송·RETRY 허용, 해제 후 fault로 신규 START 차단.
  실패 원인은 로컬 UART에 범주만 진단하며 MQTT/REST 스키마를 추가하지 않는다.
  보관 버퍼를 리셋으로 버리지 않는다. 재측정 복구는 해제·IDLE 확인 후 점검/재부팅이다.
- 60개/1000ms/초기10개warmup, 기존 불변 원본·재시도·보존·해제 계약 유지.
- DB/API/MQTT 필드/보안·배포/실제 수온 센서/화면/사무실 망 조사 변경 제외.
- 교정 장비 보유 여부 미확인. 실측 수집과 교정·정확도 인수를 구분한다.

## Phase와 파일
1. 부품/자료/배선 계획: 모델·핀은 사용자 확인, 납땜/ADDR/측정도구 확인 대기.
2. ADC/순수 계산: components/sensor/{ads1115.c,tds_sensor.c,tds_math.c,include/*.h,CMakeLists.txt},
   test/sensor_tests.c. I2C 유한20ms 대기. 최초새task없음조건은 아래추가승인으로변경됐고,
   현재 ADC/필터/환산만전용worker, session/제어/MQTT는기존main이소유한다.
3. 실제 세션: session.c/include/session.h, main.c/CMakeLists.txt, esp32.ps1/verify-esp32.ps1.
   기존 가상 경로는 명시 SIMULATED 모드로 유지, 실제 ADS1115 모드와 메타데이터를 구분한다.
4. 장비 종단: scripts/device-test/sensor_accept.py, docs/ESP32_SENSOR_SETUP.md,
   실제 배선→ADC 확인→실측60개/오류prefix/RETRY/DB·해제/새START차단 인수.
5. 교정·최종: 가능한 표준액 비교/반복성, 기존 회귀/최종 실측 이미지 복원/안전 종료/HANDOFF.

## 인수 조건
S01 ADC 주소/설정 재조회, GND와 센서 아날로그 전압 확인. 모듈 미응답/timeout 차단.
S02 ADC signed/endian/0·1·정격상한·음수/포화, 제조사 환산·반올림·범위 테스트.
S03 필터25개/새창/지연·결측/새세션 초기화, I2C가 제어/전송 루프를 무한 점유하지 않음.
S04 실측60개/초기10개warmup/25°C/uncalibrated 명시, 원본 전압·TDS 전체 DB 일치.
S05 실패prefix0/1/59개·통계NULL/누락 진단, 값 대체 없음, 완료 뒤 센서 fault/새START 차단.
S06 오류 후 같은 목록 RETRY·hash 고정, 유효 확인 전 삭제 금지, 실제 커밋과 해제 별도 확인.
S07 기존 C45/가상 종단/Host21/계약100·108 및 관련 전체 회귀. 검사 기대값 약화 금지.
S08 실제 교정·정확도는 표준액/조건 확인 전 NOT VERIFIED. 물리Wi-Fi 단절은 이전 사용자 승인 제외 유지.

## 검증·위험
- Build/CredentialFree/Test 빌드와 보드 Unity 실행을 구분, Host/종단은 전용DB·브로커만.
- 변경 후 PS AST/Python 구문/diff, 관련 단위·통합/실제 보드 검사. 소스 없던 backend181개 재사용 가능.
- 새 Java/Kotlin 파일 예상0개. 계층 변경은 승인한 sensor 컴포넌트/세션 입력 경계에 한정.
- ADC는 프로브가 물속에 있는지/프로브가 끊겼는지 확실히 판단할 수 없다. 합리적 전압만으로 센서 정상/정확도를 보증하지 않는다.
- ±4.096V는 물리 입력 허용 전압을 높이지 않음. 모듈 풀업/전원·빵판 레일 단절 확인 필수.
- 10초 안정화는 초기 정책이며 실측 증거 없이 충분하다고 판단하지 않는다.
- Phase당 자동 수정 최대5회/같은 원인3회 중단. 각 Phase의 실패·수정 횟수 기록.
- 커밋 초안: feat: acquire SEN0244 readings through ADS1115

## 참고
- https://wiki.dfrobot.com/sen0244 (전원3.3~5.5V/출력0~2.3V/범위0~1000ppm)
- https://www.ti.com/lit/gpn/ADS1115 (입력/PGA/I2C/변환)
- https://github.com/DFRobot/GravityTDS (환산·교정 절차 참고, Arduino 라이브러리 도입하지 않음)

## Phase Result
- 최종배선복원후normal FAIL:16건뒤reason=late/elapsed17834/due17602/window18.
  세션8eca0289-ea54-46d9-b9f1-3932e65d5a53 INCOMPLETE16건/통계NULL/해제·IDLE0건 확인.
  1ms조기append 재발과다른 일정지연이다. 현재측정·MQTT가동일main루프를공유한다.
  고정SDK mqtt_client_priv.h의MQTT_API_LOCK은portMAX_DELAY, status property/enqueue가이를사용한다.
  실제지연시킨API구간은미확정이며 SDK잠금가능성을 이번지연의확정원인으로표시하지않는다.
  Phase4 BLOCKED: 기존승인계획의새task없음 조건변경이필요하여아래추가계획 승인대기.
  자동수정2/5 유지. 지연허용치확대·실패시험약화·새task구현은하지않았다.
- 실제interrupted exit0/PASS: SDA분리후45건INCOMPLETE/통계NULL/UART-wire-DB·환산일치/버퍼해제/새START차단.
  SDA분리유지·EN재부팅후init-fault exit0/PASS(새bootId확인/START미발행/가짜세션없음).
  사용자 USB OFF/SDA재연결/USB ON 복원대기. 수정일반942288 이미지유지, 이후final normal/retry 필요.
- 수정후normal60건 exit0/PASS. 이어retry는60건수집/센서오류0이나중후반PUBACK미관찰/RX재접속,
  stage=five publications only FAIL. strict5회/65초/hash 종단은 NOT VERIFIED, 검사완화하지 않음.
  보관60건은같은세션REST RETRY로NORMAL/RELEASE_CONFIRMED/IDLE0건·UART-wire-DB/환산 복구 PASS.
  Phase4 자동수정2/5 유지. 다음 물리SDA오류인수 위한 사용자 신호선 위치확인대기.
- 수정 일반ADS1115 Build942288 bytes/partition여유10% exit0/PASS. 현재보드Test55/로컬build수정실측일반.
  다운로드 준비 대기. 다음flash→normal/retry 재검증. 아직 수정 후실측 인수 완료라고 표시하지 않는다.
- Phase4 진단으로1ms 조기 session_append 원인 확정(7121ms<7122ms); ADC 읽기 완료시각으로 다음창 재설정.
  자동수정2/5, 허용치/1초 검사 유지. 불완전6건 동일세션 REST RETRY 복구·DB 보존/해제/UART-wire-DB PASS.
  Test55 Build201472 bytes/flash/hash PASS, 실제보드55개 실패0/무시0 exit0/PASS.
  수정 일반이미지 Build 진행 중. 수정 후 normal/retry 실측 재검증 NOT RUN.
- 후속 normal 실제60개 exit0/PASS: UART-MQTT-DB 전체필드·독립환산 일치, NORMAL 저장/RELEASE_CONFIRMED/IDLE0건.
  초기10개 제외50개 네통계128.47ppm(가정25°C/미교정). 최초 간헐 오류 근본원인 미확정 유지.
  사용자 표준액 없음 확인. 교정/독립정확도 NOT VERIFIED, 실제 retry 인수 진행 중.
- 2026-10-23 첫 실측 normal FAIL: 58건 후 acquisition 오류→INCOMPLETE DB 보존→장치 해제/IDLE0건.
  UART-DB58건·독립환산 일치/124.06~125.53ppm, 통계NULL/오류후START차단 PASS. 정상60건은 NOT VERIFIED.
  Phase4 자동수정1/5: 판정은 유지하고 원인분류·elapsed/due/window 진단 추가. 오류분류 assertions 보드 실행 NOT RUN.
  진단Build942272 bytes/flash/hash exit0 PASS. 같은 물 normal 재실행 중. 교정·독립 정확도 미검증 유지.
- 사용자 확인: ESP32/ADS1115 헤더 납땜됨, 센서 +/-/A 암 커넥터. ADDR 실연결 미확인,
  비교 TDS 미터/멀티미터 없음. 표준액 여부 미확인. 사진/주소 처리 확인 전 배선·전원 인가하지 않는다.
- Phase2: ADC/계산/25개 필터 구현, Test 빌드199328 bytes/exit0, flash/hash exit0,
  실제 COM5 UART 기존45+신규6=51개 실패0/무시0 exit0/PASS. 배선/실제 ADC 값은 아직 NOT VERIFIED.
  순수 환산은 별도 Decimal 기준과 대조했다(1V367.48/2V879.35ppm).
  테스트2V 기준값 오기를 실행 전 독립 계산으로 바로잡았다. 실패 기반 자동 수정0회.
- 현재 보드 Test51 이미지/네트워크 미사용. Phase3 실측 세션 연결·추가 회귀 테스트 IN_PROGRESS.
- 교정·정확도 NOT VERIFIED. 커밋/푸시 없음.

### 최신 Phase Result — 실측 소프트웨어 준비
- Phase3 실측 샘플 입력/불완전 확정/교정버전 고정/가상·실측 빌드 선택 구현.
  Test200944 bytes/flash/hash exit0/PASS, 실제COM5 UART54개(기존45+센서9) 실패0/무시0 exit0/PASS.
- CredentialFree ADS1115 Build160656 bytes exit0/PASS. SIMULATED 일반 Build919840 bytes exit0/PASS.
  실제 ADS1115 일반 Build942016 bytes/파티션 여유10% exit0/PASS. 아직 실측 이미지는 flash하지 않았다.
- Phase4 도구 준비: sensor_accept.py의normal/init-fault/interrupted/retry, 독립Decimal 환산·UART-wire-DB 대조.
  신규Python4개 PASS, 최종Host25개 실패0/오류0/건너뜀0·PC smoke/확장ACL exit0/PASS.
  PS AST/Python구문/git diff --check PASS. backend/DB/공개 스키마 변경 없어 기존181개/계약100·108 결과 재사용.
- 현재 보드 Test54 이미지/측정·네트워크 없음, 로컬build는 실제ADS1115 일반이미지다. 서로 다름을 유지한다.
- 수정 이력: Phase2/3/4 실패 기반 자동 수정0회. 테스트 삭제/기대값 약화 없음.
- 물리 Phase1/4 BLOCKED: ADDR/풀업·배선 확인용 모듈 앞뒷면/핀 표기 사진이 필요하다.
  사용자가 ADDR 연결 판별 방법을 질문했고 멀티미터가 없어 현 상태를 추정하지 않았다.
  사진 확인 전 ADDR-GND 임의 연결/전원 인가/실측 완료 판정을 하지 않는다.
- S01 실제ADC·S04 실제원본60개·S05/6 물리 오류/RETRY·S08 교정/독립 전압 정확도 NOT VERIFIED.
  센서 없는boot fault 종단·가상종단새실행도 NOT RUN. 소프트웨어54개 통과와 구분한다.
- 신규Java/Kotlin0개. C/스크립트 추가와 기존동작 경계 수정·운영문서 갱신만 있다.
- 다음: 사진으로 실제모듈 주소·핀 확인→전원OFF 배선→실측Build flash/ADC/전용환경 종단 인수.
  최초 전체 승인은 유효하므로 Phase마다 재승인을 요구하지 않는다. 커밋·푸시는 별도 승인 대기.
- 안전 종료 PASS: 전용backend/DB/브로커 중지/시험4포트 listener0/시험규칙2개False.
  변경후보22개 알려진로컬비밀값일치0/diff --check PASS/staged0. 실제 장비는 Test54 이미지를 유지한다.

### 최신 장비 Phase Result — ADC 통신
- 사진과9선/빵판5·10·15·20행/ADDR 연결의일치를 사용자가직접 확인했다.
- 사용자USB/다운로드모드 준비 후 실측942016 bytes flash/hash exit0/PASS.
  같은명령에서부팅UART를수집해 HWM_SENSOR_INIT ready=1/address0x48/uncalibrated-v1,
  simulated0/MQTT/target60 확인. S01의주소ACK·설정쓰기/재조회부분 PASS.
- STATUS IDLE0건/tx_active0/min_heap148436 bytes, 측정START0회. 보드/build 모두실측일반이미지.
  테스트서비스/방화벽중지상태를유지, rx_ready0은브로커중지에따른예상상태.
- TDS보드/A0는미연결. 아날로그전압/물측정/오류/RETRY/교정은아직NOT VERIFIED.
- 다음USB분리/센서보드흰3핀결합사진에서 +/-/A와선색대조 후 검정3구단자-M/M점퍼로배선.
  코드수정/재빌드없음, 실패기반자동수정횟수증가없음. 커밋/푸시없음.

## 추가 계획 — 측정 실행 격리 (2026-10-23 사용자 승인)
- 정상배선복원후두번째worker normal60건 exit0/PASS(UART-wire-DB/환산/NORMAL/해제/IDLE0건).
  현재strict retry5회/65초/동일payload 수동RETRY 최종실행중. 자동수정3/5 유지.
- 실측943600 bytes Build/flash/hash PASS. worker normal60건/통계50/121.11ppm/UART-wire-DB/환산/보존·해제 exit0/PASS.
  worker interrupted 실제SDA분리29건INCOMPLETE/통계NULL/해제/START차단 exit0/PASS.
  worker init-fault SDA분리유지·EN재부팅/새bootId/START미발행/가짜세션없음 exit0/PASS.
  현재정상SDA배선복원준비 대기. 이후normal반복/strict retry 최종검증 필요. 자동수정3/5 유지.
- 최신검증: 보완Test60 Build205184 bytes/flash/hash PASS, 실제보드60개 실패0/무시0 exit0/PASS.
  CredentialFree160656/SIMULATED919856 Build PASS, ADS1115 일반Build중.
  Host25개 실패0/오류0/건너뜀0·PC smoke/확장ACL 새실행 PASS. Python구문/diff PASS.
  sensor_accept 실패진단은MQTT원문없이결과크기/hash·프록시카운터만기록하도록보완, 인수조건유지.
- 사용자 "수정안으로 진행하자"로 전체추가계획 승인. 기존 새task없음 조건은 이 승인 범위에서 변경한다.
- 첫보드Test60 실패4/무시0: Unity64비트표시기능꺼짐2개/cleanup미실행후연쇄init실패2개.
  RTS재부팅은실제데이터없는Test이미지에서만수행했다. 로그.tools/device-test/worker-unity-failure.log.
  전체64비트C비교를TEST_ASSERT_TRUE로유지하고tearDown에worker취소/정리추가, 자동수정3/5.
  실패조건삭제/기대값약화없음. Test60 재Build205184 bytes exit0/PASS, 재업로드준비 대기로 새실행NOT RUN.
- 구현 IN_PROGRESS: 정적task/요청1·결과최대61 고정큐, 값복사/세대태그/실제시각, session 단일소유 유지.
  session 내부 append_observed 경로에서 측정시각과처리시각을분리하여 오래된 전송timeout을 만들지 않는다.
  변경파일에 session.c/include/session.h와 test/sensor_worker_tests.c도 포함(동일 승인 목적의 내부경계 구현).
  기존55+추가5=60개 Test Build205024 bytes exit0/PASS. 실제보드 실행은 다운로드준비 대기로 NOT RUN.
  정적task/큐 생성·소유core 수명·취소·포화신호·실제측정시각과전송deadline분리 구현. 자동수정2/5 유지.
- 변경 이유: 기존의 "새 task 없이 기존 task가 소유한다" 조건에서는 MQTT API의 잠금/정리 대기가
  센서 40ms 주기를 지연시킬 수 있다. 실제 late 오류가 발생했으며 정상60건 반복 완료는 아직 보장하지 못한다.
- 범위: 기존 ESP-IDF/FreeRTOS의 센서 전용task 1개와 고정 크기 요청/결과 큐를 사용한다.
  센서task는 ADC 읽기·25개 필터·환산·실제 완료시각만 소유한다. session/제어/버퍼확정/MQTT는 main 단독 소유를 유지한다.
- 요청은 세션 세대·시작시각·target60의 값 복사다. 결과는 세대·실제측정시각·전압/TDS 또는 오류의 값 복사다.
  세션 객체나 SDK 문자열 포인터를 task 사이에 공유하지 않는다. 오래된 세대 결과는 현재세션에 추가하지 않는다.
- 결과 큐는 한 세션 최대60개와 오류 신호를 담는 유한 크기로 잡는다. 큐 포화/생성 실패/ADC 오류는
  정상값 대체 없이 불완전prefix로 확정하고 기존fault/RETRY/해제후START차단 정책을 유지한다.
- main이 MQTT에서 지연돼도 worker는 실제 입력을 계속 읽으며, main은 큐의 실제 완료시각으로 원본을 추가한다.
  측정간1000ms/지연100ms 검사·원본 불변·커밋확인 이후삭제·기존공개계약·가상모드는 변경하지 않는다.
- START 수락 직후 상태보고 전에 worker를 시작한다. 중복명령은 worker를 다시 시작하지 않는다.
  이전 worker가 종료된 후만 다음 세션을 허용하며 오류 세션의 보관 원본은 같은 sessionId로 복구한다.
- 예상 파일: sensor 컴포넌트의 worker.c/include/worker.h/CMakeLists.txt, main.c,
  sensor_tests.c, sensor_accept.py 및 센서운영문서/HANDOFF. backend/DB/API/SDK고정버전 변경 없음.
- 검증: 기존55개+느린소비자/세대분리/유한큐/오류전파 회귀를 실제보드에서 실행한다.
  결정적main 소비지연에도 측정시각·원본을 유지하는지 확인하고 정상물측정 반복/물리SDA·부팅오류/strict RETRY를 재검증한다.
  Host·제어/버퍼 회귀는 실제변경 영향에 맞춰 실행하고 이전성공과 새실행을 구분한다.
- 위험: task간 수명·큐메모리·세대 경합. 고정메모리/값 복사/단일session소유/포화실패 테스트로 검증한다.
  사무실 MQTT 전송확인 손실은 별도 관측하며 센서격리 성공으로 네트워크 인수까지 PASS 처리하지 않는다.
- 승인 경계: 위 동시성 실행 방식 변경의 최초승인 이후 순차 구현·검증한다. 커밋/푸시는 여전히 별도승인 대상이다.
- 승인대기 안전종료 PASS: 전용server/compose 중지/두 시험규칙False/시험4포트listener0,
  COM5 IDLE0건/tx_active0/rx_ready0/min_heap114216 bytes. 정상SDA배선복원완료, 수정942288 이미지유지.

## 최종 Phase Result — 2026-10-23
- 상태 DONE(실측기능). 최초5개Phase 및 추가worker계획 구현·인수후 사용자가 현검증수준으로마무리·Git반영승인.
  교정·절대정확도는미검증사실을유지한다. 최종리뷰에서추가수정문제없음을확인했다.
- 변경: sensor컴포넌트(ADC/계산/필터/worker), session.h/c, main/CMake, C회귀, 빌드선택스크립트,
  sensor_accept/observer회귀와운영문서. backend/DB/API계약 변경없음. 새Java/Kotlin0개.
- 검증명령/결과: esp32.ps1 Test/Flash 및esp32-control watch --expected-tests60 →60/0/0 exit0/PASS.
  CredentialFree160656/SIMULATED919856/ADS1115 943600 Build exit0/PASS, 일반flash/hash PASS.
  verify_host.py →25개 실패0/오류0/건너뜀0/PC smoke·확장ACL exit0/PASS.
  sensor_accept.py normal 두번/interrupted/init-fault/retry 실제보드각각exit0/PASS.
  strict retry는자동5+수동1/hash1/65초무전송/실측60개/NORMAL·RELEASE_CONFIRMED/IDLE0건, proxy errors0.
  Python구문/diff PASS, 알려진로컬비밀값25개파일후보일치0. backend181/계약100·108/기존가상장비종단은기존성공재사용.
- 인수: S01 주소ACK/설정재조회/3.3V공통GND배선·센서ADC전압읽기 PASS, 독립전압정확도는S08미검증.
  S02/S03 C회귀·실제worker지연소비/포화/취소/오류 PASS. S04 정상60개 반복·메타데이터·환산·모든원본DB 일치 PASS.
  S05 C prefix0/1/59 및worker실제29개INCOMPLETE/통계NULL/해제후START차단·부팅실패 PASS.
  S06 C불완전고정payload RETRY/이전실제6건복구 재사용 + 새worker정상60건strict RETRY PASS.
  오류prefix+프록시차단 결합새시험은NOT RUN이며 수행한것처럼표시하지않는다.
  S07 관련C60/Host25/빌드·장비통합 PASS, 미변경backend/계약은재사용. S08 표준액/비교미터없음·정확도NOT VERIFIED기록완료.
- 위험/한계: 교정·실수온·절대전압/TDS정확도/10초안정화충분성은미검증. 물리Wi-Fi단절시험은사용자승인제외 유지.
  worker전간헐MQTT지연의정확한API구간은미확정이나 승인된격리구조에서위기능인수를통과했다.
- 안전종료: 전용server/compose 중지/시험규칙2개False/시험4포트0/COM5 IDLE0건·min_heap93376 bytes.
- 수정한도:3/5 사용(진단/1ms시각수정/Unityassertion·정리호환). 기존실패기록보존/기대조건삭제·완화없음.
- Git: codex/tds-sensor-integration/기준b247145. 이문서를포함한승인범위25개파일을커밋한다.
  실행결과와최종SHA는git log/status로확인한다. 첨부사진/비밀설정/빌드결과제외, 푸시는승인대기.
  커밋메시지 feat: acquire SEN0244 readings through ADS1115. 커밋승인완료, 푸시/PR/병합은별도명시승인대기.
