param(
    [ValidateSet('Unit', 'Full')]
    [string]$Mode = 'Full'
)

# 어느 디렉터리에서 호출해도 같은 Gradle 프로젝트를 검증한다.
$ErrorActionPreference = 'Stop'
$projectPath = Join-Path $PSScriptRoot '../backend/home-water-monitor-backend'
$wrapperName = if ($env:OS -eq 'Windows_NT') { 'gradlew.bat' } else { 'gradlew' }
$verificationExitCode = 1

Push-Location $projectPath
try {
    # Unit은 DB 없는 도메인 테스트, Full은 컴파일·전체 테스트·패키징을 수행한다.
    $gradleArguments = if ($Mode -eq 'Unit') {
        @('test', '--tests', '*.domain.*', '--no-daemon', '--console=plain')
    } else {
        @('build', '--no-daemon', '--console=plain')
    }
    & (Join-Path (Get-Location) $wrapperName) @gradleArguments
    $verificationExitCode = $LASTEXITCODE
    if ($verificationExitCode -ne 0) {
        Write-Error "Gradle verification failed (exit $verificationExitCode)." -ErrorAction Continue
    }
} finally {
    # 실패한 경우에도 호출자의 작업 디렉터리를 복원한다.
    Pop-Location
}
exit $verificationExitCode
