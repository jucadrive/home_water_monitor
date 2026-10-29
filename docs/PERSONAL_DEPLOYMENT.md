# 뽀싹 Docker 수동 배포

> 매시간 자동 측정 확장은 [별도 적용 안내](HOURLY_AUTOMATIC_MEASUREMENT.md)를 따른다. 기본 OFF이며 기존 Compose/비밀번호/포트를 보존하고 opt-in overlay와 V10 포함 실행 JAR를 사용자 수동으로 적용한다.

> 아래는 기본3개 서비스 배포다. 이미 배포한 서버에 장비 없는 MQTT 준비를 추가하려면 [MQTT 추가 안내](PERSONAL_MQTT.md)를 따른다. 기존 compose.yml/DB 포트/로그인 비밀을 덮어쓰지 않고 별도 추가 Compose를 병합한다.

이 문서는 **Windows 개발 PC에서 준비 → WinSCP로 전송 → Ubuntu에서 실행 → 앱 연결** 순서다. 미니 PC에는 Docker Engine과 Compose만 있으면 된다. JDK·Python·cloudflared를 Ubuntu에 따로 설치하지 않는다. 에이전트는 서버에 접속하지 않는다.

## 1. 구성과 시작 전 확인

```text
앱 → HTTPS → Cloudflare → cloudflared → backend:8080 → postgres:5432
                         └──────── 같은 Docker Compose 네트워크 ────────┘
```

- 백엔드·DB는 호스트 포트를 공개하지 않는다. 공유기에 새 포트 포워딩을 만들지 않는다.
- HTTPS 인증서는 Cloudflare가 처리한다. Tunnel의 Cloudflare 구간은 암호화되고, 컨테이너 사이의 HTTP는 같은 호스트의 Docker 네트워크 내부다.
- 백엔드 이미지는 Java 25 JRE를 포함한다. Dockerfile의 별도 빌드 단계가 상태 확인용 Java 클래스를 컴파일하므로 그 단계에만 JDK 이미지를 사용한다. 앱 JAR 자체는 개발 PC에서 검증한 것을 사용한다.
- Compose **2.30 이상**이 필요하다. `env_file.format: raw`로 BCrypt 해시의 `$`를 보존한다. `docker-compose` 구버전 대신 `docker compose`를 사용한다.
- 여기서는 신규 설치를 설명한다. 이미 뽀싹 DB/볼륨/서비스를 만들었다면 새로 초기화하지 말고 먼저 이전 데이터와 설치 방식을 확인한다. 이전 systemd 방식과 동시에 실행하지 않는다.
- `ppossak-personal`은 전용 Compose 프로젝트 이름이다. 기존 다른 프로젝트는 중지·삭제하지 않는다. 기존 뽀싹 이름과 충돌하면 실행을 멈추고 확인한다.

**PuTTY에서 아래 확인 명령만 먼저 실행한다.**

```bash
uname -m
docker --version
docker compose version
docker compose ls
docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
docker volume ls
```

N150은 보통 `x86_64`다. 실제 결과를 확인한다. Docker 명령 권한이 없으면 이 문서의 Docker 명령에만 `sudo`를 붙인다. 기존 Docker 권한 정책은 임의로 바꾸지 않는다.

## 2. Windows: 이미 만든 인증 파일을 유지한다

사용자가 이미 `scripts/create-personal-secrets.py`를 실행했다면 **재생성하지 않는다.** 저장소의 다음 폴더에 파일이 있는지 파일명만 확인한다.

```text
.tools/deployment/secrets/
  auth.env       # 앱 로그인 계정·BCrypt 해시
  postgres.env   # 신규 DB 이름·사용자·임의 비밀번호
  tunnel.token   # 뽀싹 전용 Cloudflare Tunnel 토큰
```

`owner`로 생성했다면 앱 아이디는 `owner`, 앱 비밀번호는 그때 직접 입력한 값이다. SSH 비밀번호와 별개다. 비밀번호·파일 내용은 채팅에 보내지 않는다. `postgres.env`의 예전 `PPOSSAK_DB_PORT=15433`은 새 구성에서 사용하지 않아도 되며 파일을 편집할 필요는 없다.

`tunnel.token`이 없다면 새로 생성한 DB 파일로 기존 파일을 바꾸지 않는다. Cloudflare에서 뽀싹 전용 Tunnel의 토큰만 받아 별도 비공개 `tunnel.token` 파일로 저장한다. 설치 명령 전체가 아니라 토큰 값만 한 줄로 넣는다.

