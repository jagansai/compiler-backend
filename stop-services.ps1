#!/usr/bin/env pwsh
# Stop Compiler Explorer Services

Write-Host "`n=== Stopping Compiler Explorer ===" -ForegroundColor Cyan

# Stop Backend (port 8083)
Write-Host "`nStopping Backend..." -ForegroundColor Yellow
$backendPort = Get-NetTCPConnection -LocalPort 8083 -ErrorAction SilentlyContinue
if ($backendPort) {
    $backendPid = $backendPort.OwningProcess
    Stop-Process -Id $backendPid -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 2
    Write-Host "✓ Backend stopped (PID: $backendPid)" -ForegroundColor Green
} else {
    Write-Host "✓ Backend not running" -ForegroundColor Gray
}

# Stop Frontend (port 5173)
Write-Host "`nStopping Frontend..." -ForegroundColor Yellow
$frontendPort = Get-NetTCPConnection -LocalPort 5173 -ErrorAction SilentlyContinue
if ($frontendPort) {
    $frontendPid = $frontendPort.OwningProcess
    Stop-Process -Id $frontendPid -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 2
    Write-Host "✓ Frontend stopped (PID: $frontendPid)" -ForegroundColor Green
} else {
    Write-Host "✓ Frontend not running" -ForegroundColor Gray
}

Write-Host "`n=== All services stopped ===" -ForegroundColor Cyan
Write-Host ""
