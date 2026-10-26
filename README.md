# Hydroponic Nutrient Monitor

기존 Home Water Monitor의 IoT 측정·보존 기반을 수경재배 영양액 TDS 추세와 재배·물 관리 이력 서비스로 확장한다. TDS로 식물 건강·영양소별 상태·안전성·교체 시점을 확정하지 않는다.

2026-10-26 승인: 클라이언트는 Kotlin·Jetpack Compose 안드로이드 앱이며 웹사이트는 구현하지 않는다. 재배·관리·측정 연결/조회 백엔드는 Full204개 검증을 통과했고 앱은 APK/JVM9개/Lint 검증을 통과했다. 실제 폰(Android17) 화면·앱 종단5개도 통과했다. 소프트웨어 인수는 VERIFIED다. 현재 PR 통합은 사용자 승인 범위에서 진행 중이다. 실제 센서 장비 인수는 사용자 지시로 후속 검증하며 NOT VERIFIED다. 현재 상태는 [HANDOFF](docs/HANDOFF.md), 제품 정책은 [MVP](docs/HYDROPONIC_MVP.md), 승인·인수 조건은 [전환 명세](docs/tasks/HYDROPONIC_MIGRATION.md)를 따른다.

현재 SEN0244는 0~1000ppm·미교정·25°C 가정이다. 교정·독립 정확도는 NOT VERIFIED이며 대상 영양액의 측정 범위 적합성을 확인해야 한다.

## 구조

```text
home-water-monitor/                  # Git / Codex 프로젝트 루트
├── AGENTS.md                        # Codex 작업 안내
├── .gitignore                       # 저장소 공통 제외 규칙
├── docs/                            # 명세, 설계, 최신 인계
├── firmware/esp32/                 # ESP-IDF 측정·통신 펌웨어
├── scripts/                        # 자동검증·장비 인수 도구
├── mobile/android/                 # Kotlin·Compose Android 앱, 별도 Gradle 루트
├── infra/
│   ├── compose.yml                  # PostgreSQL 실행 설정
│   ├── .env.example                 # 공유할 빈 양식
│   └── .env                         # PC별 실제 값, Git 제외
└── backend/home-water-monitor-backend/  # Gradle / IntelliJ 프로젝트 루트
```

## 새 PC에서 시작

Android 설정·실행·검증은 [ANDROID_SETUP.md](docs/ANDROID_SETUP.md)를 따른다. 백엔드는 JDK25, Android Gradle은 JDK21을 구분한다. SDK·개인 local.properties·서명 키·APK는 Git 제외다.

Git, JDK 25, Docker Desktop을 준비한다. 모든 명령의 시작 위치는 저장소 루트다.

1. `infra/.env.example`을 `infra/.env`로 복사하고 `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`를 채운다. `.env`는 각 PC에서 직접 관리한다. 기존 DB 볼륨을 사용하는 경우 그 DB의 계정과 일치시킨다.
2. PostgreSQL을 실행한다.

   ```powershell
   docker compose --env-file infra/.env -f infra/compose.yml up -d
   docker compose --env-file infra/.env -f infra/compose.yml ps
   ```

3. IntelliJ에서 `backend/home-water-monitor-backend`를 Gradle 프로젝트로 열고 Gradle JVM을 JDK 25로 설정한다. 별도 Gradle 설치는 필요 없다.
4. 서버 Run Configuration의 **Environment variables**에 `.env`와 같은 세 변수를 설정한다. Compose의 `.env`가 IntelliJ에서 실행하는 서버에 자동 전달되지는 않는다. 비밀번호를 포함한 실행 설정은 공유 파일로 저장하지 않는다.
5. `HomeWaterMonitorBackendApplication`을 실행한다. 새 DB의 테이블은 Flyway가 적용되지 않은 마이그레이션을 순서대로 실행한다. 전환 전 기준은 V1~V5이며 새 전환 마이그레이션은 테스트 검증 후 운영 적용을 별도 승인받는다. 실제 DB 데이터와 Docker 볼륨은 Git으로 이동하지 않는다.

