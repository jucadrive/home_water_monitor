# Offline wire/semantic validation; not a broker, clock or device execution guarantee.
. (Join-Path $PSScriptRoot 'Test-MqttContract.ps1')
function Test-MqttControlContract {
    param([string]$Json, [int]$Expiry = 10, [int]$Qos = 1, [bool]$Retained = $false,
        [string]$ContentType = 'application/json', [int]$Pfi = 1)
    if ($Expiry -lt 1 -or $Expiry -gt 10 -or $Qos -ne 1 -or $Retained -or $Pfi -ne 1 -or $ContentType -cne 'application/json') { return $false }
    $document = $null
    try {
        if ([Text.UTF8Encoding]::new($false,$true).GetByteCount($Json) -gt 4096) { return $false }
        $document = [System.Text.Json.JsonDocument]::Parse($Json)
        if (-not(Test-MqttUniqueJsonKeys $document.RootElement)) { return $false }
        $body = ConvertFrom-Json $Json -AsHashtable -DateKind String -ErrorAction Stop
        $schemaName = switch -CaseSensitive ($body.eventType) {
            'START' {'start'} 'RETRY' {'retry'} 'STATE_REQUEST' {'state-request'} 'DEVICE_STATUS' {'device-status'} default {return $false}
        }
        if (-not(Test-Json -Json $Json -SchemaFile (Join-Path $PSScriptRoot "../../docs/mqtt/v1/$schemaName.schema.json") -ErrorAction SilentlyContinue)) { return $false }
        if ($body.eventType -ceq 'DEVICE_STATUS') {
            if ($body.state -ceq 'IDLE') {
                if ($null -ne $body.sessionId -or $body.sampleCount -ne 0 -or $body.finalized -or $body.round -ne 0 -or $body.attempt -ne 0) { return $false }
            } elseif ($body.state -ceq 'MEASURING') {
                if ($null -eq $body.sessionId -or $body.finalized -or $body.round -ne 1 -or $body.attempt -ne 0) { return $false }
            } elseif ($null -eq $body.sessionId -or -not $body.finalized -or $body.round -lt 1 -or $body.attempt -lt 1) { return $false }
            if ($null -ne $body.controlWindow -and ($null -eq $body.replyToProbeId -or -not $body.controlReady -or
                    $body.controlMode -cne 'MQTT' -or $null -ne $body.fault -or $body.state -cnotin @('IDLE','MANUAL_WAIT'))) { return $false }
            if ($null -ne $body.commandResult -and (($body.commandResult.outcome -ceq 'ACCEPTED' -and $null -ne $body.commandResult.reason) -or
                    ($body.commandResult.outcome -ceq 'REJECTED' -and $null -eq $body.commandResult.reason))) { return $false }
            if ($null -ne $body.lastRelease -and (($body.lastRelease.preservationResult -ceq 'NORMAL' -and $body.lastRelease.preservedSampleCount -ne 60) -or
                    ($body.lastRelease.preservationResult -ceq 'INCOMPLETE' -and $body.lastRelease.preservedSampleCount -ge 60))) { return $false }
        }
        return $true
    } catch { return $false } finally { if ($null -ne $document) { $document.Dispose() } }
}
