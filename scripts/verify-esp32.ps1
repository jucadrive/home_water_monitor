[CmdletBinding()]
param(
    [ValidateSet('Build','CredentialFree','Host','UART')][string]$Mode = 'Build',
    [string]$IdfPath = "$env:USERPROFILE/esp/esp-idf-v5.5.5"
)
$ErrorActionPreference = 'Stop'
if ($Mode -eq 'Build' -or $Mode -eq 'CredentialFree') {
    & "$PSScriptRoot/esp32.ps1" -Action Build -IdfPath $IdfPath -CredentialFree:($Mode -eq 'CredentialFree')
} else {
    # Export the pinned SDK Python environment, including its existing pyserial/psutil.
    & "$PSScriptRoot/esp32.ps1" -Action Version -IdfPath $IdfPath
    if ($Mode -eq 'Host') { & python "$PSScriptRoot/device-test/verify_host.py" }
    else { & python "$PSScriptRoot/device-test/uart_smoke.py" }
}
if ($LASTEXITCODE -ne 0) { throw "ESP32 $Mode verification failed: $LASTEXITCODE" }
