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

전체 테스트의 `contextLoads`는 Spring 컨텍스트를 시작하므로 DB와 환경변수가 필요하다. 통합 테스트의 Testcontainers 연결은 아직 작성하지 않았다.

## 이어서 작업

Codex에는 이 저장소 루트를 프로젝트로 연다. [최신 인계](docs/HANDOFF.md)를 먼저 읽고 한 단계씩 이어간다.

PC를 바꾸기 전 진행 메모와 변경 파일을 커밋·푸시하고, 이어받는 PC는 수정 전 작업 트리가 깨끗한지 확인한 뒤 `git pull --ff-only`로 갱신한다.
