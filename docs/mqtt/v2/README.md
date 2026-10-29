# 온도 보정 측정 결과 v2

v1 결과·명령·상태는 변경하지 않는다. v2 results는 `home/water/v2/{deviceId}/events`, 보존 확인은 같은 prefix의 `preservation`, QoS1/non-retained/UTF-8 application/json이다. schemaVersion=2. START/장치 상태는 계속 v1 토픽이다. 센서 없이 모의 인수할 때도 인증·TLS 운영 정책은 변경하지 않는다.

session-result.schema.json과 examples가 형식 기준이다. 추가로 서버는 temperatureElapsedMs가 원본 elapsedMs보다 미래가 아니며 2000ms 이내인지 검사한다. 온도 출처/공식은 세션 동안 고정한다. 60개 NORMAL/초기10개 제외 통계, 0~59개 INCOMPLETE, 원본 불변·동일 payload 재시도·실제 커밋 후 확인은 v1과 같다. temperatureAssumedC 필드는 v2 payload에 넣지 않는다.

각 원본 temperatureC는0~50°C/소수4자리, 전압은 보정 전0~2300mV, TDS는0~1000ppm이다. 온도 시점은 세션 시작 이후 변환 완료 상대 ms다. 오류·신선도 초과는 원본을 만들지 않고 기존 유효 prefix를 확정한다. SIMULATED 온도는 실측 추세에 포함하지 않는다. 센서 실측이라고 표시하는 DS18B20 경로의 실제 인수는 센서 도착 후 별도다.

재수신은 기존4필드와 temperatureC/temperatureElapsedMs를 비교하고 원본/수신 온도 스냅샷도 진단에 보존한다. 온도 출처/공식 변경은 세션 메타데이터 충돌로 거절한다. v1/v2 토픽·payload 버전 불일치·알 수 없는 필드를 거절한다. 보존 확인은 요청과 같은 버전/토픽으로 송신한다.

## 수동 배포 순서(아직 실행하지 않음)

1. 운영 백업 후 기존 MQTT 비밀번호 파일을 유지한다. `python scripts/create-personal-mqtt.py --device-id esp32-bench --acl-output .tools/deployment/mosquitto-v2.acl`은 새 ACL만 생성하며 기존 파일이 있으면 거절한다. 실제 사용자가 커스텀 계정을 추가했다면 해당 ACL 항목을 보존하며 검토·병합해야 한다.
2. 사용자 수동으로 ACL을 먼저 적용하고 broker를 재적용한다. 새 backend가 v2도 구독하므로 ACL보다 먼저 배포하면 구독이 거절될 수 있다. 추가 권한은 같은 장치의 v2 events/preservation 두 토픽에 한정한다. v2 commands/status 권한·와일드카드·LAN 신규 포트는 추가하지 않는다.
3. 사용자가 backend를 배포하여 V9 적용/v1 회귀를 확인한다. 기존 V1~V8과 원본/통계는 편집하지 않는다. 장비보다 서버를 먼저 전환한다.
4. 앱 배포 후, 센서 준비·IDLE/보관0 확인과 별도 실물 인수 승인 뒤 온도 모드를 선택해 펌웨어를 업로드한다. v1 펌웨어는 기존 경로로 계속 사용할 수 있다.

payload 최대16384bytes 유지. 형식이 맞는다는 사실만으로 온도센서 정확도·TDS 교정·물 안전을 입증하지 않는다.
