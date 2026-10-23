[CmdletBinding()]
param(
    [ValidateSet('Build','Flash','Monitor','Version','Test')][string]$Action = 'Build',
    [string]$IdfPath = "$env:USERPROFILE/esp/esp-idf-v5.5.5",
    [string]$Port = 'COM3',
    [switch]$ManualDownload,
    [ValidateSet('MQTT','UART')][string]$ControlMode = 'MQTT',
    [ValidateSet('ADS1115','SIMULATED')][string]$Source = 'ADS1115',
    [switch]$CredentialFree
)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
if (!(Test-Path -LiteralPath "$IdfPath/export.ps1")) { throw 'ESP-IDF 5.5.5 is not installed. See docs/ESP32_SETUP.md.' }
$actual = & git -C $IdfPath rev-parse HEAD
if ($LASTEXITCODE -ne 0 -or $actual -ne 'b774170ff46c393eeb5e495ea37936038d3f4f4f') { throw 'ESP-IDF source does not match pinned v5.5.5.' }
$mqtt = & git -C "$IdfPath/components/mqtt/esp-mqtt" rev-parse HEAD
if ($LASTEXITCODE -ne 0 -or $mqtt -ne '0e9ec171b448db2101ea537300976b7ffe595941') { throw 'ESP-MQTT does not match the pinned SDK submodule.' }
. "$IdfPath/export.ps1" *> $null
if ($LASTEXITCODE -ne 0) { throw 'ESP-IDF environment export failed.' }
$project = Join-Path $root 'firmware/esp32'
$credentialFlag = if ($CredentialFree) { 'ON' } else { 'OFF' }
$controlFlag = if ($ControlMode -eq 'MQTT') { 'ON' } else { 'OFF' }
$sensorFlag = if ($Source -eq 'ADS1115') { 'ON' } else { 'OFF' }
switch ($Action) {
    'Version' { & python "$IdfPath/tools/idf.py" --version }
    'Build' { & python "$IdfPath/tools/idf.py" -C $project -D HWM_TEST_IMAGE=OFF -D "HWM_CONTROL_MQTT=$controlFlag" -D "HWM_REAL_SENSOR=$sensorFlag" -D "HWM_CREDENTIAL_FREE=$credentialFlag" build }
    'Flash' {
        if ($ManualDownload) {
            # Use SDK-generated offsets/settings; keep an already entered ROM download mode.
            $buildDir = Join-Path $project 'build'
            $flash = Get-Content -Raw (Join-Path $buildDir 'flasher_args.json') | ConvertFrom-Json
            $flashArgs = @('--chip','esp32','--port',$Port,'--baud','115200','--before','no_reset','--after','hard_reset','write_flash')
            $flashArgs += @($flash.write_flash_args)
            foreach ($file in $flash.flash_files.PSObject.Properties) {
                $flashArgs += @($file.Name, (Join-Path $buildDir $file.Value))
            }
            & python -m esptool @flashArgs
        } else { & python "$IdfPath/tools/idf.py" -C $project -p $Port -b 115200 flash }
    }
    'Monitor' { & python "$IdfPath/tools/idf.py" -C $project -p $Port monitor }
    'Test' { & python "$IdfPath/tools/idf.py" -C $project -D HWM_TEST_IMAGE=ON -D "HWM_CONTROL_MQTT=$controlFlag" -D "HWM_REAL_SENSOR=$sensorFlag" -D "HWM_CREDENTIAL_FREE=$credentialFlag" build }
}
if ($LASTEXITCODE -ne 0) { throw "ESP32 $Action failed: $LASTEXITCODE" }
