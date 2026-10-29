# Android 돌봄 UI 간소화

## 수온 도입 후 설명 정리 — 2026-10-29

- 사용자 승인: 교정/보정 기술 설명은 기본 화면에서 빼고 펼침 상세로 이동한다. 기존 인수 조건4의 교정/출처 요약은 이 추가 승인으로 갱신한다.
- 범위: SessionScreen의 보정/교정/ADS1115 출처를 상세로 이동, 재배 상세 최근 측정의 보정/미교정 설명 제거. 실측 평균 수온·과거25°C 가정·가상/알 수 없는 출처 표시는 유지한다. 원본/보정 계산·계약·서버·펌웨어는 변경하지 않는다.
- 인수: 접힌 결과에서 보정/미교정/센서명 숨김, 펼치면 출처/교정/보정 정보 확인, 다시 접으면 숨김. 과거 가정 수온 표시는 접힌 상태에서도 유지한다. Android Full 및 에뮬레이터 관련 UI로 검증한다. 운영 실측·backend/펌웨어 검증은 기존 성공 결과를 재사용한다.
- 상태 VERIFIED. 기존 온도 작업 미커밋 변경을 보존하며 커밋/푸시·운영 배포·폰 설치는 수행하지 않았다. 일반 HTTPS APK를 별도 제공한다. 이번에 실행한 flutter_emulator는 검증 후 종료했다.

### 추가 승인 최종 결과

- VERIFIED: 기본 결과의 미교정/온도 보정 설명과 ADS1115 센서명을 펼침 상세로 이동, 재배 최근 결과에서 보정/미교정 설명 제거. 실측/모의 평균 수온·기존 가정 수온·가상/미확인 출처 표시는 유지했다. 계산·서버·펌웨어 변경 없음.
- Android Full exit0/59초: JVM21개 새 실행·실패0/오류0/건너뜀0, debug/미서명 release PASS, Lint 오류0/기존 경고11. 백엔드/펌웨어/격리 종단은 변경이 없어 기존 성공 결과를 재사용했다.
- API35 전용 에뮬레이터 UI17개 중16개 PASS 후 실패한1개를 수정해 단독 재검증 PASS/exit0/40초. 최종17개 인수 범위 통과이나 단일17개 실행이 전부 성공했다고 주장하지 않는다. 수정1/5는 이전 요구(기본 미교정 표시) 테스트를 상세 표시 조건으로 갱신, 수정2/5는 LazyColumn 밖 원본을 performScrollToNode로 먼저 찾도록 보완. 테스트 삭제/검증 약화 없음. 같은 원인3회 반복 없음.
- 원본/안정화 표시·접힘 화면을 fixture 캡처로 시각 검수 PASS. 증거 `.tools/temperature-ui-20261029/result.json`, `full.log`, `connected-final.log`, `targeted-final.log`, `ui-attempt2`, `ui-results`, `result-screen.png`. 첫 두 UI 실패 결과도 보존했다.
- 일반 HTTPS APK `.tools/temperature-ui-20261029/ppossak-temperature-ui-debug.apk`, SHA256 `B01E71FE51EE487C3A9F481FEF0B8EAD8AF43B4A4B70650AAEEC3B454ACABEB2`. 개인 폰 설치는 NOT RUN. 비밀 검사79파일 이슈0, diff 검사 PASS, 기존 unstaged/첨부/개인 설정 보존, 커밋/푸시 없음. 커밋 초안 `fix: move measurement technical details into expanded view`.

- 승인: 2026-10-28 사용자 전체 계획 승인. 로그인 → 메인 → 측정 결과 순서로 구현·검증한다. 서버 원격 조작·폰 설치·Git 커밋/푸시는 포함하지 않는다.
- 목표: 단일 개인 서버 로그인, 돌봄 브랜드 문구 통일, 장식으로 인한 공간 점유 제거, 측정 요약과 상세 정보 분리.
- 변경 범위: Android 설정/공통 소개/상단바/결과 화면, 서버 선택·빌드 설정과 관련 테스트, Android 검증 스크립트·사용 문서.

