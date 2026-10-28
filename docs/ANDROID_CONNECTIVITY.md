# Android 연결 정책·개발 검증

## 기본 정책
2026-10-28 UI 간소화 승인에 따라 일반 앱은 `https://ppossak-api.jucadrive.com`을 사용하며 서버 주소 입력을 제공하지 않는다. 장치 ID는 로그인 후 설정한다. 릴리즈는 HTTPS만 허용하고 인증서 검증을 우회하지 않는다.
이전 버전의 미확정 요청이 있으면 원래 origin을 유지한다. 요청의 키·본문을 지우거나 새 서버로 전송하지 않는다. debug 인수 빌드만 `-PacceptanceOrigin=http://127.0.0.1:<port>`로 격리 서버를 선택할 수 있으며 release 주소는 변하지 않는다.
현재 백엔드의 개발 HTTP와 연결하려면 디버그 한정 예외 또는 사용자가 준비한 HTTPS 서버가 필요하다. 2026-10-26 후속 승인에 따라 단일 사용자 인증과 ppossak-api.jucadrive.com의 Cloudflare Tunnel 배포를 진행한다. 실제 완료 여부는 tasks/SINGLE_USER_AUTH_DEPLOYMENT.md와 HANDOFF.md를 따른다. 인증서 검증 완화·다른 서비스 공개 변경은 승인 범위 밖이다.

앱 토큰은 서버 origin에 귀속하고 Keystore 키로 암호화해 보관한다. 비밀번호를 영속 저장하지 않는다. 만료·로그아웃 뒤에도 미확정 요청은 보존하고 재로그인 후 동일 요청을 명시적으로 재시도한다. Cloudflare 서비스 토큰·터널 자격증명을 APK에 포함하지 않는다.

## 승인된 디버그 예외
AGENTS.md의 보안 정책 완화 사전 승인에 따라 2026-10-26 사용자가 아래 디버그 루프백 예외를 명시 승인했다.
디버그 리소스에만 다음 정책을 두고 앱 URL 검증도 같은 세 호스트의 HTTP만 허용한다. LAN IP·외부 도메인의 HTTP는 허용하지 않는다.

```xml
<network-security-config>
    <base-config cleartextTrafficPermitted="false" />
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="false">localhost</domain>
        <domain includeSubdomains="false">127.0.0.1</domain>
        <domain includeSubdomains="false">10.0.2.2</domain>
    </domain-config>
</network-security-config>
```

10.0.2.2는 Android Emulator의 호스트 루프백 접근에 사용한다. USB 연결 기기는 adb reverse로 localhost에 연결할 수 있다. 릴리즈 리소스에는 예외를 포함하지 않는다.
위 XML은 app/src/debug에만 적용했다. 앱 URL 검증도 같은 예외를 사용한다. 실제 HTTP 연동 결과는 전환 작업 명세를 따른다.
