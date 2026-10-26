# Android 연결 정책·개발 검증

## 기본 정책
앱은 서버 origin을 사용자가 지정하며 기본 URL·장치 ID를 하드코딩하지 않는다. 릴리즈는 HTTPS만 허용하고 인증서 검증을 우회하지 않는다.
현재 백엔드의 개발 HTTP와 연결하려면 디버그 한정 예외 또는 사용자가 준비한 HTTPS 서버가 필요하다. 신규 TLS 프록시·공개 노출은 승인 범위 밖이다.

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
