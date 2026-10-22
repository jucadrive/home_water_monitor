#requires -Version 7.5
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'mqtt-contract/Test-MqttControlContract.ps1')
$passed = 0
$failures = [Collections.Generic.List[string]]::new()
function Check-ControlCase([string]$Name, [string]$Json, [bool]$Expected, [hashtable]$Options = @{}) {
    $actual = Test-MqttControlContract -Json $Json @Options
    if ($actual -eq $Expected) { $script:passed++ } else { $script:failures.Add($Name) }
}
$examples = Join-Path $PSScriptRoot '../docs/mqtt/v1/examples'
foreach ($name in @('start','retry','state-request','device-status-idle')) {
    $json = [IO.File]::ReadAllText((Join-Path $examples "$name.json"))
    Check-ControlCase "example $name" $json $true
    $body = ConvertFrom-Json $json -AsHashtable -DateKind String
    foreach ($field in @($body.Keys)) {
        $copy = ConvertFrom-Json $json -AsHashtable -DateKind String
        $copy.Remove($field)
        Check-ControlCase "missing $name/$field" ($copy | ConvertTo-Json -Depth 12 -Compress) $false
    }
    $copy = ConvertFrom-Json $json -AsHashtable -DateKind String
    $copy.unexpected = 1
    Check-ControlCase "extra $name" ($copy | ConvertTo-Json -Depth 12 -Compress) $false
    foreach ($options in @(@{Expiry=0},@{Expiry=11},@{Qos=0},@{Retained=$true},@{Pfi=0},@{ContentType='text/plain'})) {
        Check-ControlCase "transport $name/$($options.Keys)" $json $false $options
    }
    Check-ControlCase "expiry boundary $name" $json $true @{Expiry=1}
    Check-ControlCase "duplicate $name" ('{"schemaVersion":1,'+$json.Substring($json.IndexOf('{')+1)) $false
    $bytes = [Text.Encoding]::UTF8.GetByteCount($json)
    Check-ControlCase "size4096 $name" ($json + (' ' * (4096-$bytes))) $true
    Check-ControlCase "size4097 $name" ($json + (' ' * (4097-$bytes))) $false
}
foreach ($field in @('sampleCount','finalized','round','attempt','controlReady','controlMode','state')) {
    $json = [IO.File]::ReadAllText((Join-Path $examples 'device-status-idle.json'))
    $body = ConvertFrom-Json $json -AsHashtable -DateKind String
    $body[$field] = switch ($field) { 'sampleCount' {1} 'finalized' {$true} 'round' {1} 'attempt' {1} 'controlReady' {$false} 'controlMode' {'UART'} 'state' {'MEASURING'} }
    Check-ControlCase "status invariant $field" ($body | ConvertTo-Json -Depth 12 -Compress) $false
}
foreach ($json in @('{}','[]','null','{','{"eventType":"START","eventType":"RETRY"}')) { Check-ControlCase 'invalid JSON' $json $false }
Write-Output "MQTT control contract: passed=$passed failed=$($failures.Count) skipped=0"
foreach ($failure in $failures) { Write-Output "FAILED: $failure" }
if ($failures.Count) { exit 1 }
exit 0
