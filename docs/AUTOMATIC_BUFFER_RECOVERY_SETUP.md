# 자동 측정의 보관 원본 복구 업데이트

기존 V11 서버에 V12 포함 백엔드와 자동 측정 overlay를 적용한다. 앱·펌웨어·인증서·브로커·Tunnel·DB 포트는 바꾸지 않는다. 에이전트는 서버에 접속하지 않으며 아래 명령은 사용자가 직접 실행한다. 검증 상태는 [작업 명세](tasks/AUTOMATIC_BUFFER_RECOVERY.md)를 먼저 확인한다.

## 동작과 제한

- 기존 자동 측정은 그대로이며 `PPOSSAK_AUTO_RECOVERY_ENABLED`는 기본 false다. 기존 자동 측정과 복구 설정을 모두 true로 해야 자동 복구한다.
- 매 정각 현재 상태가 FRESH/준비 완료이면 IDLE에서는 START, 지정 재배의 완성된 보관 원본60개/attempt5/MANUAL_WAIT에서는 같은 세션 RETRY 한 회차를 요청한다. 그 내부 전송 시도는 기존 펌웨어 최대5회다.
- 최대6분 안에 NORMAL/60개 서버 보존·동일 세션/boot의 해제·IDLE 및 서버 잠금 해제까지 확인해야 새 측정을 시작한다. 재측정은 현재 시각이며 보관 세션의 과거 시각을 바꾸지 않는다.
- 복구 요청은 장비/정각당1개다. 실패·STALE·재부팅·수동 작업 경합·다른 세션·시간초과는 원본을 유지하고 새 측정을 건너뛴다. 다음 정각에 현재 상태를 다시 판단한다. 누락 회차 보충은 없다.
- 중단된 회차를 재시작 직후 다시 보내지 않는다. 이전 회차의 복구 기록은 다음 정각에 UNKNOWN/PROCESS_INTERRUPTED로 정리할 수 있으며 실제 결과는 원본/명령/장치 상태와 함께 확인한다.
- 복구 성공 뒤에도 최초 전송 장애가 없어졌다는 의미는 아니다. 장치에 남은 원본은 RAM이므로 전원 분리 시 보존되지 않는다. 수동 RETRY와 전원 유지 정책을 그대로 따른다.

## 1. 전송 전 준비

1. 장치가 IDLE·보관0·FRESH인지 확인한다. 아직 보관 원본이 있으면 기존 수동 복구를 먼저 완료하고 NORMAL/RELEASE_CONFIRMED를 확인한다.
2. 로컬 Full 검증 성공 후 `.tools/automatic-recovery/server-upload/`의 `app.recovery.jar`, `compose.automatic-measurement.recovery.yml`, `sha256.json`을 WinSCP로 `~/ppossak/`에 전송한다. 기존 `app.jar`와 overlay에 바로 덮어쓰지 않는다.
3. `sha256sum app.recovery.jar`를 제공된 해시와 대조한다. 실환경 env·비밀번호·인증서 파일은 교체하지 않는다.

## 2. 기존 구성 확인·백업

PuTTY의 같은 셸에서 기존 실행 조합을 유지한다. 실제 배포의 파일 이름이 다르면 기존 조합을 확인하고 실행한다.

```bash
cd ~/ppossak
pc() {
  sudo docker compose --env-file secrets/postgres.env \
    --env-file secrets/mqtt-lan/mqtt-lan.env \
    --env-file automatic-measurement.env \
    -f compose.yml -f compose.mqtt.yml -f compose.mqtt-lan.yml \
    -f compose.automatic-measurement.yml "$@"
}
pc config --quiet
pc ps
```

각 명령이 성공하고 장치 보관0인지 확인한 뒤, 다음 정각과 충분히 떨어진 시간에 진행한다.

