#requires -Version 7.5
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'mqtt-contract/Test-MqttContract.ps1')
$exampleRoot = Join-Path $PSScriptRoot '../docs/mqtt/v1/examples'
$script:contractPassed = 0
$script:contractFailures = [System.Collections.Generic.List[string]]::new()
$script:payloadSizes = [System.Collections.Generic.List[int]]::new()

function Read-ContractExample([string]$Name) {
    [System.IO.File]::ReadAllText((Join-Path $exampleRoot $Name))
}

function Copy-ContractPayload($Payload) {
    ConvertFrom-Json -InputObject ($Payload | ConvertTo-Json -Depth 12 -Compress) -AsHashtable -DateKind String
}

function Test-ContractCase {
    param([string]$Name, [string]$Json, [bool]$Expected, [hashtable]$Options = @{}, [string]$Reason)
    $arguments = @{ Json = $Json; Topic = 'home/water/v1/esp32-water-01/events'; AllowedDeviceIds = @('esp32-water-01') }
    foreach ($key in $Options.Keys) { $arguments[$key] = $Options[$key] }
    try {
        $result = Test-MqttContractMessage @arguments
        if ($result.Valid -ne $Expected -or ($Reason -and $result.Reason -cne $Reason)) {
            throw "expected=$Expected/$Reason actual=$($result.Valid)/$($result.Reason)"
        }
        $script:contractPassed++
        if ($Expected) { $script:payloadSizes.Add($result.PayloadBytes) }
    } catch {
        $script:contractFailures.Add("${Name}: $($_.Exception.Message)")
    }
}

