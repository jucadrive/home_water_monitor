# 개인 서버 MQTT 준비 — 실물 없이 수행

## 승인·계획 — 2026-10-27
- 사용자 승인: 배포 문서·MQTT Compose·인증/권한·백엔드 연결·로컬 모의 장비 인수를 지금 수행한다. 실제 서버 적용은 사용자 직접 실행이며 SSH·원격 조작·장비 시험·펌웨어 업로드는 제외한다.
- 사용자 보고: HTTPS 배포·앱 로그인/API·DBeaver SSH 터널로 등록 데이터 조회 성공. 에이전트 직접 관측이 아니며 실물/MQTT 성공 근거와 구분한다.
- 변경: infra/personal/compose.mqtt.yml·mosquitto.conf, MQTT 전용 비밀 생성 도구, 로컬 모의 장비 인수 도구, docs/PERSONAL_MQTT.md·기존 배포 안내/README/HANDOFF/AGENTS 링크.
- 기존 3개 서비스 Compose와 서버에서 추가한 DB loopback 포트는 덮어쓰지 않는다. 추가 Compose를 명시 병합해 총4개 서비스를 실행한다. 로그인/DB/Tunnel 비밀과 운영 DB 볼륨은 보존한다.
- API/MQTT 메시지·DB 스키마·업무 트랜잭션·펌웨어 변경 없음. 외부 기술/회원/새 공개 엔드포인트 추가 없음. 기존 고정 Mosquitto2.0.22/Paho1.2.5를 사용한다.
- 브로커는 호스트/LAN/WAN 포트 미공개, 백엔드는 tcp://broker:1883으로 내부 통신한다. ESP32의 LAN 연결·TLS·Wi-Fi·빌드/업로드는 집에서 별도 검토/인수한다. 기존 시험용 평문 네트워크 예외를 운영 LAN으로 확대하지 않는다.
- 계정: 서버와 장비 분리, anonymous 거절, 승인한 장비의 events/preservation/commands/status만 방향별 허용. 클라이언트 ID4개 고유. 인증 파일은 신규 전용 위치에만 생성·덮어쓰기 거부, 원문 비밀번호는 인수/명령 인수/표준 출력에 노출하지 않는다.
- 로컬 인수: 실제 배포 Compose/운영 JAR·독립 시험 DB/브로커·시험 계정으로 수행한다. 인증 실패/권한 거절, REST 인증, 모의 START/상태/결과→DB 커밋→보존 확인→해제 상태, 재연결·재시작·기존 재배 데이터/원본 보존을 확인한다. 가상 결과는 SIMULATED로 구분하고 운영 서버에 모의 기록을 넣지 않는다.
- 검증: 도구 단위 테스트, 새 Compose 설정·로컬 종단 인수·기존3서비스 Docker 회귀(필요한 공통 위험만), diff/보존 확인. 기존 backend/Android 소스 무변경이면 이미 성공한 Full을 무의미하게 반복하지 않는다.
- 위험: MQTT 기동 후에도 장비가 없으면 OFFLINE/명령 미전달은 정상이며 503 소멸만으로 완료 판정하지 않는다. 재시작 시 앱 재로그인이 필요하다. DB 포트/볼륨·기존 설정을 보존하고 새 MQTT 파일만 전송한다.
- 상태 VERIFIED(승인된 로컬 범위). 구현 Phase당 자동수정5회/동일원인3회 중단, 단일 에이전트, 커밋·푸시는 별도 승인 전 수행하지 않는다. 실제 서버 적용/장비 인수는 NOT RUN이다.

