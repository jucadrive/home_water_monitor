# 수온·TDS 온도 보정 준비와 후속 실물 인수

센서 도착 전 준비와 이후 수동 적용·실물 인수 안내다. 2026-10-29 사용자가 서버/앱을 적용하고 DS18B20 펌웨어 업로드 후 정상1회 인수를 통과했다. UART60원본/버퍼 해제와 사용자 앱·SQL 결과를 확인했다. 수온 정확도·TDS 교정·장애/장기는 별도 미검증이다. 아래 적용 명령은 재현 안내이며 완료한 설치에 다시 실행할 필요는 없다. 검증 상세는 [작업 명세](tasks/TEMPERATURE_COMPENSATION.md), 계약/수동 적용 순서는 [MQTT v2](mqtt/v2/README.md)를 따른다.

## 모드 선택

`scripts/esp32.ps1`과 `verify-esp32.ps1`의 Source와 Temperature는 독립 선택이다. Temperature 기본값은 ASSUMED이며 기존 v1 동작을 유지한다.

| Source | Temperature | 의미 | 결과 계약/실측 추세 |
|---|---|---|---|
| ADS1115 | ASSUMED | 기존 전압 실측 + 25°C 가정 | v1/기존 정책 유지 |
| ADS1115 | SIMULATED | 전압 실측 + 명시적 모의 수온25°C | v2/실측 추세 제외 |
| SIMULATED | SIMULATED | 전압과 수온 모두 모의 | v2/실측 추세 제외 |
| ADS1115 | DS18B20 | 전압과 수온 실측 예정 | v2/실물 인수 전 NOT VERIFIED |

모의 모드가 DS18B20 모드의 자동 대체 경로가 되는 것은 아니다. DS18B20 읽기 실패 시 오류로 처리한다. 공식 ds18b20 0.4.0·onewire_bus1.1.2 및 해시는 firmware/esp32/dependencies.lock으로 고정하고, 기존 ESP-IDF5.5.5/ESP-MQTT 고정을 유지한다. Arduino 라이브러리나 sensor_hub는 사용하지 않는다.

```powershell
# 빌드만 수행. 센서 도착 전 Flash/업로드하지 않는다.
./scripts/verify-esp32.ps1 -Mode CredentialFree -Temperature DS18B20
./scripts/verify-esp32.ps1 -Mode Build -Personal -Temperature DS18B20

# 실제 production C 계산·세션 코드를 PC에서 실행(로컬 Docker 필요)
python scripts/test-temperature-host.py
```

CredentialFree 결과는 실행부가 최적화되므로 실제 이미지 크기의 근거가 아니다. 개인 설정 빌드의 bin은 실제 네트워크 비밀을 포함하므로 Git·공유 대상이 아니다. 빌드 후 실제 실행·UART 인수는 별도로 구분한다.

## 계산·보존 정책

- 보정 전 전압 V는 기존 ADS1115 25개 중앙값의 반올림 전 ADC count/8000 V다. `V25=V/(1+0.02*(T-25))`, `TDS=(133.42*V25³-255.86*V25²+857.39*V25)*0.5`를 사용한다. 제조사 단순 측정 예제 방식이며 공식 버전은 sen0244-voltage-atc-v1이다. 서버는 재보정하지 않고 장치가 확정한 원본과 계산 근거를 보존한다.
- DS18B20의 1/16°C 단위를 이용해 모든 지원 온도에서 정수 유리수 연산으로 계산하고 최종 TDS만0.01ppm HALF_UP한다. k=T×16이면 V25=ADC count/[10×(400+k)]이며 25°C는 기존 계산과 같다. 계산기는 1/16°C 격자 밖 온도를 거절한다. rawVoltageMv는 보정 전 전압을0.01mV로 보존한다. 독립 Decimal2080벡터로 계산과 반올림을 확인했다. 계약의 최대 소수4자리 보존은 센서 정확도 보증이 아니다.
- 보정 유효 수온0~50°C, TDS0~1000ppm. 범위 밖 값을 clamp하지 않는다. DS18B20의1/16°C 단계를 소수4자리로 보존하고 앱은 소수2자리까지 표시한다. 저장 자릿수는 정확도 보증이 아니다.
- 각 원본에 temperatureC·temperatureElapsedMs를 보존한다. 시점은 session 시작 기준 변환 완료 상대 ms다. 원본보다 미래이거나2000ms를 초과한 온도는 거절한다. 실제 변환 완료/CRC 검증 전에 scratchpad 초기값을 사용하지 않는다.
- 온도 변환은 별도 낮은 우선순위 task에서 대기한다. 기존 ADC worker의40ms 수집·컨트롤/MQTT 루프를 변환 대기로 막지 않는다. 초기 준비/센서 식별 실패는 START를 막고, 측정 중 오류는 유효 prefix만 INCOMPLETE로 확정한다. 오류는 boot 동안 유지하며 정상값으로 대체하지 않는다. 이미 확정된 결과의 RETRY는 원래 payload를 유지한다.
- v2의 temperatureAssumedC는 DB/API에서null이며 실제 수온을 이 가정 필드에 넣지 않는다. 원본 온도와 세션 temperatureSource/compensationVersion을 별도 저장한다. 요약 평균 수온은 안정화를 제외한 원본에서 조회 시 계산한다. 불완전 TDS 통계는 기존처럼 없음이며, 온도 원본 보존과 구분한다.
- v1 과거 원본/통계/교정 정보는 변경하지 않는다. 온도·온도시점 재수신 충돌도 원본을 덮어쓰지 않고 두 스냅샷을 진단에 남긴다. 저장 서비스의 실제 커밋 이후에만 같은 버전의 보존 확인을 보낸다.