## 인수 조건

1. 새 설치·일반 로그인은 고정 HTTPS 서버를 사용하며 서버 주소·장치 설정·라이선스 버튼·HTTP 개발 안내가 로그인 화면에 나타나지 않는다.
2. 장치 ID는 인증 후 설정에서 편집한다. 글꼴 라이선스 자산은 APK에 유지한다.
3. 상단바와 공통 소개의 식물 장식을 제거하고 기본 브랜드 문구를 `뽀싹 돌봄 일지`로 통일한다. 제목은 전체 가용 너비를 사용한다.
4. 결과는 대표값·시각/근거·보존 상태·원본/통계 개수·출처·교정/가정 수온 근거를 요약한다. 보조 통계·장치/교정 버전/세션 ID는 펼침 영역에 표시한다. 보존 원본과 안정화 제외 표시는 유지한다.
5. 불완전 통계를 0으로 표시하지 않으며 가상과 실측을 구분한다. 320dp·글꼴 1.6배에서 스크롤로 필요한 정보를 읽고 상세를 열고 닫을 수 있다.
6. 기존 미확정 요청은 원래 서버 origin을 선택하며 키·본문을 보존한다. 일반 설치는 예전 저장 주소 대신 번들 서버를 사용한다. 토큰은 기존 origin 결합 검증을 유지한다.
7. 격리된 HTTP 인수 주소는 debug 빌드의 loopback 전용 Gradle 속성으로만 주입한다. release는 고정 HTTPS다.

## 검증·위험

- Android Full(JDK21), 명시적 에뮬레이터 Connected/EndToEnd, 화면 캡처 검수, diff/라이선스 패키징 확인.
- backend·펌웨어·API·DB·MQTT 계약 변경 없음. 백엔드 전체 테스트는 해당 변경의 검증 대상이 아니다.
- 다른 서버의 미확정 요청이 남아 있으면 원래 서버 계정으로 확인할 때까지 그 서버를 유지한다. 서버를 자동으로 갈아타며 요청을 재전송하지 않는다.
- 상태: DONE. 2026-10-28 사용자가 수정 APK를 확인하고 만족 의사 및 커밋·푸시·PR·main 병합을 명시 승인했다. UI 및 격리된 HTTP·DB·MQTT 종단 인수 VERIFIED. 아래 최초 차단은 재검증으로 해소했다.

## 종단 재검증 — 2026-10-28

- 사용자 Docker 재시작 후 재개 지시. Docker29.8.1/API35 emulator-5554 준비 확인. backend는 JDK25, Android는 JDK21로 실행했다. 실행 폴더 `.tools/android-acceptance/ui-e2e-20261028-1`.
- backend `./gradlew.bat androidAcceptanceServer '-PacceptanceDirectory=../../.tools/android-acceptance/ui-e2e-20261028-1' --no-daemon --console=plain`로 전용 PostgreSQL18·Mosquitto2.0.22·루프백 서버를 시작했다. 이번 생성 자원만 사용하며 운영 서버·DB·실물과 연결하지 않았다.
- ready의 port/deviceId를 읽고 emulator-5554에 동적 adb reverse를 만든 뒤 `./scripts/verify-android.ps1 -Mode EndToEnd -JavaHome C:/dev/jdk-21.0.8+9 -AndroidSdk C:/Users/jhjjh/AppData/Local/Android/Sdk -DeviceSerial emulator-5554 -ServerOrigin <이번 reverse의 HTTP loopback> -MqttDeviceId <ready의 deviceId>` 실행.
- 새 실행 Forms13+AppFlow1=14개, 실패0/오류0/건너뜀0, exit0/1분33초. 로그인→장치 설정/잘못된 ID 거절→재배 등록→전체 교체→START→모의 원본60/통계50→NORMAL/RELEASE_CONFIRMED→가상 출처 구분→재배 종료→미연결 불완전 기록 조회 PASS. 인증 우회 없음.
- 결과 XML·로그는 실행 폴더의 results, 전체 앱 캡처10장은 screens에 보존했다. 메인·정상 결과 캡처 검수 PASS. 이전 UI fixture 캡처와 구분한다.
- stop 파일로 서버를 종료하여 ANDROID_ACCEPTANCE_STOPPED/exit0/stopped=closed 확인. 이번 PostgreSQL·MQTT 컨테이너2개가 docker ps -a에서 사라진 것을 확인하고 이번 adb reverse 포트만 제거했다.
- 시험 origin이 기본 산출물에 남지 않도록 속성 없는 assembleDebug를 실행해 exit0/22초 PASS. 실제 HTTPS API_ORIGIN 복원 확인. 기존 배포용 `.tools/ui-20261028/ppossak-ui-debug.apk`는 유지한다.
- 코드 수정 없이 기존 환경 차단 해소. 실제 폰/운영 HTTPS/실물 재인수는 이번 범위가 아니다. 커밋·푸시 없음.

