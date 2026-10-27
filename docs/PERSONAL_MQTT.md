# 장비 없이 MQTT 서버 준비하기

> 집에서 실물 인수를 재개할 때는 [개인 장비 연결 안내](PERSONAL_DEVICE_SETUP.md)를 따른다. 아래 내부 브로커 준비 이력은 보존하며 LAN TLS를 별도 추가 Compose로 확장한다.

현재 범위는 **Docker 내부 브로커와 백엔드 연결**이다. 집의 ESP32는 아직 접속하지 않는다. 기존 앱/DB/Tunnel 인증 파일은 유지한다. 실제 미니 PC 적용은 사용자가 직접 하며 에이전트는 원격 접속하지 않는다.

## 1. 이번에 달라지는 구성

```text
앱 → HTTPS/Cloudflare Tunnel → backend → PostgreSQL
                               ↕
                            broker(Mosquitto)
                               ⋮ 실제 ESP32 연결은 집에서 후속 작업
```

- 기본 `compose.yml`에 **`compose.mqtt.yml`을 추가 병합**해 4개 서비스를 실행한다. 서버에서 직접 추가한 DB의 `127.0.0.1:15433` 매핑은 그대로 보존한다. 기본 Compose를 새 파일로 덮어쓸 필요가 없다.
- 백엔드→`tcp://broker:1883`, MQTT/제어 활성화는 추가 Compose의 실행 인수로 적용한다. 기존 JAR에 이미 구현되어 있어 이번 작업은 새 JAR/이미지 빌드를 요구하지 않는다. `personal` 프로필 자체의 기본 비활성 설정은 유지한다.
- 브로커는 호스트/LAN/WAN 포트를 열지 않는다. 공유기·Cloudflare·DB 스키마·MQTT 메시지 형식은 변경하지 않는다.
- Mosquitto 계정은 서버 `water-server`, 장비 `esp32-bench`로 분리한다. 기본 장비 ID는 기존 시험 장비와 같은 `esp32-bench`이며 앱에도 동일하게 입력한다. 이 이름이 가상 데이터/실측 여부를 판정하지는 않는다.
- 서버는 events/status 읽기·commands/preservation 쓰기, 장비는 그 반대만 허용한다. 익명·다른 장비 토픽·권한 밖 발행은 거절한다. 브로커 상태 검사는 TCP listener 확인이며 실제 인증/권한 성공은 별도 검증한다.

## 2. Windows: MQTT 비밀 파일만 새로 생성

기존 auth.env/postgres.env/tunnel.token은 **재생성하지 않는다.** 개발 PC의 저장소 루트 PowerShell에서 Docker Desktop을 실행한 후:

```powershell
docker pull eclipse-mosquitto:2.0.22
python scripts/create-personal-mqtt.py
```

새 전용 폴더 `.tools/deployment/mqtt/`에 아래 4개 파일을 만든다. 이미 있으면 덮어쓰지 않고 실패한다. 비밀번호는 임의 생성되며 화면·명령 인수에 출력하지 않는다. 해시는 고정 Mosquitto 이미지의 도구로 만들고 원문은 표준 입력으로만 전달한다.

| 파일 | 용도 | 서버에 전송 |
|---|---|---|
| mqtt.env | 백엔드 MQTT 계정·비밀번호·장비 ID·고유 클라이언트 ID4개 | 예 |
| mosquitto.passwd | 두 계정의 비밀번호 해시 | 예 |
| mosquitto.acl | 해당 장비의 방향별 토픽 권한 | 예 |
| device-mqtt.json | 집에서 ESP32를 설정할 장비 계정·비밀번호 | **아니요: 개발 PC에 비공개 보관** |

앱 로그인 비밀번호·DB 비밀번호와 MQTT 비밀번호는 서로 다르다. 파일 내용은 채팅·Git·스크린샷으로 공유하지 않는다. 생성 도구는 로그인/DB/Tunnel 파일을 읽거나 바꾸지 않는다.

## 3. Ubuntu: 백업·기존 설정 확인

PuTTY에서 기존 배포 폴더로 이동한다. 다른 위치에 배포했다면 실제 폴더를 사용한다.