## 격리된 종단 인수 재현

운영 서버·장비·비밀번호를 사용하지 않는다. 먼저 위 C host 검사를 실행해 `.tools/temperature/host`에 실제 C serializer의 모의 payload를 만든다. JDK25 backend 루트에서 매번 새 폴더로 시작한다.

```powershell
./gradlew.bat androidAcceptanceServer '-PacceptanceDirectory=../../.tools/temperature/local-new' '-PacceptanceTemperaturePayload=../../.tools/temperature/host/c-temperature-250000-prefix-60.json' --no-daemon --console=plain
```

ready.json 생성 후 저장소 루트에서 JDK25 환경으로 다음 검사를 실행한다. 이 도구는 ready의 loopback/SIMULATED/temperatureFixture 조건을 확인하고 공개 시험 계정만 사용한다. 각 케이스마다 UUID를 새로 부여하며 원본 수치·온도·시점은 C가 만든 값을 사용한다.

```powershell
python scripts/verify-temperature-wire.py --ready .tools/temperature/local-new/ready.json
```

20/25/30°C × prefix0/1/59/60 저장·조회·동일 재수신·온도 충돌·미래 온도 거절을 검사한다. 앱 종단 인수는 이 시험 데이터와 섞이지 않는 새 DB에서 시작한다. [Android 안내](ANDROID_SETUP.md)의 adb reverse 절차와 EndToEnd 명령에 `-TemperatureFixture`를 추가하면 모의 평균 수온/온도 보정 표시도 검사한다. API35의 TemperatureScreensTest는 DS18B20 표시용 fixture를 사용하므로 실제 센서 인수가 아니다.

각 실행 디렉터리의 stop 파일로 종료하고 stopped=closed와 해당 시험 컨테이너 제거를 확인한다. 자신이 만든 adb reverse만 제거한다. 시험 속성 없는 Android Build로 일반 HTTPS APK를 복원한다. 다른 컨테이너·운영 DB·보드·사용자 에뮬레이터를 종료하지 않는다.

## 센서 도착 후 별도 확인

### 기존 개인 서버에 온도 기능 적용하기

2026-10-29 실물 재개 시 준비한 Windows 폴더는 `.tools/temperature/server-upload-20261029/`다. 검증된 실행 JAR를 `app.temperature.jar`, 기본 두 MQTT 계정의 v1/v2 ACL을 `mosquitto.temperature.acl`로 준비했다. 인증/DB/MQTT 비밀번호·인증서·기존 Compose는 포함하지 않는다. WinSCP로 두 파일만 기존 `~/ppossak/`에 올린다. `sha256.json`은 로컬 대조용이다.

PuTTY에서 아래 함수를 정의한다. 기존 배포 위치/세 Compose/두 env 파일을 유지한다.

```bash
cd "$HOME/ppossak"
pc() {
  sudo docker compose --env-file secrets/postgres.env \
    --env-file secrets/mqtt-lan/mqtt-lan.env \
    -f compose.yml -f compose.mqtt.yml -f compose.mqtt-lan.yml "$@"
}
pc config --quiet
sha256sum app.temperature.jar mosquitto.temperature.acl
sudo diff -u secrets/mqtt/mosquitto.acl mosquitto.temperature.acl
```

이번 JAR SHA256: `258cdfc57d336f829c0d9781c098dc3f30f3a296f460691c9ed40e7419646405`.
ACL SHA256: `c9e791993eb1d175e10139a2df63860728bea23a83cdd1f377447c8df659cb46`.
ACL 비교에서 기존 v1 권한은 유지되고 water-server의 v2 events 읽기/preservation 쓰기, esp32-bench의 그 반대 권한 총4줄만 추가되어야 한다. `diff` 종료1은 차이가 있다는 뜻이다. 추가 계정 삭제 등 다른 차이가 있으면 다음 적용을 중단하고 기존 ACL을 병합해야 한다.