## 3. Windows: 업로드 폴더 만들기

**저장소 루트의 PowerShell**에서 실행한다. 이미 동일한 폴더가 있으면 덮어쓰지 않고 중단한다. 이 절차는 생성해 둔 인증 원본을 수정하지 않고 복사한다.

```powershell
$bundle = Join-Path (Get-Location) '.tools/deployment/ppossak-upload'
if (Test-Path -LiteralPath $bundle) { throw '업로드 폴더가 이미 있습니다. 이전 폴더를 확인하세요.' }
New-Item -ItemType Directory -Path $bundle | Out-Null
New-Item -ItemType Directory -Path (Join-Path $bundle 'secrets') | Out-Null
Copy-Item -LiteralPath 'infra/personal/compose.yml','infra/personal/Dockerfile','infra/personal/Healthcheck.java','infra/personal/.dockerignore' -Destination $bundle
Copy-Item -LiteralPath 'backend/home-water-monitor-backend/build/libs/home-water-monitor-backend-0.0.1-SNAPSHOT.jar' -Destination (Join-Path $bundle 'app.jar')
Copy-Item -LiteralPath '.tools/deployment/secrets/auth.env','.tools/deployment/secrets/postgres.env','.tools/deployment/secrets/tunnel.token' -Destination (Join-Path $bundle 'secrets')
Get-FileHash -Algorithm SHA256 (Join-Path $bundle 'app.jar')
```

업로드 폴더는 다음 모양이어야 한다. `.dockerignore`도 반드시 포함한다. 빌드 시 비밀 파일이 Docker 빌드 컨텍스트/이미지에 들어가지 않게 한다.

```text
ppossak-upload/
  compose.yml
  Dockerfile
  Healthcheck.java
  .dockerignore
  app.jar
  secrets/
    auth.env
    postgres.env
    tunnel.token
```

배포에는 현재 검증한 실행 JAR를 사용한다. 소스를 새로 받았다면 인증·개인 배포 구현이 포함된 이력인지 확인하고 Java25로 빌드한다. Git pull만으로 실행 JAR가 생성되지는 않는다. `plain.jar`는 실행 JAR가 아니므로 사용하지 않는다.

## 4. Ubuntu: 전용 폴더 준비 후 WinSCP로 업로드

**PuTTY**에서 실행한다. 이미 `~/ppossak`이 있으면 먼저 내용을 확인한다. 아래 mkdir는 새 폴더가 아닐 경우 실패하며, 임의로 삭제하지 않는다.

```bash
umask 077
mkdir -m 700 "$HOME/ppossak"
mkdir -m 700 "$HOME/ppossak/secrets"
```

**WinSCP**에서 기존 PuTTY와 같은 서버·SSH 계정으로 접속한다. 로컬 `ppossak-upload`의 **내용물**을 원격 `~/ppossak/` 안으로 전송한다. 업로드 폴더 자체를 한 단계 더 넣지 않는다. 숨김 파일 표시를 켜서 `.dockerignore`까지 복사한다.

업로드 후 **PuTTY**에서 권한을 설정한다.

```bash
cd "$HOME/ppossak"
chmod 700 . secrets
chmod 600 secrets/auth.env secrets/postgres.env
sudo chown root:65532 secrets/tunnel.token
sudo chmod 640 secrets/tunnel.token
sha256sum app.jar
```

SHA256이 Windows 출력과 같아야 한다. Tunnel 컨테이너는 UID/GID 65532로 실행되므로 토큰 파일만 해당 그룹의 읽기를 허용한다. 상위 `secrets` 폴더는 본인만 접근하는 700으로 유지한다. 일반적인 rootful Docker 기준이며 user namespace remap/rootless 환경이면 실제 UID 매핑에 맞춘 별도 권한 검토가 필요하다. 토큰 읽기 문제를 `chmod 777`로 우회하지 않는다.

## 5. Ubuntu: DB와 백엔드를 먼저 실행

이후 명령은 모두 `~/ppossak`에서 실행한다. `--env-file`을 빼면 DB 설정이 없어 실패하는 것이 정상이다.

