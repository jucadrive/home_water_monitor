[CmdletBinding()]
param(
    [ValidateSet('Build','CredentialFree','Host','UART')][string]$Mode = 'Build',
    [string]$IdfPath = "$env:USERPROFILE/esp/esp-idf-v5.5.5",
    [string]$Port = 'COM3',
    [ValidateSet('MQTT','UART')][string]$ControlMode = 'MQTT',
    [ValidateSet('ADS1115','SIMULATED')][string]$Source = 'ADS1115'
)
$ErrorActionPreference = 'Stop'
if ($Mode -eq 'Build' -or $Mode -eq 'CredentialFree') {
    & "$PSScriptRoot/esp32.ps1" -Action Build -IdfPath $IdfPath -ControlMode $ControlMode -Source $Source -CredentialFree:($Mode -eq 'CredentialFree')
} else {
    # Export the pinned SDK Python environment, including its existing pyserial/psutil.
    & "$PSScriptRoot/esp32.ps1" -Action Version -IdfPath $IdfPath
    if ($Mode -eq 'Host') { & python "$PSScriptRoot/device-test/verify_host.py" }
    else { & python "$PSScriptRoot/device-test/uart_smoke.py" --port $Port }
}
if ($LASTEXITCODE -ne 0) { throw "ESP32 $Mode verification failed: $LASTEXITCODE" }
