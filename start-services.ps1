#!/usr/bin/env pwsh
# Start Compiler Explorer Services

Write-Host "`n=== Starting Compiler Explorer ===" -ForegroundColor Cyan

# Start Backend
Write-Host "`nStarting Backend on http://localhost:8083..." -ForegroundColor Yellow
Start-Process pwsh -ArgumentList "-NoExit", "-Command", "cd '$PSScriptRoot\compiler-backend'; mvn spring-boot:run '-Dmaven.test.skip=true'"
Start-Sleep -Seconds 5

# Start Frontend
Write-Host "Starting Frontend on http://localhost:5173..." -ForegroundColor Yellow
Start-Process pwsh -ArgumentList "-NoExit", "-Command", "cd '$PSScriptRoot\compiler-frontend'; npm run dev"
Start-Sleep -Seconds 3

Write-Host "`n=== Services starting ===" -ForegroundColor Cyan
Write-Host "Backend:  http://localhost:8083" -ForegroundColor Green
Write-Host "Frontend: http://localhost:5173" -ForegroundColor Green
Write-Host "`nCheck the terminal windows for status." -ForegroundColor Gray
Write-Host ""