먼저 백엔드를 멈춰 쓰기를 중단한 뒤 DB/기존 파일을 백업한다. DB 백업이 실패하면 진행하지 않는다. 백업 경로는 같은 셸에서 이후에도 사용한다.

```bash
umask 077
backup_dir="$HOME/ppossak/backup-temperature-$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -m 700 "$backup_dir"
pc stop backend
pc exec -T postgres sh -ec 'exec pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB"' > "$backup_dir/database.sql"
```

위 pg_dump 명령 종료 코드0과 백업 파일이 비어 있지 않은지 확인한 뒤:

```bash
test -s "$backup_dir/database.sql"
cp -p app.jar "$backup_dir/app.jar"
sudo cp -p secrets/mqtt/mosquitto.acl "$backup_dir/mosquitto.acl"
```

세 명령이 모두 성공했을 때만 아래를 순서대로 실행한다. 기존 볼륨/비밀번호/포트/Compose는 교체하지 않는다. broker는 bind mount가 새 ACL 파일을 확실히 읽도록 재생성한다. 짧은 MQTT 연결 중단이 발생한다.

```bash
sudo install -o root -g 1883 -m 640 mosquitto.temperature.acl secrets/mqtt/mosquitto.acl
pc up -d --no-deps --force-recreate --wait --wait-timeout 90 broker
cp app.temperature.jar app.jar
pc build backend
pc up -d --no-deps --wait --wait-timeout 180 backend
pc ps
pc logs --tail 100 backend | grep -E 'Flyway|Migrat|Started|ERROR|Exception'
```

각 명령 실패 시 후속 명령을 계속하지 않는다. backend 시작 시 V9가 기존 DB에 추가 적용된다. broker/backend healthy와 Flyway9 적용 성공을 확인하고, 기존 앱에서 로그인·과거 식물/측정 조회가 되는지 확인한다. 실패 시 DB를 삭제/초기화하거나 인증·TLS를 끄지 않는다. V9 적용 후 DB를 무작정 이전 백업으로 덮어쓰는 자동 복구는 제공하지 않는다.

서버 적용 후 `.tools/temperature/ppossak-temperature-debug.apk`를 기존 앱의 업데이트로 설치한다. 기존 앱 삭제는 필요하지 않다. 그 뒤에만 IDLE/보관0 재확인→DS18B20 펌웨어 업로드→아래 실물 인수를 진행한다.

1. 서버/ACL/app 적용과 백업은 사용자가 직접 수행한다. ACL → backend/V9 → 앱 → 장비 순서로 적용하며 v1 회귀를 확인한다. 자세한 명령·보존 조건은 MQTT v2 안내를 따른다.
2. 보드 IDLE·미확정 보관0을 확인한 뒤 전원을 분리한다. 현재 빵판 사진과 보드 라벨로 배선을 확정한다. GPIO27은 후보이며 보드의27/D27 표기와 실제 빈 핀을 확인한다.
3. VDD는 기존3.3V 분배점, GND는 공통 GND, DQ는GPIO27에 연결한다. VDD/DQ 풀업은3.3V를 사용한다. 해당 제품의4.7kΩ 내장 및 핀 순서는 실물을 확인하고 추가 저항을 중복 연결하지 않는다. 보드/단자는 건조하게 유지하고 프로브만 물에 담근다.
4. 별도 실물 재개 승인 후 DS18B20 모드를 업로드한다. 단일 센서 식별·온도 변환/CRC·초기 준비 및 ADC/Wi-Fi 동시 동작을 확인한다. 실제 변환/초기값·배선·장기 안정성은 현재 NOT VERIFIED다.
5. 같은 물에서 기준 온도계와 안정화/반복 비교한다. 기준 온도계의 정확도까지 고려해 인수 허용 오차를 정한다. 정상60/통계50/실측 온도 원본/보정 버전·DB일치·버퍼 해제를 확인한다.
6. 센서 분리·읽기 오류와 유효 prefix 보존/RETRY를 확인한다. 미확정 버퍼를 재부팅/재업로드로 버리지 않는다. 복구·재부팅은 결과 보존·해제 후 수행한다.

온도 보정은 TDS 교정과 별개다. 실제 센서·표준액·독립 미터 비교 전에는 수온/TDS 절대 정확도나 물·식물 안전을 확정하지 않는다.

근거: [제조사 보정 예제](https://wiki.dfrobot.com/sen0244/docs/20305), [DS18B20 데이터시트](https://www.analog.com/media/en/technical-documentation/data-sheets/DS18B20.pdf), [공식 드라이버](https://components.espressif.com/components/espressif/ds18b20).
