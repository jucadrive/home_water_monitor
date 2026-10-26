# Android 개발·검증

## 별도 빌드 루트
- mobile/android: Kotlin2.2.21·Compose BOM2025.12.01·AGP8.13.2·Gradle8.13, minSdk26/targetSdk36, Java/Kotlin bytecode17. 실행 JDK21 권장.
- backend의 Java25·Gradle9.8.1을 바꾸지 않는다. Android 빌드 때 JDK25를 사용하지 않는다.
- 공식 호환 근거: https://developer.android.com/build/releases/agp-8-13-0-release-notes (AGP8.13은 Gradle8.13·JDK17 이상·API36.1까지 지원).
- Kotlin2 Compose compiler plugin: https://developer.android.com/jetpack/androidx/releases/compose-kotlin.
- 기본 HTTP/JSON은 Android 플랫폼 API를 사용하고 외부 REST·차트·DI 프레임워크를 도입하지 않았다. org.json JVM 테스트 라이브러리는 플랫폼 JSON 동작 테스트용이다.

## 로컬 준비
1. JDK21, Android SDK platform36/build-tools35.0.0/platform-tools를 준비한다. SDK는 Android Studio 또는 공식 명령줄 도구로 설치한다.
2. Android Studio에서 mobile/android를 열거나 ANDROID_HOME을 설정한다. local.properties는 Git 제외하며 Windows 드라이브 콜론은 이스케이프한다.
3. 실제 비밀·서명 키는 저장소에 넣지 않는다. 릴리즈 APK는 미서명 검증 산출물이며 Play Store 배포가 아니다.
4. SDK 약관 동의·설치는 사용자가 승인한 범위에서만 수행한다. 이번 세션은 도구 설치를 명령 승인받아 .tools에 설치했다.

공식 명령줄 도구: https://developer.android.com/studio (Windows build15859902, SHA25690ae805d20434428bffcb699c290860f19bb5f66a67e6b330067e3de801fb04a 검증).

## 검증
저장소 루트에서 -JavaHome에 로컬 JDK21 경로를 지정한다.
```powershell
./scripts/verify-android.ps1 -Mode Full -JavaHome <JDK21경로>
./scripts/verify-android.ps1 -Mode Connected -JavaHome <JDK21경로> -DeviceSerial <테스트기기serial>
```
Full은 JVM 단위·Android Lint·디버그/미서명 릴리즈 APK다. Connected는 전용 Android의 Compose 화면 테스트다. 연결 기기 없이 UI 통과로 보고하지 않는다.
각 명령의 종료 코드와 XML의 테스트 수/실패/오류/건너뜀을 확인한다. UP-TO-DATE는 새 실행으로 세지 않는다.
네트워크 정책은 ANDROID_CONNECTIVITY.md를 따른다. 에뮬레이터는 사용자 서버 포트에 맞춰 10.0.2.2 주소를 입력한다. 실기기는 명시적 adb reverse 설정 후 루프백을 사용한다.

## 앱 사용
서버 주소 설정→재배 등록→재배 상세→관리 기록/측정 시작→명령·보존·해제 상태→결과·추세·원본 확인.
미연결 과거 측정은 대시보드의 별도 진입점으로 조회한다. 임계값·영양소 판정·자동 투입·웹 UI는 없다.
통신 실패로 처리 결과가 불확실하면 저장한 동일 키·내용으로 재시도한다. 서버 주소를 바꿔 불확실한 요청을 전송하지 않는다.
추적 중단은 서버 작업 취소가 아니다. 기록·장치 상태를 먼저 확인한다.

## 실기기 검증 결과(2026-10-26)
SM-S947N(Android17/API37)에서 FormsTest4개와 AppFlowTest1개를 합쳐 새 실행5개, 실패0/오류0/건너뜀0으로 통과했다. 최초 에뮬레이터 차단은 실제 폰 사용으로 해소했다. Android17 호환을 위해 테스트 전용 Espresso3.7.0/runner1.7.0/ext.junit1.3.0을 사용한다. 앱 런타임 의존성은 추가하지 않았다.

