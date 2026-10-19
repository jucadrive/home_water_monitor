# Home Water Monitor

집에서 채수한 수돗물의 TDS 추세를 관측하고, Java/Spring 설계·구현 역량을 기르는 학습 프로젝트다. TDS만으로 음용 안전이나 필터 황변 원인을 판정하지 않는다.

## 구조

```text
home-water-monitor/                  # Git / Codex 프로젝트 루트
├── AGENTS.md                        # Codex 작업 안내
├── .gitignore                       # 저장소 공통 제외 규칙
├── docs/                            # 명세, 설계, 최신 인계
├── infra/
│   ├── compose.yml                  # PostgreSQL 실행 설정
│   ├── .env.example                 # 공유할 빈 양식
│   └── .env                         # PC별 실제 값, Git 제외
└── backend/home-water-monitor-backend/  # Gradle / IntelliJ 프로젝트 루트
```

## 새 PC에서 시작

Git, JDK 25, Docker Desktop을 준비한다. 모든 명령의 시작 위치는 저장소 루트다.

1. `infra/.env.example`을 `infra/.env`로 복사하고 `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`를 채운다. `.env`는 각 PC에서 직접 관리한다. 기존 DB 볼륨을 사용하는 경우 그 DB의 계정과 일치시킨다.
2. PostgreSQL을 실행한다.

   ```powershell
   docker compose --env-file infra/.env -f infra/compose.yml up -d
   docker compose --env-file infra/.env -f infra/compose.yml ps
   ```

3. IntelliJ에서 `backend/home-water-monitor-backend`를 Gradle 프로젝트로 열고 Gradle JVM을 JDK 25로 설정한다. 별도 Gradle 설치는 필요 없다.
4. 서버 Run Configuration의 **Environment variables**에 `.env`와 같은 세 변수를 설정한다. Compose의 `.env`가 IntelliJ에서 실행하는 서버에 자동 전달되지는 않는다. 비밀번호를 포함한 실행 설정은 공유 파일로 저장하지 않는다.
5. `HomeWaterMonitorBackendApplication`을 실행한다. 새 DB의 테이블은 Flyway가 V1·V2로 생성한다. 실제 DB 데이터와 Docker 볼륨은 Git으로 이동하지 않는다.

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

각 통합 테스트의 통과는 사용자가 보고했다. 전체 테스트의 `contextLoads`에는 Testcontainers 연결이 없으므로 별도로 개발 DB와 환경변수가 필요하다.

이미 적용한 Flyway V1/V2는 공백·정렬을 포함해 수정하지 않는다. 스키마 변경은 새 마이그레이션으로 추가한다.

## 이어서 작업

Codex에는 이 저장소 루트를 프로젝트로 연다. [최신 인계](docs/HANDOFF.md)를 먼저 읽고 한 단계씩 이어간다.

현재 재개 지점은 [측정 도메인 비교 코드와 단위 테스트](docs/NEXT_MEASUREMENT_COMPARISON.md)다. 해당 문서의 코드는 아직 작성하지 않았으며 사용자가 다음 작업에서 직접 작성한다.

PC를 바꾸기 전 진행 메모와 변경 파일을 커밋·푸시하고, 이어받는 PC는 수정 전 작업 트리가 깨끗한지 확인한 뒤 `git pull --ff-only`로 갱신한다.

Git 커밋·푸시는 `home-water-monitor` 루트에서 소스·인프라·문서를 함께 관리한다. Gradle 실행 위치인 `backend/home-water-monitor-backend`와 구분한다.