## Phase Result — 2026-10-28

- 구현: 위 1~7의 표시·설정 변경, 미확정 origin 보존 정책 및 테스트. 일반 debug/release의 API_ORIGIN 고정값을 생성 파일에서 확인했다. 기존 요청의 키·본문·토큰 암호화 코드는 변경하지 않았다.
- `./scripts/verify-android.ps1 -Mode Full -JavaHome C:/dev/jdk-21.0.8+9 -AndroidSdk C:/Users/jhjjh/AppData/Local/Android/Sdk`: exit0, JVM17개 새 실행·실패0/오류0/건너뜀0, debug/미서명 release 빌드 PASS, Lint 오류0/경고11. 최초 Gradle DSL `java.net.URI` 이름 충돌1회를 import로 수정한 후 통과했다. Gradle 캐시 접근은 샌드박스 승인 환경에서 실행했다.
- `-Mode Connected -DeviceSerial emulator-5554` 같은 JDK/SDK: exit0, API35 FormsTest12개 새 실행·실패0/오류0/건너뜀0. 이후 추가한 메인 배치 테스트만 `connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.juca.hydroponic.FormsTest#dashboardUsesCareHeadingWithPlantRecords`로 실행해 새1개 PASS/exit0. 합계13개이며 마지막 XML은 추가1개 결과다.
- 로그인·정상 결과·320dp/1.6배 결과·큰 글꼴 소개·재배 카드 메인 캡처5장을 검수했다. 원본 경고, 미교정/가상/가정 수온, 상세 펼침/접기와 큰 글꼴 스크롤 동작 PASS. 캡처는 테스트 fixture이며 운영 측정이 아니다.
- APK 내부 두 글꼴 라이선스 자산 유지 확인. 결과 `.tools/ui-20261028/ppossak-ui-debug.apk`, SHA256 `E9C30B072689B41761F4EDBFBD11BB7E645DA15615ECC1BD182FE221F9BF9DAD`. 캡처 `.tools/ui-20261028/screens/`, 검사 요약 `.tools/ui-20261028/verification.json`.
- EndToEnd는 NOT VERIFIED: 로컬 Docker Linux named pipe 부재를 반복 확인했다. Docker Desktop은 already running을 반환했으나 엔진은 사용 불가다. 서버 실행1회는 실행 JDK21 지정 오류로 JDK25 toolchain 탐색 단계에서 종료됐다. DB·MQTT 시험 자원은 만들어지지 않았다. 재개 시 Docker Linux 엔진 준비와 JDK25로 새 acceptanceDirectory를 사용해야 한다. 실제 HTTPS 로그인·폰 설치도 이번 변경에서는 NOT RUN이다.
- 신규 Java/Kotlin은 모두200줄 이하. backend·DB·장비·보안 정책 변경 없음. `git diff --check` PASS. 브랜치 `codex/android-ui-simplification`, 변경은 unstaged이며 커밋·푸시하지 않았다.
- 커밋 초안: `feat: simplify owner login and measurement UI`. 다음: 사용자 화면 검수, 환경 준비 후 선택형 EndToEnd·폰 확인.
