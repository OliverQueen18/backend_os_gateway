# Run as Administrator: opens TCP 18080 for OS Gateway API (phone/LAN access)
$ErrorActionPreference = 'Stop'
$name = 'OS Gateway API 18080'
$existing = Get-NetFirewallRule -DisplayName $name -ErrorAction SilentlyContinue
if ($existing) {
    Enable-NetFirewallRule -DisplayName $name
    Write-Host "Rule already exists — enabled."
} else {
    New-NetFirewallRule `
        -DisplayName $name `
        -Direction Inbound `
        -Action Allow `
        -Protocol TCP `
        -LocalPort 18080 `
        -Profile Any `
        -Description 'Allow LAN devices (Android gateway app) to reach api-gateway'
    Write-Host "Rule created."
}

Write-Host ""
Write-Host "Test from phone browser:"
Get-NetIPAddress -AddressFamily IPv4 |
    Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*' -and $_.InterfaceAlias -match 'Wi-Fi|Ethernet' } |
    ForEach-Object { Write-Host ("  http://{0}:18080/actuator/health" -f $_.IPAddress) }
Write-Host ""
Write-Host "Press Enter to close..."
Read-Host