## Phase Result — 2026-10-27
- 구현: 추가 Compose·Mosquitto 설정·독립 MQTT 비밀/ACL 생성·test-only 모의 장비·단계별 수동 안내. 기존 기본 Compose/JAR/마이그레이션/펌웨어 무변경. 새 JAR 없이 실행 인수로 MQTT/제어 기능을 켠다.
- 변경 파일: infra/personal/compose.mqtt.yml·mosquitto.conf; scripts/create-personal-mqtt.py·test_personal_mqtt.py·personal_mqtt_probe.py·verify-personal-mqtt.py; docs/PERSONAL_MQTT.md·PERSONAL_DEPLOYMENT.md·HANDOFF.md·Harness.md·이 명세·SINGLE_USER_AUTH_DEPLOYMENT.md; README·AGENTS·루트 .gitignore.
- 단위 명령: Python312 scripts/test_personal_mqtt.py. 최종5개 새 실행·실패0/오류0/건너뜀0/exit0. 기존 파일 덮어쓰기 거절·장비 ID 주입 거절·부분 실패 시 본인 파일만 정리·방향별 ACL·원문 비밀번호 stdin/두 계정 분리/해시 파일 평문 배제를 검증했다. 최초4PASS, 보강 후 sandbox Temp4errors는 권한 승인 후 코드 변경 없이5PASS. 환경 실패를 제품 실패와 구분한다.
- Docker 명령: Python312 scripts/verify-personal-mqtt.py. 새 실행9개 인수 그룹·실패0/exit0. 결과 .tools/deployment/ppossak-mqtt-check-353e1d1da9/result.json. 기본3+broker4서비스 병합/포트미공개, 실제 인증·권한, 최초/브로커 재시작/백엔드 재시작의 모의 장비 종단3회, 180원본·재배 보존·토큰 재로그인을 확인했다.
- 정상3회 모두 REST 로그인/START 접수→실제 MQTT5 상태/probe/window/START→60원본 SIMULATED 발행→보존 확인 수신 시 별도 DB 연결로 커밋된60원본 조회→해제 상태/REST 조회. 기존 소스의 커밋 후 확인 발행 경로를 유지하며, 새 시험은 수신 관측 시점의 DB 가시성과 해제 처리를 확인한다. 가상 결과의 실제 추세 제외·원본 수·예약 재배 연결·같은 키 재시도도 검증했다. 운영 서버에 가상 기록을 넣지 않았다.
- 부정: anonymous/잘못된 비밀번호 거절, device의 commands 쓰기/서버의 status 쓰기/타장비 events 쓰기 거절, 서버의 preservation 읽기 차단. 올바른 각 역할의 읽기/쓰기는 정상 종단으로 검증했다.
- 파일 권한 추가 인수1개: 새 Docker Linux 볼륨에서 root:1883/640의 passwd/ACL에 대해 UID1883 읽기 허용·UID10001 거절. permissions.json에 success=true/cleanupExitCode=0. Windows 바인드의 권한 동작만으로 Linux를 검증했다고 주장하지 않는다.
- 정리: 각 임시 프로젝트의 DB/MQTT 볼륨·컨테이너와 권한 시험 볼륨만 제거, exit0. 사용자 인증 원본·운영/다른 프로젝트·실제 서버/장비 접근0. Docker 엔진은 유지했다.
- 회귀: 실제 운영 JAR·현재 개인 배포 기본 Compose를 그대로 사용한 추가 구성 인수로 로그인/재배·기존 저장 경로를 검증했다. backend/Android 소스 변화가 없어 이전 Full216/JVM15/에뮬레이터9 결과를 반복하지 않았다. 메시지/마이그레이션/펌웨어 변경0, 신규 Java/Kotlin 파일0. backend 자동 정적/아키텍처 검사는 MISSING.
- 이미지 eclipse-mosquitto:2.0.22@sha256:09b62ff32c2e7b64d4b431b9a89236073289b68db273de2b089edeb9b0d92b5c. 테스트는 기존 JAR에서 Paho1.2.5를 추출해 재사용하며 실제 bench 자격 증명은 읽지 않는다.
- 실패기반 자동수정0/5, 동일 제품 실패 원인 반복0. Git 기존 미커밋·첨부/인덱스 보존, 커밋/푸시0. 커밋 초안: feat: prepare authenticated personal MQTT deployment without hardware.
- 다음: 사용자가 안내대로5개 신규 파일만 전송·권한 설정·추가 Compose 적용, 앱 재로그인/기존 데이터 조회와 컨테이너 상태 확인. 물리적 ESP32/LAN/TLS/Wi-Fi/개인 서버용 펌웨어 설정·업로드·실측은 보류/NOT RUN.
