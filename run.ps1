# run.ps1 - Local development script for Maven Workspace Manager
$ErrorActionPreference = "Stop"

Write-Host "Starting application in development mode..." -ForegroundColor Cyan

# Buscar el proceso que usa el puerto 3333 y cerrarlo si existe (equivalente a fuser -k)
$port = 3333
$process = Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue | Select-Object -First 1

if ($process) {
    Write-Host "Killing process on port $port..." -ForegroundColor Yellow
    Stop-Process -Id $process.OwningProcess -Force -ErrorAction SilentlyContinue
}

# Ejecutar Maven
mvn clean spring-boot:run