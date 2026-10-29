# 매시간 서버 자동 측정

기본 OFF다. 아래는 사용자가 미니 PC에 직접 적용하는 절차이며 에이전트는 SSH로 접속하지 않는다. 기존 앱 수동 측정과 ESP32 펌웨어는 그대로 사용한다. 구현·검증 결과는 [작업 기록](tasks/HOURLY_AUTOMATIC_MEASUREMENT.md)에서 확인한다.

## 동작

- 켜진 서버가 매시간 정각(한국 시각도 매 정각)에 지정 장비/재배로 START를 요청한다. 첫 실행은 서버가 시작한 뒤의 다음 정각이다. 앱을 켜둘 필요가 없다.
- 회당 기존1초 간격60원본·초기10원본 안정화 제외·실측 수온 보정·서버 보존/장비 버퍼 해제를 그대로 사용한다. 하루 최대24회·원본1440개다. 수동 측정은 별도로 추가할 수 있다.
- 수동/자동 중 먼저 접수된 측정이 장비를 사용한다. 측정 중·미확정 버퍼·종료 재배·오프라인·센서 오류는 기존 START 검증/준비 probe로 처리하며 강행하지 않는다. 다음 자동 회차를 위해 미확정 원본을 지우지 않는다.
- 놓친 회차 보충·즉시 재시도는 하지 않는다. 예약 콜백이 정각에서60초 이상 지연돼도 실행하지 않는다. 같은 장비/정각 회차는 DB 유일 키로 한 번만 확보한다. 프로세스가 멈춘 회차는 다음 정각에 기록만 정리하며 다시 보내지 않는다.
- 매시간 **요청**하는 기능이며 네트워크/장치/DB 상태와 무관하게24회의 정상 보존을 보장하는 기능은 아니다. 서버가 꺼지면 자동 측정도 중단된다. 수동 측정 직후라도 다음 정각에 장비가 사용 가능하면 자동 측정한다.

## 1. 기존 서버와 파일 준비

V9까지 적용한 개인 서버 기준이다. Windows `.tools/hourly-automatic/server-upload/`에 준비된 `app.automatic.jar`, `compose.automatic-measurement.yml`, `automatic-measurement.env`만 기존 `~/ppossak/`에 병합한다. 기존 compose.yml·DB 포트·인증/브로커 비밀번호·인증서·볼륨은 바꾸지 않는다. JAR가 아직 없다면 검증 결과를 먼저 확인하고 backend JDK25 build 성공 후 실행 JAR를 사용한다.

현재 로컬 검증261개 PASS 결과의 JAR SHA256은 `ED580E6CB0F4369743ACB1E0A542110F35DA5FC7B51D0398A394D8CBFC9937AB`다. 서버에서 `sha256sum app.automatic.jar`로 전송 파일을 대조한다. 이후 새 코드로 재빌드했다면 해당 산출물의 새 검증 결과/해시를 사용한다.

env 파일은 기본 OFF이며 비밀정보 없이 다음 키만 갖는다. WinSCP 편집기로 장비와 활성 재배 UUID를 직접 지정한다. 아직 ON으로 바꾸지 않는다.

```dotenv
PPOSSAK_AUTO_MEASUREMENT_ENABLED=false
PPOSSAK_AUTO_MEASUREMENT_DEVICE_ID=esp32-bench
PPOSSAK_AUTO_MEASUREMENT_CULTIVATION_ID=
```

DBeaver 읽기 전용 쿼리로 대상을 확인한다. 여러 재배가 있으면 센서가 담긴 용기의 재배를 직접 선택한다. 종료한 재배를 다른 활성 재배로 자동 전환하지 않는다.

```sql
SELECT cultivation_id, plant_name, name, started_at
FROM cultivation WHERE ended_at IS NULL ORDER BY created_at DESC;
```

## 2. 백업·OFF 상태 배포

PuTTY의 같은 셸에서 다음 함수를 사용한다. 추가 env 파일이 없어도 기존3개 파일 명령은 유지할 수 있지만, 자동 기능을 켠 후에는 반드시 아래4개 Compose/3개 env 파일을 포함한다.

```bash
cd "$HOME/ppossak"
pc() {
  sudo docker compose --env-file secrets/postgres.env \
    --env-file secrets/mqtt-lan/mqtt-lan.env \
    --env-file automatic-measurement.env \
    -f compose.yml -f compose.mqtt.yml -f compose.mqtt-lan.yml \
    -f compose.automatic-measurement.yml "$@"
}
pc config --quiet
```

