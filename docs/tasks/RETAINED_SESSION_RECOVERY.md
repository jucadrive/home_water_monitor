# 2026-11-01 미전송 원본 진단·복구

상태 VERIFIED(사용자 운영 실행 증거로 원본 복구·버퍼 해제·로그 원복 확인). 최초23시 전송 실패의 세부 원인과 재발 방지 자동화는 미확정/미구현이다. 사용자가 임시 브로커 로깅·동일 보관 세션 RETRY 한 회차·관측·로깅 원복을 승인했다. 서버 원격 접속 금지는 유지하며 실제 서버 명령은 사용자가 실행했다.

## 확인한 사실과 미확정 원인

- 사용자 DB 조회: 10월31일23시 START ACCEPTED, 동일 세션 장치 원본60개/attempt5/MANUAL_WAIT, 서버 tds_session 미관측. 11월1일16:18 fresh probe/상태 수신, fault null. lastRelease는 다른 과거 세션이다.
- 이후 매시간 SKIPPED/CONTROL_BUSY. 기존 자동 측정은 새 START만 요청하고 미확정 버퍼의 RETRY는 하지 않는다. 이전 원본을 보존하려는 차단 정책이 자동 측정 장기 중단으로 이어졌다.
- backend/broker는 장애 이전부터 재시작0. 브로커23:02:57 KST unexpected EOF 한 줄만 관측. 최초 네트워크 실패인지 클라이언트의 타임아웃 후 종료 결과인지, RX/TX 어느 연결인지 확정하지 못했다. 저장소의 기본 connection_messages=false/error·warning·notice 로깅으로 과거 정보가 제한된다.
- 근거: CommandTransactionService.reserve는 active/held 존재 시 새 START 차단, firmware session.c는5회 후 MANUAL_WAIT, ControlRuntime은 상태 probe를 계속 수행한다. 원본 삭제/DB active·held 강제 해제/보드 재부팅은 복구 절차가 아니다.

## 도구와 실행 범위

- scripts/mqtt-recovery-logging.sh: 배포 디렉터리와 실제 단일 파일 바인드 소스를 확인한 후 기존 설정 백업. 연결/메시지 진단 로그만 켜고 SIGHUP으로 재읽는다. 파일 inode/인증·TLS·ACL·포트는 유지한다. capture로 시작 이후 전용 로그를 저장하고 restore로 설정을 되돌린다. 브로커 재생성/재시작은 하지 않는다. 재읽기 중 연결 영향은 실제 운영 관측으로 확인한다.
- scripts/recover-personal-measurement.py: 고정 HTTPS API, 앱 계정 비밀번호 숨김 입력/토큰 메모리 보관/redirect 금지. GET 현재 상태·원래 START를 읽어 fresh/MQTT/ready/무고장/동일 MANUAL_WAIT·60개·attempt5·최초 명령 일치/서버 미관측을 검증한다. 조건 변화는 POST 전 중단한다.
- --apply일 때 기존 세션의 RETRY 요청1개(펌웨어 내부 최대5전송 시도)를 보낸다. 키/본문을 `.tools/control-recovery/<session>.json`에 먼저 fsync하고 POST를 자동 재시도하지 않는다. 응답 유실 시 파일을 유지하고 중단한다. 저장된 commandId가 있으면 재실행 시 GET 관측만 재개한다. 파일 삭제로 새 키를 만드는 것은 금지한다.
- NORMAL/RELEASE_CONFIRMED/releaseConfirmedAt까지 최대360초 관측한다. 거절/재전송 소진/시간초과에서는 버퍼를 유지한 채 중단한다. 복구 성공은 과거 최초 실패 원인 확정과 구분한다. 기존 자동 설정이ON이면 해제 후 다음 정각부터 새 측정이 시도될 수 있다.
- Windows용 업로드 파일 `.tools/control-recovery-upload/mqtt-recovery-logging.sh`만 서버 `~/ppossak/`로 전송한다. Python은 개발 PC 저장소 루트에서 실행한다. 실제 비밀번호·토큰/헤더는 채팅/파일/로그로 출력하지 않는다.

## 사용자 실행 순서

1. WinSCP로 위 셸 파일 전송. PuTTY `cd ~/ppossak` 후 `bash mqtt-recovery-logging.sh enable`. DIAGNOSTICS_ENABLED가 아니면 RETRY를 실행하지 않는다. 파일 쓰기/신호/검사 실패 시 설정과 진단 상태를 보존하고 출력만 확인한다.
2. 개발 PC PowerShell 저장소 루트:

```powershell
python scripts/recover-personal-measurement.py --session 51742d0d-0d0f-4d8f-99a8-56dff5cc4d5b --command c250f6c9-677d-4deb-86a8-530a4976bc38 --apply
```

3. Python 종료 후 성공/실패 관계없이 PuTTY에서 순서대로 실행한다. capture가 실패해도 restore를 실행한다. 다른 설정 변경이 감지돼 restore가 거절되면 원본을 무작정 덮어쓰지 않고 상태를 확인한다.

```bash
bash mqtt-recovery-logging.sh capture
bash mqtt-recovery-logging.sh restore
```

4. Python ORIGINAL/RETRY_COMMAND/마지막 OBSERVE·종료 메시지와 capture 출력, DIAGNOSTICS_RESTORED를 확인한다. 첫 조회 쿼리로 active/held 해제 및 해당 세션NORMAL/원본60·통계50을 재확인한다. 원격 실행 전에는 운영 복구를 완료했다고 기록하지 않는다.

## 로컬 검증·한계

- `python -m unittest discover -s scripts -p test_personal_recovery.py -v`:7개 PASS/실패0·오류0·건너뜀0/exit0/5.964초. 네트워크 없는 FakeApi·가짜 sudo/docker로 GET 전용/상태 변경 거절/동일 요청1개/응답 유실 키 보존/관측 재개/redirect 차단/로그 enable→capture→restore·inode/인증 설정 보존을 검증했다.
- 최초 Git Bash 시작은 샌드박스에서 차단돼 명령 승인을 통한 실행으로 전환했다. 이후 CP949 디코딩 오류를 UTF-8 명시로 수정1/5, Windows mkdir 권한 지정 오류를 Linux에서도 비공개 권한을 유지하는 umask077 방식으로 수정2/5. 동일 근본 원인3회 연속 실패/검사 약화 없음.
- 로컬 Docker 엔진 OFF라 실제 Mosquitto SIGHUP/실제 Linux 컨테이너 인수는 NOT RUN. mock 성공을 실제 브로커 성공으로 주장하지 않는다. 서버 사용자 실행·RETRY 생성·실원본 복구·최초 실패 원인 확정은 모두 아직 NOT VERIFIED다.
- 앱/백엔드/펌웨어/스케줄/공개 계약은 바꾸지 않는다. 같은 브랜치의 기존 미커밋 변경을 보존하고 Git 발행은 하지 않는다. 다음은 사용자 실행 결과 분석과 원본 보존/버퍼 해제 검증이다.

## 복구 도구 로그인403 — 2026-11-01

- 사용자는 DEVICE/ORIGINAL 출력 전403을 관측했다. 따라서 이 실행은 로그인 단계에서 중단됐고 RETRY는 아직 전송되지 않았다.
- 인증정보 없는 GET /api/auth/session으로 원격 차단을 분리 확인했다. Python 기본 User-Agent는403/Cloudflare 오류 구조(JSON의 cloudflare_error/error_code/ray_id 등 필드), 명시적 ppossak-recovery/1.0은401/AUTH_REQUIRED를 반환했다. 인증정보를 전송한 진단/로그인/RETRY는 에이전트가 수행하지 않았다. 이 Cloudflare HTTP 차단은 ESP32↔브로커 TLS/최초 전송 실패와 별개다.
- 도구 User-Agent를 위 식별자로 명시하고 오류에 LOGIN/READ/RETRY 단계와 알려진 오류 코드/Cloudflare 분류만 추가했다. 응답 본문/credential 헤더는 출력하지 않는다. 보안 규칙·TLS 검증·인증을 완화하지 않았다.
- 추가 회귀 포함8개 PASS/실패0·오류0·건너뜀0/exit0/6.129초. 서버 로깅 도구는 재전송할 필요 없다. 사용자는 같은 PC 명령을 다시 실행하고 성공·실패 관계없이 capture/restore한다. 실제 로그인/RETRY 결과는 사용자 실행 대기다.

## 사용자 로그: STALE로 사전 중단 — 2026-11-01