```bash
umask 077
recovery_backup="$HOME/ppossak/backup-recovery-$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -m 700 "$recovery_backup"
cp -p app.jar compose.automatic-measurement.yml automatic-measurement.env "$recovery_backup/"
pc stop backend
pc exec -T postgres sh -ec 'exec pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB"' > "$recovery_backup/database.sql"
test -s "$recovery_backup/database.sql"
```

pg_dump 종료0/백업 확인 성공 전에는 교체하지 않는다. 정각·서버 정지 중 누락된 회차는 보충하지 않는다.

## 3. 기본 OFF로 업데이트

`automatic-measurement.env`의 기존 장비·재배·자동 측정 값을 보존하고 다음 한 줄을 추가한다. 이미 키가 있다면 중복 추가하지 말고 기존 값을 false로 편집한다.

```dotenv
PPOSSAK_AUTO_RECOVERY_ENABLED=false
```

```bash
cp app.recovery.jar app.jar
cp compose.automatic-measurement.recovery.yml compose.automatic-measurement.yml
pc config --quiet
pc build backend
pc up -d --no-deps --wait --wait-timeout 180 backend
pc logs --tail 100 backend | grep -E 'Flyway|Migrat|Started|ERROR|Exception'
```

V12 적용·backend healthy·앱 로그인/기존 이력 조회를 확인한다. 실패하면 보안 검사를 끄거나 DB/볼륨을 삭제하지 말고 중단한다. V12 이후 이전 앱 JAR로 임의 되돌리거나 운영 DB 백업을 자동 복원하지 않는다.

## 4. 복구 켜기·끄기

장치·지정 재배·프로브를 확인하고 `automatic-measurement.env`에서 `PPOSSAK_AUTO_RECOVERY_ENABLED=true`로 바꾼다. 기존 `PPOSSAK_AUTO_MEASUREMENT_ENABLED=true`도 필요하다.

```bash
pc config --quiet
pc up -d --no-deps --wait --wait-timeout 180 backend
```

다음 정각부터 적용한다. 끄려면 복구 키만 false로 편집하고 같은 재생성 명령을 실행한다. 이미 발행된 RETRY/보관 원본을 취소·삭제하지 않는다. 가능하면 IDLE·보관0에서 변경한다.

## 5. 읽기 전용 운영 확인

```sql
SELECT a.scheduled_at, a.device_id, a.outcome AS start_outcome,
       a.reason AS start_reason, a.command_id AS start_command,
       r.session_id AS retained_session, r.retry_key,
       r.command_id AS recovery_command, r.outcome AS recovery_outcome,
       r.reason AS recovery_reason
FROM tds_automatic_measurement_run a
LEFT JOIN tds_automatic_recovery r USING (device_id, scheduled_at)
ORDER BY a.scheduled_at DESC LIMIT 48;
```

IDLE에서는 recovery 행 없이 START SUBMITTED가 정상이다. 버퍼 복구 시 RECOVERED와 별도 새 START 명령이 생긴다. SUBMITTED는 접수 기록이며 실측 성공을 뜻하지 않는다. 복구 STOPPED/UNKNOWN 또는 자동 SKIPPED의 reason을 확인한다.

```bash
pc logs --since 2h backend | grep -E 'Device result waiting|Automatic recovery|MQTT result|MQTT preservation|MQTT processing failed|Control dispatch failed|Hourly'
```

새 로그는 원문 없이 수신→커밋→보존 확인 발행, 장치 MANUAL_WAIT 진입, 복구 명령/결과를 구분한다. 수신 기록이 없다면 ESP32→브로커→백엔드 중 어디서 끊겼는지는 이 로그만으로 확정할 수 없다. 필요 시 기존 임시 MQTT 진단 도구로 연결/전달 기록을 함께 확보한다.

운영 최초 인수는 기존 보관 원본을 일부러 만들거나 연결을 끊어 시험하지 않는다. 먼저 다음 정각 정상 측정·원본60/통계50/수온/해제와 회차 기록을 확인한다. 자동 복구 장애 인수는 로컬 격리 시험 결과와 구분한다.
