# 보관 원본 우선 자동 복구

상태 VERIFIED(로컬 구현·자동 검증). 2026-11-04 사용자가 전체 계획 승인. 기준 main b1aacb2, 작업 브랜치 codex/automatic-buffer-recovery. 운영 적용·실물 인수는 NOT RUN이며 사용자 최종 리뷰 전 DONE이 아니다.

## 사실과 범위
- 11월3일21시 세션 37f61b37-84e1-4c9d-8456-2a5e6247c5aa가 원본60개/attempt5/MANUAL_WAIT에 남았다. 이후 11월4일14시까지17회 CONTROL_BUSY. 서버 probe는 FRESH였다.
- 사용자 수동 RETRY에서14:22:55 결과8450bytes 전달, 서버 preservation 전달/PUBACK 및 DIAGNOSTICS_RESTORED 확인. Python 최종 NORMAL/RELEASE_CONFIRMED 출력은 아직 미제공이므로 최종 해제는 NOT VERIFIED.
- 11월3일20:55~21:30 backend/broker 필터 로그 없음. 최초 실패 원인은 미확정. 반복 차단은 START 전용 정책과 보관 버퍼 보호의 조합이다.
- 서버 SSH/원격 조작·운영 배포·원본 삭제/강제 해제·Git 발행은 제외. 앱/펌웨어/MQTT 공개 계약·의존성은 유지한다.

## 계약·인수 조건
- 기존 매시간 자동 측정 설정에 recovery-enabled를 추가하며 기본 false다. false에서는 기존 START 정책을 유지한다.
- true에서는 FRESH/ready/MQTT/무고장 상태를 확인한다. IDLE이면 START, 완성된60개/attempt5/MANUAL_WAIT의 동일 보관 세션이면 RETRY 한 회차 후 NORMAL/60개 서버 보존 + 같은 boot/세션 해제 + IDLE/슬롯 해제를 모두 확인하고 START한다.
- 자동 복구는 지정 장비·지정 재배의 기존 START로 출처가 검증된 세션만 대상으로 한다. 활성 명령/lastExecution/boot/round 변경은 장치 잠금 안에서 다시 검증한다. 수동 작업을 탈취하지 않는다.
- 복구는 장비·정각 회차당 최대1개 RETRY이며 키·세션·boot·결과를 신규 V12에 요청 전 커밋한다. 새 START는 기존 회차 키를 사용한다. 응답 유실/프로세스 중단은 같은 회차를 다시 발행하지 않는다.
- 복구 대기는 최대360초, 장시간 DB 트랜잭션 없이 전용 단일 작업 스레드에서 관측한다. 실패/재부팅/STALE/다른 세션/시간초과 시 원본 유지·회차 종료. 성공한 복구에 한해서 정각60초 제한을 넘어도 이 예산 내 현재 시각 START를 허용한다.
- 이전 세션 시각은 바꾸지 않고 누락 회차를 소급 측정하지 않는다. 종료 재배에는 새 START를 하지 않는다. 기존 마이그레이션을 편집하지 않는다.
- 안전한 운영 로그에는 장치/세션/명령/단계/제한된 사유만 기록하고 비밀·원문·예외 메시지는 출력하지 않는다. 최초 실패를 확정할 증거가 없으면 미확정으로 유지한다.

## 순차 Phase
1. V12/회차 복구 기록·상태 검증·기존 제어 경로 재사용·제한된 관측/로그 구현.
2. 가상 시각·중복/동시성·중단/재시작·수동 경합·불일치/오프라인/실패 및 PostgreSQL/MQTT 격리 인수, backend Full.
3. 기본OFF 배포 템플릿·수동 배포/검증·HANDOFF·비밀/diff 검사·산출물 준비. 서버 적용은 사용자 수동이다.

