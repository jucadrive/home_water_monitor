# Administrator-only, narrowly scoped inbound rule for the approved device bench.
param(
    [Parameter(Mandatory=$true)][System.Net.IPAddress]$LanAddress,
    [ValidateSet(18883,18884)][int]$Port = 18883,
    [switch]$BothPorts,
    [switch]$Disable
)
$ErrorActionPreference = 'Stop'
if ($BothPorts) {
    foreach ($benchPort in @(18883,18884)) {
        & $PSCommandPath -LanAddress $LanAddress -Port $benchPort -Disable:$Disable
    }
    return
}
$ruleName = "HWM-ESP32-Device-Test-$Port"
$ipText = $LanAddress.IPAddressToString
$bytes = $LanAddress.GetAddressBytes()
if ($bytes.Length -ne 4 -or !($bytes[0] -eq 10 -or ($bytes[0] -eq 192 -and $bytes[1] -eq 168) -or
    ($bytes[0] -eq 172 -and $bytes[1] -ge 16 -and $bytes[1] -le 31))) { throw 'A private LAN IPv4 address is required.' }
$address = @(Get-NetIPAddress -IPAddress $ipText -AddressFamily IPv4 | Where-Object AddressState -eq Preferred)
if ($address.Count -ne 1) { throw 'Address is not assigned/preferred.' }
$adapter = Get-NetAdapter | Where-Object ifIndex -eq $address[0].InterfaceIndex
if ($adapter.Status -ne 'Up') { throw 'Selected adapter is not active.' }
$prefix = [int]$address[0].PrefixLength
if ($prefix -lt 8 -or $prefix -gt 30) { throw 'Unsupported LAN prefix.' }
$network = for ($i = 0; $i -lt 4; $i++) {
    $bits = [Math]::Min(8, [Math]::Max(0, $prefix - 8 * $i))
    $mask = (255 -shl (8 - $bits)) -band 255
    $bytes[$i] -band $mask
}
$subnet = ($network -join '.') + '/' + $prefix
$existingRule = Get-NetFirewallRule -Name $ruleName -ErrorAction SilentlyContinue
if ($existingRule) {
    $ports = $existingRule | Get-NetFirewallPortFilter
    if ($existingRule.DisplayName -ne 'Home Water Monitor ESP32 device test' -or $ports.LocalPort -ne [string]$Port -or $ports.Protocol -ne 'TCP') {
        throw 'Existing rule is unexpected; refusing to replace it.'
    }
    if ($Disable) { Disable-NetFirewallRule -Name $ruleName | Out-Null }
    else { Set-NetFirewallRule -Name $ruleName -LocalAddress $ipText -RemoteAddress $subnet -Enabled True | Out-Null }
} elseif (!$Disable) {
    New-NetFirewallRule -Name $ruleName -DisplayName 'Home Water Monitor ESP32 device test' `
        -Direction Inbound -Action Allow -Protocol TCP -LocalAddress $ipText -LocalPort $Port `
        -RemoteAddress $subnet -Profile Any | Out-Null
}
