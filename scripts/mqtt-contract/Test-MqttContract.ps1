# 오프라인 계약 검증 도구다. 실제 MQTT 어댑터/권한 검사/DB 커밋 확인을 수행하지 않는다.
$script:MqttContractSchemaRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../docs/mqtt/v1'))

function New-MqttContractCheck {
    param([bool]$Valid, [string]$Reason, [int]$PayloadBytes = 0)
    [pscustomobject]@{ Valid = $Valid; Reason = $Reason; PayloadBytes = $PayloadBytes }
}

function Test-MqttUniqueJsonKeys {
    param([System.Text.Json.JsonElement]$Element)
    if ($Element.ValueKind -eq [System.Text.Json.JsonValueKind]::Object) {
        $names = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
        foreach ($property in $Element.EnumerateObject()) {
            if (-not $names.Add($property.Name)) { return $false }
            if (-not (Test-MqttUniqueJsonKeys -Element $property.Value)) { return $false }
        }
    } elseif ($Element.ValueKind -eq [System.Text.Json.JsonValueKind]::Array) {
        foreach ($item in $Element.EnumerateArray()) {
            if (-not (Test-MqttUniqueJsonKeys -Element $item)) { return $false }
        }
    }
    return $true
}

function Test-MqttContractMessage {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][AllowEmptyString()][string]$Json,
        [Parameter(Mandatory)][string]$Topic,
        [Parameter(Mandatory)][string[]]$AllowedDeviceIds,
        [string]$ProtocolVersion = '5.0',
        [int]$Qos = 1,
        [bool]$Retained = $false,
        [int]$PayloadFormatIndicator = 1,
        [string]$ContentType = 'application/json',
        [string]$BufferDeviceId,
        [string]$BufferSessionId,
        [Nullable[int]]$BufferSampleCount
    )
    if ($AllowedDeviceIds.Count -eq 0) { throw 'An explicit device allowlist is required.' }
    if ($ProtocolVersion -cne '5.0' -or $Qos -ne 1 -or $Retained -or $PayloadFormatIndicator -ne 1 -or
            $ContentType -cne 'application/json') {
        return New-MqttContractCheck $false 'TRANSPORT'
    }
    try {
        $bytes = [System.Text.UTF8Encoding]::new($false, $true).GetByteCount($Json)
    } catch [System.Text.EncoderFallbackException] {
        return New-MqttContractCheck $false 'UTF8'
    }
    if ($bytes -gt 16384) { return New-MqttContractCheck $false 'SIZE' $bytes }
    $options = [System.Text.Json.JsonDocumentOptions]::new()
    $options.MaxDepth = 16
    try {
        $document = [System.Text.Json.JsonDocument]::Parse($Json, $options)
    } catch [System.Text.Json.JsonException] {
        return New-MqttContractCheck $false 'JSON' $bytes
    }
    try {
        if (-not (Test-MqttUniqueJsonKeys $document.RootElement)) {
            return New-MqttContractCheck $false 'DUPLICATE_KEY' $bytes
        }
        # ConvertFrom-Json보다 먼저 원문 중복 키를 확인해 마지막 값으로 덮어쓰는 해석을 막는다.
        $payload = ConvertFrom-Json -InputObject $Json -AsHashtable -Depth 16 -DateKind String
        if ($payload -isnot [System.Collections.IDictionary]) {
            return New-MqttContractCheck $false 'OBJECT' $bytes
        }
        if ($payload.eventType -ceq 'SESSION_RESULT') {
            $schemaFile = Join-Path $script:MqttContractSchemaRoot 'session-result.schema.json'
            $suffix = 'events'
        } elseif ($payload.eventType -ceq 'SESSION_PRESERVED') {
            $schemaFile = Join-Path $script:MqttContractSchemaRoot 'session-preserved.schema.json'
            $suffix = 'preservation'
        } else { return New-MqttContractCheck $false 'EVENT' $bytes }
        if (-not (Test-Path -LiteralPath $schemaFile)) { throw "Schema file missing: $schemaFile" }
        # 예상된 부정 사례의 엔진 진단은 숨기고 반환값을 검사한다. 긍정 사례 실패는 검증 runner가 실패시킨다.
        if (-not (Test-Json -Json $Json -SchemaFile $schemaFile -ErrorAction SilentlyContinue)) {
            return New-MqttContractCheck $false 'SCHEMA' $bytes
        }
        if ($AllowedDeviceIds -cnotcontains $payload.deviceId -or
                $Topic -cne "home/water/v1/$($payload.deviceId)/$suffix") {
            return New-MqttContractCheck $false 'DEVICE_TOPIC' $bytes
        }
        if ($payload.eventType -ceq 'SESSION_RESULT') {
            # Java String.length()와 같은 UTF-16 단위 제한도 확인한다. Schema maxLength만으로 같다고 주장하지 않는다.
            if ($payload.calibrationVersion.Length -gt 64) {
                return New-MqttContractCheck $false 'TEXT_LENGTH' $bytes
            }
            if ($null -ne $payload.startedAt) {
                $calendar = [datetime]::MinValue
                if (-not [datetime]::TryParseExact($payload.startedAt.Substring(0, 19), 'yyyy-MM-ddTHH:mm:ss',
                        [System.Globalization.CultureInfo]::InvariantCulture,
                        [System.Globalization.DateTimeStyles]::None, [ref]$calendar)) {
                    return New-MqttContractCheck $false 'TIME' $bytes
                }
            }
            $sequences = [System.Collections.Generic.HashSet[int]]::new()
            foreach ($sample in $payload.measurements) {
                if (-not $sequences.Add([int]$sample.seq)) {
                    return New-MqttContractCheck $false 'DUPLICATE_SEQ' $bytes
                }
            }
            if ($payload.measurements.Count -eq 60 -and
                    @($payload.measurements | Where-Object { -not $_.isWarmup }).Count -eq 0) {
                return New-MqttContractCheck $false 'NO_STATISTICS' $bytes
            }
        } else {
            if ($null -ne $payload.comparison -and
                    $payload.comparison.identicalCount + $payload.comparison.conflictCount -ne $payload.preservedSampleCount) {
                return New-MqttContractCheck $false 'COMPARISON_TOTAL' $bytes
            }
            $bufferUuid = [guid]::Empty
            if ($BufferDeviceId -cne $payload.deviceId -or $null -eq $BufferSampleCount -or
                    $BufferSampleCount -ne $payload.preservedSampleCount -or
                    -not [guid]::TryParseExact($BufferSessionId, 'D', [ref]$bufferUuid) -or
                    $bufferUuid -ne [guid]::Parse($payload.sessionId)) {
                return New-MqttContractCheck $false 'BUFFER_CONTEXT' $bytes
            }
        }
        return New-MqttContractCheck $true 'VALID' $bytes
    } finally { $document.Dispose() }
}