## Phase Result
- 구현: V12 복구 의도/명령/결과 기록, 기본OFF 설정, 지정 재배 출처·FRESH·동일 boot/실행/round 검증, 장치 잠금 안의 수동 경합 방지, 기존 RETRY→최대360초 관측→NORMAL/60·해제/IDLE 후 기존 START. 스케줄러와 별도인 단일 작업 스레드·대기열 없음·종료 시 interrupt. 프로세스 중단 회차는 재발행하지 않는다.
- 진단: 커밋 후 MANUAL_WAIT 진입, MQTT 결과 수신/실제 커밋/보존 확인 발행, 제어 dispatch 실패 및 자동 복구 결과를 제한된 식별자/단계로 기록한다. 원문/비밀/예외 메시지를 출력하지 않는다. 과거 최초 전송 원인은 여전히 NOT CONFIRMED다.
- 변경 파일: session.application.automatic/control, infrastructure.persistence/scheduling/mqtt, application.yaml, 신규 V12 및 관련 테스트, infra/personal/compose.automatic-measurement.yml, AGENTS/HANDOFF/배포·자동 측정·복구 문서. 앱/펌웨어/공개 HTTP·MQTT 스키마/의존성/V1~V11 파일은 변경하지 않았다.
- 첫 단위27개 중25 PASS/설정2 FAIL: 호환 생성자 추가로 설정 바인딩 생성자 선택 실패. 수정1/5는 canonical 생성자 ConstructorBinding 명시다. 증거 attempt1.
- 다음 실행은 시험 장치의 기존 지역 변수와 catch 변수 이름 충돌로 compileTestJava 실패(테스트 미실행). 수정2/5로 catch 이름 수정, compile-attempt2.log 보존.
- 관련50개 중49 PASS/MQTT 신규1 FAIL: 재배별 START를 일반 장비 API로 요청해 계약 검사에 거절됨. 수정3/5로 기존 재배별 경로·본문 사용 및 HTTP 상태 단언 추가. 운영 계약을 바꾸거나 검사를 약화하지 않았다. attempt3 보존.
- 실패 부분 재검증6개 PASS/실패0·오류0·건너뜀0/exit0/1분17초. 실제 시험 MQTT에서 보관60개→통계50→보존/해제→새 세션 START 및 회차 중복 방지를 확인했다. repair-results 보존.
- 최종 ./scripts/verify.ps1 -Mode Full PASS/exit0/5분44초. 297개 새 실행·실패0·오류0·건너뜀0. 기존270+신규27(복구15/서비스3/설정1/DB5/MQTT1/V11→V12 보존1/전용 worker1). test 작업은 새 실행, compileJava는 앞선 성공 결과 UP-TO-DATE이며 bootJar/전체 패키징 성공. backend 정적/Lint/아키텍처 자동 검사는 MISSING이다.
- DB 인수: 잠금 하의 수동 RETRY 선점 보호·boot/round/재배 불일치 거절, 복구 기록 유일성, 재시작 후 명령 연결/미재발행, V11 기존 회차 값 보존·V12 빈 테이블/외래키 검증. 관측에서는 STALE·재부팅·응답 유실·소진·시간초과·shutdown·서버 보존 없는 해제 메시지를 새 START 없이 종료한다.
- Compose 기본값/OFF/ON3조건 PASS. 기존4서비스·백엔드/DB 호스트 포트 미공개·LAN TLS 바인드 유지. 실제 운영 컨테이너 생성/재시작은 하지 않았다.
- 최종 비밀 검사32파일/실제 비밀값8개 대조 이슈0·diff 공백 검사 PASS. 신규 Java/Kotlin200줄 초과 없음. 기존 ControlMqttIntegrationTest256줄은 리뷰 대상이며 줄 수를 위한 분할은 하지 않았다. AGENTS125줄.
- 로컬 증거 .tools/automatic-recovery/result.json, full.log/full-final-results, compose-result.json. 소유 Testcontainers 잔여0 확인. 서버 SSH·운영 DB/장치 조작·Git 커밋/푸시 없음, 기존 작업 트리는 처음 clean이었다.
- 수동 전송 .tools/automatic-recovery/server-upload/: app.recovery.jar SHA256 834B505FEDA9752F3FB63311BDB655EBFE4FA4EC4B03FC10B83B9DAF7FB2013F, compose.automatic-measurement.recovery.yml 및 sha256.json. JAR의 V12/실행 클래스 포함 확인. [업데이트/켜기/조회](../AUTOMATIC_BUFFER_RECOVERY_SETUP.md)를 따른다.
- 남은 위험/다음: 사용자 수동 복구의 최종 NORMAL/RELEASE_CONFIRMED/IDLE 확인, 백업 후 V12 배포·opt-in 활성화 및 다음 정각 정상 실물 인수. 자동 복구는 전송 실패 자체를 없애지 않고 누락 회차를 소급 생성하지 않는다. 미확정/오프라인 시 중단하며 지속 장애에서는 다음 시간에도 측정을 건너뛸 수 있다.
- Git 상태: codex/automatic-buffer-recovery, 변경 unstaged. 커밋 초안 feat: recover retained sessions before hourly measurements. 발행 승인은 이번 범위에 포함하지 않는다.