```bash
cd "$HOME/ppossak"
umask 077
cp -n compose.yml compose.before-mqtt.yml
sudo docker compose --env-file secrets/postgres.env exec -T postgres pg_dump -U ppossak -d ppossak > "$HOME/ppossak-before-mqtt-$(date -u +%Y%m%dT%H%M%SZ).sql"
```

기본 DB 이름/계정을 변경했다면 백업 명령도 맞춘다. 명령 종료 코드0을 확인하고 백업 성공 후 진행한다. `compose.before-mqtt.yml`이 이미 있으면 기존 백업을 덮어쓰지 말고 다른 새 이름을 사용한다. 기존 볼륨을 삭제하거나 새 DB 비밀번호를 만들지 않는다.

이번 파일이 없는 신규 MQTT 추가 기준:

```bash
mkdir -m 700 secrets/mqtt
```

이미 존재하면 안의 파일과 기존 설치 여부를 확인한다. 임의 삭제·덮어쓰기·계정 재생성은 하지 않는다.

## 4. WinSCP: 새 파일 5개만 전송

| 개발 PC의 파일 | Ubuntu 목적지 |
|---|---|
| infra/personal/compose.mqtt.yml | ~/ppossak/compose.mqtt.yml |
| infra/personal/mosquitto.conf | ~/ppossak/mosquitto.conf |
| .tools/deployment/mqtt/mqtt.env | ~/ppossak/secrets/mqtt/mqtt.env |
| .tools/deployment/mqtt/mosquitto.passwd | ~/ppossak/secrets/mqtt/mosquitto.passwd |
| .tools/deployment/mqtt/mosquitto.acl | ~/ppossak/secrets/mqtt/mosquitto.acl |

**기존 compose.yml·app.jar·auth.env·postgres.env·tunnel.token은 업로드/교체하지 않는다.** 기본 배포는 이미 성공했으므로 이 단계에서는 새 MQTT 파일만 추가한다.

전송 후 PuTTY에서:

```bash
cd "$HOME/ppossak"
chmod 700 secrets/mqtt
chmod 600 secrets/mqtt/mqtt.env
chmod 644 compose.mqtt.yml mosquitto.conf
sudo chown root:1883 secrets/mqtt/mosquitto.passwd secrets/mqtt/mosquitto.acl
sudo chmod 640 secrets/mqtt/mosquitto.passwd secrets/mqtt/mosquitto.acl
```

브로커는 UID/GID1883으로 실행하므로 위 두 파일만 해당 그룹 읽기를 허용한다. 상위 비밀 폴더는700 유지. 기존 Tunnel의65532 그룹 설정과 혼동하지 않는다. 일반적인 rootful Docker 기준이며 rootless/user namespace remap 환경은 실제 UID 매핑을 별도 확인한다. 777로 우회하지 않는다.

## 5. PuTTY: 추가 Compose를 함께 지정해 시작

명령이 길어지므로 **현재 셸에서만** 다음 함수를 정의한다. 새 PuTTY 창을 열면 다시 정의한다. 비밀 값은 포함하지 않는다.

```bash
cd "$HOME/ppossak"
pc() {
  sudo docker compose --env-file secrets/postgres.env -f compose.yml -f compose.mqtt.yml "$@"
}
pc config --quiet
pc pull broker
pc up -d --wait --wait-timeout 180 backend
pc ps
pc logs --tail 80 broker backend
```

- backend와 의존하는 broker/postgres가 healthy여야 한다. 기존 cloudflared는 유지된다. 이 명령은 DB/Tunnel 비밀 파일을 교체하지 않는다.
- backend 설정 변경 때문에 컨테이너가 재생성되며 기존 앱 토큰은 무효화된다. 앱에서 다시 로그인한다.
- `pc config --quiet`는 비밀 설정 내용을 출력하지 않는다. quiet 없는 config·docker inspect·비밀 파일 내용은 공유하지 않는다.
- 이후 운영 `up/ps/logs/restart`는 MQTT 추가 파일까지 지정한 `pc`를 사용한다. 기본 Compose만으로 backend를 재생성하면 MQTT 기능이 다시 꺼진다.
- 서버에서 직접 추가한 DBeaver용 DB loopback 포트는 기존 compose.yml에 남아 있어야 한다. MQTT 포트를 이와 혼동해 공개하지 않는다.