```bash
cd "$HOME/ppossak"
docker compose --env-file secrets/postgres.env config --quiet
docker compose --env-file secrets/postgres.env up -d --build --wait --wait-timeout 180 backend
docker compose --env-file secrets/postgres.env ps
docker compose --env-file secrets/postgres.env exec -T backend java -cp /app Healthcheck
```

- 첫 실행은 공식 Java·PostgreSQL 이미지 다운로드와 빌드 때문에 시간이 걸릴 수 있다.
- `postgres`, `backend`가 `healthy`여야 한다. Healthcheck가 출력 없이 종료 코드 0이면 인증 전 JSON 401 경계가 확인된 것이다. 이 검사는 로그인/DB CRUD 인수를 대신하지 않는다.
- `backend`만 지정해도 의존하는 PostgreSQL이 먼저 실행된다. cloudflared는 아직 실행하지 않는다.
- DB URL은 `postgres:5432`, 백엔드 수신 주소는 컨테이너 내부 `0.0.0.0:8080`으로 Compose가 설정한다. 호스트의 Java·15433·18080 포트는 필요 없다.
- 파일 내용을 출력하는 `cat secrets/*`, 비밀이 포함되는 `docker compose config`(quiet 없는 출력)·`docker inspect`를 공유하지 않는다.

실패 시 로그는 본인 화면에서만 확인한다. 비밀번호/토큰이 있다면 공유하지 않는다.

```bash
docker compose --env-file secrets/postgres.env logs --tail 100 backend postgres
```

## 6. Cloudflare: 도메인 연결 후 Tunnel 시작

Cloudflare 관리 화면의 Tunnels에서 **토큰을 발급한 뽀싹 Tunnel**을 선택한다. UI 표기는 바뀔 수 있지만 Published application route(공개 애플리케이션 경로)를 다음처럼 설정한다.

| 항목 | 값 |
|---|---|
| Subdomain | `ppossak-api` |
| Domain | `jucadrive.com` |
| Path | 비워 둠 |
| Service type | `HTTP` |
| Service URL | `backend:8080` |

연결 대상은 **`http://backend:8080`**이다. `localhost`, `127.0.0.1`, 미니 PC 공인 IP, `https://backend:8080`으로 넣지 않는다. cloudflared에서 localhost는 Tunnel 컨테이너 자신이다.

기존 같은 DNS 이름이 있다면 소유와 현재 용도를 확인하고 덮어쓰지 않는다. 다른 프로젝트의 Tunnel·DNS·공유기 규칙을 변경하지 않는다. 앱 인증은 Spring Security가 담당한다. 별도의 브라우저 Access 로그인/JS challenge가 API를 가로채지 않게 하고, 이 API 호스트의 `/api/*`는 캐시 제외한다. 도메인 전체 Cache Everything 규칙 적용 여부도 확인한다.

이제 **PuTTY**에서:

```bash
docker compose --env-file secrets/postgres.env up -d cloudflared
docker compose --env-file secrets/postgres.env ps
docker compose --env-file secrets/postgres.env logs --tail 50 cloudflared
```

Cloudflare에서 Tunnel이 Healthy가 되고, 로그에 연결 등록이 보이는지 확인한다. outbound 연결이 차단된 환경에서는 Cloudflare의 방화벽 요구 사항을 확인한다. 공유기 인바운드 포트 개방은 필요 없다.

## 7. 개발 PC/폰: HTTPS와 로그인 확인

**Windows PowerShell**에서 실행한다. 인증서 검증을 끄는 `-k`는 쓰지 않는다.

```powershell
curl.exe -i https://ppossak-api.jucadrive.com/api/cultivations
```

정상 기대 결과는 **HTTP 401**, JSON 응답의 **`code: AUTH_REQUIRED`**다. 이는 미로그인 요청이 서버까지 도달했다는 뜻이다. HTML 로그인 페이지/Cloudflare challenge/HTML 401은 통과로 보지 않는다. 502는 백엔드 주소/기동 상태, Tunnel 연결 오류는 cloudflared 상태부터 확인한다.

인증 지원 새 APK를 폰에 설치하고 다음 값으로 연결한다.

- 서버 주소: `https://ppossak-api.jucadrive.com` (뒤에 `/api`를 붙이지 않음)
- 아이디: 생성 시 지정한 계정(기본 `owner`)
- 비밀번호: 파일 생성 때 입력한 앱 비밀번호

새 APK: `.tools/android-acceptance/auth-1/ppossak-auth-debug.apk`. 이전 무인증 APK는 업데이트가 필요하다.