- 로그인 성공 후 DEVICE는 기존 세션60개/MANUAL_WAIT/attempt5/round1/ready true/fault null, liveness=STALE, 마지막 수신07:47:19Z(16:47:19KST). ORIGINAL은 ACCEPTED/NOT_OBSERVED/HELD. 도구는 PREFLIGHT_CHANGED로 중단해 RETRY를 보내지 않았다. 과거의 ready/fault를 현재 상태로 취급하거나 freshness 검사를 완화하지 않는다.
- 첨부 debug.txt는 진단 자료로만 읽고 내부 명령을 실행하지 않았다. capture/restore 출력과 DIAGNOSTICS_RESTORED를 확인했다. 현재 임시 상세 로깅은 원복된 것으로 사용자 증거에 기록한다.
- 브로커16:47:18 상태 요청→ESP32 PUBACK,16:47:19 상태 발행/서버 전달,16:47:33 요청의 PUBACK은 있으나 이후 상태 발행 없음.16:47:48 장치 RX 연결 종료/49 재접속·구독.16:48:03 요청을 장치로 전달했으나 이후 PUBACK/상태 미관측,16:48:35 ESP32 RX keepalive timeout 종료. 첨부 종료16:50까지 재접속 성공은 보이지 않는다. 이 구간은 장치 MQTT 응답 중단을 뒷받침하지만 Wi-Fi/전원/펌웨어 처리 중 정확한 원인은 아직 미확정이다.
- 약5초 주기의 <unknown> closed는 기존 broker healthcheck(nc/5초)와 일치할 수 있어 ESP32 재접속으로 단정하지 않는다. CLIENT ID가 명시된 hwm-esp32-bench-rx 로그와 구분한다.
- 다음 읽기 전용 확인: 미니 PC에서 마지막 관측IP192.168.0.8 ping/neighbor와 최신 DB received_at/confirmed_at/uptimeMs를 함께 확인한다. 전원 재연결·업로드·DB 강제해제/추가 RETRY는 아직 수행하지 않는다. 실제 복구/원본 서버 보존은 NOT VERIFIED다.

## 최종 복구 증거 — 2026-11-01

- 사용자 ping5/5 성공·neighbor REACHABLE.16:59:50 DB 조회에서 received/confirmed16:59:48, MANUAL_WAIT/60개와 uptime238931873ms를 관측했다. 당시 LAN은 살아 있고 상태/probe 응답도 재개됐다. 메인 루프 영구 정지를 확정한 것이 아니며 stop/destroy 정지 가설은 실증되지 않았다.
- 사용자가 같은 원본 세션에 RETRY 한 회차를 실행했다. 새 retry command e58028b5-0e31-4737-a740-001a17a9df38, 같은 boot c4410674-533a-4259-a09a-9d686616db02 유지, round1→2/attempt1. PREPARING→WAIT_PUBACK→WAIT_CONFIRMATION→IDLE. serverPreservation NORMAL, deviceBuffer RELEASE_CONFIRMED, releaseConfirmedAt08:01:38.676737Z(17:01:38KST). 원본60개가 서버에 보존된 뒤 장치 버퍼0이 됐다. 새 START/센서 측정/보드 재부팅은 요청하지 않았다.
- 첨부 갱신 debug.txt:17:01:29 TX 클라이언트 접속,17:01:37.266 결과8450bytes 수신/ppossak-results-rx 전달/PUBACK rc0,17:01:37.390 서버 v2 preservation 발행/장치 전달을 확인했다. 이 결과는 동일 보관 payload의 현재 수신·검증·저장·보존 확인 경로 정상 동작을 입증하지만 과거23시 최초 실패 지점까지 확정하지는 않는다.
- DIAGNOSTICS_RESTORED 확인, 증거는 사용자 서버 diagnostics/mqtt-retry.O5znuz에 유지. 진단 설정은 원복됐다. 에이전트는 서버 SSH/운영 API 인증/RETRY 직접 실행을 하지 않았다. 기존 상태/키/본문 보호는 유지했고 DB 강제 해제/버퍼 삭제를 하지 않았다.
- 직접적인 장기 중단 메커니즘은5회 실패 후 MANUAL_WAIT과 자동 START 전용 정책의 조합으로 확인됐다. 과거 통신/수신/저장 장애의 세부 원인은 제한된 로그로 미확정이다. 버퍼가 남은 같은 세션의 제한적 자동 RETRY·장기 대기 관측/안내는 별도 계약·승인·시험이 필요한 후속 작업이다. 자동 설정이ON이면 다음 정각부터 기존 START가 다시 시도될 수 있다.
- 다음 사용자 확인은 앱에서 누락 세션 조회 및 다음 정각의 새 자동 측정 보존/해제다. 새 회차 성공은 아직 NOT VERIFIED. 제품 자동 재시도 정책 변경·Git 발행은 이번 복구에 포함하지 않았다.