## 6. 장비 없이 지금 확인할 결과

1. `pc ps`에서 기존3개와 broker를 포함한4개 컨테이너 상태 확인. 브로커는 포트 칸에1883/tcp만 보이는 것이 정상이다. 호스트1883/18883 매핑은 없어야 한다.
2. 앱에서 재로그인, 기존 재배 목록/데이터 조회 확인.
3. 앱 장비 ID가 기본 `esp32-bench`인지 확인. 장비가 없으므로 상태 UNKNOWN/미확인은 정상이며 장비 연결 성공으로 표시하면 안 된다.
4. 앱 측정 시작을 성공 기준으로 삼지 않는다. 구현상 요청이202 접수된 뒤 probe 응답이 없어 NOT_DISPATCHED/CONTROL_FAILURE로 끝날 수 있다. 이는 실측 성공이 아니다. 외부 서버에 모의 센서 데이터를 넣거나 버튼을 반복해 기록을 쌓을 필요는 없다.
5. MQTT connection/subscription failed가 반복되면 계정 파일·권한·서비스 주소부터 점검한다. 컨테이너 healthy나503 소멸만으로 인증·실측 성공을 확정하지 않는다.

에이전트의 로컬 인수는 독립 DB와 모의 장비로 명령/데이터/저장 확인/해제를 검증한다. 실제 서버에는 시험용 가짜 데이터를 발행하지 않는다. 결과는 HANDOFF와 tasks/PERSONAL_MQTT_READINESS.md를 따른다.

## 7. 중지·롤백·백업

기본 DB 볼륨 외에 `ppossak-personal_mqtt_data`가 추가된다. MQTT 세션/큐 보존을 위한 볼륨이며 DB 커밋 확인이나 ESP32의 버퍼를 대신하지 않는다. 메시지 처리와 장비의 보존 확인은 기존 계약을 따른다.

MQTT 추가만 되돌리려면(짧은 백엔드 중단·앱 재로그인 필요):

```bash
pc stop broker
sudo docker compose --env-file secrets/postgres.env -f compose.yml up -d --no-deps --wait backend
```

기본 compose.yml에는 기존 DB 포트 매핑을 유지한다. 이후 MQTT를 다시 켤 때는5장의 pc 함수를 사용한다. DB/브로커 볼륨을 삭제하거나 `down -v`를 실행하지 않는다. DB 백업/복원은 PERSONAL_DEPLOYMENT.md의 기존 절차를 유지하고, MQTT 계정 파일은 별도 비공개 보관한다.

## 8. 집에서 이어갈 작업 — 이번에는 보류

- 미니 PC의 집 LAN 주소/고정 할당, ESP32의 WPA2 2.4GHz Wi-Fi와 게스트/AP 격리 확인.
- ESP32→브로커 LAN 경로와 TLS·인증서 검증 정책 확정. 현재 내부 평문 listener를 임의로 외부에 공개하지 않는다. 앱용 HTTPS Tunnel은 MQTT 구간을 암호화하지 않는다.
- device-mqtt.json의 장비 계정·허용 장비 ID를 개인 서버용 펌웨어 설정에 반영. 기존 시험용 설정/비밀번호를 덮어쓰지 않는 별도 설정 경로를 준비한다.
- ADS1115 실측·MQTT 제어 모드로 빌드/업로드 후 실제 START→60개 원본→DB 저장→보존 확인→버퍼 해제→앱 조회를 인수한다. 현재 Wi-Fi/센서/실물 실행은 NOT RUN이다.

참고: [Mosquitto 공식 이미지](https://hub.docker.com/_/eclipse-mosquitto), [인증·ACL·보존 설정](https://mosquitto.org/man/mosquitto-conf-5.html), [비밀번호 도구](https://mosquitto.org/man/mosquitto_passwd-1.html).