통계 계산 테스트만 실행하려면 실제 Gradle 프로젝트 루트에서 다음 명령을 사용한다.

```powershell
cd backend/home-water-monitor-backend
.\gradlew.bat test --tests "com.juca.homewatermonitorbackend.measurement.domain.TdsStatisticsCalculatorTest"
```

정상 세션 저장 통합 테스트는 Testcontainers PostgreSQL 18을 사용한다. Docker Desktop을 실행한 상태에서 같은 Gradle 프로젝트 폴더에서 실행한다. 테스트가 DB 접속 정보를 등록하므로 개발 DB 환경변수는 필요하지 않다.

```powershell
# 정상 커밋·저장 중 실패·커밋 실패 테스트 3개 실행
./gradlew.bat test --tests "com.juca.homewatermonitorbackend.session.application.SaveNormalTdsSessionServiceIntegrationTest"
```

전체 테스트의 contextLoads도 Testcontainers를 사용한다. 전체 검증에는 Docker 엔진이 필요하며 개발 DB·비밀번호는 필요하지 않다. 최신 실행 결과는 docs/HANDOFF.md를 확인한다.

이미 적용한 모든 Flyway 마이그레이션은 공백·정렬을 포함해 수정하지 않는다. 스키마 변경은 새 마이그레이션으로 추가한다.

## 이어서 작업

Codex에는 이 저장소 루트를 프로젝트로 연다. [최신 인계](docs/HANDOFF.md)를 먼저 읽고 한 단계씩 이어간다.

현재 상태·검증 증거는 [HANDOFF](docs/HANDOFF.md)를 따른다. MQTT 5.0 서버 수신·저장·커밋 후 확인을 구현했으며,
설정과 실패 정책은 [MQTT 연동 안내](docs/MQTT_INTEGRATION.md), 인수 조건은 [작업 명세](docs/tasks/MQTT_INTEGRATION.md)를 참조한다.

PC를 바꾸기 전 진행 메모를 남기고 별도 사용자 승인 후 변경 파일을 커밋·푸시한다.  이어받는 PC는 수정 전 작업 트리가 깨끗한지 확인한 뒤 `git pull --ff-only`로 갱신한다.

Git 커밋·푸시는 `home-water-monitor` 루트에서 소스·인프라·문서를 함께 관리한다. Gradle 실행 위치인 `backend/home-water-monitor-backend`와 구분한다.

## 단일 에이전트 하네스

[AGENTS.md](AGENTS.md)와 [Harness.md](docs/Harness.md)를 따른다.
사용자는 목표와 주요 설계를 승인하고 AI는 승인 범위의 구현·검증·기록을 수행한다.
커밋·푸시·운영 배포는 별도 승인 대상이다.

JDK 25를 설치하고 JAVA_HOME과 PATH를 해당 JDK로 설정한다.
IntelliJ의 Project SDK와 Gradle JVM도 25로 맞춘다. 빌드는 이미 Java 25 toolchain을 사용한다.

```powershell
# 저장소 루트: DB 없는 도메인 단위 테스트
./scripts/verify.ps1 -Mode Unit
# Docker 엔진 실행 후: 전체 테스트와 패키징
./scripts/verify.ps1 -Mode Full
```

스크립트는 실패 종료 코드를 전파한다. Linux/macOS에서는 PowerShell(pwsh)이 필요하다.
GitHub Actions도 JDK 25에서 같은 Full 검증을 수행하고 테스트 보고서를 업로드한다.
CI 원격 실행 결과와 브랜치 보호는 별도 확인 대상이다.
Lint·정적 분석·아키텍처 자동 검사는 현재 MISSING이며 기본 check에 포함되지 않는다.

브랜치·커밋·푸시 승인과 CI 확인 절차는 [Git 작업 전략](docs/GIT_WORKFLOW.md)을 따른다.