try {
    $normalJson = Read-ContractExample 'session-result-normal.json'
    $normal = ConvertFrom-Json $normalJson -AsHashtable -DateKind String
    $partialJson = Read-ContractExample 'session-result-incomplete.json'
    $partial = ConvertFrom-Json $partialJson -AsHashtable -DateKind String
    $emptyJson = Read-ContractExample 'session-result-empty.json'
    $empty = ConvertFrom-Json $emptyJson -AsHashtable -DateKind String

    foreach ($entry in @(@('normal', $normalJson), @('incomplete', $partialJson), @('empty', $emptyJson))) {
        Test-ContractCase "example result $($entry[0])" $entry[1] $true
    }
    $ackContext = @{
        Topic = 'home/water/v1/esp32-water-01/preservation'; BufferDeviceId = $normal.deviceId
        BufferSessionId = $normal.sessionId; BufferSampleCount = 60
    }
    foreach ($kind in @('normal', 'identical', 'conflict')) {
        Test-ContractCase "example preserved $kind" (Read-ContractExample "session-preserved-$kind.json") $true $ackContext
    }
    foreach ($entry in @(@('incomplete', $partial, 1), @('empty', $empty, 0), @('empty-redelivery', $empty, 0))) {
        $context = @{ Topic = $ackContext.Topic; BufferDeviceId = $entry[1].deviceId
            BufferSessionId = $entry[1].sessionId; BufferSampleCount = $entry[2] }
        Test-ContractCase "example preserved $($entry[0])" (Read-ContractExample "session-preserved-$($entry[0]).json") $true $context
    }

    # 독립된 예상값으로 필수/nullable/타입/범위·업무 제약을 검증한다.
    $resultCases = @(
        @('version 2', {param($p) $p.schemaVersion = 2}, 'SCHEMA'),
        @('version string', {param($p) $p.schemaVersion = '1'}, 'SCHEMA'),
        @('version boolean', {param($p) $p.schemaVersion = $true}, 'SCHEMA'),
        @('unknown event', {param($p) $p.eventType = 'MEASUREMENT'}, 'EVENT'),
        @('unknown field', {param($p) $p.extra = 1}, 'SCHEMA'),
        @('missing boolean', {param($p) $p.measurements[0].Remove('isWarmup')}, 'SCHEMA'),
        @('missing raw voltage key', {param($p) $p.measurements[0].Remove('rawVoltageMv')}, 'SCHEMA'),
        @('missing time key', {param($p) $p.Remove('startedAt')}, 'SCHEMA'),
        @('null tds', {param($p) $p.measurements[0].tdsPpm = $null}, 'SCHEMA'),
        @('numeric string', {param($p) $p.measurements[0].tdsPpm = '151.3'}, 'SCHEMA'),
        @('boolean string', {param($p) $p.measurements[0].isWarmup = 'false'}, 'SCHEMA'),
        @('sequence 0', {param($p) $p.measurements[0].seq = 0}, 'SCHEMA'),
        @('sequence 61', {param($p) $p.measurements[0].seq = 61}, 'SCHEMA'),
        @('fractional sequence', {param($p) $p.measurements[0].seq = 1.5}, 'SCHEMA'),
        @('duplicate seq different value', {param($p) $p.measurements[1].seq = 1}, 'DUPLICATE_SEQ'),
        @('negative elapsed', {param($p) $p.measurements[0].elapsedMs = -1}, 'SCHEMA'),
        @('elapsed overflow', {param($p) $p.measurements[0].elapsedMs = 2147483648}, 'SCHEMA'),
        @('tds precision', {param($p) $p.measurements[0].tdsPpm = [decimal]'151.301'}, 'SCHEMA'),
        @('tds positive overflow', {param($p) $p.measurements[0].tdsPpm = 10000000}, 'SCHEMA'),
        @('tds negative overflow', {param($p) $p.measurements[0].tdsPpm = -10000000}, 'SCHEMA'),
        @('voltage precision', {param($p) $p.measurements[0].rawVoltageMv = [decimal]'750.001'}, 'SCHEMA'),
        @('temperature overflow', {param($p) $p.temperatureAssumedC = 1000}, 'SCHEMA'),
        @('temperature precision', {param($p) $p.temperatureAssumedC = [decimal]'25.001'}, 'SCHEMA'),
        @('wrong expected count', {param($p) $p.expectedSamples = 59}, 'SCHEMA'),
        @('wrong interval', {param($p) $p.samplingIntervalMs = 999}, 'SCHEMA'),
        @('overlong list', {param($p) $p.measurements += $p.measurements[0]}, 'SCHEMA'),
        @('null list', {param($p) $p.measurements = $null}, 'SCHEMA'),
        @('all warmup normal', {param($p) foreach ($m in $p.measurements) {$m.isWarmup = $true}}, 'NO_STATISTICS'),
        @('missing uuid', {param($p) $p.Remove('sessionId')}, 'SCHEMA'),
        @('noncanonical uuid', {param($p) $p.sessionId = '1-1-1-1-1'}, 'SCHEMA'),
        @('topic injection', {param($p) $p.deviceId = 'device/other'}, 'SCHEMA'),
        @('overlong device', {param($p) $p.deviceId = 'd' * 65}, 'SCHEMA'),
        @('blank calibration', {param($p) $p.calibrationVersion = '   '}, 'SCHEMA'),
        @('overlong calibration', {param($p) $p.calibrationVersion = 'x' * 65}, 'SCHEMA'),
        @('control character', {param($p) $p.calibrationVersion = "test`nversion"}, 'SCHEMA'),
        @('utf16 limit', {param($p) $p.calibrationVersion = '😀' * 33}, 'TEXT_LENGTH'),
        @('invalid calendar', {param($p) $p.startedAt = '2026-02-30T00:00:00Z'}, 'TIME'),
        @('invalid hour', {param($p) $p.startedAt = '2026-10-22T25:00:00Z'}, 'TIME'),
        @('offset rather than utc', {param($p) $p.startedAt = '2026-10-22T09:00:00+09:00'}, 'SCHEMA'),
        @('missing zone', {param($p) $p.startedAt = '2026-10-22T00:00:00'}, 'SCHEMA'),
        @('too many fractional seconds', {param($p) $p.startedAt = '2026-10-22T00:00:00.1234567890Z'}, 'SCHEMA')
    )
    foreach ($case in $resultCases) {
        $changed = Copy-ContractPayload $normal
        & $case[1] $changed | Out-Null
        Test-ContractCase $case[0] ($changed | ConvertTo-Json -Depth 8 -Compress) $false @{} $case[2]
    }
    foreach ($key in $normal.Keys) {
        $changed = Copy-ContractPayload $normal
        $changed.Remove($key)
        $reason = if ($key -ceq 'eventType') { 'EVENT' } else { 'SCHEMA' }
        Test-ContractCase "required result $key" ($changed | ConvertTo-Json -Depth 8 -Compress) $false @{} $reason
    }

    $positiveCases = @(
        @('partial 59 middle gap', {param($p) $p.measurements = @($p.measurements | Where-Object {$_.seq -ne 30})}),
        @('reversed order', {param($p) [array]::Reverse($p.measurements)}),
        @('null time and voltage', {param($p) $p.startedAt = $null; $p.measurements[0].rawVoltageMv = $null}),
        @('nanosecond time', {param($p) $p.startedAt = '2026-10-22T00:00:00.123456789Z'}),
        @('leap date', {param($p) $p.startedAt = '2028-02-29T00:00:00Z'}),
        @('positive numeric edge', {param($p) $p.measurements[0].tdsPpm = [decimal]'9999999.99'; $p.temperatureAssumedC = [decimal]'999.99'}),
        @('negative numeric edge', {param($p) $p.measurements[0].tdsPpm = [decimal]'-9999999.99'; $p.measurements[0].rawVoltageMv = [decimal]'-9999999.99'}),
        @('zero and max elapsed', {param($p) $p.measurements[0].elapsedMs = 0; $p.measurements[1].elapsedMs = [int]::MaxValue}),
        @('unicode calibration edge', {param($p) $p.calibrationVersion = '😀' * 32})
    )
    foreach ($case in $positiveCases) {
        $changed = Copy-ContractPayload $normal
        & $case[1] $changed | Out-Null
        Test-ContractCase $case[0] ($changed | ConvertTo-Json -Depth 8 -Compress) $true
    }
    Test-ContractCase 'numeric trailing zeros' ($normalJson -replace '300\.0', '300.000') $true
    Test-ContractCase 'duplicate top key' ($normalJson -replace '"schemaVersion": 1', '"schemaVersion":1,"schemaVersion":1') $false @{} 'DUPLICATE_KEY'
    Test-ContractCase 'duplicate nested key' ($normalJson -replace '"seq": 1,', '"seq":1,"seq":2,') $false @{} 'DUPLICATE_KEY'
    Test-ContractCase 'malformed json' '{' $false @{} 'JSON'
    Test-ContractCase 'nonfinite number' '{"tdsPpm":NaN}' $false @{} 'JSON'
    Test-ContractCase 'root array' '[]' $false @{} 'OBJECT'
    Test-ContractCase 'empty json' '' $false @{} 'JSON'

    foreach ($options in @(@{ ProtocolVersion = '3.1.1' }, @{ Qos = 0 }, @{ Qos = 2 }, @{ Retained = $true },
            @{ PayloadFormatIndicator = 0 }, @{ ContentType = 'text/plain' })) {
        Test-ContractCase 'wrong transport metadata' $partialJson $false $options 'TRANSPORT'
    }
    Test-ContractCase 'wrong topic device' $partialJson $false @{Topic = 'home/water/v1/other-device/events'} 'DEVICE_TOPIC'
    Test-ContractCase 'wrong direction' $partialJson $false @{Topic = $ackContext.Topic} 'DEVICE_TOPIC'
    Test-ContractCase 'device not allowed' $partialJson $false @{AllowedDeviceIds = @('other-device')} 'DEVICE_TOPIC'

    $ackJson = Read-ContractExample 'session-preserved-identical.json'
    $ack = ConvertFrom-Json $ackJson -AsHashtable -DateKind String
    foreach ($case in @(
        @('wrong comparison total', {param($p) $p.comparison.identicalCount = 59}, 'COMPARISON_TOTAL'),
        @('negative comparison', {param($p) $p.comparison.conflictCount = -1}, 'SCHEMA'),
        @('normal count 59', {param($p) $p.preservedSampleCount = 59}, 'SCHEMA'),
        @('incomplete count 60', {param($p) $p.preservationResult = 'INCOMPLETE'}, 'SCHEMA'),
        @('unknown preservation', {param($p) $p.preservationResult = 'FAILED'}, 'SCHEMA'),
        @('unknown comparison field', {param($p) $p.comparison.outcome = 'ALL_IDENTICAL'}, 'SCHEMA'),
        @('missing comparison member', {param($p) $p.comparison.Remove('conflictCount')}, 'SCHEMA')
    )) {
        $changed = Copy-ContractPayload $ack
        & $case[1] $changed | Out-Null
        Test-ContractCase $case[0] ($changed | ConvertTo-Json -Depth 8 -Compress) $false $ackContext $case[2]
    }
    foreach ($key in $ack.Keys) {
        $changed = Copy-ContractPayload $ack
        $changed.Remove($key)
        Test-ContractCase "required preserved $key" ($changed | ConvertTo-Json -Depth 8 -Compress) $false $ackContext
    }
    foreach ($changes in @(@{ BufferSessionId = $empty.sessionId }, @{ BufferSampleCount = 59 }, @{ BufferDeviceId = 'other-device' })) {
        $context = $ackContext.Clone()
        foreach ($key in $changes.Keys) { $context[$key] = $changes[$key] }
        Test-ContractCase 'wrong buffer context' $ackJson $false $context 'BUFFER_CONTEXT'
    }
    Test-ContractCase 'missing buffer context' $ackJson $false @{Topic = $ackContext.Topic} 'BUFFER_CONTEXT'
    $upperUuid = Copy-ContractPayload $ack
    $upperUuid.sessionId = $upperUuid.sessionId.ToUpperInvariant()
    Test-ContractCase 'uuid value correlation' ($upperUuid | ConvertTo-Json -Depth 8 -Compress) $true $ackContext

    $utf8 = [System.Text.UTF8Encoding]::new($false, $true)
    $compact = $partial | ConvertTo-Json -Depth 8 -Compress
    $exact = $compact + (' ' * (16384 - $utf8.GetByteCount($compact)))
    Test-ContractCase 'exact utf8 size limit' $exact $true
    Test-ContractCase 'one byte over limit' ($exact + ' ') $false @{} 'SIZE'
    $unicode = Copy-ContractPayload $partial
    $unicode.calibrationVersion = '가' * 64
    $unicodeJson = $unicode | ConvertTo-Json -Depth 8 -Compress
    $unicodeExact = $unicodeJson + (' ' * (16384 - $utf8.GetByteCount($unicodeJson)))
    Test-ContractCase 'unicode exact byte limit' $unicodeExact $true
    Test-ContractCase 'unicode over byte limit' ($unicodeExact + ' ') $false @{} 'SIZE'
} catch {
    $script:contractFailures.Add("Verifier setup: $($_.Exception.Message)")
}

foreach ($failure in $script:contractFailures) { Write-Output "FAIL $failure" }
Write-Output "MQTT contract: passed=$script:contractPassed failed=$($script:contractFailures.Count) skipped=0"
Write-Output "Accepted payload bytes: max=$(($script:payloadSizes | Measure-Object -Maximum).Maximum), limit=16384"
if ($script:contractFailures.Count -gt 0) { exit 1 }
exit 0