오류가 있으면 다음 단계로 진행하지 않는다. 정상이라면 앱 측정이 끝났고 보관 버퍼가 없는 시점에 백엔드를 멈춰 DB/실행 JAR를 백업한다. 각 명령 성공을 확인하며 실행한다.

```bash
umask 077
backup_dir="$HOME/ppossak/backup-automatic-$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -m 700 "$backup_dir"
cp -p app.jar "$backup_dir/app.jar"
cp -p automatic-measurement.env "$backup_dir/automatic-measurement.env"
pc stop backend
pc exec -T postgres sh -ec 'exec pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB"' > "$backup_dir/database.sql"
```

pg_dump 종료0 및 아래 test 성공을 확인해야 한다. 실패하면 배포를 계속하지 않는다. 백업 성공 후:

```bash
test -s "$backup_dir/database.sql"
cp app.automatic.jar app.jar
pc build backend
pc up -d --no-deps --wait --wait-timeout 180 backend
pc ps
pc logs --tail 100 backend | grep -E 'Flyway|Migrat|Started|ERROR|Exception'
```

새 V10 적용/backend healthy 및 기존 앱 로그인·측정 이력 조회를 확인한다. 이 시점은 OFF라 자동 측정하지 않는다. 인증서/계정 재생성·DB/볼륨 삭제·과거 마이그레이션 수정은 필요 없다. V10 적용 후 실패 시 DB 백업을 무작정 덮어쓰는 자동 복구는 하지 않는다.

## 3. 켜기·끄기

프로브가 대상 용기에 들어 있고 정상 실측/보존/해제가 확인된 상태에서 env의 장비와 재배 UUID를 확인한 뒤 `PPOSSAK_AUTO_MEASUREMENT_ENABLED=true`로 바꾼다. 활성화는 실제 운영 기록을 생성한다. 저장 후:

```bash
pc config --quiet
pc up -d --no-deps --wait --wait-timeout 180 backend
```

잘못된 UUID·미등록 장비·없는 재배·제어 비활성 설정은 시작 실패로 처리한다. 보안 검증을 끄거나 대상을 생략해서 우회하지 않는다. 재배를 종료하면 그 뒤 자동 요청은 SKIPPED가 된다. 새로운 재배를 시작하면 env의 UUID를 직접 갱신한다.

끄려면 env를 false로 바꾸고 같은 명령으로 재생성한다. 이미 진행 중인 측정/미확정 원본을 취소·삭제하는 기능이 아니므로 가능하면 IDLE·보관0에서 변경한다. 서버 재시작 시 진행 중 작업의 처리는 기존 제어 재조정 규칙을 따른다.

## 4. 읽기 전용 실행 확인

다음 정각 이후 DBeaver에서 실행한다. 자동·수동의 구분은 회차 기록의 명령 키/ID로 추적하며 이번 변경에서 앱 배지를 추가하지 않는다.

```sql
SELECT a.scheduled_at, a.device_id, a.cultivation_id, a.outcome,
       a.reason AS automatic_reason,
       COALESCE(a.command_id, c.command_id) AS command_id,
       c.command_state, c.reason AS command_reason, c.session_id,
       c.release_confirmed_at,
       s.preservation_result, s.preserved_sample_count, s.stats_sample_count,
       s.temperature_source
FROM tds_automatic_measurement_run a
LEFT JOIN tds_device_command c ON c.idempotency_key=a.idempotency_key
LEFT JOIN tds_session s ON s.session_id=c.session_id
ORDER BY a.scheduled_at DESC LIMIT 24;
```

- SUBMITTED: 기존 제어 경로가 요청을 생성했다. ACCEPTED/정상 저장을 뜻하지 않는다. offline/준비 실패는 기존 command_state/reason을 확인한다.
- SKIPPED: BUSY·종료 재배 등 요청 수락 전 거절. 해당 회차 재시도 없음.
- CLAIMED: 회차를 확보한 상태. 프로세스 중단 시 다음 회차까지 남을 수 있으며, 위 조인으로 이미 생성된 명령도 확인할 수 있다.
- UNKNOWN: 요청 생성 여부를 확정하지 못한 상태. 자동 재전송하지 않는다. PROCESS_INTERRUPTED는 이전 회차의 프로세스 중단 복구 표시다.

정상 인수는 ACCEPTED/NORMAL/원본60/통계50/temperature_source=DS18B20 및 release_confirmed_at 확인이다. 날짜/시간은 DB UTC 저장·DBeaver 표시 시간대에 따라 다르다. 회차 기록이 없으면 설정OFF/서버중단/허용시간초과/DB장애 등의 가능성을 로그와 함께 확인하며, 기록이 없다고 센서 측정값을0으로 해석하지 않는다.
