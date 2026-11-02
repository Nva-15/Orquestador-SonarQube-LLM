# Arranca SonarQube (Docker) y el orquestador (Spring Boot).
# Uso: .\scripts\iniciar.ps1   (desde la carpeta del proyecto o cualquier otra)

$ErrorActionPreference = 'Stop'
$raiz = Split-Path -Parent $PSScriptRoot

# 1. Docker
docker info *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Host 'Docker no responde. Abre Docker Desktop y vuelve a ejecutar el script.' -ForegroundColor Red
    exit 1
}

# 2. SonarQube
Write-Host 'Levantando SonarQube...' -ForegroundColor Cyan
docker compose -f (Join-Path $raiz 'docker-compose.yml') up -d
if ($LASTEXITCODE -ne 0) { exit 1 }

Write-Host 'Esperando a que SonarQube este UP (http://localhost:9000)...'
$limite = (Get-Date).AddMinutes(5)
$estado = ''
while ((Get-Date) -lt $limite) {
    try {
        # 127.0.0.1 y no localhost: PowerShell 5.1 prueba primero ::1 (IPv6) y se queda colgado
        $estado = (Invoke-RestMethod -Uri 'http://127.0.0.1:9000/api/system/status' -TimeoutSec 5).status
    } catch { $estado = 'sin respuesta' }
    if ($estado -eq 'UP') { break }
    Write-Host "  estado: $estado"
    Start-Sleep -Seconds 5
}
if ($estado -ne 'UP') {
    Write-Host 'SonarQube no llego a UP en 5 minutos. Revisa: docker logs sonarqube-revision-java' -ForegroundColor Red
    exit 1
}
Write-Host 'SonarQube listo.' -ForegroundColor Green

# 3. Variables de entorno
if (-not $env:SONAR_TOKEN) {
    $env:SONAR_TOKEN = [Environment]::GetEnvironmentVariable('SONAR_TOKEN', 'User')
}
if (-not $env:SONAR_TOKEN) {
    Write-Host 'Falta SONAR_TOKEN. Definela con: $env:SONAR_TOKEN="tu_token"' -ForegroundColor Red
    exit 1
}
# Sin API key: Claude Code usa la suscripcion del usuario.
Remove-Item Env:ANTHROPIC_API_KEY -ErrorAction SilentlyContinue

# 4. Orquestador
Write-Host 'Arrancando el orquestador en http://localhost:8080 (Ctrl+C para detener)...' -ForegroundColor Cyan
Push-Location (Join-Path $raiz 'orquestador')
try {
    .\mvnw.cmd spring-boot:run
} finally {
    Pop-Location
}
