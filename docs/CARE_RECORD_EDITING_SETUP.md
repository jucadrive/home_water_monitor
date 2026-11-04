# 재배 정보·돌봄 기록 수정 업데이트

[계약·검증 상태](tasks/CULTIVATION_EDITING_AND_CARE_HISTORY.md)를 먼저 확인한다. 아래는 기존 V10·MQTT LAN TLS·매시간 자동 측정 배포를 유지하는 사용자 수동 업데이트다. 에이전트는 서버에 접속하지 않는다. 새 센서/펌웨어/Tunnel/인증 파일/Compose 변경은 없다.

## 1. 파일 전송

- Windows `.tools/care-editing/server-upload/app.editing.jar`를 WinSCP로 기존 `~/ppossak/app.editing.jar`에 전송한다. 기존 app.jar를 바로 덮어쓰지 않는다.
- 로컬 검증 완료 후 제공한 SHA256과 서버 `sha256sum app.editing.jar`를 대조한다. 비밀 파일·DB 볼륨·자동 측정 env는 교체하지 않는다.
- 서버를 먼저 업데이트하고 새 `.tools/care-editing/ppossak-editing-debug.apk`를 폰에 업데이트 설치한다. 구서버에는 새 수정 API가 없다. 앱 삭제/데이터 초기화는 필요 없다.

2026-10-30 로컬 검증 JAR SHA256: `E8665BBC83410C2BB2B2FCC84C0041450C9D973AB74E994A56F7B74AFFA36936`. APK SHA256: `6C7498124F6E275C7CEE1C133EBBA025A5E063BD6E0AF0B59A1627878AABE62E`. 이후 새 코드로 재빌드하면 새 검증 결과/해시를 사용한다.

## 2. 기존 구성으로 백업·백엔드 교체

PuTTY에서 기존 배포 디렉터리로 이동하고 기존 자동 측정 적용 때와 동일한 Compose 조합을 사용한다. 실제 파일 이름/구성이 다르면 임의로 overlay를 빼지 말고 기존 실행 명령을 유지한다.

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

설정 검사가 종료0이고 측정이 끝난 IDLE·보관 원본0인 시점에 진행한다. 가능하면 다음 정각과 충분히 떨어진 시간에 작업한다. 재시작 중 누락된 자동 측정 회차는 보충하지 않는다. 아래 명령은 각 단계 성공 후 다음 단계로 진행한다.

```bash
umask 077
backup_dir="$HOME/ppossak/backup-editing-$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -m 700 "$backup_dir"
cp -p app.jar "$backup_dir/app.jar"
cp -p automatic-measurement.env "$backup_dir/automatic-measurement.env"
pc stop backend
pc exec -T postgres sh -ec 'exec pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB"' > "$backup_dir/database.sql"
```

pg_dump 종료0 및 `test -s` 성공을 확인한다. 백업 실패 시 교체하지 않는다.

```bash
test -s "$backup_dir/database.sql"
cp app.editing.jar app.jar
pc build backend
pc up -d --no-deps --wait --wait-timeout 180 backend
pc ps
pc logs --tail 100 backend
```

V11 적용·backend healthy를 확인한다. 기존 자동 측정 env를 보존했으므로 재배 UUID와 ON/OFF 설정도 유지된다. 이름을 바꿔도 UUID는 변하지 않는다. 실패하면 로그를 확인하고 작업을 멈춘다. 적용된 V11을 수정/삭제하거나 DB 백업을 실행 중 DB 위에 무작정 복원하지 않는다.

## 3. 앱·DB 확인

1. 기존 앱 로그인·재배/측정 조회가 유지되는지 확인한다.
2. 새 APK 설치 후 식물 상세의 재배 정보 편집에서 식물명·재배 별명·메모를 수정한다. 홈에 돌아갔다 재진입해 같은 재배 ID에 반영됐는지 확인한다.
3. 돌봄 항목 상세의 이 기록 수정에서 필요한 잘못된 내용만 정정한다. 테스트용 임의 내용을 운영 기록에 추가할 필요는 없다.
4. 타임라인은 최근 돌봄 최대10개와 재배 이력만 표시한다. 측정 기록은 전체 측정 이력에서 그대로 조회한다. 전체 이력은50개 단위로 이전/다음 페이지를 이동한다.
5. 충돌 시 최신 내용 확인으로 서버 내용을 읽고 내 입력으로 수정 계속을 선택한 뒤 저장한다. 통신 결과가 불확실하면 기존 같은 요청 재시도를 사용한다.

아래는 DBeaver 읽기 전용 확인 쿼리다. memo/응답 스냅샷에는 개인 기록이 있으므로 결과 전체를 공개하지 않는다.

```sql
SELECT cultivation_id, plant_name, name, version, updated_at
FROM cultivation ORDER BY updated_at DESC LIMIT 10;

SELECT maintenance_id, cultivation_id, type, occurred_at, amount_ml, version, updated_at
FROM water_maintenance ORDER BY updated_at DESC LIMIT 10;

SELECT request_key, request_path, created_at,
       before_body IS NOT NULL AS has_actual_change
FROM care_edit_receipt ORDER BY created_at DESC LIMIT 10;
```

변경 없는 저장은 버전 증가/실질 변경 이력이 없고 성공 요청 영수증만 남는다. 측정 원본·통계는 정정 대상이 아니다. 수정 이력 UI/삭제 기능은 이번 범위에 없다.