재배 생성→조회→물 관리 기록→재조회→종료, 잘못된 비밀번호 거절, 로그아웃→재로그인을 확인한다. 시험 기록은 `[인수]` 등으로 표시한다. 삭제 API가 없어 기록이 남는다. MQTT/장치 제어는 보류 상태이므로 측정 시작의 503 CONTROL_UNAVAILABLE은 예상 결과이고 실제 장비 성공을 뜻하지 않는다.

토큰은 기본 8시간이며 서버 재시작 시 무효화된다. 재로그인하면 된다. 미확정 요청은 로그아웃해도 보존하며 같은 요청 재시도를 사용한다. 다른 서버로 전송하거나 새 키로 중복 등록하지 않는다.

## 8. 운영·백업·업데이트

`restart: unless-stopped`를 사용한다. Docker 데몬이 부팅 시 자동 시작되는지 `systemctl is-enabled docker`로 확인한다. 직접 stop한 컨테이너는 자동 재시작되지 않는다.

중지/다시 시작(데이터 볼륨 유지):

```bash
docker compose --env-file secrets/postgres.env stop
docker compose --env-file secrets/postgres.env up -d
```

백업(기본 DB/사용자 ppossak 기준, 이름을 변경했다면 맞춰 입력):

```bash
umask 077
docker compose --env-file secrets/postgres.env exec -T postgres pg_dump -U ppossak -d ppossak > "$HOME/ppossak-backup-$(date -u +%Y%m%dT%H%M%SZ).sql"
```

종료 코드 0과 백업 파일을 확인한다. 백업 파일을 별도 안전한 저장소에도 보관한다. 복구 시험은 **사용 중이지 않은 새 DB 이름**으로 진행한다. 아래는 예시이며 운영 DB에 덮어쓰지 않는다.

```bash
docker compose --env-file secrets/postgres.env exec -T postgres createdb -U ppossak ppossak_restore_check
docker compose --env-file secrets/postgres.env exec -T postgres psql -v ON_ERROR_STOP=1 -U ppossak -d ppossak_restore_check < /실제/백업파일.sql
docker compose --env-file secrets/postgres.env exec -T postgres psql -U ppossak -d ppossak_restore_check -c 'SELECT count(*) FROM cultivation;'
```

행 수만으로 모든 데이터 복구를 확정하지 말고 주요 재배/관리 이력도 확인한다. 운영에서 `down -v`, 볼륨 삭제, 기존 DB 초기화를 하지 않는다. 생성 후 `postgres.env`의 비밀번호만 바꿔도 이미 생성된 DB 계정 비밀번호는 바뀌지 않는다.

업데이트 전 DB 백업과 이전 `app.jar`·Docker 이미지 ID를 보관한다. 새 JAR를 올린 뒤 backend만 `up -d --build --wait backend`로 교체한다(동일한 `--env-file` 필요). 문제 시 이전 JAR로 다시 빌드한다. DB 마이그레이션이 있는 향후 변경은 JAR만 되돌려도 안전한지 별도 검토한다.

이미지 태그는 보안 업데이트에 따라 변할 수 있다. 업데이트는 백업 후 의도적으로 수행하고 `docker compose ... images` 결과를 본인 운영 기록에 남긴다. 로컬 검증에 사용한 실제 버전/다이제스트는 HANDOFF와 작업 명세에 기록한다.

임시 SSH 공개키를 등록했다면 본인이 `~/.ssh/authorized_keys`에서 **ppossak-deploy-20261027로 끝나는 이번 키 한 줄만** 제거한다. 에이전트의 임시 개인키는 폐기됐으며 원격 접속은 재개하지 않는다.

## 9. 확인된 범위

로컬 Docker 검증 결과는 HANDOFF와 tasks/SINGLE_USER_AUTH_DEPLOYMENT.md를 따른다. 실제 미니 PC 설치·진짜 Tunnel 토큰 연결·공개 HTTPS·폰 인수는 사용자가 실행해 확인하기 전까지 NOT RUN이다.

참고: [Cloudflare Docker 실행](https://developers.cloudflare.com/tunnel/get-started/), [token-file](https://developers.cloudflare.com/tunnel/reference/run-parameters/), [Compose 네트워크](https://docs.docker.com/compose/how-tos/networking/), [raw env_file](https://docs.docker.com/reference/compose-file/services/#format).