Full은 JVM9개 새 실행 PASS, Lint0오류/11경고, 디버그·미서명 릴리즈 APK PASS다. Lint 경고는 의존성 업데이트 안내8개와 SharedPreferences KTX 권고3개이며 검사 비활성화·baseline은 없다. 실기기 캡처3장도 검수했다. 실제 센서·교정·독립 정확도는 사용자 지시로 후속 검증한다.

## 격리된 소프트웨어 종단 인수
테스트용 Android 기기에서 수행한다. 연결 시험 도구는 시험 APK 설치·제거를 수행하므로 개인 운영 앱 데이터의 백업 도구로 사용하지 않는다. AppFlowTest 자체는 설정을 보존·복원하고 기존 미확정 요청이 있으면 거절한다.

1. 매 실행마다 기존 ready/stop/stopped 파일이 없는 새 실행 디렉터리를 지정한다(아래 local은 예시). Docker 엔진을 준비한다. backend 루트에서 JDK25로 다음 명령을 실행하고 ready.json 생성을 기다린다. Gradle 속성 인수 전체를 따옴표로 감싼다.

```powershell
./gradlew.bat androidAcceptanceServer '-PacceptanceDirectory=../../.tools/android-acceptance/local' --no-daemon --console=plain
```

2. 서버는 전용 임시 PostgreSQL18·Mosquitto2.0.22와 루프백 HTTP를 시작한다. `.tools/android-acceptance/local/ready.json`의 port/deviceId를 읽는다. 서버의 최대 수명은30분이며 stopped 파일로 정상 정리를 확인한다. 개발·운영 DB를 사용하지 않는다.
3. 선택한 기기에 `adb -s <serial> reverse tcp:0 tcp:<ready의port>`를 실행하고 반환된 폰 포트를 기록한다. 저장소 루트에서 JDK21로 다음 명령을 실행한다.

```powershell
./scripts/verify-android.ps1 -Mode EndToEnd -JavaHome <JDK21경로> -DeviceSerial <serial> -ServerOrigin http://127.0.0.1:<반환된폰포트> -MqttDeviceId <ready의deviceId>
```

4. Forms4개+AppFlow1개를 실행한다. 실제 HTTP·DB·MQTT로 재배 생성→전체 교체 기록→START→모의 결과 NORMAL 보존→커밋 후 보존 확인→버퍼 해제→원본 조회→재배 종료→미연결 과거 기록 조회를 확인한다. fixture는 simulated-v1이며 실측 추세에서 제외된다. 불완전 과거 기록은 통계를0으로 꾸미지 않는다.
5. 시험이 생성한 화면은 `/data/local/tmp/com.juca.hydroponic.acceptance/accepted-<이름>.png`에 보존한다. 이름은 normal-result/cultivation-detail/legacy-incomplete이며 adb pull로 검수할 수 있다. 임시 산출물은 Git 제외다.
6. 실행 디렉터리에 stop 파일을 만들어 서버 종료를 요청하고 stopped를 확인한다. 해당 시험에서 만든 폰 포트만 `adb -s <serial> reverse --remove tcp:<폰포트>`로 제거한다. 다른 ADB 연결·에뮬레이터·Docker 엔진은 종료하지 않는다. 연결 시험 후 앱이 제거됐다면 디버그 APK를 다시 설치한다.

실물 후속 인수는 ESP32·ADS1115·SEN0244가 준비된 뒤 별도 수행한다. 이번 통과 결과를 실물 센서 인수나 정확도 검증으로 취급하지 않는다.

## 작은 정원 UI 개편 인수(2026-10-26)
초록/크림 테마와 한글 폰트·새싹 그래픽을 적용했다. emulator-5554(API37)에서 최종 EndToEnd7개(Forms6+AppFlow1) 새 실행 PASS. Forms에 좁은320dp/1.6배 글꼴과 폰트 라이선스 확인을 추가했다. 기존 위 실물 폰5개는 당시 전환 인수 이력이며 이번 디자인은 에뮬레이터에서 검증했다. AppFlow 캡처는 기존3장과 garden-settings/empty/home/detail-top/error/create/maintenance를 합쳐10장이다. 자세한 결과는 tasks/ANDROID_GARDEN_UI.md와 HANDOFF 최신 항목을 따른다.
